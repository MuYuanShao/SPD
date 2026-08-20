package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the operational dashboard from persisted facts visible to the current operator.
 */
@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new DataScopeService(OperatorContext::system));
    }

    @Autowired
    public DashboardService(JdbcTemplate jdbcTemplate, DataScopeService dataScopeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeService = dataScopeService;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        Scope inventoryScope = scope(" WHERE ib.available_qty > 0", "w.dept_id", null);
        metrics.put("productCount", count("""
                SELECT COUNT(DISTINCT product_id)
                  FROM inventory_balance ib
                  JOIN warehouse w ON w.warehouse_id = ib.warehouse_id
                """ + inventoryScope.where(), inventoryScope.args()));

        Scope orderCountScope = scope(
                " WHERE DATE(po.create_time) = CURRENT_DATE", "creator.dept_id", "po.create_by");
        metrics.put("todayOrders", count("""
                SELECT COUNT(*)
                  FROM purchase_order po
                  LEFT JOIN sys_user creator ON creator.user_id = po.create_by
                """ + orderCountScope.where(), orderCountScope.args()));

        Scope lowStockScope = scope("""
                 WHERE p.deleted = 0 AND p.status = 1
                   AND p.min_purchase_qty > 0
                   AND ib.available_qty < p.min_purchase_qty
                """, "w.dept_id", null);
        metrics.put("lowStockCount", count("""
                SELECT COUNT(*)
                  FROM inventory_balance ib
                  JOIN product p ON p.product_id = ib.product_id
                  JOIN warehouse w ON w.warehouse_id = ib.warehouse_id
                """ + lowStockScope.where(), lowStockScope.args()));

        Scope monthlyPurchaseScope = scope(
                " WHERE po.create_time >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                "creator.dept_id", "po.create_by");
        BigDecimal monthlyAmount = queryForObject("""
                SELECT COALESCE(SUM(total_amount), 0)
                  FROM purchase_order po
                  LEFT JOIN sys_user creator ON creator.user_id = po.create_by
                """ + monthlyPurchaseScope.where(), BigDecimal.class, monthlyPurchaseScope.args());
        metrics.put("monthlyPurchaseAmount", monthlyAmount == null ? BigDecimal.ZERO : monthlyAmount);

        return Map.of(
                "metrics", metrics,
                "trend", loadTrend(),
                "departments", loadDepartmentCosts(),
                "exceptions", loadExceptions(),
                "notices", loadNotices()
        );
    }

    private List<Map<String, Object>> loadTrend() {
        Map<String, Map<String, Object>> days = new LinkedHashMap<>();
        DateTimeFormatter keyFormat = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MM-dd");
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.format(keyFormat));
            row.put("label", date.format(labelFormat));
            row.put("receivingAmount", BigDecimal.ZERO);
            row.put("consumptionAmount", BigDecimal.ZERO);
            days.put(date.format(keyFormat), row);
        }

        Scope receivingScope = scope(
                " WHERE ro.create_time >= CURRENT_DATE - INTERVAL 6 DAY",
                "w.dept_id", "ro.receiver_id");
        for (Map<String, Object> row : queryForList("""
                SELECT DATE_FORMAT(ro.create_time, '%Y-%m-%d') AS bizDate,
                       COALESCE(SUM(roi.amount), 0) AS amount
                  FROM receiving_order ro
                  JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                  JOIN warehouse w ON w.warehouse_id = ro.warehouse_id
                """ + receivingScope.where() + " GROUP BY DATE_FORMAT(ro.create_time, '%Y-%m-%d')",
                receivingScope.args())) {
            updateTrend(days, row, "receivingAmount");
        }

        Scope consumptionScope = scope(
                " WHERE dc.consume_time >= CURRENT_DATE - INTERVAL 6 DAY",
                "dc.dept_id", "dc.consume_by");
        for (Map<String, Object> row : queryForList("""
                SELECT DATE_FORMAT(dc.consume_time, '%Y-%m-%d') AS bizDate,
                       COALESCE(SUM(dci.amount), 0) AS amount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                """ + consumptionScope.where() + " GROUP BY DATE_FORMAT(dc.consume_time, '%Y-%m-%d')",
                consumptionScope.args())) {
            updateTrend(days, row, "consumptionAmount");
        }
        return new ArrayList<>(days.values());
    }

    private List<Map<String, Object>> loadDepartmentCosts() {
        Scope scope = scope(
                " WHERE dc.consume_time >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                "dc.dept_id", "dc.consume_by");
        return queryForList("""
                SELECT sd.dept_name AS deptName, COALESCE(SUM(dci.amount), 0) AS amount
                  FROM department_consumption dc
                  JOIN department_consumption_item dci ON dci.consumption_id = dc.consumption_id
                  JOIN sys_dept sd ON sd.dept_id = dc.dept_id
                """ + scope.where()
                + " GROUP BY sd.dept_id, sd.dept_name ORDER BY amount DESC LIMIT 5", scope.args());
    }

    private List<Map<String, Object>> loadExceptions() {
        Scope coldScope = scope(" WHERE 1 = 1", "coldWarehouse.dept_id", null);
        Scope recallScope = scope(" WHERE 1 = 1", "recallWarehouse.dept_id", null);
        List<Object> args = new ArrayList<>(coldScope.args());
        args.addAll(recallScope.args());
        return queryForList("""
                SELECT * FROM (
                  SELECT cce.event_no AS bizNo, 'cold_chain' AS riskEvents, cce.product_name AS title,
                         CONCAT('temperature ', cce.temperature, ' / ', cce.severity) AS reason,
                         cce.status, cce.event_time AS eventTime
                    FROM cold_chain_exception cce
                    LEFT JOIN warehouse coldWarehouse
                      ON coldWarehouse.warehouse_name = cce.warehouse_name AND coldWarehouse.deleted = 0
                """ + coldScope.where() + """
                  UNION ALL
                  SELECT re.recall_no AS bizNo, 'recall' AS riskEvents, re.product_name AS title,
                         re.reason, re.status, re.create_time AS eventTime
                    FROM recall_event re
                    LEFT JOIN warehouse recallWarehouse
                      ON recallWarehouse.warehouse_name = re.warehouse_name AND recallWarehouse.deleted = 0
                """ + recallScope.where()
                + ") risks ORDER BY eventTime DESC LIMIT 8", args);
    }

    private List<Map<String, Object>> loadNotices() {
        Scope scope = scope(" WHERE 1 = 1", "operatorUser.dept_id", "al.operator_id");
        return queryForList("""
                SELECT al.operation_type AS title,
                       CONCAT(al.biz_type, ' / ', COALESCE(al.remark, '-')) AS detail,
                       al.operator_name AS operatorName,
                       DATE_FORMAT(al.operation_time, '%Y-%m-%d %H:%i') AS operationTime
                  FROM audit_log al
                  LEFT JOIN sys_user operatorUser ON operatorUser.user_id = al.operator_id
                """ + scope.where() + " ORDER BY al.operation_time DESC LIMIT 8", scope.args());
    }

    private Scope scope(String initialWhere, String deptColumn, String ownerColumn) {
        StringBuilder where = new StringBuilder(initialWhere);
        List<Object> args = new ArrayList<>();
        dataScopeService.appendScope(where, args, deptColumn, ownerColumn);
        return new Scope(where.toString(), args);
    }

    private long count(String sql, List<Object> args) {
        Long value = queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private <T> T queryForObject(String sql, Class<T> type, List<Object> args) {
        return args.isEmpty()
                ? jdbcTemplate.queryForObject(sql, type)
                : jdbcTemplate.queryForObject(sql, type, args.toArray());
    }

    private List<Map<String, Object>> queryForList(String sql, List<Object> args) {
        return args.isEmpty()
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, args.toArray());
    }

    private static void updateTrend(Map<String, Map<String, Object>> days, Map<String, Object> source, String field) {
        String date = String.valueOf(source.get("bizDate"));
        Map<String, Object> target = days.get(date);
        if (target != null) target.put(field, source.getOrDefault("amount", BigDecimal.ZERO));
    }

    private record Scope(String where, List<Object> args) {
    }
}
