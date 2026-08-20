package com.hospital.spd.masterdata.service;

record ConfiguredApprovalStep(Integer stepOrder, String stepName) {
    ConfiguredApprovalStep {
        if (stepName == null || stepName.isBlank()) {
            stepName = "审批";
        }
    }
}
