package com.hospital.spd.system;

public record ApprovalFlowStepRequest(
        Long stepId,
        Integer stepOrder,
        String stepName,
        String approverType,
        Long roleId,
        Long userId,
        Long deptId,
        Integer minApprovals,
        Boolean allowSelfApprove,
        Integer dataScope,
        Integer status
) {
}
