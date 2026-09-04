package com.hospital.spd.supplychain;

import java.util.Locale;

public enum ReceivingSourceType {
    PURCHASE_ORDER("purchase_order"),
    TEMPORARY("temporary");

    private final String code;

    ReceivingSourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReceivingSourceType resolve(String value, String purchaseOrderNo) {
        if (value == null || value.isBlank()) {
            return purchaseOrderNo == null || purchaseOrderNo.isBlank() ? TEMPORARY : PURCHASE_ORDER;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "purchase_order" -> PURCHASE_ORDER;
            case "temporary" -> TEMPORARY;
            default -> throw new IllegalArgumentException("收货来源仅支持采购订单收货或临时收货");
        };
    }
}
