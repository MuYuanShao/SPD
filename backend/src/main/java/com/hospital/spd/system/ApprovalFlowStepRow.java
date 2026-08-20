package com.hospital.spd.system;

public record ApprovalFlowStepRow(
        Long stepId,
        Integer stepOrder,
        String stepName,
        String approverType,
        Long roleId,
        String roleName,
        Long userId,
        String realName,
        Long deptId,
        String deptName,
        Integer minApprovals,
        Boolean allowSelfApprove,
        Integer dataScope,
        Integer status
) {
}
