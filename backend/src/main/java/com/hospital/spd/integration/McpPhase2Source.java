package com.hospital.spd.integration;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Exports real purchasing receipts, immutable receipt-in events and current loose plus packaged batch balances. */
@Service
public class McpPhase2Source {
  public record Upload(String kind,String code,Map<String,Object> payload,String error) {}
  public record Page(long cursor,List<Upload> records) {}
  private final JdbcTemplate db;private final Environment env;
  public McpPhase2Source(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Page receipts(long cursor) {
    var rows=db.queryForList("SELECT ro.*,DATE(ro.create_time) AS document_date,DATE(ro.receive_time) AS received_date,DATE_FORMAT(ro.update_time,'%Y-%m-%dT%H:%i:%s.%f') AS source_wall_time,po.order_no,s.supplier_code,w.warehouse_code,w.warehouse_name FROM receiving_order ro JOIN purchase_order po ON po.purchase_order_id=ro.purchase_order_id JOIN supplier s ON s.supplier_id=ro.supplier_id JOIN warehouse w ON w.warehouse_id=ro.warehouse_id WHERE ro.receiving_order_id>? ORDER BY ro.receiving_order_id LIMIT 100",cursor);
    var records=new ArrayList<Upload>();long last=cursor;
    for(var row:rows) {
      long id=((Number)row.get("receiving_order_id")).longValue();last=id;String code=row.get("receiving_no").toString();
      try {
        String status=row.get("receiving_status").toString();
        if(!Set.of("draft","approved","rejected").contains(status)) throw new IllegalArgumentException("收货状态无法映射: "+status);
        var payload=new LinkedHashMap<String,Object>();payload.put("documentNo",code);payload.put("orderNo",row.get("order_no"));payload.put("supplierCode",row.get("supplier_code"));
        payload.put("warehouseCode",row.get("warehouse_code"));payload.put("warehouseName",row.get("warehouse_name"));payload.put("documentDate",calendarDate(row,"document_date","create_time"));
        payload.put("receivedDate",row.get("receive_time")==null?null:calendarDate(row,"received_date","receive_time"));payload.put("status",status);payload.put("sourceUpdatedAt",sourceVersion(row,"update_time"));
        var items=db.queryForList("""
            SELECT i.*,p.product_code,p.unit,
              COALESCE(i.purchase_order_item_id,(SELECT CASE WHEN COUNT(*)=1 THEN MIN(oi.item_id) ELSE NULL END FROM purchase_order_item oi WHERE oi.purchase_order_id=? AND oi.product_id=i.product_id)) AS order_line_id
            FROM receiving_order_item i JOIN product p ON p.product_id=i.product_id WHERE i.receiving_order_id=? ORDER BY i.item_id
            """,row.get("purchase_order_id"),id);
        var lines=new ArrayList<Map<String,Object>>();
        for(var i:items) {
          if(i.get("order_line_id")==null) throw new IllegalArgumentException("收货明细 "+i.get("item_id")+" 无法唯一关联采购明细，请补充purchaseOrderItemId");
          var l=new LinkedHashMap<String,Object>();l.put("lineId",i.get("item_id").toString());l.put("orderLineId",i.get("order_line_id").toString());l.put("productCode",i.get("product_code"));l.put("unit",i.get("unit"));l.put("productionBatchNo",i.get("production_batch_no"));
          l.put("quantity",i.get("quantity"));l.put("qualifiedQuantity",status.equals("rejected")?BigDecimal.ZERO:i.get("qualified_quantity"));l.put("rejectedQuantity",status.equals("rejected")?i.get("quantity"):i.get("unqualified_quantity"));l.put("reason",row.get("remark"));lines.add(l);
        }
        payload.put("items",lines);records.add(new Upload("receiving",code,payload,null));
      }catch(IllegalArgumentException e){records.add(new Upload("receiving",code,Map.of(),e.getMessage()));}
    }
    return new Page(last,records);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Page inbounds(long cursor) {
    var rows=db.queryForList("""
        SELECT e.*,DATE(e.event_time) AS document_date,DATE_FORMAT(e.event_time,'%Y-%m-%dT%H:%i:%s.%f') AS source_wall_time,ro.receiving_no,b.receiving_item_id,b.production_date,b.expire_date,w.warehouse_code,w.warehouse_name
        FROM inventory_event e JOIN inventory_batch b ON b.batch_id=e.batch_id
        JOIN receiving_order ro ON ro.receiving_order_id=b.receiving_order_id AND ro.purchase_order_id IS NOT NULL
        JOIN warehouse w ON w.warehouse_id=e.warehouse_id
        WHERE e.event_id>? AND e.event_type='purchase_receive_in' AND e.qty_change>0
        ORDER BY e.event_id LIMIT 100
        """,cursor);
    var records=new ArrayList<Upload>();long last=cursor;
    for(var row:rows) {
      last=((Number)row.get("event_id")).longValue();String code=row.get("event_no").toString();
      if(row.get("amount_snapshot")==null || row.get("product_code_snapshot")==null || row.get("unit_snapshot")==null || row.get("receiving_item_id")==null || row.get("system_batch_no_snapshot")==null) {records.add(new Upload("inbound",code,Map.of(),"采购入库事件缺少不可变金额、商品单位快照或验收批次关联"));continue;}
      var p=new LinkedHashMap<String,Object>();p.put("documentNo",code);p.put("receivingNo",row.get("receiving_no"));p.put("warehouseCode",row.get("warehouse_code"));p.put("warehouseName",row.get("warehouse_name"));
      p.put("documentDate",calendarDate(row,"document_date","event_time"));p.put("status","completed");p.put("sourceUpdatedAt",sourceVersion(row,"event_time"));
      p.put("amountOrigin",Objects.toString(row.get("snapshot_origin"),"legacy_backfill"));
      var line=new LinkedHashMap<String,Object>();line.put("lineId",row.get("event_id").toString());line.put("receivingLineId",row.get("receiving_item_id").toString());line.put("productCode",row.get("product_code_snapshot"));line.put("unit",row.get("unit_snapshot"));line.put("systemBatchNo",row.get("system_batch_no_snapshot"));
      line.put("productionBatchNo",row.get("production_batch_no_snapshot"));line.put("productionDate",text(row.get("production_date")));line.put("expiryDate",text(row.get("expire_date")));line.put("quantity",row.get("qty_change"));line.put("amount",row.get("amount_snapshot"));p.put("items",List.of(line));records.add(new Upload("inbound",code,p,null));
    }
    return new Page(last,records);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Map<String,Object> inventory() {
    Instant version=Instant.now();
    Integer incomplete=db.queryForObject("SELECT COUNT(*) FROM quota_package_label l JOIN product p ON p.product_id=l.product_id JOIN mcp_inbound_product mp ON mp.product_code=p.product_code WHERE l.status IN ('pending_print','available') AND COALESCE((SELECT SUM(s.source_qty) FROM quota_package_label_source s WHERE s.label_id=l.label_id),0) <> l.package_quantity",Integer.class);
    if(incomplete!=null && incomplete>0) throw new IllegalArgumentException("在库定数包缺少完整来源批次，不能生成准确批次库存，请补齐来源后重试");
    var rows=db.queryForList("""
        SELECT w.warehouse_code,w.warehouse_name,p.product_code,p.unit,b.system_batch_no,b.production_batch_no,b.production_date,b.expire_date,
          SUM(q.loose_qty) AS loose_qty,SUM(q.package_qty) AS package_qty,SUM(q.locked_qty) AS locked_qty,SUM(q.isolated_qty) AS isolated_qty,SUM(q.in_transit_qty) AS in_transit_qty,
          (SUM(q.loose_qty)+SUM(q.package_qty))*p.purchase_price AS amount
        FROM (
          SELECT warehouse_id,product_id,batch_id,available_qty AS loose_qty,0 AS package_qty,locked_qty,isolated_qty,in_transit_qty FROM inventory_balance WHERE location_id IS NULL
          UNION ALL
          SELECT l.warehouse_id,l.product_id,s.batch_id,0,s.source_qty,0,0,0 FROM quota_package_label l JOIN quota_package_label_source s ON s.label_id=l.label_id WHERE l.status IN ('pending_print','available')
        ) q JOIN product p ON p.product_id=q.product_id AND p.deleted=0 JOIN inventory_batch b ON b.batch_id=q.batch_id
        JOIN warehouse w ON w.warehouse_id=q.warehouse_id
        JOIN mcp_inbound_product mp ON mp.product_code=p.product_code
        GROUP BY w.warehouse_code,w.warehouse_name,p.product_code,p.unit,b.system_batch_no,b.production_batch_no,b.production_date,b.expire_date,p.purchase_price
        ORDER BY w.warehouse_code,p.product_code,b.system_batch_no
        """);
    if(rows.size()>10000) throw new IllegalArgumentException("完整批次库存超过10000行，需先扩展分片快照协议");
    var items=new ArrayList<Map<String,Object>>();
    for(var row:rows) {
      var l=new LinkedHashMap<String,Object>();l.put("warehouseCode",row.get("warehouse_code"));l.put("warehouseName",row.get("warehouse_name"));l.put("productCode",row.get("product_code"));l.put("unit",row.get("unit"));l.put("systemBatchNo",row.get("system_batch_no"));l.put("productionBatchNo",row.get("production_batch_no"));
      l.put("productionDate",text(row.get("production_date")));l.put("expiryDate",text(row.get("expire_date")));l.put("looseQuantity",row.get("loose_qty"));l.put("packagedQuantity",row.get("package_qty"));l.put("lockedQuantity",row.get("locked_qty"));l.put("isolatedQuantity",row.get("isolated_qty"));l.put("inTransitQuantity",row.get("in_transit_qty"));l.put("amount",row.get("amount"));items.add(l);
    }
    var payload=new LinkedHashMap<String,Object>();payload.put("items",items);payload.put("sourceUpdatedAt",version.toString());payload.put("valuationBasis","catalog_purchase_price");return payload;
  }
  private String timestamp(Object time){return McpSourceTime.instant(time,env.getProperty("SPD_MCP_SOURCE_ZONE","Asia/Shanghai"));}
  private String sourceVersion(Map<String,Object> row,String fallback){return row.get("source_wall_time")==null?timestamp(row.get(fallback)):LocalDateTime.parse(row.get("source_wall_time").toString()).atZone(ZoneId.of(env.getProperty("SPD_MCP_SOURCE_ZONE","Asia/Shanghai"))).toInstant().toString();}
  private String calendarDate(Map<String,Object> row,String column,String fallback){return row.get(column)==null?day(row.get(fallback)):row.get(column).toString();}
  private String day(Object time){return McpSourceTime.date(time).toString();}
  private String text(Object value){return value==null?null:value.toString();}
}
