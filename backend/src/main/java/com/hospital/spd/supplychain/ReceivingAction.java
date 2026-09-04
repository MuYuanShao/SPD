package com.hospital.spd.supplychain;

import java.util.Locale;

public enum ReceivingAction {
    APPROVE("approve"),
    REJECT("reject");

    private final String code;

    ReceivingAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReceivingAction from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("收货验收仅支持审核入库或拒收操作");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "approve" -> APPROVE;
            case "reject" -> REJECT;
            default -> throw new IllegalArgumentException("收货验收仅支持审核入库或拒收操作");
        };
    }
}
