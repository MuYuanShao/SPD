package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.masterdata.PendingProductApplicationRequest;
import com.hospital.spd.masterdata.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Proves MCP receiving writes pending applications and never changes hospital products. */
class McpReceiveServiceTest {
  final JdbcTemplate db=mock(JdbcTemplate.class);
  final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
  final MockEnvironment env=new MockEnvironment().withProperty("SPD_MCP_ORGANIZATION","H1");
  final ProductApprovalService approvals=mock(ProductApprovalService.class);
  final ProductService products=mock(ProductService.class);
  final PendingProductAttachmentService attachments=mock(PendingProductAttachmentService.class);
  final AuditLogService audit=mock(AuditLogService.class);
  final McpReceiveService service=new McpReceiveService(db,mapper,env,approvals,products,attachments,audit);
  final String key="00000000-0000-0000-0000-000000000001";
  ObjectNode envelope() {
    var e=mapper.createObjectNode();e.put("syncId",key);e.put("organization","H1");e.put("sourceSystem","MCP-UI");
    var p=e.putObject("payload");p.put("productId",1);p.put("sourceUpdatedAt","2026-10-08T00:00:00Z");p.putArray("licenses");
    var product=p.putObject("product");product.put("productCode","P1");product.put("productName","测试商品");product.put("specModel","5ml");product.put("unit","支");product.put("status","active");
    return e;
  }
  @Test void rejectsCrossHospitalEnvelopesBeforeDatabaseAccess() {
    var e=envelope();e.put("organization","H2");
    assertThrows(IllegalArgumentException.class,()->service.receive(key,e));verifyNoInteractions(db,approvals,products);
  }
  @Test void createsPendingCatalogApplicationRatherThanHospitalProduct() {
    when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","","source_updated_at",""));
    when(db.queryForObject(contains("COUNT(*) FROM product"),eq(Integer.class),eq("P1"))).thenReturn(0);
    when(approvals.createApplication(any())).thenReturn(Map.of("applicationNo","APP1","productCode","P1"));
    when(db.queryForObject(contains("SELECT application_id"),eq(Long.class),eq("APP1"))).thenReturn(10L);
    var result=service.receive(key,envelope());
    assertEquals("processed",result.get("state"));assertTrue(result.get("message").toString().contains("待审批目录"));
    var captured=org.mockito.ArgumentCaptor.forClass(PendingProductApplicationRequest.class);
    verify(approvals).createApplication(captured.capture());assertEquals("新品准入",captured.getValue().applicationType());
    verifyNoInteractions(products);verify(audit).record(eq("pending_product_application"),eq("mcp_receive"),eq(10L),eq("APP1"),anyString());
  }
  @Test void olderVersionReturnsReceiptWithoutOverwritingPendingCatalog() {
    when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","APP1","source_updated_at","2026-10-09T00:00:00Z"));
    var result=service.receive(key,envelope());assertEquals("processed",result.get("state"));
    verifyNoInteractions(approvals,products,attachments);
  }
  @Test void duplicateIdempotencyKeyReturnsOriginalResult() {
    var e=envelope();when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","APP1","source_updated_at","2026-10-08T00:00:00Z"));
    when(db.queryForList(contains("SELECT request_hash"),eq(key))).thenReturn(List.of(Map.of("request_hash",McpReceiveService.hash(e.toString()),"result_json","{\"state\":\"processed\",\"productCode\":\"P1\"}")));
    assertEquals("processed",service.receive(key,e).get("state"));verifyNoInteractions(approvals,products,attachments);
    ((ObjectNode)e.path("payload").path("product")).put("productName","不同请求");assertThrows(IllegalArgumentException.class,()->service.receive(key,e));
  }
  @Test void cannotUploadLicensesBeforePendingProductExists() {
    var e=envelope();((ObjectNode)e.path("payload")).put("licensesOnly",true);
    when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","","source_updated_at",""));
    assertThrows(IllegalArgumentException.class,()->service.receive(key,e));verifyNoInteractions(approvals,products,attachments);
  }
  @Test void certificateOnlyRequestsDoNotPersistDetailDisplayPlaceholders() {
    var e=envelope();((ObjectNode)e.path("payload")).put("licensesOnly",true);
    when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","OLD","source_updated_at","2026-10-07T00:00:00Z","license_updated_at","2026-10-07T00:00:00Z"));
    when(approvals.getDetail("OLD")).thenReturn(mapper.convertValue(Map.of("applicationNo","OLD","applicationType","信息变更","approvalStatus","approved"),com.hospital.spd.masterdata.PendingProductApplicationDetail.class));
    when(db.queryForObject(contains("COUNT(*) FROM product"),eq(Integer.class),eq("P1"))).thenReturn(1);
    when(products.hospitalProductDetail("P1")).thenReturn(mapper.convertValue(Map.of("productCode","P1","productName","商品","specModel","5ml","unit","支","registrationExpireDate","-","brand","-","statusLabel","停用"),com.hospital.spd.masterdata.ProductDetail.class));
    when(approvals.createApplication(any())).thenReturn(Map.of("applicationNo","NEW","productCode","P1"));
    when(db.queryForObject(contains("SELECT application_id"),eq(Long.class),eq("NEW"))).thenReturn(20L);
    service.receive(key,e);
    var request=org.mockito.ArgumentCaptor.forClass(PendingProductApplicationRequest.class);
    verify(approvals).createApplication(request.capture());
    assertNull(request.getValue().registrationExpireDate());assertNull(request.getValue().brand());
    assertEquals("资质更新",request.getValue().applicationType());
  }
  @Test void mapsPurchaseStatusesWithoutIncludingVoidedOrRejectedOrders() {
    assertEquals("pending_approval",McpUploadJob.orderStatus("pending_approval"));assertEquals("approved",McpUploadJob.orderStatus("approved"));
    assertEquals("voided",McpUploadJob.orderStatus("voided"));assertEquals("rejected",McpUploadJob.orderStatus("rejected"));
    assertEquals("sent",McpUploadJob.orderStatus("sent"));
    assertThrows(IllegalArgumentException.class,()->McpUploadJob.orderStatus("unexpected"));
  }
  @Test void decodesRealAttachmentBytesAndAssociatesThemWithPendingApplication() throws Exception {
    var e=envelope();
    var licenses=(com.fasterxml.jackson.databind.node.ArrayNode)e.path("payload").path("licenses");
    var l=licenses.addObject();l.put("licenseCode","REG1");l.put("licenseType","注册证");l.put("productCode","P1");l.put("status","active");l.put("expiryDate","2030-01-01");l.put("fileName","certificate.pdf");
    byte[] bytes="%PDF-1.4\nreal attachment bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    l.put("attachmentBase64",Base64.getEncoder().encodeToString(bytes));
    when(db.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("product_code","P1","application_no","","source_updated_at",""));
    when(db.queryForObject(contains("COUNT(*) FROM product"),eq(Integer.class),eq("P1"))).thenReturn(0);
    when(approvals.createApplication(any())).thenReturn(Map.of("applicationNo","APP1","productCode","P1"));
    when(db.queryForObject(contains("SELECT application_id"),eq(Long.class),eq("APP1"))).thenReturn(10L);
    when(attachments.upload(eq("APP1"),any())).thenReturn(Map.of("attachmentId",42L));
    service.receive(key,e);
    var captured=org.mockito.ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
    verify(attachments).upload(eq("APP1"),captured.capture());assertArrayEquals(bytes,captured.getValue().getBytes());verifyNoInteractions(products);
  }
}
