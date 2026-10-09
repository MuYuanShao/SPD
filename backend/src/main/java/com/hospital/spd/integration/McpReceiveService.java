package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.spd.masterdata.PendingProductApplicationRequest;
import com.hospital.spd.masterdata.service.ProductApprovalService;
import com.hospital.spd.masterdata.service.ProductService;
import com.hospital.spd.masterdata.service.PendingProductAttachmentService;
import com.hospital.spd.common.service.AuditLogService;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Lands MCP products and real qualification attachments in the existing pending catalog, never the hospital catalog. */
@Service
public class McpReceiveService {
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final Environment env;
  private final ProductApprovalService approvals;
  private final ProductService products;
  private final PendingProductAttachmentService attachments;
  private final AuditLogService audit;
  public McpReceiveService(JdbcTemplate db,ObjectMapper mapper,Environment env,ProductApprovalService approvals,ProductService products,PendingProductAttachmentService attachments,AuditLogService audit) {
    this.db=db;this.mapper=mapper;this.env=env;this.approvals=approvals;this.products=products;this.attachments=attachments;this.audit=audit;
  }
  @Transactional
  public Map<String,Object> receive(String key,JsonNode envelope) {
    require(envelope!=null && key.equals(envelope.path("syncId").asText()),"同步编号与幂等键不一致");
    UUID.fromString(key);
    require("MCP-UI".equals(envelope.path("sourceSystem").asText()),"来源系统不合法");
    require(!env.getProperty("SPD_MCP_ORGANIZATION","").isBlank() && env.getProperty("SPD_MCP_ORGANIZATION").equals(envelope.path("organization").asText()),"机构标识不匹配");
    JsonNode payload=envelope.path("payload");JsonNode product=payload.path("product");
    long sourceId=payload.path("productId").asLong();require(sourceId>0,"商品ID不合法");
    String code=product.path("productCode").asText();require(!code.isBlank() && code.length()<=50,"商品编码不合法");
    Instant version=Instant.parse(payload.path("sourceUpdatedAt").asText());
    String hash=hash(envelope.toString());
    db.update("INSERT INTO mcp_inbound_product(source_product_id,product_code,application_no,source_updated_at) VALUES(?,?,'','') ON DUPLICATE KEY UPDATE source_product_id=source_product_id",sourceId,code);
    Map<String,Object> mapping;
    try {mapping=db.queryForMap("SELECT * FROM mcp_inbound_product WHERE source_product_id=? FOR UPDATE",sourceId);}
    catch(org.springframework.dao.EmptyResultDataAccessException e) {throw new IllegalArgumentException("商品编码已关联其他来源商品");}
    require(code.equals(mapping.get("product_code")),"商品编码已关联其他来源商品或发生变化");
    var duplicate=db.queryForList("SELECT request_hash,result_json FROM mcp_receive_result WHERE sync_id=?",key);
    if(!duplicate.isEmpty()) {require(hash.equals(duplicate.get(0).get("request_hash")),"幂等编号不能用于不同请求");return result(duplicate.get(0).get("result_json").toString());}
    boolean licensesOnly=payload.path("licensesOnly").asBoolean();
    String previous=Objects.toString(mapping.get(licensesOnly?"license_updated_at":"source_updated_at"),"");
    if(!previous.isBlank() && !version.isAfter(Instant.parse(previous))) return saveResult(key,hash,code,"重复或旧版本，待审批目录未被覆盖");
    String previousLicenseVersion=Objects.toString(mapping.get("license_updated_at"),"");
    boolean applyLicenses=previousLicenseVersion.isBlank() || version.isAfter(Instant.parse(previousLicenseVersion));
    var decoded=applyLicenses?decodeLicenses(payload.path("licenses"),code):List.<DecodedLicense>of();
    String application=Objects.toString(mapping.get("application_no"),"");
    if(applyLicenses) {
      for(var license:decoded) {
        String identity=license.metadata().path("sourceLicenseId").asText(),number=license.metadata().path("licenseCode").asText();
        if(!identity.startsWith("legacy:") && db.queryForList("SELECT source_license_id FROM mcp_license_approved WHERE source_product_id=? AND source_license_id=?",sourceId,identity).isEmpty())
          db.update("UPDATE mcp_license_approved SET source_license_id=? WHERE source_product_id=? AND source_license_id=?",identity,sourceId,"legacy:"+number);
      }
      if(payload.path("licensesComplete").asBoolean(false)) {
        var complete=new ArrayList<>(decoded);var identities=new HashSet<String>();decoded.forEach(l->identities.add(l.metadata().path("sourceLicenseId").asText()));
        var oldVersions=new ArrayList<>(db.queryForList("SELECT source_license_id,metadata FROM mcp_license_approved WHERE source_product_id=?",sourceId));
        oldVersions.addAll(db.queryForList("SELECT source_license_id,metadata FROM mcp_license_intent WHERE application_no=? AND source_product_id=?",application,sourceId));
        for(var old:oldVersions) if(identities.add(old.get("source_license_id").toString())) {
          ObjectNode metadata;
          try {metadata=(ObjectNode)mapper.readTree(old.get("metadata").toString());}catch(Exception e){throw new IllegalArgumentException("原证照关联数据不合法");}
          metadata.put("sourceLicenseId",old.get("source_license_id").toString());metadata.put("status","unlinked");
          complete.add(new DecodedLicense(metadata,null));
        }
        decoded=complete;
      }
    }
    if(licensesOnly) require(!application.isBlank(),"独立证照下发要求商品已进入待审批目录");
    boolean newQualification=false;
    if(licensesOnly) {
      var detail=approvals.getDetail(application);
      newQualification=!(detail.approvalStatus().startsWith("pending") || detail.approvalStatus().equals("routing"));
    }
    {
      ObjectNode merged=mapper.createObjectNode();String type;boolean resubmitting=false;
      if(!application.isBlank()) {
        var detail=approvals.getDetail(application);
        if(detail.approvalStatus().startsWith("pending") || detail.approvalStatus().equals("routing")) {
          merged=mapper.valueToTree(detail);type=detail.applicationType();
          if(!licensesOnly) require(product.path("status").asText().equals("inactive")==type.equals("停用申请"),"下发启停状态与当前待审批申请冲突，请先在SPD-PR处理原申请后重新下发");
        } else if(Set.of("returned","rejected").contains(detail.approvalStatus())) {
          if(licensesOnly && !detail.applicationType().equals("新品准入")) {
            application="";type="资质更新";
          } else if(!licensesOnly && product.path("status").asText().equals("inactive")!=detail.applicationType().equals("停用申请")) {
            application="";type=product.path("status").asText().equals("inactive")?"停用申请":"信息变更";
          } else {merged=mapper.valueToTree(detail);type=detail.applicationType();resubmitting=true;}
        } else {
          application="";type=product.path("status").asText().equals("inactive")?"停用申请":"信息变更";
        }
      } else type="新品准入";
      if(application.isBlank()) {
        Integer count=db.queryForObject("SELECT COUNT(*) FROM product WHERE product_code=? AND deleted=0",Integer.class,code);
        if(count!=null && count>0) {
          merged=mapper.valueToTree(products.hospitalProductDetail(code));type=product.path("status").asText().equals("inactive")?"停用申请":"信息变更";
        } else {require(!product.path("status").asText().equals("inactive"),"未准入商品不能提交停用申请");type="新品准入";}
      }
      // Detail DTOs render absent optional values as "-"; never write that UI marker back to business fields.
      var displayFields=merged.fields();
      while(displayFields.hasNext()) {var field=displayFields.next();if(field.getValue().isTextual() && field.getValue().asText().equals("-")) merged.putNull(field.getKey());}
      var target=merged;
      product.fields().forEachRemaining(e->{
        if(!applyLicenses && Set.of("registrationNo","registrationExpireDate").contains(e.getKey())) return;
        if(!licensesOnly || Set.of("registrationNo","registrationExpireDate").contains(e.getKey())) target.set(e.getKey(),e.getValue());
      });
      if(newQualification && !resubmitting) type="资质更新";
      for(var license:decoded) if(license.metadata().path("licenseCode").asText().equals(target.path("registrationNo").asText()) && Set.of("revoked","unlinked").contains(license.metadata().path("status").asText())) target.putNull("registrationExpireDate");
      target.put("applicationType",type);
      target.put("changeReason",licensesOnly?"MCP-UI证照更新/作废："+String.join("、",decoded.stream().map(l->l.metadata().path("licenseCode").asText()+"("+l.metadata().path("status").asText()+")").toList()):"MCP-UI商品目录下发");
      Set<String> allowed=new HashSet<>();for(var c:PendingProductApplicationRequest.class.getRecordComponents()) allowed.add(c.getName());
      var requestNode=mapper.createObjectNode();target.fields().forEachRemaining(e->{if(allowed.contains(e.getKey())) requestNode.set(e.getKey(),e.getValue());});
      PendingProductApplicationRequest request;
      try {request=mapper.treeToValue(requestNode,PendingProductApplicationRequest.class);} catch(Exception e) {throw new IllegalArgumentException("商品字段不合法",e);}
      if(application.isBlank()) application=Objects.toString(approvals.createApplication(request).get("applicationNo"));
      else if(resubmitting) approvals.resubmitApplication(application,request);
      else approvals.updateApplicationData(application,request);
    }
    Long applicationId=db.queryForObject("SELECT application_id FROM pending_product_application WHERE application_no=?",Long.class,application);
    if(licensesOnly) db.update("UPDATE pending_product_application a JOIN product p ON p.product_code=a.product_code AND p.deleted=0 SET a.product_snapshot=JSON_SET(COALESCE(a.product_snapshot,JSON_OBJECT()),'$.targetStatus',p.status) WHERE a.application_no=?",application);
    for(var license:decoded) {
      String licenseCode=license.metadata().path("licenseCode").asText();
      String identity=license.metadata().path("sourceLicenseId").asText();
      var approvedLicense=db.queryForList("SELECT attachment_id FROM mcp_license_approved WHERE source_product_id=? AND source_license_id=?",sourceId,identity);
      if(!approvedLicense.isEmpty() && approvedLicense.get(0).get("attachment_id") instanceof Number approvedAttachment)
        db.update("INSERT IGNORE INTO mcp_license_retirement(application_no,source_attachment_id,source_product_id,license_code) VALUES(?,?,?,?)",application,approvedAttachment.longValue(),sourceId,licenseCode);
      var previousLicense=db.queryForList("SELECT application_no,attachment_id FROM mcp_pending_license WHERE source_product_id=? AND license_code=?",sourceId,licenseCode);
      if(!previousLicense.isEmpty() && previousLicense.get(0).get("attachment_id") instanceof Number old)
        db.update("INSERT IGNORE INTO mcp_license_retirement(application_no,source_attachment_id,source_product_id,license_code) VALUES(?,?,?,?)",application,old.longValue(),sourceId,licenseCode);
      if(!previousLicense.isEmpty() && previousLicense.get(0).get("attachment_id") instanceof Number old && application.equals(previousLicense.get(0).get("application_no")))
        attachments.delete(application,old.longValue());
      Long attachmentId=null;
      if(license.file()!=null) {
        attachmentId=((Number)attachments.upload(application,license.file()).get("attachmentId")).longValue();
        db.update("UPDATE sys_attachment SET valid_date=?,description=? WHERE attachment_id=?",license.metadata().path("expiryDate").asText(null),license.metadata().path("licenseType").asText()+" / "+licenseCode+" / "+license.metadata().path("status").asText(),attachmentId);
      }
      db.update("INSERT INTO mcp_pending_license(source_product_id,license_code,application_no,attachment_id,metadata) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE application_no=VALUES(application_no),attachment_id=VALUES(attachment_id),metadata=VALUES(metadata)",sourceId,licenseCode,application,attachmentId,license.metadata().toString());
      db.update("INSERT INTO mcp_license_intent(application_no,source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,source_updated_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE license_code=VALUES(license_code),attachment_id=VALUES(attachment_id),metadata=VALUES(metadata),source_updated_at=VALUES(source_updated_at)",application,sourceId,identity,code,licenseCode,attachmentId,license.metadata().toString(),version.toString());
    }
    db.update("UPDATE mcp_inbound_product SET application_no=?,source_updated_at=?,license_updated_at=? WHERE source_product_id=?",application,
        licensesOnly?Objects.toString(mapping.get("source_updated_at"),""):version.toString(),applyLicenses?version.toString():previousLicenseVersion,sourceId);
    audit.record("pending_product_application","mcp_receive",applicationId,application,"MCP-UI下发商品及证照，等待院内审批");
    return saveResult(key,hash,code,"商品及证照已进入待审批目录："+application+"，尚未进入院内目录");
  }
  private List<DecodedLicense> decodeLicenses(JsonNode licenses,String code) {
    require(licenses.isArray() && licenses.size()<=10,"每个商品最多下发10个证照");
    var list=new ArrayList<DecodedLicense>();var codes=new HashSet<String>();var sourceIds=new HashSet<String>();long size=0;
    for(var l:licenses) {
      String licenseCode=l.path("licenseCode").asText();require(!licenseCode.isBlank() && licenseCode.length()<=80 && codes.add(licenseCode),"证照编号缺失或重复");
      String identity=l.path("sourceLicenseId").asText("legacy:"+licenseCode);
      require(identity.startsWith("legacy:") || identity.matches("[1-9][0-9]{0,18}"),"来源证照身份不合法");require(sourceIds.add(identity),"来源证照身份重复");
      require(code.equals(l.path("productCode").asText()),"证照商品关联不一致");
      String state=l.path("status").asText();require(Set.of("active","pending","expired","revoked").contains(state),"证照状态不合法");
      LocalDate expiry=LocalDate.parse(l.path("expiryDate").asText());
      if(l.hasNonNull("issueDate")) require(!LocalDate.parse(l.path("issueDate").asText()).isAfter(expiry),"证照签发日期不能晚于有效期");
      MultipartFile file=null;
      if(!state.equals("revoked")) {
        byte[] bytes=Base64.getDecoder().decode(l.path("attachmentBase64").asText());size+=bytes.length;
        require(bytes.length>0 && bytes.length<=10*1024*1024 && size<=18*1024*1024,"证照附件为空或超过容量限制");
        String name=l.path("fileName").asText();require(!name.isBlank() && !name.contains("/") && !name.contains("\\"),"证照文件名不合法");
        if(name.toLowerCase().endsWith(".jpeg")) name=name.substring(0,name.length()-5)+".jpg";
        file=new ReceivedFile(name,bytes);
      }
      ObjectNode metadata=l.deepCopy();metadata.put("sourceLicenseId",identity);metadata.remove("attachmentBase64");list.add(new DecodedLicense(metadata,file));
    }
    return list;
  }
  private record DecodedLicense(JsonNode metadata,MultipartFile file) {}
  private record ReceivedFile(String filename,byte[] bytes) implements MultipartFile {
    public String getName(){return "file";}public String getOriginalFilename(){return filename;}public String getContentType(){return "application/octet-stream";}
    public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
    public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File destination) throws IOException {Files.write(destination.toPath(),bytes);}
  }
  private Map<String,Object> saveResult(String key,String hash,String code,String message) {
    var result=Map.<String,Object>of("state","processed","productCode",code,"message",message);
    try {db.update("INSERT INTO mcp_receive_result(sync_id,request_hash,result_json) VALUES(?,?,?)",key,hash,mapper.writeValueAsString(result));} catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
    return result;
  }
  @SuppressWarnings("unchecked") private Map<String,Object> result(String json) {
    try{return mapper.readValue(json,Map.class);}catch(Exception e){throw new IllegalStateException(e);}
  }
  public static String hash(String value) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));} catch(Exception e){throw new IllegalStateException(e);}}
  private void require(boolean valid,String message) {if(!valid) throw new IllegalArgumentException(message);}
}
