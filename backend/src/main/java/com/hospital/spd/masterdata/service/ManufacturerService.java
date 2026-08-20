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

/**
 * Maintains manufacturer master data, including search, create, update, status changes, import, and export.
 */
@Service
public class ManufacturerService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentNumberService documentNumberService;

    public ManufacturerService(JdbcTemplate jdbcTemplate, DocumentNumberService documentNumberService) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentNumberService = documentNumberService;
    }

    // ==================== 公开方法 ====================

    /** 分页查询厂家列表 */
    public MasterDataPage manufacturers(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE deleted = 0
                """);
        appendLike(where, args, "manufacturer_name", params.get("manufacturerName"));
        appendLike(where, args, "license_no", params.get("licenseNo"));
        appendStatus(where, args, params.get("status"));

        Long total = args.isEmpty()
            ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manufacturer " + where, Long.class)
            : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manufacturer " + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT manufacturer_code AS code, manufacturer_name AS name,
                       COALESCE(credit_code, '-') AS creditCode,
                       COALESCE(license_no, '-') AS licenseNo,
                       COALESCE(contact_name, '-') AS contactName,
                       COALESCE(contact_phone, '-') AS contactPhone,
                       COALESCE(address, '-') AS address,
                       CASE status WHEN 1 THEN '启用' ELSE '停用' END AS status
                FROM manufacturer
                """ + where + " ORDER BY manufacturer_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "厂家管理",
                "生产厂家档案、生产许可与联系人信息。",
                List.of("厂家编码", "厂家名称", "统一社会信用代码", "生产许可证号", "联系人", "联系电话", "地址", "状态"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    /** 创建厂家 */
    @Transactional
    public Map<String, Object> createManufacturer(ManufacturerUpsertRequest request) {
        validateManufacturer(request);
        String manufacturerCode = documentNumberService.next(DocumentKind.MANUFACTURER);
        jdbcTemplate.update("""
                INSERT INTO manufacturer (
                  manufacturer_code, manufacturer_name, credit_code, license_no,
                  contact_name, contact_phone, address, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """,
                manufacturerCode,
                request.manufacturerName().trim(),
                nullIfBlank(request.creditCode()),
                nullIfBlank(request.licenseNo()),
                nullIfBlank(request.contactName()),
                nullIfBlank(request.contactPhone()),
                nullIfBlank(request.address())
        );
        return Map.of("manufacturerCode", manufacturerCode);
    }

    /** 更新厂家 */
    public Map<String, Object> updateManufacturer(String manufacturerCode, ManufacturerUpsertRequest request) {
        validateManufacturer(request);
        int updatedRows = jdbcTemplate.update("""
                UPDATE manufacturer
                SET manufacturer_name = ?, credit_code = ?, license_no = ?,
                    contact_name = ?, contact_phone = ?, address = ?
                WHERE manufacturer_code = ? AND deleted = 0
                """,
                request.manufacturerName().trim(),
                nullIfBlank(request.creditCode()),
                nullIfBlank(request.licenseNo()),
                nullIfBlank(request.contactName()),
                nullIfBlank(request.contactPhone()),
                nullIfBlank(request.address()),
                manufacturerCode.trim()
        );
        return Map.of("updatedRows", updatedRows, "manufacturerCode", manufacturerCode.trim());
    }

    /** 更新厂家状态 */
    public Map<String, Object> updateManufacturerStatus(ManufacturerStatusUpdateRequest request) {
        if (request.manufacturerCodes() == null || request.manufacturerCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要停用的厂家");
        }

        int status = request.status() == null ? 0 : request.status();
        String placeholders = String.join(",", request.manufacturerCodes().stream().map(code -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(status);
        args.addAll(request.manufacturerCodes().stream().map(String::trim).toList());
        int updatedRows = jdbcTemplate.update("""
                UPDATE manufacturer
                SET status = ?
                WHERE deleted = 0 AND manufacturer_code IN (%s)
                """.formatted(placeholders), args.toArray());
        return Map.of("updatedRows", updatedRows);
    }

    /** 导出厂家 */
    public List<Map<String, Object>> exportManufacturers(Map<String, String> params) {
        return manufacturers(params).rows();
    }

    /** 导入厂家 */
    public Map<String, Object> importManufacturers(java.io.BufferedReader reader) throws Exception {
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
            if (cells.length < 2 || isBlank(cells[0]) || isBlank(cells[1])) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO manufacturer (
                      manufacturer_code, manufacturer_name, credit_code, license_no,
                      contact_name, contact_phone, address, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE manufacturer_name = VALUES(manufacturer_name),
                      credit_code = VALUES(credit_code), license_no = VALUES(license_no),
                      contact_name = VALUES(contact_name), contact_phone = VALUES(contact_phone),
                      address = VALUES(address), deleted = 0
                    """,
                    cells[0].trim(), cells[1].trim(), nullIfBlank(defaultText(cells, 2, "")),
                    nullIfBlank(defaultText(cells, 3, "")), nullIfBlank(defaultText(cells, 4, "")),
                    nullIfBlank(defaultText(cells, 5, "")), nullIfBlank(defaultText(cells, 6, ""))
            );
            importedRows++;
        }
        return Map.of("importedRows", importedRows);
    }

    // ==================== 私有辅助方法 ====================

    private static void validateManufacturer(ManufacturerUpsertRequest request) {
        if (isBlank(request.manufacturerName())) {
            throw new IllegalArgumentException("厂家名称为必填项");
        }
    }
}
