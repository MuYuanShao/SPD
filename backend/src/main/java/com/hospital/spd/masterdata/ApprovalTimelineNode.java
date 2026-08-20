package com.hospital.spd.masterdata;

public record ApprovalTimelineNode(
        String title,
        String operator,
        String time,
        String status
) {
}
