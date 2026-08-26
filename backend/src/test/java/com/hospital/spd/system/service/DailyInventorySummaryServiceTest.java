package com.hospital.spd.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies incremental day selection for inventory daily summaries. */
class DailyInventorySummaryServiceTest {
    @Test
    void generatesOnlyTheNextMissingDayFromPreviousClose() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LocalDate target = LocalDate.of(2026, 8, 25);
        when(jdbcTemplate.queryForObject("SELECT MAX(business_date) FROM inventory_daily_summary", Date.class))
                .thenReturn(Date.valueOf(target.minusDays(1)));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(3);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        int generated = new DailyInventorySummaryService(jdbcTemplate).generateThrough(target);

        assertThat(generated).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
