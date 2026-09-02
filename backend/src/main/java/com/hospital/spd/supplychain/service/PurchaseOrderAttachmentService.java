package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hospital.spd.common.SqlHelper.isBlank;

/**
 * Stores and serves authenticated purchase-order attachments through the shared attachment table.
 */
@Service
public class PurchaseOrderAttachmentService {

    private static final String BIZ_TYPE = "purchase_order";

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final Path uploadRoot;
    private final PurchasePermissionGuard permissionGuard;
    private final AuditLogService auditLogService;

    public PurchaseOrderAttachmentService(JdbcTemplate jdbcTemplate,
                                          OperatorContextProvider operatorContextProvider,
                                          @Value("${spd.purchase-order.upload.dir:./output/purchase-order-files}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.permissionGuard = new PurchasePermissionGuard(jdbcTemplate, operatorContextProvider);
        this.auditLogService = new AuditLogService(jdbcTemplate, operatorContextProvider);
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("采购订单附件目录初始化失败: " + uploadRoot, e);
        }
    }

    public List<Map<String, Object>> list(String orderNo) {
        Long orderId = orderId(orderNo);
        return jdbcTemplate.queryForList("""
                SELECT attachment_id AS id, file_name AS fileName, file_ext AS ext,
                       file_type AS fileType, file_size AS size, category, description,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM sys_attachment
                 WHERE biz_type = ? AND biz_id = ? AND deleted = 0
                 ORDER BY attachment_id DESC
                """, BIZ_TYPE, orderId);
    }

    @Transactional
    public Map<String, Object> upload(String orderNo, MultipartFile file, String category) {
        permissionGuard.require("purchase-order:attachment");
        Long orderId = orderId(orderNo);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的采购订单附件");
        }
        String originalName = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        String storedName = "purchase-order-" + orderId + "-" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("采购订单附件路径不合法");
        }
        try {
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("采购订单附件保存失败", e);
        }
        jdbcTemplate.update("""
                INSERT INTO sys_attachment (
                  biz_type, biz_id, file_name, file_ext, file_type, file_size,
                  file_path, file_url, category, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, BIZ_TYPE, orderId, originalName, ext.replace(".", ""), mediaTypeOf(ext), file.getSize(),
                storedName, "/purchase-orders/attachments/file/" + storedName,
                isBlank(category) ? "other" : category.trim(), operatorContextProvider.current().userId());
        Long attachmentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.record(BIZ_TYPE, "upload_attachment", orderId, orderNo,
                "上传采购订单附件：" + originalName);
        return Map.of("attachmentId", attachmentId == null ? 0L : attachmentId);
    }

    public ResponseEntity<Resource> file(Long attachmentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT file_name AS fileName, file_path AS filePath, file_ext AS ext
                  FROM sys_attachment
                 WHERE attachment_id = ? AND biz_type = ? AND deleted = 0
                """, attachmentId, BIZ_TYPE);
        Path file = uploadRoot.resolve(String.valueOf(row.get("filePath"))).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("采购订单附件文件不存在");
        }
        String fileName = String.valueOf(row.get("fileName"));
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(mediaTypeOf(String.valueOf(row.get("ext")))))
                .body(new FileSystemResource(file));
    }

    private Long orderId(String orderNo) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT purchase_order_id FROM purchase_order WHERE order_no = ?", Long.class, orderNo);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("采购订单不存在");
        }
        return ids.get(0);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase();
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
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
}
