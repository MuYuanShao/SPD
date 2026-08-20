package com.hospital.spd.masterdata.service;

import java.util.List;

final class ProductApprovalWorkflowSteps {

    private ProductApprovalWorkflowSteps() {
    }

    static List<ConfiguredApprovalStep> legacyApprovalSteps() {
        return List.of(
                new ConfiguredApprovalStep(1, "初审"),
                new ConfiguredApprovalStep(2, "复审")
        );
    }

    static boolean isPendingStatus(String status) {
        return "pending_initial".equals(status)
                || "pending_final".equals(status)
                || (status != null && status.startsWith("pending_step_"));
    }

    static int currentStepOrder(String status, List<ConfiguredApprovalStep> approvalSteps) {
        if ("pending_final".equals(status)) {
            return Math.min(2, maxStepOrder(approvalSteps));
        }
        if (status != null && status.startsWith("pending_step_")) {
            try {
                return Integer.parseInt(status.substring("pending_step_".length()));
            } catch (NumberFormatException ignored) {
                return approvalSteps.get(0).stepOrder();
            }
        }
        return approvalSteps.get(0).stepOrder();
    }

    static boolean isFinalApprovalStep(int currentStepOrder, List<ConfiguredApprovalStep> approvalSteps) {
        return currentStepOrder >= maxStepOrder(approvalSteps);
    }

    static int nextStepOrder(int currentStepOrder, List<ConfiguredApprovalStep> approvalSteps) {
        return approvalSteps.stream()
                .map(ConfiguredApprovalStep::stepOrder)
                .filter(order -> order > currentStepOrder)
                .findFirst()
                .orElse(maxStepOrder(approvalSteps));
    }

    static String nextPendingStatus(String currentStatus, int nextStepOrder, List<ConfiguredApprovalStep> approvalSteps) {
        if ("pending_initial".equals(currentStatus) && legacyApprovalSteps().equals(approvalSteps)) {
            return "pending_final";
        }
        return pendingStatus(nextStepOrder);
    }

    static String pendingStatus(int stepOrder) {
        return "pending_step_" + stepOrder;
    }

    static ConfiguredApprovalStep stepForStatus(String status, List<ConfiguredApprovalStep> approvalSteps) {
        int currentOrder = currentStepOrder(status, approvalSteps);
        return approvalSteps.stream()
                .filter(step -> step.stepOrder() == currentOrder)
                .findFirst()
                .orElse(approvalSteps.get(0));
    }

    private static int maxStepOrder(List<ConfiguredApprovalStep> approvalSteps) {
        return approvalSteps.stream().mapToInt(ConfiguredApprovalStep::stepOrder).max().orElse(1);
    }
}
