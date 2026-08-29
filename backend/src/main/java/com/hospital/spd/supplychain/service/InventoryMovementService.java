package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.InventoryEventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns inventory balance mutations and the matching immutable inventory events.
 */
@Service
@Transactional
public class InventoryMovementService {

    private static final String INSUFFICIENT_INVENTORY_MESSAGE =
            "inventory is insufficient, negative stock is not allowed";

    private final JdbcTemplate jdbcTemplate;
    private final InventoryEventService inventoryEventService;

    public InventoryMovementService(JdbcTemplate jdbcTemplate, InventoryEventService inventoryEventService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryEventService = inventoryEventService;
    }

    public Long receiveAvailable(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                 String eventType, String sourceType, Long sourceId, String remark) {
        requirePositive(quantity);
        jdbcTemplate.update("""
                INSERT INTO inventory_balance (
                  warehouse_id, location_id, product_id, batch_id, available_qty, locked_qty, in_transit_qty, isolated_qty
                ) VALUES (?, NULL, ?, ?, ?, 0, 0, 0)
                ON DUPLICATE KEY UPDATE available_qty = available_qty + VALUES(available_qty)
                """, warehouseId, productId, batchId, quantity);
        Map<String, Object> balance = jdbcTemplate.queryForMap("""
                SELECT balance_id AS balanceId, available_qty AS availableQty
                 FROM inventory_balance
                 WHERE warehouse_id = ? AND location_id IS NULL AND product_id = ? AND batch_id = ?
                 ORDER BY balance_id DESC
                 LIMIT 1
                 FOR UPDATE
                """, warehouseId, productId, batchId);
        Long eventId = recordEvent(eventType, sourceType, sourceId, warehouseId, productId, batchId,
                quantity, (BigDecimal) balance.get("availableQty"), remark);
        jdbcTemplate.update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?",
                eventId, balance.get("balanceId"));
        return eventId;
    }

    public List<InventoryDeduction> consumeAvailableFifo(Long warehouseId, Long productId, BigDecimal requiredQty,
                                                         String eventType, String sourceType, Long sourceId,
                                                         String remark) {
        requirePositive(requiredQty);
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, bal.batch_id AS batchId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                 FOR UPDATE
                """, warehouseId, productId);
        BigDecimal totalAvailable = balances.stream()
                .map(balance -> (BigDecimal) balance.get("availableQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAvailable.compareTo(requiredQty) < 0) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        BigDecimal remaining = requiredQty;
        List<InventoryDeduction> deductions = new ArrayList<>();
        for (Map<String, Object> balance : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal availableQty = (BigDecimal) balance.get("availableQty");
            BigDecimal deductQty = availableQty.min(remaining);
            Object balanceId = balance.get("balanceId");
            int affected = jdbcTemplate.update("""
                    UPDATE inventory_balance
                       SET available_qty = available_qty - ?
                     WHERE balance_id = ? AND available_qty >= ?
                    """, deductQty, balanceId, deductQty);
            if (affected != 1) {
                throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
            }
            BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                    "SELECT available_qty FROM inventory_balance WHERE balance_id = ?",
                    BigDecimal.class,
                    balanceId);
            Long batchId = ((Number) balance.get("batchId")).longValue();
            Long eventId = recordEvent(eventType, sourceType, sourceId, warehouseId, productId,
                    batchId, deductQty.negate(), qtyAfter, remark);
            finalizeBalanceMutation(balanceId, eventId);
            deductions.add(new InventoryDeduction(batchId, deductQty, (BigDecimal) balance.get("unitPrice")));
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        return deductions;
    }

    public List<InventoryDeduction> transferAvailableFifo(Long sourceWarehouseId, Long destinationWarehouseId,
                                                           Long productId, BigDecimal quantity,
                                                           String sourceType, Long sourceId, String remark) {
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new IllegalArgumentException("source and destination warehouses must be different");
        }
        List<InventoryDeduction> deductions = consumeAvailableFifo(sourceWarehouseId, productId, quantity,
                "warehouse_transfer_out", sourceType, sourceId, remark);
        for (InventoryDeduction deduction : deductions) {
            receiveAvailable(destinationWarehouseId, productId, deduction.batchId(), deduction.quantity(),
                    "warehouse_transfer_in", sourceType, sourceId, remark);
        }
        return deductions;
    }
    public InventoryDeduction consumeSpecificBatch(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                                    String eventType, String sourceType, Long sourceId, String remark) {
        requirePositive(quantity);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.batch_id = ?
                 LIMIT 1
                 FOR UPDATE
                """, warehouseId, productId, batchId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        Map<String, Object> balance = rows.get(0);
        Long balanceId = ((Number) balance.get("balanceId")).longValue();
        int affected = jdbcTemplate.update("""
                UPDATE inventory_balance
                   SET available_qty = available_qty - ?
                 WHERE balance_id = ? AND available_qty >= ?
                """, quantity, balanceId, quantity);
        if (affected != 1) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                "SELECT available_qty FROM inventory_balance WHERE balance_id = ?", BigDecimal.class, balanceId);
        Long eventId = recordEvent(eventType, sourceType, sourceId, warehouseId, productId,
                batchId, quantity.negate(), qtyAfter, remark);
        finalizeBalanceMutation(balanceId, eventId);
        return new InventoryDeduction(batchId, quantity, (BigDecimal) balance.get("unitPrice"));
    }

    public InventoryDeduction transferSpecificBatch(Long sourceWarehouseId, Long destinationWarehouseId,
                                                     Long productId, Long batchId, BigDecimal quantity,
                                                     String sourceType, Long sourceId, String remark) {
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new IllegalArgumentException("source and destination warehouses must be different");
        }
        InventoryDeduction deduction = consumeSpecificBatch(sourceWarehouseId, productId, batchId, quantity,
                "warehouse_transfer_out", sourceType, sourceId, remark);
        receiveAvailable(destinationWarehouseId, productId, batchId, quantity,
                "warehouse_transfer_in", sourceType, sourceId, remark);
        return deduction;
    }

    public List<InventoryReservation> reserveAvailableFifo(Long warehouseId, Long productId, BigDecimal requiredQty) {
        requirePositive(requiredQty);
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, bal.batch_id AS batchId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                 FOR UPDATE
                """, warehouseId, productId);
        BigDecimal remaining = requiredQty;
        List<InventoryReservation> reservations = new ArrayList<>();
        for (Map<String, Object> balance : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal availableQty = (BigDecimal) balance.get("availableQty");
            BigDecimal reserveQty = availableQty.min(remaining);
            Object balanceId = balance.get("balanceId");
            int affected = jdbcTemplate.update("""
                    UPDATE inventory_balance
                       SET available_qty = available_qty - ?, locked_qty = locked_qty + ?
                     WHERE balance_id = ? AND available_qty >= ?
                    """, reserveQty, reserveQty, balanceId, reserveQty);
            if (affected != 1) {
                throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
            }
            reservations.add(new InventoryReservation(
                    ((Number) balanceId).longValue(),
                    ((Number) balance.get("batchId")).longValue(),
                    reserveQty,
                    (BigDecimal) balance.get("unitPrice")));
            remaining = remaining.subtract(reserveQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        return reservations;
    }

    /**
     * Isolates available inventory from one specific batch: available quantity moves to isolated.
     */
    public InventoryDeduction isolateSpecificBatch(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                                    String sourceType, Long sourceId, String remark) {
        requirePositive(quantity);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.batch_id = ?
                 LIMIT 1
                 FOR UPDATE
                """, warehouseId, productId, batchId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        Map<String, Object> balance = rows.get(0);
        Long balanceId = ((Number) balance.get("balanceId")).longValue();
        int affected = jdbcTemplate.update("""
                UPDATE inventory_balance
                   SET available_qty = available_qty - ?, isolated_qty = isolated_qty + ?
                 WHERE balance_id = ? AND available_qty >= ?
                """, quantity, quantity, balanceId, quantity);
        if (affected != 1) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                "SELECT available_qty FROM inventory_balance WHERE balance_id = ?", BigDecimal.class, balanceId);
        Long eventId = recordEvent("recall_isolate", sourceType, sourceId, warehouseId, productId,
                batchId, quantity.negate(), qtyAfter, remark);
        finalizeBalanceMutation(balanceId, eventId);
        return new InventoryDeduction(batchId, quantity, (BigDecimal) balance.get("unitPrice"));
    }

    public List<InventoryDeduction> isolateAvailableFifo(Long warehouseId, Long productId, BigDecimal requiredQty,
                                                          String sourceType, Long sourceId, String remark) {
        requirePositive(requiredQty);
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, bal.batch_id AS batchId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                 FOR UPDATE
                """, warehouseId, productId);
        BigDecimal remaining = requiredQty;
        List<InventoryDeduction> isolated = new ArrayList<>();
        for (Map<String, Object> balance : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal quantity = ((BigDecimal) balance.get("availableQty")).min(remaining);
            Long balanceId = ((Number) balance.get("balanceId")).longValue();
            int affected = jdbcTemplate.update("""
                    UPDATE inventory_balance
                       SET available_qty = available_qty - ?, isolated_qty = isolated_qty + ?
                     WHERE balance_id = ? AND available_qty >= ?
                    """, quantity, quantity, balanceId, quantity);
            if (affected != 1) throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
            BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                    "SELECT available_qty FROM inventory_balance WHERE balance_id = ?", BigDecimal.class, balanceId);
            Long batchId = ((Number) balance.get("batchId")).longValue();
            Long eventId = recordEvent("recall_isolate", sourceType, sourceId, warehouseId, productId,
                    batchId, quantity.negate(), qtyAfter, remark);
            finalizeBalanceMutation(balanceId, eventId);
            isolated.add(new InventoryDeduction(batchId, quantity, (BigDecimal) balance.get("unitPrice")));
            remaining = remaining.subtract(quantity);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        return isolated;
    }

    public Long adjustAvailable(Long balanceId, Long warehouseId, Long productId, Long batchId, BigDecimal quantityChange,
                                String eventType, String sourceType, Long sourceId, String remark) {
        if (quantityChange == null || quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("inventory adjustment quantity must not be zero");
        }
        Map<String, Object> balance = jdbcTemplate.queryForMap("""
                SELECT available_qty AS availableQty
                  FROM inventory_balance
                 WHERE balance_id = ?
                 FOR UPDATE
                """, balanceId);
        BigDecimal currentQty = (BigDecimal) balance.get("availableQty");
        if (currentQty.add(quantityChange).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        int affected = jdbcTemplate.update("""
                UPDATE inventory_balance
                   SET available_qty = available_qty + ?
                 WHERE balance_id = ? AND available_qty + ? >= 0
                """, quantityChange, balanceId, quantityChange);
        if (affected != 1) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                "SELECT available_qty FROM inventory_balance WHERE balance_id = ?",
                BigDecimal.class,
                balanceId);
        Long eventId = recordEvent(eventType, sourceType, sourceId, warehouseId, productId,
                batchId, quantityChange, qtyAfter, remark);
        finalizeBalanceMutation(balanceId, eventId);
        return eventId;
    }

    public Long consumeLocked(Long balanceId, Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                              String eventType, String sourceType, Long sourceId, String remark) {
        requirePositive(quantity);
        int affected = jdbcTemplate.update("""
                UPDATE inventory_balance
                   SET locked_qty = locked_qty - ?
                 WHERE balance_id = ? AND locked_qty >= ?
                """, quantity, balanceId, quantity);
        if (affected != 1) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
        BigDecimal qtyAfter = jdbcTemplate.queryForObject(
                "SELECT available_qty FROM inventory_balance WHERE balance_id = ?",
                BigDecimal.class,
                balanceId);
        Long eventId = recordEvent(eventType, sourceType, sourceId, warehouseId, productId,
                batchId, quantity.negate(), qtyAfter, remark);
        finalizeBalanceMutation(balanceId, eventId);
        return eventId;
    }

    public void releaseLockedToAvailable(Long balanceId, BigDecimal quantity) {
        requirePositive(quantity);
        int affected = jdbcTemplate.update("""
                UPDATE inventory_balance
                   SET available_qty = available_qty + ?, locked_qty = locked_qty - ?
                 WHERE balance_id = ? AND locked_qty >= ?
                """, quantity, quantity, balanceId, quantity);
        if (affected != 1) {
            throw new IllegalArgumentException(INSUFFICIENT_INVENTORY_MESSAGE);
        }
    }

    private void finalizeBalanceMutation(Object balanceId, Long eventId) {
        int deleted = jdbcTemplate.update("""
                DELETE FROM inventory_balance
                 WHERE balance_id = ?
                   AND available_qty = 0
                   AND locked_qty = 0
                   AND in_transit_qty = 0
                   AND isolated_qty = 0
                """, balanceId);
        if (deleted == 0) {
            jdbcTemplate.update("UPDATE inventory_balance SET last_event_id = ? WHERE balance_id = ?",
                    eventId, balanceId);
        }
    }

    private Long recordEvent(String eventType, String sourceType, Long sourceId, Long warehouseId, Long productId,
                             Long batchId, BigDecimal qtyChange, BigDecimal qtyAfter, String remark) {
        return inventoryEventService.record(eventType, sourceType, sourceId, warehouseId, productId,
                batchId, qtyChange, qtyAfter, remark);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("inventory quantity must be greater than zero");
        }
    }

    public record InventoryDeduction(Long batchId, BigDecimal quantity, BigDecimal unitPrice) {
    }

    public record InventoryReservation(Long balanceId, Long batchId, BigDecimal quantity, BigDecimal unitPrice) {
    }
}
