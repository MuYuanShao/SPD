package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.service.DocumentKind.SETTLEMENT_BILL;

/**
 * Creates supplier settlement data when the inventory batch's snapshotted settlement point is reached.
 */
@Service
public class SettlementPointService {

    public static final String PURCHASE_IN = "purchase_in";
    public static final String DEPARTMENT_CONSUMPTION = "department_consumption";
    public static final String ACTUAL_SALE = "actual_sale";

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public SettlementPointService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    @Transactional
    public List<String> generateForReceivingOrder(Long receivingOrderId) {
        return createBills(PURCHASE_IN, jdbcTemplate.queryForList("""
                SELECT ib.supplier_id AS supplierId, DATE_FORMAT(ro.receive_time, '%Y-%m') AS settlementPeriod,
                       'receiving_order_item' AS sourceBizType, roi.item_id AS sourceBizId,
                       roi.product_id AS productId, ib.batch_id AS batchId, NULL AS traceCodeId,
                       roi.qualified_quantity AS quantity, ib.batch_unit_price AS unitPrice,
                       roi.qualified_quantity * ib.batch_unit_price AS amount
                  FROM receiving_order ro
                  JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                  JOIN inventory_batch ib ON ib.receiving_item_id = roi.item_id
                 WHERE ro.receiving_order_id = ? AND ro.receiving_status = 'approved'
                   AND ib.settlement_mode = 'purchase_in'
                   AND roi.qualified_quantity > 0
                   AND NOT EXISTS (
                     SELECT 1 FROM settlement_bill_item sbi
                      WHERE sbi.source_biz_type = 'receiving_order_item' AND sbi.source_biz_id = roi.item_id
                   )
                 ORDER BY ib.supplier_id, roi.item_id
                 FOR UPDATE
                """, receivingOrderId));
    }

    @Transactional
    public List<String> generateForConsumption(Long consumptionId) {
        return createBills(DEPARTMENT_CONSUMPTION, jdbcTemplate.queryForList("""
                SELECT ib.supplier_id AS supplierId, DATE_FORMAT(dc.consume_time, '%Y-%m') AS settlementPeriod,
                       'department_consumption_item' AS sourceBizType, dci.item_id AS sourceBizId,
                       dci.product_id AS productId, dci.batch_id AS batchId, dci.trace_code_id AS traceCodeId,
                       dci.quantity, dci.unit_price AS unitPrice, dci.amount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN inventory_batch ib ON ib.batch_id = dci.batch_id
                 WHERE dc.consumption_id = ? AND dc.status = 'confirmed'
                   AND ib.settlement_mode = 'department_consumption'
                   AND NOT EXISTS (
                     SELECT 1 FROM settlement_bill_item sbi
                      WHERE sbi.source_biz_type = 'department_consumption_item'
                        AND sbi.source_biz_id = dci.item_id
                   )
                 ORDER BY ib.supplier_id, dci.item_id
                 FOR UPDATE
                """, consumptionId));
    }

    @Transactional
    public List<String> generateForHighValueCharge(Long chargeId) {
        return createBills(ACTUAL_SALE, jdbcTemplate.queryForList("""
                SELECT ib.supplier_id AS supplierId, DATE_FORMAT(hvc.charge_time, '%Y-%m') AS settlementPeriod,
                       'high_value_charge' AS sourceBizType, hvc.charge_id AS sourceBizId,
                       ib.product_id AS productId, ib.batch_id AS batchId, hvc.trace_code_id AS traceCodeId,
                       hvc.quantity,
                       CASE WHEN hvc.quantity = 0 THEN 0 ELSE hvc.amount / hvc.quantity END AS unitPrice,
                       hvc.amount
                  FROM high_value_charge hvc
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = hvc.trace_code_id
                  JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                 WHERE hvc.charge_id = ? AND hvc.status = 'charged'
                   AND ib.settlement_mode = 'actual_sale'
                   AND NOT EXISTS (
                     SELECT 1 FROM settlement_bill_item sbi
                      WHERE sbi.source_biz_type = 'high_value_charge' AND sbi.source_biz_id = hvc.charge_id
                   )
                 FOR UPDATE
                """, chargeId));
    }

    /**
     * Generates only settlement details missing for an already completed business source.
     * Source rows are locked and the database source unique key remains the final concurrency guard.
     */
    @Transactional
    public Map<String, Object> generateMissing(ManualSettlementGenerationRequest request) {
        validateManualRequest(request);
        List<Object> args = new ArrayList<>();
        args.add(request.startDate());
        args.add(request.endDate().plusDays(1));
        String sql = manualSourceSql(request.settlementMode());
        if (request.supplierId() != null) {
            sql += " AND ib.supplier_id = ?";
            args.add(request.supplierId());
        }
        sql += " ORDER BY ib.supplier_id, sourceBizId FOR UPDATE";
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(sql, args.toArray());
        List<Map<String, Object>> missing = candidates.stream()
                .filter(row -> !truthy(row.get("alreadySettled")))
                .toList();
        List<String> settlementNos = createBills(request.settlementMode().code(), missing, "manual_generate");
        return Map.of(
                "settlementNos", settlementNos,
                "billCount", settlementNos.size(),
                "detailCount", missing.size(),
                "skippedExistingCount", candidates.size() - missing.size()
        );
    }

    private static void validateManualRequest(ManualSettlementGenerationRequest request) {
        if (request == null || request.settlementMode() == null
                || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("settlement mode and date range are required");
        }
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (days < 0) throw new IllegalArgumentException("end date must not precede start date");
        if (days > 30) throw new IllegalArgumentException("settlement date range cannot exceed 31 days");
    }

    private static String manualSourceSql(SettlementMode mode) {
        return switch (mode) {
            case PURCHASE_IN -> """
                    SELECT ib.supplier_id AS supplierId, DATE_FORMAT(ro.receive_time, '%Y-%m') AS settlementPeriod,
                           'receiving_order_item' AS sourceBizType, roi.item_id AS sourceBizId,
                           roi.product_id AS productId, ib.batch_id AS batchId, NULL AS traceCodeId,
                           roi.qualified_quantity AS quantity, ib.batch_unit_price AS unitPrice,
                           roi.qualified_quantity * ib.batch_unit_price AS amount,
                           EXISTS (SELECT 1 FROM settlement_bill_item sbi
                                    WHERE sbi.source_biz_type = 'receiving_order_item'
                                      AND sbi.source_biz_id = roi.item_id) AS alreadySettled
                      FROM receiving_order ro
                      JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                      JOIN inventory_batch ib ON ib.receiving_item_id = roi.item_id
                     WHERE ro.receiving_status = 'approved' AND ib.settlement_mode = 'purchase_in'
                       AND roi.qualified_quantity > 0 AND ro.receive_time >= ? AND ro.receive_time < ?
                    """;
            case DEPARTMENT_CONSUMPTION -> """
                    SELECT ib.supplier_id AS supplierId, DATE_FORMAT(dc.consume_time, '%Y-%m') AS settlementPeriod,
                           'department_consumption_item' AS sourceBizType, dci.item_id AS sourceBizId,
                           dci.product_id AS productId, dci.batch_id AS batchId, dci.trace_code_id AS traceCodeId,
                           dci.quantity, dci.unit_price AS unitPrice, dci.amount,
                           EXISTS (SELECT 1 FROM settlement_bill_item sbi
                                    WHERE sbi.source_biz_type = 'department_consumption_item'
                                      AND sbi.source_biz_id = dci.item_id) AS alreadySettled
                      FROM department_consumption dc
                      JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                      JOIN inventory_batch ib ON ib.batch_id = dci.batch_id
                     WHERE dc.status = 'confirmed' AND ib.settlement_mode = 'department_consumption'
                       AND dc.consume_time >= ? AND dc.consume_time < ?
                    """;
            case ACTUAL_SALE -> """
                    SELECT ib.supplier_id AS supplierId, DATE_FORMAT(hvc.charge_time, '%Y-%m') AS settlementPeriod,
                           'high_value_charge' AS sourceBizType, hvc.charge_id AS sourceBizId,
                           ib.product_id AS productId, ib.batch_id AS batchId, hvc.trace_code_id AS traceCodeId,
                           hvc.quantity, CASE WHEN hvc.quantity = 0 THEN 0 ELSE hvc.amount / hvc.quantity END AS unitPrice,
                           hvc.amount,
                           EXISTS (SELECT 1 FROM settlement_bill_item sbi
                                    WHERE sbi.source_biz_type = 'high_value_charge'
                                      AND sbi.source_biz_id = hvc.charge_id) AS alreadySettled
                      FROM high_value_charge hvc
                      JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = hvc.trace_code_id
                      JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                     WHERE hvc.status = 'charged' AND ib.settlement_mode = 'actual_sale'
                       AND hvc.charge_time >= ? AND hvc.charge_time < ?
                    """;
        };
    }

    private List<String> createBills(String settlementPoint, List<Map<String, Object>> eligible) {
        return createBills(settlementPoint, eligible, "auto_generate");
    }

    private List<String> createBills(String settlementPoint, List<Map<String, Object>> eligible, String auditAction) {
        eligible = eligible.stream()
                .filter(row -> row.get("supplierId") instanceof Number
                        && row.get("sourceBizType") != null && row.get("sourceBizId") instanceof Number
                        && row.get("settlementPeriod") != null)
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Map<GroupKey, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : eligible) {
            GroupKey key = new GroupKey(number(row.get("supplierId")), String.valueOf(row.get("settlementPeriod")));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        List<String> settlementNos = new ArrayList<>();
        for (Map.Entry<GroupKey, List<Map<String, Object>>> entry : groups.entrySet()) {
            BigDecimal totalAmount = entry.getValue().stream()
                    .map(row -> (BigDecimal) row.get("amount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String settlementNo = support.nextNo(SETTLEMENT_BILL);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO settlement_bill (
                          settlement_no, supplier_id, settlement_period, settlement_mode, total_amount, status
                        ) VALUES (?, ?, ?, ?, ?, 'pending_confirm')
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, settlementNo);
                ps.setLong(2, entry.getKey().supplierId());
                ps.setString(3, entry.getKey().period());
                ps.setString(4, settlementPoint);
                ps.setBigDecimal(5, totalAmount);
                return ps;
            }, keyHolder);
            Long settlementId = Objects.requireNonNull(keyHolder.getKey()).longValue();
            for (Map<String, Object> row : entry.getValue()) {
                jdbcTemplate.update("""
                        INSERT INTO settlement_bill_item (
                          settlement_id, source_biz_type, source_biz_id, product_id, batch_id, trace_code_id,
                          quantity, unit_price, amount
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, settlementId, row.get("sourceBizType"), row.get("sourceBizId"), row.get("productId"),
                        row.get("batchId"), row.get("traceCodeId"), row.get("quantity"),
                        row.get("unitPrice"), row.get("amount"));
            }
            String adverb = "manual_generate".equals(auditAction) ? "manually" : "automatically";
            support.writeAudit("settlement_bill", auditAction, settlementId, settlementNo,
                    "settlement generated " + adverb + " at " + settlementPoint);
            settlementNos.add(settlementNo);
        }
        return List.copyOf(settlementNos);
    }

    private static Long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("settlement source supplier is required");
        }
        return number.longValue();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record GroupKey(Long supplierId, String period) {
    }
}
