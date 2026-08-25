package com.hospital.spd.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists and serves the operation cockpit read model. */
@Service
public class OperationCockpitSnapshotService {

    private static final List<String> CATEGORY_ORDER = List.of("高值耗材", "低值收费耗材", "低值不收费耗材");
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OperationCockpitService sourceService;

    public OperationCockpitSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                           OperationCockpitService sourceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sourceService = sourceService;
    }

    /** API query path: reads only operation_cockpit_snapshot. */
    public Map<String, Object> cockpit(String requestedMonth) {
        YearMonth month = parseMonth(requestedMonth);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT current_amount AS currentAmount, previous_amount AS previousAmount,
                       month_on_month AS monthOnMonth, department_count AS departmentCount,
                       warning_count AS warningCount,
                       CAST(categories_json AS CHAR) AS categoriesJson,
                       CAST(trend_json AS CHAR) AS trendJson,
                       CAST(departments_json AS CHAR) AS departmentsJson,
                       CAST(focused_products_json AS CHAR) AS focusedProductsJson,
                       CAST(alerts_json AS CHAR) AS alertsJson,
                       statistics_date AS statisticsDate, generated_at AS generatedAt
                  FROM operation_cockpit_snapshot
                 WHERE statistics_month = ?
                """, Date.valueOf(month.atDay(1)));
        if (rows.isEmpty()) return emptySnapshot(month);

        Map<String, Object> row = rows.get(0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currentAmount", decimal(row.get("currentAmount")));
        summary.put("previousAmount", decimal(row.get("previousAmount")));
        summary.put("monthOnMonth", decimal(row.get("monthOnMonth")));
        summary.put("departmentCount", longValue(row.get("departmentCount")));
        summary.put("warningCount", longValue(row.get("warningCount")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month.toString());
        result.put("summary", summary);
        result.put("categories", readList(row.get("categoriesJson")));
        result.put("trend", readList(row.get("trendJson")));
        result.put("departments", readList(row.get("departmentsJson")));
        result.put("focusedProducts", readList(row.get("focusedProductsJson")));
        result.put("alerts", readList(row.get("alertsJson")));
        result.put("statisticsDate", String.valueOf(row.get("statisticsDate")));
        result.put("generatedAt", String.valueOf(row.get("generatedAt")));
        return result;
    }

    public boolean snapshotExists(YearMonth month) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_cockpit_snapshot WHERE statistics_month = ?",
                Long.class, Date.valueOf(month.atDay(1)));
        return count != null && count > 0;
    }

    /** Batch path: aggregates source facts and atomically upserts one monthly snapshot. */
    @Transactional
    public void refreshSnapshot(YearMonth month) {
        Map<String, Object> data = sourceService.cockpit(month.toString());
        Map<String, Object> summary = map(data.get("summary"));
        jdbcTemplate.update("""
                INSERT INTO operation_cockpit_snapshot (
                  statistics_month, statistics_date, current_amount, previous_amount, month_on_month,
                  department_count, warning_count, categories_json, trend_json, departments_json,
                  focused_products_json, alerts_json, generated_at
                ) VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON),
                          CAST(? AS JSON), CAST(? AS JSON), CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  statistics_date = VALUES(statistics_date), current_amount = VALUES(current_amount),
                  previous_amount = VALUES(previous_amount), month_on_month = VALUES(month_on_month),
                  department_count = VALUES(department_count), warning_count = VALUES(warning_count),
                  categories_json = VALUES(categories_json), trend_json = VALUES(trend_json),
                  departments_json = VALUES(departments_json), focused_products_json = VALUES(focused_products_json),
                  alerts_json = VALUES(alerts_json), generated_at = CURRENT_TIMESTAMP
                """,
                Date.valueOf(month.atDay(1)), decimal(summary.get("currentAmount")),
                decimal(summary.get("previousAmount")), decimal(summary.get("monthOnMonth")),
                longValue(summary.get("departmentCount")), longValue(summary.get("warningCount")),
                writeJson(data.get("categories")), writeJson(data.get("trend")),
                writeJson(data.get("departments")), writeJson(data.get("focusedProducts")),
                writeJson(data.get("alerts")));
    }

    private Map<String, Object> emptySnapshot(YearMonth month) {
        List<Map<String, Object>> categories = CATEGORY_ORDER.stream()
                .map(category -> Map.<String, Object>of("category", category, "currentAmount", BigDecimal.ZERO,
                        "previousAmount", BigDecimal.ZERO, "monthOnMonth", BigDecimal.ZERO))
                .toList();
        List<Map<String, Object>> trend = new ArrayList<>();
        YearMonth firstMonth = month.minusMonths(11);
        for (int index = 0; index < 12; index++) {
            YearMonth item = firstMonth.plusMonths(index);
            trend.add(Map.of("month", item.toString(), "label", item.getMonthValue() + "月", "amount", BigDecimal.ZERO));
        }
        return Map.of(
                "month", month.toString(),
                "summary", Map.of("currentAmount", BigDecimal.ZERO, "previousAmount", BigDecimal.ZERO,
                        "monthOnMonth", BigDecimal.ZERO, "departmentCount", 0L, "warningCount", 0L),
                "categories", categories, "trend", trend, "departments", List.of(),
                "focusedProducts", List.of(), "alerts", List.of()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运营驾驶舱快照序列化失败", exception);
        }
    }

    private List<Map<String, Object>> readList(Object value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(String.valueOf(value), LIST_OF_MAPS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运营驾驶舱快照数据格式错误", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("统计月份格式应为 YYYY-MM");
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
