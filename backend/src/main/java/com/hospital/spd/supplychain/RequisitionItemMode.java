package com.hospital.spd.supplychain;

import java.util.Locale;

/** Defines the server-authoritative fulfillment mode of one department requisition item. */
public enum RequisitionItemMode {
    LOOSE("loose"),
    QUOTA_PACKAGE("quota_package"),
    HIGH_VALUE("high_value");

    private final String code;

    RequisitionItemMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RequisitionItemMode parse(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("unique_code".equals(value)) value = HIGH_VALUE.code;
        for (RequisitionItemMode mode : values()) {
            if (mode.code.equals(value)) return mode;
        }
        throw new IllegalArgumentException("不支持的申领模式：" + raw);
    }
}
