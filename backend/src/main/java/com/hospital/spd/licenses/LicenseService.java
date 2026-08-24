package com.hospital.spd.licenses;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.common.OperatorContextProvider;
import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.common.SqlHelper.nullIfBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns license documents (product, supplier, manufacturer, contract) with attachment storage and preview.
 */
@Service
public class LicenseService {

    private static final List<String> LICENSE_TYPES = List.of("product", "supplier", "manufacturer", "contract");

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final Path uploadRoot;

    public LicenseService(JdbcTemplate jdbcTemplate,
                          OperatorContextProvider operatorContextProvider,
                          @Value("${spd.upload.dir:./output/license-files}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("证照附件目录初始化失败: " + uploadRoot, e);
        }
    }

    public Map<String, Object> list(String type, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        List<Object> args = new java.util.ArrayList<>();
        if (!isBlank(type) && LICENSE_TYPES.contains(type)) {
            where.append(" AND license_type = ?");
            args.add(type);
        }
        if (!isBlank(keyword)) {
            where.append(" AND (license_name LIKE ? OR license_no LIKE ? OR owner_name LIKE ? OR owner_code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM license_document" + where, Long.class, args.toArray());

        List<Object> queryArgs = new java.util.ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT l.license_id AS id, l.license_type AS licenseType, l.license_name AS licenseName,
                       l.license_no AS licenseNo, l.owner_type AS ownerType, l.owner_code AS ownerCode,
                       l.owner_name AS ownerName, l.party_a AS partyA, l.party_b AS partyB,
                       l.contract_amount AS contractAmount, l.issue_date AS issueDate,
                       l.expire_date AS expireDate, l.status AS status, l.remark,
                       (SELECT COUNT(*) FROM sys_attachment a
                         WHERE a.biz_type = 'license' AND a.biz_id = l.license_id AND a.deleted = 0) AS attachmentCount
                  FROM license_document l
                """ + where + " ORDER BY l.license_id DESC LIMIT ? OFFSET ?", queryArgs.toArray());

        Map<String, Object> summary = new LinkedHashMap<>();
        for (String licenseType : LICENSE_TYPES) {
            summary.put(licenseType + "Count", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM license_document WHERE deleted = 0 AND license_type = ?",
                    Long.class, licenseType));
        }
        return com.hospital.spd.common.PageResponse.of(rows, total == null ? 0 : total,
                new com.hospital.spd.common.PageRequest(page, size, (page - 1) * size), summary);
    }

    public Map<String, Object> detail(Long licenseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT license_id AS id, license_type AS licenseType, license_name AS licenseName,
                       license_no AS licenseNo, owner_type AS ownerType, owner_id AS ownerId,
                       owner_code AS ownerCode, owner_name AS ownerName, party_a AS partyA,
                       party_b AS partyB, contract_amount AS contractAmount, issue_date AS issueDate,
                       expire_date AS expireDate, status AS status, remark
                  FROM license_document
                 WHERE license_id = ? AND deleted = 0
                """, licenseId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("证照不存在或已删除");
        }
        Map<String, Object> result = rows.get(0);
        result.put("attachments", attachments(licenseId));
        return result;
    }

    @Transactional
    public Map<String, Object> create(LicenseUpsertRequest request) {
        validate(request);
        String licenseType = normalizeType(request.licenseType());
        jdbcTemplate.update("""
                INSERT INTO license_document (
                  license_type, license_name, license_no, owner_type, owner_id, owner_code, owner_name,
                  party_a, party_b, contract_amount, issue_date, expire_date, status, remark, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                licenseType,
                request.licenseName().trim(),
                nullIfBlank(request.licenseNo()),
                nullIfBlank(request.ownerType()),
                request.ownerId(),
                nullIfBlank(request.ownerCode()),
                nullIfBlank(request.ownerName()),
                nullIfBlank(request.partyA()),
                nullIfBlank(request.partyB()),
                request.contractAmount(),
                parseDate(request.issueDate()),
                parseDate(request.expireDate()),
                request.status() != null && request.status() == 0 ? 0 : 1,
                nullIfBlank(request.remark()),
                operatorContextProvider.current().userId());
        return Map.of("id", lastInsertedId());
    }

    @Transactional
    public Map<String, Object> update(Long licenseId, LicenseUpsertRequest request) {
        validate(request);
        requireExists(licenseId);
        jdbcTemplate.update("""
                UPDATE license_document
                   SET license_name = ?, license_no = ?, owner_type = ?, owner_id = ?, owner_code = ?,
                       owner_name = ?, party_a = ?, party_b = ?, contract_amount = ?, issue_date = ?,
                       expire_date = ?, status = ?, remark = ?
                 WHERE license_id = ? AND deleted = 0
                """,
                request.licenseName().trim(),
                nullIfBlank(request.licenseNo()),
                nullIfBlank(request.ownerType()),
                request.ownerId(),
                nullIfBlank(request.ownerCode()),
                nullIfBlank(request.ownerName()),
                nullIfBlank(request.partyA()),
                nullIfBlank(request.partyB()),
                request.contractAmount(),
                parseDate(request.issueDate()),
                parseDate(request.expireDate()),
                request.status() != null && request.status() == 0 ? 0 : 1,
                nullIfBlank(request.remark()),
                licenseId);
        return Map.of("id", licenseId);
    }

    @Transactional
    public Map<String, Object> remove(Long licenseId) {
        requireExists(licenseId);
        jdbcTemplate.update("UPDATE license_document SET deleted = 1 WHERE license_id = ?", licenseId);
        jdbcTemplate.update("UPDATE sys_attachment SET deleted = 1 WHERE biz_type = 'license' AND biz_id = ?", licenseId);
        return Map.of("id", licenseId);
    }

    public List<Map<String, Object>> attachments(Long licenseId) {
        requireExists(licenseId);
        return jdbcTemplate.queryForList("""
                SELECT attachment_id AS id, file_name AS fileName, file_ext AS ext,
                       file_type AS fileType, file_size AS size, category, description,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM sys_attachment
                 WHERE biz_type = 'license' AND biz_id = ? AND deleted = 0
                 ORDER BY attachment_id DESC
                """, licenseId);
    }

    @Transactional
    public Map<String, Object> uploadAttachment(Long licenseId, MultipartFile file, String category) {
        requireExists(licenseId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的证照附件");
        }
        String originalName = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        String storedName = "license-" + licenseId + "-" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storedName);
        try {
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("证照附件保存失败", e);
        }
        jdbcTemplate.update("""
                INSERT INTO sys_attachment (
                  biz_type, biz_id, file_name, file_ext, file_type, file_size, file_path, file_url, category, create_by
                ) VALUES ('license', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                licenseId,
                originalName,
                ext.replace(".", ""),
                mediaTypeOf(ext),
                file.getSize(),
                storedName,
                "/licenses/attachments/file/" + storedName,
                isBlank(category) ? "other" : category.trim(),
                operatorContextProvider.current().userId());
        return Map.of("attachmentId", lastInsertedId());
    }

    public ResponseEntity<Resource> downloadAttachment(Long attachmentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT file_name AS fileName, file_path AS filePath, file_ext AS ext
                  FROM sys_attachment
                 WHERE attachment_id = ? AND biz_type = 'license' AND deleted = 0
                """, attachmentId);
        String storedName = String.valueOf(row.get("filePath"));
        Path file = uploadRoot.resolve(storedName).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("证照附件文件不存在");
        }
        String fileName = String.valueOf(row.get("fileName"));
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(mediaTypeOf(String.valueOf(row.get("ext")))))
                .body(new FileSystemResource(file));
    }

    public ResponseEntity<Resource> downloadAttachmentByStoredName(String storedName) {
        Path file = uploadRoot.resolve(storedName).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("证照附件文件不存在");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(mediaTypeOf(extensionOf(storedName))))
                .body(new FileSystemResource(file));
    }

    // ======================== helpers ========================

    private void validate(LicenseUpsertRequest request) {
        if (isBlank(request.licenseType()) || isBlank(request.licenseName())) {
            throw new IllegalArgumentException("证照类型与证照名称为必填项");
        }
    }

    private static String normalizeType(String type) {
        return switch (type.trim()) {
            case "product", "商品证照" -> "product";
            case "supplier", "供应商证照" -> "supplier";
            case "manufacturer", "厂家证照" -> "manufacturer";
            case "contract", "合同" -> "contract";
            default -> throw new IllegalArgumentException("证照类型不正确");
        };
    }

    private void requireExists(Long licenseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM license_document WHERE license_id = ? AND deleted = 0",
                Integer.class, licenseId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("证照不存在或已删除");
        }
    }

    private Long lastInsertedId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static Date parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase();
    }

    private static String mediaTypeOf(String ext) {
        String normalized = ext == null ? "" : ext.trim().toLowerCase();
        if (!normalized.isEmpty() && !normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        return switch (normalized) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
