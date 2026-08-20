package com.hospital.spd.masterdata.service;

import java.util.List;

final class ProductApprovalStatus {

    private ProductApprovalStatus() {
    }

    static String toStatusLabel(String status) {
        return switch (status) {
            case "pending_initial" -> "待审批";
            case "pending_final" -> "流转中";
            case "rejected" -> "已驳回";
            case "returned" -> "退回修改";
            case "approved" -> "已通过";
            default -> status != null && status.startsWith("pending_step_") ? "流转中" : status;
        };
    }

    static String toStatusLabel(String status, List<ConfiguredApprovalStep> approvalSteps) {
        if (status != null && status.startsWith("pending_step_")) {
            return ProductApprovalWorkflowSteps.stepForStatus(status, approvalSteps).stepName() + "待审批";
        }
        return toStatusLabel(status);
    }

    static String toStatusTone(String status) {
        return switch (status) {
            case "pending_initial" -> "orange";
            case "pending_final", "approved" -> "blue";
            case "rejected", "returned" -> "red";
            default -> status != null && status.startsWith("pending_step_") ? "blue" : "orange";
        };
    }
}
