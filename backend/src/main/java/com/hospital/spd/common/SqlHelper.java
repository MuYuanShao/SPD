package com.hospital.spd.common;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * SQL 辅助工具类——提供通用的查询构建、参数处理和空值安全转换方法。
 * 从各 Controller 中提取的共享方法，遵循开发规范 3.3 节。
 */
public final class SqlHelper {

    private SqlHelper() {}

    /**
     * 构建 LIKE 条件参数值（自动包裹 %）
     */
    public static String appendLike(String value) {
        return "%" + (value == null ? "" : value.trim()) + "%";
    }

    /**
     * 追加 LIKE 条件到 StringBuilder（带 AND 前缀）
     */
    public static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    /**
     * 追加布尔等值条件到 StringBuilder（中文"是/否"转 1/0）
     */
    public static void appendBoolean(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        args.add("是".equals(value.trim()) ? 1 : 0);
    }

    /**
     * 追加供应商/厂家状态条件（中文"启用/停用"转 1/0）
     */
    public static void appendStatus(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND status = ?");
        args.add("启用".equals(value.trim()) ? 1 : 0);
    }

    /** 判断字符串是否为空白 */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /** 空字符串转 null（用于 SQL 参数） */
    public static String nullIfBlank(String str) {
        return (str == null || str.trim().isEmpty()) ? null : str.trim();
    }

    /** 字符串转 Decimal，无效返回默认值 */
    public static BigDecimal defaultDecimal(String str, BigDecimal defaultValue) {
        if (str == null || str.trim().isEmpty()) return defaultValue;
        try { return new BigDecimal(str.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    /** BigDecimal 空值兜底 */
    public static BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    /** 字符串转 LocalDate，无效返回 null */
    public static LocalDate parseDate(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try { return LocalDate.parse(str.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd")); } catch (Exception e) { return null; }
    }

    /** 字符串转 java.sql.Date */
    public static Date toSqlDate(String value) {
        return isBlank(value) ? null : Date.valueOf(LocalDate.parse(value.trim()));
    }

    /** 空值兜底 */
    public static String defaultText(String str, String defaultVal) {
        return (str == null || str.trim().isEmpty()) ? defaultVal : str.trim();
    }

    /** 空集合转 null */
    public static <T> List<T> emptyToNull(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }

    /** 空字符串转 null */
    public static String emptyToNull(String str) {
        return (str == null || str.trim().isEmpty()) ? null : str.trim();
    }

    /** 非负数校验（null 转 0） */
    public static BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    /** 正数校验，无效则抛异常 */
    public static BigDecimal positive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** 空值兜底（支持整型） */
    public static Integer nvl(Integer value, Integer defaultVal) {
        return value == null ? defaultVal : value;
    }

    /** 空字符串兜底 */
    public static String blankDefault(String value, String defaultVal) {
        return (value == null || value.trim().isEmpty()) ? defaultVal : value.trim();
    }

    /** 空集合校验 */
    public static <T> List<T> nonEmpty(List<T> list, String message) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return list;
    }

    /** 生成 IN 子句占位符串 */
    public static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    // ========== CSV/导入辅助方法 ==========

    /**
     * CSV 单元格默认值——从字符串数组中安全获取第 index 个元素
     */
    public static String defaultText(String[] cells, int index, String defaultValue) {
        if (index >= cells.length || cells[index] == null || cells[index].isBlank()) {
            return defaultValue;
        }
        return cells[index].trim();
    }

    // ========== 通用数据转换 ==========

    /** null/空字符串转 "-" */
    public static String fallback(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** null/空字符串/"-" 转空字符串 */
    public static String cleanFallback(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? "" : value;
    }

    /** 中文"是/否"转 boolean，空时使用 fallback */
    public static boolean parseYesNo(String value, boolean fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        return "是".equals(value.trim());
    }

    /** 优先选择非空值 */
    public static String prefer(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    /** 从多个值中取第一个非空值 */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "未分类";
    }

    /** Date 转字符串 */
    public static String dateString(Date date) {
        return date == null ? "-" : date.toLocalDate().toString();
    }

    /** 字符串转 JSON 数组文本 */
    public static String jsonText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String escaped = value.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        return "[\"" + escaped + "\"]";
    }

    /** 附件分类中文映射 */
    public static String attachmentCategory(String category) {
        if (category == null || category.isBlank()) {
            return "资质材料";
        }
        return switch (category) {
            case "registration" -> "注册证";
            case "production_license" -> "生产许可";
            case "business_license" -> "经营许可";
            default -> category;
        };
    }
}
