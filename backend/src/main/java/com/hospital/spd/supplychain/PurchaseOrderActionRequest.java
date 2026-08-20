package com.hospital.spd.supplychain;

public record PurchaseOrderActionRequest(
        String action,
        String opinion
) {
}
