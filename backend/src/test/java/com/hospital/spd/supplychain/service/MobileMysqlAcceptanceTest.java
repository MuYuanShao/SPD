package com.hospital.spd.supplychain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Proves mobile HTTP authentication, transactional receipts and desktop conflicts in an owned MySQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spd.mobile.enabled=true", "spd.mobile.server-id=mobile-isolated-test"})
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class MobileMysqlAcceptanceTest {
    static IsolatedMysqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase(); database.register(registry);
    }
    @AfterAll static void cleanup() { if (database != null) database.close(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired OperationalDeliveryModule delivery;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;
    long dept, source, target, product, user, role, deliveryId;
    String token, no;
    Map<String, Object> operation;
    final HttpClient http = HttpClient.newBuilder().proxy(new java.net.ProxySelector() {
        public List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
        public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException ex) {}
    }).build();

    @BeforeEach void fixture() throws Exception {
        String code = "MA-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, ?)", code, code);
        dept = id("SELECT dept_id FROM sys_dept WHERE dept_code = ?", code);
        jdbc.update("INSERT INTO product_category (category_code, category_name, level) VALUES (?, ?, 1)", code, code);
        long category = id("SELECT category_id FROM product_category WHERE category_code = ?", code);
        jdbc.update("INSERT INTO product (product_code, product_name, spec_model, category_id, unit) VALUES (?, ?, 'test', ?, '个')", code, code, category);
        product = id("SELECT product_id FROM product WHERE product_code = ?", code);
        source = warehouse(code + "S", null, "中心库"); target = warehouse(code + "T", dept, "科室库");
        jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 10)", code, product);
        long batch = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", code);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty) VALUES (?, ?, ?, 10)", source, product, batch);
        jdbc.update("INSERT INTO department_requisition (requisition_no, dept_id, warehouse_id, source_warehouse_id, requisition_type, status, applicant_id) VALUES (?, ?, ?, ?, 'normal', 'approved', 1)", code, dept, target, source);
        long req = id("SELECT requisition_id FROM department_requisition WHERE requisition_no = ?", code);
        jdbc.update("INSERT INTO department_requisition_item (requisition_id, product_id, quantity, unit, item_type) VALUES (?, ?, 4, '个', 'loose')", req, product);
        long item = id("SELECT item_id FROM department_requisition_item WHERE requisition_id = ?", req);
        no = (String) delivery.confirmLoosePicking(Map.of("requisitionNo", code, "itemId", item, "warehouseName", code + "S", "quantity", 4)).get("deliveryNo");
        deliveryId = id("SELECT delivery_id FROM spd_delivery_order WHERE delivery_no = ?", no);
        String password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user (username, password, real_name, dept_id) VALUES (?, ?, ?, ?)", code, encoder.encode(password), code, dept);
        user = id("SELECT user_id FROM sys_user WHERE username = ?", code);
        jdbc.update("INSERT INTO sys_role (role_code, role_name, data_scope) VALUES (?, ?, 2)", code, code);
        role = id("SELECT role_id FROM sys_role WHERE role_code = ?", code);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", user, role);
        jdbc.update("INSERT INTO sys_role_perm (role_id, perm_id) SELECT ?, perm_id FROM sys_permission WHERE perm_code = 'picking-delivery:write' AND deleted = 0", role);
        JsonNode login = call("POST", "/auth/login", Map.of("username", code, "password", password));
        assertThat(login.path("code").asInt()).isZero(); token = login.path("data").path("token").asText();
        operation = new LinkedHashMap<>(Map.of("operationId", UUID.randomUUID().toString(), "deviceId", "acceptance-device",
                "kind", "SIGN_DELIVERY", "taskId", deliveryId, "deptId", dept, "warehouseId", target,
                "packageIds", List.of(), "traceCodeIds", List.of(), "looseConfirmed", true));
    }

    @Test void loginValidateSubmitReplayAndDatabaseReadback() throws Exception {
        assertThat(call("GET", "/auth/me", null).path("data").path("userId").asLong()).isEqualTo(user);
        assertThat(call("GET", "/mobile/context", null).path("data").path("capabilities").toString()).contains("SIGN_DELIVERY");
        assertThat(call("GET", "/mobile/tasks?deptId=" + dept + "&warehouseId=" + target, null).path("data").path("total").asLong()).isEqualTo(1);
        review();
        JsonNode first = call("POST", "/mobile/operations", operation);
        assertThat(first.path("code").asInt()).isZero();
        assertThat(call("POST", "/mobile/operations", operation).path("data")).isEqualTo(first.path("data"));
        assertThat(call("GET", "/mobile/operations/" + operation.get("operationId"), null).path("data")).isEqualTo(first.path("data"));
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ? AND operation_id = ? AND status = 'succeeded'", user, operation.get("operationId"))).isEqualTo(1);
        assertThat(stock(source)).isEqualByComparingTo("6"); assertThat(stock(target)).isEqualByComparingTo("4");
        assertThat(id("SELECT COUNT(*) FROM inventory_event WHERE source_biz_type = 'spd_delivery_order' AND source_biz_id = ? AND event_type = 'delivery_sign_in'", deliveryId)).isEqualTo(1);
        operation.put("deviceId", "changed-device");
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(409);
    }

    @Test void desktopWinsAfterReviewAndMobileRollsBackReceipt() throws Exception {
        review(); delivery.signDelivery(no);
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(409);
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isZero();
        assertThat(stock(target)).isEqualByComparingTo("4");
    }

    @Test void concurrentRetriesReturnOneReceiptAndOneInventoryMovement() throws Exception {
        review(); ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<JsonNode> task = () -> { start.await(); return call("POST", "/mobile/operations", operation); };
        try {
            Future<JsonNode> a = pool.submit(task), b = pool.submit(task); start.countDown();
            JsonNode first = a.get(30, TimeUnit.SECONDS), second = b.get(30, TimeUnit.SECONDS);
            assertThat(first.path("code").asInt()).isZero(); assertThat(second.path("code").asInt()).isZero();
            assertThat(first.path("data")).isEqualTo(second.path("data"));
            assertThat(stock(target)).isEqualByComparingTo("4");
            assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void missingConfirmationAndRevokedPermissionNeverWrite() throws Exception {
        operation.put("looseConfirmed", false);
        assertThat(call("POST", "/mobile/operations/validate", operation).path("code").asInt()).isEqualTo(409);
        operation.put("looseConfirmed", true); review();
        jdbc.update("DELETE FROM sys_role_perm WHERE role_id = ?", role);
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(403);
        assertThat(stock(target)).isZero();
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isZero();
    }

    @Test void permissionRepairRestoresOnlyMissingDefinitionWithoutGrantingRoles() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            long permission = id("SELECT perm_id FROM sys_permission WHERE perm_code = 'picking-delivery:write'");
            jdbc.update("DELETE FROM sys_role_perm WHERE perm_id = ?", permission);
            jdbc.update("DELETE FROM sys_permission WHERE perm_id = ?", permission);
            var script = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/V81__ensure_mobile_signing_permission.sql"));
            script.execute(jdbc.getDataSource());
            script.execute(jdbc.getDataSource());
            assertThat(id("SELECT COUNT(*) FROM sys_permission WHERE perm_code = 'picking-delivery:write' AND status = 1 AND deleted = 0")).isEqualTo(1);
            assertThat(id("SELECT COUNT(*) FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id = rp.perm_id WHERE p.perm_code = 'picking-delivery:write'")).isZero();
            jdbc.update("UPDATE sys_permission SET status = 0 WHERE perm_code = 'picking-delivery:write'");
            script.execute(jdbc.getDataSource());
            assertThat(id("SELECT status FROM sys_permission WHERE perm_code = 'picking-delivery:write'")).isZero();
            status.setRollbackOnly();
        });
    }

    private void review() throws Exception {
        JsonNode review = call("POST", "/mobile/operations/validate", operation);
        assertThat(review.path("code").asInt()).as(review.toString()).isZero();
        operation.put("reviewHash", review.path("data").path("reviewHash").asText());
    }
    private JsonNode call(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).timeout(java.time.Duration.ofSeconds(20));
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private long warehouse(String name, Long deptId, String type) {
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name, dept_id) VALUES (?, ?, ?, '测试', ?)", name, name, type, deptId);
        return id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", name);
    }
    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private BigDecimal stock(long warehouse) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(available_qty), 0) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?", BigDecimal.class, warehouse, product);
    }
}
