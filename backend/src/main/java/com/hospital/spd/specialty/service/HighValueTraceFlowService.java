package com.hospital.spd.specialty.service;

import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.service.SettlementPointService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.service.InventoryEventCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.service.DocumentKind.UDI_TRACE_EVENT;

/** Coordinates the stable per-unit identity of high-value consumables across business documents. */
@Service
public class HighValueTraceFlowService {
    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;

    public HighValueTraceFlowService(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                     OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
    }

    public boolean isHighValue(Long productId) {
        Integer value = jdbcTemplate.queryForObject("SELECT is_high_value FROM product WHERE product_id = ?", Integer.class, productId);
        return value != null && value == 1;
    }

    public List<TraceUnit> requireUnits(Object rawCodes, BigDecimal quantity, Long productId, Long warehouseId,
                                        Collection<String> allowedStatuses) {
        int expected = exactUnits(quantity);
        List<String> codes = codes(rawCodes);
        if (codes.size() != expected) {
            throw new IllegalArgumentException("高值耗材唯一码数量必须与业务数量一致");
        }
        String placeholders = String.join(",", codes.stream().map(code -> "?").toList());
        List<Object> args = new ArrayList<>(codes);
        args.add(productId);
        args.add(warehouseId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode,
                       ibtc.batch_id AS batchId, ibtc.current_warehouse_id AS warehouseId,
                       ibtc.lifecycle_status AS lifecycleStatus
                  FROM udi_trace_code tc
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                  JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                 WHERE tc.unique_code IN (%s) AND ib.product_id = ? AND ibtc.current_warehouse_id = ?
                 FOR UPDATE
                """.formatted(placeholders), args.toArray());
        if (rows.size() != codes.size()) {
            throw new IllegalArgumentException("唯一码不存在、商品不匹配或不在当前库房");
        }
        Map<String, Map<String, Object>> byCode = rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> String.valueOf(row.get("uniqueCode")), row -> row));
        List<TraceUnit> units = new ArrayList<>();
        for (String code : codes) {
            Map<String, Object> row = byCode.get(code);
            String status = String.valueOf(row.get("lifecycleStatus"));
            if (!allowedStatuses.contains(status)) {
                throw new IllegalArgumentException("唯一码“" + code + "”当前状态不允许执行该业务：" + status);
            }
            units.add(new TraceUnit(((Number) row.get("traceCodeId")).longValue(), code,
                    ((Number) row.get("batchId")).longValue()));
        }
        return units;
    }

    /** Locks high-value units selected by stable identity during picking. */
    public List<TraceUnit> requireUnitsByIds(List<Long> traceCodeIds, Long productId, Long warehouseId,
                                             Collection<String> allowedStatuses) {
        if (traceCodeIds == null || traceCodeIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个高值唯一码");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(traceCodeIds);
        if (uniqueIds.size() != traceCodeIds.size()) throw new IllegalArgumentException("高值唯一码不能重复选择");
        String placeholders = String.join(",", uniqueIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(uniqueIds);
        args.add(productId);
        args.add(warehouseId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode,
                       ibtc.batch_id AS batchId, ibtc.lifecycle_status AS lifecycleStatus
                  FROM udi_trace_code tc
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                  JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                 WHERE tc.trace_code_id IN (%s) AND ib.product_id = ? AND ibtc.current_warehouse_id = ?
                 FOR UPDATE
                """.formatted(placeholders), args.toArray());
        if (rows.size() != uniqueIds.size()) throw new IllegalArgumentException("唯一码不存在、商品不匹配或不在来源中心库");
        Map<Long, Map<String, Object>> byId = rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> ((Number) row.get("traceCodeId")).longValue(), row -> row));
        List<TraceUnit> units = new ArrayList<>();
        for (Long id : uniqueIds) {
            Map<String, Object> row = byId.get(id);
            String status = String.valueOf(row.get("lifecycleStatus"));
            if (!allowedStatuses.contains(status)) {
                throw new IllegalArgumentException("唯一码“" + row.get("uniqueCode") + "”当前状态不允许拣配：" + status);
            }
            units.add(unit(row));
        }
        return units;
    }

    public void bindRequisition(Long requisitionId, Long itemId, List<TraceUnit> units, String requisitionNo,
                                String departmentName) {
        for (TraceUnit unit : units) {
            jdbcTemplate.update("INSERT INTO department_requisition_trace_code (requisition_id, requisition_item_id, trace_code_id) VALUES (?, ?, ?)",
                    requisitionId, itemId, unit.traceCodeId());
            transition(unit, null, "requisitioned", "department_requisition", "科室申领", requisitionNo,
                    null, departmentName, "高值耗材唯一码已绑定申领单", 30);
        }
    }

    public void releaseRejectedRequisition(Long requisitionId, String requisitionNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode, ibtc.batch_id AS batchId
                  FROM department_requisition_trace_code rtc
                  JOIN udi_trace_code tc ON tc.trace_code_id = rtc.trace_code_id
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = rtc.trace_code_id
                 WHERE rtc.requisition_id = ? AND ibtc.lifecycle_status = 'requisitioned'
                 FOR UPDATE
                """, requisitionId);
        for (Map<String, Object> row : rows) {
            transition(unit(row), null, "in_stock", "requisition_rejected", "申领驳回释放", requisitionNo,
                    null, null, "申领驳回，唯一码恢复可用库存", 31);
        }
    }

    public void bindDelivery(Long deliveryId, List<TraceUnit> units, String deliveryNo, String departmentName) {
        for (TraceUnit unit : units) {
            jdbcTemplate.update("INSERT INTO spd_delivery_trace_code (delivery_id, trace_code_id) VALUES (?, ?)",
                    deliveryId, unit.traceCodeId());
            transition(unit, null, "delivery_picked", "picking_delivery", "高值耗材拣配", deliveryNo,
                    null, departmentName, "高值耗材唯一码已绑定配送单", 40);
        }
    }

    public int signDelivery(Long deliveryId, String deliveryNo, Long productId, Long sourceWarehouseId,
                            Long destinationWarehouseId, String destinationName, String departmentName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode, ibtc.batch_id AS batchId
                  FROM spd_delivery_trace_code dtc
                  JOIN udi_trace_code tc ON tc.trace_code_id = dtc.trace_code_id
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                 WHERE dtc.delivery_id = ? AND ibtc.current_warehouse_id = ? AND ibtc.lifecycle_status = 'delivery_picked'
                 FOR UPDATE
                """, deliveryId, sourceWarehouseId);
        for (Map<String, Object> row : rows) {
            TraceUnit unit = unit(row);
            support.transferSpecificBatch(sourceWarehouseId, destinationWarehouseId, productId, unit.batchId(), BigDecimal.ONE,
                    "spd_delivery_order", deliveryId, "high-value delivery sign transfers exact unique code");
            transition(unit, destinationWarehouseId, "signed", "department_sign", "科室签收", deliveryNo,
                    destinationName, departmentName, "高值耗材按唯一码签收入科室库房", 50);
        }
        return rows.size();
    }

    public BigDecimal consumeUnits(List<TraceUnit> units, Long consumptionId, String consumptionNo,
                                   Long productId, Long warehouseId, String departmentName,
                                   String patientNo, String patientNameMasked) {
        BigDecimal amount = BigDecimal.ZERO;
        for (TraceUnit unit : units) {
            SupplyChainSupport.InventoryDeductionEvent deductionEvent = support.consumeSpecificBatchEvent(
                    warehouseId, productId, unit.batchId(), BigDecimal.ONE,
                    "department_consumption_out", "department_consumption", consumptionId,
                    "high-value department consumption by unique code");
            SupplyChainSupport.InventoryDeduction deduction = deductionEvent.deduction();
            support.linkInventoryEventTraceCodes(deductionEvent.eventId(), List.of(
                    new InventoryEventCommand.TraceLink(unit.traceCodeId(), "high_value_unit", BigDecimal.ONE)));
            BigDecimal itemAmount = deduction.unitPrice();
            jdbcTemplate.update("""
                    INSERT INTO department_consumption_item
                      (consumption_id, product_id, batch_id, trace_code_id, quantity, unit_price, amount)
                    VALUES (?, ?, ?, ?, 1, ?, ?)
                    """, consumptionId, productId, unit.batchId(), unit.traceCodeId(), deduction.unitPrice(), itemAmount);
            if (patientNo != null && !patientNo.isBlank()) {
                insertPatientBinding(unit.traceCodeId(), patientNo, patientNameMasked, departmentName, null);
            }
            transition(unit, warehouseId, "consumed", "department_consumption", "科室消耗", consumptionNo,
                    null, departmentName, patientNo == null || patientNo.isBlank() ? "高值耗材按唯一码消耗" : "高值耗材绑定患者后消耗", 70);
            updatePatient(unit.traceCodeId(), patientNo, patientNameMasked);
            amount = amount.add(itemAmount);
        }
        return amount;
    }

    public Map<String, Object> bindPatient(Map<String, Object> body) {
        String uniqueCode = required(body, "uniqueCode");
        String patientNo = required(body, "patientNo");
        String departmentName = text(body.get("deptName"));
        String locationName = text(body.get("roomName"));
        String patientNameMasked = text(body.get("patientNameMasked"));
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode, ibtc.batch_id AS batchId,
                       ibtc.lifecycle_status AS lifecycleStatus
                  FROM udi_trace_code tc
                  JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                 WHERE tc.unique_code = ? AND tc.trace_scope = 'high_value'
                 FOR UPDATE
                """, uniqueCode);
        String status = String.valueOf(row.get("lifecycleStatus"));
        if (!List.of("signed", "in_stock", "requisitioned").contains(status)) {
            throw new IllegalArgumentException("唯一码当前状态不允许绑定患者：" + status);
        }
        Long traceCodeId = ((Number) row.get("traceCodeId")).longValue();
        insertPatientBinding(traceCodeId, patientNo, patientNameMasked, departmentName, locationName);
        TraceUnit unit = unit(row);
        transition(unit, null, "patient_bound", "patient_binding", "绑定患者", patientNo,
                locationName, departmentName, "高值耗材唯一码绑定患者", 60);
        updatePatient(traceCodeId, patientNo, patientNameMasked);
        return Map.of("uniqueCode", uniqueCode, "patientNo", patientNo, "status", "patient_bound");
    }

    public void markSettled(Long traceCodeId, String settlementNo) {
        if (traceCodeId == null) return;
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT tc.trace_code_id AS traceCodeId, tc.unique_code AS uniqueCode, ibtc.batch_id AS batchId
                  FROM udi_trace_code tc JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = tc.trace_code_id
                 WHERE tc.trace_code_id = ? FOR UPDATE
                """, traceCodeId);
        transition(unit(row), null, "settled", "settlement_confirm", "结算确认", settlementNo,
                null, null, "高值耗材唯一码完成供应商结算", 90);
    }

    private void transition(TraceUnit unit, Long warehouseId, String status, String eventType, String eventName,
                            String bizNo, String locationName, String departmentName, String remark, int sortOrder) {
        jdbcTemplate.update("""
                UPDATE inventory_batch_trace_code
                   SET current_warehouse_id = COALESCE(?, current_warehouse_id), lifecycle_status = ?
                 WHERE trace_code_id = ?
                """, warehouseId, status, unit.traceCodeId());
        jdbcTemplate.update("""
                UPDATE udi_trace_code SET current_status = ?, current_location = COALESCE(?, current_location),
                       current_department = COALESCE(?, current_department), responsible_person = ?,
                       last_event_name = ?, last_event_time = NOW() WHERE trace_code_id = ?
                """, status, locationName, departmentName, operatorContextProvider.current().username(), eventName, unit.traceCodeId());
        jdbcTemplate.update("""
                INSERT INTO udi_trace_event
                  (trace_code_id, event_no, event_type, event_name, biz_no, location_name, department_name,
                   operator_name, event_time, status, remark, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'done', ?, ?)
                """, unit.traceCodeId(), support.nextNo(UDI_TRACE_EVENT), eventType, eventName, bizNo,
                locationName, departmentName, operatorContextProvider.current().username(), remark, sortOrder);
    }

    private void insertPatientBinding(Long traceCodeId, String patientNo, String patientNameMasked,
                                      String departmentName, String locationName) {
        jdbcTemplate.update("""
                INSERT INTO udi_trace_patient_binding
                  (trace_code_id, patient_no, patient_name_masked, department_name, location_name, binding_status)
                VALUES (?, ?, ?, ?, ?, 'bound')
                """, traceCodeId, patientNo, patientNameMasked, departmentName, locationName);
    }

    private void updatePatient(Long traceCodeId, String patientNo, String patientNameMasked) {
        if (patientNo == null || patientNo.isBlank()) return;
        jdbcTemplate.update("UPDATE udi_trace_code SET patient_no = ?, patient_name_masked = ? WHERE trace_code_id = ?",
                patientNo, patientNameMasked, traceCodeId);
    }

    private static TraceUnit unit(Map<String, Object> row) {
        return new TraceUnit(((Number) row.get("traceCodeId")).longValue(), String.valueOf(row.get("uniqueCode")),
                ((Number) row.get("batchId")).longValue());
    }

    private static int exactUnits(BigDecimal quantity) {
        try {
            return quantity.intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("高值耗材业务数量必须为整数");
        }
    }

    private static List<String> codes(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> addCodes(values, value));
        } else {
            addCodes(values, raw);
        }
        return List.copyOf(values);
    }

    private static void addCodes(LinkedHashSet<String> target, Object raw) {
        if (raw == null) return;
        for (String value : String.valueOf(raw).split("[,，\\s]+")) {
            if (!value.isBlank()) target.add(value.trim());
        }
    }

    private static String required(Map<String, Object> body, String key) {
        String value = text(body.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    public record TraceUnit(Long traceCodeId, String uniqueCode, Long batchId) {}
}
