package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class QuantityRulesTest {
    @Test void physicalCountsMustBeWholeWithoutRounding() {
        for (String value : new String[]{"1.5", "0.00001", "-0.5"}) {
            assertThatThrownBy(() -> QuantityRules.whole(new BigDecimal(value), "数量"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("必须为整数");
        }
        assertThat(QuantityRules.positive(new BigDecimal("12.0000"), "数量")).isEqualTo(new BigDecimal("12"));
        assertThat(QuantityRules.nonNegative(BigDecimal.ZERO, "不合格数量")).isZero();
        assertThatThrownBy(() -> QuantityRules.positive(BigDecimal.ZERO, "数量")).hasMessageContaining("正整数");
    }

    @Test void requestValidationCascadesCountsButDoesNotRestrictMonetaryDecimals() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var normal = new com.hospital.spd.supplychain.PurchaseOrderItemRequest("P1", BigDecimal.ONE, "支", new BigDecimal("1.2345"));
            assertThat(validator.validate(normal)).isEmpty();
            var fractional = new com.hospital.spd.supplychain.ReceivingItemRequest("P1", "B1", null,
                    "2026-10-01", "2028-10-01", new BigDecimal("1.5"), new BigDecimal("1.5"), BigDecimal.ZERO);
            var receipt = new com.hospital.spd.supplychain.ReceivingOrderRequest(null, "S1", "W1", "normal", false, "test", java.util.List.of(fractional));
            assertThat(validator.validate(receipt)).hasSize(2).allSatisfy(error -> assertThat(error.getMessage()).contains("整数"));
        }
    }
}
