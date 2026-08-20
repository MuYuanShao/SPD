package com.hospital.spd.system;

import java.util.List;

public record ConfigHitExplanation(
        String businessPage,
        String configType,
        String finalValue,
        String sourceLevel,
        Long sourceConfigId,
        String scopeType,
        String scopeId,
        String effectiveTime,
        String expireTime,
        Boolean overridesParent,
        String parentValue,
        String lastChangedBy,
        String approvalStatus,
        List<ConfigHitCandidate> candidates,
        List<String> priorityRules
) {
}
