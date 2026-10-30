package com.hospital.spd.licenses;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.integration.Phase3Json;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.Objects;

/** Projects approved MCP certificates into the shared ledger within the owning catalog approval transaction. */
public final class McpLicenseLedgerService {
    private final JdbcTemplate db;
    private final OperatorContextProvider operators;
    private final LicenseHistoryService history;
    private final AuditLogService audit;
    public McpLicenseLedgerService(JdbcTemplate db,OperatorContextProvider operators) {
        this.db=db;this.operators=operators;history=new LicenseHistoryService(db,operators);audit=new AuditLogService(db,operators);
    }
    public void publish(String application) {
        for(var source:db.queryForList("""
                SELECT a.*,p.product_id,p.product_name FROM mcp_license_approved a
                JOIN product p ON p.product_code=a.product_code WHERE a.application_no=?
                ORDER BY a.source_product_id,a.source_license_id FOR UPDATE
                """,application)) {
            long sourceProduct=((Number)source.get("source_product_id")).longValue();
            String identity=source.get("source_license_id").toString(),version=source.get("source_updated_at").toString();
            JsonNode metadata=Phase3Json.parse(source.get("metadata").toString());
            var existing=db.queryForList("SELECT license_id,source_updated_at,source_application_no FROM license_document WHERE source_product_id=? AND source_license_id=? FOR UPDATE",sourceProduct,identity);
            Long id=null;
            if(!existing.isEmpty()) {
                var row=existing.get(0);id=((Number)row.get("license_id")).longValue();
                if(version.equals(row.get("source_updated_at")) && application.equals(row.get("source_application_no")))continue;
                if(Instant.parse(version).isBefore(Instant.parse(row.get("source_updated_at").toString())))throw new IllegalArgumentException("不能以旧审批覆盖较新的来源证照台账");
                history.capture(id,"baseline");
            }
            boolean unlinked=metadata.path("status").asText().equals("unlinked");
            String name=metadata.path("licenseName").asText("");if(name.isBlank())name=source.get("license_code").toString();
            db.update("""
                    INSERT INTO license_document(license_type,license_name,license_no,owner_type,owner_id,owner_code,owner_name,
                      issue_date,expire_date,status,remark,deleted,create_by,source_product_id,source_license_id,source_updated_at,source_application_no)
                    VALUES('product',?,?,'product',?,?,?,?,?,?,'MCP-UI来源证照，变更须经过院内目录审批',?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE license_name=VALUES(license_name),license_no=VALUES(license_no),owner_id=VALUES(owner_id),
                      owner_code=VALUES(owner_code),owner_name=VALUES(owner_name),issue_date=VALUES(issue_date),expire_date=VALUES(expire_date),
                      status=VALUES(status),deleted=VALUES(deleted),source_updated_at=VALUES(source_updated_at),source_application_no=VALUES(source_application_no),revision_no=revision_no+1
                    """,name,source.get("license_code"),source.get("product_id"),source.get("product_code"),source.get("product_name"),
                    date(metadata,"issueDate"),date(metadata,"expiryDate"),metadata.path("status").asText().equals("active")?1:0,
                    unlinked?1:0,operators.current().userId(),sourceProduct,identity,version,application);
            if(id==null)id=db.queryForObject("SELECT license_id FROM license_document WHERE source_product_id=? AND source_license_id=?",Long.class,sourceProduct,identity);
            db.update("UPDATE sys_attachment SET deleted=1 WHERE biz_type='license' AND biz_id=?",id);
            if(!unlinked && source.get("attachment_id") instanceof Number attachment) {
                db.update("""
                        INSERT INTO sys_attachment(biz_type,biz_id,file_name,file_ext,file_type,file_size,file_path,file_url,
                          category,description,valid_date,create_by,source_attachment_id)
                        SELECT 'license',?,s.file_name,s.file_ext,s.file_type,s.file_size,CONCAT('pending-products/',s.file_path),
                          CONCAT('/pending-product-applications/attachments/',s.attachment_id,'/file'),s.category,s.description,s.valid_date,s.create_by,s.attachment_id
                        FROM sys_attachment s WHERE s.attachment_id=? AND s.biz_type='pending_product_application' AND s.deleted=0
                        ON DUPLICATE KEY UPDATE deleted=0,valid_date=VALUES(valid_date),description=VALUES(description)
                        """,id,attachment.longValue());
            }
            history.capture(id,"mcp_import");
            audit.record("license","mcp_import",id,Objects.toString(source.get("license_code"),""),"MCP证照审批发布："+application);
        }
    }
    private static String date(JsonNode metadata,String key) {String value=metadata.path(key).asText("");return value.isBlank()?null:value;}
}
