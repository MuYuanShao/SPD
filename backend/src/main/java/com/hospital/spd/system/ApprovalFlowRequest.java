package com.hospital.spd.system;

import java.util.List;

public record ApprovalFlowRequest(
        String featureCode,
        String featureName,
        String nodeCode,
        String nodeName,
        String scopeType,
        String scopeId,
        Integer dataScope,
        Integer status,
        String remark,
        Long deptId,
        List<ApprovalFlowStepRequest> steps
) {
}
