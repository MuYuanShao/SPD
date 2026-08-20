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
            Long stepDeptId = number(step.get("deptId"));
            if (stepDeptId != null && !Objects.equals(stepDeptId, operator.deptId())) {
                return false;
            }
            return operator.deptId() != null && (documentDeptId == null || Objects.equals(documentDeptId, operator.deptId()));
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
