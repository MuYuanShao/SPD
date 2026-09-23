package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only projections for the operational-closure workbench and its paged business lists.
 */
@Service
public class OperationalClosureReadModel {

    private final JdbcTemplate jdbcTemplate;
    private final DepartmentRequisitionAccessService requisitionAccess;

    public OperationalClosureReadModel(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new DepartmentRequisitionAccessService(jdbcTemplate, OperatorContext::system,
                new DataScopeService(OperatorContext::system)));
    }

    @Autowired
    public OperationalClosureReadModel(JdbcTemplate jdbcTemplate,
                                       DepartmentRequisitionAccessService requisitionAccess) {
        this.jdbcTemplate = jdbcTemplate;
        this.requisitionAccess = requisitionAccess;
    }

    public Map<String, Object> overview() {
        Map<String, Object> summary = Map.of(
                "shortageTasks", count("shortage_replenishment_task", "status <> 'closed'"),
                "pendingDeliveries", count("spd_delivery_order", "status <> 'signed'"),
                "consumptions", count("department_consumption", "status <> 'reversed'"),
                "settlements", count("settlement_bill", "1 = 1"),
                "pdaRecords", count("pda_offline_record", "1 = 1"),
                "riskEvents", count("cold_chain_exception", "1 = 1") + count("recall_event", "1 = 1")
        );
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT event_no AS eventNo, event_type AS eventType, qty_change AS qtyChange,
                       remark, DATE_FORMAT(event_time, '%Y-%m-%d %H:%i') AS eventTime
                  FROM inventory_event
                 WHERE event_type IN ('delivery_out', 'delivery_return', 'dept_consumption', 'reverse_consumption', 'recall_isolate')
                 ORDER BY event_time DESC
                 LIMIT 20
                """);
        return Map.of("summary", summary, "events", events);
    }

    public Map<String, Object> options() {
        StringBuilder deptWhere = new StringBuilder(" WHERE deleted = 0 AND status = 1");
        List<Object> deptArgs = new ArrayList<>();
        requisitionAccess.appendScope(deptWhere, deptArgs, "dept_id", null);
        String departmentsSql = """
                SELECT dept_code AS deptCode, dept_name AS deptName
                  FROM sys_dept
                """ + deptWhere + " ORDER BY dept_id LIMIT 80";
        List<Map<String, Object>> departments = deptArgs.isEmpty()
                ? jdbcTemplate.queryForList(departmentsSql)
                : jdbcTemplate.queryForList(departmentsSql, deptArgs.toArray());
        List<Map<String, Object>> warehouses = jdbcTemplate.queryForList("""
                SELECT warehouse_name AS warehouseName, warehouse_type AS warehouseType
                  FROM warehouse WHERE deleted = 0 AND status = 1 ORDER BY warehouse_id LIMIT 80
                """);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       p.unit, p.purchase_price AS purchasePrice,
                       CASE p.is_high_value WHEN 1 THEN 'yes' ELSE 'no' END AS highValue,
                       CASE p.is_cold_chain WHEN 1 THEN 'yes' ELSE 'no' END AS coldChain
                  FROM product p
                 WHERE p.deleted = 0 AND p.status = 1
                 ORDER BY p.product_id DESC
                 LIMIT 200
                """);
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT w.warehouse_name AS warehouseName, p.product_code AS productCode, p.product_name AS productName,
                       ib.system_batch_no AS systemBatchNo, ib.batch_unit_price AS batchUnitPrice,
                       bal.available_qty AS availableQty
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.available_qty > 0
                 ORDER BY bal.update_time DESC
                 LIMIT 120
                """);
        return Map.of("departments", departments, "warehouses", warehouses, "products", products, "balances", balances);
    }

    /** Lightweight, data-scoped options for the department requisition entry page. */
    public Map<String, Object> requisitionOptions() {
        requisitionAccess.requirePermission("department-requisition:read");
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 AND status = 1");
        List<Object> args = new ArrayList<>();
        requisitionAccess.appendScope(where, args, "dept_id", null);
        List<Map<String, Object>> departments = jdbcTemplate.queryForList("""
                SELECT dept_code AS deptCode, dept_name AS deptName
                  FROM sys_dept
                """ + where + " ORDER BY sort_order, dept_id", args.toArray());
        return Map.of("departments", departments, "warehouses", List.of(), "products", List.of(), "balances", List.of());
    }

    /** Returns only enabled warehouses currently owned by the authorized department. */
    public List<Map<String, Object>> requisitionWarehouses(String deptCode) {
        requisitionAccess.requirePermission("department-requisition:read");
        Map<String, Object> department = requisitionAccess.resolveDepartment(deptCode, null);
        Long deptId = ((Number) department.get("deptId")).longValue();
        return jdbcTemplate.queryForList("""
                SELECT warehouse_id AS warehouseId, warehouse_code AS code, warehouse_name AS name,
                       warehouse_type AS type, campus_name AS campus, '启用' AS status,
                       ? AS relatedDepartment, 1 AS selected
                  FROM warehouse
                 WHERE dept_id = ? AND deleted = 0 AND status = 1
                 ORDER BY warehouse_id
                """, department.get("deptName"), deptId);
    }

    public Map<String, Object> list(String type, Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);

        return switch (type) {
            case "shortage" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT task_no AS bizNo, dept_name AS deptName, product_code AS productCode, product_name AS productName,
                           min_qty AS minQty, current_qty AS currentQty, replenish_qty AS quantity,
                           period_days AS periodDays, period_issue_qty AS periodIssueQty,
                           avg_daily_issue_qty AS avgDailyIssueQty,
                           formula_replenish_qty AS formulaReplenishQty,
                           manual_adjusted AS manualAdjusted, formula_text AS formulaText, status,
                           DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM shortage_replenishment_task ORDER BY create_time DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()), countLong("shortage_replenishment_task", "1 = 1"), pageReq);
            case "delivery" -> deliveryRecords(params, pageReq);
            case "requisition" -> requisitionRecords(params, pageReq);
            case "consumption" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT dc.consumption_no AS bizNo, sd.dept_name AS deptName, p.product_code AS productCode,
                           p.product_name AS productName, dci.quantity, dci.unit_price AS unitPrice, dci.amount,
                           dc.status, DATE_FORMAT(dc.consume_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM department_consumption dc
                      JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                      JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                      JOIN product p ON p.product_id = dci.product_id
                     ORDER BY dc.consume_time DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()), countJoinedRows("""
                    SELECT COUNT(*)
                      FROM department_consumption dc
                      JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                    """), pageReq);
            case "red-flush" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT flush_no AS bizNo, source_consumption_no AS sourceNo,
                           flush_type AS flushType, status, remark,
                           DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM consumption_red_flush
                     ORDER BY create_time DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()), countLong("consumption_red_flush", "1 = 1"), pageReq);
            case "settlement" -> settlementDetails(pageReq);
            case "pda" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT record_no AS bizNo, device_no AS deviceNo, operation_type AS operationType,
                           payload, status, DATE_FORMAT(upload_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM pda_offline_record ORDER BY upload_time DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()), countLong("pda_offline_record", "1 = 1"), pageReq);
            case "cold-chain" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT event_no AS bizNo, warehouse_name AS warehouseName, product_code AS productCode,
                           product_name AS productName, temperature, severity, status,
                           DATE_FORMAT(event_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM cold_chain_exception
                     ORDER BY event_time DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()), countLong("cold_chain_exception", "1 = 1"), pageReq);
            case "risk" -> PageResponse.of(jdbcTemplate.queryForList("""
                    SELECT event_no AS bizNo, '冷链异常' AS eventType, product_code AS productCode, product_name AS productName,
                           warehouse_name AS warehouseName, severity AS status, temperature AS quantity,
                           DATE_FORMAT(event_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM cold_chain_exception
                    UNION ALL
                    SELECT recall_no AS bizNo, CASE business_type WHEN 'isolate' THEN '库存隔离' ELSE '产品召回' END AS eventType, product_code AS productCode, product_name AS productName,
                           warehouse_name AS warehouseName, status, affected_qty AS quantity,
                           DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                      FROM recall_event
                     ORDER BY createTime DESC LIMIT ? OFFSET ?
                    """, pageReq.size(), pageReq.offset()),
                    countLong("cold_chain_exception", "1 = 1") + countLong("recall_event", "1 = 1"),
                    pageReq);
            case "high-value" -> highValueChargeDetails(params, pageReq);
            default -> PageResponse.of(List.of(), 0, pageReq);
        };
    }

    public Map<String, Object> requisitionDetails(String requisitionNo) {
        StringBuilder where = new StringBuilder(" WHERE dr.requisition_no = ?");
        List<Object> args = new ArrayList<>();
        args.add(requisitionNo);
        requisitionAccess.appendScope(where, args, "dr.dept_id", "dr.applicant_id");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode,
                       p.product_name AS productName,
                       p.spec_model AS specModel,
                       p.spec_model AS model,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(NULLIF(dri.unit_price, 0), p.purchase_price) AS unitPrice,
                       dri.quantity AS applyQuantity,
                       dri.item_type AS itemType,
                       (SELECT qpt.template_code FROM quota_package_template qpt
                         WHERE qpt.template_id = dri.quota_template_id) AS templateCode,
                       dri.quota_template_version AS templateVersion,
                       dri.quota_package_quantity AS packageQuantity,
                       dri.quota_package_unit AS packageUnit,
                       dri.requested_package_count AS packageCount,
                       COALESCE(picked.pickedQty, dri.picked_quantity, 0) AS pickedQuantity,
                       GREATEST(dri.quantity - COALESCE(picked.pickedQty, dri.picked_quantity, 0), 0) AS remainingQuantity,
                       (SELECT GROUP_CONCAT(tc.unique_code ORDER BY tc.unique_code SEPARATOR ', ')
                          FROM department_requisition_trace_code rtc
                          JOIN udi_trace_code tc ON tc.trace_code_id = rtc.trace_code_id
                         WHERE rtc.requisition_item_id = dri.item_id) AS uniqueCodes,
                       COALESCE(NULLIF(dri.amount, 0), dri.quantity * p.purchase_price) AS applyAmount,
                       CASE WHEN p.is_volume_based = 1 THEN '是' ELSE '否' END AS volumeBased,
                       COALESCE(p.registration_no, '-') AS registrationNo
                  FROM department_requisition dr
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                  JOIN product p ON p.product_id = dri.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN (
                    SELECT requisition_item_id, SUM(pickedQty) AS pickedQty
                      FROM (
                        SELECT requisition_item_id, SUM(package_quantity) AS pickedQty
                          FROM spd_delivery_package_binding GROUP BY requisition_item_id
                        UNION ALL
                        SELECT rt.requisition_item_id, COUNT(*) AS pickedQty
                          FROM department_requisition_trace_code rt
                          JOIN spd_delivery_trace_code dt ON dt.trace_code_id = rt.trace_code_id
                         GROUP BY rt.requisition_item_id
                        UNION ALL
                        SELECT requisition_item_id, SUM(quantity) AS pickedQty
                          FROM spd_delivery_order WHERE delivery_type = 'loose'
                         GROUP BY requisition_item_id
                      ) sources GROUP BY requisition_item_id
                  ) picked ON picked.requisition_item_id = dri.item_id
                """ + where + " ORDER BY dri.item_id", args.toArray());
        if (rows.isEmpty()) {
            Long exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM department_requisition WHERE requisition_no = ?", Long.class, requisitionNo);
            if (exists != null && exists > 0) throw new org.springframework.security.access.AccessDeniedException("无权查看该科室申领单");
        }
        return Map.of("rows", rows, "total", rows.size(), "requisitionNo", requisitionNo);
    }

    private Map<String, Object> requisitionRecords(Map<String, String> params, PageRequest pageReq) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        requisitionAccess.appendScope(where, args, "dr.dept_id", "dr.applicant_id");
        appendLike(where, args, "dr.requisition_no", params.get("requisitionNo"));
        appendLike(where, args, "sd.dept_name", params.get("deptName"));
        appendEquals(where, args, "dr.status", params.get("status"));
        String countSql = "SELECT COUNT(*) FROM department_requisition dr JOIN sys_dept sd ON sd.dept_id = dr.dept_id" + where;
        Long total = args.isEmpty() ? jdbcTemplate.queryForObject(countSql, Long.class)
                : jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageReq.size());
        pageArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT dr.requisition_no AS bizNo, sd.dept_name AS deptName,
                       COALESCE(w.warehouse_name, '-') AS warehouseName,
                       COALESCE(u.real_name, u.username, '-') AS applicantName,
                       COALESCE(SUM(dri.quantity), 0) AS totalQuantity,
                       COALESCE(SUM(COALESCE(NULLIF(dri.amount, 0), dri.quantity * p.purchase_price)), 0) AS totalAmount,
                       dr.status, DATE_FORMAT(dr.apply_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM department_requisition dr
                  JOIN sys_dept sd ON sd.dept_id = dr.dept_id
                  LEFT JOIN warehouse w ON w.warehouse_id = dr.warehouse_id
                  LEFT JOIN sys_user u ON u.user_id = dr.applicant_id
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                  JOIN product p ON p.product_id = dri.product_id
                """ + where + """
                 GROUP BY dr.requisition_id, dr.requisition_no, sd.dept_name, w.warehouse_name,
                          u.real_name, u.username, dr.status, dr.apply_time
                 ORDER BY dr.apply_time DESC LIMIT ? OFFSET ?
                """, pageArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    private Map<String, Object> deliveryRecords(Map<String, String> params, PageRequest pageReq) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "d.delivery_no", params.get("deliveryNo"));
        appendLike(where, args, "d.requisition_no", params.get("requisitionNo"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "d.warehouse_name", params.get("warehouseName"));
        appendAnyLike(where, args, List.of("d.product_code", "d.product_name"), params.get("productKeyword"));
        appendLike(where, args, "qpl.label_no", params.get("labelNo"));
        appendEquals(where, args, "d.status", params.get("status"));
        appendDateBoundary(where, args, "d.create_time", params.get("dateFrom"), false);
        appendDateBoundary(where, args, "d.create_time", params.get("dateTo"), true);

        Object[] countArgs = args.toArray();
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT d.delivery_id)
                  FROM spd_delivery_order d
                  LEFT JOIN spd_delivery_package_binding b ON b.delivery_id = d.delivery_id
                  LEFT JOIN quota_package_label qpl ON qpl.label_id = b.label_id
                """ + where, Long.class, countArgs);

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT d.delivery_no AS bizNo, d.requisition_no AS sourceNo, d.dept_name AS deptName,
                       d.warehouse_name AS warehouseName, d.product_code AS productCode, d.product_name AS productName,
                       d.quantity, d.status, COUNT(b.binding_id) AS packageCount,
                       GROUP_CONCAT(qpl.label_no ORDER BY qpl.label_no SEPARATOR ', ') AS labelNos,
                       DATE_FORMAT(d.sign_time, '%Y-%m-%d %H:%i') AS finishTime,
                       DATE_FORMAT(d.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM spd_delivery_order d
                  LEFT JOIN spd_delivery_package_binding b ON b.delivery_id = d.delivery_id
                  LEFT JOIN quota_package_label qpl ON qpl.label_id = b.label_id
                """ + where + """
                 GROUP BY d.delivery_id, d.delivery_no, d.requisition_no, d.dept_name, d.warehouse_name,
                          d.product_code, d.product_name, d.quantity, d.status, d.sign_time, d.create_time
                 ORDER BY d.create_time DESC LIMIT ? OFFSET ?
                """, queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0L : total, pageReq);
    }

    private Map<String, Object> settlementDetails(PageRequest pageReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT sbi.item_id AS settlementItemId,
                       sb.settlement_id AS settlementId,
                       sb.settlement_no AS settlementNo,
                       COALESCE(sd.dept_name, hvc.dept_name, tc.current_department, '-') AS settlementDept,
                       COALESCE(w.warehouse_name, tc.current_location, '-') AS settlementWarehouse,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       COALESCE(p.generic_name, p.product_name, '-') AS genericName,
                       p.spec_model AS specModel,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(p.registration_no, '-') AS registrationNo,
                       s.supplier_name AS settlementSupplier,
                       p.unit,
                       sbi.unit_price AS unitPrice,
                       sbi.quantity AS settlementQuantity,
                       sbi.amount AS settlementAmount,
                       CASE WHEN p.is_volume_based = 1 THEN '是' ELSE '否' END AS volumeBased,
                       COALESCE(tc.udi_code, '-') AS uid,
                       COALESCE(tc.unique_code, '-') AS uniqueCode,
                       COALESCE(tc.package_label_no, '-') AS quotaPackageCode,
                       CASE WHEN p.is_medical_insurance_payment = 1 THEN '是' ELSE '否' END AS medicalInsurancePayment,
                       COALESCE(p.volume_based_type, '-') AS volumeBasedType,
                       COALESCE(p.medical_insurance_payment_type, '-') AS medicalInsurancePaymentType,
                       COALESCE(al.operator_name, '-') AS settlementOperator,
                       DATE_FORMAT(sb.confirm_time, '%Y-%m-%d %H:%i') AS settlementTime,
                       DATE_FORMAT(sb.generate_time, '%Y-%m-%d %H:%i') AS createTime,
                       COALESCE(sd.finance_dept_name, '-') AS financeDepartment,
                       COALESCE(tc.patient_name_masked,
                                JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.patientNameMasked')),
                                JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.patientName')),
                                '-') AS patientName,
                       sb.status,
                       sb.settlement_period AS period
                  FROM settlement_bill_item sbi
                  JOIN settlement_bill sb ON sb.settlement_id = sbi.settlement_id
                  JOIN supplier s ON s.supplier_id = sb.supplier_id
                  JOIN product p ON p.product_id = sbi.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN department_consumption_item dci
                    ON sbi.source_biz_type = 'department_consumption_item'
                   AND dci.item_id = sbi.source_biz_id
                  LEFT JOIN department_consumption dc ON dc.consumption_id = dci.consumption_id
                  LEFT JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                  LEFT JOIN warehouse w ON w.warehouse_id = dc.warehouse_id
                  LEFT JOIN high_value_charge hvc
                    ON sbi.source_biz_type = 'high_value_charge'
                   AND hvc.charge_id = sbi.source_biz_id
                  LEFT JOIN udi_trace_code tc ON tc.trace_code_id = sbi.trace_code_id
                  LEFT JOIN audit_log al
                    ON al.audit_id = (
                      SELECT MAX(confirm_audit.audit_id)
                        FROM audit_log confirm_audit
                       WHERE confirm_audit.biz_type = 'settlement_bill'
                         AND confirm_audit.operation_type = 'confirm_settlement'
                         AND confirm_audit.biz_id = sb.settlement_id
                    )
                 ORDER BY sb.generate_time DESC, sbi.item_id
                 LIMIT ? OFFSET ?
                """, pageReq.size(), pageReq.offset());
        return PageResponse.of(rows, countJoinedRows("""
                SELECT COUNT(*)
                  FROM settlement_bill_item sbi
                  JOIN settlement_bill sb ON sb.settlement_id = sbi.settlement_id
                """), pageReq);
    }

    private Map<String, Object> highValueChargeDetails(Map<String, String> params, PageRequest pageReq) {
        StringBuilder where = new StringBuilder("""
                 WHERE p.deleted = 0
                   AND p.is_chargeable = 1
                   AND (p.is_high_value = 1 OR hvc.charge_id IS NOT NULL)
                """);
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "hvc.patient_no", params.get("patientNo"));
        appendLike(where, args, "hvc.patient_no", params.get("inpatientNo"));
        appendLike(where, args, "utc.patient_name_masked", params.get("patientName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendAnyLike(where, args, List.of("p.udi_code", "hvc.udi_code", "utc.udi_code"), params.get("udi"));
        appendAnyLike(where, args, List.of("hvc.unique_code", "utc.unique_code"), params.get("uid"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        appendLike(where, args, "m.manufacturer_name", params.get("manufacturerName"));
        appendLike(where, args, "p.registration_no", params.get("registrationNo"));
        appendDateFrom(where, args, params.get("dateFrom"));
        appendDateTo(where, args, params.get("dateTo"));

        Object[] countArgs = args.toArray();
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM high_value_charge hvc
                  JOIN product p ON p.product_code = hvc.product_code
                  LEFT JOIN udi_trace_code utc ON utc.trace_code_id = hvc.trace_code_id
                  LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                  LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                """ + where, Long.class, countArgs);

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT hvc.charge_no AS bizNo,
                       COALESCE(hvc.external_charge_no, '-') AS externalChargeNo,
                       COALESCE(hvc.source_system, '-') AS sourceSystem,
                       COALESCE(hvc.operation_no, '-') AS operationNo,
                       hvc.dept_name AS deptName,
                       hvc.patient_no AS patientNo,
                       COALESCE(utc.patient_name_masked, '-') AS patientName,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       p.spec_model AS specModel,
                       COALESCE(p.brand, '-') AS brand,
                       p.unit,
                       COALESCE(s.supplier_name, '-') AS supplierName,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       p.registration_no AS registrationNo,
                       DATE_FORMAT(p.registration_expire_date, '%Y-%m-%d') AS registrationExpireDate,
                       COALESCE(hvc.udi_code, p.udi_code, '-') AS udiCode,
                       COALESCE(hvc.unique_code, '-') AS uniqueCode,
                       hvc.quantity AS chargeQuantity,
                       CASE WHEN hvc.quantity = 0 THEN 0 ELSE ROUND(hvc.amount / hvc.quantity, 4) END AS unitPrice,
                       hvc.amount AS chargeAmount,
                       hvc.status,
                       DATE_FORMAT(COALESCE(hvc.charge_time, hvc.create_time), '%Y-%m-%d %H:%i') AS chargeTime,
                       DATE_FORMAT(hvc.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM high_value_charge hvc
                  JOIN product p ON p.product_code = hvc.product_code
                  LEFT JOIN udi_trace_code utc ON utc.trace_code_id = hvc.trace_code_id
                  LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                  LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                """ + where + " ORDER BY COALESCE(hvc.charge_time, hvc.create_time) DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        List<Map<String, Object>> summaryRows = jdbcTemplate.queryForList("""
                SELECT COALESCE(SUM(hvc.quantity), 0) AS totalQuantity,
                       COALESCE(SUM(hvc.amount), 0) AS totalAmount
                  FROM high_value_charge hvc
                  JOIN product p ON p.product_code = hvc.product_code
                  LEFT JOIN udi_trace_code utc ON utc.trace_code_id = hvc.trace_code_id
                  LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                  LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                """ + where, countArgs);
        Map<String, Object> summary = summaryRows.isEmpty()
                ? Map.of("totalQuantity", 0, "totalAmount", 0)
                : summaryRows.get(0);
        return PageResponse.of(rows, total == null ? 0L : total, pageReq, summary);
    }

    private void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private void appendAnyLike(StringBuilder where, List<Object> args, List<String> columns, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String like = "%" + value.trim() + "%";
        where.append(" AND (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                where.append(" OR ");
            }
            where.append(columns.get(i)).append(" LIKE ?");
            args.add(like);
        }
        where.append(")");
    }

    private void appendEquals(StringBuilder where, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private void appendDateBoundary(StringBuilder where, List<Object> args, String column, String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append(endOfDay ? " <= ?" : " >= ?");
        args.add(value.trim() + (endOfDay ? " 23:59:59" : " 00:00:00"));
    }

    private void appendDateFrom(StringBuilder where, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND COALESCE(hvc.charge_time, hvc.create_time) >= ?");
        args.add(value.trim() + " 00:00:00");
    }

    private void appendDateTo(StringBuilder where, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND COALESCE(hvc.charge_time, hvc.create_time) <= ?");
        args.add(value.trim() + " 23:59:59");
    }

    private Integer count(String table, String where) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        return value == null ? 0 : value;
    }

    private long countLong(String table, String where) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return value == null ? 0 : value;
    }

    private long countJoinedRows(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
