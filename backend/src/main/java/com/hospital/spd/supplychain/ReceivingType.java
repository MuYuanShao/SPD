package com.hospital.spd.supplychain;

import java.util.Locale;

public enum ReceivingType {
    NORMAL("normal"),
    AGENT("agent");

    private final String code;

    ReceivingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isAgent() {
        return this == AGENT;
    }

    public static ReceivingType resolve(String value, Boolean legacyAgent) {
        if (value == null || value.isBlank()) {
            return Boolean.TRUE.equals(legacyAgent) ? AGENT : NORMAL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        ReceivingType type = switch (normalized) {
            case "normal" -> NORMAL;
            case "agent" -> AGENT;
            default -> throw new IllegalArgumentException("收货类型仅支持正常收货或代理商直送");
        };
        if (legacyAgent != null && legacyAgent != type.isAgent()) {
            throw new IllegalArgumentException("收货类型与代理商标记不一致");
        }
        return type;
    }
}
