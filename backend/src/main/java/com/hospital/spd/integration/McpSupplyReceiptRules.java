package com.hospital.spd.integration;

import com.hospital.spd.supplychain.ReceivingItemRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;

/** Keeps hospital edits of imported drafts within the supplier's immutable order-line declarations. */
public final class McpSupplyReceiptRules {
    private final JdbcTemplate db;
    public McpSupplyReceiptRules(JdbcTemplate db){this.db=db;}
    public void validateEdit(long receivingId,Long orderId,List<ReceivingItemRequest> items) {
        var rows=db.queryForList("SELECT purchase_order_id,payload FROM mcp_supply_receipt WHERE receiving_order_id=?",receivingId);
        if(rows.isEmpty())return;
        var row=rows.get(0);
        if(orderId==null || ((Number)row.get("purchase_order_id")).longValue()!=orderId)throw new IllegalArgumentException("MCP供货收货单不能更换来源采购订单");
        var limits=new HashMap<Long,BigDecimal>();
        Phase3Json.parse(row.get("payload").toString()).path("payload").path("items").forEach(line->limits.merge(Long.parseLong(line.path("orderLineId").asText()),new BigDecimal(line.path("quantity").asText()),BigDecimal::add));
        var totals=new HashMap<Long,BigDecimal>();
        for(var item:items) {
            Long id=item.purchaseOrderItemId();
            if(id==null || !limits.containsKey(id) || totals.merge(id,item.quantity(),BigDecimal::add).compareTo(limits.get(id))>0)
                throw new IllegalArgumentException("收货明细或数量不能超出MCP原供货单申报范围");
        }
    }
}
