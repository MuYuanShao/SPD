package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DataScopeService dataScopeService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(jdbcTemplate, dataScopeService);
    }

    @Test
    void returnsOnlyDatabaseBackedDashboardData() {
        when(jdbcTemplate.queryForObject(argThat(sql -> sql != null && sql.contains("COUNT(DISTINCT product_id)")), eq(Long.class))).thenReturn(28L);
        when(jdbcTemplate.queryForObject(contains("FROM purchase_order"), eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForObject(argThat(sql -> sql != null && sql.contains("p.min_purchase_qty")), eq(Long.class))).thenReturn(3L);
        when(jdbcTemplate.queryForObject(contains("SUM(total_amount)"), eq(BigDecimal.class))).thenReturn(new BigDecimal("1200.00"));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM receiving_order ro")
                && sql.contains("GROUP BY DATE_FORMAT(ro.create_time, '%Y-%m-%d')"))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM department_consumption dc")
                && sql.contains("GROUP BY DATE_FORMAT(dc.consume_time, '%Y-%m-%d')"))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept sd"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("riskEvents"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM audit_log"))).thenReturn(List.of());

        Map<String, Object> result = service.dashboard();

        assertThat(result).containsKeys("metrics", "trend", "departments", "exceptions", "notices");
        @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        assertThat(metrics)
                .containsEntry("productCount", 28L)
                .containsEntry("todayOrders", 2L)
                .containsEntry("lowStockCount", 3L)
                .containsEntry("monthlyPurchaseAmount", new BigDecimal("1200.00"));
        verify(dataScopeService, atLeast(6)).appendScope(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
