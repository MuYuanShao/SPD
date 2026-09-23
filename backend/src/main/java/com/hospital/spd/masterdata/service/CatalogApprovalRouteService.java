package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves and persists the immutable approval route used by one catalog application round. */
@Service
public class CatalogApprovalRouteService {
    static final String CATALOG_FEATURE = "pending-product-catalog";
    static final String INITIAL_NODE = "initial-review";
    static final String FINAL_NODE = "final-review";
    static final String PRICE_FEATURE = "batch-price-adjustment";
    static final String PRICE_NODE = "price-adjustment-approval";

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    public CatalogApprovalRouteService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    public String snapshotRoute(long applicationId, int approvalRound, String applicationType, Long documentDeptId) {
        List<Node> nodes = "价格调整".equals(applicationType)
                ? List.of(new Node(PRICE_FEATURE, PRICE_NODE))
                : List.of(new Node(CATALOG_FEATURE, INITIAL_NODE), new Node(CATALOG_FEATURE, FINAL_NODE));
        List<RouteStep> route = new ArrayList<>();
        int routeOrder = 1;
        for (Node node : nodes) {
            Map<String, Object> flow = selectFlow(node, documentDeptId);
            Long flowId = requiredNumber(flow, "flowId");
            List<Map<String, Object>> steps = jdbcTemplate.queryForList("""
                    SELECT s.step_id AS stepId, s.step_order AS stepOrder, s.step_name AS stepName,
                           s.approver_type AS approverType, s.role_id AS roleId, s.user_id AS userId,
                           s.dept_id AS deptId, s.min_approvals AS minApprovals,
                           s.allow_self_approve AS allowSelfApprove, s.data_scope AS dataScope
                      FROM approval_flow_step s
                     WHERE s.flow_id = ? AND s.status = 1
                     ORDER BY s.step_order, s.step_id
                    """, flowId);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("审批流程未配置有效步骤：" + node.nodeCode());
            }
            for (Map<String, Object> step : steps) {
                route.add(new RouteStep(routeOrder++, node.featureCode(), node.nodeCode(), flowId,
                        requiredNumber(step, "stepId"), integer(step.get("stepOrder"), 1),
                        text(step.get("stepName")), text(step.get("approverType")),
                        number(step.get("roleId")), number(step.get("userId")), number(step.get("deptId")),
                        integer(step.get("minApprovals"), 1), truthy(step.get("allowSelfApprove")),
                        integer(step.get("dataScope"), OperatorContext.DATA_SCOPE_ALL)));
            }
        }
        for (RouteStep step : route) {
            jdbcTemplate.update("""
                    INSERT INTO pending_product_approval_route_step (
                      application_id, approval_round, route_order, feature_code, node_code,
                      flow_id, source_step_id, source_step_order, step_name, approver_type,
                      role_id, user_id, dept_id, min_approvals, allow_self_approve, data_scope, route_status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, applicationId, approvalRound, step.routeOrder(), step.featureCode(), step.nodeCode(),
                    step.flowId(), step.sourceStepId(), step.sourceStepOrder(), step.stepName(), step.approverType(),
                    step.roleId(), step.userId(), step.deptId(), step.minApprovals(),
                    step.allowSelfApprove() ? 1 : 0, step.dataScope(),
                    step.routeOrder() == 1 ? "pending" : "waiting");
        }
        return "pending_step_1";
    }

    /** Only pre-snapshot rounds may continue using the original dynamic workflow. */
    public boolean isLegacyDynamicRoute(long applicationId, int approvalRound, String status) {
        if (status == null || !status.matches("pending_step_[1-9][0-9]*")) return false;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_product_application a
                 WHERE a.application_id = ? AND a.approval_round = ?
                   AND a.submit_time < (SELECT MIN(installed_on) FROM flyway_schema_history
                                        WHERE version = '71' AND success = 1)
                   AND NOT EXISTS (SELECT 1 FROM pending_product_approval_route_step r
                                    WHERE r.application_id = a.application_id
                                      AND r.approval_round = a.approval_round)
                """, Integer.class, applicationId, approvalRound);
        return count != null && count == 1;
    }

    /** Lazily freezes a safe route for pre-V71 applications. */
    public void ensureLegacyRoute(long applicationId, int approvalRound, String applicationType,
                                  String currentStatus, Long documentDeptId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_product_approval_route_step
                 WHERE application_id = ? AND approval_round = ?
                """, Integer.class, applicationId, approvalRound);
        if (count != null && count > 0) return;
        if (currentStatus != null && currentStatus.startsWith("pending_step_")) {
            throw new IllegalArgumentException("历史审批步骤无法安全映射，请管理员处理审批路由");
        }
        snapshotRoute(applicationId, approvalRound, applicationType, documentDeptId);
        if ("pending_final".equals(currentStatus)) {
            jdbcTemplate.update("""
                    UPDATE pending_product_approval_route_step
                       SET route_status = 'completed', complete_time = NOW()
                     WHERE application_id = ? AND approval_round = ? AND node_code = ?
                    """, applicationId, approvalRound, INITIAL_NODE);
            jdbcTemplate.update("""
                    UPDATE pending_product_approval_route_step
                       SET route_status = CASE
                         WHEN route_order = (SELECT first_final FROM (SELECT MIN(route_order) first_final
                           FROM pending_product_approval_route_step WHERE application_id = ? AND approval_round = ?
                             AND node_code = ?) x) THEN 'pending' ELSE 'waiting' END,
                           complete_time = NULL
                     WHERE application_id = ? AND approval_round = ? AND node_code = ?
                    """, applicationId, approvalRound, FINAL_NODE,
                    applicationId, approvalRound, FINAL_NODE);
        } else if (!"pending_initial".equals(currentStatus)) {
            throw new IllegalArgumentException("当前审批状态无法生成兼容路由");
        }
    }

    public RouteSnapshotStep currentStep(long applicationId, int approvalRound) {
        return jdbcTemplate.queryForObject("""
                SELECT route_order, feature_code, node_code, flow_id, source_step_id, step_name,
                       approver_type, role_id, user_id, dept_id, min_approvals,
                       allow_self_approve, data_scope
                  FROM pending_product_approval_route_step
                 WHERE application_id = ? AND approval_round = ? AND route_status = 'pending'
                 ORDER BY route_order LIMIT 1
                """, (rs, rowNum) -> new RouteSnapshotStep(
                rs.getInt("route_order"), rs.getString("feature_code"), rs.getString("node_code"),
                rs.getLong("flow_id"), rs.getLong("source_step_id"), rs.getString("step_name"),
                rs.getString("approver_type"), nullableLong(rs, "role_id"), nullableLong(rs, "user_id"),
                nullableLong(rs, "dept_id"), rs.getInt("min_approvals"),
                rs.getBoolean("allow_self_approve"), rs.getInt("data_scope")), applicationId, approvalRound);
    }

    public List<ConfiguredApprovalStep> configuredSteps(long applicationId, int approvalRound) {
        return jdbcTemplate.query("""
                SELECT route_order, step_name, min_approvals
                  FROM pending_product_approval_route_step
                 WHERE application_id = ? AND approval_round = ?
                 ORDER BY route_order
                """, (rs, rowNum) -> new ConfiguredApprovalStep(
                rs.getInt("route_order"), rs.getString("step_name"), rs.getInt("min_approvals")),
                applicationId, approvalRound);
    }

    public void requireApprovalAccess(RouteSnapshotStep step, Long documentDeptId, Long documentOwnerId) {
        OperatorContext operator = operatorContextProvider.current();
        List<String> roles = operator.roles() == null ? List.of() : operator.roles().stream()
                .map(CatalogApprovalRouteService::normalizeRole).toList();
        if (roles.contains("admin") || roles.contains("system")
                || "admin".equalsIgnoreCase(operator.username()) || "system".equalsIgnoreCase(operator.username())) return;
        if (!step.allowSelfApprove() && Objects.equals(operator.userId(), documentOwnerId)) {
            throw new IllegalArgumentException("当前审批节点不允许自审");
        }
        if (!dataScopeAllows(step.dataScope(), documentDeptId, documentOwnerId, operator)) {
            throw new IllegalArgumentException("当前用户无权审批该节点数据");
        }
        boolean allowed = switch (step.approverType()) {
            case "user" -> Objects.equals(step.userId(), operator.userId());
            case "dept_manager" -> operator.deptId() != null
                    && (step.deptId() == null || Objects.equals(step.deptId(), operator.deptId()))
                    && (documentDeptId == null || Objects.equals(documentDeptId, operator.deptId()));
            default -> step.roleId() != null && roleCode(step.roleId()).map(roles::contains).orElse(false);
        };
        if (!allowed) throw new IllegalArgumentException("当前用户无权审批该节点");
    }

    /** Completes current route step and activates the next one. Empty means final approval. */
    public Optional<Integer> completeAndAdvance(long applicationId, int approvalRound, int routeOrder) {
        jdbcTemplate.update("""
                UPDATE pending_product_approval_route_step SET route_status = 'completed', complete_time = NOW()
                 WHERE application_id = ? AND approval_round = ? AND route_order = ? AND route_status = 'pending'
                """, applicationId, approvalRound, routeOrder);
        List<Integer> next = jdbcTemplate.queryForList("""
                SELECT route_order FROM pending_product_approval_route_step
                 WHERE application_id = ? AND approval_round = ? AND route_order > ? AND route_status = 'waiting'
                 ORDER BY route_order LIMIT 1
                """, Integer.class, applicationId, approvalRound, routeOrder);
        if (next.isEmpty()) return Optional.empty();
        jdbcTemplate.update("""
                UPDATE pending_product_approval_route_step SET route_status = 'pending'
                 WHERE application_id = ? AND approval_round = ? AND route_order = ? AND route_status = 'waiting'
                """, applicationId, approvalRound, next.get(0));
        return Optional.of(next.get(0));
    }

    public void terminate(long applicationId, int approvalRound, int routeOrder, String status) {
        jdbcTemplate.update("""
                UPDATE pending_product_approval_route_step SET route_status = ?, complete_time = NOW()
                 WHERE application_id = ? AND approval_round = ? AND route_order = ? AND route_status = 'pending'
                """, status, applicationId, approvalRound, routeOrder);
    }

    private Optional<String> roleCode(Long roleId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT role_code FROM sys_role WHERE role_id = ? AND status = 1 AND deleted = 0",
                String.class, roleId);
        return values.stream().findFirst().map(CatalogApprovalRouteService::normalizeRole);
    }

    private static boolean dataScopeAllows(int scope, Long documentDeptId, Long documentOwnerId, OperatorContext operator) {
        if (scope <= OperatorContext.DATA_SCOPE_ALL) return true;
        if (scope == OperatorContext.DATA_SCOPE_SELF) return Objects.equals(documentOwnerId, operator.userId());
        return documentDeptId != null && Objects.equals(documentDeptId, operator.deptId());
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record RouteSnapshotStep(int routeOrder, String featureCode, String nodeCode, Long flowId,
                                    Long sourceStepId, String stepName, String approverType,
                                    Long roleId, Long userId, Long deptId, int minApprovals,
                                    boolean allowSelfApprove, int dataScope) {}

    private Map<String, Object> selectFlow(Node node, Long documentDeptId) {
        List<Map<String, Object>> flows = jdbcTemplate.queryForList("""
                SELECT flow_id AS flowId, scope_type AS scopeType, scope_id AS scopeId, dept_id AS deptId
                  FROM approval_flow
                 WHERE feature_code = ? AND node_code = ? AND status = 1 AND deleted = 0
                """, node.featureCode(), node.nodeCode());
        OperatorContext operator = operatorContextProvider.current();
        List<String> roles = operator.roles() == null ? List.of() : operator.roles().stream()
                .map(CatalogApprovalRouteService::normalizeRole).toList();
        List<Map<String, Object>> matches = flows.stream()
                .filter(flow -> applies(flow, documentDeptId, operator.deptId(), roles))
                .sorted(Comparator.comparingInt(CatalogApprovalRouteService::specificity).reversed())
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("当前节点未配置或未启用审批流程：" + node.nodeCode());
        }
        int best = specificity(matches.get(0));
        List<Map<String, Object>> bestMatches = matches.stream().filter(flow -> specificity(flow) == best).toList();
        if (bestMatches.size() != 1) {
            throw new IllegalArgumentException("审批流程配置冲突：" + node.nodeCode());
        }
        return bestMatches.get(0);
    }

    private static boolean applies(Map<String, Object> flow, Long documentDeptId, Long operatorDeptId, List<String> roles) {
        String type = text(flow.get("scopeType"));
        if (type == null || "global".equals(type)) return true;
        if ("department".equals(type)) {
            Long configured = number(flow.get("deptId"));
            if (configured == null) configured = parseLong(text(flow.get("scopeId")));
            return Objects.equals(configured, documentDeptId) || Objects.equals(configured, operatorDeptId);
        }
        return "role".equals(type) && roles.contains(normalizeRole(text(flow.get("scopeId"))));
    }

    private static int specificity(Map<String, Object> flow) {
        return switch (String.valueOf(flow.get("scopeType"))) {
            case "department" -> 3;
            case "role" -> 2;
            default -> 1;
        };
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return role.startsWith("role_") ? role.substring(5) : role;
    }

    private static Long requiredNumber(Map<String, Object> row, String key) {
        Long result = number(row.get(key));
        if (result == null) throw new IllegalArgumentException("审批配置缺少字段：" + key);
        return result;
    }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static Long parseLong(String value) { try { return value == null ? null : Long.parseLong(value); } catch (NumberFormatException e) { return null; } }
    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static boolean truthy(Object value) { return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() == 1; }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }

    private record Node(String featureCode, String nodeCode) {}
    private record RouteStep(int routeOrder, String featureCode, String nodeCode, Long flowId,
                             Long sourceStepId, int sourceStepOrder, String stepName, String approverType,
                             Long roleId, Long userId, Long deptId, int minApprovals,
                             boolean allowSelfApprove, int dataScope) {}
}
