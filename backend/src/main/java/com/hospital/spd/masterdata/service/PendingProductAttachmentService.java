package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stores and authorizes real qualification attachments for pending product applications. */
@Service
public class PendingProductAttachmentService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    /**
     * 单个申请允许上传的附件数量上限。
     * ---------------------------------------------------------------------
     * 需求：「新增」弹窗要支持一次上传多张图片，资质审阅要能自然排布多张图。
     * 原来的 10 个上限偏紧（多张资质照 + 多张 PDF 很容易撞顶），
     * 这里放宽到 20 个，仍然保留上限以避免单据附件无限膨胀。
     */
    private static final int MAX_FILES = 20;

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final Path uploadRoot;
    private final Path licenseUploadRoot;
    private final CatalogApprovalRouteService catalogApprovalRouteService;

    public PendingProductAttachmentService(JdbcTemplate jdbcTemplate,
                                           OperatorContextProvider operatorContextProvider,
                                           ApprovalFlowGuard approvalFlowGuard,
                                           @Value("${spd.upload.dir:./output/private-files}") String uploadDir) {
        this(jdbcTemplate, operatorContextProvider, approvalFlowGuard, uploadDir, null);
    }

    @Autowired
    public PendingProductAttachmentService(JdbcTemplate jdbcTemplate,
                                           OperatorContextProvider operatorContextProvider,
                                           ApprovalFlowGuard approvalFlowGuard,
                                           @Value("${spd.upload.dir:./output/private-files}") String uploadDir,
                                           CatalogApprovalRouteService catalogApprovalRouteService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
        this.catalogApprovalRouteService = catalogApprovalRouteService;
        this.licenseUploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.uploadRoot = licenseUploadRoot.resolve("pending-products");
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("待审批附件目录初始化失败", ex);
        }
    }

    public List<Map<String, Object>> list(String applicationNo) {
        Map<String, Object> application = requireAccess(applicationNo);
        return jdbcTemplate.queryForList("""
                SELECT attachment_id AS attachmentId, file_name AS fileName, file_type AS contentType,
                       file_size AS fileSize, category,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM sys_attachment
                 WHERE biz_type = 'pending_product_application' AND biz_id = ? AND deleted = 0
                 ORDER BY attachment_id
                """, number(application.get("applicationId")));
    }

    /** Includes current linked licenses without changing the immutable submitted approval route. */
    public List<Map<String, Object>> qualificationAttachments(String applicationNo) {
        Map<String, Object> application = requireAccess(applicationNo);
        Long applicationId = number(application.get("applicationId"));
        return jdbcTemplate.queryForList("""
                SELECT a.attachment_id AS attachmentId, a.file_name AS fileName, a.file_type AS contentType,
                       a.file_size AS fileSize, a.category, DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       'application' AS source, NULL AS licenseName, NULL AS licenseNo, NULL AS ownerName,
                       NULL AS licenseType, NULL AS expireDate, NULL AS licenseStatus
                  FROM sys_attachment a
                 WHERE a.biz_type='pending_product_application' AND a.biz_id=? AND a.deleted=0
                UNION ALL
                SELECT a.attachment_id, a.file_name, a.file_type, a.file_size, a.category,
                       DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i'), 'license', l.license_name,
                       l.license_no, l.owner_name, l.license_type, DATE_FORMAT(l.expire_date, '%Y-%m-%d'),
                       CASE WHEN l.status=0 THEN 'invalid' WHEN l.expire_date<CURRENT_DATE THEN 'expired'
                            WHEN l.issue_date>CURRENT_DATE THEN 'not_effective' ELSE 'valid' END
                  FROM sys_attachment a JOIN license_document l ON a.biz_type='license' AND a.biz_id=l.license_id
                  JOIN pending_product_application p ON p.application_id=?
                  LEFT JOIN product pr ON pr.product_code=p.product_code AND pr.deleted=0
                 WHERE a.deleted=0 AND l.deleted=0
                   AND NOT EXISTS (SELECT 1 FROM sys_attachment direct
                     WHERE direct.attachment_id=a.source_attachment_id AND direct.biz_type='pending_product_application'
                       AND direct.biz_id=p.application_id AND direct.deleted=0)
                   AND (l.license_type<>'contract' OR l.license_no=p.contract_code)
                   AND (
                     (COALESCE(NULLIF(l.owner_type,''),l.license_type)='product' AND
                       (l.owner_id=pr.product_id OR (l.owner_id IS NULL AND l.owner_code=p.product_code)))
                     OR (COALESCE(NULLIF(l.owner_type,''),l.license_type)='supplier' AND
                       (l.owner_id=COALESCE(p.supplier_id,pr.supplier_id) OR (l.owner_id IS NULL AND l.owner_code=
                         (SELECT supplier_code FROM supplier WHERE supplier_id=COALESCE(p.supplier_id,pr.supplier_id)))))
                     OR (COALESCE(NULLIF(l.owner_type,''),l.license_type)='manufacturer' AND
                       (l.owner_id=COALESCE(p.manufacturer_id,pr.manufacturer_id) OR (l.owner_id IS NULL AND l.owner_code=
                         (SELECT manufacturer_code FROM manufacturer WHERE manufacturer_id=COALESCE(p.manufacturer_id,pr.manufacturer_id)))))
                   )
                 ORDER BY source, attachmentId
                """, applicationId, applicationId);
    }

    public ResponseEntity<Resource> previewQualification(String applicationNo, Long attachmentId) {
        Map<String, Object> linked = qualificationAttachments(applicationNo).stream()
                .filter(row -> Objects.equals(number(row.get("attachmentId")), attachmentId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("附件未关联当前审批申请或已删除"));
        String storedName = jdbcTemplate.queryForObject("SELECT file_path FROM sys_attachment WHERE attachment_id=? AND deleted=0",
                String.class, attachmentId);
        Path root = "license".equals(linked.get("source")) ? licenseUploadRoot : uploadRoot;
        return fileResponse(root, storedName, String.valueOf(linked.get("fileName")), String.valueOf(linked.get("contentType")));
    }

    @Transactional
    public Map<String, Object> upload(String applicationNo, MultipartFile file) {
        Map<String, Object> application = requireAccess(applicationNo);
        Long applicationId = number(application.get("applicationId"));
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择证照附件");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("单个附件不能超过 20 MB");
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_attachment
                 WHERE biz_type = 'pending_product_application' AND biz_id = ? AND deleted = 0
                """, Integer.class, applicationId);
        if (count != null && count >= MAX_FILES) throw new IllegalArgumentException("每个申请最多上传 " + MAX_FILES + " 个附件");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("附件读取失败", ex);
        }
        FileKind kind = detect(bytes);
        String originalName = sanitizeOriginalName(file.getOriginalFilename(), kind);
        if (!kind.accepts(originalName)) {
            throw new IllegalArgumentException("文件扩展名与文件签名不一致");
        }
        String storedName = UUID.randomUUID() + kind.extension();
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) throw new IllegalArgumentException("附件路径不合法");
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw new IllegalStateException("证照附件保存失败", ex);
        }
        jdbcTemplate.update("""
                INSERT INTO sys_attachment (
                  biz_type, biz_id, file_name, file_ext, file_type, file_size,
                  file_path, file_url, category, create_by
                ) VALUES ('pending_product_application', ?, ?, ?, ?, ?, ?, '', 'qualification', ?)
                """, applicationId, originalName, kind.extension().substring(1), kind.mediaType(), bytes.length,
                storedName, operatorContextProvider.current().userId());
        refreshAttachmentCount(applicationId);
        Long attachmentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Map.of("attachmentId", attachmentId == null ? 0L : attachmentId);
    }

    public ResponseEntity<Resource> preview(Long attachmentId) {
        Map<String, Object> row = attachment(attachmentId);
        requireAccess(String.valueOf(row.get("applicationNo")));
        return fileResponse(uploadRoot, String.valueOf(row.get("filePath")),
                String.valueOf(row.get("fileName")), String.valueOf(row.get("contentType")));
    }

    private ResponseEntity<Resource> fileResponse(Path root, String storedName, String name, String contentType) {
        if (storedName == null || storedName.isBlank()) throw new IllegalArgumentException("证照附件文件不存在");
        Path file = root.resolve(storedName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("证照附件文件不存在");
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        String safeContentType = List.of("application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif").contains(contentType)
                ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(safeContentType))
                .body(new FileSystemResource(file));
    }

    @Transactional
    public Map<String, Object> delete(String applicationNo, Long attachmentId) {
        Map<String, Object> application = requireAccess(applicationNo);
        Long applicationId = number(application.get("applicationId"));
        int updated = jdbcTemplate.update("""
                UPDATE sys_attachment SET deleted = 1
                 WHERE attachment_id = ? AND biz_type = 'pending_product_application'
                   AND biz_id = ? AND deleted = 0
                """, attachmentId, applicationId);
        if (updated != 1) throw new IllegalArgumentException("附件不存在或已删除");
        refreshAttachmentCount(applicationId);
        return Map.of("attachmentId", attachmentId);
    }

    private Map<String, Object> attachment(Long attachmentId) {
        return jdbcTemplate.queryForMap("""
                SELECT a.file_name AS fileName, a.file_path AS filePath, a.file_type AS contentType,
                       p.application_no AS applicationNo
                  FROM sys_attachment a
                  JOIN pending_product_application p ON p.application_id = a.biz_id
                 WHERE a.attachment_id = ? AND a.biz_type = 'pending_product_application' AND a.deleted = 0
                """, attachmentId);
    }

    private Map<String, Object> requireAccess(String applicationNo) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT a.application_id AS applicationId, a.submit_by AS submitBy,
                       a.approval_status AS approvalStatus, a.approval_round AS approvalRound,
                       a.application_type AS applicationType, u.dept_id AS documentDeptId
                  FROM pending_product_application a LEFT JOIN sys_user u ON u.user_id = a.submit_by
                 WHERE a.application_no = ?
                """, applicationNo);
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData() || Objects.equals(operator.userId(), nullableNumber(row.get("submitBy")))) return row;
        Long handled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_product_approval_action
                 WHERE application_id = ? AND actor_id = ?
                """, Long.class, number(row.get("applicationId")), operator.userId());
        if (handled != null && handled > 0) return row;
        if (catalogApprovalRouteService != null) {
            long applicationId = number(row.get("applicationId"));
            int approvalRound = ((Number) row.get("approvalRound")).intValue();
            Long documentDeptId = nullableNumber(row.get("documentDeptId"));
            catalogApprovalRouteService.ensureLegacyRoute(applicationId, approvalRound,
                    String.valueOf(row.get("applicationType")), String.valueOf(row.get("approvalStatus")),
                    documentDeptId);
            catalogApprovalRouteService.requireApprovalAccess(
                    catalogApprovalRouteService.currentStep(applicationId, approvalRound),
                    documentDeptId, nullableNumber(row.get("submitBy")));
            return row;
        }
        approvalFlowGuard.requireApprovalAccess("pending-product-catalog", "initial-review",
                stepOrder(String.valueOf(row.get("approvalStatus"))), null, nullableNumber(row.get("submitBy")));
        return row;
    }

    private void refreshAttachmentCount(Long applicationId) {
        jdbcTemplate.update("""
                UPDATE pending_product_application p
                   SET qualification_attachment_count = (
                     SELECT COUNT(*) FROM sys_attachment a
                      WHERE a.biz_type = 'pending_product_application'
                        AND a.biz_id = p.application_id AND a.deleted = 0
                   )
                 WHERE p.application_id = ?
                """, applicationId);
    }

    /**
     * Derives the stored/display name of an uploaded file.
     * The original name is opaque user data and may legitimately contain spaces,
     * so only path separators and control characters are stripped here.
     */
    private static String sanitizeOriginalName(String originalFileName, FileKind kind) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "attachment" + kind.extension();
        }
        String sanitized = originalFileName.replace('\\', '/');
        int separator = sanitized.lastIndexOf('/');
        if (separator >= 0) sanitized = sanitized.substring(separator + 1);
        StringBuilder builder = new StringBuilder(sanitized.length());
        for (int index = 0; index < sanitized.length(); index++) {
            char character = sanitized.charAt(index);
            if (character < 0x20 || character == 0x7F) continue;
            builder.append(character);
        }
        String cleaned = builder.toString().trim();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "attachment" + kind.extension();
        }
        return cleaned;
    }

    private static FileKind detect(byte[] bytes) {
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return FileKind.PDF;
        if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return FileKind.PNG;
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return FileKind.JPEG;
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return FileKind.WEBP;
        throw new IllegalArgumentException("文件签名不受支持，仅允许 PDF/PNG/JPEG/WebP");
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }

    private static int stepOrder(String status) {
        if ("pending_initial".equals(status)) return 1;
        if ("pending_final".equals(status)) return 2;
        if (status.startsWith("pending_step_")) {
            try { return Integer.parseInt(status.substring("pending_step_".length())); }
            catch (NumberFormatException ignored) { return 1; }
        }
        return 1;
    }

    private static Long number(Object value) { return ((Number) value).longValue(); }
    private static Long nullableNumber(Object value) { return value instanceof Number number ? number.longValue() : null; }

    private enum FileKind {
        PDF(".pdf", List.of(".pdf"), "application/pdf"),
        PNG(".png", List.of(".png"), "image/png"),
        JPEG(".jpg", List.of(".jpg", ".jpeg"), "image/jpeg"),
        WEBP(".webp", List.of(".webp"), "image/webp");
        private final String extension;
        private final List<String> acceptedExtensions;
        private final String mediaType;
        FileKind(String extension, List<String> acceptedExtensions, String mediaType) {
            this.extension = extension;
            this.acceptedExtensions = acceptedExtensions;
            this.mediaType = mediaType;
        }
        String extension() { return extension; }
        String mediaType() { return mediaType; }
        /** Accepts any equivalent alias of the detected kind, ignoring case. */
        boolean accepts(String fileName) {
            if (fileName == null) return false;
            String lowerCased = fileName.toLowerCase(java.util.Locale.ROOT);
            return acceptedExtensions.stream().anyMatch(lowerCased::endsWith);
        }
    }
}
