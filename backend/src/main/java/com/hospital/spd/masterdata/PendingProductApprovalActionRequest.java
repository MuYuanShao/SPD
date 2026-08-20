package com.hospital.spd.masterdata;

public record PendingProductApprovalActionRequest(
        String action,
        String opinion
) {
}
