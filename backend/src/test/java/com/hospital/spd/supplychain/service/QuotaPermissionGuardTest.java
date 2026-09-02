package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaPermissionGuardTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock OperatorContextProvider operatorContextProvider;

    @Test
    void administratorBypassesPermissionLookup() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_ADMIN")));
        assertThatCode(() -> guard().require("quota-packing-task:create")).doesNotThrowAnyException();
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), eq(9L), anyString());
    }

    @Test
    void explicitlyAssignedOperatorCanExecuteAction() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_WAREHOUSE")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(9L),
                eq("quota-packing-task:confirm"))).thenReturn(1L);
        assertThatCode(() -> guard().require("quota-packing-task:confirm")).doesNotThrowAnyException();
    }

    @Test
    void unassignedOperatorIsRejected() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_WAREHOUSE")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(9L),
                eq("quota-package-label:consume"))).thenReturn(0L);
        assertThatThrownBy(() -> guard().require("quota-package-label:consume"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("quota-package-label:consume");
    }

    private QuotaPermissionGuard guard() {
        return new QuotaPermissionGuard(jdbcTemplate, operatorContextProvider);
    }

    private static OperatorContext operator(List<String> roles) {
        return new OperatorContext(9L, "operator", "10.0.0.9", roles, 20L,
                OperatorContext.DATA_SCOPE_DEPT);
    }
}
