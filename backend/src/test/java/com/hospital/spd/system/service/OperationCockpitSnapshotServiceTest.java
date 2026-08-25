package com.hospital.spd.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationCockpitSnapshotServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private OperationCockpitService sourceService;
    private OperationCockpitSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new OperationCockpitSnapshotService(jdbcTemplate, new ObjectMapper(), sourceService);
    }

    @Test
    void cockpitReadsOnlyPersistedSnapshot() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("currentAmount", new BigDecimal("120000")),
                Map.entry("previousAmount", new BigDecimal("100000")),
                Map.entry("monthOnMonth", new BigDecimal("20.00")),
                Map.entry("departmentCount", 6),
                Map.entry("warningCount", 2),
                Map.entry("categoriesJson", "[{\"category\":\"高值耗材\",\"currentAmount\":120000}]"),
                Map.entry("trendJson", "[]"), Map.entry("departmentsJson", "[]"),
                Map.entry("focusedProductsJson", "[]"), Map.entry("alertsJson", "[]"),
                Map.entry("statisticsDate", Date.valueOf("2026-08-25")),
                Map.entry("generatedAt", "2026-08-25 00:05:00")
        )));

        Map<String, Object> result = service.cockpit("2026-08");

        assertThat(result).containsEntry("month", "2026-08")
                .containsEntry("statisticsDate", "2026-08-25");
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary).containsEntry("currentAmount", new BigDecimal("120000"))
                .containsEntry("departmentCount", 6L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("FROM operation_cockpit_snapshot")
                .doesNotContain("department_consumption");
    }

    @Test
    void refreshSnapshotUpsertsMaterialisedPayload() {
        Map<String, Object> source = Map.of(
                "summary", Map.of("currentAmount", new BigDecimal("10"),
                        "previousAmount", new BigDecimal("8"), "monthOnMonth", new BigDecimal("25"),
                        "departmentCount", 2L, "warningCount", 1L),
                "categories", List.of(), "trend", List.of(), "departments", List.of(),
                "focusedProducts", List.of(), "alerts", List.of(), "month", "2026-08"
        );
        when(sourceService.cockpit("2026-08")).thenReturn(source);

        service.refreshSnapshot(YearMonth.of(2026, 8));

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
