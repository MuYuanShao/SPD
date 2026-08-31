package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplenishmentSuggestionCalculatorTest {

    @Test
    void calculatesSelectedPeriodAndNonNegativeRecommendation() {
        ReplenishmentSuggestionCalculator.Result result = ReplenishmentSuggestionCalculator.calculate(
                Map.of(5, BigDecimal.valueOf(5), 7, BigDecimal.valueOf(9)),
                7,
                BigDecimal.valueOf(4),
                BigDecimal.ZERO,
                false);

        assertThat(result.selectedIssueQty()).isEqualByComparingTo("9");
        assertThat(result.formulaReplenishQty()).isEqualByComparingTo("5");
        assertThat(result.recommendedQty()).isEqualByComparingTo("5");
    }

    @Test
    void appliesMinimumAndWholeUnitRoundingOnlyWhenReplenishmentIsPositive() {
        ReplenishmentSuggestionCalculator.Result positive = ReplenishmentSuggestionCalculator.calculate(
                Map.of(30, new BigDecimal("25.2")),
                30,
                BigDecimal.valueOf(24),
                BigDecimal.TEN,
                true);
        ReplenishmentSuggestionCalculator.Result zero = ReplenishmentSuggestionCalculator.calculate(
                Map.of(30, BigDecimal.valueOf(20)),
                30,
                BigDecimal.valueOf(24),
                BigDecimal.TEN,
                true);

        assertThat(positive.formulaReplenishQty()).isEqualByComparingTo("1.2");
        assertThat(positive.recommendedQty()).isEqualByComparingTo("10");
        assertThat(zero.recommendedQty()).isEqualByComparingTo("0");
    }
}
