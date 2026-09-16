package com.hospital.spd.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Serializes retries by user and operation ID within the same transaction as delivery signing. */
@Repository
public class MobileReceiptRepository {
    public record Receipt(String payloadHash, Long deptId, Long warehouseId, Result result) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MobileReceiptRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Receipt acquire(Long userId, SignOperation op, String hash) {
        // A concurrent retry waits on the unique key. Rollback removes pending and the business write together.
        jdbc.update("""
                INSERT INTO mobile_operation_receipt
                (user_id, operation_id, device_id, kind, task_id, dept_id, warehouse_id, payload_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')
                ON DUPLICATE KEY UPDATE operation_id = operation_id
                """, userId, op.operationId().toString(), op.deviceId(), op.kind(), op.taskId(),
                op.deptId(), op.warehouseId(), hash);
        return find(userId, op.operationId(), true);
    }

    public Receipt find(Long userId, UUID operationId, boolean lock) {
        List<Receipt> rows = jdbc.query("""
                SELECT payload_hash, dept_id, warehouse_id, result_json
                FROM mobile_operation_receipt WHERE user_id = ? AND operation_id = ?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Receipt(rs.getString(1), rs.getLong(2),
                rs.getLong(3), decode(rs.getString(4))), userId, operationId.toString());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void complete(Long userId, SignOperation op, Result result, String resultJson) {
        int changed = jdbc.update("""
                UPDATE mobile_operation_receipt SET status = 'succeeded', result_json = CAST(? AS JSON), completed_at = ?
                WHERE user_id = ? AND operation_id = ? AND status = 'pending'
                """, resultJson, Timestamp.from(result.completedAt()), userId, op.operationId().toString());
        if (changed != 1) throw new IllegalStateException("移动操作回执未能保存");
    }

    private Result decode(String value) {
        if (value == null) return null;
        try { return json.readValue(value, Result.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("移动回执格式错误", ex); }
    }
}
