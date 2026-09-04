package com.hospital.spd.common.service;

import java.math.BigDecimal;
import java.util.List;

/** Carries one immutable inventory-event fact and any stable trace identities it owns. */
public record InventoryEventCommand(
        String eventType,
        String sourceBizType,
        Long sourceBizId,
        Long warehouseId,
        Long productId,
        Long batchId,
        BigDecimal quantityChange,
        BigDecimal quantityAfter,
        String remark,
        List<TraceLink> traceLinks
) {
    public InventoryEventCommand {
        traceLinks = traceLinks == null ? List.of() : List.copyOf(traceLinks);
    }

    public static InventoryEventCommand quantity(String eventType, String sourceBizType, Long sourceBizId,
                                                  Long warehouseId, Long productId, Long batchId,
                                                  BigDecimal quantityChange, BigDecimal quantityAfter,
                                                  String remark) {
        return new InventoryEventCommand(eventType, sourceBizType, sourceBizId, warehouseId, productId,
                batchId, quantityChange, quantityAfter, remark, List.of());
    }

    public record TraceLink(Long traceCodeId, String traceType, BigDecimal linkedQuantity) {
    }
}
