package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns supplier invoice registration, verification, and settlement linkage.
 */
@Service
public class InvoiceService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentNumberService documentNumberService;
    private final AuditLogService auditLogService;

    public InvoiceService(JdbcTemplate jdbcTemplate, DocumentNumberService documentNumberService, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentNumberService = documentNumberService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> list(Map<String, String> params) {
        PageRequest page = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        String keyword = params.getOrDefault("keyword", "").trim();
        String status = params.getOrDefault("status", "").trim();
        if (!keyword.isEmpty()) {
            where.append(" AND (si.invoice_no LIKE ? OR si.invoice_code LIKE ? OR si.invoice_number LIKE ? OR s.supplier_name LIKE ?)");
            for (int i = 0; i < 4; i++) args.add("%" + keyword + "%");
        }
        if (!status.isEmpty()) {
            where.append(" AND si.status = ?");
            args.add(status);
        }
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(page.size());
        listArgs.add(page.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT si.invoice_id AS invoiceId, si.invoice_no AS invoiceNo,
                       si.invoice_code AS invoiceCode, si.invoice_number AS invoiceNumber,
                       s.supplier_name AS supplierName,
                       DATE_FORMAT(si.invoice_date, '%Y-%m-%d') AS invoiceDate,
                       si.amount, si.tax_amount AS taxAmount, si.status,
                       sb.settlement_no AS settlementNo, si.remark,
                       DATE_FORMAT(si.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM supplier_invoice si
                  JOIN supplier s ON s.supplier_id = si.supplier_id
                  LEFT JOIN settlement_bill sb ON sb.settlement_id = si.settlement_id
                """ + where + " ORDER BY si.create_time DESC LIMIT ? OFFSET ?", listArgs.toArray());
        String countSql = "SELECT COUNT(*) FROM supplier_invoice si JOIN supplier s ON s.supplier_id = si.supplier_id" + where;
        Long total = args.isEmpty()
                ? jdbcTemplate.queryForObject(countSql, Long.class)
                : jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        return PageResponse.of(rows, total == null ? 0L : total, page);
    }

    public List<Map<String, Object>> options() {
        return jdbcTemplate.queryForList("""
                SELECT supplier_id AS supplierId, supplier_name AS supplierName
                  FROM supplier WHERE deleted = 0 AND status = 1
                 ORDER BY supplier_name
                """);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        long supplierId = longValue(body.get("supplierId"), "supplierId");
        String invoiceCode = required(body, "invoiceCode");
        String invoiceNumber = required(body, "invoiceNumber");
        Date invoiceDate = Date.valueOf(required(body, "invoiceDate"));
        BigDecimal amount = decimal(body.get("amount"), "amount");
        BigDecimal taxAmount = body.get("taxAmount") == null ? BigDecimal.ZERO : decimal(body.get("taxAmount"), "taxAmount");
        Long settlementId = nullableLong(body.get("settlementId"));
        String remark = text(body.get("remark"));
        Long supplierCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supplier WHERE supplier_id = ? AND deleted = 0 AND status = 1", Long.class, supplierId);
        if (supplierCount == null || supplierCount == 0) throw new IllegalArgumentException("supplier not found or disabled");
        String invoiceNo = documentNumberService.next(DocumentKind.SUPPLIER_INVOICE);
        jdbcTemplate.update("""
                INSERT INTO supplier_invoice (
                  invoice_no, supplier_id, invoice_code, invoice_number, invoice_date,
                  amount, tax_amount, settlement_id, status, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, invoiceNo, supplierId, invoiceCode, invoiceNumber, invoiceDate,
                amount, taxAmount, settlementId, "pending_verification", remark);
        Long invoiceId = jdbcTemplate.queryForObject("SELECT invoice_id FROM supplier_invoice WHERE invoice_no = ?", Long.class, invoiceNo);
        auditLogService.record("supplier_invoice", "create", invoiceId, invoiceNo, "supplier invoice created");
        return Map.of("invoiceNo", invoiceNo, "status", "pending_verification");
    }

    @Transactional
    public Map<String, Object> verify(String invoiceNo) {
        int updated = jdbcTemplate.update("UPDATE supplier_invoice SET status = 'verified', verify_time = CURRENT_TIMESTAMP WHERE invoice_no = ? AND status = 'pending_verification'", invoiceNo);
        if (updated != 1) throw new IllegalArgumentException("invoice is not pending verification");
        Long invoiceId = jdbcTemplate.queryForObject("SELECT invoice_id FROM supplier_invoice WHERE invoice_no = ?", Long.class, invoiceNo);
        auditLogService.record("supplier_invoice", "verify", invoiceId, invoiceNo, "supplier invoice verified");
        return Map.of("invoiceNo", invoiceNo, "status", "verified");
    }

    private static String required(Map<String, Object> body, String key) {
        String value = text(body.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.trim();
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private static long longValue(Object value, String key) { if (value == null) throw new IllegalArgumentException(key + " is required"); return Long.parseLong(String.valueOf(value)); }
    private static Long nullableLong(Object value) { return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value)); }
    private static BigDecimal decimal(Object value, String key) { BigDecimal number = new BigDecimal(required(Map.of(key, value), key)); if (number.signum() < 0) throw new IllegalArgumentException(key + " cannot be negative"); return number; }
}
