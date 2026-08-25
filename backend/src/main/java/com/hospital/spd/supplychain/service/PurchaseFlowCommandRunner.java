package com.hospital.spd.supplychain.service;

import static com.hospital.spd.common.SqlHelper.nullIfBlank;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.PurchaseOrderActionRequest;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Runs purchase demand, plan, and order action commands through one flow seam.
 */
final class PurchaseFlowCommandRunner {

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final BiFunction<String, Map<String, Object>, String> orderFromPlanFactory;
    private final ApprovalFlowGuard approvalFlowGuard;

    PurchaseFlowCommandRunner(JdbcTemplate jdbcTemplate, BiFunction<String, Map<String, Object>, String> orderFromPlanFactory) {
        this(jdbcTemplate, OperatorContext::system, orderFromPlanFactory, new ApprovalFlowGuard(jdbcTemplate));
    }

    PurchaseFlowCommandRunner(JdbcTemplate jdbcTemplate,
                              OperatorContextProvider operatorContextProvider,
                              BiFunction<String, Map<String, Object>, String> orderFromPlanFactory) {
        this(jdbcTemplate, operatorContextProvider, orderFromPlanFactory, new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider));
    }

    PurchaseFlowCommandRunner(JdbcTemplate jdbcTemplate,
                              OperatorContextProvider operatorContextProvider,
                              BiFunction<String, Map<String, Object>, String> orderFromPlanFactory,
                              ApprovalFlowGuard approvalFlowGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.orderFromPlanFactory = orderFromPlanFactory;
        this.approvalFlowGuard = approvalFlowGuard;
    }

    Map<String, Object> runOrderAction(String orderNo, PurchaseOrderActionRequest request) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT po.purchase_order_id AS orderId, po.order_status AS orderStatus,
                       COALESCE(SUM(poi.quantity), 0) AS orderQuantity,
                       COALESCE(SUM(poi.received_quantity), 0) AS receivedQuantity,
                       po.create_by AS createBy
                  FROM purchase_order po
                  LEFT JOIN purchase_order_item poi ON poi.purchase_order_id = po.purchase_order_id
                 WHERE po.order_no = ?
                 GROUP BY po.purchase_order_id
                 FOR UPDATE
                """, orderNo);
        Long orderId = ((Number) order.get("orderId")).longValue();
        OperatorContext operator = operatorContextProvider.current();
        String action = normalizeAction(request.action());
        requireApprovalFlowForAction(action, "order-approval", null, number(order.get("createBy")));
        String nextStatus = PurchaseFlowRules.nextOrderStatus(
                String.valueOf(order.get("orderStatus")),
                action,
                (BigDecimal) order.get("orderQuantity"),
                (BigDecimal) order.get("receivedQuantity"),
                request.opinion()
        );
        jdbcTemplate.update("""
                UPDATE purchase_order
                   SET order_status = ?,
                        approve_by = CASE WHEN ? IN ('approved', 'rejected') THEN ? ELSE approve_by END,
                       approve_time = CASE WHEN ? IN ('approved', 'rejected') THEN NOW() ELSE approve_time END,
                       send_time = CASE WHEN ? = 'sent' THEN NOW() ELSE send_time END,
                       close_time = CASE WHEN ? IN ('closed', 'voided') THEN NOW() ELSE close_time END,
                       close_reason = CASE WHEN ? IN ('closed', 'voided') THEN ? ELSE close_reason END
                 WHERE order_no = ? AND order_status = ?
                """, nextStatus, nextStatus, operator.userId(), nextStatus, nextStatus, nextStatus, nextStatus,
                nullIfBlank(request.opinion()), orderNo, order.get("orderStatus"));
        appendTracking(orderId, action, nextStatus, nullIfBlank(request.opinion()));
        writeAudit(action, orderId, orderNo, request.opinion());
        return result("orderNo", orderNo, nextStatus);
    }

    Map<String, Object> runDemandAction(String demandNo, PurchaseOrderActionRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT demand_id AS demandId, demand_status AS demandStatus,
                       dept_id AS deptId
                 FROM purchase_demand
                 WHERE demand_no = ?
                 FOR UPDATE
                """, demandNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("purchase demand not found");
        }
        requireApprovalFlowForAction(request.action(), "demand-review",
                number(rows.get(0).get("deptId")), null);
        String nextStatus = PurchaseFlowRules.nextDemandStatus(
                String.valueOf(rows.get(0).get("demandStatus")),
                request.action()
        );
        jdbcTemplate.update("""
                UPDATE purchase_demand
                   SET demand_status = ?,
                       approved_quantity = CASE WHEN ? = 'approved' THEN quantity ELSE approved_quantity END,
                       approve_time = CASE WHEN ? IN ('approved', 'rejected') THEN NOW() ELSE approve_time END
                 WHERE demand_no = ? AND demand_status = ?
                """, nextStatus, nextStatus, nextStatus, demandNo, rows.get(0).get("demandStatus"));
        return result("demandNo", demandNo, nextStatus);
    }

    Map<String, Object> runPlanAction(String planNo, PurchaseOrderActionRequest request) {
        Map<String, Object> plan = jdbcTemplate.queryForMap("""
                SELECT plan_id AS planId, plan_status AS planStatus, supplier_id AS supplierId,
                       product_id AS productId, planned_quantity AS plannedQuantity
                 FROM purchase_plan
                 WHERE plan_no = ?
                 FOR UPDATE
                """, planNo);
        requireApprovalFlowForAction(request.action(), "plan-approval", null, null);
        String nextStatus = PurchaseFlowRules.nextPlanStatus(String.valueOf(plan.get("planStatus")), request.action());
        if ("approved".equals(nextStatus)) {
            requireSingleChange(jdbcTemplate.update("UPDATE purchase_plan SET plan_status = 'approved', approve_time = NOW() WHERE plan_no = ? AND plan_status = 'draft'", planNo));
            return result("planNo", planNo, nextStatus);
        }
        if ("executed".equals(nextStatus)) {
            String orderNo = orderFromPlanFactory.apply(planNo, plan);
            requireSingleChange(jdbcTemplate.update("UPDATE purchase_plan SET plan_status = 'executed', converted_order_no = ? WHERE plan_no = ? AND plan_status = 'approved'", orderNo, planNo));
            Map<String, Object> result = result("planNo", planNo, nextStatus);
            result.put("orderNo", orderNo);
            return result;
        }
        if ("rejected".equals(nextStatus)) {
            requireSingleChange(jdbcTemplate.update("UPDATE purchase_plan SET plan_status = 'rejected', remark = COALESCE(?, remark) WHERE plan_no = ? AND plan_status = 'draft'",
                    nullIfBlank(request.opinion()), planNo));
            return result("planNo", planNo, nextStatus);
        }
        throw new IllegalArgumentException("purchase plan action is invalid");
    }

    private void appendTracking(Long orderId, String eventType, String eventStatus, String remark) {
        jdbcTemplate.update("""
                INSERT INTO purchase_order_tracking (purchase_order_id, event_type, event_status, remark)
                VALUES (?, ?, ?, ?)
                """, orderId, eventType, eventStatus, remark);
    }

    private void writeAudit(String operationType, Long orderId, String orderNo, String remark) {
        OperatorContext operator = operatorContextProvider.current();
        jdbcTemplate.update("""
                INSERT INTO audit_log (operator_name, operation_type, biz_type, biz_id, after_data, ip_address, remark)
                VALUES (?, ?, 'purchase_order', ?, JSON_OBJECT('orderNo', ?), ?, ?)
                """, operator.username(), operationType, orderId, orderNo, operator.ipAddress(), remark);
    }

    private void requireApprovalFlowForAction(String action, String nodeCode, Long documentDeptId, Long documentOwnerId) {
        String normalizedAction = normalizeAction(action);
        if ("approve".equals(normalizedAction) || "reject".equals(normalizedAction)) {
            approvalFlowGuard.requireApprovalAccess("purchase-management", nodeCode, documentDeptId, documentOwnerId);
        }
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static void requireSingleChange(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("document status changed, please refresh and retry");
        }
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private static Map<String, Object> result(String noKey, String no, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(noKey, no);
        result.put("status", status);
        return result;
    }
}
