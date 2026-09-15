package com.hospital.spd.system.service;

import com.hospital.spd.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DashboardWorkbenchServiceTest {
    @Test void workbenchReturnsThirtyCalendarDaysAndNoSyntheticNotices() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT CURRENT_DATE", java.time.LocalDate.class))
                .thenReturn(java.time.LocalDate.of(2026, 9, 10));
        var service = new DashboardWorkbenchService(jdbc, new DataScopeService(OperatorContext::system),
                OperatorContext::system, mock(RbacAuthorizationService.class));
        var result = service.workbench();
        assertThat((List<?>) result.get("trend")).hasSize(30);
        assertThat((List<?>) result.get("notices")).isEmpty();
        assertThat(result).containsEntry("noticeSourceAvailable", false).containsEntry("date", "2026-09-10");
    }
    @Test void productsReturnDatabasePageWithRegistrationManufacturerAndBatchDistributor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(12L);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 9L, "name", "真实耗材", "registrationNo", "注册证001", "quantity", 3)));
        var service = new DashboardWorkbenchService(jdbc, new DataScopeService(OperatorContext::system),
                OperatorContext::system, mock(RbacAuthorizationService.class));
        var result = service.products(Map.of("page", "2", "size", "5", "keyword", "针"));
        assertThat(result).containsEntry("page", 2).containsEntry("size", 5).containsEntry("total", 12L);
        assertThat((List<?>) result.get("rows")).hasSize(1);
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), arguments.capture());
        assertThat(sql.getValue()).contains("COALESCE(p.registration_no, '') AS registrationNo", "COALESCE(m.manufacturer_name, '') AS manufacturerName",
                "COALESCE(s.supplier_name, '') AS distributorName", "s.supplier_id = b.supplier_id", "LIMIT ? OFFSET ?");
        assertThat(arguments.getValue()).endsWith(5, 5);
        assertThat(sql.getValue()).contains("p.registration_no LIKE", "m.manufacturer_name LIKE", "s.supplier_name LIKE");
    }
}
