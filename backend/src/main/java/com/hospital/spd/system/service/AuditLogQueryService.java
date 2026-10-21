package com.hospital.spd.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.SqlHelper.appendLike;

/**
 * Read-side service for {@code audit_log}. AuditLogService is the write-side
 * producer; this service is the paged query + detail surface consumed by the
 * front-end audit log page.
 */
@Service
public class AuditLogQueryService {

    /** SQL that normalises JSON columns so the front-end can render before/after as a tree. */
    private static final String LIST_SQL = """
            SELECT a.audit_id, a.operator_id, a.operator_name, a.operation_type, a.biz_type,
                   a.biz_id, a.ip_address, a.operation_time, a.remark,
                   JSON_UNQUOTE(JSON_EXTRACT(a.after_data, '$.bizNo')) AS biz_no,
                   JSON_EXTRACT(a.before_data, '$') AS before_data,
                   JSON_EXTRACT(a.after_data, '$') AS after_data
              FROM audit_log a
             WHERE 1=1
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM audit_log a WHERE 1=1 ";

    private static final String DETAIL_SQL = """
            SELECT a.audit_id, a.operator_id, a.operator_name, a.operation_type, a.biz_type,
                   a.biz_id, a.ip_address, a.operation_time, a.remark,
                   JSON_UNQUOTE(JSON_EXTRACT(a.after_data, '$.bizNo')) AS biz_no,
                   a.before_data, a.after_data
              FROM audit_log a
             WHERE a.audit_id = ?
            """;

    private static final String OPERATION_TYPES_SQL = """
            SELECT operation_type AS operationType, COUNT(*) AS total
              FROM audit_log
             GROUP BY operation_type
             ORDER BY total DESC
            """;

    private static final String BIZ_TYPES_SQL = """
            SELECT biz_type AS bizType, COUNT(*) AS total
              FROM audit_log
             GROUP BY biz_type
             ORDER BY total DESC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(Map<String, String> params) {
        PageRequest pageRequest = PageRequest.from(params);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();

        applyFilters(params, where, args);

        String listSql = LIST_SQL + where + " ORDER BY a.operation_time DESC LIMIT ? OFFSET ?";
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageRequest.size());
        listArgs.add(pageRequest.offset());
        List<Map<String, Object>> rows = jdbcTemplate.query(listSql, this::mapRow, listArgs.toArray());

        String countSql = COUNT_SQL + where;
        long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        Map<String, Object> summary = buildSummary();
        return PageResponse.of(rows, total, pageRequest, summary);
    }

    public Map<String, Object> detail(long auditId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(DETAIL_SQL, this::mapRow, auditId);
        if (rows.isEmpty()) {
            return Map.of("audit", null);
        }
        return Map.of("audit", rows.get(0));
    }

    public Map<String, Object> operationTypes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(OPERATION_TYPES_SQL);
        return Map.of("operationTypes", rows);
    }

    public Map<String, Object> bizTypes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(BIZ_TYPES_SQL);
        return Map.of("bizTypes", rows);
    }

    // ========== helpers ==========

    private void applyFilters(Map<String, String> params, StringBuilder where, List<Object> args) {
        appendLike(where, args, "a.operator_name", params.get("operatorName"));
        appendLike(where, args, "a.operation_type", params.get("operationType"));
        appendLike(where, args, "a.biz_type", params.get("bizType"));
        appendLike(where, args, "a.biz_id", params.get("bizId"));
        appendLike(where, args, "a.ip_address", params.get("ipAddress"));
        appendLike(where, args, "a.remark", params.get("remark"));

        String bizNo = params.get("bizNo");
        if (bizNo != null && !bizNo.isBlank()) {
            where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(a.after_data, '$.bizNo')) LIKE ?");
            args.add("%" + bizNo.trim() + "%");
        }

        String from = params.get("fromDate");
        if (from != null && !from.isBlank()) {
            where.append(" AND a.operation_time >= ?");
            args.add(from.trim() + " 00:00:00");
        }
        String to = params.get("toDate");
        if (to != null && !to.isBlank()) {
            where.append(" AND a.operation_time <= ?");
            args.add(to.trim() + " 23:59:59");
        }
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs, int idx) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("auditId", rs.getLong("audit_id"));
        row.put("operatorId", rs.getObject("operator_id") == null ? null : rs.getLong("operator_id"));
        row.put("operatorName", rs.getString("operator_name"));
        row.put("operationType", rs.getString("operation_type"));
        row.put("bizType", rs.getString("biz_type"));
        row.put("bizId", rs.getObject("biz_id") == null ? null : rs.getLong("biz_id"));
        row.put("bizNo", rs.getString("biz_no"));
        row.put("ipAddress", rs.getString("ip_address"));
        row.put("operationTime", formatDateTime(rs.getTimestamp("operation_time")));
        row.put("remark", rs.getString("remark"));
        row.put("beforeData", parseJson(rs.getString("before_data")));
        row.put("afterData", parseJson(rs.getString("after_data")));
        return row;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private String formatDateTime(java.sql.Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private Map<String, Object> buildSummary() {
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
        long today = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                 WHERE operation_time >= ?
                """, Long.class, LocalDate.now().toString() + " 00:00:00");
        long distinctOperators = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT operator_id) FROM audit_log WHERE operator_id IS NOT NULL
                """, Long.class);
        long distinctBizTypes = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT biz_type) FROM audit_log
                """, Long.class);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("today", today);
        summary.put("distinctOperators", distinctOperators);
        summary.put("distinctBizTypes", distinctBizTypes);
        return summary;
    }
}
