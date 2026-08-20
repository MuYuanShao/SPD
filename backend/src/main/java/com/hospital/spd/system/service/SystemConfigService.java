package com.hospital.spd.system.service;

import static com.hospital.spd.common.SqlHelper.*;
import com.hospital.spd.system.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    public Map<String, Object> create(SystemConfigRequest request) {
        validate(request);
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
        return Map.of("configKey", request.configKey().trim());
    }

    public Map<String, Object> update(Long configId, SystemConfigRequest request) {
        validate(request);
        int updatedRows = jdbcTemplate.update("""
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
        return Map.of("updatedRows", updatedRows, "configId", configId);
    }

    public Map<String, Object> updateStatus(Long configId, int status) {
        int updatedRows = jdbcTemplate.update("UPDATE system_config SET status = ? WHERE config_id = ? AND deleted = 0", status, configId);
        return Map.of("updatedRows", updatedRows, "configId", configId);
    }

    public ConfigHitExplanation explain(Map<String, String> params) {
        String configType = defaultText(params.get("configType"), "workflow");
        List<ScopeCandidate> scopes = buildScopes(params);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT config_id, config_type, scope_type, scope_id, config_key,
                       JSON_UNQUOTE(config_value) AS config_value,
                       DATE_FORMAT(effective_time, '%Y-%m-%d %H:%i') AS effective_time,
                       DATE_FORMAT(expire_time, '%Y-%m-%d') AS expire_time,
                       COALESCE(risk_level, '-') AS risk_level
                FROM system_config
                WHERE status = 1 AND deleted = 0
                  AND effective_time <= CURRENT_TIMESTAMP
                  AND (expire_time IS NULL OR expire_time >= CURRENT_DATE)
                  AND config_type = ?
                """);
        args.add(configType);

        if (!scopes.isEmpty()) {
            sql.append(" AND (");
            List<String> parts = new ArrayList<>();
            for (ScopeCandidate scope : scopes) {
                parts.add("(scope_type = ? AND scope_id = ?)");
                args.add(scope.scopeType());
                args.add(scope.scopeId());
            }
            sql.append(String.join(" OR ", parts)).append(")");
        }

        List<ConfigHitCandidate> candidates = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            int priority = priorityOf(rs.getString("scope_type"), rs.getString("scope_id"), scopes);
            return new ConfigHitCandidate(
                    rs.getLong("config_id"),
                    rs.getString("config_type"),
                    configTypeName(rs.getString("config_type")),
                    sourceLevelName(rs.getString("scope_type")),
                    priority,
                    rs.getString("scope_type"),
                    rs.getString("scope_id"),
                    rs.getString("config_key"),
                    configName(rs.getString("config_key")),
                    rs.getString("config_value"),
                    rs.getString("effective_time"),
                    rs.getString("expire_time") == null ? "-" : rs.getString("expire_time"),
                    rs.getString("risk_level"),
                    "已生效",
                    false,
                    false
            );
        }, args.toArray()).stream().sorted(Comparator.comparing(ConfigHitCandidate::priority)).toList();

        ConfigHitCandidate hit = candidates.isEmpty() ? null : candidates.get(0);
        ConfigHitCandidate parent = candidates.size() > 1 ? candidates.get(1) : null;
        List<ConfigHitCandidate> marked = candidates.stream()
                .map(row -> new ConfigHitCandidate(
                        row.configId(), row.configType(), row.configTypeName(), row.sourceLevel(), row.priority(),
                        row.scopeType(), row.scopeId(), row.configKey(), row.configName(), row.configValue(),
                        row.effectiveTime(), row.expireTime(), row.riskLevel(), row.approvalStatus(),
                        hit != null && row.configId().equals(hit.configId()),
                        hit != null && !row.configId().equals(hit.configId())
                ))
                .toList();

        return new ConfigHitExplanation(
                defaultText(params.get("businessPage"), "系统配置命中说明"),
                configType,
                hit == null ? "-" : hit.configValue(),
                hit == null ? "-" : hit.sourceLevel(),
                hit == null ? null : hit.configId(),
                hit == null ? "-" : hit.scopeType(),
                hit == null ? "-" : hit.scopeId(),
                hit == null ? "-" : hit.effectiveTime(),
                hit == null ? "-" : hit.expireTime(),
                parent != null,
                parent == null ? "-" : parent.configValue(),
                "admin",
                hit == null ? "未命中" : "已生效",
                marked,
                List.of("用户级 > 模块级 > 仓库级 > 科室级 > 院区级 > 租户级 > 系统级 > 默认值",
                        "业务策略按更细粒度优先命中，上下级配置可同时存在，细粒度覆盖粗粒度",
                        "仅已启用、已到生效时间、未过失效时间的配置参与当前命中")
        );
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
        addScope(scopes, 8, "global", "default");
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
