package com.hospital.spd.integration;

import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpPhase2SourceTest {
  @Test void ambiguousPurchaseLineStopsWholeReceiptWithoutGuessing() {
    var db=mock(JdbcTemplate.class);var source=new McpPhase2Source(db,new MockEnvironment());
    var row=new HashMap<String,Object>();row.put("receiving_order_id",10L);row.put("receiving_no","R1");row.put("receiving_status","approved");row.put("order_no","PO1");row.put("purchase_order_id",1L);
    row.put("create_time",Timestamp.valueOf("2026-10-08 10:00:00"));row.put("update_time",Timestamp.valueOf("2026-10-08 10:00:00"));
    when(db.queryForList(contains("SELECT ro.*"),eq(0L))).thenReturn(List.of(row));
    when(db.queryForList(contains("SELECT i.*"),eq(1L),eq(10L))).thenReturn(List.of(Map.of("item_id",5L)));
    var result=source.receipts(0);
    assertEquals(10,result.cursor());assertEquals(1,result.records().size());assertTrue(result.records().get(0).error().contains("无法唯一关联"));assertTrue(result.records().get(0).payload().isEmpty());
  }
  @Test void inboundMissingImmutableAmountCannotInventPrice() {
    var db=mock(JdbcTemplate.class);var source=new McpPhase2Source(db,new MockEnvironment());
    when(db.queryForList(contains("SELECT e.*"),eq(0L))).thenReturn(List.of(Map.of("event_id",1L,"event_no","E1")));
    var result=source.inbounds(0);
    assertEquals("E1",result.records().get(0).code());assertTrue(result.records().get(0).error().contains("不可变金额"));assertTrue(result.records().get(0).payload().isEmpty());
  }
}
