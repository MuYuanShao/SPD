package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.service.DocumentKind.CONSUMPTION_RED_FLUSH;
import static com.hospital.spd.common.service.DocumentKind.DEPARTMENT_CONSUMPTION;

/**
 * Owns department consumption creation and reversal inside Operational Closure.
 */
@Service
public class OperationalConsumptionModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;
    private final SettlementPointService settlementPointService;
    private final QuotaPackageTraceFlowService quotaPackageTraceFlowService;

    public OperationalConsumptionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system,
                new SettlementPointService(jdbcTemplate, support),
                new QuotaPackageTraceFlowService(jdbcTemplate, support));
    }

    OperationalConsumptionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                 QuotaPackageTraceFlowService quotaPackageTraceFlowService) {
        this(jdbcTemplate, support, OperatorContext::system,
                new SettlementPointService(jdbcTemplate, support), quotaPackageTraceFlowService);
    }

    @Autowired
    public OperationalConsumptionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                        OperatorContextProvider operatorContextProvider,
                                        SettlementPointService settlementPointService,
                                        QuotaPackageTraceFlowService quotaPackageTraceFlowService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.settlementPointService = settlementPointService;
        this.quotaPackageTraceFlowService = quotaPackageTraceFlowService;
    }

    @Transactional
    public Map<String, Object> createConsumption(Map<String, Object> body) {
        String deptName = requireText(body, "deptName");
        String warehouseName = requireText(body, "warehouseName");
        String productCode = requireText(body, "productCode");
        BigDecimal quantity = requirePositive(body, "quantity");
        Long deptId = findDept(deptName);
        Long warehouseId = findDepartmentWarehouse(deptName, warehouseName);
        Map<String, Object> product = findProduct(productCode);
        if (product.get("highValue") instanceof Number highValue && highValue.intValue() == 1) {
            throw new IllegalArgumentException("高值耗材只能在收到 HIS/手麻/手术系统计费回传后扣减库存");
        }
        OperatorContext operator = operatorContextProvider.current();
        String consumptionNo = support.nextNo(DEPARTMENT_CONSUMPTION);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO department_consumption (
                      consumption_no, dept_id, warehouse_id, consumption_type, related_biz_type, status, consume_by
                    ) VALUES (?, ?, ?, 'department_consumption', 'inventory', 'confirmed', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, consumptionNo);
            ps.setLong(2, deptId);
            ps.setLong(3, warehouseId);
            ps.setLong(4, operator.userId());
            return ps;
        }, keyHolder);
        Long consumptionId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        List<SupplyChainSupport.InventoryDeduction> deductions = support.consumeAvailableFifo(
                warehouseId, ((Number) product.get("productId")).longValue(), quantity,
                "department_consumption_out", "department_consumption", consumptionId,
                "department consumption confirmed");
        BigDecimal amount = BigDecimal.ZERO;
        for (SupplyChainSupport.InventoryDeduction deduction : deductions) {
            BigDecimal itemAmount = deduction.quantity().multiply(deduction.unitPrice());
            jdbcTemplate.update("""
                    INSERT INTO department_consumption_item (consumption_id, product_id, batch_id, quantity, unit_price, amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, consumptionId, product.get("productId"), deduction.batchId(), deduction.quantity(),
                    deduction.unitPrice(), itemAmount);
            amount = amount.add(itemAmount);
        }
        settlementPointService.generateForConsumption(consumptionId);
        return Map.of("consumptionNo", consumptionNo, "amount", amount);
    }

    @Transactional
    public Map<String, Object> reverseConsumption(String consumptionNo) {
        List<Long> products = jdbcTemplate.queryForList("""
                SELECT DISTINCT item.product_id FROM department_consumption_item item
                  JOIN department_consumption consumption ON consumption.consumption_id = item.consumption_id
                 WHERE consumption.consumption_no = ?
                """, Long.class, consumptionNo);
        new QuotaPackagePricingService(jdbcTemplate).lockProducts(products);
        Map<String, Object> consumption = jdbcTemplate.queryForMap("""
                SELECT consumption_id AS consumptionId, warehouse_id AS warehouseId, status,
                       related_biz_type AS relatedBizType, related_biz_id AS relatedBizId
                  FROM department_consumption
                 WHERE consumption_no = ?
                 FOR UPDATE
                """, consumptionNo);
        if (!"confirmed".equals(String.valueOf(consumption.get("status")))) {
            throw new IllegalArgumentException("only confirmed consumption can be reversed");
        }
        Long consumptionId = ((Number) consumption.get("consumptionId")).longValue();
        Long warehouseId = ((Number) consumption.get("warehouseId")).longValue();
        // Lock all related bills before inspecting status; a concurrent confirmation must serialize here.
        List<Map<String, Object>> settlements = jdbcTemplate.queryForList("""
                SELECT sb.settlement_id AS settlementId, sb.status
                  FROM settlement_bill sb
                 WHERE EXISTS (
                     SELECT 1 FROM settlement_bill_item sbi
                     JOIN department_consumption_item dci ON dci.item_id = sbi.source_biz_id
                     WHERE sbi.settlement_id = sb.settlement_id
                       AND sbi.source_biz_type = 'department_consumption_item'
                       AND dci.consumption_id = ?
                 )
                 ORDER BY sb.settlement_id
                 FOR UPDATE
                """, consumptionId);
        if (settlements.stream().anyMatch(row -> "confirmed".equals(row.get("status")))) {
            throw new IllegalArgumentException("该消耗已确认结算，不能反消耗；请先按财务红冲流程处理结算单");
        }
        if ("quota_package_label".equals(String.valueOf(consumption.get("relatedBizType")))
                && consumption.get("relatedBizId") instanceof Number labelId) {
            quotaPackageTraceFlowService.reverseConsumption(labelId.longValue(), consumptionNo);
        }
        removePendingSettlementItems(consumptionId, settlements);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT product_id AS productId, batch_id AS batchId, quantity
                  FROM department_consumption_item
                 WHERE consumption_id = ?
                """, consumptionId);
        for (Map<String, Object> item : items) {
            support.receiveAvailable(warehouseId, ((Number) item.get("productId")).longValue(),
                    ((Number) item.get("batchId")).longValue(), (BigDecimal) item.get("quantity"),
                    "department_consumption_reverse_in", "department_consumption", consumptionId,
                    "department consumption reversed");
        }
        int updated = jdbcTemplate.update("UPDATE department_consumption SET status = 'reversed' WHERE consumption_no = ? AND status = 'confirmed'", consumptionNo);
        if (updated != 1) {
            throw new IllegalArgumentException("consumption has already been processed");
        }
        String reverseNo = support.nextNo(CONSUMPTION_RED_FLUSH);
        jdbcTemplate.update("""
                INSERT INTO consumption_red_flush (flush_no, source_consumption_no, flush_type, status, remark)
                VALUES (?, ?, 'reverse_consumption', 'approved', 'reverse consumption red flush')
                """, reverseNo, consumptionNo);
        support.writeAudit("operational_closure", "reverse_consumption", consumptionId, consumptionNo, "reverse consumption red flush");
        return Map.of("flushNo", reverseNo, "status", "approved");
    }

    /**
     * A reversed consumption is no longer payable. Remove only its items from unconfirmed bills,
     * then recalculate shared bills and discard a bill only when it has become empty.
     */
    private void removePendingSettlementItems(Long consumptionId, List<Map<String, Object>> lockedSettlements) {
        List<Map<String, Object>> settlements = lockedSettlements.stream()
                .filter(row -> "pending_confirm".equals(row.get("status"))).toList();
        if (settlements.isEmpty()) {
            return;
        }
        jdbcTemplate.update("""
                DELETE sbi FROM settlement_bill_item sbi
                  JOIN department_consumption_item dci ON dci.item_id = sbi.source_biz_id
                  JOIN settlement_bill sb ON sb.settlement_id = sbi.settlement_id
                 WHERE sbi.source_biz_type = 'department_consumption_item'
                   AND dci.consumption_id = ?
                   AND sb.status = 'pending_confirm'
                """, consumptionId);
        for (Map<String, Object> settlement : settlements) {
            Long settlementId = ((Number) settlement.get("settlementId")).longValue();
            jdbcTemplate.update("""
                    UPDATE settlement_bill sb
                       SET total_amount = COALESCE((
                           SELECT SUM(sbi.amount) FROM settlement_bill_item sbi
                            WHERE sbi.settlement_id = sb.settlement_id
                       ), 0)
                     WHERE sb.settlement_id = ? AND sb.status = 'pending_confirm'
                    """, settlementId);
            jdbcTemplate.update("""
                    DELETE FROM settlement_bill
                     WHERE settlement_id = ? AND status = 'pending_confirm'
                       AND NOT EXISTS (
                           SELECT 1 FROM settlement_bill_item sbi
                            WHERE sbi.settlement_id = settlement_bill.settlement_id
                       )
                    """, settlementId);
        }
    }

    /**
     * 科室消耗按定数包码、UDI 或唯一码定位商品，返回商品信息与来源码信息。
     */
    public Map<String, Object> resolveConsumptionProduct(String queryCode) {
        if (queryCode == null || queryCode.isBlank()) {
            throw new IllegalArgumentException("请输入定数包码、UDI 或唯一码");
        }
        String code = queryCode.trim();

        List<Map<String, Object>> labels = jdbcTemplate.queryForList("""
                SELECT qpl.label_no AS labelNo, qpl.status AS labelStatus, qpl.trace_code_id AS traceCodeId,
                       qpl.package_quantity AS packageQuantity,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.unit,
                       w.warehouse_name AS warehouseName
                  FROM quota_package_label qpl
                  LEFT JOIN udi_trace_code tc ON tc.trace_code_id = qpl.trace_code_id
                  JOIN product p ON p.product_id = qpl.product_id AND p.deleted = 0 AND p.status = 1
                  LEFT JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                 WHERE qpl.label_no = ? OR tc.udi_code = ? OR tc.unique_code = ?
                 LIMIT 1
                """, code, code, code);
        if (!labels.isEmpty()) {
            Map<String, Object> row = labels.get(0);
            row.put("sourceType", "package");
            row.put("sourceCode", code);
            return row;
        }

        List<Map<String, Object>> traces = jdbcTemplate.queryForList("""
                SELECT utc.udi_code AS udiCode, utc.unique_code AS uniqueCode,
                       utc.product_code AS productCode, utc.product_name AS productName,
                       utc.spec_model AS specModel, p.unit,
                       utc.current_location AS warehouseName
                  FROM udi_trace_code utc
                  LEFT JOIN product p ON p.product_code = utc.product_code AND p.deleted = 0 AND p.status = 1
                 WHERE utc.udi_code = ? OR utc.unique_code = ?
                 LIMIT 1
                """, code, code);
        if (!traces.isEmpty()) {
            Map<String, Object> row = traces.get(0);
            row.put("sourceType", "udi");
            row.put("sourceCode", code);
            return row;
        }

        throw new IllegalArgumentException("未找到该定数包码/UDI/唯一码对应的商品");
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice, is_high_value AS highValue
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1 FOR UPDATE
                """, productCode);
    }

    private Long findDept(String deptName) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1",
                Long.class, deptName);
        if (ids.isEmpty()) throw new IllegalArgumentException("department does not exist or is disabled");
        return ids.get(0);
    }

    private Long findDepartmentWarehouse(String deptName, String warehouseName) {
        return jdbcTemplate.queryForObject("""
                SELECT w.warehouse_id
                  FROM warehouse w
                  JOIN sys_dept d ON d.dept_id = w.dept_id
                 WHERE d.dept_name = ? AND w.warehouse_name = ?
                   AND d.deleted = 0 AND d.status = 1 AND w.deleted = 0 AND w.status = 1
                """, Long.class, deptName, warehouseName);
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value).trim();
    }

    private static BigDecimal requirePositive(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        BigDecimal quantity = new BigDecimal(String.valueOf(value));
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
        return quantity;
    }
}
