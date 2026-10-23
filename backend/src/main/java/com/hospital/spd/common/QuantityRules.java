package com.hospital.spd.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Enforces whole physical quantities without silently rounding business or inventory data. */
public final class QuantityRules {
    private QuantityRules() {}

    public static BigDecimal whole(BigDecimal value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        if (value.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException(label + "必须为整数，不支持小数");
        BigDecimal result = value.setScale(0, RoundingMode.UNNECESSARY);
        if (result.precision() > 14) throw new IllegalArgumentException(label + "不能超过14位整数");
        return result;
    }

    public static BigDecimal positive(BigDecimal value, String label) {
        BigDecimal result = whole(value, label);
        if (result.signum() <= 0) throw new IllegalArgumentException(label + "必须为正整数");
        return result;
    }

    public static BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal result = whole(value, label);
        if (result.signum() < 0) throw new IllegalArgumentException(label + "必须为非负整数");
        return result;
    }

    public static void optionalPositive(BigDecimal value, String label) {
        if (value != null) positive(value, label);
    }
}
