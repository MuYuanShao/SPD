package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

import static com.hospital.spd.common.service.DocumentKind.COLD_CHAIN_EXCEPTION;
import static com.hospital.spd.common.service.DocumentKind.RECALL_EVENT;
import static com.hospital.spd.common.service.DocumentKind.QUOTA_PACKAGE_EVENT;

/**
 * Owns risk-event creation inside Operational Closure.
 */
@Service
public class OperationalRiskModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public OperationalRiskModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    public Map<String, Object> coldChainException(Map<String, Object> body) {
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        String warehouseName = requireText(body, "warehouseName");
        BigDecimal temperature = new BigDecimal(requireText(body, "temperature"));
        String severity = requireText(body, "severity");
        findWarehouseId(warehouseName);
        String eventNo = support.nextNo(COLD_CHAIN_EXCEPTION);
        jdbcTemplate.update("""
                INSERT INTO cold_chain_exception (
                  event_no, warehouse_name, product_code, product_name, temperature, severity, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'pending_dispose')
                """, eventNo, warehouseName, product.get("productCode"),
                product.get("productName"), temperature, severity);
        return Map.of("eventNo", eventNo, "status", "pending_dispose");
    }

    /** 召回隔离批次选项：所选商品在该库房仍有可用库存的批次。 */
    public Map<String, Object> recallBatches(String productCode, String warehouseName) {
        Long productId = ((Number) findProduct(productCode).get("productId")).longValue();
        Long warehouseId = findWarehouseId(warehouseName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ib.batch_id AS batchId, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       bal.available_qty AS availableQty
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                """, warehouseId, productId);
        return Map.of("rows", rows);
    }

    /** 召回范围内库存：定数包按来源批次折算为散货数量展示。 */
    public Map<String, Object> recallInventory(Map<String, String> params) {
        String productCode = params.getOrDefault("productCode", "").trim();
        if (productCode.isBlank()) return Map.of("rows", List.of());
        Map<String, Object> product = findProduct(productCode);
        return Map.of("rows", recallInventoryRows(
                ((Number) product.get("productId")).longValue(),
                normalizeScope(params.get("scope")),
                params.getOrDefault("warehouseName", "").trim(), null));
    }

    /**
     * 召回隔离：选定商品与批次后提交确认，立即将该批次的可用库存转为隔离库存（库存扣减）。
     */
    @Transactional
    public Map<String, Object> createRecall(Map<String, Object> body) {
        String scope = normalizeScope(String.valueOf(body.getOrDefault("scope", "all")));
        String warehouseName = text(body.get("warehouseName"));
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        String batchNo = requireText(body, "batchNo");
        String reason = requireText(body, "reason");
        Long productId = ((Number) product.get("productId")).longValue();
        List<Map<String, Object>> candidates = recallInventoryRows(productId, scope, warehouseName, batchNo);
        if (candidates.isEmpty()) throw new IllegalArgumentException("所选范围内没有该商品批号的可召回库存");
        Map<String, Object> primary = jdbcTemplate.queryForMap("""
                SELECT warehouse_id AS warehouseId, warehouse_name AS warehouseName
                  FROM warehouse
                 WHERE deleted = 0 AND status = 1
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                 ORDER BY warehouse_id LIMIT 1
                """);
        Long primaryWarehouseId = ((Number) primary.get("warehouseId")).longValue();
        String primaryWarehouseName = String.valueOf(primary.get("warehouseName"));
        BigDecimal quantity = candidates.stream()
                .map(row -> (BigDecimal) row.get("totalQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> batchIds = candidates.stream().map(row -> ((Number) row.get("batchId")).longValue()).distinct().toList();
        Long eventBatchId = batchIds.size() == 1 ? batchIds.get(0) : null;
        String recallNo = support.nextNo(RECALL_EVENT);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO recall_event (
                      recall_no, warehouse_name, product_code, product_name, batch_id, affected_qty, status, reason
                    ) VALUES (?, ?, ?, ?, ?, ?, 'isolated', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, recallNo);
            ps.setString(2, primaryWarehouseName);
            ps.setString(3, String.valueOf(product.get("productCode")));
            ps.setString(4, String.valueOf(product.get("productName")));
            if (eventBatchId == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, eventBatchId);
            }
            ps.setBigDecimal(6, quantity);
            ps.setString(7, reason);
            return ps;
        }, keyHolder);
        Long recallId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        unpackRecallPackages(candidates, productId, recallId, reason);
        for (Map<String, Object> candidate : candidates) {
            Long sourceWarehouseId = ((Number) candidate.get("warehouseId")).longValue();
            Long batchId = ((Number) candidate.get("batchId")).longValue();
            BigDecimal totalQty = (BigDecimal) candidate.get("totalQty");
            if (!sourceWarehouseId.equals(primaryWarehouseId)) {
                support.transferSpecificBatch(sourceWarehouseId, primaryWarehouseId, productId, batchId, totalQty,
                        "recall_event", recallId, "召回库存回收到一级库：" + reason);
            }
            support.isolateSpecificBatch(primaryWarehouseId, productId, batchId, totalQty,
                    "recall_event", recallId, reason);
        }
        support.writeAudit("recall", "recall_to_primary_and_isolate", recallId, recallNo,
                "scope=" + scope + ", batch=" + batchNo + ", quantity=" + quantity);
        return Map.of("recallNo", recallNo, "status", "isolated", "affectedQty", quantity,
                "primaryWarehouseName", primaryWarehouseName);
    }

    private List<Map<String, Object>> recallInventoryRows(Long productId, String scope, String warehouseName, String batchNo) {
        List<Object> args = new ArrayList<>();
        args.add(productId);
        StringBuilder where = new StringBuilder(" WHERE stock.product_id = ? AND stock.total_qty > 0");
        switch (scope) {
            case "primary" -> where.append(" AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')");
            case "secondary" -> where.append(" AND w.warehouse_type LIKE '%二级%'");
            case "tertiary" -> where.append(" AND w.warehouse_type LIKE '%三级%'");
            default -> { }
        }
        if (!warehouseName.isBlank()) {
            where.append(" AND w.warehouse_name = ?");
            args.add(warehouseName);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            where.append(" AND (ib.system_batch_no = ? OR ib.production_batch_no = ?)");
            args.add(batchNo.trim());
            args.add(batchNo.trim());
        }
        return jdbcTemplate.queryForList("""
                SELECT w.warehouse_id AS warehouseId, w.warehouse_name AS warehouseName,
                       w.warehouse_type AS warehouseType, p.product_code AS productCode,
                       p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName, p.unit,
                       p.purchase_price AS unitPrice, ib.batch_id AS batchId,
                       ib.system_batch_no AS systemBatchNo, ib.production_batch_no AS productionBatchNo,
                       stock.loose_qty AS looseQty, stock.package_qty AS packageQty,
                       stock.total_qty AS totalQty
                  FROM (
                    SELECT warehouse_id, product_id, batch_id, SUM(loose_qty) AS loose_qty,
                           SUM(package_qty) AS package_qty, SUM(loose_qty + package_qty) AS total_qty
                      FROM (
                        SELECT bal.warehouse_id, bal.product_id, bal.batch_id,
                               GREATEST(SUM(bal.available_qty) - COALESCE(signed_pkg.package_qty, 0), 0) AS loose_qty,
                               CAST(0 AS DECIMAL(18,4)) AS package_qty
                          FROM inventory_balance bal
                          LEFT JOIN (
                            SELECT qpl.warehouse_id, qpl.product_id, src.batch_id, SUM(src.source_qty) AS package_qty
                              FROM quota_package_label qpl
                              JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                             WHERE qpl.status = 'signed'
                             GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                          ) signed_pkg ON signed_pkg.warehouse_id = bal.warehouse_id
                            AND signed_pkg.product_id = bal.product_id AND signed_pkg.batch_id = bal.batch_id
                         WHERE bal.location_id IS NULL AND bal.available_qty > 0
                         GROUP BY bal.warehouse_id, bal.product_id, bal.batch_id, signed_pkg.package_qty
                        UNION ALL
                        SELECT qpl.warehouse_id, qpl.product_id, src.batch_id,
                               CAST(0 AS DECIMAL(18,4)) AS loose_qty, SUM(src.source_qty) AS package_qty
                          FROM quota_package_label qpl
                          JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                         WHERE qpl.status IN ('available', 'signed')
                         GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                      ) converted_stock
                     GROUP BY warehouse_id, product_id, batch_id
                  ) stock
                  JOIN warehouse w ON w.warehouse_id = stock.warehouse_id AND w.deleted = 0 AND w.status = 1
                  JOIN product p ON p.product_id = stock.product_id
                  JOIN inventory_batch ib ON ib.batch_id = stock.batch_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                """ + where + " ORDER BY w.warehouse_type, w.warehouse_name, ib.system_batch_no", args.toArray());
    }

    private void unpackRecallPackages(List<Map<String, Object>> candidates, Long productId, Long recallId, String reason) {
        for (Map<String, Object> candidate : candidates) {
            if (((BigDecimal) candidate.get("packageQty")).signum() <= 0) continue;
            Long warehouseId = ((Number) candidate.get("warehouseId")).longValue();
            Long batchId = ((Number) candidate.get("batchId")).longValue();
            List<Map<String, Object>> labels = jdbcTemplate.queryForList("""
                    SELECT DISTINCT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.status
                      FROM quota_package_label qpl
                      JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                     WHERE qpl.status IN ('available', 'signed') AND qpl.warehouse_id = ?
                       AND qpl.product_id = ? AND src.batch_id = ?
                     FOR UPDATE
                    """, warehouseId, productId, batchId);
            for (Map<String, Object> label : labels) {
                Long labelId = ((Number) label.get("labelId")).longValue();
                String statusBefore = String.valueOf(label.get("status"));
                List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                        SELECT batch_id AS batchId, source_qty AS sourceQty, warehouse_id AS warehouseId
                          FROM quota_package_label_source WHERE label_id = ?
                        """, labelId);
                BigDecimal unpacked = BigDecimal.ZERO;
                for (Map<String, Object> source : sources) {
                    BigDecimal sourceQty = (BigDecimal) source.get("sourceQty");
                    if ("available".equals(statusBefore)) {
                        support.receiveAvailable(warehouseId, productId,
                                ((Number) source.get("batchId")).longValue(), sourceQty,
                                "quota_unpack_in", "recall_event", recallId, "召回自动解包：" + reason);
                    }
                    unpacked = unpacked.add(sourceQty);
                }
                jdbcTemplate.update("UPDATE quota_package_label SET status = 'void', version = version + 1 WHERE label_id = ? AND status IN ('available', 'signed')", labelId);
                jdbcTemplate.update("""
                        UPDATE udi_trace_code utc
                        JOIN quota_package_label qpl ON qpl.trace_code_id = utc.trace_code_id
                           SET utc.current_status = 'unpacked', utc.package_status = 'void',
                               utc.last_event_name = '召回自动解包', utc.last_event_time = NOW()
                         WHERE qpl.label_id = ? AND utc.trace_scope = 'low_value_quota_pack'
                        """, labelId);
                jdbcTemplate.update("""
                        INSERT INTO quota_package_event (event_no, label_id, event_type, status_before, status_after, qty_change, remark)
                        VALUES (?, ?, 'recall_unpack_to_loose', ?, 'void', ?, ?)
                        """, support.nextNo(QUOTA_PACKAGE_EVENT), labelId, statusBefore, unpacked, "召回自动解包：" + reason);
            }
        }
    }

    private static String normalizeScope(String value) {
        String scope = value == null ? "all" : value.trim();
        if (!List.of("all", "primary", "secondary", "tertiary").contains(scope)) {
            throw new IllegalArgumentException("召回范围不正确");
        }
        return scope;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long findWarehouseId(String warehouseName) {
        return jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0 AND status = 1
                """, Long.class, warehouseName);
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        return String.valueOf(value).trim();
    }

    private static BigDecimal requirePositive(Map<String, Object> body, String key) {
        BigDecimal value = new BigDecimal(requireText(body, key));
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
        return value;
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

}
