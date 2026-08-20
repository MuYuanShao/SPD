package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class UdiTraceabilityServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsUnknownTraceCodeInsteadOfReturningAnUnrelatedRecord() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForMap(anyString(), any(Object[].class));

        UdiTraceabilityService service = new UdiTraceabilityService(jdbcTemplate);

        assertThatThrownBy(() -> service.detail("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("追溯码不存在");
    }
}
