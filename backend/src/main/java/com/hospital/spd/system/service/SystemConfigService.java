package com.hospital.spd.system.service;

import static com.hospital.spd.common.SqlHelper.*;
import com.hospital.spd.system.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves system configuration rows and explains which scoped rule is active for a business context.
 */
@Service
public class SystemConfigService {
    private final JdbcTemplate jdbcTemplate;

    public SystemConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SystemConfigRow> list(String configType, String keyword, String status) {
        ensureDefaultConfigs();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT config_id, config_type, scope_type, scope_id, config_key,
                       JSON_UNQUOTE(config_value) AS config_value,
                       effective_mode, DATE_FORMAT(effective_time, '%Y-%m-%d %H:%i') AS effective_time,
                       DATE_FORMAT(expire_time, '%Y-%m-%d') AS expire_time,
                       COALESCE(risk_level, '-') AS risk_level,
                       CASE status WHEN 1 THEN '启用' ELSE '停用' END AS status,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS update_time
                FROM system_config
                WHERE deleted = 0
                """);
        if (configType != null && !configType.isBlank()) {
            sql.append(" AND config_type = ?");
            args.add(configType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (config_key LIKE ? OR scope_id LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add("启用".equals(status.trim()) ? 1 : 0);
        }
        sql.append(" ORDER BY config_type, risk_level DESC, config_id");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SystemConfigRow(
                rs.getLong("config_id"),
                rs.getString("config_type"),
                configTypeName(rs.getString("config_type")),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getString("config_key"),
                configName(rs.getString("config_key")),
                rs.getString("config_value"),
                rs.getString("effective_mode"),
                rs.getString("effective_time"),
                rs.getString("expire_time") == null ? "-" : rs.getString("expire_time"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getString("update_time")
        ), args.toArray());
    }

    @Transactional
    public Map<String, Object> create(SystemConfigRequest request) {
        validate(request);
        validateMcpConnection(request);
        requireNoActiveConflict(null, request);
        try {
            jdbcTemplate.update("""
                    INSERT INTO system_config (
                      config_type, scope_type, scope_id, config_key, config_value,
                      effective_mode, expire_time, risk_level, status
                    ) VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?)
                    """,
                    request.configType().trim(),
                    request.scopeType().trim(),
                    request.scopeId().trim(),
                    request.configKey().trim(),
                    normalizeJson(request.configValue()),
                    defaultText(request.effectiveMode(), "realtime"),
                    toSqlDate(request.expireTime()),
                    nullIfBlank(request.riskLevel()),
                    request.status() == null ? 1 : request.status()
            );
        } catch (DuplicateKeyException conflict) {
            throw new IllegalArgumentException("该配置键在此范围已存在，请编辑已有配置（包括停用或过期记录）");
        }
        return Map.of("configKey", request.configKey().trim());
    }

    @Transactional
    public Map<String, Object> update(Long configId, SystemConfigRequest request) {
        validate(request);
        validateMcpConnection(request);
        requireNoActiveConflict(configId, request);
        int updatedRows;
        try {
            updatedRows = jdbcTemplate.update("""
                    UPDATE system_config
                    SET config_type = ?, scope_type = ?, scope_id = ?, config_key = ?,
                        config_value = CAST(? AS JSON), effective_mode = ?, expire_time = ?,
                        risk_level = ?, status = ?
                    WHERE config_id = ? AND deleted = 0
                    """,
                    request.configType().trim(),
                    request.scopeType().trim(),
                    request.scopeId().trim(),
                    request.configKey().trim(),
                    normalizeJson(request.configValue()),
                    defaultText(request.effectiveMode(), "realtime"),
                    toSqlDate(request.expireTime()),
                    nullIfBlank(request.riskLevel()),
                    request.status() == null ? 1 : request.status(),
                    configId
            );
        } catch (DuplicateKeyException conflict) {
            throw new IllegalArgumentException("该配置键在目标范围已存在，请编辑已有配置");
        }
        return Map.of("updatedRows", updatedRows, "configId", configId);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long configId, int status) {
        if (status != 0 && status != 1) throw new IllegalArgumentException("配置状态只能为启用或停用");
        if (status == 1) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT config_type AS configType, scope_type AS scopeType, scope_id AS scopeId,
                           config_key AS configKey, expire_time AS expireTime
                      FROM system_config WHERE config_id = ? AND deleted = 0 FOR UPDATE
                    """, configId);
            if (rows.isEmpty()) throw new IllegalArgumentException("配置不存在或已删除");
            Map<String, Object> row = rows.get(0);
            requireNoActiveConflict(configId, new SystemConfigRequest(String.valueOf(row.get("configType")),
                    String.valueOf(row.get("scopeType")), String.valueOf(row.get("scopeId")),
                    String.valueOf(row.get("configKey")), "", "realtime",
                    row.get("expireTime") == null ? null : row.get("expireTime").toString().substring(0, 10), null, 1));
        }
        int updatedRows = jdbcTemplate.update("UPDATE system_config SET status = ? WHERE config_id = ? AND deleted = 0", status, configId);
        return Map.of("updatedRows", updatedRows, "configId", configId);
    }

    /** Preserve legacy rows; reject overlapping active identities before writes, with the unique key as concurrency guard. */
    private void requireNoActiveConflict(Long configId, SystemConfigRequest request) {
        int status = request.status() == null ? 1 : request.status();
        if (status != 0 && status != 1) throw new IllegalArgumentException("配置状态只能为启用或停用");
        if (status == 0) return;
        List<Long> conflicts = jdbcTemplate.queryForList("""
                SELECT config_id FROM system_config
                 WHERE config_type = ? AND scope_type = ? AND scope_id = ? AND config_key = ?
                   AND config_id <> ? AND status = 1 AND deleted = 0
                   AND (expire_time IS NULL OR expire_time >= CURRENT_DATE)
                   AND effective_time < COALESCE(DATE_ADD(CAST(? AS DATE), INTERVAL 1 DAY), '9999-12-31 23:59:59')
                 ORDER BY config_id FOR UPDATE
                """, Long.class, request.configType().trim(), request.scopeType().trim(), request.scopeId().trim(),
                request.configKey().trim(), configId == null ? 0L : configId, toSqlDate(request.expireTime()));
        if (!conflicts.isEmpty()) throw new IllegalArgumentException("同一配置键在此范围存在生效冲突，请编辑已有配置");
    }

    public List<ConfigKeyOption> keyOptions(String configType) {
        if (isBlank(configType)) throw new IllegalArgumentException("请选择配置类型");
        return jdbcTemplate.query("""
                SELECT DISTINCT config_key FROM system_config
                 WHERE config_type = ? AND deleted = 0 ORDER BY config_key
                """, (rs, rowNum) -> new ConfigKeyOption(rs.getString("config_key"),
                configName(rs.getString("config_key"))), configType.trim());
    }

    public ConfigHitExplanation explain(Map<String, String> params) {
        String configType = defaultText(params.get("configType"), "workflow");
        if (isBlank(params.get("configKey"))) throw new IllegalArgumentException("请选择需要核对的配置项");
        String configKey = params.get("configKey").trim();
        List<ScopeCandidate> scopes = buildScopes(params);
        List<Object> args = new ArrayList<>(List.of(configType, configKey));
        StringBuilder sql = new StringBuilder("""
                SELECT config_id, config_type, scope_type, scope_id, config_key,
                       JSON_UNQUOTE(config_value) AS config_value,
                       DATE_FORMAT(effective_time, '%Y-%m-%d %H:%i') AS effective_time,
                       DATE_FORMAT(expire_time, '%Y-%m-%d') AS expire_time,
                       COALESCE(risk_level, '-') AS risk_level
                  FROM system_config
                 WHERE status = 1 AND deleted = 0 AND effective_time <= CURRENT_TIMESTAMP
                   AND (expire_time IS NULL OR expire_time >= CURRENT_DATE)
                   AND config_type = ? AND config_key = ?
                """);
        sql.append(" AND (");
        List<String> parts = new ArrayList<>();
        for (ScopeCandidate scope : scopes) {
            parts.add("(scope_type = ? AND scope_id = ?)");
            args.add(scope.scopeType());
            args.add(scope.scopeId());
        }
        sql.append(String.join(" OR ", parts)).append(") ORDER BY config_id");
        List<ConfigHitCandidate> candidates = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String scopeType = rs.getString("scope_type"), scopeId = rs.getString("scope_id");
            return new ConfigHitCandidate(rs.getLong("config_id"), rs.getString("config_type"),
                    configTypeName(rs.getString("config_type")),
                    "global".equals(scopeType) && "default".equals(scopeId) ? "默认范围" : sourceLevelName(scopeType),
                    priorityOf(scopeType, scopeId, scopes), scopeType, scopeId, rs.getString("config_key"),
                    configName(rs.getString("config_key")), rs.getString("config_value"),
                    rs.getString("effective_time"), rs.getString("expire_time") == null ? "-" : rs.getString("expire_time"),
                    rs.getString("risk_level"), "有效候选", false, false);
        }, args.toArray()).stream().sorted(Comparator.comparing(ConfigHitCandidate::priority)
                .thenComparing(ConfigHitCandidate::configId)).toList();

        // Different keys never compete. Multiple active candidates at one rank are ambiguous, not inheritance.
        Set<Integer> conflictingPriorities = candidates.stream()
                .collect(Collectors.groupingBy(ConfigHitCandidate::priority, Collectors.counting())).entrySet().stream()
                .filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        boolean conflict = !conflictingPriorities.isEmpty();
        ConfigHitCandidate hit = conflict || candidates.isEmpty() ? null : candidates.get(0);
        ConfigHitCandidate parent = hit == null ? null : candidates.stream()
                .filter(row -> row.priority() > hit.priority()).findFirst().orElse(null);
        String reason = conflict ? "同一配置键存在同级生效冲突，请在系统配置中核对，当前不判定最终值"
                : hit == null ? "当前配置键在此上下文没有启用且有效的配置"
                : parent == null ? "命中同一配置键的最细范围，无继承配置"
                : "命中同一配置键的最细范围，并覆盖更粗范围配置";
        List<ConfigHitCandidate> marked = candidates.stream().map(row -> {
            boolean rowConflict = conflictingPriorities.contains(row.priority());
            boolean isHit = hit != null && row.configId().equals(hit.configId());
            boolean overridden = hit != null && row.priority() > hit.priority();
            String rowReason = rowConflict ? "同一配置键的同级有效候选不唯一"
                    : conflict ? "存在同级冲突，暂不判定覆盖关系"
                    : isHit ? "同一配置键中优先级最高的有效候选"
                    : "被同一配置键更细范围的配置覆盖";
            return new ConfigHitCandidate(row.configId(), row.configType(), row.configTypeName(), row.sourceLevel(),
                    row.priority(), row.scopeType(), row.scopeId(), row.configKey(), row.configName(), row.configValue(),
                    row.effectiveTime(), row.expireTime(), row.riskLevel(), rowConflict ? "配置冲突" : "有效候选",
                    isHit, overridden, rowConflict, rowReason);
        }).toList();
        return new ConfigHitExplanation(defaultText(params.get("businessPage"), "配置核对"), configType,
                hit == null ? "-" : hit.configValue(), hit == null ? "-" : hit.sourceLevel(),
                hit == null ? null : hit.configId(), hit == null ? "-" : hit.scopeType(),
                hit == null ? "-" : hit.scopeId(), hit == null ? "-" : hit.effectiveTime(),
                hit == null ? "-" : hit.expireTime(), parent != null, parent == null ? "-" : parent.configValue(),
                "-", conflict ? "配置冲突" : hit == null ? "未命中" : "已匹配", marked,
                List.of("仅同一配置键参与范围优先级比较，不同配置项互不覆盖",
                        "用户级 > 模块级 > 仓库级 > 科室级 > 院区级 > 医院级 > 租户级 > 系统专用范围 > 默认范围",
                        "只有启用、已到生效时间且未过期的记录参与匹配",
                        "同一配置键的同级有效候选冲突时不选择最终值；只有更粗范围才构成继承"),
                configKey, parent == null ? null : parent.configId(), parent == null ? "-" : parent.sourceLevel(),
                conflict, reason, executionNote(configType, configKey));
    }

    private static String executionNote(String configType, String configKey) {
        if ("integration".equals(configType) && "integration.mcp-ui.connection".equals(configKey)) {
            return "该配置供 MCP-UI 对接使用；本页显示范围匹配结果，实际连接状态以对接功能为准。";
        }
        if ("parameter".equals(configType) && "warning.stock.threshold".equals(configKey)) {
            return "工作台读取系统预警配置；本页显示范围匹配结果，实际预警以工作台为准。";
        }
        return "该配置键当前未接入业务执行；本页仅显示配置记录匹配结果，不控制结算时点或审批进度。";
    }

    private void ensureDefaultConfigs() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM system_config", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        Object[][] rows = {
                {"base", "hospital", "default", "hospital.info", "{\"hospitalName\":\"第一人民医院\",\"hospitalLevel\":\"三级甲等\",\"hospitalCode\":\"H-SPD-001\",\"serviceMode\":\"托管\",\"serviceScope\":\"耗材,试剂,设备\"}", "realtime", "low"},
                {"workflow", "global", "product_catalog", "approval.product.admission", "{\"nodes\":\"运营管理员初审->质控复审->院领导终审\",\"enabled\":true}", "realtime", "high"},
                {"workflow", "global", "purchase_order", "approval.purchase.order", "{\"nodes\":\"采购发起->运营审核->院领导审批(>50万)\",\"amountThreshold\":500000}", "realtime", "high"},
                {"permission", "global", "menu", "permission.button.control", "{\"enabled\":true,\"mode\":\"role+button\"}", "realtime", "medium"},
                {"parameter", "global", "warning", "warning.stock.threshold", "{\"lowStockDays\":7,\"expiryWarningDays\":30,\"coldChainAlert\":true}", "realtime", "medium"},
                {"audit", "global", "log", "audit.retention.days", "{\"operationLogDays\":365,\"loginLogDays\":180,\"exceptionLogDays\":365}", "realtime", "low"},
                {"data", "global", "backup", "data.backup.policy", "{\"frequency\":\"daily\",\"retentionCopies\":30,\"backupTime\":\"02:00\"}", "scheduled", "medium"},
                {"integration", "global", "his", "integration.his.sync", "{\"enabled\":true,\"direction\":\"inbound+outbound\",\"timeoutSeconds\":30}", "realtime", "high"}
        };
        for (Object[] row : rows) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO system_config (
                      config_type, scope_type, scope_id, config_key, config_value,
                      effective_mode, risk_level, status
                    ) VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?, 1)
                    """, row);
        }
    }

    private static void validate(SystemConfigRequest request) {
        if (isBlank(request.configType()) || isBlank(request.scopeType()) || isBlank(request.scopeId()) ||
                isBlank(request.configKey()) || isBlank(request.configValue())) {
            throw new IllegalArgumentException("配置类型、适用范围、适用对象、配置键、配置值为必填项");
        }
        normalizeJson(request.configValue());
    }

    private static String normalizeJson(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }
        return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String configTypeName(String type) {
        return switch (type) {
            case "base" -> "基础配置";
            case "workflow" -> "业务流程配置";
            case "permission" -> "权限配置";
            case "parameter" -> "参数配置";
            case "audit" -> "日志审计";
            case "data" -> "数据管理";
            case "integration" -> "系统集成";
            default -> type;
        };
    }

    private static String configName(String key) {
        return switch (key) {
            case "integration.mcp-ui.connection" -> "MCP-UI对接地址";
            case "hospital.info" -> "医院信息配置";
            case "approval.product.admission" -> "商品准入审批流程";
            case "approval.purchase.order" -> "采购订单审批流程";
            case "permission.button.control" -> "按钮权限控制";
            case "warning.stock.threshold" -> "预警参数";
            case "audit.retention.days" -> "日志留存策略";
            case "data.backup.policy" -> "数据备份策略";
            case "integration.his.sync" -> "HIS接口同步配置";
            default -> key;
        };
    }

    private static void validateMcpConnection(SystemConfigRequest request) {
        if (!com.hospital.spd.integration.McpConnectionAddress.CONFIG_KEY.equals(request.configKey().trim())) return;
        if (!"integration".equals(request.configType()) || !"global".equals(request.scopeType()) || !"default".equals(request.scopeId()) || !"realtime".equals(request.effectiveMode()))
            throw new IllegalArgumentException("MCP-UI连接必须使用全局默认范围和实时生效模式");
        try {
            var value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.configValue());
            com.hospital.spd.integration.McpConnectionAddress.apiUrl(value);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalArgumentException("MCP-UI连接配置必须为合法JSON");}
    }

    private static String sourceLevelName(String scopeType) {
        return switch (scopeType) {
            case "user" -> "用户级";
            case "module" -> "模块级";
            case "warehouse" -> "仓库级";
            case "department" -> "科室级";
            case "campus" -> "院区级";
            case "hospital" -> "医院级";
            case "tenant" -> "租户/集团级";
            case "global" -> "系统级";
            default -> scopeType;
        };
    }

    private static List<ScopeCandidate> buildScopes(Map<String, String> params) {
        List<ScopeCandidate> scopes = new ArrayList<>();
        addScope(scopes, 1, "user", params.get("userId"));
        addScope(scopes, 2, "module", params.get("moduleId"));
        addScope(scopes, 3, "warehouse", params.get("warehouseId"));
        addScope(scopes, 4, "department", params.get("departmentId"));
        addScope(scopes, 5, "campus", params.get("campusId"));
        addScope(scopes, 6, "hospital", params.get("hospitalId"));
        addScope(scopes, 7, "tenant", params.get("tenantId"));
        addScope(scopes, 9, "global", "default");
        addScope(scopes, 8, "global", params.get("moduleId"));
        addScope(scopes, 8, "global", "warning");
        addScope(scopes, 8, "global", "menu");
        addScope(scopes, 8, "global", "log");
        addScope(scopes, 8, "global", "backup");
        addScope(scopes, 8, "global", "his");
        return scopes;
    }

    private static void addScope(List<ScopeCandidate> scopes, int priority, String scopeType, String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return;
        }
        scopes.add(new ScopeCandidate(priority, scopeType, scopeId.trim()));
    }

    private static int priorityOf(String scopeType, String scopeId, List<ScopeCandidate> scopes) {
        return scopes.stream()
                .filter(scope -> scope.scopeType().equals(scopeType) && scope.scopeId().equals(scopeId))
                .map(ScopeCandidate::priority)
                .findFirst()
                .orElse(99);
    }

    private record ScopeCandidate(Integer priority, String scopeType, String scopeId) {
    }
}
