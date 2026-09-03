package com.hospital.spd.supplychain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** Typed requests for department-requisition smart analysis and confirmed generation. */
public final class DepartmentRequisitionSmartRequests {
    private DepartmentRequisitionSmartRequests() {}

    public record AnalysisRequest(String deptCode, String deptName, Long destinationWarehouseId,
                                  String destinationWarehouseName,
                                  Long sourceWarehouseId, @Positive Integer selectedPeriodDays) {}

    public record GenerateRequest(@NotNull @Positive Long analysisId,
                                  @NotEmpty List<@Valid GenerateItem> items) {}

    public record GenerateItem(@NotNull @Positive Long analysisItemId,
                               @NotNull BigDecimal quantity,
                               boolean selected) {}

    public record HighValuePickingRequest(@NotNull @Positive Long itemId,
                                          @NotEmpty List<@NotNull @Positive Long> traceCodeIds) {}
}
