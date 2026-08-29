package com.hospital.spd.masterdata.service;

record ConfiguredApprovalStep(Integer stepOrder, String stepName, Integer minApprovals) {
    ConfiguredApprovalStep(Integer stepOrder, String stepName) {
        this(stepOrder, stepName, 1);
    }

    ConfiguredApprovalStep {
        if (stepName == null || stepName.isBlank()) {
            stepName = "审批";
        }
        if (minApprovals == null || minApprovals < 1) {
            minApprovals = 1;
        }
    }
}
