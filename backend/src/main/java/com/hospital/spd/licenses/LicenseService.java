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
    private final LicenseOwnerService owners;
    private final LicenseHistoryService history;
    private final com.hospital.spd.common.service.AuditLogService audit;

    public LicenseService(JdbcTemplate jdbcTemplate,
                          OperatorContextProvider operatorContextProvider,
                          @Value("${spd.upload.dir:./output/license-files}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.owners = new LicenseOwnerService(jdbcTemplate);
        this.history = new LicenseHistoryService(jdbcTemplate, operatorContextProvider);
        this.audit = new com.hospital.spd.common.service.AuditLogService(jdbcTemplate, operatorContextProvider);
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
                       l.owner_name AS ownerName, l.owner_id AS ownerId, l.revision_no AS revisionNo,
                       CASE WHEN l.status = 0 THEN 'invalid' WHEN l.expire_date < CURRENT_DATE THEN 'expired'
                            WHEN l.issue_date > CURRENT_DATE THEN 'not_effective' ELSE 'valid' END AS effectiveStatus, l.party_a AS partyA, l.party_b AS partyB,
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
                       expire_date AS expireDate, status AS status, remark, revision_no AS revisionNo
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
        var owner = owners.resolve(licenseType, request, null);
        jdbcTemplate.update("""
                INSERT INTO license_document (
                  license_type, license_name, license_no, owner_type, owner_id, owner_code, owner_name,
                  party_a, party_b, contract_amount, issue_date, expire_date, status, remark, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                licenseType,
                request.licenseName().trim(),
                nullIfBlank(request.licenseNo()),
                owner.type(),
                owner.id(),
                owner.code(),
                owner.name(),
                nullIfBlank(request.partyA()),
                nullIfBlank(request.partyB()),
                request.contractAmount(),
                parseDate(request.issueDate()),
                parseDate(request.expireDate()),
                request.status() != null && request.status() == 0 ? 0 : 1,
                nullIfBlank(request.remark()),
                operatorContextProvider.current().userId());
        Long id = lastInsertedId();
        history.capture(id, "create");
        audit.record("license", "create", id, request.licenseNo(), "新增证照");
        return Map.of("id", id);
    }

    @Transactional
    public Map<String, Object> update(Long licenseId, LicenseUpsertRequest request) {
        return saveVersion(licenseId, request, false);
    }

    @Transactional
    public Map<String, Object> renew(Long licenseId, LicenseUpsertRequest request) {
        return saveVersion(licenseId, request, true);
    }

    private Map<String, Object> saveVersion(Long id, LicenseUpsertRequest request, boolean renewal) {
        validate(request);
        Map<String, Object> current = lockDocument(id);
        String type = String.valueOf(current.get("ownerLicenseType"));
        if (!type.equals(normalizeType(request.licenseType()))) throw new IllegalArgumentException("编辑不能改变证照类型");
        if (request.revisionNo() != null && request.revisionNo().intValue() != ((Number) current.get("revisionNo")).intValue()) {
            throw new IllegalArgumentException("证照已被其他用户修改，请刷新后重试");
        }
        var owner = owners.resolve(type, request, current);
        if (renewal) {
            Date expiry = parseDate(request.expireDate());
            if (expiry == null || expiry.toLocalDate().isBefore(LocalDate.now())) throw new IllegalArgumentException("续证有效期必须为今天或之后");
            Object oldExpiry = current.get("expireDate");
            if (oldExpiry != null && !expiry.toLocalDate().isAfter(LocalDate.parse(String.valueOf(oldExpiry)))) {
                throw new IllegalArgumentException("续证有效期必须晚于原有效期");
            }
            String previousType = (String) current.get("ownerType");
            if (previousType == null || previousType.isBlank()) previousType = type;
            if (current.get("ownerId") instanceof Number previous && (previous.longValue() != owner.id()
                    || !owner.type().equals(previousType))) throw new IllegalArgumentException("续证不能更换所属主体");
            if (!Integer.valueOf(1).equals(request.status())) throw new IllegalArgumentException("续证状态应为有效");
        }
        history.capture(id, "baseline");
        jdbcTemplate.update("""
                UPDATE license_document SET license_name=?, license_no=?, owner_type=?, owner_id=?, owner_code=?, owner_name=?,
                  party_a=?, party_b=?, contract_amount=?, issue_date=?, expire_date=?, status=?, remark=?, revision_no=revision_no+1
                 WHERE license_id=? AND deleted=0
                """, request.licenseName().trim(), nullIfBlank(request.licenseNo()), owner.type(), owner.id(), owner.code(), owner.name(),
                nullIfBlank(request.partyA()), nullIfBlank(request.partyB()), request.contractAmount(), parseDate(request.issueDate()),
                parseDate(request.expireDate()), Integer.valueOf(0).equals(request.status()) ? 0 : 1, nullIfBlank(request.remark()), id);
        history.capture(id, renewal ? "renew" : "update");
        audit.record("license", renewal ? "renew" : "update", id, request.licenseNo(), renewal ? "证照续证生效" : "修改证照");
        return Map.of("id", id);
    }

    public Map<String, Object> ownerOptions(String type, String keyword, Map<String, String> params) {
        return owners.options(type, keyword, params);
    }

    public Map<String, Object> history(Long id) {
        requireExists(id);
        return Map.of("history", history.list(id));
    }

    private Map<String, Object> lockDocument(Long id) {
        var rows = jdbcTemplate.queryForList("""
                SELECT license_type AS ownerLicenseType, owner_type AS ownerType, owner_id AS ownerId,
                       owner_code AS ownerCode, expire_date AS expireDate, revision_no AS revisionNo
                  FROM license_document WHERE license_id=? AND deleted=0 FOR UPDATE
                """, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("证照不存在或已删除");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> remove(Long licenseId) {
        lockDocument(licenseId);
        history.capture(licenseId, "baseline");
        jdbcTemplate.update("UPDATE license_document SET deleted = 1, revision_no=revision_no+1 WHERE license_id = ?", licenseId);
        jdbcTemplate.update("UPDATE sys_attachment SET deleted = 1 WHERE biz_type = 'license' AND biz_id = ?", licenseId);
        history.capture(licenseId, "delete");
        audit.record("license", "delete", licenseId, String.valueOf(licenseId), "软删除证照与附件");
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
        lockDocument(licenseId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的证照附件");
        }
        history.capture(licenseId, "baseline");
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
        Long attachmentId = lastInsertedId();
        jdbcTemplate.update("UPDATE license_document SET revision_no=revision_no+1 WHERE license_id=?", licenseId);
        history.capture(licenseId, "attachment");
        return Map.of("attachmentId", attachmentId);
    }

    public ResponseEntity<Resource> downloadAttachment(Long attachmentId) {
        return attachmentFile("a.attachment_id", attachmentId);
    }

    public ResponseEntity<Resource> downloadAttachmentByStoredName(String storedName) {
        return attachmentFile("a.file_path", storedName);
    }

    private ResponseEntity<Resource> attachmentFile(String selector, Object value) {
        var rows = jdbcTemplate.queryForList("""
                SELECT a.file_name AS fileName, a.file_path AS filePath, a.file_ext AS ext
                  FROM sys_attachment a JOIN license_document l ON l.license_id=a.biz_id
                 WHERE a.biz_type='license' AND a.deleted=0 AND l.deleted=0 AND
                """ + selector + " = ?", value);
        if (rows.size() != 1) throw new IllegalArgumentException("证照附件不存在或已删除");
        var row = rows.get(0);
        Path file = uploadRoot.resolve(String.valueOf(row.get("filePath"))).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) throw new IllegalArgumentException("证照附件文件不存在");
        String encodedName = URLEncoder.encode(String.valueOf(row.get("fileName")), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(mediaTypeOf(String.valueOf(row.get("ext")))))
                .body(new FileSystemResource(file));
    }

    // ======================== helpers ========================

    private void validate(LicenseUpsertRequest request) {
        if (isBlank(request.licenseType()) || isBlank(request.licenseName())) {
            throw new IllegalArgumentException("证照类型与证照名称为必填项");
        }
        Date issue = parseDate(request.issueDate());
        Date expiry = parseDate(request.expireDate());
        if (issue != null && expiry != null && issue.after(expiry)) throw new IllegalArgumentException("签发日期不能晚于有效期");
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
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException("证照日期格式不正确，应为YYYY-MM-DD");
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
