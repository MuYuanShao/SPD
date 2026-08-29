package com.hospital.spd.supplychain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hospital.spd.supplychain.OperationalCommandRequests.*;
import static org.assertj.core.api.Assertions.assertThat;

class OperationalCommandRequestsTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositivePickingQuantityAndEmptyLabels() {
        assertThat(validator.validate(new LoosePickingRequest("REQ-1", 1L, "中心库", BigDecimal.ZERO)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("quantity");
        assertThat(validator.validate(new PackagePickingRequest("REQ-1", 1L, "中心库", List.of())))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("labelNos");
    }

    @Test
    void rejectsUnknownRequisitionAction() {
        assertThat(validator.validate(new RequisitionActionRequest("skip")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("action");
    }

    @Test
    void billingCallbackRequiresIdempotencyAndTraceIdentifiers() {
        HighValueBillingCallbackRequest request = new HighValueBillingCallbackRequest(
                null, null, null, null, null, null, "P-1", BigDecimal.ONE,
                null, "手术室", "PAT-1", null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sourceEventPresent", "traceCodePresent");
    }
}
