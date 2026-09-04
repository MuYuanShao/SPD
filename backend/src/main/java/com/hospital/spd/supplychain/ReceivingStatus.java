package com.hospital.spd.supplychain;

public enum ReceivingStatus {
    DRAFT("draft"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String code;

    ReceivingStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
