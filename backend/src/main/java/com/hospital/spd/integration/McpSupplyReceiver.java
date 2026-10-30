package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.supplychain.*;
import com.hospital.spd.supplychain.service.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Receives supplier deliveries as draft receiving orders, never approving stock or financial facts. */
@Service
public class McpSupplyReceiver {
    public static final class Conflict extends IllegalArgumentException { public Conflict(String message){super(message);} }
    private final JdbcTemplate db;private final Environment env;private final ReceivingOrderService receiving;private final AuditLogService audit;
    public McpSupplyReceiver(JdbcTemplate db,Environment env,ReceivingOrderService receiving,AuditLogService audit){this.db=db;this.env=env;this.receiving=receiving;this.audit=audit;}
    private void enabled(){if(!env.getProperty("SPD_MCP_SUPPLY_ENABLED",Boolean.class,false))throw new IllegalStateException("供货回传尚未启用");}
    private void organization(String org){if(org==null || org.isBlank() || !org.equals(env.getProperty("SPD_MCP_ORGANIZATION","")))throw new IllegalArgumentException("供货机构不匹配");}
    private static String text(JsonNode n,String field,int limit){String value=n.path(field).asText().trim();if(value.isBlank()||value.length()>limit)throw new IllegalArgumentException("供货字段缺失或过长："+field);return value;}
    private static long id(JsonNode n,String field){String value=text(n,field,19);if(!n.path(field).isTextual() || !value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("来源明细身份不合法");return Long.parseLong(value);}
    private static BigDecimal quantity(JsonNode n){String value=text(n,"quantity",14);if(!n.path("quantity").isTextual() || !value.matches("[1-9][0-9]{0,13}"))throw new IllegalArgumentException("供货数量必须为基本单位正整数");return new BigDecimal(value);}
    private static LocalDate date(String value){try{return LocalDate.parse(value);}catch(java.time.DateTimeException error){throw new IllegalArgumentException("供货日期格式不合法");}}
    private Map<String,Object> order(String no,String supplier,boolean lock) {
        var rows=db.queryForList("""
                SELECT po.purchase_order_id,po.order_no,po.supplier_id,po.order_status,s.supplier_code
                FROM purchase_order po JOIN supplier s ON s.supplier_id=po.supplier_id
                WHERE po.order_no=? AND s.supplier_code=? AND s.status=1 AND s.deleted=0
                """+(lock?" FOR UPDATE":""),no,supplier);
        if(rows.size()!=1)throw new IllegalArgumentException("采购订单不存在或供应商不匹配");
        if(!Set.of("approved","sent").contains(rows.get(0).get("order_status")))throw new IllegalArgumentException("采购订单当前状态不允许供货");
        return rows.get(0);
    }
    private Map<String,BigDecimal> reserved(long orderId,boolean lock) {
        var amounts=new HashMap<String,BigDecimal>();
        for(var row:db.queryForList("""
                SELECT roi.purchase_order_item_id,roi.quantity,
                  (SELECT p.product_code FROM product p WHERE p.product_id=roi.product_id) AS product_code FROM receiving_order_item roi
                JOIN receiving_order ro ON ro.receiving_order_id=roi.receiving_order_id
                WHERE ro.purchase_order_id=? AND ro.receiving_status='draft'
                """+(lock?" FOR UPDATE":""),orderId)) {
            String key=row.get("purchase_order_item_id")==null?"product:"+row.get("product_code"):row.get("purchase_order_item_id").toString();
            amounts.merge(key,(BigDecimal)row.get("quantity"),BigDecimal::add);
        }return amounts;
    }
    public Map<String,Object> options(String org,String no,String supplier) {
        enabled();organization(org);var header=order(no,supplier,false);long orderId=((Number)header.get("purchase_order_id")).longValue();
        var used=reserved(orderId,false);var items=new ArrayList<Map<String,Object>>();
        for(var row:db.queryForList("SELECT i.item_id,p.product_code,i.unit,i.quantity-i.received_quantity AS remaining FROM purchase_order_item i JOIN product p ON p.product_id=i.product_id WHERE i.purchase_order_id=? ORDER BY i.item_id",orderId)) {
            String line=row.get("item_id").toString();var remaining=((BigDecimal)row.get("remaining")).subtract(used.getOrDefault(line,BigDecimal.ZERO)).subtract(used.getOrDefault("product:"+row.get("product_code"),BigDecimal.ZERO)).max(BigDecimal.ZERO);
            items.add(Map.of("lineId",line,"productCode",row.get("product_code"),"unit",row.get("unit"),"remainingQuantity",remaining.toBigIntegerExact().toString()));
        }
        return Map.of("state","processed","orderNo",no,"supplierCode",supplier,"items",items,"warehouses",db.queryForList("SELECT warehouse_code AS code,warehouse_name AS name FROM warehouse WHERE deleted=0 AND status=1 AND receiving_enabled=1 ORDER BY warehouse_code"));
    }
    @Transactional public Map<String,Object> receive(String key,JsonNode wire) {
        enabled();if(wire==null || !wire.isObject())throw new IllegalArgumentException("供货消息必须为JSON对象");organization(wire.path("organization").asText());UUID.fromString(key);
        if(!key.equals(wire.path("messageId").asText()) || !"3.0".equals(wire.path("schemaVersion").asText()) || !"MCP-UI".equals(wire.path("sourceSystem").asText()) || !"supply".equals(wire.path("documentType").asText()))throw new IllegalArgumentException("供货消息协议或身份不一致");
        String hash=Phase3Json.hash(wire);var cached=db.queryForList("SELECT payload_hash FROM mcp_supply_receipt WHERE message_id=?",key);
        if(!cached.isEmpty()){if(!hash.equals(cached.get(0).get("payload_hash")))throw new Conflict("同一供货消息不能用于不同内容");return receipt(wire.path("organization").asText(),key,wire.path("payload").path("supplierCode").asText());}
        JsonNode payload=wire.path("payload");String no=text(payload,"orderNo",100),supplier=text(payload,"supplierCode",100),supplyNo=text(payload,"supplyNo",100),warehouse=text(payload,"warehouseCode",100);
        var lines=payload.path("items");if(!lines.isArray() || lines.isEmpty() || lines.size()>1000)throw new IllegalArgumentException("供货单需包含1至1000条明细");
        var preliminary=order(no,supplier,false);long preliminaryId=((Number)preliminary.get("purchase_order_id")).longValue();
        var codes=db.queryForList("SELECT p.product_code FROM purchase_order_item i JOIN product p ON p.product_id=i.product_id WHERE i.purchase_order_id=?",String.class,preliminaryId);
        // Match the existing receiving service's product-first lock order before locking the source order.
        new QuotaPackagePricingService(db).lockReceivingProducts(null,codes);
        cached=db.queryForList("SELECT payload_hash FROM mcp_supply_receipt WHERE message_id=? FOR UPDATE",key);
        if(!cached.isEmpty()){if(!hash.equals(cached.get(0).get("payload_hash")))throw new Conflict("同一供货消息不能用于不同内容");return receipt(wire.path("organization").asText(),key,supplier);}
        var header=order(no,supplier,true);long orderId=((Number)header.get("purchase_order_id")).longValue();
        if(!db.queryForList("SELECT message_id FROM mcp_supply_receipt WHERE organization=? AND supplier_code=? AND supply_no=? AND purchase_order_id=? FOR UPDATE",wire.path("organization").asText(),supplier,supplyNo,orderId).isEmpty())throw new Conflict("此订单的供应商供货单号已使用，请核查原回传记录");
        var available=new HashMap<Long,Map<String,Object>>();
        for(var row:db.queryForList("SELECT i.item_id,p.product_code,i.unit,i.quantity-i.received_quantity AS remaining FROM purchase_order_item i JOIN product p ON p.product_id=i.product_id WHERE i.purchase_order_id=? ORDER BY i.item_id FOR UPDATE",orderId))available.put(((Number)row.get("item_id")).longValue(),row);
        var used=reserved(orderId,true);var totals=new HashMap<Long,BigDecimal>();var identities=new HashSet<String>();var items=new ArrayList<ReceivingItemRequest>();
        for(var line:lines) {
            if(!identities.add(text(line,"supplyLineId",100)))throw new IllegalArgumentException("供货明细身份重复");
            long lineId=id(line,"orderLineId");var original=available.get(lineId);BigDecimal qty=quantity(line);
            if(original==null || !Objects.equals(original.get("product_code"),text(line,"productCode",100)) || !Objects.equals(original.get("unit"),text(line,"unit",30)))throw new IllegalArgumentException("供货明细、商品或基本单位与采购订单不一致");
            BigDecimal remaining=((BigDecimal)original.get("remaining")).subtract(used.getOrDefault(Long.toString(lineId),BigDecimal.ZERO)).subtract(used.getOrDefault("product:"+original.get("product_code"),BigDecimal.ZERO));
            if(totals.merge(lineId,qty,BigDecimal::add).compareTo(remaining)>0)throw new IllegalArgumentException("供货数量超过订单剩余可收数量（含已有待收货占用）");
            String production=text(line,"productionDate",10),expiry=text(line,"expireDate",10),batch=text(line,"productionBatchNo",100),udi=line.path("udiCode").asText("");
            if(udi.length()>120 || date(production).isAfter(LocalDate.now()) || date(production).isAfter(date(expiry)) || date(expiry).isBefore(LocalDate.now()))throw new IllegalArgumentException("供货日期或UDI信息不合法（UDI最多120字符）");
            items.add(new ReceivingItemRequest(original.get("product_code").toString(),batch,udi,production,expiry,qty,qty,BigDecimal.ZERO,lineId));
        }
        String remark=payload.path("remark").asText("");if(remark.length()>500)throw new IllegalArgumentException("供货备注过长");
        var created=receiving.create(new ReceivingOrderRequest("purchase_order",no,((Number)header.get("supplier_id")).longValue(),null,warehouse,null,"normal",false,remark,items));
        String receivingNo=created.get("receivingNo").toString();long receivingId=db.queryForObject("SELECT receiving_order_id FROM receiving_order WHERE receiving_no=?",Long.class,receivingNo);
        db.update("UPDATE receiving_order SET source_system='MCP-UI',external_supply_no=? WHERE receiving_order_id=?",supplyNo,receivingId);
        db.update("INSERT INTO mcp_supply_receipt(message_id,organization,supplier_code,supply_no,payload_hash,purchase_order_id,receiving_order_id,payload) VALUES(?,?,?,?,?,?,?,CAST(? AS JSON))",key,wire.path("organization").asText(),supplier,supplyNo,hash,orderId,receivingId,Phase3Json.json(wire));
        audit.record("mcp_supply","receive",receivingId,supplyNo,"MCP供货生成待收货单，外部操作人："+text(payload,"externalOperator",100));
        return receipt(wire.path("organization").asText(),key,supplier);
    }
    @Transactional public Map<String,Object> receipt(String org,String key,String supplier) {
        enabled();organization(org);UUID.fromString(key);
        var rows=db.queryForList("""
                SELECT map.supply_no,ro.receiving_order_id,ro.receiving_no,ro.receiving_status,
                  JSON_UNQUOTE(JSON_EXTRACT(map.payload,'$.payload.orderNo')) AS order_no FROM mcp_supply_receipt map
                JOIN receiving_order ro ON ro.receiving_order_id=map.receiving_order_id
                WHERE map.message_id=? AND map.organization=? AND map.supplier_code=? FOR SHARE
                """,key,org,supplier);
        if(rows.size()!=1)throw new IllegalArgumentException("供货回执不存在或供应商不匹配");var row=rows.get(0);
        var result=new LinkedHashMap<String,Object>(Map.of("state","processed","messageId",key,"supplyNo",row.get("supply_no"),"orderNo",row.get("order_no"),"receivingNo",row.get("receiving_no"),"receivingStatus",row.get("receiving_status")));
        if("approved".equals(row.get("receiving_status"))) {
            var groups=new TreeMap<String,BigDecimal[]>();
            for(var line:db.queryForList("""
                    SELECT i.quantity,i.qualified_quantity,i.unqualified_quantity,
                      (SELECT poi.unit FROM purchase_order_item poi WHERE poi.item_id=i.purchase_order_item_id) AS unit
                    FROM receiving_order_item i
                    WHERE i.receiving_order_id=? FOR SHARE
                    """,row.get("receiving_order_id"))) {
                var sums=groups.computeIfAbsent(line.get("unit").toString(),unit->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
                sums[0]=sums[0].add((BigDecimal)line.get("quantity"));sums[1]=sums[1].add((BigDecimal)line.get("qualified_quantity"));sums[2]=sums[2].add((BigDecimal)line.get("unqualified_quantity"));
            }
            var quantities=new ArrayList<Map<String,String>>();groups.forEach((unit,sums)->quantities.add(Map.of("unit",unit,"quantity",sums[0].toBigIntegerExact().toString(),"qualifiedQuantity",sums[1].toBigIntegerExact().toString(),"unqualifiedQuantity",sums[2].toBigIntegerExact().toString())));
            result.put("quantities",quantities);
            if(quantities.size()==1){var single=quantities.get(0);result.put("quantity",single.get("quantity"));result.put("qualifiedQuantity",single.get("qualifiedQuantity"));result.put("unqualifiedQuantity",single.get("unqualifiedQuantity"));}
        }
        return result;
    }
}
