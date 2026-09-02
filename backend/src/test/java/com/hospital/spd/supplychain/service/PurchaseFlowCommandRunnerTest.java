package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.PurchaseOrderActionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseFlowCommandRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AtomicReference<Map<String, Object>> planSeenByFactory;
    private PurchaseFlowCommandRunner runner;

    @BeforeEach
    void setUp() {
        planSeenByFactory = new AtomicReference<>();
        runner = new PurchaseFlowCommandRunner(jdbcTemplate, (planNo, plan) -> {
            planSeenByFactory.set(Map.of("planNo", planNo, "plan", plan));
            return "CG20260609001";
        });
    }

    @Test
    void orderActionUpdatesStatusAndWritesTrackingAndAudit() {
        when(jdbcTemplate.queryForMap(anyString(), eq("CG001"))).thenReturn(Map.of(
                "orderId", 100L,
                "orderStatus", "pending_approval",
                "orderQuantity", BigDecimal.TEN,
                "receivedQuantity", BigDecimal.ZERO
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = runner.runOrderAction("CG001", new PurchaseOrderActionRequest("approve", "同意"));

        assertThat(result).containsEntry("orderNo", "CG001").containsEntry("status", "approved");
        verify(jdbcTemplate).update(contains("UPDATE purchase_order"), eq("approved"), eq("approved"), eq(1L),
                eq("approved"), eq("approved"), eq("approved"), eq("approved"), eq("同意"), eq("CG001"),
                eq("pending_approval"));
        verify(jdbcTemplate).update(contains("INSERT INTO purchase_order_tracking"), eq(100L), eq("approve"), eq("approved"), eq("同意"));
        verify(jdbcTemplate).update(contains("INSERT INTO audit_log"), eq("system"), eq("approve"), eq("purchase_order"), eq(100L), eq("CG001"),
                eq("127.0.0.1"), eq("同意"));
    }

    @Test
    void demandActionUpdatesApprovedQuantityOnlyWhenApproved() {
        when(jdbcTemplate.queryForList(anyString(), eq("XQ001"))).thenReturn(List.of(Map.of(
                "demandId", 10L,
                "demandStatus", "pending_review"
        )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = runner.runDemandAction("XQ001", new PurchaseOrderActionRequest("approve", null));

        assertThat(result).containsEntry("demandNo", "XQ001").containsEntry("status", "approved");
        verify(jdbcTemplate).update(contains("UPDATE purchase_demand"), eq("approved"), eq("approved"), eq("approved"),
                eq("XQ001"), eq("pending_review"));
    }

    @Test
    void demandActionRejectsMissingDemand() {
        when(jdbcTemplate.queryForList(anyString(), eq("XQ404"))).thenReturn(List.of());

        assertThatThrownBy(() -> runner.runDemandAction("XQ404", new PurchaseOrderActionRequest("approve", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purchase demand not found");
    }

    @Test
    void planExecuteCreatesOrderAndStoresConvertedOrderNo() {
        Map<String, Object> plan = Map.of(
                "planId", 1L,
                "planStatus", "approved",
                "supplierId", 2L,
                "productId", 3L,
                "plannedQuantity", BigDecimal.TEN
        );
        when(jdbcTemplate.queryForMap(anyString(), eq("JH001"))).thenReturn(plan);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = runner.runPlanAction("JH001", new PurchaseOrderActionRequest("execute", null));

        assertThat(result)
                .containsEntry("planNo", "JH001")
                .containsEntry("status", "executed")
                .containsEntry("orderNo", "CG20260609001");
        assertThat(planSeenByFactory.get()).containsEntry("planNo", "JH001").containsEntry("plan", plan);
        verify(jdbcTemplate).update(contains("converted_order_no"), eq("CG20260609001"), eq("JH001"));
    }
}
