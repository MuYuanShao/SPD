package com.hospital.spd.supplychain;

import com.hospital.spd.common.service.InventoryEventCommand;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import com.hospital.spd.supplychain.service.InventoryMovementService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SupplyChainSupport {

    private final DocumentNumberService documentNumberService;
    private final AuditLogService auditLogService;
    private final InventoryMovementService inventoryMovementService;

    public SupplyChainSupport(DocumentNumberService documentNumberService,
                              AuditLogService auditLogService,
                              InventoryMovementService inventoryMovementService) {
        this.documentNumberService = documentNumberService;
        this.auditLogService = auditLogService;
        this.inventoryMovementService = inventoryMovementService;
    }

    /**
     * Allocates a business document number without leaking prefix/table/column policy to callers.
     */
    public String nextNo(DocumentKind kind) {
        return documentNumberService.next(kind);
    }

    /**
     * Compatibility entry point for legacy services that still ask the supply-chain facade for numbers.
     */
    @Deprecated(forRemoval = false)
    public String nextNo(String prefix, String table, String column) {
        return nextNo(prefix, table, column, 5);
    }

    @Deprecated(forRemoval = false)
    public String nextNo(String prefix, String table, String column, int width) {
        return documentNumberService.next(prefix, width, table, column);
    }

    /**
     * Compatibility entry point for legacy services that still ask the supply-chain facade for audit rows.
     */
    public void writeAudit(String bizType, String operationType, Long bizId, String bizNo, String remark) {
        auditLogService.record(bizType, operationType, bizId, bizNo, remark);
    }

    /**
     * Deducts available inventory by expiry/batch order and writes one inventory event per consumed batch.
     */
    public List<InventoryDeduction> consumeAvailableFifo(Long warehouseId, Long productId, BigDecimal requiredQty,
                                                         String eventType, String sourceType, Long sourceId,
                                                         String remark) {
        return inventoryMovementService.consumeAvailableFifo(warehouseId, productId, requiredQty,
                        eventType, sourceType, sourceId, remark)
                .stream()
                .map(deduction -> new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice()))
                .toList();
    }

    public List<InventoryReservation> reserveAvailableFifo(Long warehouseId, Long productId, BigDecimal requiredQty) {
        return inventoryMovementService.reserveAvailableFifo(warehouseId, productId, requiredQty)
                .stream()
                .map(reservation -> new InventoryReservation(
                        reservation.balanceId(), reservation.batchId(), reservation.quantity(), reservation.unitPrice()))
                .toList();
    }

    public List<InventoryDeduction> transferAvailableFifo(Long sourceWarehouseId, Long destinationWarehouseId,
                                                           Long productId, BigDecimal quantity,
                                                           String sourceType, Long sourceId, String remark) {
        return inventoryMovementService.transferAvailableFifo(sourceWarehouseId, destinationWarehouseId,
                        productId, quantity, sourceType, sourceId, remark)
                .stream()
                .map(deduction -> new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice()))
                .toList();
    }
    public InventoryDeduction consumeSpecificBatch(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                                   String eventType, String sourceType, Long sourceId, String remark) {
        InventoryMovementService.InventoryDeduction deduction = inventoryMovementService.consumeSpecificBatch(
                warehouseId, productId, batchId, quantity, eventType, sourceType, sourceId, remark);
        return new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice());
    }

    public InventoryDeductionEvent consumeSpecificBatchEvent(Long warehouseId, Long productId, Long batchId,
                                                   BigDecimal quantity, String eventType, String sourceType,
                                                   Long sourceId, String remark) {
        InventoryMovementService.InventoryDeductionEvent result = inventoryMovementService.consumeSpecificBatchEvent(
                warehouseId, productId, batchId, quantity, eventType, sourceType, sourceId, remark);
        InventoryMovementService.InventoryDeduction deduction = result.deduction();
        return new InventoryDeductionEvent(
                new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice()), result.eventId());
    }

    public InventoryDeduction transferSpecificBatch(Long sourceWarehouseId, Long destinationWarehouseId,
                                                    Long productId, Long batchId, BigDecimal quantity,
                                                    String sourceType, Long sourceId, String remark) {
        InventoryMovementService.InventoryDeduction deduction = inventoryMovementService.transferSpecificBatch(
                sourceWarehouseId, destinationWarehouseId, productId, batchId, quantity, sourceType, sourceId, remark);
        return new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice());
    }

    public List<InventoryDeduction> isolateAvailableFifo(Long warehouseId, Long productId, BigDecimal quantity,
                                                          String sourceType, Long sourceId, String remark) {
        return inventoryMovementService.isolateAvailableFifo(warehouseId, productId, quantity, sourceType, sourceId, remark)
                .stream()
                .map(deduction -> new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice()))
                .toList();
    }

    public InventoryDeduction isolateSpecificBatch(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                                    String sourceType, Long sourceId, String remark) {
        InventoryMovementService.InventoryDeduction deduction = inventoryMovementService.isolateSpecificBatch(
                warehouseId, productId, batchId, quantity, sourceType, sourceId, remark);
        return new InventoryDeduction(deduction.batchId(), deduction.quantity(), deduction.unitPrice());
    }

    public Long receiveAvailable(Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                                 String eventType, String sourceType, Long sourceId, String remark) {
        return inventoryMovementService.receiveAvailable(warehouseId, productId, batchId, quantity,
                eventType, sourceType, sourceId, remark);
    }

    public Long adjustAvailable(Long balanceId, Long warehouseId, Long productId, Long batchId, BigDecimal quantityChange,
                                String eventType, String sourceType, Long sourceId, String remark) {
        return inventoryMovementService.adjustAvailable(balanceId, warehouseId, productId, batchId, quantityChange,
                eventType, sourceType, sourceId, remark);
    }

    public Long consumeLocked(Long balanceId, Long warehouseId, Long productId, Long batchId, BigDecimal quantity,
                              String eventType, String sourceType, Long sourceId, String remark) {
        return inventoryMovementService.consumeLocked(balanceId, warehouseId, productId, batchId, quantity,
                eventType, sourceType, sourceId, remark);
    }

    public void linkInventoryEventTraceCodes(Long eventId, List<InventoryEventCommand.TraceLink> traceLinks) {
        inventoryMovementService.linkEventTraceCodes(eventId, traceLinks);
    }

    public void releaseLockedToAvailable(Long balanceId, BigDecimal quantity) {
        inventoryMovementService.releaseLockedToAvailable(balanceId, quantity);
    }

    public record InventoryDeduction(Long batchId, BigDecimal quantity, BigDecimal unitPrice) {
    }

    public record InventoryDeductionEvent(InventoryDeduction deduction, Long eventId) {
    }

    public record InventoryReservation(Long balanceId, Long batchId, BigDecimal quantity, BigDecimal unitPrice) {
    }
}
