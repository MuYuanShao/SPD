package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uploads persisted suppliers, manufacturers and complete purchase orders to one hospital-bound MCP instance. */
@Component
public class McpUploadJob {
  private static final Logger log=LoggerFactory.getLogger(McpUploadJob.class);
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final Environment env;
  private final McpPhase2Source phase2;
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
  private String token="";
  private Instant renewAt=Instant.EPOCH;
  private String runtimeUrl="";
  private boolean phase2UploadEnabled;
  public McpUploadJob(JdbcTemplate db,ObjectMapper mapper,Environment env) {this(db,mapper,env,new McpPhase2Source(db,env));}
  @org.springframework.beans.factory.annotation.Autowired
  public McpUploadJob(JdbcTemplate db,ObjectMapper mapper,Environment env,McpPhase2Source phase2) {this.db=db;this.mapper=mapper;this.env=env;this.phase2=phase2;}
  @Scheduled(fixedDelayString="${spd.mcp.upload-delay-ms:60000}")
  public synchronized void upload() {
    if(!env.getProperty("SPD_MCP_ENABLED",Boolean.class,false) || !env.getProperty("SPD_MCP_UPLOAD_ENABLED",Boolean.class,false)) return;
    try {
      String nextUrl=connectionUrl();
      if(!nextUrl.equals(runtimeUrl)) {
        runtimeUrl=nextUrl;token="";renewAt=Instant.EPOCH;
        client.cookieHandler().filter(CookieManager.class::isInstance).map(CookieManager.class::cast).ifPresent(c->c.getCookieStore().removeAll());
      }
      for(String key:List.of("SPD_MCP_UI_INSTANCE_ID","SPD_MCP_UI_USERNAME","SPD_MCP_UI_PASSWORD","SPD_MCP_UI_API_KEY","SPD_MCP_SHARED_TOKEN","SPD_MCP_CURRENCY","SPD_MCP_TAX_INCLUDED"))
        if(env.getProperty(key,"").isBlank()) throw new IllegalArgumentException("上传配置缺少 "+key);
      login();
      uploadMasters("manufacturer");uploadMasters("supplier");uploadOrders();
      if(phase2UploadEnabled) uploadPhase2();
    } catch(Exception e) {log.warn("MCP上传任务未完成：{} ({})",e instanceof IllegalArgumentException?e.getMessage():"网络或数据库异常，请检查同步配置和上传状态",e.getClass().getSimpleName());}
  }
  private void uploadPhase2() throws Exception {
    for(String kind:List.of("receiving","inbound")) {
      long cursor=0;
      for(;;) {
        var page=kind.equals("receiving")?phase2.receipts(cursor):phase2.inbounds(cursor);
        if(page.records().isEmpty()) break;
        cursor=page.cursor();
        for(var record:page.records()) {
          if(record.error()!=null) {db.update("INSERT INTO mcp_upload_state(kind,source_code,state,message) VALUES(?,?,'failed',?) ON DUPLICATE KEY UPDATE state='failed',message=VALUES(message)",kind,record.code(),record.error());continue;}
          var payload=new LinkedHashMap<>(record.payload());
          if(kind.equals("inbound")){payload.put("currency",env.getProperty("SPD_MCP_CURRENCY"));payload.put("taxIncluded",Boolean.parseBoolean(env.getProperty("SPD_MCP_TAX_INCLUDED")));}
          push(kind,record.code(),payload,"/phase2/"+kind);
        }
      }
    }
    try {
      var inventory=new LinkedHashMap<>(phase2.inventory());inventory.put("currency",env.getProperty("SPD_MCP_CURRENCY"));inventory.put("taxIncluded",Boolean.parseBoolean(env.getProperty("SPD_MCP_TAX_INCLUDED")));
      push("inventory","current",inventory,"/phase2/inventory");
    } catch(IllegalArgumentException e) {db.update("INSERT INTO mcp_upload_state(kind,source_code,state,message) VALUES('inventory','current','failed',?) ON DUPLICATE KEY UPDATE state='failed',message=VALUES(message)",e.getMessage());}
  }
  String connectionUrl() throws Exception {
    var configs=db.queryForList("SELECT config_value,status,effective_time,expire_time FROM system_config WHERE config_type='integration' AND scope_type='global' AND scope_id='default' AND config_key=? AND deleted=0",McpConnectionAddress.CONFIG_KEY);
    if(!configs.isEmpty()) {
      var row=configs.get(0);
      if(((Number)row.get("status")).intValue()!=1) throw new IllegalArgumentException("系统设置中的MCP-UI连接已停用");
      if(row.get("effective_time")!=null && McpSourceTime.dateTime(row.get("effective_time")).isAfter(LocalDateTime.now())) throw new IllegalArgumentException("MCP-UI连接配置尚未生效");
      if(row.get("expire_time")!=null && McpSourceTime.date(row.get("expire_time")).isBefore(LocalDate.now())) throw new IllegalArgumentException("MCP-UI连接配置已过期");
      var settings=mapper.readTree(row.get("config_value").toString());
      phase2UploadEnabled=settings.path("phase2Enabled").asBoolean(env.getProperty("SPD_MCP_PHASE2_UPLOAD_ENABLED",Boolean.class,false));
      return McpConnectionAddress.apiUrl(settings);
    }
    String fallback=env.getProperty("SPD_MCP_UI_URL","");
    phase2UploadEnabled=env.getProperty("SPD_MCP_PHASE2_UPLOAD_ENABLED",Boolean.class,false);
    if(fallback.isBlank()) throw new IllegalArgumentException("请在系统配置的系统集成中设置MCP-UI IP地址和端口");
    return fallback;
  }
  private void login() throws Exception {
    if(!token.isBlank() && Instant.now().isBefore(renewAt)) return;
    JsonNode response;
    try { response=post("/auth/refresh",Map.of(),false); }
    catch(Exception e) {response=post("/auth/login",Map.of("username",env.getProperty("SPD_MCP_UI_USERNAME"),"password",env.getProperty("SPD_MCP_UI_PASSWORD")),false);}
    token=response.path("data").path("token").asText();
    if(token.isBlank()) throw new IllegalArgumentException("MCP上传账户登录失败");
    renewAt=Instant.now().plusSeconds(900);
  }
  private void uploadMasters(String kind) throws Exception {
    long cursor=0;
    for(;;) {
      String idColumn=kind+"_id",codeColumn=kind+"_code",nameColumn=kind+"_name";
      var rows=db.queryForList("SELECT * FROM "+kind+" WHERE "+idColumn+">? ORDER BY "+idColumn+" LIMIT 100",cursor);
      if(rows.isEmpty()) break;
      for(var row:rows) {
        cursor=((Number)row.get(idColumn)).longValue();
        var payload=new LinkedHashMap<String,Object>();payload.put("sourceCode",row.get(codeColumn));payload.put("name",row.get(nameColumn));
        payload.put("creditCode",row.get("credit_code"));payload.put("contactName",row.get("contact_name"));payload.put("contactPhone",row.get("contact_phone"));
        payload.put("status",((Number)row.get("status")).intValue()==1 && ((Number)row.get("deleted")).intValue()==0?"active":"inactive");
        payload.put("sourceUpdatedAt",timestamp(row.get("update_time")));
        push(kind,row.get(codeColumn).toString(),payload,"/upload/"+kind);
      }
    }
  }
  private void uploadOrders() throws Exception {
    String currency=env.getProperty("SPD_MCP_CURRENCY");
    if(!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("SPD_MCP_CURRENCY 必须为三位币种编码");
    String tax=env.getProperty("SPD_MCP_TAX_INCLUDED");
    if(!Set.of("true","false").contains(tax)) throw new IllegalArgumentException("SPD_MCP_TAX_INCLUDED 必须明确配置 true 或 false");
    long cursor=0;
    for(;;) {
      var rows=db.queryForList("SELECT po.*,s.supplier_code FROM purchase_order po JOIN supplier s ON s.supplier_id=po.supplier_id WHERE po.purchase_order_id>? ORDER BY po.purchase_order_id LIMIT 100",cursor);
      if(rows.isEmpty()) break;
      for(var row:rows) {
        cursor=((Number)row.get("purchase_order_id")).longValue();String code=row.get("order_no").toString();
        try {
          var payload=new LinkedHashMap<String,Object>();payload.put("orderNo",code);payload.put("supplierCode",row.get("supplier_code"));
          payload.put("orderDate",McpSourceTime.date(row.get("create_time")).toString());
          payload.put("status",orderStatus(row.get("order_status").toString()));payload.put("totalAmount",row.get("total_amount"));
          payload.put("currency",currency);payload.put("taxIncluded",Boolean.parseBoolean(tax));payload.put("sourceUpdatedAt",timestamp(row.get("update_time")));
          var items=db.queryForList("SELECT i.item_id,i.quantity,i.unit,i.estimated_unit_price,i.amount,p.product_code FROM purchase_order_item i JOIN product p ON p.product_id=i.product_id WHERE i.purchase_order_id=? ORDER BY i.item_id",cursor);
          payload.put("items",items.stream().map(i->Map.of("lineId",i.get("item_id").toString(),"productCode",i.get("product_code"),"quantity",i.get("quantity"),"unit",i.get("unit"),"unitPrice",i.get("estimated_unit_price"),"amount",i.get("amount"))).toList());
          push("order",code,payload,"/orders");
        } catch(IllegalArgumentException e) {db.update("INSERT INTO mcp_upload_state(kind,source_code,state,message) VALUES('order',?,'failed',?) ON DUPLICATE KEY UPDATE state='failed',message=VALUES(message)",code,e.getMessage());}
      }
    }
  }
  private void push(String kind,String code,Object payload,String path) throws Exception {
    String hash=McpReceiveService.hash(mapper.writeValueAsString(payload));
    String target=runtimeUrl+"|"+env.getProperty("SPD_MCP_UI_INSTANCE_ID")+"|"+env.getProperty("SPD_MCP_UI_TENANT_ID","1");
    var existing=db.queryForList("SELECT payload_hash,state,target_key FROM mcp_upload_state WHERE kind=? AND source_code=?",kind,code);
    if(!existing.isEmpty() && target.equals(existing.get(0).get("target_key")) && hash.equals(existing.get(0).get("payload_hash")) && "processed".equals(existing.get(0).get("state"))) return;
    String state="failed",message="上传网络或接口异常";
    try {
      var response=post("/integration/instances/"+env.getProperty("SPD_MCP_UI_INSTANCE_ID")+path,payload,true).path("data");
      String result=response.path("state").asText();
      if(Set.of("processed","ignored").contains(result)) {state="processed";message=response.path("message").asText("处理成功");}
      else message=response.path("message").asText("上传失败，等待补齐关联");
    } catch(InterruptedException e) {Thread.currentThread().interrupt();throw e;}
    catch(Exception e) {if(e instanceof IllegalArgumentException) message=e.getMessage();}
    db.update("INSERT INTO mcp_upload_state(kind,source_code,payload_hash,state,message,target_key) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE payload_hash=VALUES(payload_hash),state=VALUES(state),message=VALUES(message),target_key=VALUES(target_key)",kind,code,hash,state,message.substring(0,Math.min(1000,message.length())),target);
  }
  private JsonNode post(String path,Object payload,boolean authenticated) throws Exception {
    URI base=URI.create(runtimeUrl);
    if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null) throw new IllegalArgumentException("MCP接口地址不合法");
    var builder=HttpRequest.newBuilder(URI.create(base.toString().replaceAll("/$","")+path)).timeout(Duration.ofSeconds(15))
        .header("Content-Type","application/json").header("x-api-key",env.getProperty("SPD_MCP_UI_API_KEY"))
        .header("x-tenant-id",env.getProperty("SPD_MCP_UI_TENANT_ID","1"));
    if(authenticated) builder.header("Authorization","Bearer "+token).header("x-spd-token",env.getProperty("SPD_MCP_SHARED_TOKEN"));
    var response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofInputStream());
    try(var body=response.body()) {
      if(response.statusCode()==401) token="";
      if(response.statusCode()!=200) throw new IllegalArgumentException("MCP接口返回 HTTP "+response.statusCode());
      var bytes=body.readNBytes(1024*1024+1);if(bytes.length>1024*1024) throw new IllegalArgumentException("MCP回执过大");
      return mapper.readTree(bytes);
    }
  }
  private String timestamp(Object timestamp) {
    return McpSourceTime.instant(timestamp,env.getProperty("SPD_MCP_SOURCE_ZONE","Asia/Shanghai"));
  }
  public static String orderStatus(String status) {
    return switch(status) {
      case "draft","rejected","pending_approval","approved","sent","closed","voided","cancelled"->status;
      default->throw new IllegalArgumentException("无法映射SPD采购订单状态: "+status);
    };
  }
}
