package com.hospital.spd.masterdata.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;

/** Reads only current attachments owned by an admitted product under hospital catalog permissions. */
@Service
public class HospitalProductAttachmentService {
    private final JdbcTemplate db;
    private final Path uploads;
    public HospitalProductAttachmentService(JdbcTemplate db,@Value("${spd.upload.dir:./output/private-files}") String root) {
        this.db=db;uploads=Path.of(root).toAbsolutePath().normalize();
    }
    public ResponseEntity<Resource> file(String code,long attachment) {
        var rows=db.queryForList("""
                SELECT a.file_name,a.file_path,a.file_type,source.biz_type AS source_type
                FROM sys_attachment a JOIN product p ON p.product_id=a.biz_id
                LEFT JOIN sys_attachment source ON source.attachment_id=a.source_attachment_id
                WHERE a.attachment_id=? AND a.biz_type='product' AND a.deleted=0 AND p.product_code=? AND p.deleted=0
                """,attachment,code);
        if(rows.size()!=1)throw new IllegalArgumentException("当前商品不存在此附件或附件已退役");
        var row=rows.get(0);Path root="pending_product_application".equals(row.get("source_type"))?uploads.resolve("pending-products"):uploads;
        Path file=root.resolve(row.get("file_path").toString()).normalize();
        if(!file.startsWith(root) || !Files.isRegularFile(file))throw new IllegalArgumentException("商品附件文件不存在");
        String type=String.valueOf(row.get("file_type"));
        if(!List.of("application/pdf","image/png","image/jpeg","image/webp","image/gif").contains(type))type=MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String name=URLEncoder.encode(row.get("file_name").toString(),StandardCharsets.UTF_8).replace("+","%20");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename*=UTF-8''"+name)
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(file));
    }
}
