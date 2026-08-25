package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the hospital-consumable cockpit from consumption facts and hospital catalog attributes.
 */
@Service
public class OperationCockpitService {

    private static final List<String> CATEGORY_ORDER = List.of("高值耗材", "低值收费耗材", "低值不收费耗材");

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;

    public OperationCockpitService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new DataScopeService(OperatorContext::system));
    }

    @Autowired
    public OperationCockpitService(JdbcTemplate jdbcTemplate, DataScopeService dataScopeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeService = dataScopeService;
    }

    public Map<String, Object> cockpit(String requestedMonth) {
        YearMonth month = parseMonth(requestedMonth);
        LocalDate start = month.atDay(1);
        LocalDate next = month.plusMonths(1).atDay(1);
        LocalDate previous = month.minusMonths(1).atDay(1);

        BigDecimal currentAmount = consumptionAmount(start, next);
        BigDecimal previousAmount = consumptionAmount(previous, start);
        long departmentCount = consumptionDepartmentCount(start, next);
        long warningCount = keyProductWarningCount();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currentAmount", currentAmount);
        summary.put("previousAmount", previousAmount);
        summary.put("monthOnMonth", changeRate(currentAmount, previousAmount));
        summary.put("departmentCount", departmentCount);
        summary.put("warningCount", warningCount);

        return Map.of(
                "month", month.toString(),
                "summary", summary,
                "categories", loadCategoryComparison(previous, next, start),
                "trend", loadMonthlyTrend(month),
                "departments", loadDepartmentRanking(previous, next, start),
                "focusedProducts", loadFocusedProductRanking(previous, next, start),
                "alerts", loadKeyProductAlerts()
        );
    }

    private BigDecimal consumptionAmount(LocalDate start, LocalDate end) {
        ScopedQuery query = scoped("""
                 WHERE dc.status = 'confirmed' AND dc.consume_time >= ? AND dc.consume_time < ?
                """, List.of(Date.valueOf(start), Date.valueOf(end)), "dc.dept_id", "dc.consume_by");
        BigDecimal amount = queryForObject("""
                SELECT COALESCE(SUM(dci.amount), 0)
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                """ + query.where(), BigDecimal.class, query.args());
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private long consumptionDepartmentCount(LocalDate start, LocalDate end) {
        ScopedQuery query = scoped("""
                 WHERE dc.status = 'confirmed' AND dc.consume_time >= ? AND dc.consume_time < ?
                """, List.of(Date.valueOf(start), Date.valueOf(end)), "dc.dept_id", "dc.consume_by");
        Long count = queryForObject("""
                SELECT COUNT(DISTINCT dc.dept_id)
                  FROM department_consumption dc
                """ + query.where(), Long.class, query.args());
        return count == null ? 0 : count;
    }

    private long keyProductWarningCount() {
        ScopedQuery query = scoped("""
                 WHERE p.deleted = 0 AND p.status = 1 AND p.is_key_monitored = 1
                """, List.of(), "w.dept_id", null);
        Long count = queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT p.product_id
                    FROM product p
                    LEFT JOIN inventory_balance ib ON ib.product_id = p.product_id
                    LEFT JOIN warehouse w ON w.warehouse_id = ib.warehouse_id
                """ + query.where() + """
                   GROUP BY p.product_id, p.min_purchase_qty
                  HAVING COALESCE(SUM(ib.available_qty), 0) < p.min_purchase_qty
                ) warning_products
                """, Long.class, query.args());
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> loadCategoryComparison(LocalDate previous, LocalDate next, LocalDate current) {
        ScopedQuery query = scoped("""
                 WHERE dc.status = 'confirmed' AND dc.consume_time >= ? AND dc.consume_time < ?
                   AND (p.is_high_value = 1 OR p.is_quota_managed = 1)
                   AND p.deleted = 0
                """, List.of(Date.valueOf(previous), Date.valueOf(next)), "dc.dept_id", "dc.consume_by");
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(current));
        args.add(Date.valueOf(current));
        args.addAll(query.args());
        List<Map<String, Object>> rows = queryForList("""
                SELECT CASE
                         WHEN p.is_high_value = 1 THEN '高值耗材'
                         WHEN p.is_quota_managed = 1 AND p.is_chargeable = 1 THEN '低值收费耗材'
                         WHEN p.is_quota_managed = 1 AND p.is_chargeable = 0 THEN '低值不收费耗材'
                       END AS category,
                       COALESCE(SUM(CASE WHEN dc.consume_time >= ? THEN dci.amount ELSE 0 END), 0) AS currentAmount,
                       COALESCE(SUM(CASE WHEN dc.consume_time < ? THEN dci.amount ELSE 0 END), 0) AS previousAmount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN product p ON p.product_id = dci.product_id
                """ + query.where() + " GROUP BY category", args);

        Map<String, Map<String, Object>> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byCategory.put(String.valueOf(row.get("category")), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            Map<String, Object> source = byCategory.getOrDefault(category, Map.of());
            BigDecimal currentAmount = decimal(source.get("currentAmount"));
            BigDecimal previousAmount = decimal(source.get("previousAmount"));
            result.add(Map.of(
                    "category", category,
                    "currentAmount", currentAmount,
                    "previousAmount", previousAmount,
                    "monthOnMonth", changeRate(currentAmount, previousAmount)
            ));
        }
        return result;
    }

    private List<Map<String, Object>> loadMonthlyTrend(YearMonth selectedMonth) {
        YearMonth firstMonth = selectedMonth.minusMonths(11);
        ScopedQuery query = scoped("""
                 WHERE dc.status = 'confirmed' AND dc.consume_time >= ? AND dc.consume_time < ? AND p.deleted = 0
                """, List.of(Date.valueOf(firstMonth.atDay(1)), Date.valueOf(selectedMonth.plusMonths(1).atDay(1))),
                "dc.dept_id", "dc.consume_by");
        List<Map<String, Object>> rows = queryForList("""
                SELECT DATE_FORMAT(dc.consume_time, '%Y-%m') AS month,
                       COALESCE(SUM(dci.amount), 0) AS amount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN product p ON p.product_id = dci.product_id
                """ + query.where() + " GROUP BY DATE_FORMAT(dc.consume_time, '%Y-%m') ORDER BY month", query.args());
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) values.put(String.valueOf(row.get("month")), decimal(row.get("amount")));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            YearMonth item = firstMonth.plusMonths(index);
            result.add(Map.of("month", item.toString(), "label", item.getMonthValue() + "月",
                    "amount", values.getOrDefault(item.toString(), BigDecimal.ZERO)));
        }
        return result;
    }

    private List<Map<String, Object>> loadDepartmentRanking(LocalDate previous, LocalDate next, LocalDate current) {
        return loadRanking(previous, next, current, false);
    }

    private List<Map<String, Object>> loadFocusedProductRanking(LocalDate previous, LocalDate next, LocalDate current) {
        return loadRanking(previous, next, current, true);
    }

    private List<Map<String, Object>> loadRanking(LocalDate previous, LocalDate next, LocalDate current, boolean productRanking) {
        String extra = productRanking ? " AND p.is_key_monitored = 1" : "";
        ScopedQuery query = scoped("""
                 WHERE dc.status = 'confirmed' AND dc.consume_time >= ? AND dc.consume_time < ? AND p.deleted = 0
                """ + extra, List.of(Date.valueOf(previous), Date.valueOf(next)), "dc.dept_id", "dc.consume_by");
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(current));
        args.add(Date.valueOf(current));
        args.addAll(query.args());
        String selectName = productRanking
                ? "p.product_code AS code, p.product_name AS name"
                : "CAST(sd.dept_id AS CHAR) AS code, sd.dept_name AS name";
        String joinDepartment = productRanking ? "" : " JOIN sys_dept sd ON sd.dept_id = dc.dept_id\n";
        String groupBy = productRanking ? "p.product_id, p.product_code, p.product_name" : "sd.dept_id, sd.dept_name";
        return queryForList("SELECT " + selectName + ",\n" + """
                       COALESCE(SUM(CASE WHEN dc.consume_time >= ? THEN dci.amount ELSE 0 END), 0) AS currentAmount,
                       COALESCE(SUM(CASE WHEN dc.consume_time < ? THEN dci.amount ELSE 0 END), 0) AS previousAmount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN product p ON p.product_id = dci.product_id
                """ + joinDepartment + query.where()
                + " GROUP BY " + groupBy + " ORDER BY currentAmount DESC LIMIT 8", args);
    }

    private List<Map<String, Object>> loadKeyProductAlerts() {
        ScopedQuery query = scoped("""
                 WHERE p.deleted = 0 AND p.status = 1 AND p.is_key_monitored = 1
                """, List.of(), "w.dept_id", null);
        return queryForList("""
                SELECT p.product_code AS productCode, p.product_name AS productName,
                       COALESCE(SUM(ib.available_qty), 0) AS availableQty,
                       p.min_purchase_qty AS threshold,
                       '库存不足' AS alertType
                  FROM product p
                  LEFT JOIN inventory_balance ib ON ib.product_id = p.product_id
                  LEFT JOIN warehouse w ON w.warehouse_id = ib.warehouse_id
                """ + query.where() + """
                 GROUP BY p.product_id, p.product_code, p.product_name, p.min_purchase_qty
                HAVING COALESCE(SUM(ib.available_qty), 0) < p.min_purchase_qty
                 ORDER BY availableQty ASC, p.product_name
                 LIMIT 6
                """, query.args());
    }

    private ScopedQuery scoped(String initialWhere, List<Object> initialArgs, String deptColumn, String ownerColumn) {
        StringBuilder where = new StringBuilder(initialWhere);
        List<Object> args = new ArrayList<>(initialArgs);
        dataScopeService.appendScope(where, args, deptColumn, ownerColumn);
        return new ScopedQuery(where.toString(), args);
    }

    private <T> T queryForObject(String sql, Class<T> type, List<Object> args) {
        return args.isEmpty() ? jdbcTemplate.queryForObject(sql, type)
                : jdbcTemplate.queryForObject(sql, type, args.toArray());
    }

    private List<Map<String, Object>> queryForList(String sql, List<Object> args) {
        return args.isEmpty() ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, args.toArray());
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

    private static BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : new BigDecimal("100.00");
        }
        return current.subtract(previous)
                .multiply(new BigDecimal("100"))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private record ScopedQuery(String where, List<Object> args) {
    }
}
