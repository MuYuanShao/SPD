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

    @Transactional
    public Map<String, Object> createRecall(Map<String, Object> body) {
        String warehouseName = requireText(body, "warehouseName");
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        BigDecimal quantity = requirePositive(body, "quantity");
        String reason = requireText(body, "reason");
        Long warehouseId = findWarehouseId(warehouseName);
        String recallNo = support.nextNo(RECALL_EVENT);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO recall_event (
                      recall_no, warehouse_name, product_code, product_name, affected_qty, status, reason
                    ) VALUES (?, ?, ?, ?, ?, 'isolated', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, recallNo);
            ps.setString(2, warehouseName);
            ps.setString(3, String.valueOf(product.get("productCode")));
            ps.setString(4, String.valueOf(product.get("productName")));
            ps.setBigDecimal(5, quantity);
            ps.setString(6, reason);
            return ps;
        }, keyHolder);
        Long recallId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        support.isolateAvailableFifo(warehouseId, ((Number) product.get("productId")).longValue(), quantity,
                "recall_event", recallId, reason);
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

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

}
