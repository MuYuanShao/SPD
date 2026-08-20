package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.ApprovalFlowNodeOption;
import com.hospital.spd.system.ApprovalFlowRequest;
import com.hospital.spd.system.ApprovalFlowRow;
import com.hospital.spd.system.ApprovalFlowStepRequest;
import com.hospital.spd.system.ApprovalFlowStepRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApprovalFlowService {
    private static final List<ApprovalFlowNodeOption> DEFAULT_NODES = List.of(
            new ApprovalFlowNodeOption("pending-product-catalog", "待审批目录", "initial-review", "目录初审"),
            new ApprovalFlowNodeOption("pending-product-catalog", "待审批目录", "final-review", "目录终审"),
            new ApprovalFlowNodeOption("purchase-management", "采购管理", "demand-review", "采购需求审核"),
            new ApprovalFlowNodeOption("purchase-management", "采购管理", "plan-approval", "采购计划审批"),
            new ApprovalFlowNodeOption("purchase-management", "采购管理", "order-approval", "采购订单审批"),
            new ApprovalFlowNodeOption("receiving-acceptance", "收货验收", "receiving-approval", "收货验收审批"),
            new ApprovalFlowNodeOption("department-requisition", "科室申领", "requisition-approval", "申领审批"),
            new ApprovalFlowNodeOption("stocktaking-management", "盘点管理", "stocktaking-approval", "盘点审批"),
            new ApprovalFlowNodeOption("batch-price-adjustment", "价格调整", "price-adjustment-approval", "调价审批"),
            new ApprovalFlowNodeOption("settlement-reconciliation", "结算对账", "settlement-confirm", "结算确认")
    );

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    @Autowired
    public ApprovalFlowService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    public List<ApprovalFlowRow> list(String featureCode, String keyword, String status) {
        OperatorContext operator = operatorContextProvider.current();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE af.deleted = 0");
        if (featureCode != null && !featureCode.isBlank()) {
            where.append(" AND af.feature_code = ?");
            args.add(featureCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (af.feature_name LIKE ? OR af.node_name LIKE ? OR af.remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND af.status = ?");
            args.add(Integer.parseInt(status));
        }
        appendOperatorScope(where, args, operator);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT af.flow_id AS flowId, af.feature_code AS featureCode, af.feature_name AS featureName,
                       af.node_code AS nodeCode, af.node_name AS nodeName, af.scope_type AS scopeType,
                       af.scope_id AS scopeId, af.data_scope AS dataScope, af.status, af.remark,
                       af.dept_id AS deptId, d.dept_name AS deptName, af.create_by AS createBy,
                       u.real_name AS createByName, DATE_FORMAT(af.update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM approval_flow af
                  LEFT JOIN sys_dept d ON d.dept_id = af.dept_id
                  LEFT JOIN sys_user u ON u.user_id = af.create_by
                """ + where + " ORDER BY af.feature_code, af.node_code, af.scope_type, af.scope_id", args.toArray());

        Map<Long, ApprovalFlowRow> flows = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long flowId = number(row.get("flowId"));
            flows.put(flowId, new ApprovalFlowRow(
                    flowId,
                    text(row.get("featureCode")),
                    text(row.get("featureName")),
                    text(row.get("nodeCode")),
                    text(row.get("nodeName")),
                    text(row.get("scopeType")),
                    text(row.get("scopeId")),
                    integer(row.get("dataScope")),
                    integer(row.get("status")),
                    text(row.get("remark")),
                    number(row.get("deptId")),
                    text(row.get("deptName")),
                    number(row.get("createBy")),
                    text(row.get("createByName")),
                    text(row.get("updateTime")),
                    steps(flowId)
            ));
        }
        return new ArrayList<>(flows.values());
    }

    public Map<String, Object> options() {
        return Map.of(
                "nodes", DEFAULT_NODES,
                "roles", jdbcTemplate.queryForList("""
                        SELECT role_id AS roleId, role_name AS roleName, role_code AS roleCode
                          FROM sys_role
                         WHERE deleted = 0 AND status = 1
                         ORDER BY sort_order, role_id
                        """),
                "users", jdbcTemplate.queryForList("""
                        SELECT user_id AS userId, username, real_name AS realName, dept_id AS deptId
                          FROM sys_user
                         WHERE deleted = 0 AND status = 1
                         ORDER BY user_id
                        """),
                "departments", jdbcTemplate.queryForList("""
                        SELECT dept_id AS deptId, dept_name AS deptName, dept_code AS deptCode
                          FROM sys_dept
                         WHERE deleted = 0 AND status = 1
                         ORDER BY sort_order, dept_id
                        """)
        );
    }

    @Transactional
    public Map<String, Object> create(ApprovalFlowRequest request) {
        validate(request);
        OperatorContext operator = operatorContextProvider.current();
        String featureCode = textRequired(request.featureCode(), "featureCode");
        String featureName = textRequired(request.featureName(), "featureName");
        String nodeCode = textRequired(request.nodeCode(), "nodeCode");
        String nodeName = textRequired(request.nodeName(), "nodeName");
        String scopeType = defaultText(request.scopeType(), "global");
        String scopeId = defaultText(request.scopeId(), "default");
        try {
            jdbcTemplate.update("""
                    INSERT INTO approval_flow
                    (feature_code, feature_name, node_code, node_name, scope_type, scope_id, data_scope, status, remark, dept_id, create_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    featureCode,
                    featureName,
                    nodeCode,
                    nodeName,
                    scopeType,
                    scopeId,
                    nvl(request.dataScope(), OperatorContext.DATA_SCOPE_ALL),
                    nvl(request.status(), 1),
                    emptyToNull(request.remark()),
                    request.deptId() == null ? operator.deptId() : request.deptId(),
                    operator.userId());
            Long flowId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            replaceSteps(flowId, request.steps());
            return Map.of("flowId", flowId);
        } catch (DuplicateKeyException ex) {
            Long existingFlowId = findExistingFlowId(featureCode, nodeCode, scopeType, scopeId);
            if (existingFlowId == null) {
                throw new IllegalArgumentException("审批流配置已存在，请刷新后编辑");
            }
            update(existingFlowId, request);
            return Map.of("flowId", existingFlowId, "updated", 1);
        }
    }

    @Transactional
    public Map<String, Object> update(Long flowId, ApprovalFlowRequest request) {
        validate(request);
        jdbcTemplate.update("""
                UPDATE approval_flow
                   SET feature_code = ?, feature_name = ?, node_code = ?, node_name = ?,
                       scope_type = ?, scope_id = ?, data_scope = ?, status = ?, remark = ?, dept_id = ?
                 WHERE flow_id = ? AND deleted = 0
                """,
                textRequired(request.featureCode(), "featureCode"),
                textRequired(request.featureName(), "featureName"),
                textRequired(request.nodeCode(), "nodeCode"),
                textRequired(request.nodeName(), "nodeName"),
                defaultText(request.scopeType(), "global"),
                defaultText(request.scopeId(), "default"),
                nvl(request.dataScope(), OperatorContext.DATA_SCOPE_ALL),
                nvl(request.status(), 1),
                emptyToNull(request.remark()),
                request.deptId(),
                flowId);
        replaceSteps(flowId, request.steps());
        return Map.of("updated", 1);
    }

    public Map<String, Object> updateStatus(Long flowId, Integer status) {
        jdbcTemplate.update("UPDATE approval_flow SET status = ? WHERE flow_id = ? AND deleted = 0", nvl(status, 1), flowId);
        return Map.of("updated", 1);
    }

    private List<ApprovalFlowStepRow> steps(Long flowId) {
        return jdbcTemplate.queryForList("""
                SELECT s.step_id AS stepId, s.step_order AS stepOrder, s.step_name AS stepName,
                       s.approver_type AS approverType, s.role_id AS roleId, r.role_name AS roleName,
                       s.user_id AS userId, u.real_name AS realName, s.dept_id AS deptId, d.dept_name AS deptName,
                       s.min_approvals AS minApprovals, s.allow_self_approve AS allowSelfApprove,
                       s.data_scope AS dataScope, s.status
                  FROM approval_flow_step s
                  LEFT JOIN sys_role r ON r.role_id = s.role_id
                  LEFT JOIN sys_user u ON u.user_id = s.user_id
                  LEFT JOIN sys_dept d ON d.dept_id = s.dept_id
                 WHERE s.flow_id = ?
                 ORDER BY s.step_order, s.step_id
                """, flowId).stream().map(row -> new ApprovalFlowStepRow(
                number(row.get("stepId")),
                integer(row.get("stepOrder")),
                text(row.get("stepName")),
                text(row.get("approverType")),
                number(row.get("roleId")),
                text(row.get("roleName")),
                number(row.get("userId")),
                text(row.get("realName")),
                number(row.get("deptId")),
                text(row.get("deptName")),
                integer(row.get("minApprovals")),
                Boolean.TRUE.equals(row.get("allowSelfApprove")) || Integer.valueOf(1).equals(integer(row.get("allowSelfApprove"))),
                integer(row.get("dataScope")),
                integer(row.get("status"))
        )).toList();
    }

    private Long findExistingFlowId(String featureCode, String nodeCode, String scopeType, String scopeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT flow_id AS flowId
                  FROM approval_flow
                 WHERE feature_code = ? AND node_code = ? AND scope_type = ? AND scope_id = ? AND deleted = 0
                 LIMIT 1
                """, featureCode, nodeCode, scopeType, scopeId);
        return rows.isEmpty() ? null : number(rows.get(0).get("flowId"));
    }

    private void replaceSteps(Long flowId, List<ApprovalFlowStepRequest> steps) {
        jdbcTemplate.update("DELETE FROM approval_flow_step WHERE flow_id = ?", flowId);
        List<ApprovalFlowStepRequest> safeSteps = steps == null || steps.isEmpty()
                ? List.of(new ApprovalFlowStepRequest(null, 1, "一级审批", "role", null, null, null, 1, false, OperatorContext.DATA_SCOPE_ALL, 1))
                : steps;
        int fallbackOrder = 1;
        for (ApprovalFlowStepRequest step : safeSteps) {
            int order = step.stepOrder() == null ? fallbackOrder : step.stepOrder();
            jdbcTemplate.update("""
                    INSERT INTO approval_flow_step
                    (flow_id, step_order, step_name, approver_type, role_id, user_id, dept_id, min_approvals, allow_self_approve, data_scope, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    flowId,
                    order,
                    textRequired(step.stepName(), "stepName"),
                    defaultText(step.approverType(), "role"),
                    step.roleId(),
                    step.userId(),
                    step.deptId(),
                    nvl(step.minApprovals(), 1),
                    Boolean.TRUE.equals(step.allowSelfApprove()) ? 1 : 0,
                    nvl(step.dataScope(), OperatorContext.DATA_SCOPE_ALL),
                    nvl(step.status(), 1));
            fallbackOrder++;
        }
    }

    private static void appendOperatorScope(StringBuilder where, List<Object> args, OperatorContext operator) {
        if (operator.canViewAllData()) {
            return;
        }
        where.append(" AND (af.scope_type = 'global'");
        if (operator.deptId() != null) {
            where.append(" OR af.dept_id = ?");
            args.add(operator.deptId());
        }
        if (operator.userId() != null) {
            where.append(" OR af.create_by = ?");
            args.add(operator.userId());
        }
        where.append(")");
    }

    private static void validate(ApprovalFlowRequest request) {
        textRequired(request.featureCode(), "featureCode");
        textRequired(request.featureName(), "featureName");
        textRequired(request.nodeCode(), "nodeCode");
        textRequired(request.nodeName(), "nodeName");
        validateSteps(request.steps());
    }

    private static void validateSteps(List<ApprovalFlowStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        Set<Integer> orders = new HashSet<>();
        for (ApprovalFlowStepRequest step : steps) {
            int order = nvl(step.stepOrder(), 0);
            if (order < 1) {
                throw new IllegalArgumentException("审批顺序必须填写大于 0 的整数");
            }
            if (!orders.add(order)) {
                throw new IllegalArgumentException("审批顺序不能重复，请调整后再保存");
            }
            textRequired(step.stepName(), "stepName");
            String approverType = defaultText(step.approverType(), "role");
            if ("role".equals(approverType) && step.roleId() == null) {
                throw new IllegalArgumentException("按角色审批时必须选择角色");
            }
            if ("user".equals(approverType) && step.userId() == null) {
                throw new IllegalArgumentException("指定人员审批时必须选择人员");
            }
        }
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String textRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer nvl(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
