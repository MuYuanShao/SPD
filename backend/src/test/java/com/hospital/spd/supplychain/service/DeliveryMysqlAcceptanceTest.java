package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises real delivery transactions against an owned, disposable MySQL schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class DeliveryMysqlAcceptanceTest {
    static IsolatedMysqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase(); database.register(registry);
    }
    @AfterAll static void cleanup() { if (database != null) database.close(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired OperationalDeliveryModule delivery;
    @Autowired com.hospital.spd.specialty.service.QuotaPackageTraceFlowService trace;
    @LocalServerPort int port;
    String code, sourceName, requisitionNo;
    long productId, sourceId, targetId, otherTargetId, deptId, batchId, itemId, requisitionId;

    @BeforeEach void fixture() {
        code = "DA-" + UUID.randomUUID().toString().replace("-", "");
        sourceName = code;
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, '配送验收科室')", code);
        deptId = id("SELECT dept_id FROM sys_dept WHERE dept_code = ?", code);
        jdbc.update("INSERT INTO product_category (category_code, category_name, level) VALUES (?, '验收分类', 1)", code);
        long category = id("SELECT category_id FROM product_category WHERE category_code = ?", code);
        jdbc.update("INSERT INTO product (product_code, product_name, spec_model, category_id, unit) VALUES (?, '配送验收耗材', '验收规格', ?, '个')", code, category);
        productId = id("SELECT product_id FROM product WHERE product_code = ?", code);
        sourceId = warehouse(code, "中心库", null);
        otherTargetId = warehouse(code + "-A", "科室库", deptId);
        targetId = warehouse(code + "-B", "科室库", deptId);
        batchId = batch(code, 10);
        requisitionNo = code;
        jdbc.update("INSERT INTO department_requisition (requisition_no, dept_id, warehouse_id, source_warehouse_id, requisition_type, status, applicant_id) VALUES (?, ?, ?, ?, 'normal', 'approved', 1)", requisitionNo, deptId, targetId, sourceId);
        requisitionId = id("SELECT requisition_id FROM department_requisition WHERE requisition_no = ?", requisitionNo);
        jdbc.update("INSERT INTO department_requisition_item (requisition_id, product_id, quantity, unit, item_type) VALUES (?, ?, 4, '个', 'loose')", requisitionId, productId);
        itemId = id("SELECT item_id FROM department_requisition_item WHERE requisition_id = ?", requisitionId);
    }

    @Test void concurrentAndRepeatedSigningReceivesOnlyOnceIntoSavedWarehouse() throws Exception {
        String no = pickLoose(4);
        assertThat(stock(sourceId)).isEqualByComparingTo("6");
        List<Object> results = concurrent(() -> delivery.signDelivery(no));
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Map.class));
        delivery.signDelivery(no);
        assertThat(stock(sourceId)).isEqualByComparingTo("6");
        assertThat(stock(targetId)).isEqualByComparingTo("4");
        assertThat(stock(otherTargetId)).isZero();
        assertThat(events(no, "delivery_sign_in")).isEqualTo(1);
    }

    @Test void sourceDepletedByPickingCanStillBeSigned() {
        jdbc.update("UPDATE inventory_balance SET available_qty = 4 WHERE batch_id = ?", batchId);
        String no = pickLoose(4);
        assertThat(stock(sourceId)).isZero();
        delivery.signDelivery(no);
        assertThat(stock(sourceId)).isZero();
        assertThat(stock(targetId)).isEqualByComparingTo("4");
    }

    @Test void concurrentPickingCannotExceedRequestedQuantity() throws Exception {
        List<Object> results = concurrent(() -> delivery.confirmLoosePicking(payload(4)));
        assertThat(results.stream().filter(Map.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(Throwable.class::isInstance).count()).isEqualTo(1);
        assertThat(stock(sourceId)).isEqualByComparingTo("6");
        assertThat(id("SELECT COUNT(*) FROM spd_delivery_order WHERE requisition_no = ?", requisitionNo)).isEqualTo(1);
    }

    @Test void changedDestinationIsRejectedWithoutSubstitutingAnotherDepartmentWarehouse() {
        String no = pickLoose(4);
        jdbc.update("UPDATE warehouse SET status = 0 WHERE warehouse_id = ?", targetId);
        assertThatThrownBy(() -> delivery.signDelivery(no)).hasMessageContaining("目标库无效");
        assertThat(stock(targetId)).isZero();
        assertThat(stock(otherTargetId)).isZero();
        assertThat(events(no, "delivery_sign_in")).isZero();
        assertThat(status(no)).isEqualTo("picked");
    }

    @Test void laterBatchFailureRollsBackEarlierReceiptAndStatus() {
        jdbc.update("UPDATE inventory_balance SET available_qty = 2 WHERE batch_id = ?", batchId);
        long second = batch(code + "-2", 2);
        String no = pickLoose(4);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty) VALUES (?, ?, ?, 99999999999999.9999)", targetId, productId, second);
        BigDecimal before = stock(targetId);
        assertThatThrownBy(() -> delivery.signDelivery(no)).isInstanceOf(RuntimeException.class);
        assertThat(stock(targetId)).isEqualByComparingTo(before);
        assertThat(events(no, "delivery_sign_in")).isZero();
        assertThat(status(no)).isEqualTo("picked");
    }

    @Test void twoHistoricalVersionPackagesAndMultiPlaceholderSignExactlyOnce() throws Exception {
        List<String> labels = packages();
        Map<String, Object> result = delivery.confirmPicking(Map.of("requisitionNo", requisitionNo,
                "itemId", itemId, "warehouseName", sourceName, "labelNos", labels));
        String no = (String) result.get("deliveryNo");
        jdbc.update("UPDATE spd_delivery_order SET product_code = 'MULTI' WHERE delivery_no = ?", no);
        assertThat(concurrent(() -> delivery.signDelivery(no))).allSatisfy(r -> assertThat(r).isInstanceOf(Map.class));
        assertThat(stock(targetId)).isEqualByComparingTo("4");
        assertThat(events(no, "quota_package_delivery_sign_in")).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM quota_package_label WHERE warehouse_id = ? AND status = 'signed'", targetId)).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM udi_trace_code t JOIN quota_package_label l ON l.trace_code_id = t.trace_code_id WHERE l.warehouse_id = ? AND t.current_status = 'signed'", targetId)).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM udi_trace_event e JOIN quota_package_label l ON l.trace_code_id = e.trace_code_id WHERE l.warehouse_id = ? AND e.event_type = 'department_sign'", targetId)).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM inventory_event_trace_code t JOIN inventory_event e ON e.event_id = t.event_id JOIN spd_delivery_order d ON d.delivery_id = e.source_biz_id WHERE e.source_biz_type = 'spd_delivery_order' AND d.delivery_no = ?", no)).isEqualTo(2);
    }

    @Test void missingOnePackageSourceRejectsTheWholeDelivery() {
        List<String> labels = packages();
        String no = (String) delivery.confirmPicking(Map.of("requisitionNo", requisitionNo,
                "itemId", itemId, "warehouseName", sourceName, "labelNos", labels)).get("deliveryNo");
        long second = id("SELECT label_id FROM quota_package_label WHERE label_no = ?", labels.get(1));
        jdbc.update("DELETE FROM quota_package_label_source WHERE label_id = ?", second);
        assertThatThrownBy(() -> delivery.signDelivery(no)).isInstanceOf(IllegalArgumentException.class);
        assertThat(stock(targetId)).isZero();
        assertThat(status(no)).isEqualTo("picked");
        assertThat(events(no, "quota_package_delivery_sign_in")).isZero();
    }

    @Test void mismatchedTemplateSnapshotRejectsPickingBeforeAnyDeliveryWrite() {
        List<String> labels = packages();
        jdbc.update("UPDATE department_requisition_item SET quota_template_version = 2 WHERE item_id = ?", itemId);
        assertThatThrownBy(() -> delivery.confirmPicking(Map.of("requisitionNo", requisitionNo,
                "itemId", itemId, "warehouseName", sourceName, "labelNos", labels)))
                .hasMessageContaining("标签模板版本或包装规格");
        assertThat(id("SELECT COUNT(*) FROM spd_delivery_order WHERE requisition_no = ?", requisitionNo)).isZero();
        assertThat(id("SELECT COUNT(*) FROM quota_package_label WHERE warehouse_id = ? AND status = 'available'", sourceId)).isEqualTo(2);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SPD_DELIVERY_UI_TESTS", matches = "true")
    void realBrowserPickingAndSigningAcrossViewports() throws Exception {
        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (int width : List.of(1440, 1280, 390)) {
            for (String mode : List.of("loose", "quota_package")) {
                fixture();
                List<String> labels = mode.equals("quota_package") ? packages() : List.of();
                scenarios.add(Map.of("requisitionNo", requisitionNo, "sourceName", sourceName,
                        "targetId", targetId, "productId", productId, "mode", mode,
                        "labels", labels, "width", width, "height", width == 1280 ? 720 : 900));
            }
        }
        fixture();
        String blockedNo = pickLoose(4);
        jdbc.update("UPDATE warehouse SET status = 0 WHERE warehouse_id = ?", targetId);
        String password = UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password = ?, status = 1, deleted = 0 WHERE username = 'admin'",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
        var projectRoot = new java.io.File(System.getProperty("user.dir")).getParentFile();
        var browserLog = new java.io.File(projectRoot, "output/playwright/delivery-browser.log");
        java.nio.file.Files.createDirectories(browserLog.toPath().getParent());
        var process = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test",
                "--config=playwright.delivery.config.ts")
                .directory(projectRoot)
                .redirectErrorStream(true).redirectOutput(browserLog);
        process.environment().keySet().removeIf(key -> key.startsWith("SPD_DB_")
                || key.startsWith("SPD_TEST_MYSQL_") || key.equals("MYSQL_PWD") || key.contains("JWT_SECRET"));
        process.environment().put("SPD_DELIVERY_API_URL", "http://127.0.0.1:" + port);
        process.environment().put("SPD_DELIVERY_UI_PASSWORD", password);
        process.environment().put("SPD_DELIVERY_UI_CASES", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scenarios));
        process.environment().put("SPD_DELIVERY_BLOCKED_NO", blockedNo);
        Process browser = process.start();
        try {
            assertThat(browser.waitFor(240, TimeUnit.SECONDS)).as("Browser acceptance completed within four minutes").isTrue();
            assertThat(browser.exitValue()).as("Real API Playwright acceptance").isZero();
        } finally {
            if (browser.isAlive()) browser.destroyForcibly();
        }
        for (Map<String, Object> scenario : scenarios) {
            assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(available_qty), 0) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?",
                    BigDecimal.class, scenario.get("targetId"), scenario.get("productId"))).isEqualByComparingTo("4");
            assertThat(id("SELECT COUNT(*) FROM spd_delivery_order WHERE requisition_no = ? AND status = 'signed'", scenario.get("requisitionNo"))).isEqualTo(1);
        }
        assertThat(status(blockedNo)).isEqualTo("picked");
        assertThat(stock(targetId)).isZero();
    }

    List<String> packages() {
        jdbc.update("INSERT INTO quota_package_template (template_code, template_name, version_no, is_current, status) VALUES (?, '历史验收模板', 1, 0, 0)", code);
        long template = id("SELECT template_id FROM quota_package_template WHERE template_code = ?", code);
        jdbc.update("INSERT INTO quota_package_template_item (template_id, product_id, quantity, unit) VALUES (?, ?, 2, '包')", template, productId);
        jdbc.update("UPDATE department_requisition_item SET item_type = 'quota_package', quota_template_id = ?, quota_template_version = 1, quota_package_quantity = 2, quota_package_unit = '包' WHERE item_id = ?", template, itemId);
        List<String> labels = List.of(code + "-1", code + "-2");
        for (String label : labels) {
            jdbc.update("INSERT INTO quota_package_label (label_no, template_id, warehouse_id, product_id, package_quantity, status) VALUES (?, ?, ?, ?, 2, 'available')", label, template, sourceId, productId);
            long labelId = id("SELECT label_id FROM quota_package_label WHERE label_no = ?", label);
            jdbc.update("INSERT INTO quota_package_label_source (label_id, batch_id, source_qty, unit_price, warehouse_id) VALUES (?, ?, 2, 10, ?)", labelId, batchId, sourceId);
            trace.ensureTrace(labelId);
        }
        return labels;
    }
    long warehouse(String name, String type, Long dept) {
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name, dept_id) VALUES (?, ?, ?, '验收院区', ?)", name, name, type, dept);
        return id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", name);
    }
    long batch(String no, int qty) {
        jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 10)", no, productId);
        long batch = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", no);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty) VALUES (?, ?, ?, ?)", sourceId, productId, batch, qty);
        return batch;
    }
    long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    Map<String, Object> payload(int qty) { return Map.of("requisitionNo", requisitionNo, "itemId", itemId, "warehouseName", sourceName, "quantity", qty); }
    String pickLoose(int qty) { return (String) delivery.confirmLoosePicking(payload(qty)).get("deliveryNo"); }
    BigDecimal stock(long warehouse) { return jdbc.queryForObject("SELECT COALESCE(SUM(available_qty), 0) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?", BigDecimal.class, warehouse, productId); }
    long events(String no, String type) { return id("SELECT COUNT(*) FROM inventory_event e JOIN spd_delivery_order d ON d.delivery_id = e.source_biz_id WHERE e.source_biz_type = 'spd_delivery_order' AND d.delivery_no = ? AND e.event_type = ?", no, type); }
    String status(String no) { return jdbc.queryForObject("SELECT status FROM spd_delivery_order WHERE delivery_no = ?", String.class, no); }
    List<Object> concurrent(Callable<?> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        Callable<Object> task = () -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); try { return action.call(); } catch (Exception e) { return e; } };
        try {
            Future<Object> first = executor.submit(task), second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
}
