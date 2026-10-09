package com.hospital.spd.integration;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpLicenseLifecycleServiceTest {
  @Test void retirementUsesApprovedAttachmentRatherThanTheLastRejectedPendingFile() {
    var db=mock(JdbcTemplate.class);var service=new McpLicenseLifecycleService(db);
    when(db.queryForList(contains("FROM mcp_license_intent"),eq("C"))).thenReturn(List.of(Map.of("source_product_id",1L,"source_license_id","42","source_updated_at","2026-10-08T02:00:00Z")));
    when(db.queryForList(contains("FROM mcp_license_approved"),eq(1L),eq("42"))).thenReturn(List.of(Map.of("attachment_id",100L,"license_code","REG1","source_updated_at","2026-10-08T01:00:00Z")));
    service.prepareApproval("C");
    verify(db).update(contains("mcp_license_retirement"),eq("C"),eq(100L),eq(1L),eq("REG1"));
  }
  @Test void oldApprovalsCannotOverwriteNewerApprovedCertificates() {
    var db=mock(JdbcTemplate.class);var service=new McpLicenseLifecycleService(db);
    when(db.queryForList(contains("FROM mcp_license_intent"),eq("OLD"))).thenReturn(List.of(Map.of("source_product_id",1L,"source_license_id","42","source_updated_at","2026-10-08T01:00:00Z")));
    when(db.queryForList(contains("FROM mcp_license_approved"),eq(1L),eq("42"))).thenReturn(List.of(Map.of("source_updated_at","2026-10-08T02:00:00Z")));
    assertThrows(IllegalArgumentException.class,()->service.prepareApproval("OLD"));
    verify(db,never()).update(anyString(),any(),any(),any(),any());
  }
  @Test void approvedStatusesAndDatesParticipateInActualEligibility() {
    var db=mock(JdbcTemplate.class);var service=new McpLicenseLifecycleService(db);
    for(String status:List.of("pending","expired","revoked")) {
      when(db.queryForList(contains("FROM mcp_license_approved"),eq("P1"))).thenReturn(List.of(Map.of("license_code","PRODUCTION","metadata","{\"status\":\""+status+"\",\"expiryDate\":\"2099-01-01\"}")));
      assertThrows(IllegalArgumentException.class,()->service.requireEligible("P1"));
    }
    when(db.queryForList(contains("FROM mcp_license_approved"),eq("P1"))).thenReturn(List.of(Map.of("license_code","PRODUCTION","metadata","{\"status\":\"active\",\"issueDate\":\"2099-01-01\",\"expiryDate\":\"2099-02-01\"}")));
    assertThrows(IllegalArgumentException.class,()->service.requireEligible("P1"));
    when(db.queryForList(contains("FROM mcp_license_approved"),eq("P1"))).thenReturn(List.of(Map.of("license_code","PRODUCTION","metadata","{\"status\":\"unlinked\"}")));
    assertDoesNotThrow(()->service.requireEligible("P1"));
  }
}
