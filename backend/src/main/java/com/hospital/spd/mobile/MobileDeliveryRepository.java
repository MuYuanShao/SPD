package com.hospital.spd.mobile;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Reads authoritative delivery routes and physical identities; write callers lock the same delivery as desktop. */
@Repository
public class MobileDeliveryRepository {
    private final JdbcTemplate jdbc;
    private static final String FROM = """
            FROM spd_delivery_order s
            JOIN department_requisition r ON r.requisition_no = s.requisition_no
            JOIN sys_dept d ON d.dept_id = r.dept_id AND d.deleted = 0 AND d.status = 1
            JOIN warehouse src ON src.warehouse_id = r.source_warehouse_id AND src.deleted = 0 AND src.status = 1
            JOIN warehouse dst ON dst.warehouse_id = r.warehouse_id AND dst.dept_id = r.dept_id AND dst.deleted = 0 AND dst.status = 1
            JOIN product p ON p.product_code = s.product_code AND p.deleted = 0 AND p.status = 1
            """;
    private static final String SELECT = """
            SELECT s.delivery_id, s.delivery_no, s.status, s.delivery_type,
                   d.dept_id, d.dept_name, src.warehouse_id, src.warehouse_name,
                   dst.warehouse_id, dst.warehouse_name, s.product_code, s.product_name, s.quantity, p.unit
            """;
    private static final RowMapper<Task> TASK = (rs, row) -> new Task(rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getString(4), rs.getLong(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getLong(9),
            rs.getString(10), rs.getString(11), rs.getString(12), rs.getBigDecimal(13), rs.getString(14));

    public MobileDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResponse<Task> tasks(Long deptId, Long warehouseId, String status, PageRequest page) {
        String where = " WHERE r.dept_id = ? AND r.warehouse_id = ? AND s.status = ?";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, deptId, warehouseId, status);
        List<Task> rows = jdbc.query(SELECT + FROM + where + " ORDER BY s.delivery_id DESC LIMIT ? OFFSET ?",
                TASK, deptId, warehouseId, status, page.size(), page.offset());
        return new PageResponse<>(rows, total == null ? 0 : total, page.page(), page.size(), null);
    }

    public Detail detail(Long id, Long deptId, Long warehouseId, boolean lock) {
        if (lock) {
            // Lock order matches OperationalDeliveryModule.signDelivery: delivery, then its requisition route.
            jdbc.queryForList("SELECT delivery_id FROM spd_delivery_order WHERE delivery_id = ? FOR UPDATE", id);
        }
        List<Task> tasks = jdbc.query(SELECT + FROM + " WHERE s.delivery_id = ? AND r.dept_id = ? AND r.warehouse_id = ?"
                + (lock ? " FOR UPDATE" : ""), TASK, id, deptId, warehouseId);
        if (tasks.size() != 1) throw new IllegalArgumentException("配送单不存在、不属于当前库房或关联资料已停用");
        String suffix = lock ? " FOR UPDATE" : "";
        List<CheckItem> items = new ArrayList<>(jdbc.query("""
                SELECT q.label_id, q.label_no, COALESCE(t.unique_code, ''), q.status
                FROM spd_delivery_package_binding b JOIN quota_package_label q ON q.label_id = b.label_id
                LEFT JOIN udi_trace_code t ON t.trace_code_id = q.trace_code_id
                WHERE b.delivery_id = ? ORDER BY q.label_id
                """ + suffix, (rs, row) -> new CheckItem("package", rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), id));
        items.addAll(jdbc.query("""
                SELECT t.trace_code_id, t.unique_code, COALESCE(t.udi_code, ''), t.current_status
                FROM spd_delivery_trace_code b JOIN udi_trace_code t ON t.trace_code_id = b.trace_code_id
                WHERE b.delivery_id = ? ORDER BY t.trace_code_id
                """ + suffix, (rs, row) -> new CheckItem("trace", rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), id));
        return new Detail(tasks.get(0), List.copyOf(items));
    }
}
