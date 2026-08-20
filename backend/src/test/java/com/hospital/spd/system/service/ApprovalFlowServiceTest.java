package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.ApprovalFlowRequest;
import com.hospital.spd.system.ApprovalFlowStepRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalFlowService approval flow settings")
class ApprovalFlowServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OperatorContextProvider operatorContextProvider;

    private ApprovalFlowService approvalFlowService;

    @BeforeEach
    void setUp() {
        approvalFlowService = new ApprovalFlowService(jdbcTemplate, operatorContextProvider);
    }

    @Test
    @DisplayName("create() writes the flow and replaces configured approval steps")
    void should_create_flow_with_steps() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                9L, "admin", "127.0.0.1", List.of("ROLE_ADMIN"), 3L, OperatorContext.DATA_SCOPE_ALL));
        ApprovalFlowRequest request = new ApprovalFlowRequest(
                "purchase-management",
                "采购管理",
                "order-approval",
                "采购订单审批",
                "global",
                "default",
                OperatorContext.DATA_SCOPE_DEPT,
                1,
                "unit test",
                3L,
                List.of(new ApprovalFlowStepRequest(
                        null,
                        1,
                        "采购负责人审批",
                        "role",
                        5L,
                        null,
                        null,
                        1,
                        false,
                        OperatorContext.DATA_SCOPE_DEPT,
                        1
                ))
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(88L);

        Map<String, Object> result = approvalFlowService.create(request);

        assertThat(result).containsEntry("flowId", 88L);
        verify(jdbcTemplate, times(1)).update(
                eq("DELETE FROM approval_flow_step WHERE flow_id = ?"),
                eq(88L)
        );
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("create() updates the existing node config when the flow scope already exists")
    void should_update_existing_flow_when_node_scope_exists() {
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                9L, "admin", "127.0.0.1", List.of("ROLE_ADMIN"), 3L, OperatorContext.DATA_SCOPE_ALL));
        ApprovalFlowRequest request = new ApprovalFlowRequest(
                "pending-product-catalog",
                "待审批目录",
                "initial-review",
                "目录初审",
                "global",
                "default",
                OperatorContext.DATA_SCOPE_ALL,
                1,
                "updated by setting dialog",
                null,
                List.of(new ApprovalFlowStepRequest(
                        null,
                        1,
                        "一级审批",
                        "role",
                        5L,
                        null,
                        null,
                        1,
                        false,
                        OperatorContext.DATA_SCOPE_ALL,
                        1
                ))
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate approval flow"))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("flowId", 66L)));

        Map<String, Object> result = approvalFlowService.create(request);

        assertThat(result).containsEntry("flowId", 66L).containsEntry("updated", 1);
        verify(jdbcTemplate, times(1)).update(
                eq("DELETE FROM approval_flow_step WHERE flow_id = ?"),
                eq(66L)
        );
    }

    @Test
    @DisplayName("update() rewrites the flow and configured approval steps")
    void should_update_flow_with_steps() {
        ApprovalFlowRequest request = new ApprovalFlowRequest(
                "purchase-management",
                "采购管理",
                "order-approval",
                "采购订单审批",
                "global",
                "default",
                OperatorContext.DATA_SCOPE_DEPT,
                1,
                "updated",
                3L,
                List.of(new ApprovalFlowStepRequest(
                        null,
                        2,
                        "库管审批",
                        "role",
                        5L,
                        null,
                        null,
                        1,
                        false,
                        OperatorContext.DATA_SCOPE_DEPT,
                        1
                ))
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = approvalFlowService.update(88L, request);

        assertThat(result).containsEntry("updated", 1);
        verify(jdbcTemplate, times(1)).update(
                eq("DELETE FROM approval_flow_step WHERE flow_id = ?"),
                eq(88L)
        );
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("save rejects duplicate approval step orders before database writes")
    void should_reject_duplicate_step_order() {
        ApprovalFlowRequest request = new ApprovalFlowRequest(
                "purchase-management",
                "采购管理",
                "order-approval",
                "采购订单审批",
                "global",
                "default",
                OperatorContext.DATA_SCOPE_DEPT,
                1,
                "duplicate orders",
                3L,
                List.of(
                        new ApprovalFlowStepRequest(null, 1, "一级审批", "role", 5L, null, null, 1, false, 1, 1),
                        new ApprovalFlowStepRequest(null, 1, "二级审批", "role", 5L, null, null, 1, false, 1, 1)
                )
        );

        assertThatThrownBy(() -> approvalFlowService.update(88L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批顺序不能重复");
    }
}
