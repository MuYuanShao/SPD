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
    private static final int MAX_FILES = 10;

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final Path uploadRoot;

    public PendingProductAttachmentService(JdbcTemplate jdbcTemplate,
                                           OperatorContextProvider operatorContextProvider,
                                           ApprovalFlowGuard approvalFlowGuard,
                                           @Value("${spd.upload.dir:./output/private-files}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("pending-products");
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
        if (count != null && count >= MAX_FILES) throw new IllegalArgumentException("每个申请最多上传 10 个附件");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("附件读取失败", ex);
        }
        FileKind kind = detect(bytes);
        String originalName = file.getOriginalFilename() == null ? "attachment" + kind.extension()
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        if (!originalName.toLowerCase().endsWith(kind.extension())) {
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
        Path file = uploadRoot.resolve(String.valueOf(row.get("filePath"))).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("证照附件文件不存在");
        }
        String encodedName = URLEncoder.encode(String.valueOf(row.get("fileName")), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(String.valueOf(row.get("contentType"))))
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
                SELECT application_id AS applicationId, submit_by AS submitBy, approval_status AS approvalStatus
                  FROM pending_product_application WHERE application_no = ?
                """, applicationNo);
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData() || Objects.equals(operator.userId(), nullableNumber(row.get("submitBy")))) return row;
        Long handled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_product_approval_action
                 WHERE application_id = ? AND actor_id = ?
                """, Long.class, number(row.get("applicationId")), operator.userId());
        if (handled != null && handled > 0) return row;
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
        PDF(".pdf", "application/pdf"), PNG(".png", "image/png"),
        JPEG(".jpg", "image/jpeg"), WEBP(".webp", "image/webp");
        private final String extension;
        private final String mediaType;
        FileKind(String extension, String mediaType) { this.extension = extension; this.mediaType = mediaType; }
        String extension() { return extension; }
        String mediaType() { return mediaType; }
    }
}
