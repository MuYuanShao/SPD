package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalFlowGuard")
class ApprovalFlowGuardTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OperatorContextProvider operatorContextProvider;

    private ApprovalFlowGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider);
    }

    @Test
    @DisplayName("allows a user whose role matches an enabled approval step")
    void should_allow_matching_role_step() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                7L, "reviewer", "127.0.0.1", List.of("ROLE_APPROVER"), 3L, OperatorContext.DATA_SCOPE_ALL));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow"), eq("purchase-management"), eq("order-approval")))
                .thenReturn(List.of(flow(11L)));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow_step"), eq(11L)))
                .thenReturn(List.of(roleStep("approver", 1)));

        assertThatCode(() -> guard.requireApprovalAccess("purchase-management", "order-approval", null, 99L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blocks self approval when the matching step forbids it")
    void should_block_self_approval_when_step_forbids_it() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                7L, "reviewer", "127.0.0.1", List.of("ROLE_APPROVER"), 3L, OperatorContext.DATA_SCOPE_ALL));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow"), eq("purchase-management"), eq("order-approval")))
                .thenReturn(List.of(flow(11L)));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow_step"), eq(11L)))
                .thenReturn(List.of(roleStep("approver", 0)));

        assertThatThrownBy(() -> guard.requireApprovalAccess("purchase-management", "order-approval", null, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权审批");
    }

    @Test
    @DisplayName("allows system administrator to approve every node without matching approval settings")
    void should_allow_admin_to_approve_every_node() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                1L, "admin", "127.0.0.1", List.of("ROLE_ADMIN"), 1L, OperatorContext.DATA_SCOPE_ALL));

        assertThatCode(() -> guard.requireApprovalAccess("pending-product-catalog", "custom-review", 3, 99L, 88L))
                .doesNotThrowAnyException();
        assertThat(guard.hasApprovalAccess("pending-product-catalog", "another-review", 9, 101L, 202L))
                .isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("blocks a user whose roles do not match any enabled step")
    void should_block_role_mismatch() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                8L, "operator", "127.0.0.1", List.of("ROLE_OPERATOR"), 3L, OperatorContext.DATA_SCOPE_ALL));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow"), eq("purchase-management"), eq("order-approval")))
                .thenReturn(List.of(flow(11L)));
        when(jdbcTemplate.queryForList(contains("FROM approval_flow_step"), eq(11L)))
                .thenReturn(List.of(roleStep("approver", 1)));

        assertThatThrownBy(() -> guard.requireApprovalAccess("purchase-management", "order-approval", null, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权审批");
    }

    private static Map<String, Object> flow(Long flowId) {
        return Map.of(
                "flowId", flowId,
                "scopeType", "global",
                "scopeId", "default",
                "dataScope", OperatorContext.DATA_SCOPE_ALL
        );
    }

    private static Map<String, Object> roleStep(String roleCode, int allowSelfApprove) {
        return Map.of(
                "approverType", "role",
                "roleId", 5L,
                "roleCode", roleCode,
                "allowSelfApprove", allowSelfApprove,
                "dataScope", OperatorContext.DATA_SCOPE_ALL
        );
    }
}
