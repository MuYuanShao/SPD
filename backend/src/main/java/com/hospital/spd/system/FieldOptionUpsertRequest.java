package com.hospital.spd.system;

/**
 * Creates or updates one dropdown option in the field management dictionary.
 */
public record FieldOptionUpsertRequest(
        String fieldKey,
        String fieldLabel,
        String optionValue,
        String optionLabel,
        Integer sortOrder,
        Integer status,
        String remark
) {
}
