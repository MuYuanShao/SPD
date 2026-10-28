package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real approval state machine with persisted-step and vote responses. */
class ApprovalDocumentWorkflowTest {
    @Test
    void laterReviewerCannotSkipFirstStepAndDistinctVotesAreRequired() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AtomicReference<OperatorContext> actor = new AtomicReference<>(operator(9, "SECOND"));
        Map<String,Object> run = new HashMap<>(Map.of("currentOrder", 1, "status", "pending"));
        Map<Integer,Set<Long>> votes = new HashMap<>();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> List.of(new HashMap<>(run)));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenAnswer(call -> {
            int order = ((Number) call.getArgument(2)).intValue();
            return Map.of("approverType", "role", "roleCode", order == 1 ? "first" : "second",
                    "minApprovals", order == 1 ? 2 : 1, "allowSelfApprove", 0, "dataScope", 1);
        });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(call -> {
            int order = ((Number) call.getArgument(3)).intValue();
            long user = ((Number) call.getArgument(4)).longValue();
            return votes.getOrDefault(order, Set.of()).contains(user) ? List.of(user) : List.of();
        });
        when(jdbc.queryForList(anyString(), eq(Integer.class), any(Object[].class))).thenAnswer(call ->
                ((Number) call.getArgument(3)).intValue() == 1 ? List.of(2) : List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(call ->
                (long) votes.getOrDefault(((Number) call.getArgument(3)).intValue(), Set.of()).size());
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("INSERT INTO business_approval_vote")) {
                votes.computeIfAbsent(((Number) call.getArgument(2)).intValue(), ignored -> new HashSet<>())
                        .add(((Number) call.getArgument(3)).longValue());
            } else if (sql.contains("SET current_order")) run.put("currentOrder", call.getArgument(1));
            else if (sql.contains("status = 'approved'")) run.put("status", "approved");
            return 1;
        });
        ApprovalFlowGuard guard = new ApprovalFlowGuard(jdbc, actor::get);
        assertThatThrownBy(() -> guard.recordDecision("purchase-management", "order-approval", "PO1", null, 99L, true))
                .hasMessageContaining("当前步骤");
        actor.set(operator(7, "FIRST"));
        assertThat(guard.recordDecision("purchase-management", "order-approval", "PO1", null, 99L, true)).isFalse();
        assertThatThrownBy(() -> guard.recordDecision("purchase-management", "order-approval", "PO1", null, 99L, true))
                .hasMessageContaining("重复");
        actor.set(operator(8, "FIRST"));
        assertThat(guard.recordDecision("purchase-management", "order-approval", "PO1", null, 99L, true)).isFalse();
        assertThat(run.get("currentOrder")).isEqualTo(2);
        actor.set(operator(9, "SECOND"));
        assertThat(guard.recordDecision("purchase-management", "order-approval", "PO1", null, 99L, true)).isTrue();
        assertThat(run.get("status")).isEqualTo("approved");
        assertThat(votes.get(1)).containsExactlyInAnyOrder(7L, 8L);
        assertThat(votes.get(2)).containsExactly(9L);
    }

    @Test
    void applicantCannotCastOwnApprovalVote() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("currentOrder", 1, "status", "pending")));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "approverType", "role", "roleCode", "first", "allowSelfApprove", 0, "dataScope", 1));
        ApprovalFlowGuard guard = new ApprovalFlowGuard(jdbc, () -> operator(7, "FIRST"));
        assertThatThrownBy(() -> guard.recordDecision("department-requisition", "requisition-approval", "R1", 3L, 7L, true))
                .hasMessageContaining("无权审批");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void laterVotesCannotBypassSnapshottedFlowDataScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "currentOrder", 2, "status", "pending", "flowDataScope", OperatorContext.DATA_SCOPE_SELF)));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "approverType", "role", "roleCode", "first", "allowSelfApprove", 1, "dataScope", 1));
        var guard = new ApprovalFlowGuard(jdbc, () -> operator(7, "FIRST"));
        assertThatThrownBy(() -> guard.recordDecision("purchase-management", "order-approval", "PO-SELF", null, 99L, true))
                .hasMessageContaining("无权审批");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static OperatorContext operator(long id, String role) {
        return new OperatorContext(id, "reviewer" + id, "127.0.0.1", List.of("ROLE_" + role), 3L, 1);
    }
}
