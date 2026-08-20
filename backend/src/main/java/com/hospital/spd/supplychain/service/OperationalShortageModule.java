package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.service.DocumentKind.SHORTAGE_REPLENISHMENT_TASK;

/**
 * Owns shortage replenishment task generation inside Operational Closure.
 */
@Service
public class OperationalShortageModule {

    private static final List<Integer> SMART_PERIOD_DAYS = List.of(5, 7, 15, 30);

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public OperationalShortageModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> generateShortage(Map<String, Object> body) {
        String deptName = text(body, "deptName");
        if (deptName.isBlank()) {
            throw new IllegalArgumentException("deptName is required");
        }
        String productCode = text(body, "productCode");
        if (productCode.isBlank()) {
            throw new IllegalArgumentException("productCode is required");
        }
        BigDecimal minQty = decimal(body, "minQty", null);
        BigDecimal currentQty = decimal(body, "currentQty", BigDecimal.ZERO);
        if (minQty == null || minQty.signum() <= 0) {
            throw new IllegalArgumentException("minQty must be greater than zero");
        }
        if (currentQty.signum() < 0) {
            throw new IllegalArgumentException("currentQty must not be negative");
        }
        int periodDays = positiveInt(body, "replenishmentDays", 7);
        Map<String, Object> product = findProduct(productCode);
        BigDecimal periodIssueQty = periodIssueQty(deptName, productCode, periodDays);
        BigDecimal avgDailyIssueQty = periodIssueQty
                .divide(BigDecimal.valueOf(periodDays), 4, RoundingMode.HALF_UP);
        BigDecimal formulaReplenishQty = periodIssueQty.subtract(currentQty).max(BigDecimal.ZERO);
        boolean manualAdjusted = hasValue(body, "replenishQty");
        BigDecimal replenishQty = manualAdjusted
                ? decimal(body, "replenishQty", formulaReplenishQty)
                : formulaReplenishQty;
        if (replenishQty.signum() < 0) {
            throw new IllegalArgumentException("replenishQty must not be negative");
        }
        String formulaText = "近" + periodDays + "天出库量(" + periodIssueQty.stripTrailingZeros().toPlainString()
                + ") - 当前库存(" + currentQty.stripTrailingZeros().toPlainString() + ")";
        String taskNo = support.nextNo(SHORTAGE_REPLENISHMENT_TASK);
        jdbcTemplate.update("""
                INSERT INTO shortage_replenishment_task (
                  task_no, dept_name, product_code, product_name, min_qty, current_qty, replenish_qty,
                  period_days, period_issue_qty, avg_daily_issue_qty, formula_replenish_qty,
                  manual_adjusted, formula_text, status, source_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending_replenish', 'period_issue')
                """, taskNo, deptName, productCode, product.get("productName"), minQty, currentQty, replenishQty,
                periodDays, periodIssueQty, avgDailyIssueQty, formulaReplenishQty, manualAdjusted ? 1 : 0, formulaText);
        return Map.of(
                "taskNo", taskNo,
                "status", "pending_replenish",
                "periodDays", periodDays,
                "periodIssueQty", periodIssueQty,
                "avgDailyIssueQty", avgDailyIssueQty,
                "formulaReplenishQty", formulaReplenishQty,
                "replenishQty", replenishQty,
                "manualAdjusted", manualAdjusted
        );
    }

    @Transactional
    public Map<String, Object> smartAnalyze(Map<String, Object> body) {
        String deptName = text(body, "deptName");
        if (deptName.isBlank()) {
            throw new IllegalArgumentException("请选择科室");
        }
        String warehouseName = text(body, "warehouseName");
        warehouseName = resolveLinkedWarehouse(deptName, warehouseName);
        int selectedPeriodDays = positiveInt(body, "selectedPeriodDays", 7);
        if (!SMART_PERIOD_DAYS.contains(selectedPeriodDays)) {
            selectedPeriodDays = 7;
        }
        final int periodDaysForAnalysis = selectedPeriodDays;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode,
                       p.product_name AS productName,
                       COALESCE(SUM(CASE WHEN dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) THEN dci.quantity ELSE 0 END), 0) AS issue5,
                       COALESCE(SUM(CASE WHEN dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY) THEN dci.quantity ELSE 0 END), 0) AS issue7,
                       COALESCE(SUM(CASE WHEN dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 15 DAY) THEN dci.quantity ELSE 0 END), 0) AS issue15,
                       COALESCE(SUM(CASE WHEN dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY) THEN dci.quantity ELSE 0 END), 0) AS issue30,
                       COALESCE((
                         SELECT SUM(bal.available_qty)
                           FROM inventory_balance bal
                           JOIN warehouse bw ON bw.warehouse_id = bal.warehouse_id
                          WHERE bw.warehouse_name = ? AND bw.deleted = 0 AND bal.product_id = p.product_id
                       ), 0) AS currentQty
                  FROM department_consumption dc
                  JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN product p ON p.product_id = dci.product_id
                 WHERE sd.dept_name = ?
                   AND dc.status <> 'reversed'
                   AND dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                 GROUP BY p.product_id, p.product_code, p.product_name
                 ORDER BY issue30 DESC, p.product_name
                """, warehouseName, deptName);

        List<Map<String, Object>> suggestions = rows.stream()
                .map(row -> buildSuggestion(row, periodDaysForAnalysis))
                .toList();
        Long analysisId = storeAnalysis(deptName, warehouseName, selectedPeriodDays, suggestions);
        return Map.of(
                "analysisId", analysisId,
                "deptName", deptName,
                "warehouseName", warehouseName,
                "selectedPeriodDays", selectedPeriodDays,
                "periodDays", SMART_PERIOD_DAYS,
                "rows", suggestions
        );
    }

    private Map<String, Object> buildSuggestion(Map<String, Object> row, int selectedPeriodDays) {
        BigDecimal currentQty = decimalValue(row.get("currentQty"));
        BigDecimal issue5 = decimalValue(row.get("issue5"));
        BigDecimal issue7 = decimalValue(row.get("issue7"));
        BigDecimal issue15 = decimalValue(row.get("issue15"));
        BigDecimal issue30 = decimalValue(row.get("issue30"));
        BigDecimal selectedIssueQty = switch (selectedPeriodDays) {
            case 5 -> issue5;
            case 15 -> issue15;
            case 30 -> issue30;
            default -> issue7;
        };
        BigDecimal recommendedQty = selectedIssueQty.subtract(currentQty).max(BigDecimal.ZERO);
        return Map.of(
                "productCode", String.valueOf(row.get("productCode")),
                "productName", String.valueOf(row.get("productName")),
                "issue5", issue5,
                "issue7", issue7,
                "issue15", issue15,
                "issue30", issue30,
                "currentQty", currentQty,
                "selectedIssueQty", selectedIssueQty,
                "recommendedQty", recommendedQty,
                "formulaText", "近" + selectedPeriodDays + "天出库量 - 当前库存"
        );
    }

    private Long storeAnalysis(String deptName, String warehouseName, int selectedPeriodDays, List<Map<String, Object>> rows) {
        ensureAnalysisTables();
        jdbcTemplate.update("""
                INSERT INTO replenishment_smart_analysis (
                  dept_name, warehouse_name, selected_period_days, item_count, total_recommended_qty
                ) VALUES (?, ?, ?, ?, ?)
                """,
                deptName,
                warehouseName,
                selectedPeriodDays,
                rows.size(),
                rows.stream()
                        .map(row -> decimalValue(row.get("recommendedQty")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        Long analysisId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Long id = analysisId == null ? 0L : analysisId;
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO replenishment_smart_analysis_item (
                      analysis_id, product_code, product_name, issue_5_qty, issue_7_qty, issue_15_qty,
                      issue_30_qty, current_qty, recommended_qty, formula_text
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    row.get("productCode"),
                    row.get("productName"),
                    row.get("issue5"),
                    row.get("issue7"),
                    row.get("issue15"),
                    row.get("issue30"),
                    row.get("currentQty"),
                    row.get("recommendedQty"),
                    row.get("formulaText"));
        }
        return id;
    }

    private void ensureAnalysisTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS replenishment_smart_analysis (
                  analysis_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析ID',
                  dept_name VARCHAR(80) NOT NULL COMMENT '科室名称',
                  warehouse_name VARCHAR(80) NOT NULL COMMENT '库房名称',
                  selected_period_days INT NOT NULL COMMENT '选择周期天数',
                  item_count INT NOT NULL DEFAULT 0 COMMENT '分析商品数',
                  total_recommended_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '建议补货总量',
                  analysis_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
                  PRIMARY KEY (analysis_id),
                  KEY idx_dept_warehouse_time (dept_name, warehouse_name, analysis_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能补货分析主表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS replenishment_smart_analysis_item (
                  item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析明细ID',
                  analysis_id BIGINT UNSIGNED NOT NULL COMMENT '分析ID',
                  product_code VARCHAR(50) NOT NULL COMMENT '商品编码',
                  product_name VARCHAR(120) NOT NULL COMMENT '商品名称',
                  issue_5_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '5天出库数量',
                  issue_7_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '7天出库数量',
                  issue_15_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '15天出库数量',
                  issue_30_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '30天出库数量',
                  current_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '当前库存',
                  recommended_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '建议补货数量',
                  formula_text VARCHAR(255) DEFAULT NULL COMMENT '公式说明',
                  PRIMARY KEY (item_id),
                  KEY idx_analysis_id (analysis_id),
                  KEY idx_product_code (product_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能补货分析明细表'
                """);
    }

    private String resolveLinkedWarehouse(String deptName, String requestedWarehouseName) {
        List<String> linkedWarehouses;
        if (requestedWarehouseName.isBlank()) {
            linkedWarehouses = jdbcTemplate.queryForList("""
                    SELECT w.warehouse_name
                      FROM warehouse w
                      JOIN sys_dept sd ON sd.dept_id = w.dept_id
                     WHERE sd.dept_name = ?
                       AND sd.deleted = 0 AND sd.status = 1
                       AND w.deleted = 0 AND w.status = 1
                     ORDER BY w.warehouse_id
                     LIMIT 1
                    """, String.class, deptName);
        } else {
            linkedWarehouses = jdbcTemplate.queryForList("""
                    SELECT w.warehouse_name
                      FROM warehouse w
                      JOIN sys_dept sd ON sd.dept_id = w.dept_id
                     WHERE sd.dept_name = ?
                       AND w.warehouse_name = ?
                       AND sd.deleted = 0 AND sd.status = 1
                       AND w.deleted = 0 AND w.status = 1
                     ORDER BY w.warehouse_id
                     LIMIT 1
                    """, String.class, deptName, requestedWarehouseName);
        }
        if (!linkedWarehouses.isEmpty()) {
            return linkedWarehouses.get(0);
        }
        if (!requestedWarehouseName.isBlank()) {
            throw new IllegalArgumentException("所选库房未关联当前科室");
        }
        throw new IllegalArgumentException("当前科室未关联启用库房");
    }

    private BigDecimal periodIssueQty(String deptName, String productCode, int periodDays) {
        BigDecimal value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(dci.quantity), 0)
                  FROM department_consumption dc
                  JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN product p ON p.product_id = dci.product_id
                 WHERE sd.dept_name = ?
                   AND p.product_code = ?
                   AND dc.status <> 'reversed'
                   AND dc.consume_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? DAY)
                """, BigDecimal.class, deptName, productCode, periodDays);
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static int positiveInt(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        int parsed = Integer.parseInt(String.valueOf(value));
        if (parsed <= 0 || parsed > 365) {
            throw new IllegalArgumentException("补货周期天数需在 1-365 天之间");
        }
        return parsed;
    }

    private static boolean hasValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }
}
