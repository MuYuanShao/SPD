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

import static com.hospital.spd.common.service.DocumentKind.COLD_CHAIN_EXCEPTION;
import static com.hospital.spd.common.service.DocumentKind.RECALL_EVENT;

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

    /**
     * 召回隔离：选定商品与批次后提交确认，立即将该批次的可用库存转为隔离库存（库存扣减）。
     */
    @Transactional
    public Map<String, Object> createRecall(Map<String, Object> body) {
        String warehouseName = requireText(body, "warehouseName");
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        BigDecimal quantity = requirePositive(body, "quantity");
        String reason = requireText(body, "reason");
        Long warehouseId = findWarehouseId(warehouseName);
        Long productId = ((Number) product.get("productId")).longValue();
        Long batchId = longValue(body.get("batchId"));
        String recallNo = support.nextNo(RECALL_EVENT);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO recall_event (
                      recall_no, warehouse_name, product_code, product_name, batch_id, affected_qty, status, reason
                    ) VALUES (?, ?, ?, ?, ?, ?, 'isolated', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, recallNo);
            ps.setString(2, warehouseName);
            ps.setString(3, String.valueOf(product.get("productCode")));
            ps.setString(4, String.valueOf(product.get("productName")));
            if (batchId == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, batchId);
            }
            ps.setBigDecimal(6, quantity);
            ps.setString(7, reason);
            return ps;
        }, keyHolder);
        Long recallId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        if (batchId != null) {
            support.isolateSpecificBatch(warehouseId, productId, batchId, quantity,
                    "recall_event", recallId, reason);
        } else {
            support.isolateAvailableFifo(warehouseId, productId, quantity,
                    "recall_event", recallId, reason);
        }
        return Map.of("recallNo", recallNo, "status", "isolated");
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
