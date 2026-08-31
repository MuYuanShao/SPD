package com.hospital.spd.supplychain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Calculates replenishment quantities from period demand and current stock without persistence concerns.
 */
public final class ReplenishmentSuggestionCalculator {

    private ReplenishmentSuggestionCalculator() {
    }

    public static Result calculate(Map<Integer, BigDecimal> issueQtyByPeriod,
                                   int selectedPeriodDays,
                                   BigDecimal currentQty,
                                   BigDecimal minimumRecommendedQty,
                                   boolean roundToWholeUnits) {
        BigDecimal selectedIssueQty = issueQtyByPeriod.get(selectedPeriodDays);
        if (selectedIssueQty == null) {
            throw new IllegalArgumentException("selected replenishment period is not supported");
        }
        BigDecimal stock = defaultZero(currentQty);
        BigDecimal formulaQty = defaultZero(selectedIssueQty).subtract(stock).max(BigDecimal.ZERO);
        BigDecimal recommendedQty = BigDecimal.ZERO;
        if (formulaQty.signum() > 0) {
            recommendedQty = formulaQty.max(defaultZero(minimumRecommendedQty));
            if (roundToWholeUnits) {
                recommendedQty = recommendedQty.setScale(0, RoundingMode.CEILING);
            }
        }
        return new Result(defaultZero(selectedIssueQty), formulaQty, recommendedQty);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record Result(BigDecimal selectedIssueQty,
                         BigDecimal formulaReplenishQty,
                         BigDecimal recommendedQty) {
    }
}
