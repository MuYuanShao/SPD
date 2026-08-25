package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.service.AuditLogService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Reconciles SPD consumption facts against HIS charge details for regulatory inspection. */
@Service
public class SpdHisReconciliationReportService {
    private static final String DISPLAY_COLUMNS = """
            reconciliationDate, productCode, medicalInsuranceCode, hisMedicalInsuranceCode,
            productName, specModel, departmentName, patientName, patientNo,
            spdConsumptionQuantity, hisChargeQuantity, differenceQuantity, differenceAmount,
            chargeTime, differenceReason, riskLevel, riskCode
            """;
    private static final String[] EXPORT_HEADERS = {
            "核对日期", "耗材编码", "医保编码", "HIS医保编码", "耗材名称", "规格", "科室",
            "患者", "住院号", "SPD消耗数量", "HIS计费数量", "差异数量", "差异金额", "收费时间", "差异原因标记", "风险标记"
    };
    private static final String[] EXPORT_KEYS = {
            "reconciliationDate", "productCode", "medicalInsuranceCode", "hisMedicalInsuranceCode", "productName", "specModel", "departmentName",
            "patientName", "patientNo", "spdConsumptionQuantity", "hisChargeQuantity", "differenceQuantity", "differenceAmount", "chargeTime", "differenceReason", "riskLevel"
    };

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;

    public SpdHisReconciliationReportService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new DataScopeService(OperatorContext::system), new AuditLogService(jdbcTemplate));
    }

    @Autowired
    public SpdHisReconciliationReportService(JdbcTemplate jdbcTemplate, DataScopeService dataScopeService,
                                             AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeService = dataScopeService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> list(Map<String, String> params) {
        PageRequest pageRequest = PageRequest.from(params);
        QuerySpec spec = buildQuery(params);
        List<Object> rowArgs = new ArrayList<>(spec.args());
        rowArgs.add(pageRequest.size());
        rowArgs.add(pageRequest.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                spec.cte() + " SELECT " + DISPLAY_COLUMNS + " FROM reconciled " + spec.where()
                        + " ORDER BY reconciliationDate DESC, riskSort DESC, ABS(differenceAmount) DESC, productCode LIMIT ? OFFSET ?",
                rowArgs.toArray());
        Long total = jdbcTemplate.queryForObject(
                spec.cte() + " SELECT COUNT(*) FROM reconciled " + spec.where(), Long.class, spec.args().toArray());
        auditLogService.record("spd_his_reconciliation_report", "view_report", null, "SPD-HIS",
                "查看SPD消耗-HIS收费核对对账报表");
        return PageResponse.of(rows, total == null ? 0 : total, pageRequest, loadSummary(spec));
    }

    public byte[] exportExcel(Map<String, String> params) {
        QuerySpec spec = buildQuery(params);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                spec.cte() + " SELECT " + DISPLAY_COLUMNS + " FROM reconciled " + spec.where()
                        + " ORDER BY reconciliationDate DESC, riskSort DESC, ABS(differenceAmount) DESC, productCode",
                spec.args().toArray());
        auditLogService.record("spd_his_reconciliation_report", "export_report", null, "SPD-HIS-XLSX",
                "导出SPD消耗-HIS收费核对对账报表，记录数：" + rows.size());
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SPD-HIS收费核对");
            sheet.createFreezePane(0, 1);
            Row header = sheet.createRow(0);
            for (int column = 0; column < EXPORT_HEADERS.length; column++) {
                header.createCell(column).setCellValue(EXPORT_HEADERS[column]);
                sheet.setColumnWidth(column, Math.min(42, Math.max(12, EXPORT_HEADERS[column].length() * 3)) * 256);
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                Map<String, Object> source = rows.get(index);
                for (int column = 0; column < EXPORT_KEYS.length; column++) {
                    Object value = source.get(EXPORT_KEYS[column]);
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

    public void recordExport(String format) {
        String normalized = format == null ? "PDF" : format.trim().toUpperCase(Locale.ROOT);
        auditLogService.record("spd_his_reconciliation_report", "export_report", null, "SPD-HIS-" + normalized,
                "导出SPD消耗-HIS收费核对对账报表（" + normalized + "）");
    }

    private Map<String, Object> loadSummary(QuerySpec spec) {
        return new LinkedHashMap<>(jdbcTemplate.queryForMap(spec.cte() + """
                SELECT COUNT(*) AS totalCount,
                       COALESCE(SUM(CASE WHEN riskCode <> 'CONSISTENT' THEN 1 ELSE 0 END), 0) AS exceptionCount,
                       COALESCE(SUM(ABS(differenceAmount)), 0) AS differenceAmount,
                       COALESCE(SUM(CASE WHEN riskCode = 'MISSING_CHARGE' THEN 1 ELSE 0 END), 0) AS missingChargeCount,
                       COALESCE(SUM(CASE WHEN riskCode = 'DUPLICATE_CHARGE' THEN 1 ELSE 0 END), 0) AS duplicateChargeCount,
                       COALESCE(SUM(CASE WHEN riskCode = 'SUSPECTED_SWAP' THEN 1 ELSE 0 END), 0) AS suspectedSwapCount
                  FROM reconciled
                """ + spec.where(), spec.args().toArray()));
    }

    private QuerySpec buildQuery(Map<String, String> params) {
        LocalDate endDate = parseDate(params.get("dateTo"), LocalDate.now());
        LocalDate startDate = parseDate(params.get("dateFrom"), endDate.minusDays(29));
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("开始日期不能晚于结束日期");

        Scope lowValue = scope(" WHERE p.deleted = 0 AND p.is_high_value = 0", "dc.dept_id", "dc.consume_by");
        Scope highValue = scope(" WHERE p.deleted = 0", "hvc_dept.dept_id", null);
        Scope his = scope(" WHERE p.deleted = 0 AND NOT EXISTS (SELECT 1 FROM high_value_charge duplicate_hvc WHERE duplicate_hvc.external_charge_no = hcd.external_charge_no)", "his_dept.dept_id", null);
        List<Object> args = new ArrayList<>();
        args.addAll(lowValue.args());
        args.addAll(highValue.args());
        args.addAll(his.args());

        String cte = factsCte(lowValue.where(), highValue.where(), his.where());
        StringBuilder where = new StringBuilder(" WHERE reconciliationRawDate >= ? AND reconciliationRawDate <= ?");
        args.add(Date.valueOf(startDate));
        args.add(Date.valueOf(endDate));
        appendLike(where, args, "departmentName", params.get("department"));
        appendAnyLike(where, args, List.of("productCode", "productName", "medicalInsuranceCode", "hisMedicalInsuranceCode"), params.get("productKeyword"));
        appendAnyLike(where, args, List.of("patientSearchNo", "patientSearchName"), params.get("patientKeyword"));
        String riskType = text(params.get("riskType"));
        if (!riskType.isBlank() && !"ALL".equalsIgnoreCase(riskType)) {
            where.append(" AND riskCode = ?");
            args.add(riskType.toUpperCase(Locale.ROOT));
        }
        if (Boolean.parseBoolean(params.getOrDefault("onlyExceptions", "false"))) {
            where.append(" AND riskCode <> 'CONSISTENT'");
        }
        return new QuerySpec(cte, where.toString(), args);
    }

    private Scope scope(String initialWhere, String departmentColumn, String ownerColumn) {
        StringBuilder where = new StringBuilder(initialWhere);
        List<Object> args = new ArrayList<>();
        dataScopeService.appendScope(where, args, departmentColumn, ownerColumn);
        return new Scope(where.toString(), args);
    }

    private static String factsCte(String lowValueWhere, String highValueWhere, String hisWhere) {
        return """
                WITH reconciliation_facts AS (
                  SELECT DATE(dc.consume_time) AS reconciliation_date, sd.dept_name AS department_name,
                         COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.patientNo')), ''), NULLIF(JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.inpatientNo')), ''), '-') AS patient_no,
                         COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.patientNameMasked')), ''), NULLIF(JSON_UNQUOTE(JSON_EXTRACT(dc.patient_info, '$.patientName')), ''), '-') AS patient_name,
                         p.product_code, p.product_name, p.spec_model, COALESCE(p.medical_insurance_code, p.tender_sub_code, '-') AS catalog_medical_code,
                         CAST(NULL AS CHAR(80)) AS his_medical_code, dci.quantity AS spd_quantity, CAST(0 AS DECIMAL(18,4)) AS his_quantity,
                         COALESCE(NULLIF(dci.unit_price, 0), p.retail_price, p.purchase_price, 0) AS unit_price, CAST(NULL AS DATETIME) AS charge_time
                    FROM department_consumption dc JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                    JOIN product p ON p.product_id = dci.product_id JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                """ + lowValueWhere + """
                  UNION ALL
                  SELECT DATE(COALESCE(hvc.charge_time, hvc.create_time)), hvc.dept_name, hvc.patient_no, COALESCE(utc.patient_name_masked, '-'),
                         p.product_code, p.product_name, p.spec_model, COALESCE(p.medical_insurance_code, p.tender_sub_code, '-'),
                         COALESCE(p.medical_insurance_code, p.tender_sub_code, '-'), hvc.quantity, hvc.quantity,
                         CASE WHEN hvc.quantity = 0 THEN 0 ELSE hvc.amount / hvc.quantity END, COALESCE(hvc.charge_time, hvc.create_time)
                    FROM high_value_charge hvc JOIN product p ON p.product_code = hvc.product_code
                    LEFT JOIN udi_trace_code utc ON utc.trace_code_id = hvc.trace_code_id
                    LEFT JOIN sys_dept hvc_dept ON hvc_dept.dept_name = hvc.dept_name AND hvc_dept.deleted = 0
                """ + highValueWhere + """
                  UNION ALL
                  SELECT DATE(hcd.charge_time), hcd.dept_name, hcd.patient_no, COALESCE(hcd.patient_name_masked, '-'),
                         p.product_code, p.product_name, p.spec_model, COALESCE(p.medical_insurance_code, p.tender_sub_code, '-'),
                         COALESCE(hcd.medical_insurance_code, '-'), CAST(0 AS DECIMAL(18,4)), hcd.quantity,
                         COALESCE(NULLIF(hcd.unit_price, 0), p.retail_price, p.purchase_price, 0), hcd.charge_time
                    FROM his_charge_detail hcd JOIN product p ON p.product_code = hcd.product_code
                    LEFT JOIN sys_dept his_dept ON his_dept.dept_name = hcd.dept_name AND his_dept.deleted = 0
                """ + hisWhere + """
                ), aggregated AS (
                  SELECT reconciliation_date, department_name, patient_no, MAX(patient_name) AS patient_name,
                         product_code, MAX(product_name) AS product_name, MAX(spec_model) AS spec_model,
                         MAX(catalog_medical_code) AS catalog_medical_code, COALESCE(MAX(his_medical_code), '-') AS his_medical_code,
                         SUM(spd_quantity) AS spd_quantity, SUM(his_quantity) AS his_quantity,
                         MAX(unit_price) AS unit_price, MAX(charge_time) AS charge_time
                    FROM reconciliation_facts GROUP BY reconciliation_date, department_name, patient_no, product_code
                ), reconciled AS (
                  SELECT reconciliation_date AS reconciliationRawDate, DATE_FORMAT(reconciliation_date, '%Y-%m-%d') AS reconciliationDate,
                         product_code AS productCode, catalog_medical_code AS medicalInsuranceCode, his_medical_code AS hisMedicalInsuranceCode,
                         product_name AS productName, spec_model AS specModel, department_name AS departmentName,
                         patient_no AS patientSearchNo, patient_name AS patientSearchName,
                         CASE WHEN patient_name = '-' THEN '-' WHEN CHAR_LENGTH(patient_name) <= 1 THEN '*' ELSE CONCAT(LEFT(patient_name, 1), '**') END AS patientName,
                         CASE WHEN patient_no = '-' THEN '-' ELSE CONCAT(LEFT(patient_no, 2), '****', RIGHT(patient_no, 2)) END AS patientNo,
                         spd_quantity AS spdConsumptionQuantity, his_quantity AS hisChargeQuantity,
                         spd_quantity - his_quantity AS differenceQuantity, ROUND((spd_quantity - his_quantity) * unit_price, 2) AS differenceAmount,
                         COALESCE(DATE_FORMAT(charge_time, '%Y-%m-%d %H:%i'), '-') AS chargeTime,
                         CASE WHEN his_quantity > 0 AND his_medical_code <> '-' AND catalog_medical_code <> '-' AND his_medical_code <> catalog_medical_code THEN '疑似串换'
                              WHEN spd_quantity > his_quantity THEN '漏计费' WHEN spd_quantity < his_quantity THEN '重复计费' ELSE '一致' END AS differenceReason,
                         CASE WHEN his_quantity > 0 AND his_medical_code <> '-' AND catalog_medical_code <> '-' AND his_medical_code <> catalog_medical_code THEN '高风险'
                              WHEN spd_quantity > his_quantity THEN '高风险' WHEN spd_quantity < his_quantity THEN '中风险' ELSE '低风险' END AS riskLevel,
                         CASE WHEN his_quantity > 0 AND his_medical_code <> '-' AND catalog_medical_code <> '-' AND his_medical_code <> catalog_medical_code THEN 'SUSPECTED_SWAP'
                              WHEN spd_quantity > his_quantity THEN 'MISSING_CHARGE' WHEN spd_quantity < his_quantity THEN 'DUPLICATE_CHARGE' ELSE 'CONSISTENT' END AS riskCode,
                         CASE WHEN his_quantity > 0 AND his_medical_code <> '-' AND catalog_medical_code <> '-' AND his_medical_code <> catalog_medical_code THEN 4
                              WHEN spd_quantity > his_quantity THEN 3 WHEN spd_quantity < his_quantity THEN 2 ELSE 1 END AS riskSort
                    FROM aggregated
                )
                """;
    }

    private static void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        String normalized = text(value);
        if (normalized.isBlank()) return;
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + normalized + "%");
    }

    private static void appendAnyLike(StringBuilder where, List<Object> args, List<String> columns, String value) {
        String normalized = text(value);
        if (normalized.isBlank()) return;
        where.append(" AND (");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) where.append(" OR ");
            where.append(columns.get(index)).append(" LIKE ?");
            args.add("%" + normalized + "%");
        }
        where.append(')');
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

    private record QuerySpec(String cte, String where, List<Object> args) {}
    private record Scope(String where, List<Object> args) {}
}
