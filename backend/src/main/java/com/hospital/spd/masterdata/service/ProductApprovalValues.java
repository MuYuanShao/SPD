package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.PendingProductChangeItem;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static com.hospital.spd.common.SqlHelper.isBlank;

final class ProductApprovalValues {

    private ProductApprovalValues() {
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "未分类";
    }

    static BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    static String matchText(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static Date parseDate(String value) {
        return isBlank(value) ? null : Date.valueOf(LocalDate.parse(value.trim()));
    }

    static void addChange(List<PendingProductChangeItem> changes, String fieldName, Object before, Object after) {
        String beforeValue = displayValue(before);
        String afterValue = displayValue(after);
        if (!beforeValue.equals(afterValue)) {
            changes.add(new PendingProductChangeItem(fieldName, beforeValue, afterValue));
        }
    }

    static String boolLabel(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Number number) {
            return number.intValue() == 1 ? "是" : "否";
        }
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    static String displayValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toString();
        }
        if (value instanceof java.time.LocalDate date) {
            return date.toString();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "-" : text;
    }
}
