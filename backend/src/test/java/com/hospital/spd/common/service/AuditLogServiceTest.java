package com.hospital.spd.common.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldWriteAuditWithBusinessNumberSnapshot() {
        AuditLogService service = new AuditLogService(jdbcTemplate);

        service.record("purchase", "approve", 12L, "CG20260608001", "approved");

        verify(jdbcTemplate).update(anyString(),
                eq("system"),
                eq("approve"),
                eq("purchase"),
                eq(12L),
                eq("CG20260608001"),
                eq("127.0.0.1"),
                eq("approved"));
    }
}
