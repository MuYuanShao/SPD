package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.InventoryEventCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.service.DocumentKind.HIGH_VALUE_CHARGE;

/**
 * Owns high-value consumable charging inside Operational Closure.
 */
@Service
public class OperationalHighValueModule {

    private static final String BILLING_EVENT_NAME = "\u624b\u9ebb\u8ba1\u8d39\u56de\u4f20";
    private static final String BILLING_EVENT_REMARK =
            "\u624b\u9ebb/\u624b\u672f\u7cfb\u7edf\u56de\u4f20\u60a3\u8005\u4e0e\u8017\u6750\u8ba1\u8d39\u6570\u636e\uff0cSPD \u636e\u6b64\u6263\u51cf\u5e93\u5b58\u5e76\u8bb0\u5f55\u8ffd\u6eaf";

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final HighValueTraceFlowService traceFlowService;
    private final SettlementPointService settlementPointService;

    public OperationalHighValueModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system),
                new SettlementPointService(jdbcTemplate, support));
    }

    public OperationalHighValueModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                      HighValueTraceFlowService traceFlowService) {
        this(jdbcTemplate, support, traceFlowService, new SettlementPointService(jdbcTemplate, support));
    }
    @Autowired
    public OperationalHighValueModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                      HighValueTraceFlowService traceFlowService,
                                      SettlementPointService settlementPointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.traceFlowService = traceFlowService;
        this.settlementPointService = settlementPointService;
    }

    public Map<String, Object> highValueCharge(Map<String, Object> body) {
        Map<String, Object> product = findProduct(requiredText(body, "productCode"));
        BigDecimal quantity = requiredPositive(body, "quantity");
        BigDecimal amount = quantity.multiply((BigDecimal) product.get("purchasePrice"));
        String chargeNo = support.nextNo(HIGH_VALUE_CHARGE);
        jdbcTemplate.update("""
                INSERT INTO high_value_charge (
                  charge_no, dept_name, patient_no, product_code, product_name, quantity, amount, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'charge_ready')
                """, chargeNo, requiredText(body, "deptName"), requiredText(body, "patientNo"),
                product.get("productCode"), product.get("productName"), quantity, amount);
        return Map.of("chargeNo", chargeNo, "amount", amount);
    }

    @Transactional
    public Map<String, Object> bindPatient(Map<String, Object> body) {
        return traceFlowService.bindPatient(body);
    }

    /**
     * AIMS/OR billing callback is the authoritative high-value use trigger for SPD.
     * SPD deducts inventory and writes UDI trace only after this message arrives.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> receiveBillingCallback(Map<String, Object> body) {
        String externalChargeNo = text(body, "externalChargeNo", text(body, "eventId", ""));
        if (externalChargeNo.isBlank()) {
            throw new IllegalArgumentException("externalChargeNo or eventId is required");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                SELECT charge_no AS chargeNo, status
                  FROM high_value_charge
                 WHERE external_charge_no = ?
                 LIMIT 1
                """, externalChargeNo);
        if (!existing.isEmpty()) {
            return Map.of(
                    "chargeNo", existing.get(0).get("chargeNo"),
                    "status", existing.get(0).get("status"),
                    "idempotent", true);
        }

        Map<String, Object> trace = findTrace(body);
        String productCode = text(body, "productCode", text(trace, "productCode", ""));
        if (productCode.isBlank()) throw new IllegalArgumentException("productCode is required");
        if (!trace.isEmpty() && !productCode.equals(String.valueOf(trace.get("productCode")))) {
            throw new IllegalArgumentException("商品编码与高值耗材追溯记录不一致");
        }
        Map<String, Object> product = findProduct(productCode);
        BigDecimal quantity = requiredPositive(body, "quantity");
        if (trace.isEmpty() || quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("高值耗材计费回传必须提供一个唯一码，且数量必须为 1");
        }
        BigDecimal amount = decimal(body, "amount", quantity.multiply((BigDecimal) product.get("purchasePrice")));
        String deptName = text(body, "deptName", text(trace, "currentDepartment", ""));
        String patientNo = text(body, "patientNo", text(trace, "patientNo", ""));
        if (deptName.isBlank()) throw new IllegalArgumentException("deptName is required");
        if (patientNo.isBlank()) throw new IllegalArgumentException("patientNo is required");
        String patientNameMasked = text(body, "patientNameMasked", text(trace, "patientNameMasked", ""));
        String roomName = text(body, "roomName", text(trace, "currentLocation", ""));
        String operationNo = text(body, "operationNo", "");
        String udiCode = text(body, "udiCode", text(trace, "udiCode", ""));
        String uniqueCode = text(body, "uniqueCode", text(trace, "uniqueCode", ""));
        Long productId = ((Number) product.get("productId")).longValue();
        Long warehouseId = resolveWarehouseId(body, trace, productId, quantity);

        String chargeNo = support.nextNo(HIGH_VALUE_CHARGE);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO high_value_charge (
                          charge_no, source_system, external_charge_no, operation_no, udi_code, unique_code, trace_code_id,
                          dept_name, patient_no, product_code, product_name, quantity, amount, status, charge_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'charged', NOW())
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, chargeNo);
                ps.setString(2, text(body, "sourceSystem", "AIMS"));
                ps.setString(3, externalChargeNo);
                ps.setString(4, nullIfBlank(operationNo));
                ps.setString(5, nullIfBlank(udiCode));
                ps.setString(6, nullIfBlank(uniqueCode));
                if (trace.isEmpty()) ps.setNull(7, java.sql.Types.BIGINT);
                else ps.setLong(7, ((Number) trace.get("traceCodeId")).longValue());
                ps.setString(8, deptName);
                ps.setString(9, patientNo);
                ps.setString(10, String.valueOf(product.get("productCode")));
                ps.setString(11, String.valueOf(product.get("productName")));
                ps.setBigDecimal(12, quantity);
                ps.setBigDecimal(13, amount);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            List<Map<String, Object>> concurrent = findExistingCharge(externalChargeNo);
            if (!concurrent.isEmpty()) {
                return idempotentResult(concurrent.get(0));
            }
            throw ex;
        }
        Long chargeId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        SupplyChainSupport.InventoryDeductionEvent deductionEvent = support.consumeSpecificBatchEvent(
                warehouseId, productId, ((Number) trace.get("batchId")).longValue(), BigDecimal.ONE,
                "high_value_billing_deduct", "high_value_charge", chargeId, "billing callback deducts exact unique code");
        support.linkInventoryEventTraceCodes(deductionEvent.eventId(), List.of(
                new InventoryEventCommand.TraceLink(((Number) trace.get("traceCodeId")).longValue(),
                        "high_value_unit", BigDecimal.ONE)));

        if (!trace.isEmpty()) {
            writeBillingTrace(body, trace, externalChargeNo, deptName, patientNo, patientNameMasked, roomName);
        }

        settlementPointService.generateForHighValueCharge(chargeId);
        support.writeAudit("high_value_charge", "billing_callback", chargeId, chargeNo,
                "AIMS/OR billing callback confirmed high-value consumption");
        return Map.of("chargeNo", chargeNo, "amount", amount, "status", "charged", "idempotent", false);
    }

    private void writeBillingTrace(Map<String, Object> body, Map<String, Object> trace, String externalChargeNo,
                                   String deptName, String patientNo, String patientNameMasked, String roomName) {
        Long traceCodeId = ((Number) trace.get("traceCodeId")).longValue();
        String eventNo = support.nextNo("UT", "udi_trace_event", "event_no", 6);
        jdbcTemplate.update("""
                INSERT INTO udi_trace_event (
                  trace_code_id, event_no, event_type, event_name, biz_no, location_name, department_name,
                  operator_name, event_time, status, remark, sort_order
                ) VALUES (?, ?, 'high_value_billing', ?, ?, ?, ?, ?, NOW(), 'done', ?, 50)
                """, traceCodeId, eventNo, BILLING_EVENT_NAME, externalChargeNo, nullIfBlank(roomName), deptName,
                text(body, "operatorName", "AIMS/OR"), BILLING_EVENT_REMARK);
        jdbcTemplate.update("""
                UPDATE udi_trace_code
                   SET current_status = 'consumed',
                       current_location = COALESCE(?, current_location),
                       current_department = ?,
                       patient_no = ?,
                       patient_name_masked = ?,
                       last_event_name = ?,
                       last_event_time = NOW()
                 WHERE trace_code_id = ?
                """, nullIfBlank(roomName), deptName, patientNo, nullIfBlank(patientNameMasked),
                BILLING_EVENT_NAME, traceCodeId);
        jdbcTemplate.update("UPDATE inventory_batch_trace_code SET lifecycle_status = 'consumed' WHERE trace_code_id = ?",
                traceCodeId);
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

    private Map<String, Object> findTrace(Map<String, Object> body) {
        String uniqueCode = text(body, "uniqueCode", "");
        String udiCode = text(body, "udiCode", "");
        if (uniqueCode.isBlank() && udiCode.isBlank()) {
            return Map.of();
        }
        String predicate;
        List<Map<String, Object>> rows;
        if (!uniqueCode.isBlank() && !udiCode.isBlank()) {
            predicate = "unique_code = ? AND udi_code = ?";
            rows = jdbcTemplate.queryForList(traceSql(predicate), uniqueCode, udiCode);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("UDI 与唯一码未指向同一高值耗材");
            }
        } else if (!uniqueCode.isBlank()) {
            predicate = "unique_code = ?";
            rows = jdbcTemplate.queryForList(traceSql(predicate), uniqueCode);
        } else {
            predicate = "udi_code = ?";
            rows = jdbcTemplate.queryForList(traceSql(predicate), udiCode);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("高值耗材追溯码不存在");
        }
        return rows.get(0);
    }

    private static String traceSql(String predicate) {
        return """
                SELECT tc.trace_code_id AS traceCodeId, tc.udi_code AS udiCode, tc.unique_code AS uniqueCode,
                       tc.product_code AS productCode, tc.current_location AS currentLocation,
                       tc.current_department AS currentDepartment, tc.patient_no AS patientNo,
                       tc.patient_name_masked AS patientNameMasked, ibtc.batch_id AS batchId,
                       ibtc.current_warehouse_id AS currentWarehouseId, ibtc.lifecycle_status AS lifecycleStatus
                  FROM udi_trace_code tc
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                 WHERE tc.%s AND tc.trace_scope = 'high_value'
                   AND ibtc.lifecycle_status IN ('signed', 'patient_bound', 'in_stock', 'requisitioned')
                 LIMIT 1
                """.formatted(predicate);
    }

    private List<Map<String, Object>> findExistingCharge(String externalChargeNo) {
        return jdbcTemplate.queryForList("""
                SELECT charge_no AS chargeNo, status
                  FROM high_value_charge
                 WHERE external_charge_no = ?
                 LIMIT 1
                """, externalChargeNo);
    }

    private static Map<String, Object> idempotentResult(Map<String, Object> existing) {
        return Map.of(
                "chargeNo", existing.get("chargeNo"),
                "status", existing.get("status"),
                "idempotent", true);
    }

    private Long resolveWarehouseId(Map<String, Object> body, Map<String, Object> trace, Long productId, BigDecimal quantity) {
        if (trace.get("currentWarehouseId") instanceof Number warehouseId) {
            return warehouseId.longValue();
        }
        String warehouseName = text(body, "warehouseName", text(trace, "currentLocation", ""));
        if (!warehouseName.isBlank()) {
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT warehouse_id FROM warehouse
                     WHERE warehouse_name = ? AND deleted = 0 AND status = 1
                     LIMIT 1
                    """, Long.class, warehouseName);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        String deptName = text(body, "deptName", text(trace, "currentDepartment", ""));
        if (!deptName.isBlank()) {
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT w.warehouse_id
                      FROM warehouse w
                      JOIN sys_dept d ON d.dept_id = w.dept_id
                     WHERE d.dept_name = ? AND w.deleted = 0 AND w.status = 1
                     ORDER BY w.warehouse_id
                     LIMIT 1
                    """, Long.class, deptName);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        throw new IllegalArgumentException("warehouseName or a department warehouse mapping is required");
    }

    private String firstDeptName() {
        return jdbcTemplate.queryForObject("SELECT dept_name FROM sys_dept WHERE deleted = 0 AND status = 1 ORDER BY dept_id LIMIT 1", String.class);
    }

    private String firstProductCode() {
        return jdbcTemplate.queryForObject("SELECT product_code FROM product WHERE deleted = 0 AND status = 1 ORDER BY product_id DESC LIMIT 1", String.class);
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return new BigDecimal(String.valueOf(value));
    }

    private static String requiredText(Map<String, Object> body, String key) {
        String value = text(body, key, "");
        if (value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static BigDecimal requiredPositive(Map<String, Object> body, String key) {
        BigDecimal value = decimal(body, key, null);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
        return value;
    }
}
