package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Captures source facts and their durable versions inside the owning business transaction. */
@Service
public class McpPhase3Outbox {
    private final JdbcTemplate db;
    private final Environment env;
    public McpPhase3Outbox(JdbcTemplate db,Environment env){this.db=db;this.env=env;}

    @Transactional public void capturePlan(String planNo){capturePlan(planNo,false);}
    private void capturePlan(String planNo,boolean backfill) {
        var rows=db.queryForList("""
                SELECT plan.*,s.supplier_code,p.product_code,p.unit
                  FROM purchase_plan plan JOIN supplier s ON s.supplier_id=plan.supplier_id
                  JOIN product p ON p.product_id=plan.product_id WHERE plan.plan_no=? FOR SHARE
                """,planNo);
        if(rows.isEmpty()) return;
        var row=rows.get(0);long id=((Number)row.get("plan_id")).longValue();
        var payload=Phase3Json.MAPPER.createObjectNode();payload.put("documentNo",planNo);payload.put("supplierCode",row.get("supplier_code").toString());
        String status=row.get("plan_status").toString();payload.put("sourceStatus",status);
        payload.put("createdAt",time(row.get("create_time")));nullable(payload,"approvedAt",time(row.get("approve_time")));
        payload.put("remark",Objects.toString(row.get("remark"),""));
        var orderNos=payload.putArray("convertedOrderNos");var orderIds=payload.putArray("convertedOrderIds");
        if(row.get("converted_order_no")!=null) {
            orderNos.add(row.get("converted_order_no").toString());
            db.queryForList("SELECT purchase_order_id FROM purchase_order WHERE order_no=?",Long.class,row.get("converted_order_no")).forEach(order->orderIds.add(order.toString()));
        }
        var item=payload.putArray("items").addObject();item.put("sourceLineId",Long.toString(id));item.put("productCode",row.get("product_code").toString());
        item.put("unit",row.get("unit").toString());item.put("plannedQuantity",whole(row.get("planned_quantity")));
        item.putNull("estimatedUnitPrice");item.putNull("estimatedAmount");item.putNull("expectedDate");item.put("priceBasis","not_recorded");
        capture("purchase_plan",id,"UPSERT",payload,Set.of("approved","executed").contains(status),backfill,
                row.get("approve_time")==null?time(row.get("create_time")):time(row.get("approve_time")));
    }

    @Transactional public void captureSettlement(long id){captureSettlement(id,false,false);}
    @Transactional public void captureSettlementAfterReversal(long id){captureSettlement(id,true,false);}
    private void captureSettlement(long id,boolean reversal,boolean backfill) {
        var rows=db.queryForList("SELECT bill.*,s.supplier_code FROM settlement_bill bill JOIN supplier s ON s.supplier_id=bill.supplier_id WHERE bill.settlement_id=? FOR SHARE",id);
        if(rows.isEmpty()) return;
        var row=rows.get(0);var payload=Phase3Json.MAPPER.createObjectNode();
        payload.put("documentNo",row.get("settlement_no").toString());payload.put("supplierCode",row.get("supplier_code").toString());
        payload.put("settlementPeriod",row.get("settlement_period").toString());payload.put("settlementMode",row.get("settlement_mode").toString());
        payload.put("sourceStatus",row.get("status").toString());payload.put("generatedAt",time(row.get("generate_time")));nullable(payload,"confirmedAt",time(row.get("confirm_time")));
        payload.put("moneyScale",4);payload.put("totalAmount",money(row.get("total_amount")));payload.put("catalogDisplayOrigin","current_at_capture");
        var items=payload.putArray("items");
        for(var line:db.queryForList("""
                SELECT item.*,p.product_code,p.unit,b.system_batch_no FROM settlement_bill_item item
                  JOIN product p ON p.product_id=item.product_id LEFT JOIN inventory_batch b ON b.batch_id=item.batch_id
                 WHERE item.settlement_id=? ORDER BY item.item_id FOR SHARE
                """,id)) {
            var item=items.addObject();item.put("sourceLineId",line.get("item_id").toString());item.put("sourceBizType",line.get("source_biz_type").toString());
            item.put("sourceBizId",line.get("source_biz_id").toString());item.put("productCode",line.get("product_code").toString());item.put("unit",line.get("unit").toString());
            item.put("unitOrigin","current_catalog_at_capture");nullable(item,"systemBatchNo",line.get("system_batch_no"));nullable(item,"traceCodeId",line.get("trace_code_id"));
            item.put("goodsType",Objects.toString(line.get("goods_type"),"unknown"));item.put("quantity",whole(line.get("quantity")));
            item.put("unitPrice",money(line.get("unit_price")));item.put("amount",money(line.get("amount")));
        }
        boolean cancel=reversal && items.isEmpty();if(cancel) payload.put("cancelReason","consumption_reversed");
        capture("settlement",id,cancel?"CANCEL":"UPSERT",payload,true,backfill,
                row.get("confirm_time")==null?time(row.get("generate_time")):time(row.get("confirm_time")));
    }

    private void capture(String kind,long id,String operation,ObjectNode payload,boolean publish,boolean backfill,String historicalTime) {
        db.update("INSERT INTO mcp_phase3_document(document_type,source_id) VALUES(?,?) ON DUPLICATE KEY UPDATE source_id=source_id",kind,id);
        var current=db.queryForMap("SELECT * FROM mcp_phase3_document WHERE document_type=? AND source_id=? FOR UPDATE",kind,id);
        String hash=Phase3Json.hash(Map.of("operation",operation,"payload",payload));
        if(hash.equals(current.get("source_hash"))) return;
        long revision=((Number)current.get("revision")).longValue()+1;
        boolean previouslyPublished=((Number)current.get("published")).intValue()==1;
        boolean send=publish || previouslyPublished;
        db.update("UPDATE mcp_phase3_document SET revision=?,source_hash=?,published=? WHERE document_type=? AND source_id=?",revision,hash,send?1:0,kind,id);
        if(!send) return;
        db.update("UPDATE mcp_phase3_outbox SET state='superseded',message='已由更高完整版本替代' WHERE document_type=? AND source_id=? AND revision<? AND state IN ('pending','failed')",kind,id,revision);
        payload.put("origin",backfill?"historical_backfill":"business_transaction");
        db.update("""
                INSERT INTO mcp_phase3_outbox(message_id,document_type,source_id,revision,operation,source_updated_at,source_payload)
                VALUES(?,?,?,?,?,?,CAST(? AS JSON))
                """,UUID.randomUUID().toString(),kind,id,revision,operation,backfill?historicalTime:Instant.now().toString(),Phase3Json.json(payload));
    }

    @Transactional public Map<String,Object> backfill(int size) {
        if(size<1 || size>500) throw new IllegalArgumentException("回填批次必须为1至500");
        // A persisted revision remains after deletion; do not infer cancellations from missing rows.
        var plans=db.queryForList("SELECT plan_no FROM purchase_plan WHERE plan_status IN ('approved','executed') AND NOT EXISTS(SELECT 1 FROM mcp_phase3_document doc WHERE doc.document_type='purchase_plan' AND doc.source_id=purchase_plan.plan_id AND doc.published=1) ORDER BY plan_id LIMIT ?",String.class,size);
        for(String no:plans) capturePlan(no,true);
        var bills=db.queryForList("SELECT settlement_id FROM settlement_bill WHERE status IN ('pending_confirm','confirmed') AND NOT EXISTS(SELECT 1 FROM mcp_phase3_document doc WHERE doc.document_type='settlement' AND doc.source_id=settlement_bill.settlement_id AND doc.published=1) ORDER BY settlement_id LIMIT ?",Long.class,size);
        for(Long id:bills) captureSettlement(id,false,true);
        return Map.of("plans",plans.size(),"settlements",bills.size());
    }

    public Map<String,Object> status(){return Map.of("rows",db.queryForList("SELECT message_id AS messageId,document_type AS documentType,CAST(source_id AS CHAR) AS sourceId,CAST(revision AS CHAR) AS revision,state,attempts,message,next_attempt AS nextAttempt FROM mcp_phase3_outbox ORDER BY created_at DESC LIMIT 100"));}
    @Transactional public void retry(String id){if(db.update("UPDATE mcp_phase3_outbox SET state='pending',next_attempt=NOW(6),lease_id=NULL,lease_until=NULL WHERE message_id=? AND state='failed'",id)!=1)throw new IllegalArgumentException("仅失败消息可以重试");}
    /** Explicitly republishes the latest durable snapshot when an administrator changes the destination. */
    @Transactional public void republish(String kind,long source) {
        if(!Set.of("purchase_plan","settlement").contains(kind))throw new IllegalArgumentException("来源类型不合法");
        var doc=db.queryForMap("SELECT revision FROM mcp_phase3_document WHERE document_type=? AND source_id=? FOR UPDATE",kind,source);
        long revision=((Number)doc.get("revision")).longValue();
        var row=db.queryForMap("SELECT operation,source_updated_at,source_payload FROM mcp_phase3_outbox WHERE document_type=? AND source_id=? AND revision=?",kind,source,revision);
        db.update("UPDATE mcp_phase3_document SET revision=? WHERE document_type=? AND source_id=?",revision+1,kind,source);
        db.update("UPDATE mcp_phase3_outbox SET state='superseded',message='管理员重新发布最新快照' WHERE document_type=? AND source_id=? AND state IN ('pending','failed')",kind,source);
        db.update("INSERT INTO mcp_phase3_outbox(message_id,document_type,source_id,revision,operation,source_updated_at,source_payload) VALUES(?,?,?,?,?,?,CAST(? AS JSON))",UUID.randomUUID().toString(),kind,source,revision+1,row.get("operation"),row.get("source_updated_at"),row.get("source_payload").toString());
    }
    private String time(Object value){return value==null?null:McpSourceTime.instant(value,env.getProperty("SPD_MCP_SOURCE_ZONE","Asia/Shanghai"));}
    private static String whole(Object value){BigDecimal q=(BigDecimal)value;com.hospital.spd.common.QuantityRules.positive(q,"来源数量");return q.toBigIntegerExact().toString();}
    private static String money(Object value){return ((BigDecimal)value).setScale(4,java.math.RoundingMode.UNNECESSARY).toPlainString();}
    private static void nullable(ObjectNode node,String key,Object value){if(value==null)node.putNull(key);else node.put(key,value.toString());}
}
