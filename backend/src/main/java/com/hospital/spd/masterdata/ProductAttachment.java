package com.hospital.spd.masterdata;

public record ProductAttachment(
        String fileName,
        String category,
        String status,
        String fileUrl,
        String validDate
) {
}
