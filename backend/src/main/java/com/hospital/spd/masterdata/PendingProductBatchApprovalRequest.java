package com.hospital.spd.masterdata;

import java.util.List;

public record PendingProductBatchApprovalRequest(
        List<String> applicationNos,
        String action,
        String opinion
) {
}
