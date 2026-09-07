package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.InventoryEventService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BatchPriceAdjustmentServiceTest {

    @Test
    void rejectsStaleDraftWithoutWritingValuation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperatorContextProvider operators = mock(OperatorContextProvider.class);
        InventoryEventService events = mock(InventoryEventService.class);
        BatchPriceAdjustmentService service = new BatchPriceAdjustmentService(jdbc,
                mock(SupplyChainSupport.class), operators, mock(ApprovalFlowGuard.class), events,
                mock(AuditLogService.class));
        when(operators.current()).thenReturn(OperatorContext.system());
        when(jdbc.queryForMap(contains("FROM batch_price_adjustment"), eq("TJ002"))).thenReturn(Map.of(
                "adjustmentId", 7L, "batchId", 8L, "oldUnitPrice", new BigDecimal("10.00"),
                "newUnitPrice", new BigDecimal("15.00"), "status", "draft"));
        when(jdbc.queryForObject(contains("FROM inventory_batch"), eq(BigDecimal.class), eq(8L)))
                .thenReturn(new BigDecimal("12.00"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approve("TJ002"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("价格已变化");
        verifyNoInteractions(events);
    }

    @Test
    void approvalLocksBalancesAndCreatesValuationEventsBeforeCompleting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        OperatorContextProvider operators = mock(OperatorContextProvider.class);
        ApprovalFlowGuard approvals = mock(ApprovalFlowGuard.class);
        InventoryEventService events = mock(InventoryEventService.class);
        AuditLogService audit = mock(AuditLogService.class);
        BatchPriceAdjustmentService service = new BatchPriceAdjustmentService(
                jdbc, support, operators, approvals, events, audit);
        when(operators.current()).thenReturn(OperatorContext.system());
        when(jdbc.queryForMap(contains("FROM batch_price_adjustment"), eq("TJ001"))).thenReturn(Map.of(
                "adjustmentId", 7L, "batchId", 8L, "oldUnitPrice", new BigDecimal("10.00"),
                "newUnitPrice", new BigDecimal("12.00"), "reason", "合同调价", "status", "draft"));
        when(jdbc.queryForList(contains("FROM inventory_balance"), eq(8L))).thenReturn(List.of(Map.of("balanceId", 9L)));
        when(jdbc.queryForObject(contains("FROM inventory_batch"), eq(BigDecimal.class), eq(8L)))
                .thenReturn(new BigDecimal("10.00"));
        when(events.recordValuationEvents(eq(7L), eq(8L), eq(new BigDecimal("10.00")),
                eq(new BigDecimal("12.00")), anyString())).thenReturn(List.of(101L, 102L));
        when(jdbc.update(startsWith("UPDATE batch_price_adjustment"), eq(1L), eq(7L))).thenReturn(1);

        Map<String, Object> result = service.approve("TJ001");

        assertThat(result).containsEntry("status", "approved").containsEntry("valuationEventCount", 2);
        verify(jdbc).queryForList(contains("FOR UPDATE"), eq(8L));
        verify(jdbc).update("UPDATE inventory_batch SET batch_unit_price = ? WHERE batch_id = ?",
                new BigDecimal("12.00"), 8L);
        verify(events).recordValuationEvents(7L, 8L, new BigDecimal("10.00"),
                new BigDecimal("12.00"), "批次调价：TJ001");
        verify(audit).record(eq("batch_price_adjustment"), eq("approve"), eq(7L), eq("TJ001"), contains("2 条"));
    }
}
