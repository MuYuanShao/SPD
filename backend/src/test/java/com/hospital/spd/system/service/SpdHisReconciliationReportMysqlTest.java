package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Opt-in read-only verification of the SPD-HIS reconciliation SQL against MySQL. */
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class SpdHisReconciliationReportMysqlTest {

    @Test
    void queriesRealFactsAndBuildsMaskedExcel() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                environment("SPD_DB_URL", "jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"),
                environment("SPD_DB_USERNAME", "admin"),
                environment("SPD_DB_PASSWORD", "admin123"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SpdHisReconciliationReportService service = new SpdHisReconciliationReportService(
                jdbcTemplate,
                new DataScopeService(OperatorContext::system),
                mock(AuditLogService.class));

        Map<String, String> params = Map.of(
                "dateFrom", "2020-01-01",
                "dateTo", "2030-12-31",
                "page", "1",
                "size", "20");
        Map<String, Object> report = service.list(params);

        assertNotNull(report.get("summary"));
        assertNotNull(report.get("rows"));
        ((java.util.List<?>) report.get("rows")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .forEach(row -> {
                    assertFalse(row.containsKey("patientSearchNo"));
                    assertFalse(row.containsKey("patientSearchName"));
                });
        assertTrue(service.exportExcel(params).length > 100);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
