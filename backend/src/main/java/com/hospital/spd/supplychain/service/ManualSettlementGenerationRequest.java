package com.hospital.spd.supplychain.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Validated request for idempotent settlement catch-up generation. */
public record ManualSettlementGenerationRequest(
        @NotNull SettlementMode settlementMode,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Positive Long supplierId
) {
}
