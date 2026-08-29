package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.InventoryEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private InventoryEventService inventoryEventService;

    @Test
    void shouldReceiveIntoNullLocationBalanceAndRecordEvent() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("4.0000");
        BigDecimal qtyAfter = new BigDecimal("14.0000");
        Map<String, Object> balance = new HashMap<>();
        balance.put("balanceId", 10L);
        balance.put("availableQty", qtyAfter);
        when(jdbcTemplate.update(contains("ON DUPLICATE KEY UPDATE"), eq(1L), eq(2L), eq(20L), eq(quantity)))
                .thenReturn(1);
        when(jdbcTemplate.queryForMap(anyString(), eq(1L), eq(2L), eq(20L))).thenReturn(balance);
        when(inventoryEventService.record("purchase_receive_in", "receiving_order", 99L, 1L, 2L, 20L,
                quantity, qtyAfter, "receive approved")).thenReturn(77L);

        Long eventId = service.receiveAvailable(1L, 2L, 20L, quantity,
                "purchase_receive_in", "receiving_order", 99L, "receive approved");

        assertThat(eventId).isEqualTo(77L);
        ArgumentCaptor<String> balanceSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(balanceSql.capture(), eq(1L), eq(2L), eq(20L));
        assertThat(balanceSql.getValue()).contains("location_id IS NULL").contains("FOR UPDATE");
        verify(jdbcTemplate).update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?",
                77L, 10L);
    }

    @Test
    void shouldLockFifoRowsAndDeductWithNonNegativeGuard() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal requestedQty = new BigDecimal("3.0000");
        BigDecimal qtyAfter = new BigDecimal("7.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("available_qty = available_qty -"), eq(requestedQty), eq(10L), eq(requestedQty)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(10L)))
                .thenReturn(qtyAfter);
        when(inventoryEventService.record("delivery_out", "delivery_order", 99L, 1L, 2L, 20L,
                requestedQty.negate(), qtyAfter, "sign delivery")).thenReturn(77L);

        List<InventoryMovementService.InventoryDeduction> deductions = service.consumeAvailableFifo(
                1L, 2L, requestedQty, "delivery_out", "delivery_order", 99L, "sign delivery");

        assertThat(deductions).containsExactly(new InventoryMovementService.InventoryDeduction(
                20L, requestedQty, new BigDecimal("5.0000")));
        ArgumentCaptor<String> fifoSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(fifoSql.capture(), eq(1L), eq(2L));
        assertThat(fifoSql.getValue()).contains("FOR UPDATE");
        verify(jdbcTemplate).update(contains("available_qty >= ?"), eq(requestedQty), eq(10L), eq(requestedQty));
        verify(jdbcTemplate).update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?", 77L, 10L);
    }

    @Test
    void shouldDeleteBalanceAfterFullAvailableStockConsumption() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("10.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("available_qty = available_qty -"), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(10L)))
                .thenReturn(BigDecimal.ZERO);
        when(inventoryEventService.record("delivery_out", "delivery_order", 99L, 1L, 2L, 20L,
                quantity.negate(), BigDecimal.ZERO, "sign delivery")).thenReturn(77L);
        when(jdbcTemplate.update(contains("DELETE FROM inventory_balance"), eq(10L))).thenReturn(1);

        service.consumeAvailableFifo(
                1L, 2L, quantity, "delivery_out", "delivery_order", 99L, "sign delivery");

        verify(jdbcTemplate).update(contains("available_qty = 0"), eq(10L));
    }

    @Test
    void shouldRejectConcurrentDeductionThatWouldOverdrawBalance() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal requestedQty = new BigDecimal("3.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("available_qty = available_qty -"), eq(requestedQty), eq(10L), eq(requestedQty)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.consumeAvailableFifo(
                1L, 2L, requestedQty, "delivery_out", "delivery_order", 99L, "sign delivery"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldDeductAcrossFifoBatchesAndRecordEachMovement() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        Map<String, Object> first = balance(10L, 20L, "2.0000", "5.0000");
        Map<String, Object> second = balance(11L, 21L, "5.0000", "6.0000");
        BigDecimal requestedQty = new BigDecimal("4.0000");
        BigDecimal firstQty = new BigDecimal("2.0000");
        BigDecimal secondQty = new BigDecimal("2.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(first, second));
        when(jdbcTemplate.update(contains("available_qty = available_qty -"), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(10L)))
                .thenReturn(BigDecimal.ZERO);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(11L)))
                .thenReturn(new BigDecimal("3.0000"));
        when(inventoryEventService.record("delivery_out", "delivery_order", 99L, 1L, 2L, 20L,
                firstQty.negate(), BigDecimal.ZERO, "sign delivery")).thenReturn(77L);
        when(inventoryEventService.record("delivery_out", "delivery_order", 99L, 1L, 2L, 21L,
                secondQty.negate(), new BigDecimal("3.0000"), "sign delivery")).thenReturn(78L);

        List<InventoryMovementService.InventoryDeduction> deductions = service.consumeAvailableFifo(
                1L, 2L, requestedQty, "delivery_out", "delivery_order", 99L, "sign delivery");

        assertThat(deductions).containsExactly(
                new InventoryMovementService.InventoryDeduction(20L, firstQty, new BigDecimal("5.0000")),
                new InventoryMovementService.InventoryDeduction(21L, secondQty, new BigDecimal("6.0000")));
        verify(inventoryEventService).record("delivery_out", "delivery_order", 99L, 1L, 2L, 20L,
                firstQty.negate(), BigDecimal.ZERO, "sign delivery");
        verify(inventoryEventService).record("delivery_out", "delivery_order", 99L, 1L, 2L, 21L,
                secondQty.negate(), new BigDecimal("3.0000"), "sign delivery");
    }

    @Test
    void shouldRejectAggregateShortageBeforeBalanceOrEventWrites() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L)))
                .thenReturn(List.of(balance(10L, 20L, "2.0000", "5.0000")));

        assertThatThrownBy(() -> service.consumeAvailableFifo(
                1L, 2L, new BigDecimal("3.0000"), "delivery_out", "delivery_order", 99L, "sign delivery"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldRejectSpecificBatchOverdrawWithoutRecordingEvent() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(20L)))
                .thenReturn(List.of(balance()));
        BigDecimal requestedQty = new BigDecimal("11.0000");
        when(jdbcTemplate.update(contains("available_qty = available_qty -"),
                eq(requestedQty), eq(10L), eq(requestedQty))).thenReturn(0);

        assertThatThrownBy(() -> service.consumeSpecificBatch(
                1L, 2L, 20L, requestedQty, "delivery_out", "delivery_order", 99L, "sign delivery"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldRejectTransferWithinSameWarehouseBeforeInventoryAccess() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);

        assertThatThrownBy(() -> service.transferAvailableFifo(
                1L, 1L, 2L, BigDecimal.ONE, "warehouse_transfer", 99L, "same warehouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");

        verifyNoInteractions(jdbcTemplate, inventoryEventService);
    }

    @Test
    void shouldReserveAvailableStockWithNonNegativeGuard() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("locked_qty = locked_qty +"), eq(quantity), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(1);

        List<InventoryMovementService.InventoryReservation> reservations =
                service.reserveAvailableFifo(1L, 2L, quantity);

        assertThat(reservations).containsExactly(new InventoryMovementService.InventoryReservation(
                10L, 20L, quantity, new BigDecimal("5.0000")));
        verify(jdbcTemplate).update(contains("available_qty >= ?"), eq(quantity), eq(quantity), eq(10L), eq(quantity));
        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldRejectConcurrentReservationThatWouldOverdrawAvailableStock() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("locked_qty = locked_qty +"), eq(quantity), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.reserveAvailableFifo(1L, 2L, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldMoveRecalledStockFromAvailableToIsolatedAndRecordEvent() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        BigDecimal qtyAfter = new BigDecimal("7.0000");
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(balance()));
        when(jdbcTemplate.update(contains("isolated_qty = isolated_qty +"),
                eq(quantity), eq(quantity), eq(10L), eq(quantity))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(10L)))
                .thenReturn(qtyAfter);
        when(inventoryEventService.record("recall_isolate", "recall_event", 99L, 1L, 2L, 20L,
                quantity.negate(), qtyAfter, "quality issue")).thenReturn(77L);

        List<InventoryMovementService.InventoryDeduction> isolated = service.isolateAvailableFifo(
                1L, 2L, quantity, "recall_event", 99L, "quality issue");

        assertThat(isolated).containsExactly(new InventoryMovementService.InventoryDeduction(
                20L, quantity, new BigDecimal("5.0000")));
        verify(jdbcTemplate).update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?", 77L, 10L);
    }

    @Test
    void shouldConsumeLockedStockAndRecordEvent() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        BigDecimal qtyAfter = new BigDecimal("7.0000");
        when(jdbcTemplate.update(contains("locked_qty = locked_qty -"), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT available_qty"), eq(BigDecimal.class), eq(10L)))
                .thenReturn(qtyAfter);
        when(inventoryEventService.record("quota_pack_out", "quota_packing_task", 99L, 1L, 2L, 20L,
                quantity.negate(), qtyAfter, "confirm packing")).thenReturn(77L);

        Long eventId = service.consumeLocked(10L, 1L, 2L, 20L, quantity,
                "quota_pack_out", "quota_packing_task", 99L, "confirm packing");

        assertThat(eventId).isEqualTo(77L);
        verify(jdbcTemplate).update(contains("locked_qty >= ?"), eq(quantity), eq(10L), eq(quantity));
        verify(jdbcTemplate).update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?", 77L, 10L);
    }

    @Test
    void shouldRejectConcurrentLockedStockOverdraw() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        when(jdbcTemplate.update(contains("locked_qty = locked_qty -"), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.consumeLocked(10L, 1L, 2L, 20L, quantity,
                "quota_pack_out", "quota_packing_task", 99L, "confirm packing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldReleaseLockedStockBackToAvailableWithGuard() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        when(jdbcTemplate.update(contains("available_qty = available_qty +"), eq(quantity), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(1);

        service.releaseLockedToAvailable(10L, quantity);

        verify(jdbcTemplate).update(contains("locked_qty >= ?"), eq(quantity), eq(quantity), eq(10L), eq(quantity));
        verifyNoInteractions(inventoryEventService);
    }

    @Test
    void shouldRejectConcurrentReleaseThatWouldOverdrawLockedStock() {
        InventoryMovementService service = new InventoryMovementService(jdbcTemplate, inventoryEventService);
        BigDecimal quantity = new BigDecimal("3.0000");
        when(jdbcTemplate.update(contains("available_qty = available_qty +"), eq(quantity), eq(quantity), eq(10L), eq(quantity)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.releaseLockedToAvailable(10L, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative stock is not allowed");

        verifyNoInteractions(inventoryEventService);
    }

    private static Map<String, Object> balance() {
        return balance(10L, 20L, "10.0000", "5.0000");
    }

    private static Map<String, Object> balance(Long balanceId, Long batchId,
                                               String availableQty, String unitPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("balanceId", balanceId);
        row.put("batchId", batchId);
        row.put("availableQty", new BigDecimal(availableQty));
        row.put("unitPrice", new BigDecimal(unitPrice));
        return row;
    }
}
