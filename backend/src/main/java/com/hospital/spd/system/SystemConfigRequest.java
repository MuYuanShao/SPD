package com.hospital.spd.system;

public record SystemConfigRequest(
        String configType,
        String scopeType,
        String scopeId,
        String configKey,
        String configValue,
        String effectiveMode,
        String expireTime,
        String riskLevel,
        Integer status
) {
}
