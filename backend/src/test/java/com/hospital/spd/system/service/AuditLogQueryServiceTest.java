package com.hospital.spd.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditLogQueryServiceTest {
    @Test
    void keywordFiltersRemarksOrIpAndPaginationKeepsBoundParameters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        var result = new AuditLogQueryService(jdbc).list(Map.of("keyword", " 业务 ", "page", "2", "size", "10"));
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), args.capture());
        assertThat(sql.getValue()).contains("AND (a.remark LIKE ? OR a.ip_address LIKE ?)", "LIMIT ? OFFSET ?");
        assertThat(args.getValue()).containsExactly("%业务%", "%业务%", 10, 10);
        assertThat(result).containsEntry("total", 7L).containsEntry("page", 2).containsEntry("size", 10);
    }
}
