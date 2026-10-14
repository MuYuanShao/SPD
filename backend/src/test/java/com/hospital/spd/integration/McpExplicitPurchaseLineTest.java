package com.hospital.spd.integration;

import com.hospital.spd.supplychain.service.PurchaseFulfillmentService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class McpExplicitPurchaseLineTest {
  @Test void explicitReceiptUpdatesOnlyItsOriginalPurchaseLine() {
    var db=mock(JdbcTemplate.class);var service=new PurchaseFulfillmentService(db);var quantity=new BigDecimal("3");
    when(db.update(anyString(),eq(quantity),eq(22L),eq(1L),eq(2L),eq(quantity))).thenReturn(1);
    service.recordAcceptedReceipt(1L,2L,quantity,22L);
    verify(db).update(contains("item_id=? AND purchase_order_id=? AND product_id=?"),eq(quantity),eq(22L),eq(1L),eq(2L),eq(quantity));
    assertThrows(IllegalStateException.class,()->service.recordAcceptedReceipt(1L,2L,quantity,23L));
  }
}
