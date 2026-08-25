package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.service.AuditLogService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds auditable supplier, centralized-procurement, and inventory regulatory reports. */
@Service
public class RegulatoryReportCenterService {
    private static final String[] SUPPLIER_HEADERS = {
            "序号", "供货日期", "供应商", "生产厂家", "物资编码", "医保编码", "物资名称", "规格",
            "注册证号", "采购单价", "供货数量", "供货金额", "订单号", "合同号", "验收科室", "验收人", "验收时间"
    };
    private static final String[] SUPPLIER_KEYS = {
            "sequence", "supplyDate", "supplierName", "manufacturerName", "productCode", "medicalInsuranceCode",
            "productName", "specModel", "registrationNo", "unitPrice", "supplyQuantity", "supplyAmount",
            "orderNo", "contractNo", "acceptanceDepartment", "acceptanceUser", "acceptanceTime"
    };
    private static final String[] CENTRALIZED_HEADERS = {
            "集采批次", "物资名称", "规格", "中选厂家", "中选价格", "年度任务量", "本期采购量", "累计采购量",
            "完成率", "中选标识", "科室消耗量", "未完成原因"
    };
    private static final String[] CENTRALIZED_KEYS = {
            "batchName", "productName", "specModel", "selectedManufacturer", "selectedPrice", "annualTargetQuantity",
            "periodPurchaseQuantity", "cumulativePurchaseQuantity", "completionRate", "selectedFlag", "departmentConsumptionQuantity", "incompleteReason"
    };
    private static final String[] INVENTORY_HEADERS = {
            "物资编码", "物资名称", "规格", "生产厂家", "期初库存", "本期入库", "本期退库", "本期领用",
            "本期消耗", "本期报废", "期末库存", "库存单价", "库存金额", "库房", "物资属性"
    };
    private static final String[] INVENTORY_KEYS = {
            "productCode", "productName", "specModel", "manufacturerName", "openingQuantity", "inboundQuantity",
            "returnQuantity", "requisitionQuantity", "consumptionQuantity", "scrapQuantity", "closingQuantity",
            "unitPrice", "inventoryAmount", "warehouseName", "materialAttribute"
    };

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;

    public RegulatoryReportCenterService(JdbcTemplate jdbcTemplate, DataScopeService dataScopeService,
                                         AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeService = dataScopeService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> supplierDeliveryLedger(Map<String, String> params) {
        QuerySpec spec = supplierSpec(params);
        return page(spec, params, "supplyDate DESC, acceptanceTime DESC, productCode", supplierSummary(spec),
                "supplier_delivery_ledger_report", "查看供应商供货明细台账");
    }

    public byte[] exportSupplierDeliveryLedger(Map<String, String> params) {
        QuerySpec spec = supplierSpec(params);
        return export(spec, "supplyDate DESC, acceptanceTime DESC, productCode", "供应商供货明细台账",
                SUPPLIER_HEADERS, SUPPLIER_KEYS, true, "supplier_delivery_ledger_report");
    }

    public Map<String, Object> centralizedProcurementProgress(Map<String, String> params) {
        QuerySpec spec = centralizedSpec(params);
        return page(spec, params, "completionRate ASC, batchName, productName", centralizedSummary(spec),
                "centralized_procurement_progress_report", "查看集采执行进度报表");
    }

    public byte[] exportCentralizedProcurementProgress(Map<String, String> params) {
        QuerySpec spec = centralizedSpec(params);
        return export(spec, "completionRate ASC, batchName, productName", "集采执行进度报表",
                CENTRALIZED_HEADERS, CENTRALIZED_KEYS, false, "centralized_procurement_progress_report");
    }

    public Map<String, Object> inventoryMovementSummary(Map<String, String> params) {
        QuerySpec spec = inventorySpec(params);
        return page(spec, params, "warehouseName, productCode", inventorySummary(spec),
                "inventory_movement_summary_report", "查看全院物资进销存汇总表");
    }

    public byte[] exportInventoryMovementSummary(Map<String, String> params) {
        QuerySpec spec = inventorySpec(params);
        return export(spec, "warehouseName, productCode", "全院物资进销存汇总表",
                INVENTORY_HEADERS, INVENTORY_KEYS, false, "inventory_movement_summary_report");
    }

    public void recordExport(String reportCode, String format) {
        String normalizedFormat = text(format).isBlank() ? "PDF" : text(format).toUpperCase(Locale.ROOT);
        String normalizedReport = switch (text(reportCode)) {
            case "supplier-delivery-ledger" -> "supplier_delivery_ledger_report";
            case "centralized-procurement-progress" -> "centralized_procurement_progress_report";
            case "inventory-movement-summary" -> "inventory_movement_summary_report";
            default -> throw new IllegalArgumentException("未知报表编码");
        };
        auditLogService.record(normalizedReport, "export_report", null, normalizedFormat,
                "导出报表（" + normalizedFormat + "）");
    }

    private Map<String, Object> page(QuerySpec spec, Map<String, String> params, String orderBy,
                                     Map<String, Object> summary, String auditTarget, String auditMessage) {
        PageRequest pageRequest = PageRequest.from(params);
        List<Object> rowArgs = new ArrayList<>(spec.args());
        rowArgs.add(pageRequest.size());
        rowArgs.add(pageRequest.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM (" + spec.sql() + ") report_rows ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                rowArgs.toArray());
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + spec.sql() + ") report_count",
                Long.class, spec.args().toArray());
        auditLogService.record(auditTarget, "view_report", null, auditTarget, auditMessage);
        return PageResponse.of(rows, total == null ? 0 : total, pageRequest, summary);
    }

    private byte[] export(QuerySpec spec, String orderBy, String sheetName, String[] headers, String[] keys,
                          boolean addSequence, String auditTarget) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM (" + spec.sql() + ") report_rows ORDER BY " + orderBy, spec.args().toArray());
        if (addSequence) {
            for (int index = 0; index < rows.size(); index++) rows.get(index).put("sequence", index + 1);
        }
        auditLogService.record(auditTarget, "export_report", null, sheetName + "-XLSX",
                "导出" + sheetName + "，记录数：" + rows.size());
        return workbook(sheetName, headers, keys, rows);
    }

    private QuerySpec supplierSpec(Map<String, String> params) {
        DateRange range = dateRange(params);
        StringBuilder where = new StringBuilder(" WHERE ro.receive_time >= ? AND ro.receive_time < ? AND p.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(range.from()), Date.valueOf(range.to().plusDays(1))));
        dataScopeService.appendScope(where, args, "w.dept_id", "ro.receiver_id");
        appendLike(where, args, "s.supplier_name", params.get("supplier"));
        appendCategory(where, args, params.get("category"));
        appendLike(where, args, "d.dept_name", params.get("department"));
        String centralized = text(params.get("centralizedStatus"));
        if ("SELECTED".equalsIgnoreCase(centralized)) where.append(" AND p.is_centralized_procurement = 1");
        if ("NON_SELECTED".equalsIgnoreCase(centralized)) where.append(" AND p.is_centralized_procurement = 0");
        String sql = """
                SELECT DATE_FORMAT(ro.receive_time, '%Y-%m-%d') AS supplyDate,
                       s.supplier_name AS supplierName, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       p.product_code AS productCode, COALESCE(p.medical_insurance_code, p.tender_sub_code, '-') AS medicalInsuranceCode,
                       p.product_name AS productName, p.spec_model AS specModel, COALESCE(p.registration_no, '-') AS registrationNo,
                       roi.unit_price AS unitPrice, roi.qualified_quantity AS supplyQuantity,
                       ROUND(roi.qualified_quantity * roi.unit_price, 2) AS supplyAmount,
                       COALESCE(po.order_no, '-') AS orderNo, COALESCE(p.contract_code, '-') AS contractNo,
                       COALESCE(d.dept_name, w.warehouse_name) AS acceptanceDepartment,
                       COALESCE(NULLIF(u.real_name, ''), u.username, '-') AS acceptanceUser,
                       DATE_FORMAT(ro.receive_time, '%Y-%m-%d %H:%i') AS acceptanceTime,
                       CASE WHEN p.is_centralized_procurement = 1 THEN '中选' ELSE '非中选' END AS centralizedStatus
                  FROM receiving_order ro
                  JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                  JOIN product p ON p.product_id = roi.product_id
                  JOIN supplier s ON s.supplier_id = ro.supplier_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN purchase_order po ON po.purchase_order_id = ro.purchase_order_id
                  JOIN warehouse w ON w.warehouse_id = ro.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                  LEFT JOIN sys_user u ON u.user_id = ro.receiver_id
                """ + where;
        return new QuerySpec(sql, args);
    }

    private QuerySpec centralizedSpec(Map<String, String> params) {
        DateRange range = dateRange(params);
        LocalDate yearStart = LocalDate.of(range.to().getYear(), 1, 1);
        StringBuilder consumptionWhere = new StringBuilder(" WHERE dc.consume_time >= ? AND dc.consume_time < ?");
        List<Object> consumptionArgs = new ArrayList<>(List.of(Date.valueOf(range.from()), Date.valueOf(range.to().plusDays(1))));
        dataScopeService.appendScope(consumptionWhere, consumptionArgs, "dc.dept_id", "dc.consume_by");
        appendLike(consumptionWhere, consumptionArgs, "sd.dept_name", params.get("department"));

        StringBuilder outerWhere = new StringBuilder(" WHERE p.deleted = 0 AND (p.is_centralized_procurement = 1 OR t.task_id IS NOT NULL)");
        List<Object> outerArgs = new ArrayList<>();
        appendLike(outerWhere, outerArgs, "COALESCE(t.batch_name, t.batch_code, '未配置批次')", params.get("batch"));
        appendCategory(outerWhere, outerArgs, params.get("category"));

        List<Object> args = new ArrayList<>(List.of(
                Date.valueOf(range.from()), Date.valueOf(range.to().plusDays(1)),
                Date.valueOf(yearStart), Date.valueOf(range.to().plusDays(1))));
        args.addAll(consumptionArgs);
        args.add(range.to().getYear());
        args.addAll(outerArgs);
        String sql = """
                WITH purchase_summary AS (
                  SELECT roi.product_id,
                         SUM(CASE WHEN ro.receive_time >= ? AND ro.receive_time < ? THEN roi.qualified_quantity ELSE 0 END) AS period_qty,
                         SUM(CASE WHEN ro.receive_time >= ? AND ro.receive_time < ? THEN roi.qualified_quantity ELSE 0 END) AS cumulative_qty
                    FROM receiving_order ro
                    JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                   WHERE ro.receive_time IS NOT NULL
                   GROUP BY roi.product_id
                ), consumption_summary AS (
                  SELECT dci.product_id, SUM(dci.quantity) AS consumption_qty
                    FROM department_consumption dc
                    JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                    JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                """ + consumptionWhere + """
                   GROUP BY dci.product_id
                )
                SELECT COALESCE(t.batch_code, 'UNCONFIGURED') AS batchCode,
                       COALESCE(t.batch_name, t.batch_code, '未配置批次') AS batchName,
                       p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(selected_m.manufacturer_name, product_m.manufacturer_name, '-') AS selectedManufacturer,
                       COALESCE(t.selected_price, p.purchase_price, 0) AS selectedPrice,
                       COALESCE(t.annual_target_quantity, 0) AS annualTargetQuantity,
                       COALESCE(ps.period_qty, 0) AS periodPurchaseQuantity,
                       COALESCE(ps.cumulative_qty, 0) AS cumulativePurchaseQuantity,
                       CASE WHEN COALESCE(t.annual_target_quantity, 0) = 0 THEN 0
                            ELSE ROUND(COALESCE(ps.cumulative_qty, 0) * 100 / t.annual_target_quantity, 2) END AS completionRate,
                       CASE WHEN p.is_centralized_procurement = 1 THEN '中选' ELSE '非中选' END AS selectedFlag,
                       COALESCE(cs.consumption_qty, 0) AS departmentConsumptionQuantity,
                       CASE WHEN t.task_id IS NULL THEN '未配置年度任务量'
                            WHEN COALESCE(ps.cumulative_qty, 0) >= t.annual_target_quantity THEN '-'
                            ELSE COALESCE(NULLIF(t.incomplete_reason, ''), '未填报') END AS incompleteReason
                  FROM product p
                  LEFT JOIN centralized_procurement_task t ON t.product_id = p.product_id AND t.task_year = ? AND t.status = 1
                  LEFT JOIN manufacturer selected_m ON selected_m.manufacturer_id = t.selected_manufacturer_id
                  LEFT JOIN manufacturer product_m ON product_m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN purchase_summary ps ON ps.product_id = p.product_id
                  LEFT JOIN consumption_summary cs ON cs.product_id = p.product_id
                """ + outerWhere;
        return new QuerySpec(sql, args);
    }

    private QuerySpec inventorySpec(Map<String, String> params) {
        DateRange range = dateRange(params);
        Date from = Date.valueOf(range.from());
        Date endExclusive = Date.valueOf(range.to().plusDays(1));
        List<Object> args = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            args.add(from);
            args.add(endExclusive);
        }
        args.add(from);
        args.add(endExclusive);
        StringBuilder scopedWhere = new StringBuilder(" WHERE p.deleted = 0 AND w.deleted = 0");
        dataScopeService.appendScope(scopedWhere, args, "w.dept_id", "w.manager_user_id");
        StringBuilder outerWhere = new StringBuilder(" WHERE 1 = 1");
        appendLike(outerWhere, args, "warehouseName", params.get("warehouse"));
        appendLike(outerWhere, args, "categoryText", params.get("category"));
        String materialType = text(params.get("materialType"));
        if (!materialType.isBlank() && !"ALL".equalsIgnoreCase(materialType)) {
            outerWhere.append(" AND materialAttribute = ?");
            args.add(switch (materialType.toUpperCase(Locale.ROOT)) {
                case "HIGH" -> "高值耗材";
                case "REAGENT" -> "试剂";
                default -> "低值耗材";
            });
        }
        String sql = """
                WITH key_rows AS (
                  SELECT warehouse_id, product_id FROM inventory_balance GROUP BY warehouse_id, product_id
                  UNION
                  SELECT warehouse_id, product_id FROM inventory_event GROUP BY warehouse_id, product_id
                ), current_balance AS (
                  SELECT warehouse_id, product_id,
                         SUM(available_qty + locked_qty + isolated_qty) AS current_qty
                    FROM inventory_balance
                   GROUP BY warehouse_id, product_id
                ), event_summary AS (
                  SELECT warehouse_id, product_id,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('purchase_receive_in', 'warehouse_transfer_in', 'stocktaking_profit', 'quota_package_delivery_sign_in') THEN qty_change ELSE 0 END) AS inbound_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type = 'department_consumption_reverse_in' THEN qty_change ELSE 0 END) AS return_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('delivery_out', 'delivery_loose_out', 'warehouse_transfer_out', 'quota_pack_out') THEN ABS(qty_change) ELSE 0 END) AS requisition_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type = 'department_consumption_out' THEN ABS(qty_change) ELSE 0 END) AS consumption_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type = 'stocktaking_loss' THEN ABS(qty_change) ELSE 0 END) AS scrap_qty,
                         SUM(CASE WHEN event_time >= ? THEN qty_change ELSE 0 END) AS net_after_from,
                         SUM(CASE WHEN event_time >= ? THEN qty_change ELSE 0 END) AS net_after_end
                    FROM inventory_event
                   GROUP BY warehouse_id, product_id
                ), movement_base AS (
                  SELECT p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                         COALESCE(m.manufacturer_name, '-') AS manufacturerName, w.warehouse_name AS warehouseName,
                         p.purchase_price AS unitPrice,
                         CONCAT_WS('/', p.first_category, p.second_category, p.third_category) AS categoryText,
                         CASE WHEN COALESCE(p.first_category, '') LIKE '%试剂%' OR COALESCE(p.second_category, '') LIKE '%试剂%'
                                   OR COALESCE(p.third_category, '') LIKE '%试剂%' THEN '试剂'
                              WHEN p.is_high_value = 1 THEN '高值耗材' ELSE '低值耗材' END AS materialAttribute,
                         COALESCE(cb.current_qty, 0) - COALESCE(es.net_after_from, 0) AS openingQuantity,
                         COALESCE(es.inbound_qty, 0) AS inboundQuantity,
                         COALESCE(es.return_qty, 0) AS returnQuantity,
                         COALESCE(es.requisition_qty, 0) AS requisitionQuantity,
                         COALESCE(es.consumption_qty, 0) AS consumptionQuantity,
                         COALESCE(es.scrap_qty, 0) AS scrapQuantity,
                         COALESCE(cb.current_qty, 0) - COALESCE(es.net_after_end, 0) AS closingQuantity
                    FROM key_rows k
                    JOIN product p ON p.product_id = k.product_id
                    JOIN warehouse w ON w.warehouse_id = k.warehouse_id
                    LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                    LEFT JOIN current_balance cb ON cb.warehouse_id = k.warehouse_id AND cb.product_id = k.product_id
                    LEFT JOIN event_summary es ON es.warehouse_id = k.warehouse_id AND es.product_id = k.product_id
                """ + scopedWhere + """
                )
                SELECT productCode, productName, specModel, manufacturerName, openingQuantity, inboundQuantity,
                       returnQuantity, requisitionQuantity, consumptionQuantity, scrapQuantity, closingQuantity,
                       unitPrice, ROUND(closingQuantity * unitPrice, 2) AS inventoryAmount,
                       warehouseName, materialAttribute, categoryText
                  FROM movement_base
                """ + outerWhere;
        return new QuerySpec(sql, args);
    }

    private Map<String, Object> supplierSummary(QuerySpec spec) {
        return summary(spec, "COUNT(*) AS totalCount, COALESCE(SUM(supplyQuantity), 0) AS supplyQuantity, " +
                "COALESCE(SUM(supplyAmount), 0) AS supplyAmount, COUNT(DISTINCT supplierName) AS supplierCount");
    }

    private Map<String, Object> centralizedSummary(QuerySpec spec) {
        return summary(spec, "COUNT(*) AS taskCount, COALESCE(SUM(annualTargetQuantity), 0) AS annualTargetQuantity, " +
                "COALESCE(SUM(cumulativePurchaseQuantity), 0) AS cumulativePurchaseQuantity, " +
                "CASE WHEN SUM(annualTargetQuantity) = 0 THEN 0 ELSE ROUND(SUM(cumulativePurchaseQuantity) * 100 / SUM(annualTargetQuantity), 2) END AS completionRate, " +
                "COALESCE(SUM(CASE WHEN incompleteReason <> '-' THEN 1 ELSE 0 END), 0) AS incompleteCount");
    }

    private Map<String, Object> inventorySummary(QuerySpec spec) {
        return summary(spec, "COUNT(*) AS itemCount, COALESCE(SUM(openingQuantity * unitPrice), 0) AS openingAmount, " +
                "COALESCE(SUM(inboundQuantity * unitPrice), 0) AS inboundAmount, " +
                "COALESCE(SUM(consumptionQuantity * unitPrice), 0) AS consumptionAmount, COALESCE(SUM(inventoryAmount), 0) AS closingAmount");
    }

    private Map<String, Object> summary(QuerySpec spec, String columns) {
        return new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "SELECT " + columns + " FROM (" + spec.sql() + ") report_summary", spec.args().toArray()));
    }

    private static byte[] workbook(String sheetName, String[] headers, String[] keys, List<Map<String, Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            sheet.createFreezePane(0, 1);
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
                sheet.setColumnWidth(column, Math.min(40, Math.max(12, headers[column].length() * 3)) * 256);
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                Map<String, Object> source = rows.get(index);
                for (int column = 0; column < keys.length; column++) {
                    Object value = source.get(keys[column]);
                    if (value instanceof Number number) row.createCell(column).setCellValue(number.doubleValue());
                    else row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成Excel报表失败", exception);
        }
    }

    private static DateRange dateRange(Map<String, String> params) {
        LocalDate to = parseDate(params.get("dateTo"), LocalDate.now());
        LocalDate from = parseDate(params.get("dateFrom"), to.minusDays(29));
        if (from.isAfter(to)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        return new DateRange(from, to);
    }

    private static void appendCategory(StringBuilder where, List<Object> args, String value) {
        String normalized = text(value);
        if (normalized.isBlank()) return;
        where.append(" AND (p.first_category LIKE ? OR p.second_category LIKE ? OR p.third_category LIKE ?)");
        for (int index = 0; index < 3; index++) args.add("%" + normalized + "%");
    }

    private static void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        String normalized = text(value);
        if (normalized.isBlank()) return;
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + normalized + "%");
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record QuerySpec(String sql, List<Object> args) {}
    private record DateRange(LocalDate from, LocalDate to) {}
}
