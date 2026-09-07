package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeliverySigningRegressionTest {
    @Test
    void missingHistoricalBatchEvidencePreventsReceiptAndStatusChanges() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        when(jdbc.queryForMap(contains("FROM spd_delivery_order"), eq("PS003")))
                .thenReturn(Map.of("deliveryId", 3L, "status", "picked", "requisitionNo", "SL003",
                        "quantity", java.math.BigDecimal.TEN));
        when(jdbc.queryForList(contains("SELECT dr.source_warehouse_id"), eq("SL003")))
                .thenReturn(java.util.List.of(Map.of("sourceWarehouseId", 10L,
                        "destinationWarehouseId", 20L, "deptId", 3L)));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new OperationalDeliveryModule(jdbc, support).signDelivery("PS003"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(support);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void looseSigningReceivesOriginalBatchWithoutDeductingSourceAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        when(jdbc.queryForMap(contains("FROM spd_delivery_order"), eq("PS002")))
                .thenReturn(Map.of("deliveryId", 2L, "status", "picked", "requisitionNo", "SL002",
                        "warehouseName", "中心库", "deptName", "内科", "productCode", "P1",
                        "quantity", new java.math.BigDecimal("4")));
        when(jdbc.queryForList(contains("SELECT dr.source_warehouse_id"), eq("SL002")))
                .thenReturn(java.util.List.of(Map.of("sourceWarehouseId", 10L, "destinationWarehouseId", 20L, "deptId", 3L)));
        when(jdbc.queryForList(contains("FROM spd_delivery_batch"), eq(2L)))
                .thenReturn(java.util.List.of(Map.of("productId", 5L, "batchId", 6L, "sourceWarehouseId", 10L,
                        "quantity", new java.math.BigDecimal("4"))));
        when(jdbc.queryForObject(contains("COUNT(*) FROM spd_delivery"), eq(Integer.class), eq(2L))).thenReturn(0);
        when(jdbc.update(startsWith("UPDATE spd_delivery_order"), eq(20L), eq("PS002"))).thenReturn(1);
        assertThat(new OperationalDeliveryModule(jdbc, support).signDelivery("PS002")).containsEntry("status", "signed");
        verify(support).receiveAvailable(eq(20L), eq(5L), eq(6L), eq(new java.math.BigDecimal("4")),
                eq("delivery_sign_in"), eq("spd_delivery_order"), eq(2L), anyString());
        verify(support, never()).transferAvailableFifo(any(), any(), any(), any(), any(), any(), any());
    }
    @Test
    void repeatedSigningDoesNotMoveInventoryAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        when(jdbc.queryForMap(contains("FROM spd_delivery_order"), eq("PS001")))
                .thenReturn(Map.of("deliveryId", 1L, "status", "signed"));
        assertThat(new OperationalDeliveryModule(jdbc, support).signDelivery("PS001"))
                .containsEntry("status", "signed");
        verifyNoInteractions(support);
    }
}
