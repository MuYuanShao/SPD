package com.hospital.spd.supplychain.service;

import static com.hospital.spd.common.SqlHelper.isBlank;

import java.math.BigDecimal;

/**
 * Encapsulates purchase demand, plan, and order status transitions.
 */
public final class PurchaseFlowRules {

    private PurchaseFlowRules() {
    }

    public static String nextOrderStatus(String currentStatus,
                                         String action,
                                         BigDecimal orderQuantity,
                                         BigDecimal receivedQuantity,
                                         String closeReason) {
        return switch (normalizeAction(action)) {
            case "submit" -> requireStatus(currentStatus, "draft", "pending_approval");
            case "approve" -> requireStatus(currentStatus, "pending_approval", "approved");
            case "send" -> requireStatus(currentStatus, "approved", "sent");
            case "close" -> requireCloseStatus(currentStatus, orderQuantity, receivedQuantity, closeReason);
            case "void" -> requireVoidStatus(currentStatus, closeReason);
            case "reject" -> requireStatus(currentStatus, "pending_approval", "rejected");
            default -> throw new IllegalArgumentException("采购订单动作无效");
        };
    }

    public static String nextDemandStatus(String currentStatus, String action) {
        return switch (normalizeAction(action)) {
            case "submit" -> requireStatus(currentStatus, "draft", "pending_review");
            case "approve" -> requireStatus(currentStatus, "pending_review", "approved");
            case "reject" -> requireStatus(currentStatus, "pending_review", "rejected");
            default -> throw new IllegalArgumentException("purchase demand action is invalid");
        };
    }

    public static String nextPlanStatus(String currentStatus, String action) {
        return switch (normalizeAction(action)) {
            case "approve" -> requireStatus(currentStatus, "draft", "approved");
            case "execute" -> requireStatus(currentStatus, "approved", "executed");
            case "reject" -> requireStatus(currentStatus, "draft", "rejected");
            default -> throw new IllegalArgumentException("purchase plan action is invalid");
        };
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private static String requireStatus(String currentStatus, String required, String nextStatus) {
        if (!required.equals(currentStatus)) {
            throw new IllegalArgumentException("当前状态不允许执行该操作");
        }
        return nextStatus;
    }

    private static String requireCloseStatus(String currentStatus,
                                             BigDecimal orderQuantity,
                                             BigDecimal receivedQuantity,
                                             String closeReason) {
        if (!"sent".equals(currentStatus) && !"approved".equals(currentStatus)) {
            throw new IllegalArgumentException("仅已审批或已发送的采购订单允许关闭");
        }
        if (orderQuantity != null && receivedQuantity != null && receivedQuantity.compareTo(orderQuantity) < 0) {
            BigDecimal remaining = orderQuantity.subtract(receivedQuantity);
            throw new IllegalArgumentException("采购订单尚未完成数量履约，采购数量 "
                    + orderQuantity.stripTrailingZeros().toPlainString()
                    + "，已收货 "
                    + receivedQuantity.stripTrailingZeros().toPlainString()
                    + "，剩余 "
                    + remaining.stripTrailingZeros().toPlainString()
                    + "。请先完成收货验收后再关闭。");
        }
        if (isBlank(closeReason)) {
            throw new IllegalArgumentException("关闭采购订单必须填写关闭原因");
        }
        return "closed";
    }

    private static String requireVoidStatus(String currentStatus, String reason) {
        if (!"draft".equals(currentStatus)
                && !"pending_approval".equals(currentStatus)
                && !"approved".equals(currentStatus)) {
            throw new IllegalArgumentException("当前状态不允许作废采购订单");
        }
        if (isBlank(reason)) {
            throw new IllegalArgumentException("作废采购订单必须填写原因");
        }
        return "voided";
    }
}
