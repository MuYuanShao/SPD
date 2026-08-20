package com.hospital.spd.system;

public record SystemConfigRow(
        Long configId,
        String configType,
        String configTypeName,
        String scopeType,
        String scopeId,
        String configKey,
        String configName,
        String configValue,
        String effectiveMode,
        String effectiveTime,
        String expireTime,
        String riskLevel,
        String status,
        String updateTime
) {
}
