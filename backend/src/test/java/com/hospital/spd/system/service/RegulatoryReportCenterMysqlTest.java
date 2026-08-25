package com.hospital.spd.system.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.AuditLogService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Opt-in verification of regulatory report SQL and exports against the local MySQL schema. */
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class RegulatoryReportCenterMysqlTest {

    @Test
    void runsAllReportsAndBuildsExcel() {
        String url = environment("SPD_DB_URL", "jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");
        String username = environment("SPD_DB_USERNAME", "admin");
        String password = environment("SPD_DB_PASSWORD", "admin123");
        Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        RegulatoryReportCenterService service = new RegulatoryReportCenterService(
                new JdbcTemplate(dataSource), new DataScopeService(OperatorContext::system), mock(AuditLogService.class));
        Map<String, String> params = Map.of("dateFrom", "2020-01-01", "dateTo", "2030-12-31", "page", "1", "size", "20");

        assertReport(service.supplierDeliveryLedger(params));
        assertReport(service.centralizedProcurementProgress(params));
        assertReport(service.inventoryMovementSummary(params));
        assertTrue(service.exportSupplierDeliveryLedger(params).length > 100);
        assertTrue(service.exportCentralizedProcurementProgress(params).length > 100);
        assertTrue(service.exportInventoryMovementSummary(params).length > 100);
    }

    private static void assertReport(Map<String, Object> report) {
        assertNotNull(report.get("rows"));
        assertNotNull(report.get("summary"));
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
