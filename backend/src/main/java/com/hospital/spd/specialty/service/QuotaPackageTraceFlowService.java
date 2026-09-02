package com.hospital.spd.specialty.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.supplychain.service.SettlementPointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.service.DocumentKind.DEPARTMENT_CONSUMPTION;
import static com.hospital.spd.common.service.DocumentKind.UDI_TRACE_EVENT;

/**
 * Owns the stable trace identity and scan-driven lifecycle of low-value quota packages.
 */
@Service
public class QuotaPackageTraceFlowService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;
    private final SettlementPointService settlementPointService;

    public QuotaPackageTraceFlowService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system, new SettlementPointService(jdbcTemplate, support));
    }

    public QuotaPackageTraceFlowService(JdbcTemplate jdbcTemplate,
                                        SupplyChainSupport support,
                                        OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, support, operatorContextProvider, new SettlementPointService(jdbcTemplate, support));
    }
    @Autowired
    public QuotaPackageTraceFlowService(JdbcTemplate jdbcTemplate,
                                        SupplyChainSupport support,
                                        OperatorContextProvider operatorContextProvider,
                                        SettlementPointService settlementPointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.settlementPointService = settlementPointService;
    }

    @Transactional
    public Long ensureTrace(Long labelId) {
        Map<String, Object> label = label(labelId, false);
        if (label.get("traceCodeId") instanceof Number number) {
            return number.longValue();
        }
        List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList("""
                SELECT ib.system_batch_no AS batchNo, ib.expire_date AS expireDate
                  FROM quota_package_label_source qpls
                  JOIN inventory_batch ib ON ib.batch_id = qpls.batch_id
                 WHERE qpls.label_id = ?
                 ORDER BY qpls.source_id
                 LIMIT 1
                """, labelId);
        Map<String, Object> source = sourceRows.isEmpty() ? Map.of() : sourceRows.get(0);
        String labelNo = String.valueOf(label.get("labelNo"));
        String currentStatus = traceStatus(String.valueOf(label.get("status")));
        jdbcTemplate.update("""
                INSERT IGNORE INTO udi_trace_code (
                  udi_code, unique_code, trace_scope, package_label_no,
                  template_code, template_name, package_quantity, package_unit, package_status,
                  product_code, product_name, spec_model, manufacturer_name, supplier_name,
                  batch_no, expire_date, current_location, current_department,
                  current_status, responsible_person, risk_level, last_event_name, last_event_time
                ) VALUES (?, ?, 'low_value_quota_pack', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'normal',
                          '定数包追溯身份建立', NOW())
                """, "QP-" + labelNo, labelNo, labelNo,
                label.get("templateCode"), label.get("templateName"), label.get("packageQuantity"), label.get("unit"),
                label.get("status"), label.get("productCode"), label.get("productName"), label.get("specModel"),
                label.get("manufacturerName"), label.get("supplierName"), source.get("batchNo"), source.get("expireDate"),
                label.get("warehouseName"), label.get("deptName"), currentStatus,
                operatorContextProvider.current().username());
        Long traceCodeId = jdbcTemplate.queryForObject("""
                SELECT trace_code_id FROM udi_trace_code
                 WHERE trace_scope = 'low_value_quota_pack' AND package_label_no = ?
                """, Long.class, labelNo);
        jdbcTemplate.update("""
                UPDATE quota_package_label SET trace_code_id = ?
                 WHERE label_id = ? AND trace_code_id IS NULL
                """, traceCodeId, labelId);
        writeTraceEvent(traceCodeId, "quota_pack_task", "定数包打包", labelNo,
                String.valueOf(label.get("warehouseName")), text(label.get("deptName")),
                "定数包标签绑定稳定唯一码", 10);
        return traceCodeId;
    }

    @Transactional
    public void transitionLabel(Long labelId, String status, String eventType, String eventName,
                                String bizNo, String locationName, String departmentName,
                                String remark, int sortOrder) {
        Long traceCodeId = ensureTrace(labelId);
        jdbcTemplate.update("""
                UPDATE udi_trace_code
                   SET current_status = ?, package_status = ?,
                       current_location = COALESCE(?, current_location),
                       current_department = COALESCE(?, current_department),
                       responsible_person = ?, last_event_name = ?, last_event_time = NOW()
                 WHERE trace_code_id = ? AND trace_scope = 'low_value_quota_pack'
                """, status, labelStatus(status), blankToNull(locationName), blankToNull(departmentName),
                operatorContextProvider.current().username(), eventName, traceCodeId);
        writeTraceEvent(traceCodeId, eventType, eventName, bizNo, locationName, departmentName, remark, sortOrder);
    }

    public Map<String, Object> signTarget(String code) {
        Map<String, Object> row = findByCode(code, false);
        List<String> deliveryNos = jdbcTemplate.queryForList("""
                SELECT delivery.delivery_no
                  FROM spd_delivery_package_binding binding
                  JOIN spd_delivery_order delivery ON delivery.delivery_id = binding.delivery_id
                 WHERE binding.label_id = ?
                 ORDER BY delivery.delivery_id DESC
                 LIMIT 1
                """, String.class, row.get("labelId"));
        if (deliveryNos.isEmpty()) {
            throw new IllegalArgumentException("定数包未绑定配送单，不能签收入库");
        }
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("deliveryNo", deliveryNos.get(0));
        return result;
    }

    @Transactional
    public void completeSign(Long labelId, String deliveryNo) {
        Map<String, Object> signed = label(labelId, false);
        assertDepartmentScope(signed);
        if ("signed".equals(String.valueOf(signed.get("status")))) {
            return;
        }
        transitionLabel(labelId, "signed", "department_sign", "定数包扫码签收入库",
                deliveryNo, String.valueOf(signed.get("warehouseName")), text(signed.get("deptName")),
                "按定数包唯一码完成科室签收入库", 60);
    }

    @Transactional
    public Map<String, Object> consumeByCode(Map<String, Object> body) {
        String code = required(body, "code");
        Map<String, Object> initial = findByCode(code, false);
        Long labelId = ((Number) initial.get("labelId")).longValue();
        Long traceCodeId = ensureTrace(labelId);
        Map<String, Object> quotaPackage = label(labelId, true);
        assertDepartmentScope(quotaPackage);
        if (!"signed".equals(String.valueOf(quotaPackage.get("status")))) {
            throw new IllegalArgumentException("只有已签收入库的定数包才能扫码消耗");
        }
        String departmentName = text(quotaPackage.get("deptName"));
        if (departmentName == null) {
            throw new IllegalArgumentException("deptName is required");
        }
        Long deptId = ((Number) quotaPackage.get("deptId")).longValue();
        Long warehouseId = ((Number) quotaPackage.get("warehouseId")).longValue();
        Long productId = ((Number) quotaPackage.get("productId")).longValue();
        String consumptionNo = support.nextNo(DEPARTMENT_CONSUMPTION);
        OperatorContext operator = operatorContextProvider.current();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO department_consumption (
                      consumption_no, dept_id, warehouse_id, consumption_type,
                      related_biz_type, related_biz_id, status, consume_by
                    ) VALUES (?, ?, ?, 'quota_package_scan', 'quota_package_label', ?, 'confirmed', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, consumptionNo);
            ps.setLong(2, deptId);
            ps.setLong(3, warehouseId);
            ps.setLong(4, labelId);
            ps.setLong(5, operator.userId());
            return ps;
        }, keyHolder);
        Long consumptionId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                SELECT batch_id AS batchId, source_qty AS sourceQty
                  FROM quota_package_label_source
                 WHERE label_id = ?
                 ORDER BY source_id
                """, labelId);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("定数包缺少来源批次，不能消耗");
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (Map<String, Object> source : sources) {
            BigDecimal sourceQty = (BigDecimal) source.get("sourceQty");
            SupplyChainSupport.InventoryDeduction deduction = support.consumeSpecificBatch(
                    warehouseId, productId, ((Number) source.get("batchId")).longValue(), sourceQty,
                    "quota_package_scan_out", "department_consumption", consumptionId,
                    "low-value quota package consumed by unique code");
            BigDecimal itemAmount = deduction.quantity().multiply(deduction.unitPrice());
            jdbcTemplate.update("""
                    INSERT INTO department_consumption_item
                      (consumption_id, product_id, batch_id, trace_code_id, quantity, unit_price, amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, consumptionId, productId, deduction.batchId(), traceCodeId,
                    deduction.quantity(), deduction.unitPrice(), itemAmount);
            amount = amount.add(itemAmount);
        }
        int updated = jdbcTemplate.update("""
                UPDATE quota_package_label SET status = 'consumed', version = version + 1
                 WHERE label_id = ? AND status = 'signed'
                """, labelId);
        if (updated != 1) {
            throw new IllegalArgumentException("定数包状态已变化，请刷新后重试");
        }
        settlementPointService.generateForConsumption(consumptionId);
        transitionLabel(labelId, "consumed", "department_consumption", "定数包扫码消耗",
                consumptionNo, String.valueOf(quotaPackage.get("warehouseName")), departmentName,
                "按定数包唯一码整包消耗，已进入结算待办", 70);
        return Map.of("code", code, "consumptionNo", consumptionNo, "status", "consumed",
                "quantity", quotaPackage.get("packageQuantity"), "amount", amount, "settlementEligible", true);
    }

    public boolean isQuotaPackageTrace(Long traceCodeId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM udi_trace_code
                 WHERE trace_code_id = ? AND trace_scope = 'low_value_quota_pack'
                """, Integer.class, traceCodeId);
        return count != null && count > 0;
    }

    private void assertDepartmentScope(Map<String, Object> quotaPackage) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData()) return;
        Long targetDeptId = quotaPackage.get("deptId") instanceof Number number ? number.longValue() : null;
        if (targetDeptId == null || operator.deptId() == null || !targetDeptId.equals(operator.deptId())) {
            throw new IllegalArgumentException("只能操作当前登录科室的定数包");
        }
    }

    /** Restore a consumed package to its signed department-stock state during an approved red flush. */
    @Transactional
    public void reverseConsumption(Long labelId, String consumptionNo) {
        Map<String, Object> quotaPackage = label(labelId, true);
        String status = String.valueOf(quotaPackage.get("status"));
        if ("signed".equals(status)) {
            return;
        }
        if (!"consumed".equals(status)) {
            throw new IllegalArgumentException("只有已消耗且未结算的定数包才能反消耗");
        }
        int updated = jdbcTemplate.update("""
                UPDATE quota_package_label SET status = 'signed', version = version + 1
                 WHERE label_id = ? AND status = 'consumed'
                """, labelId);
        if (updated != 1) {
            throw new IllegalArgumentException("定数包状态已变化，请刷新后重试");
        }
        transitionLabel(labelId, "signed", "consumption_reverse", "定数包反消耗恢复",
                consumptionNo, String.valueOf(quotaPackage.get("warehouseName")),
                text(quotaPackage.get("deptName")), "科室消耗红冲，定数包恢复为已签收库存", 75);
    }

    @Transactional
    public void markSettled(Long traceCodeId, String settlementNo) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT qpl.label_id AS labelId, qpl.status
                  FROM quota_package_label qpl
                  JOIN udi_trace_code tc ON tc.trace_code_id = qpl.trace_code_id
                 WHERE tc.trace_code_id = ? AND tc.trace_scope = 'low_value_quota_pack'
                 FOR UPDATE
                """, traceCodeId);
        if ("settled".equals(String.valueOf(row.get("status")))) return;
        if (!"consumed".equals(String.valueOf(row.get("status")))) {
            throw new IllegalArgumentException("只有已消耗的定数包才能结算");
        }
        Long labelId = ((Number) row.get("labelId")).longValue();
        jdbcTemplate.update("UPDATE quota_package_label SET status = 'settled', version = version + 1 WHERE label_id = ?",
                labelId);
        transitionLabel(labelId, "settled", "settlement_confirm", "定数包结算确认",
                settlementNo, null, null, "低值定数包完成供应商结算", 90);
    }

    private Map<String, Object> findByCode(String code, boolean forUpdate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.status,
                       qpl.trace_code_id AS traceCodeId
                  FROM quota_package_label qpl
                  LEFT JOIN udi_trace_code tc ON tc.trace_code_id = qpl.trace_code_id
                 WHERE qpl.label_no = ? OR tc.unique_code = ? OR tc.udi_code = ?
                 LIMIT 1
                """ + (forUpdate ? " FOR UPDATE" : ""), code, code, code);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("低值定数包唯一码不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> label(Long labelId, boolean forUpdate) {
        return jdbcTemplate.queryForMap("""
                SELECT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.trace_code_id AS traceCodeId,
                       qpl.status, qpl.warehouse_id AS warehouseId, qpl.product_id AS productId,
                       qpl.package_quantity AS packageQuantity, qpt.template_code AS templateCode,
                       qpt.template_name AS templateName, p.product_code AS productCode,
                       p.product_name AS productName, p.spec_model AS specModel, p.unit,
                       m.manufacturer_name AS manufacturerName, s.supplier_name AS supplierName,
                       w.warehouse_name AS warehouseName, w.dept_id AS deptId, d.dept_name AS deptName
                  FROM quota_package_label qpl
                  JOIN quota_package_template qpt ON qpt.template_id = qpl.template_id
                  JOIN product p ON p.product_id = qpl.product_id
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                 WHERE qpl.label_id = ?
                """ + (forUpdate ? " FOR UPDATE" : ""), labelId);
    }

    private void writeTraceEvent(Long traceCodeId, String eventType, String eventName, String bizNo,
                                 String locationName, String departmentName, String remark, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO udi_trace_event (
                  trace_code_id, event_no, event_type, event_name, biz_no,
                  location_name, department_name, operator_name, event_time, status, remark, sort_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'done', ?, ?)
                """, traceCodeId, support.nextNo(UDI_TRACE_EVENT), eventType, eventName, bizNo,
                blankToNull(locationName), blankToNull(departmentName),
                operatorContextProvider.current().username(), remark, sortOrder);
    }

    private static String traceStatus(String labelStatus) {
        return switch (labelStatus) {
            case "delivered" -> "delivered";
            case "signed" -> "signed";
            case "consumed" -> "consumed";
            case "settled" -> "settled";
            case "void" -> "unpacked";
            default -> "in_stock";
        };
    }

    private static String labelStatus(String traceStatus) {
        return switch (traceStatus) {
            case "delivery_picked", "delivered" -> "delivered";
            case "signed", "consumed", "settled" -> traceStatus;
            case "unpacked" -> "void";
            default -> "available";
        };
    }

    private static String required(Map<String, Object> body, String key) {
        String value = text(body.get(key));
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
