package com.hospital.spd.masterdata;

public record PendingProductChangeItem(
        String fieldName,
        String beforeValue,
        String afterValue
) {
}
