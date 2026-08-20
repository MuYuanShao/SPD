package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import com.hospital.spd.masterdata.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maintains supplier master data, including search, create, update, status changes, import, and export.
 */
@Service
public class SupplierService {

    private static final Pattern UNIFIED_SOCIAL_CREDIT_CODE = Pattern.compile("[0-9A-HJ-NPQRTUWXY]{18}");

    private final JdbcTemplate jdbcTemplate;
    private final DocumentNumberService documentNumberService;

    public SupplierService(JdbcTemplate jdbcTemplate, DocumentNumberService documentNumberService) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentNumberService = documentNumberService;
    }

    // ==================== 公开方法 ====================

    /** 分页查询供应商列表 */
    public MasterDataPage suppliers(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE deleted = 0
                """);
        appendLike(where, args, "supplier_name", params.get("supplierName"));
        appendLike(where, args, "supplier_type", params.get("supplierType"));
        appendStatus(where, args, params.get("status"));

        Long total = args.isEmpty()
            ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supplier " + where, Long.class)
            : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supplier " + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT supplier_code AS code, supplier_name AS name, credit_code AS creditCode,
                       COALESCE(business_license_no, '-') AS businessLicenseNo,
                       supplier_type AS type, COALESCE(grade, '-') AS grade,
                       contact_name AS contactName, contact_phone AS contactPhone,
                       COALESCE(email, '-') AS email, COALESCE(address, '-') AS address,
                       CASE status WHEN 1 THEN '启用' ELSE '停用' END AS status
                FROM supplier
                """ + where + " ORDER BY supplier_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "供应商管理",
                "供应商准入、资质、联系人、等级与启停管理。",
                List.of("供应商编码", "供应商名称", "统一社会信用代码", "经营许可证号", "类型", "等级", "联系人", "联系电话", "邮箱", "地址", "状态"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    /** 创建供应商 */
    @Transactional
    public Map<String, Object> createSupplier(SupplierUpsertRequest request) {
        validateSupplier(request);
        String supplierCode = documentNumberService.next(DocumentKind.SUPPLIER);
        jdbcTemplate.update("""
                INSERT INTO supplier (
                  supplier_code, supplier_name, credit_code, business_license_no, supplier_type, grade,
                  contact_name, contact_phone, email, address, approval_status, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'approved', 1)
                """,
                supplierCode,
                request.supplierName().trim(),
                request.creditCode().trim(),
                nullIfBlank(request.businessLicenseNo()),
                request.supplierType().trim(),
                nullIfBlank(request.grade()),
                request.contactName().trim(),
                request.contactPhone().trim(),
                nullIfBlank(request.email()),
                nullIfBlank(request.address())
        );
        return Map.of("supplierCode", supplierCode);
    }

    /** 更新供应商 */
    @Transactional
    public Map<String, Object> updateSupplier(String supplierCode, SupplierUpsertRequest request) {
        validateSupplier(request);
        int updatedRows = jdbcTemplate.update("""
                UPDATE supplier
                SET supplier_name = ?, credit_code = ?, business_license_no = ?, supplier_type = ?, grade = ?,
                    contact_name = ?, contact_phone = ?, email = ?, address = ?
                WHERE supplier_code = ? AND deleted = 0
                """,
                request.supplierName().trim(),
                request.creditCode().trim(),
                nullIfBlank(request.businessLicenseNo()),
                request.supplierType().trim(),
                nullIfBlank(request.grade()),
                request.contactName().trim(),
                request.contactPhone().trim(),
                nullIfBlank(request.email()),
                nullIfBlank(request.address()),
                supplierCode.trim()
        );
        return Map.of("updatedRows", updatedRows, "supplierCode", supplierCode.trim());
    }

    /** 更新供应商状态 */
    public Map<String, Object> updateSupplierStatus(SupplierStatusUpdateRequest request) {
        if (request.supplierCodes() == null || request.supplierCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要停用的供应商");
        }

        int status = request.status() == null ? 0 : request.status();
        String placeholders = String.join(",", request.supplierCodes().stream().map(code -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(status);
        args.addAll(request.supplierCodes().stream().map(String::trim).toList());
        int updatedRows = jdbcTemplate.update("""
                UPDATE supplier
                SET status = ?
                WHERE deleted = 0 AND supplier_code IN (%s)
                """.formatted(placeholders), args.toArray());
        return Map.of("updatedRows", updatedRows);
    }

    /** 导出供应商 */
    public List<Map<String, Object>> exportSuppliers(Map<String, String> params) {
        return suppliers(params).rows();
    }

    /** 导入供应商 */
    public Map<String, Object> importSuppliers(java.io.BufferedReader reader) throws Exception {
        int importedRows = 0;
        String line;
        boolean header = true;
        while ((line = reader.readLine()) != null) {
            if (header) {
                header = false;
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length < 7 || isBlank(cells[0]) || isBlank(cells[1])) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO supplier (
                      supplier_code, supplier_name, credit_code, supplier_type, grade,
                      contact_name, contact_phone, email, address, approval_status, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'approved', 1)
                    ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name),
                      supplier_type = VALUES(supplier_type), grade = VALUES(grade),
                      contact_name = VALUES(contact_name), contact_phone = VALUES(contact_phone),
                      email = VALUES(email), address = VALUES(address), deleted = 0
                    """,
                    cells[0].trim(), cells[1].trim(), defaultText(cells, 2, "TEMP" + System.nanoTime()),
                    defaultText(cells, 3, "配送商"), nullIfBlank(defaultText(cells, 4, "")),
                    defaultText(cells, 5, "未维护"), defaultText(cells, 6, "未维护"),
                    nullIfBlank(defaultText(cells, 7, "")), nullIfBlank(defaultText(cells, 8, ""))
            );
            importedRows++;
        }
        return Map.of("importedRows", importedRows);
    }

    // ==================== 私有辅助方法 ====================

    private static void validateSupplier(SupplierUpsertRequest request) {
        if (isBlank(request.supplierName()) ||
                isBlank(request.creditCode()) || isBlank(request.supplierType()) ||
                isBlank(request.contactName()) || isBlank(request.contactPhone())) {
            throw new IllegalArgumentException("供应商名称、统一社会信用代码、类型、联系人、联系电话为必填项");
        }
        String creditCode = request.creditCode().trim();
        if (!UNIFIED_SOCIAL_CREDIT_CODE.matcher(creditCode).matches()) {
            throw new IllegalArgumentException("统一社会信用代码必须为18位标准代码（大写字母或数字）");
        }
    }
}
