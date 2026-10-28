package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Authorizes configured approval participants and advances per-document approval snapshots. */
@Service
public class ApprovalFlowGuard {

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    public ApprovalFlowGuard(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, OperatorContext::system);
    }

    @Autowired
    public ApprovalFlowGuard(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    public void requireApprovalAccess(String featureCode, String nodeCode, Long documentDeptId, Long documentOwnerId) {
        requireApprovalAccess(featureCode, nodeCode, null, documentDeptId, documentOwnerId);
    }

    public boolean hasApprovalAccess(String featureCode,
                                     String nodeCode,
                                     Integer stepOrder,
                                     Long documentDeptId,
                                     Long documentOwnerId) {
        try {
            requireApprovalAccess(featureCode, nodeCode, stepOrder, documentDeptId, documentOwnerId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public void requireApprovalAccess(String featureCode, String nodeCode, Integer stepOrder, Long documentDeptId, Long documentOwnerId) {
        OperatorContext operator = operatorContextProvider.current();
        if (isGlobalApprovalAdmin(operator)) {
            return;
        }
        List<String> roleCodes = normalizedRoleCodes(operator);
        List<Map<String, Object>> flows = jdbcTemplate.queryForList("""
                SELECT flow_id AS flowId, scope_type AS scopeType, scope_id AS scopeId,
                       data_scope AS dataScope, dept_id AS deptId
                  FROM approval_flow
                 WHERE feature_code = ? AND node_code = ? AND status = 1 AND deleted = 0
                """, featureCode, nodeCode);
        List<Map<String, Object>> candidates = flows.stream()
                .filter(flow -> flowApplies(flow, roleCodes, documentDeptId, operator))
                .sorted(Comparator.comparingInt(ApprovalFlowGuard::flowSpecificity).reversed())
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("当前节点未配置或未启用审批流程");
        }
        for (Map<String, Object> flow : candidates) {
            if (!dataScopeAllows(integer(flow.get("dataScope")), documentDeptId, documentOwnerId, operator)) {
                continue;
            }
            if (hasAuthorizedStep(number(flow.get("flowId")), stepOrder, roleCodes, documentDeptId, documentOwnerId, operator)) {
                return;
            }
        }
        throw new IllegalArgumentException("当前用户无权审批该节点");
    }

    public void requireNotStarted(String feature, String node, String documentNo) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_approval_run WHERE document_key = ?", Long.class,
                feature + ":" + node + ":" + documentNo);
        if (count != null && count > 0) throw new IllegalArgumentException("单据已开始审批，不能修改审批内容");
    }

    /** Caller holds its business document lock; votes and downstream effects share the same transaction. */
    public boolean recordDecision(String feature, String node, String documentNo, Long deptId, Long ownerId, boolean approve) {
        OperatorContext operator = operatorContextProvider.current();
        if (isGlobalApprovalAdmin(operator)) return true;
        String key = feature + ":" + node + ":" + documentNo;
        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT current_order AS currentOrder, status, flow_data_scope AS flowDataScope FROM business_approval_run WHERE document_key = ? FOR UPDATE", key);
        if (runs.isEmpty()) {
            List<String> roles = normalizedRoleCodes(operator);
            Map<String, Object> flow = jdbcTemplate.queryForList("""
                    SELECT flow_id AS flowId, scope_type AS scopeType, scope_id AS scopeId,
                           data_scope AS dataScope, dept_id AS deptId
                      FROM approval_flow WHERE feature_code = ? AND node_code = ? AND status = 1 AND deleted = 0
                      ORDER BY flow_id FOR SHARE
                    """, feature, node).stream()
                    .filter(candidate -> flowApplies(candidate, roles, deptId, operator))
                    .filter(candidate -> dataScopeAllows(integer(candidate.get("dataScope")), deptId, ownerId, operator))
                    .sorted(Comparator.comparingInt(ApprovalFlowGuard::flowSpecificity).reversed())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("当前节点未配置适用的审批流程"));
            List<Map<String, Object>> steps = jdbcTemplate.queryForList("""
                    SELECT s.step_order AS stepOrder, s.approver_type AS approverType, r.role_code AS roleCode,
                           s.user_id AS userId, s.dept_id AS deptId, s.min_approvals AS minApprovals,
                           s.allow_self_approve AS allowSelfApprove, s.data_scope AS dataScope
                      FROM approval_flow_step s LEFT JOIN sys_role r ON r.role_id = s.role_id
                     WHERE s.flow_id = ? AND s.status = 1
                     ORDER BY s.step_order, s.step_id FOR SHARE
                    """, flow.get("flowId"));
            if (steps.isEmpty()) throw new IllegalArgumentException("审批流程没有启用步骤");
            int flowScope = integer(flow.get("dataScope")) == null ? OperatorContext.DATA_SCOPE_ALL : integer(flow.get("dataScope"));
            jdbcTemplate.update("INSERT INTO business_approval_run (document_key, current_order, status, flow_data_scope) VALUES (?, ?, 'pending', ?)",
                    key, steps.get(0).get("stepOrder"), flowScope);
            for (Map<String, Object> step : steps) {
                jdbcTemplate.update("""
                        INSERT INTO business_approval_step
                        (document_key, step_order, approver_type, role_code, user_id, dept_id, min_approvals, allow_self_approve, data_scope)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, key, step.get("stepOrder"), step.get("approverType"), step.get("roleCode"), step.get("userId"),
                        step.get("deptId"), step.get("minApprovals"), step.get("allowSelfApprove"), step.get("dataScope"));
            }
            runs = List.of(Map.of("currentOrder", steps.get(0).get("stepOrder"), "status", "pending", "flowDataScope", flowScope));
        }
        Map<String, Object> run = runs.get(0);
        if (!"pending".equals(run.get("status"))) throw new IllegalArgumentException("该单据审批已完成");
        Map<String, Object> step = jdbcTemplate.queryForMap("""
                SELECT approver_type AS approverType, role_code AS roleCode, user_id AS userId,
                       dept_id AS deptId, min_approvals AS minApprovals, allow_self_approve AS allowSelfApprove,
                       data_scope AS dataScope
                  FROM business_approval_step WHERE document_key = ? AND step_order = ?
                """, key, run.get("currentOrder"));
        List<String> roles = normalizedRoleCodes(operator);
        if (!dataScopeAllows(integer(run.get("flowDataScope")), deptId, ownerId, operator)
                || !selfApprovalAllows(step.get("allowSelfApprove"), ownerId, operator, roles)
                || !dataScopeAllows(integer(step.get("dataScope")), deptId, ownerId, operator)
                || !approverAllows(step, roles, deptId, operator)) throw new IllegalArgumentException("当前用户无权审批当前步骤");
        if (!approve) {
            jdbcTemplate.update("UPDATE business_approval_run SET status = 'rejected' WHERE document_key = ?", key);
            return true;
        }
        List<Long> prior = jdbcTemplate.queryForList("SELECT operator_id FROM business_approval_vote WHERE document_key = ? AND step_order = ? AND operator_id = ?",
                Long.class, key, run.get("currentOrder"), operator.userId());
        if (!prior.isEmpty()) throw new IllegalArgumentException("本人已审批当前步骤，请勿重复提交");
        jdbcTemplate.update("INSERT INTO business_approval_vote (document_key, step_order, operator_id) VALUES (?, ?, ?)",
                key, run.get("currentOrder"), operator.userId());
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_approval_vote WHERE document_key = ? AND step_order = ?", Long.class, key, run.get("currentOrder"));
        if (count == null || count < ((Number) step.get("minApprovals")).longValue()) return false;
        List<Integer> next = jdbcTemplate.queryForList("SELECT step_order FROM business_approval_step WHERE document_key = ? AND step_order > ? ORDER BY step_order LIMIT 1",
                Integer.class, key, run.get("currentOrder"));
        if (!next.isEmpty()) {
            jdbcTemplate.update("UPDATE business_approval_run SET current_order = ? WHERE document_key = ?", next.get(0), key);
            return false;
        }
        jdbcTemplate.update("UPDATE business_approval_run SET status = 'approved' WHERE document_key = ?", key);
        return true;
    }

    private boolean hasAuthorizedStep(Long flowId,
                                      Integer stepOrder,
                                      List<String> roleCodes,
                                      Long documentDeptId,
                                      Long documentOwnerId,
                                      OperatorContext operator) {
        List<Object> args = new java.util.ArrayList<>();
        args.add(flowId);
        String stepClause = "";
        if (stepOrder != null) {
            stepClause = " AND s.step_order = ?";
            args.add(stepOrder);
        }
        List<Map<String, Object>> steps = jdbcTemplate.queryForList("""
                SELECT s.approver_type AS approverType, s.role_id AS roleId, r.role_code AS roleCode,
                       s.user_id AS userId, s.dept_id AS deptId, s.allow_self_approve AS allowSelfApprove,
                       s.data_scope AS dataScope
                  FROM approval_flow_step s
                  LEFT JOIN sys_role r ON r.role_id = s.role_id
                 WHERE s.flow_id = ? AND s.status = 1
                 %s
                 ORDER BY s.step_order, s.step_id
                """.formatted(stepClause), args.toArray());
        for (Map<String, Object> step : steps) {
            if (!selfApprovalAllows(step.get("allowSelfApprove"), documentOwnerId, operator, roleCodes)) {
                continue;
            }
            if (!dataScopeAllows(integer(step.get("dataScope")), documentDeptId, documentOwnerId, operator)) {
                continue;
            }
            if (approverAllows(step, roleCodes, documentDeptId, operator)) {
                return true;
            }
        }
        return false;
    }

    private boolean approverAllows(Map<String, Object> step,
                                   List<String> roleCodes,
                                   Long documentDeptId,
                                   OperatorContext operator) {
        String approverType = text(step.get("approverType"));
        if ("user".equals(approverType)) {
            return Objects.equals(number(step.get("userId")), operator.userId());
        }
        if ("dept_manager".equals(approverType)) {
            Long targetDept = number(step.get("deptId")) != null ? number(step.get("deptId")) : documentDeptId;
            return targetDept != null && Objects.equals(targetDept, operator.deptId())
                    && Objects.equals(number(step.get("userId")), operator.userId());
        }
        String roleCode = normalizedRoleCode(text(step.get("roleCode")));
        return roleCode != null && roleCodes.contains(roleCode);
    }

    private boolean dataScopeAllows(Integer dataScope, Long documentDeptId, Long documentOwnerId, OperatorContext operator) {
        int scope = dataScope == null ? OperatorContext.DATA_SCOPE_ALL : dataScope;
        if (scope <= OperatorContext.DATA_SCOPE_ALL) {
            return true;
        }
        if (scope == OperatorContext.DATA_SCOPE_SELF) {
            return documentOwnerId != null && Objects.equals(documentOwnerId, operator.userId());
        }
        if (scope == OperatorContext.DATA_SCOPE_DEPT) {
            return documentDeptId != null && Objects.equals(documentDeptId, operator.deptId());
        }
        if (scope == OperatorContext.DATA_SCOPE_DEPT_AND_CHILDREN) {
            return documentDeptId != null && operator.deptId() != null && isDeptOrChild(documentDeptId, operator.deptId());
        }
        return false;
    }

    private boolean isDeptOrChild(Long documentDeptId, Long operatorDeptId) {
        if (Objects.equals(documentDeptId, operatorDeptId)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE dept_tree AS (
                    SELECT dept_id FROM sys_dept WHERE dept_id = ?
                    UNION ALL
                    SELECT d.dept_id
                      FROM sys_dept d
                      JOIN dept_tree t ON d.parent_id = t.dept_id
                     WHERE d.deleted = 0
                )
                SELECT COUNT(*) FROM dept_tree WHERE dept_id = ?
                """, Integer.class, operatorDeptId, documentDeptId);
        return count != null && count > 0;
    }

    private static boolean selfApprovalAllows(Object allowSelfApprove,
                                              Long documentOwnerId,
                                              OperatorContext operator,
                                              List<String> roleCodes) {
        boolean allowed = Boolean.TRUE.equals(allowSelfApprove)
                || (allowSelfApprove instanceof Number number && number.intValue() == 1);
        return allowed || roleCodes.contains("admin") || documentOwnerId == null || !Objects.equals(documentOwnerId, operator.userId());
    }

    private static boolean flowApplies(Map<String, Object> flow,
                                       List<String> roleCodes,
                                       Long documentDeptId,
                                       OperatorContext operator) {
        String scopeType = text(flow.get("scopeType"));
        String scopeId = text(flow.get("scopeId"));
        if (scopeType == null || "global".equals(scopeType)) {
            return true;
        }
        if ("department".equals(scopeType)) {
            Long scopeDeptId = numberOrText(scopeId);
            Long flowDeptId = number(flow.get("deptId"));
            return (documentDeptId != null && (Objects.equals(scopeDeptId, documentDeptId) || Objects.equals(flowDeptId, documentDeptId)))
                    || (operator.deptId() != null && (Objects.equals(scopeDeptId, operator.deptId()) || Objects.equals(flowDeptId, operator.deptId())));
        }
        if ("role".equals(scopeType)) {
            return scopeId != null && roleCodes.contains(normalizedRoleCode(scopeId));
        }
        return false;
    }

    private static int flowSpecificity(Map<String, Object> flow) {
        String scopeType = text(flow.get("scopeType"));
        if ("department".equals(scopeType)) {
            return 3;
        }
        if ("role".equals(scopeType)) {
            return 2;
        }
        return 1;
    }

    private static boolean isGlobalApprovalAdmin(OperatorContext operator) {
        if (operator == null) {
            return false;
        }
        String username = operator.username() == null ? "" : operator.username().trim().toLowerCase(Locale.ROOT);
        if ("system".equals(username) || "admin".equals(username)) {
            return true;
        }
        List<String> roleCodes = normalizedRoleCodes(operator);
        return roleCodes.contains("system") || roleCodes.contains("admin");
    }

    private static List<String> normalizedRoleCodes(OperatorContext operator) {
        if (operator.roles() == null) {
            return List.of();
        }
        return operator.roles().stream()
                .map(ApprovalFlowGuard::normalizedRoleCode)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String normalizedRoleCode(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String value = role.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("role_") ? value.substring(5) : value;
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long numberOrText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
