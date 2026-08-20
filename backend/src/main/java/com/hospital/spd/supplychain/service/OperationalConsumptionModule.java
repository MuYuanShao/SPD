package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
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

    public OperationalConsumptionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system,
                new SettlementPointService(jdbcTemplate, support));
    }

    @Autowired
    public OperationalConsumptionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                        OperatorContextProvider operatorContextProvider,
                                        SettlementPointService settlementPointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.settlementPointService = settlementPointService;
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
        Map<String, Object> consumption = jdbcTemplate.queryForMap("""
                SELECT consumption_id AS consumptionId, warehouse_id AS warehouseId, status
                  FROM department_consumption
                 WHERE consumption_no = ?
                 FOR UPDATE
                """, consumptionNo);
        if (!"confirmed".equals(String.valueOf(consumption.get("status")))) {
            throw new IllegalArgumentException("only confirmed consumption can be reversed");
        }
        Long consumptionId = ((Number) consumption.get("consumptionId")).longValue();
        Long warehouseId = ((Number) consumption.get("warehouseId")).longValue();
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

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice, is_high_value AS highValue
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
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
