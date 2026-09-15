package com.hospital.spd.system.service;

import com.hospital.spd.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Read-only home workbench projections, scoped to the authenticated operator. */
@Service
@Transactional(readOnly = true)
public class DashboardWorkbenchService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    private final OperatorContextProvider operators;
    private final RbacAuthorizationService permissions;
    public DashboardWorkbenchService(JdbcTemplate jdbc, DataScopeService scopes,
            OperatorContextProvider operators, RbacAuthorizationService permissions) {
        this.jdbc = jdbc; this.scopes = scopes; this.operators = operators; this.permissions = permissions;
    }

    public Map<String, Object> workbench() {
        LocalDate today = Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class));
        LocalDate start = today.minusDays(29), end = today.plusDays(1);
        Map<String, Map<String, Object>> days = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("date", day.toString());
            for (String key : List.of("salesAmount", "salesQuantity", "inboundQuantity", "outboundQuantity",
                    "inboundAmount", "outboundAmount", "purchaseAmount")) values.put(key, BigDecimal.ZERO);
            days.put(day.toString(), values);
        }
        merge(days, daily("""
                SELECT DATE_FORMAT(dc.consume_time, '%Y-%m-%d') AS date,
                       SUM(i.amount) AS salesAmount, SUM(i.quantity) AS salesQuantity
                  FROM department_consumption dc
                  JOIN department_consumption_item i ON i.consumption_id = dc.consumption_id
                 WHERE dc.status = 'confirmed'
                   AND COALESCE(dc.related_biz_type, '') <> 'high_value_charge'
                """, "dc.consume_time", "dc.dept_id", "dc.consume_by", start, end));
        // Legacy billing owns a department name, not a department ID. Ambiguous names fail closed.
        merge(days, daily("""
                SELECT DATE_FORMAT(COALESCE(h.charge_time, h.create_time), '%Y-%m-%d') AS date,
                       SUM(h.amount) AS salesAmount, SUM(h.quantity) AS salesQuantity
                  FROM high_value_charge h LEFT JOIN (
                    SELECT dept_name, MIN(dept_id) AS dept_id FROM sys_dept
                     WHERE deleted = 0 GROUP BY dept_name HAVING COUNT(*) = 1
                  ) d ON d.dept_name = h.dept_name
                 WHERE h.status = 'charged'
                """, "COALESCE(h.charge_time, h.create_time)", "d.dept_id", null, start, end));
        merge(days, daily("""
                SELECT DATE_FORMAT(e.event_time, '%Y-%m-%d') AS date,
                       SUM(GREATEST(e.qty_change, 0)) AS inboundQuantity,
                       SUM(GREATEST(-e.qty_change, 0)) AS outboundQuantity,
                       SUM(CASE WHEN e.qty_change > 0 THEN e.amount_snapshot ELSE 0 END) AS inboundAmount,
                       SUM(CASE WHEN e.qty_change < 0 THEN -e.amount_snapshot ELSE 0 END) AS outboundAmount
                  FROM inventory_event e WHERE e.qty_change <> 0
                """, "e.event_time", "e.dept_id_snapshot", "e.operator_id", start, end));
        merge(days, daily("""
                SELECT DATE_FORMAT(po.approve_time, '%Y-%m-%d') AS date,
                       SUM(po.total_amount) AS purchaseAmount
                  FROM purchase_order po LEFT JOIN sys_user u ON u.user_id = po.create_by
                 WHERE po.order_status IN ('approved', 'sent', 'closed') AND po.approve_time IS NOT NULL
                """, "po.approve_time", "u.dept_id", "po.create_by", start, end));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.toString());
        result.put("metrics", days.get(today.toString()));
        result.put("trend", new ArrayList<>(days.values()));
        int expiryDays = expiryWarningDays();
        result.put("alerts", alerts(expiryDays));
        result.put("todos", todos());
        result.put("notices", List.of());
        result.put("noticeSourceAvailable", false);
        result.put("definitions", Map.of("sales", "已确认科室耗用及高值计费；排除冲销及高值镜像消耗",
                "expiryWarningDays", expiryDays, "stagnantDays", 90, "amountUnit", "元",
                "holderAvailable", false, "todoProgressAvailable", false));
        return result;
    }

    private List<Map<String, Object>> daily(String sql, String time, String dept, String owner,
            LocalDate start, LocalDate end) {
        StringBuilder query = new StringBuilder(sql).append(" AND ").append(time).append(" >= ? AND ").append(time).append(" < ?");
        List<Object> args = new ArrayList<>(List.of(start, end));
        scopes.appendScope(query, args, dept, owner);
        return jdbc.queryForList(query + " GROUP BY date", args.toArray());
    }

    private static void merge(Map<String, Map<String, Object>> days, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> day = days.get(String.valueOf(row.get("date")));
            if (day == null) continue;
            row.forEach((key, value) -> {
                if (!key.equals("date") && day.containsKey(key) && value != null)
                    day.put(key, new BigDecimal(day.get(key).toString()).add(new BigDecimal(value.toString())));
            });
        }
    }

    private int expiryWarningDays() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.expiryWarningDays')) AS days
                  FROM system_config WHERE config_key = 'warning.stock.threshold'
                   AND scope_type = 'global' AND scope_id = 'warning' AND deleted = 0 AND status = 1
                   AND (effective_time IS NULL OR effective_time <= NOW())
                   AND (expire_time IS NULL OR expire_time > NOW())
                """);
        if (rows.size() > 1) throw new IllegalArgumentException("效期预警配置存在冲突");
        if (rows.isEmpty() || rows.get(0).get("days") == null || "null".equals(rows.get(0).get("days"))) return 30;
        try {
            int days = Integer.parseInt(rows.get(0).get("days").toString());
            if (days < 0 || days > 3650) throw new NumberFormatException();
            return days;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("效期预警天数配置无效"); }
    }

    private Map<String, Object> alerts(int expiryDays) {
        Map<String, Object> result = new LinkedHashMap<>();
        String stock = """
                 FROM inventory_balance bal JOIN inventory_batch b ON b.batch_id = bal.batch_id
                 JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                 WHERE w.deleted = 0 AND w.status = 1
                """;
        result.put("expiry", scopedCount("SELECT COUNT(*)" + stock
                + " AND bal.available_qty > 0 AND b.expire_date <= CURRENT_DATE + INTERVAL ? DAY", "w.dept_id", null, expiryDays));
        result.put("quality", scopedCount("SELECT COUNT(*)" + stock + " AND bal.isolated_qty > 0", "w.dept_id", null));
        result.put("stagnant", scopedCount("SELECT COUNT(*)" + stock + """
                 AND bal.available_qty > 0 AND b.create_time < CURRENT_DATE - INTERVAL 90 DAY
                 AND NOT EXISTS (SELECT 1 FROM inventory_event e WHERE e.warehouse_id = bal.warehouse_id
                   AND e.product_id = bal.product_id AND e.batch_id = bal.batch_id
                   AND e.qty_change < 0 AND e.event_time >= CURRENT_DATE - INTERVAL 90 DAY)
                """, "w.dept_id", null));
        String safety = """
                 FROM quota_safety_stock s JOIN product p ON p.product_id = s.product_id
                 LEFT JOIN (SELECT w.dept_id, b.product_id, SUM(b.available_qty) AS qty
                   FROM inventory_balance b JOIN warehouse w ON w.warehouse_id = b.warehouse_id
                   WHERE w.deleted = 0 AND w.status = 1 GROUP BY w.dept_id, b.product_id
                 ) stock ON stock.dept_id = s.dept_id AND stock.product_id = s.product_id
                 WHERE s.status = 1 AND s.deleted = 0 AND p.deleted = 0 AND p.status = 1
                """;
        result.put("lowStock", scopedCount("SELECT COUNT(*)" + safety + " AND COALESCE(stock.qty, 0) < s.min_qty", "s.dept_id", null));
        result.put("shortage", scopedCount("SELECT COUNT(*)" + safety + " AND s.min_qty > 0 AND COALESCE(stock.qty, 0) <= 0", "s.dept_id", null));
        StringBuilder licenses = new StringBuilder("""
                SELECT COUNT(*) FROM license_document ld WHERE ld.deleted = 0 AND ld.status = 1
                 AND ld.expire_date <= CURRENT_DATE + INTERVAL ? DAY
                 AND EXISTS (SELECT 1 FROM inventory_balance bal
                    JOIN product p ON p.product_id = bal.product_id
                    JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                   WHERE w.deleted = 0 AND w.status = 1 AND
                     ((ld.owner_type = 'product' AND ld.owner_id = p.product_id)
                       OR (ld.owner_type = 'supplier' AND ld.owner_id = p.supplier_id)
                       OR (ld.owner_type = 'manufacturer' AND ld.owner_id = p.manufacturer_id))
                """);
        List<Object> args = new ArrayList<>(List.of(expiryDays));
        scopes.appendScope(licenses, args, "w.dept_id", "ld.create_by");
        result.put("license", count(licenses + ")", args));
        return result;
    }

    private Map<String, Object> todos() {
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        if (allowed("/purchase-orders")) total += todo(rows, """
                SELECT po.order_no AS id, '采购订单待审核' AS title, '待审核' AS status, 0 AS progress, po.create_time AS eventTime
                """, " FROM purchase_order po LEFT JOIN sys_user u ON u.user_id = po.create_by WHERE po.order_status = 'pending_approval'",
                "u.dept_id", "po.create_by");
        if (allowed("/receiving-orders")) total += todo(rows, """
                SELECT ro.receiving_no AS id, '到货验收' AS title, '待验收' AS status, 0 AS progress, ro.create_time AS eventTime
                """, " FROM receiving_order ro JOIN warehouse w ON w.warehouse_id = ro.warehouse_id WHERE ro.receiving_status = 'draft'",
                "w.dept_id", "ro.receiver_id");
        if (allowed("/operational-closure/requisitions")) total += todo(rows, """
                SELECT dr.requisition_no AS id, '科室申领' AS title,
                       CASE WHEN dr.status = 'pending_approval' THEN '待审核' ELSE '待配送' END AS status,
                       0 AS progress, dr.apply_time AS eventTime
                """, " FROM department_requisition dr WHERE dr.status IN ('pending_approval', 'approved', 'partial_picked')",
                "dr.dept_id", "dr.applicant_id");
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("eventTime"))).reversed());
        return PageResponse.of(rows.stream().limit(8).toList(), total, new PageRequest(1, 8, 0));
    }

    private boolean allowed(String path) {
        OperatorContext op = operators.current();
        return permissions.isAllowed(op.userId(), op.roles(), "GET", path);
    }
    private long todo(List<Map<String, Object>> rows, String select, String from, String dept, String owner) {
        List<Object> args = new ArrayList<>();
        StringBuilder query = new StringBuilder(from);
        scopes.appendScope(query, args, dept, owner);
        long total = count("SELECT COUNT(*)" + query, args);
        rows.addAll(jdbc.queryForList(select + query + " ORDER BY eventTime DESC, id DESC LIMIT 8", args.toArray()));
        return total;
    }
    private long scopedCount(String sql, String dept, String owner, Object... initialArgs) {
        List<Object> args = new ArrayList<>(Arrays.asList(initialArgs)); StringBuilder query = new StringBuilder(sql);
        scopes.appendScope(query, args, dept, owner);
        return count(query.toString(), args);
    }
    private long count(String sql, List<Object> args) {
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public Map<String, Object> products(Map<String, String> params) {
        PageRequest page = PageRequest.from(params);
        if ((long) (page.page() - 1) * page.size() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("页码过大");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE w.deleted = 0 AND w.status = 1 AND p.deleted = 0");
        scopes.appendScope(where, args, "w.dept_id", null);
        String keyword = params.getOrDefault("keyword", "").trim();
        if (keyword.length() > 100) throw new IllegalArgumentException("搜索词不能超过 100 字");
        if (!keyword.isEmpty()) {
            where.append(" AND (p.product_name LIKE ? ESCAPE '!' OR p.product_code LIKE ? ESCAPE '!' OR p.spec_model LIKE ? ESCAPE '!' OR w.warehouse_name LIKE ? ESCAPE '!' OR loc.location_code LIKE ? ESCAPE '!' OR p.registration_no LIKE ? ESCAPE '!' OR m.manufacturer_name LIKE ? ESCAPE '!' OR s.supplier_name LIKE ? ESCAPE '!')");
            String like = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            for (int i = 0; i < 8; i++) args.add(like);
        }
        String from = """
                 FROM inventory_balance bal JOIN product p ON p.product_id = bal.product_id
                 JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                 JOIN inventory_batch b ON b.batch_id = bal.batch_id
                 LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                 LEFT JOIN supplier s ON s.supplier_id = b.supplier_id
                 LEFT JOIN warehouse_location loc ON loc.location_id = bal.location_id AND loc.warehouse_id = w.warehouse_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size()); pageArgs.add(page.offset());
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bal.balance_id AS id, p.product_name AS name, COALESCE(p.registration_no, '') AS registrationNo,
                       COALESCE(m.manufacturer_name, '') AS manufacturerName, COALESCE(s.supplier_name, '') AS distributorName,
                       COALESCE(DATE_FORMAT(b.expire_date, '%Y-%m-%d'), '-') AS expiry,
                       p.spec_model AS specification, bal.available_qty AS quantity,
                       CONCAT(w.warehouse_name, ' / ', COALESCE(loc.location_code, '未分配货位')) AS location
                """ + from + where + " ORDER BY bal.balance_id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, page,
                Map.of("quantityDefinition", "available_qty"));
    }
}
