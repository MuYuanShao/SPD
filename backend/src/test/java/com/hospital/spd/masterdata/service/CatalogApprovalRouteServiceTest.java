package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogApprovalRouteServiceTest {
    @Mock JdbcTemplate jdbc;
    private CatalogApprovalRouteService service;

    @BeforeEach
    void setUp() {
        service = new CatalogApprovalRouteService(jdbc, () ->
                new OperatorContext(9L, "applicant", "127.0.0.1", List.of("ROLE_USER"), 20L, 1));
    }

    @Test
    void ordinaryApplicationConcatenatesInitialAndFinalRoutes() {
        when(jdbc.queryForList(anyString(), eq("pending-product-catalog"), eq("initial-review")))
                .thenReturn(List.of(flow(1L, "global", null)));
        when(jdbc.queryForList(anyString(), eq("pending-product-catalog"), eq("final-review")))
                .thenReturn(List.of(flow(2L, "global", null)));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(step(11L)));
        when(jdbc.queryForList(anyString(), eq(2L))).thenReturn(List.of(step(21L)));

        assertThat(service.snapshotRoute(100L, 1, "新品准入", 20L)).isEqualTo("pending_step_1");

        assertThat(updateCount()).isEqualTo(2);
    }

    @Test
    void priceApplicationUsesOnlyPriceApprovalFlow() {
        when(jdbc.queryForList(anyString(), eq("batch-price-adjustment"), eq("price-adjustment-approval")))
                .thenReturn(List.of(flow(3L, "global", null)));
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(List.of(step(31L)));

        assertThat(service.snapshotRoute(101L, 1, "价格调整", 20L)).isEqualTo("pending_step_1");

        verify(jdbc).queryForList(anyString(), eq("batch-price-adjustment"), eq("price-adjustment-approval"));
        assertThat(updateCount()).isEqualTo(1);
    }

    @Test
    void departmentFlowWinsOverGlobalFlow() {
        when(jdbc.queryForList(anyString(), eq("pending-product-catalog"), eq("initial-review")))
                .thenReturn(List.of(flow(1L, "global", null), flow(10L, "department", 20L)));
        when(jdbc.queryForList(anyString(), eq("pending-product-catalog"), eq("final-review")))
                .thenReturn(List.of(flow(2L, "global", null)));
        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(step(101L)));
        when(jdbc.queryForList(anyString(), eq(2L))).thenReturn(List.of(step(21L)));

        service.snapshotRoute(102L, 1, "信息变更", 20L);

        verify(jdbc).queryForList(anyString(), eq(10L));
    }

    @Test
    void ambiguousConfigurationBlocksSubmission() {
        when(jdbc.queryForList(anyString(), eq("pending-product-catalog"), eq("initial-review")))
                .thenReturn(List.of(flow(1L, "global", null), flow(2L, "global", null)));

        assertThatThrownBy(() -> service.snapshotRoute(103L, 1, "新品准入", 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置冲突");
    }

    @Test
    void onlyIdentifiedPreUpgradeRoundsUseDynamicRoutes() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(100L), eq(1)))
                .thenReturn(1, 0);
        assertThat(service.isLegacyDynamicRoute(100L, 1, "pending_step_2")).isTrue();
        assertThat(service.isLegacyDynamicRoute(100L, 1, "pending_step_2")).isFalse();
        assertThat(service.isLegacyDynamicRoute(100L, 1, "pending_step_invalid")).isFalse();
        assertThat(service.isLegacyDynamicRoute(100L, 1, "approved")).isFalse();
    }

    @Test
    void missingSnapshotStillBlocksUnidentifiedNumberedSteps() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(100L), eq(1))).thenReturn(0);
        assertThatThrownBy(() -> service.ensureLegacyRoute(100L, 1, "新品准入", "pending_step_2", 20L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无法安全映射");
        assertThat(updateCount()).isZero();
    }

    private static Map<String, Object> flow(long id, String scopeType, Long deptId) {
        Map<String, Object> flow = new HashMap<>();
        flow.put("flowId", id);
        flow.put("scopeType", scopeType);
        flow.put("scopeId", deptId == null ? "default" : deptId.toString());
        flow.put("deptId", deptId);
        return flow;
    }

    private static Map<String, Object> step(long id) {
        return Map.of("stepId", id, "stepOrder", 1, "stepName", "审批", "approverType", "role",
                "roleId", 5L, "minApprovals", 1, "allowSelfApprove", 0, "dataScope", 1);
    }

    private long updateCount() {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .count();
    }
}
