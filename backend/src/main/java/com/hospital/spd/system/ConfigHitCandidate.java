package com.hospital.spd.system;

public record ConfigHitCandidate(
        Long configId,
        String configType,
        String configTypeName,
        String sourceLevel,
        Integer priority,
        String scopeType,
        String scopeId,
        String configKey,
        String configName,
        String configValue,
        String effectiveTime,
        String expireTime,
        String riskLevel,
        String approvalStatus,
        Boolean hit,
        Boolean overridden,
        Boolean conflict,
        String matchReason
) {
    public ConfigHitCandidate(Long configId, String configType, String configTypeName, String sourceLevel,
                              Integer priority, String scopeType, String scopeId, String configKey,
                              String configName, String configValue, String effectiveTime, String expireTime,
                              String riskLevel, String approvalStatus, Boolean hit, Boolean overridden) {
        this(configId, configType, configTypeName, sourceLevel, priority, scopeType, scopeId, configKey,
                configName, configValue, effectiveTime, expireTime, riskLevel, approvalStatus, hit, overridden,
                false, "");
    }
}
