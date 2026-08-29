package com.hospital.spd.supplychain.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Supported settlement points for automatic and manual catch-up generation. */
public enum SettlementMode {
    PURCHASE_IN("purchase_in"),
    DEPARTMENT_CONSUMPTION("department_consumption"),
    ACTUAL_SALE("actual_sale");

    private final String code;

    SettlementMode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static SettlementMode from(String value) {
        for (SettlementMode mode : values()) {
            if (mode.code.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) return mode;
        }
        throw new IllegalArgumentException("unsupported settlement mode: " + value);
    }
}
