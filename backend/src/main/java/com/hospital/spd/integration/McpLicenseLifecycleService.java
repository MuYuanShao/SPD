package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Separates approved certificates from pending versions and publishes qualification state only after approval. */
public class McpLicenseLifecycleService {
  private final JdbcTemplate db;
  private final ObjectMapper mapper=new ObjectMapper();
  public McpLicenseLifecycleService(JdbcTemplate db){this.db=db;}
  public void prepareApproval(String application) {
    for(var intent:db.queryForList("SELECT * FROM mcp_license_intent WHERE application_no=? ORDER BY source_product_id,source_license_id",application)) {
      var approved=db.queryForList("SELECT * FROM mcp_license_approved WHERE source_product_id=? AND source_license_id=? FOR UPDATE",intent.get("source_product_id"),intent.get("source_license_id"));
      if(!approved.isEmpty()) {
        var old=approved.get(0);
        if(!Instant.parse(intent.get("source_updated_at").toString()).isAfter(Instant.parse(old.get("source_updated_at").toString())))
          throw new IllegalArgumentException("证照已有较新的审批版本，不能用旧申请覆盖");
        if(old.get("attachment_id") instanceof Number attachment)
          db.update("INSERT IGNORE INTO mcp_license_retirement(application_no,source_attachment_id,source_product_id,license_code) VALUES(?,?,?,?)",application,attachment.longValue(),intent.get("source_product_id"),old.get("license_code"));
      }
    }
  }
  public void publishApproval(String application) {
    db.update("""
        INSERT INTO mcp_license_approved(source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,application_no,source_updated_at)
        SELECT source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,application_no,source_updated_at
        FROM mcp_license_intent WHERE application_no=?
        ON DUPLICATE KEY UPDATE product_code=VALUES(product_code),license_code=VALUES(license_code),attachment_id=VALUES(attachment_id),metadata=VALUES(metadata),application_no=VALUES(application_no),source_updated_at=VALUES(source_updated_at)
        """,application);
  }
  public void requireEligible(String productCode) {
    for(var row:db.queryForList("SELECT license_code,metadata FROM mcp_license_approved WHERE product_code=?",productCode)) requireValid(row);
  }
  public void requireAdmission(String application) {
    for(var row:db.queryForList("SELECT license_code,metadata FROM mcp_license_intent WHERE application_no=?",application)) requireValid(row);
  }
  private void requireValid(Map<String,Object> row) {
    JsonNode metadata;
    try {metadata=mapper.readTree(row.get("metadata").toString());}catch(Exception e){throw new IllegalArgumentException("来源证照数据不合法");}
    String status=metadata.path("status").asText();
    if(status.equals("unlinked")) return;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate expiry=date(metadata.path("expiryDate")),issue=date(metadata.path("issueDate"));
    if(!status.equals("active") || (expiry!=null && expiry.isBefore(today)) || (issue!=null && issue.isAfter(today)))
      throw new IllegalArgumentException("来源证照已过期、失效或尚未生效，不能准入或采购："+row.get("license_code"));
  }
  private LocalDate date(JsonNode node){return node.isMissingNode() || node.isNull() || node.asText().isBlank()?null:LocalDate.parse(node.asText());}
}
