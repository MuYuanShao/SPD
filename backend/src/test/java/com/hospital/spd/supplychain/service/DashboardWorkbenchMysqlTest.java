package com.hospital.spd.supplychain.service;

import com.hospital.spd.system.service.DashboardWorkbenchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.*;
import org.springframework.web.context.request.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Real SQL and browser contracts in a disposable database; never targets the business schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class DashboardWorkbenchMysqlTest {
    static IsolatedMysqlDatabase database;
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase(); database.register(registry);
    }
    @AfterAll static void cleanup() { if (database != null) database.close(); }
    @AfterEach void clearContext() { RequestContextHolder.resetRequestAttributes(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardWorkbenchService service;
    @Autowired InventoryMovementService movement;
    @LocalServerPort int port;
    String key;
    long dept, product, warehouse, batch;
    @BeforeEach void fixture() {
        key = "HOME-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, ?)", key, key);
        dept = id("SELECT dept_id FROM sys_dept WHERE dept_code = ?", key);
        jdbc.update("INSERT INTO product_category (category_code, category_name, level) VALUES (?, '首页测试分类', 1)", key);
        long category = id("SELECT category_id FROM product_category WHERE category_code = ?", key);
        jdbc.update("INSERT INTO product (product_code, product_name, spec_model, category_id, unit) VALUES (?, ?, '首页规格', ?, '个')", key, key, category);
        product = id("SELECT product_id FROM product WHERE product_code = ?", key);
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name, dept_id) VALUES (?, ?, '科室库', '首页测试院区', ?)", key, key, dept);
        warehouse = id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", key);
        for (int i = 0; i < 7; i++) {
            jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 3)", key + "-" + i, product);
            long current = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", key + "-" + i);
            jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty) VALUES (?, ?, ?, 0)", warehouse, product, current);
            if (i == 0) batch = current;
        }
        scope(dept);
    }
    @Test void salesExcludeUnconfirmedReversedAndMirroredChargesAndKeepEventPrices() {
        consumption("confirmed", null, 5, 50);
        consumption("confirmed", "high_value_charge", 2, 40);
        consumption("reversed", null, 100, 1000);
        consumption("red_flushed", null, 100, 1000);
        consumption("pending_confirm", null, 100, 1000);
        charge("charged", 2, 40);
        charge("charge_ready", 100, 1000);
        movement.receiveAvailable(warehouse, product, batch, BigDecimal.TEN, "purchase_receive_in", "receiving_order", 1L, "首页隔离入库");
        movement.consumeSpecificBatch(warehouse, product, batch, new BigDecimal("4"), "department_consume_out", "department_consumption", 1L, "首页隔离出库");
        jdbc.update("UPDATE inventory_batch SET batch_unit_price = 30 WHERE batch_id = ?", batch);
        var overview = service.workbench();
        Map<?, ?> metrics = (Map<?, ?>) overview.get("metrics");
        assertThat(number(metrics, "salesAmount")).isEqualByComparingTo("90");
        assertThat(number(metrics, "salesQuantity")).isEqualByComparingTo("7");
        assertThat(number(metrics, "inboundQuantity")).isEqualByComparingTo("10");
        assertThat(number(metrics, "outboundAmount")).isEqualByComparingTo("12");
        assertThat((List<?>) overview.get("trend")).hasSize(30);
        assertThat((List<?>) overview.get("notices")).isEmpty();
    }
    @Test void warningsUseActiveSafetyRulesAndConfiguredExpiryHorizon() {
        jdbc.update("INSERT INTO quota_safety_stock (dept_id, product_id, min_qty, max_qty, status, deleted) VALUES (?, ?, 2, 10, 1, 1)", dept, product);
        jdbc.update("INSERT INTO system_config (config_type, scope_type, scope_id, config_key, config_value, effective_mode, status) VALUES ('parameter', 'global', 'warning', 'warning.stock.threshold', '{\"expiryWarningDays\":3}', 'realtime', 1) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)");
        jdbc.update("UPDATE inventory_batch SET expire_date = CURRENT_DATE + INTERVAL 10 DAY WHERE batch_id = ?", batch);
        jdbc.update("UPDATE inventory_balance SET available_qty = 1 WHERE batch_id = ?", batch);
        Map<?, ?> alerts = (Map<?, ?>) service.workbench().get("alerts");
        assertThat(alerts.get("lowStock")).isEqualTo(0L);
        assertThat(alerts.get("expiry")).isEqualTo(0L);
    }
    @Test void paginationSearchAndDepartmentIsolationAreConsistent() {
        var first = service.products(Map.of("page", "1", "size", "5"));
        var second = service.products(Map.of("page", "2", "size", "5"));
        assertThat(first).containsEntry("total", 7L);
        assertThat((List<?>) first.get("rows")).hasSize(5);
        assertThat((List<?>) second.get("rows")).hasSize(2);
        assertThat(service.products(Map.of("keyword", "missing"))).containsEntry("total", 0L);
        assertThat(service.products(Map.of("keyword", "%"))).containsEntry("total", 0L);
        scope(dept + 999999);
        assertThat(service.products(Map.of())).containsEntry("total", 0L);
        assertThat(number((Map<?, ?>) service.workbench().get("metrics"), "salesAmount")).isZero();
    }
    @Test void ambiguousLegacyChargeDepartmentCannotLeakOrMultiplyIntoScopedMetrics() {
        charge("charged", 2, 40);
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, ?)", key + "-dup", key);
        assertThat(number((Map<?, ?>) service.workbench().get("metrics"), "salesAmount")).isZero();
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "SPD_HOME_UI_TESTS", matches = "true")
    void browserReadsRealDataWithoutChangingHomeComponents() throws Exception {
        consumption("confirmed", null, 5, 50);
        RequestContextHolder.resetRequestAttributes();
        String password = UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password = ?, status = 1 WHERE username = 'admin'",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
        java.io.File root = new java.io.File(System.getProperty("user.dir")).getParentFile();
        java.io.File log = new java.io.File(root, "output/playwright/home-smoke.log");
        java.nio.file.Files.createDirectories(log.toPath().getParent());
        var builder = new ProcessBuilder("node", "scripts/home-workbench-smoke.mjs").directory(root)
                .redirectErrorStream(true).redirectOutput(log);
        builder.environment().keySet().removeIf(k -> k.startsWith("SPD_DB_") || k.startsWith("SPD_TEST_MYSQL_") || k.contains("JWT_SECRET") || k.equals("MYSQL_PWD"));
        builder.environment().put("SPD_HOME_API", "http://127.0.0.1:" + port);
        builder.environment().put("SPD_HOME_PASSWORD", password);
        builder.environment().put("SPD_HOME_PRODUCT", key);
        var process = builder.start();
        try {
            assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as("See output/playwright/home-smoke.log").isZero();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    void consumption(String status, String related, int qty, int amount) {
        String no = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO department_consumption (consumption_no, dept_id, consumption_type, status, consume_by, related_biz_type) VALUES (?, ?, 'test', ?, 1, ?)", no, dept, status, related);
        long cid = id("SELECT consumption_id FROM department_consumption WHERE consumption_no = ?", no);
        jdbc.update("INSERT INTO department_consumption_item (consumption_id, product_id, quantity, unit_price, amount) VALUES (?, ?, ?, 10, ?)", cid, product, qty, amount);
    }
    void charge(String status, int qty, int amount) {
        jdbc.update("INSERT INTO high_value_charge (charge_no, dept_name, patient_no, product_code, product_name, quantity, amount, status, charge_time) VALUES (?, ?, 'TEST', ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString(), key, key, key, qty, amount, status);
    }
    void scope(long department) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 1L); request.setAttribute("currentUsername", "test");
        request.setAttribute("currentDeptId", department); request.setAttribute("currentDataScope", 2);
        request.setAttribute("currentRoles", List.of("ROLE_ADMIN"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    BigDecimal number(Map<?, ?> values, String key) { return new BigDecimal(values.get(key).toString()); }
}
