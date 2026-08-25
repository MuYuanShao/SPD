package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationCockpitServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DataScopeService dataScopeService;
    private OperationCockpitService service;

    @BeforeEach
    void setUp() {
        service = new OperationCockpitService(jdbcTemplate, dataScopeService);
    }

    @Test
    void buildsFixedCatalogCaliberAndSummaryForSelectedMonth() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("120000"), new BigDecimal("100000"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(6L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.cockpit("2026-07");

        assertThat(result.get("month")).isEqualTo("2026-07");
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary).containsEntry("currentAmount", new BigDecimal("120000"))
                .containsEntry("previousAmount", new BigDecimal("100000"))
                .containsEntry("monthOnMonth", new BigDecimal("20.00"))
                .containsEntry("departmentCount", 6L)
                .containsEntry("warningCount", 2L);

        @SuppressWarnings("unchecked") List<Map<String, Object>> categories =
                (List<Map<String, Object>>) result.get("categories");
        assertThat(categories).extracting(row -> row.get("category"))
                .containsExactly("高值耗材", "低值收费耗材", "低值不收费耗材");

        ArgumentCaptor<String> amountSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(
                amountSql.capture(), eq(BigDecimal.class), any(Object[].class));
        assertThat(amountSql.getAllValues()).allSatisfy(sql ->
                assertThat(sql).contains("dc.status = 'confirmed'"));
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service.cockpit("2026/07"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("统计月份格式应为 YYYY-MM");
    }
}
