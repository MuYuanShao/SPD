package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = "spring.flyway.ignore-migration-patterns=*:missing")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class OperationalHighValueMysqlConcurrencyTest {

    private static IsolatedMysqlDatabase database;

    @org.springframework.test.context.DynamicPropertySource
    static void isolatedDatabase(org.springframework.test.context.DynamicPropertyRegistry properties) {
        database = new IsolatedMysqlDatabase();
        database.register(properties);
    }

    @org.junit.jupiter.api.AfterAll
    static void dropTestDatabase() {
        if (database != null) database.close();
    }

    @Autowired OperationalHighValueModule module;
    @Autowired InventoryMovementService inventoryMovementService;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long fixtureBalanceId;

    @org.junit.jupiter.api.BeforeEach
    void createIndependentInventoryFixture() {
        String code = "IT-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO product_category (category_code, category_name, level)
                VALUES (?, '隔离测试分类', 1)
                """, code);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM product_category WHERE category_code = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO product (product_code, product_name, spec_model, category_id, unit, is_high_value)
                VALUES (?, '隔离测试耗材', '测试规格', ?, '个', 1)
                """, code, categoryId);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM product WHERE product_code = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name)
                VALUES (?, ?, '中心库', '隔离测试院区')
                """, code, code);
        Long warehouseId = jdbcTemplate.queryForObject(
                "SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price)
                VALUES (?, ?, 10)
                """, code, productId);
        Long batchId = jdbcTemplate.queryForObject(
                "SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty)
                VALUES (?, ?, ?, 10)
                """, warehouseId, productId, batchId);
        fixtureBalanceId = jdbcTemplate.queryForObject(
                "SELECT balance_id FROM inventory_balance WHERE batch_id = ?", Long.class, batchId);
    }

    @Test
    void maintenanceVariableCannotBypassImmutableEventOrTraceGuards() throws Exception {
        Map<String, Object> fixture = jdbcTemplate.queryForMap(
                "SELECT warehouse_id, product_id, batch_id FROM inventory_balance WHERE balance_id = ?",
                fixtureBalanceId);
        Long eventId = inventoryMovementService.receiveAvailable(
                ((Number) fixture.get("warehouse_id")).longValue(),
                ((Number) fixture.get("product_id")).longValue(),
                ((Number) fixture.get("batch_id")).longValue(), BigDecimal.ONE,
                "purchase_receive_in", "receiving_order", 1L, "隔离测试入库");
        String traceCode = "IT-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO udi_trace_code
                  (udi_code, unique_code, trace_scope, product_code, product_name,
                   current_location, current_status, risk_level, last_event_time)
                VALUES (?, ?, 'high_value', 'IT', '隔离测试', '隔离测试库', 'in_stock', 'normal', NOW())
                """, traceCode, traceCode);
        Long traceId = jdbcTemplate.queryForObject(
                "SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", Long.class, traceCode);
        jdbcTemplate.update("""
                INSERT INTO inventory_event_trace_code (event_id, trace_code_id, trace_type, linked_quantity)
                VALUES (?, ?, 'high_value_unit', 1)
                """, eventId, traceId);
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            // Set and exercise the former bypass on the same physical MySQL connection.
            statement.execute("SET @spd_allow_inventory_event_maintenance = 1");
            for (String mutation : List.of(
                    "UPDATE inventory_event SET remark = 'changed' WHERE event_id = " + eventId,
                    "DELETE FROM inventory_event WHERE event_id = " + eventId,
                    "UPDATE inventory_event_trace_code SET linked_quantity = 2 WHERE event_id = " + eventId,
                    "DELETE FROM inventory_event_trace_code WHERE event_id = " + eventId)) {
                Throwable failure = catchThrowable(() -> statement.executeUpdate(mutation));
                assertThat(failure).isInstanceOf(java.sql.SQLException.class);
                assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo("45000");
                assertThat(failure).hasMessageContaining("immutable");
            }
            statement.execute("SET @spd_allow_inventory_event_maintenance = NULL");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_event WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT linked_quantity FROM inventory_event_trace_code WHERE event_id = ?",
                BigDecimal.class, eventId)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void inventoryMovementRollsBackBalanceWhenEventRecordingFails() {
        Map<String, Object> fixture = jdbcTemplate.queryForMap("""
                SELECT balance_id AS balanceId, available_qty AS availableQty,
                       warehouse_id AS warehouseId, product_id AS productId, batch_id AS batchId
                  FROM inventory_balance
                 WHERE balance_id = ?
                """, fixtureBalanceId);
        BigDecimal before = (BigDecimal) fixture.get("availableQty");
        String eventType = "atomic_" + UUID.randomUUID().toString().substring(0, 8);
        int eventsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_event WHERE event_type = ?", Integer.class, eventType);

        try {
            Throwable failure = catchThrowable(() -> inventoryMovementService.consumeSpecificBatch(
                    ((Number) fixture.get("warehouseId")).longValue(),
                    ((Number) fixture.get("productId")).longValue(),
                    ((Number) fixture.get("batchId")).longValue(),
                    BigDecimal.ONE,
                    eventType,
                    "inventory_atomicity_test",
                    null,
                    "force event failure after balance mutation"));
            assertThat(failure).isInstanceOf(RuntimeException.class);

            BigDecimal after = jdbcTemplate.queryForObject(
                    "SELECT available_qty FROM inventory_balance WHERE balance_id = ?",
                    BigDecimal.class,
                    fixture.get("balanceId"));
            assertThat(after).isEqualByComparingTo(before);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_event WHERE event_type = ?", Integer.class, eventType))
                    .isEqualTo(eventsBefore);
        } finally {
            jdbcTemplate.update("""
                    UPDATE inventory_balance
                       SET available_qty = ?
                     WHERE balance_id = ? AND available_qty = ?
                    """, before, fixture.get("balanceId"), before.subtract(BigDecimal.ONE));
        }
    }

    @Test
    void concurrentBillingCallbacksDeductInventoryExactlyOnce() throws Exception {
        Map<String, Object> fixture = jdbcTemplate.queryForMap("""
                SELECT bal.balance_id AS balanceId, bal.available_qty AS availableQty,
                       bal.last_event_id AS lastEventId, bal.warehouse_id AS warehouseId,
                       bal.batch_id AS batchId,
                       w.warehouse_name AS warehouseName, bal.product_id AS productId,
                       p.product_code AS productCode, p.product_name AS productName
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                 WHERE bal.balance_id = ?
                """, fixtureBalanceId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String uniqueCode = "IT-UID-" + suffix;
        String udiCode = "IT-UDI-" + suffix;
        String externalNo = "IT-EXT-" + suffix;
        Long traceId = null;
        List<Map<String, Object>> balanceSnapshot = jdbcTemplate.queryForList("""
                SELECT balance_id AS balanceId, available_qty AS availableQty, last_event_id AS lastEventId
                  FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?
                """, fixture.get("warehouseId"), fixture.get("productId"));
        BigDecimal totalBefore = balanceSnapshot.stream()
                .map(row -> (BigDecimal) row.get("availableQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            jdbcTemplate.update("""
                    INSERT INTO udi_trace_code
                      (udi_code, unique_code, trace_scope, product_code, product_name, current_location,
                       current_status, risk_level, last_event_time)
                    VALUES (?, ?, 'high_value', ?, ?, ?, 'in_stock', 'normal', NOW())
                    """, udiCode, uniqueCode, fixture.get("productCode"), fixture.get("productName"), fixture.get("warehouseName"));
            traceId = jdbcTemplate.queryForObject("SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", Long.class, uniqueCode);
            jdbcTemplate.update("""
                    INSERT INTO inventory_batch_trace_code
                      (batch_id, trace_code_id, receiving_item_id, current_warehouse_id, lifecycle_status)
                    VALUES (?, ?, 0, ?, 'in_stock')
                    """, fixture.get("batchId"), traceId, fixture.get("warehouseId"));

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Map<String, Object>> callback = () -> {
                ready.countDown();
                start.await();
                return module.receiveBillingCallback(Map.of(
                        "externalChargeNo", externalNo,
                        "uniqueCode", uniqueCode,
                        "productCode", fixture.get("productCode"),
                        "warehouseName", fixture.get("warehouseName"),
                        "deptName", "并发测试科室",
                        "patientNo", "IT-PATIENT",
                        "quantity", BigDecimal.ONE));
            };
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Map<String, Object>> first = executor.submit(callback);
                Future<Map<String, Object>> second = executor.submit(callback);
                ready.await();
                start.countDown();
                List<Map<String, Object>> results = List.of(first.get(), second.get());
                assertThat(results).extracting(result -> result.get("idempotent"))
                        .containsExactlyInAnyOrder(false, true);
            } finally {
                executor.shutdownNow();
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM high_value_charge WHERE external_charge_no = ?", Integer.class, externalNo))
                    .isEqualTo(1);
            BigDecimal after = jdbcTemplate.queryForObject(
                    "SELECT SUM(available_qty) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?",
                    BigDecimal.class, fixture.get("warehouseId"), fixture.get("productId"));
            assertThat(after).isEqualByComparingTo(totalBefore.subtract(BigDecimal.ONE));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trace_code_id FROM high_value_charge WHERE external_charge_no = ?", Long.class, externalNo))
                    .isEqualTo(traceId);
        } finally {
            // The entire isolated database is removed after this test class, including immutable events.
        }
    }
}
