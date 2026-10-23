package com.hospital.spd.system.service;

import com.hospital.spd.system.ConfigHitCandidate;
import com.hospital.spd.system.SystemConfigRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemConfigResolutionTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SystemConfigService service = new SystemConfigService(jdbc);

    @Test void requiresAnExplicitKeyBeforeLookingUpCandidates() {
        assertThatThrownBy(() -> service.explain(Map.of("configType", "workflow")))
                .hasMessageContaining("请选择需要核对的配置项");
        verifyNoInteractions(jdbc);
    }

    @Test void bindsTheKeyAndOnlyMarksStrictlyCoarserCandidatesAsOverridden() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                candidate(1, 9, "global", "default"), candidate(2, 4, "department", "D1")));
        var result = service.explain(Map.of("configType", "workflow", "configKey", "approval.purchase.order", "departmentId", "D1"));
        var parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(contains("config_type = ? AND config_key = ?"), any(RowMapper.class), parameters.capture());
        assertThat(parameters.getValue()).startsWith("workflow", "approval.purchase.order");
        assertThat(result.sourceConfigId()).isEqualTo(2L);
        assertThat(result.parentConfigId()).isEqualTo(1L);
        assertThat(result.conflict()).isFalse();
        assertThat(result.candidates().get(0).hit()).isTrue();
        assertThat(result.candidates().get(0).overridden()).isFalse();
        assertThat(result.candidates().get(1).overridden()).isTrue();
        assertThat(result.executionNote()).contains("未接入业务执行");
    }

    @Test void refusesToChooseBetweenEqualRankCandidatesEvenIfTheirIdsDiffer() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                candidate(2, 8, "global", "warning"), candidate(1, 8, "global", "purchase_order")));
        var result = service.explain(Map.of("configKey", "approval.purchase.order", "moduleId", "purchase_order"));
        assertThat(result.conflict()).isTrue();
        assertThat(result.sourceConfigId()).isNull();
        assertThat(result.parentConfigId()).isNull();
        assertThat(result.finalValue()).isEqualTo("-");
        assertThat(result.candidates()).allSatisfy(row -> {
            assertThat(row.conflict()).isTrue();
            assertThat(row.hit()).isFalse();
            assertThat(row.overridden()).isFalse();
        });
    }

    @Test void rejectsAnActiveDuplicateBeforeWriting() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(77L));
        assertThatThrownBy(() -> service.create(request(1))).hasMessageContaining("生效冲突");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void translatesTheUniqueKeyRaceIntoAnActionableBusinessError() {
        when(jdbc.update(contains("INSERT INTO system_config"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_config_scope_key"));
        assertThatThrownBy(() -> service.create(request(1))).hasMessageContaining("请编辑已有配置");
    }

    @Test void enablingChecksTheExistingIdentityBeforeChangingItsStatus() {
        when(jdbc.queryForList(contains("config_type AS configType"), eq(3L))).thenReturn(List.of(Map.of(
                "configType", "workflow", "scopeType", "global", "scopeId", "default", "configKey", "approval.purchase.order")));
        when(jdbc.queryForList(contains("config_id <> ?"), eq(Long.class), any(Object[].class))).thenReturn(List.of(4L));
        assertThatThrownBy(() -> service.updateStatus(3L, 1)).hasMessageContaining("生效冲突");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void disablingRemainsAvailableToResolveLegacyConflicts() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(service.updateStatus(3L, 0)).containsEntry("updatedRows", 1);
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    private static ConfigHitCandidate candidate(long id, int priority, String scope, String object) {
        return new ConfigHitCandidate(id, "workflow", "业务流程配置", scope, priority, scope, object,
                "approval.purchase.order", "采购订单审批流程", "value-" + id, "2026-01-01", "-", "low", "有效候选", false, false);
    }

    private static SystemConfigRequest request(int status) {
        return new SystemConfigRequest("workflow", "global", "default", "approval.purchase.order", "{}", "realtime", null, "low", status);
    }
}
