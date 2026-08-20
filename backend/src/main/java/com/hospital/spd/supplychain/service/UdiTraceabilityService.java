package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.SqlHelper.appendLike;

/**
 * Read model for UDI and unique-code traceability.
 */
@Service
public class UdiTraceabilityService {

    private final JdbcTemplate jdbcTemplate;

    public UdiTraceabilityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary(Map<String, String> params) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = baseWhere(params, args);
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS totalCodes,
                       SUM(CASE WHEN tc.trace_scope = 'high_value' THEN 1 ELSE 0 END) AS highValueCodes,
                       SUM(CASE WHEN tc.trace_scope = 'low_value_quota_pack' THEN 1 ELSE 0 END) AS quotaPackageCodes,
                       SUM(CASE WHEN tc.risk_level = 'exception' OR tc.current_status = 'isolated' THEN 1 ELSE 0 END) AS exceptionCodes,
                       SUM(CASE WHEN tc.current_status IN ('in_stock', 'putaway') THEN 1 ELSE 0 END) AS inStockCodes,
                       SUM(CASE WHEN tc.current_status = 'consumed' THEN 1 ELSE 0 END) AS consumedCodes
                  FROM udi_trace_code tc
                """ + where, args.toArray());
    }

    public Map<String, Object> records(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = baseWhere(params, args);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM udi_trace_code tc " + where,
                Long.class,
                args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tc.trace_code_id AS traceCodeId,
                       tc.udi_code AS udiCode,
                       tc.unique_code AS uniqueCode,
                       tc.trace_scope AS traceScope,
                       tc.package_label_no AS packageLabelNo,
                       tc.template_code AS templateCode,
                       tc.template_name AS templateName,
                       tc.package_quantity AS packageQuantity,
                       tc.package_unit AS packageUnit,
                       tc.package_status AS packageStatus,
                       tc.product_code AS productCode,
                       tc.product_name AS productName,
                       tc.spec_model AS specModel,
                       tc.manufacturer_name AS manufacturerName,
                       tc.supplier_name AS supplierName,
                       tc.batch_no AS batchNo,
                       DATE_FORMAT(tc.expire_date, '%Y-%m-%d') AS expireDate,
                       tc.current_location AS currentLocation,
                       tc.current_department AS currentDepartment,
                       tc.current_status AS currentStatus,
                       tc.responsible_person AS responsiblePerson,
                       tc.patient_no AS patientNo,
                       tc.patient_name_masked AS patientNameMasked,
                       tc.risk_level AS riskLevel,
                       tc.last_event_name AS lastEventName,
                       DATE_FORMAT(tc.last_event_time, '%Y-%m-%d %H:%i') AS lastEventTime
                  FROM udi_trace_code tc
                """ + where + " ORDER BY tc.last_event_time DESC, tc.trace_code_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq, summary(params));
    }

    public Map<String, Object> detail(String code) {
        Map<String, Object> record = findRecord(code);
        Long traceCodeId = ((Number) record.get("traceCodeId")).longValue();
        List<Map<String, Object>> timeline = jdbcTemplate.queryForList("""
                SELECT te.trace_event_id AS traceEventId,
                       te.event_no AS eventNo,
                       te.event_type AS eventType,
                       te.event_name AS eventName,
                       te.biz_no AS bizNo,
                       te.location_name AS locationName,
                       te.department_name AS departmentName,
                       te.operator_name AS operatorName,
                       DATE_FORMAT(te.event_time, '%Y-%m-%d %H:%i') AS eventTime,
                       te.status,
                       te.remark,
                       te.sort_order AS sortOrder
                  FROM udi_trace_event te
                 WHERE te.trace_code_id = ?
                 ORDER BY te.sort_order ASC, te.event_time ASC
                """, traceCodeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("timeline", timeline);
        return result;
    }

    public Map<String, Object> options() {
        return Map.of(
                "statuses", List.of(
                        Map.of("value", "in_stock", "label", "在库"),
                        Map.of("value", "requisitioned", "label", "已申领"),
                        Map.of("value", "delivery_picked", "label", "已拣配"),
                        Map.of("value", "patient_bound", "label", "已绑定患者"),
                        Map.of("value", "delivered", "label", "已配送"),
                        Map.of("value", "signed", "label", "科室签收"),
                        Map.of("value", "consumed", "label", "已消耗"),
                        Map.of("value", "settled", "label", "已结算"),
                        Map.of("value", "isolated", "label", "隔离")
                ),
                "traceScopes", List.of(
                        Map.of("value", "high_value", "label", "高值耗材"),
                        Map.of("value", "low_value_quota_pack", "label", "低值定数包")
                ),
                "eventTypes", List.of(
                        Map.of("value", "supplier_ship", "label", "供应商发货"),
                        Map.of("value", "center_inbound", "label", "中心库入库"),
                        Map.of("value", "shelf_putaway", "label", "上架定位"),
                        Map.of("value", "department_requisition", "label", "科室申领"),
                        Map.of("value", "picking_delivery", "label", "拣货配送"),
                        Map.of("value", "department_sign", "label", "科室签收"),
                        Map.of("value", "patient_binding", "label", "绑定患者"),
                        Map.of("value", "settlement_confirm", "label", "结算确认"),
                        Map.of("value", "requisition_rejected", "label", "申领驳回释放"),
                        Map.of("value", "department_consumption", "label", "科室消耗"),
                        Map.of("value", "high_value_billing", "label", "手麻计费回传"),
                        Map.of("value", "quota_pack_task", "label", "定数包打包"),
                        Map.of("value", "quota_pack_print", "label", "标签打印"),
                        Map.of("value", "recall_isolation", "label", "召回隔离")
                )
        );
    }

    private Map<String, Object> findRecord(String code) {
        try {
            return jdbcTemplate.queryForMap("""
                    SELECT tc.trace_code_id AS traceCodeId,
                           tc.udi_code AS udiCode,
                           tc.unique_code AS uniqueCode,
                           tc.trace_scope AS traceScope,
                           tc.package_label_no AS packageLabelNo,
                           tc.template_code AS templateCode,
                           tc.template_name AS templateName,
                           tc.package_quantity AS packageQuantity,
                           tc.package_unit AS packageUnit,
                           tc.package_status AS packageStatus,
                           tc.product_code AS productCode,
                           tc.product_name AS productName,
                           tc.spec_model AS specModel,
                           tc.manufacturer_name AS manufacturerName,
                           tc.supplier_name AS supplierName,
                           tc.batch_no AS batchNo,
                           DATE_FORMAT(tc.expire_date, '%Y-%m-%d') AS expireDate,
                           tc.current_location AS currentLocation,
                           tc.current_department AS currentDepartment,
                           tc.current_status AS currentStatus,
                           tc.responsible_person AS responsiblePerson,
                           tc.patient_no AS patientNo,
                           tc.patient_name_masked AS patientNameMasked,
                           tc.risk_level AS riskLevel,
                           tc.last_event_name AS lastEventName,
                           DATE_FORMAT(tc.last_event_time, '%Y-%m-%d %H:%i') AS lastEventTime
                      FROM udi_trace_code tc
                     WHERE tc.unique_code = ? OR tc.udi_code = ? OR tc.package_label_no = ?
                     LIMIT 1
                    """, code, code, code);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("追溯码不存在");
        }
    }

    private StringBuilder baseWhere(Map<String, String> params, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        String keyword = params.get("keyword");
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                     AND (tc.udi_code LIKE ? OR tc.unique_code LIKE ? OR tc.package_label_no LIKE ?
                          OR tc.template_code LIKE ? OR tc.template_name LIKE ? OR tc.product_code LIKE ?
                          OR tc.product_name LIKE ? OR tc.batch_no LIKE ?)
                    """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        appendLike(where, args, "tc.trace_scope", params.get("traceScope"));
        appendLike(where, args, "tc.current_status", params.get("status"));
        appendLike(where, args, "tc.current_department", params.get("department"));
        appendLike(where, args, "tc.batch_no", params.get("batchNo"));

        String eventType = params.get("eventType");
        if (eventType != null && !eventType.isBlank()) {
            where.append("""
                     AND EXISTS (
                       SELECT 1 FROM udi_trace_event te
                        WHERE te.trace_code_id = tc.trace_code_id
                          AND te.event_type = ?
                     )
                    """);
            args.add(eventType.trim());
        }
        return where;
    }
}
