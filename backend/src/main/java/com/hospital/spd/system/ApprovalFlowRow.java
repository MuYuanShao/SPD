package com.hospital.spd.system;

import java.util.List;

public record ApprovalFlowRow(
        Long flowId,
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
        String deptName,
        Long createBy,
        String createByName,
        String updateTime,
        List<ApprovalFlowStepRow> steps
) {
}
