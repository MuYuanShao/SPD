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
class PurchasePermissionGuardTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock OperatorContextProvider operatorContextProvider;

    @Test
    void administratorBypassesDatabasePermissionLookup() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_ADMIN")));

        assertThatCode(() -> guard().require("purchase-order:approve")).doesNotThrowAnyException();

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class),
                eq(9L), eq("purchase-order:approve"));
    }

    @Test
    void userWithAssignedPermissionCanExecuteAction() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_PURCHASE")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq(9L), eq("purchase-demand:convert-plan"))).thenReturn(1L);

        assertThatCode(() -> guard().require("purchase-demand:convert-plan")).doesNotThrowAnyException();
    }

    @Test
    void userWithoutAssignedPermissionIsRejected() {
        when(operatorContextProvider.current()).thenReturn(operator(List.of("ROLE_PURCHASE")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq(9L), eq("purchase-order:void"))).thenReturn(0L);

        assertThatThrownBy(() -> guard().require("purchase-order:void"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("purchase-order:void");
    }

    private PurchasePermissionGuard guard() {
        return new PurchasePermissionGuard(jdbcTemplate, operatorContextProvider);
    }

    private static OperatorContext operator(List<String> roles) {
        return new OperatorContext(9L, "buyer01", "10.0.0.9", roles, 20L, OperatorContext.DATA_SCOPE_DEPT);
    }
}
