package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseFlowRulesTest {

    @Test
    void orderActionsFollowPurchaseLifecycle() {
        assertEquals("pending_approval", PurchaseFlowRules.nextOrderStatus("draft", "submit", null, null, null));
        assertEquals("approved", PurchaseFlowRules.nextOrderStatus("pending_approval", "approve", null, null, null));
        assertEquals("sent", PurchaseFlowRules.nextOrderStatus("approved", "send", null, null, null));
        assertEquals("rejected", PurchaseFlowRules.nextOrderStatus("pending_approval", "reject", null, null, null));
        assertEquals("voided", PurchaseFlowRules.nextOrderStatus("draft", "void", null, null, "录入有误"));
        assertEquals("voided", PurchaseFlowRules.nextOrderStatus("approved", "void", null, null, "采购取消"));
    }

    @Test
    void orderCloseRequiresFulfilledQuantityAndReason() {
        assertEquals("closed", PurchaseFlowRules.nextOrderStatus(
                "sent", "close", BigDecimal.TEN, BigDecimal.TEN, "数量履约完成"));

        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextOrderStatus(
                "sent", "close", BigDecimal.TEN, BigDecimal.ONE, "数量履约完成"));
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextOrderStatus(
                "sent", "close", BigDecimal.TEN, BigDecimal.TEN, ""));
    }

    @Test
    void demandActionsFollowReviewLifecycle() {
        assertEquals("pending_review", PurchaseFlowRules.nextDemandStatus("draft", "submit"));
        assertEquals("approved", PurchaseFlowRules.nextDemandStatus("pending_review", "approve"));
        assertEquals("rejected", PurchaseFlowRules.nextDemandStatus("pending_review", "reject"));
    }

    @Test
    void planActionsFollowExecutionLifecycle() {
        assertEquals("approved", PurchaseFlowRules.nextPlanStatus("draft", "approve"));
        assertEquals("executed", PurchaseFlowRules.nextPlanStatus("approved", "execute"));
        assertEquals("rejected", PurchaseFlowRules.nextPlanStatus("draft", "reject"));
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextOrderStatus(
                "draft", "send", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextOrderStatus(
                "sent", "void", null, null, "已发送不可作废"));
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextOrderStatus(
                "draft", "void", null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextDemandStatus("approved", "reject"));
        assertThrows(IllegalArgumentException.class, () -> PurchaseFlowRules.nextPlanStatus("approved", "reject"));
    }
}
