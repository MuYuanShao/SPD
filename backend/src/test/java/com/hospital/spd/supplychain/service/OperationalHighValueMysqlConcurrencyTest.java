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
    @Autowired OperationalClosureReadModel readModel;
    @Autowired OperationalConsumptionModule consumption;
    @Autowired com.hospital.spd.specialty.service.QuotaPackageTraceFlowService quotaTrace;
    @Autowired QuotaPackagePricingService pricing;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private Long fixtureBalanceId;

    @Test
    void consumeRepriceReverseAndConsumeAgainUsesLatestPriceWithoutChangingSourceCost() {
        Map<String, Object> f = jdbcTemplate.queryForMap("SELECT warehouse_id,product_id,batch_id FROM inventory_balance WHERE balance_id=?", fixtureBalanceId);
        String code = "RV-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO sys_dept(dept_code,dept_name) VALUES(?,?)", code, code);
        Long dept = jdbcTemplate.queryForObject("SELECT dept_id FROM sys_dept WHERE dept_code=?", Long.class, code);
        jdbcTemplate.update("UPDATE warehouse SET dept_id=?,warehouse_type='二级库' WHERE warehouse_id=?", dept, f.get("warehouse_id"));
        jdbcTemplate.update("""
                INSERT INTO supplier(supplier_code,supplier_name,credit_code,supplier_type,contact_name,contact_phone)
                VALUES(?,?,?,'配送商','测试联系人','13800000000')
                """, code, code, "91" + code.substring(3, 19).toUpperCase());
        Long supplier = jdbcTemplate.queryForObject("SELECT supplier_id FROM supplier WHERE supplier_code=?", Long.class, code);
        jdbcTemplate.update("UPDATE product SET is_high_value=0,is_quota_managed=1,purchase_price=2.5,supplier_id=? WHERE product_id=?", supplier, f.get("product_id"));
        jdbcTemplate.update("UPDATE inventory_batch SET supplier_id=?,settlement_mode='department_consumption',batch_unit_price=2.5 WHERE batch_id=?", supplier, f.get("batch_id"));
        jdbcTemplate.update("INSERT INTO quota_package_template(template_code,template_name,dept_id,status) VALUES(?,?,?,1)", code, code, dept);
        Long template = jdbcTemplate.queryForObject("SELECT template_id FROM quota_package_template WHERE template_code=?", Long.class, code);
        jdbcTemplate.update("INSERT INTO quota_package_template_item(template_id,product_id,quantity,unit) VALUES(?,?,10,'个')", template, f.get("product_id"));
        long label = createPackageFixture(f, "signed", "10");
        jdbcTemplate.update("UPDATE quota_package_label SET template_id=?,settlement_unit_price=2.5,catalog_price_version=0 WHERE label_id=?", template, label);
        jdbcTemplate.update("INSERT INTO quota_package_label_source(label_id,batch_id,source_qty,unit_price,warehouse_id) VALUES(?,?,10,2.5,?)", label, f.get("batch_id"), f.get("warehouse_id"));
        String labelNo = jdbcTemplate.queryForObject("SELECT label_no FROM quota_package_label WHERE label_id=?", String.class, label);
        var first = quotaTrace.consumeByCode(Map.of("code", labelNo));
        Long traceId = jdbcTemplate.queryForObject("SELECT trace_code_id FROM quota_package_label WHERE label_id=?", Long.class, label);
        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM settlement_bill_item WHERE trace_code_id=?", BigDecimal.class, traceId)).isEqualByComparingTo("25");
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            var before = pricing.lockProduct(((Number) f.get("product_id")).longValue());
            jdbcTemplate.update("UPDATE product SET purchase_price=3.75 WHERE product_id=?", f.get("product_id"));
            pricing.afterCatalogApproval(code, before, 1L);
        });
        consumption.reverseConsumption(String.valueOf(first.get("consumptionNo")));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_bill_item WHERE trace_code_id=?", Integer.class, traceId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT settlement_unit_price FROM quota_package_label WHERE label_id=?", BigDecimal.class, label)).isEqualByComparingTo("3.75");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE operation_type='refresh_reversed_package_price' AND JSON_UNQUOTE(JSON_EXTRACT(after_data,'$.bizNo'))=?", Integer.class, first.get("consumptionNo"))).isEqualTo(1);
        quotaTrace.consumeByCode(Map.of("code", labelNo));
        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM settlement_bill_item WHERE trace_code_id=?", BigDecimal.class, traceId)).isEqualByComparingTo("37.5");
        assertThat(jdbcTemplate.queryForObject("SELECT SUM(source_qty*unit_price) FROM quota_package_label_source WHERE label_id=?", BigDecimal.class, label)).isEqualByComparingTo("25");
    }

    @Test
    void fifoProtectsSignedPackagesButConsumesLooseRemainder() {
        Map<String, Object> fixture = jdbcTemplate.queryForMap(
                "SELECT warehouse_id, product_id, batch_id FROM inventory_balance WHERE balance_id = ?", fixtureBalanceId);
        Long warehouse = ((Number) fixture.get("warehouse_id")).longValue();
        Long product = ((Number) fixture.get("product_id")).longValue();
        Long batch = ((Number) fixture.get("batch_id")).longValue();
        long label = createPackageFixture(fixture, "signed", "10");
        jdbcTemplate.update("""
                INSERT INTO quota_package_label_source(label_id,batch_id,source_qty,unit_price,warehouse_id)
                VALUES(?,?,10,10,?)
                """, label, batch, warehouse);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inventoryMovementService.consumeAvailableFifo(
                warehouse, product, BigDecimal.ONE, "department_consumption_out", "department_consumption", 1L, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT available_qty FROM inventory_balance WHERE balance_id=?",
                BigDecimal.class, fixtureBalanceId)).isEqualByComparingTo("10");
        inventoryMovementService.receiveAvailable(warehouse, product, batch, new BigDecimal("2"),
                "purchase_receive_in", "receiving_order", 1L, "fixture loose receipt");
        var deductions = inventoryMovementService.consumeAvailableFifo(warehouse, product, new BigDecimal("2"),
                "department_consumption_out", "department_consumption", 1L, "test");
        assertThat(deductions).hasSize(1);
        assertThat(deductions.get(0).quantity()).isEqualByComparingTo("2");
        assertThat(jdbcTemplate.queryForObject("SELECT available_qty FROM inventory_balance WHERE balance_id=?",
                BigDecimal.class, fixtureBalanceId)).isEqualByComparingTo("10");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM quota_package_label WHERE label_id=?",
                String.class, label)).isEqualTo("signed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void settlementProjectionClassifiesQuantitiesAndCountsCrossSupplierPackageOnceBeforePagination() {
        Map<String, Object> fixture = jdbcTemplate.queryForMap(
                "SELECT warehouse_id, product_id, batch_id FROM inventory_balance WHERE balance_id = ?", fixtureBalanceId);
        long label = createPackageFixture(fixture, "consumed", "10");
        jdbcTemplate.update("UPDATE quota_package_label SET settlement_unit_price=3 WHERE label_id=?", label);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO department_consumption(consumption_no,dept_id,warehouse_id,consumption_type,related_biz_type,related_biz_id,status)
                VALUES(?,1,?,'quota_package_scan','quota_package_label',?,'confirmed')
                """, "DC-" + suffix, fixture.get("warehouse_id"), label);
        Long consumption = jdbcTemplate.queryForObject("SELECT consumption_id FROM department_consumption WHERE consumption_no=?", Long.class, "DC-" + suffix);
        for (int i = 0; i < 2; i++) {
            String code = suffix + i;
            jdbcTemplate.update("""
                    INSERT INTO supplier(supplier_code,supplier_name,credit_code,supplier_type,contact_name,contact_phone)
                    VALUES(?,?,?,'distributor','测试联系人','13800000000')
                    """, code, code, suffix.substring(0, 17) + i);
            Long supplier = jdbcTemplate.queryForObject("SELECT supplier_id FROM supplier WHERE supplier_code=?", Long.class, code);
            jdbcTemplate.update("""
                    INSERT INTO settlement_bill(settlement_no,supplier_id,settlement_period,settlement_mode,total_amount,status)
                    VALUES(?,?,'2026-10','department_consumption',15,'draft')
                    """, "JS-" + code, supplier);
            Long bill = jdbcTemplate.queryForObject("SELECT settlement_id FROM settlement_bill WHERE settlement_no=?", Long.class, "JS-" + code);
            jdbcTemplate.update("""
                    INSERT INTO department_consumption_item(consumption_id,product_id,batch_id,quantity,unit_price,amount)
                    VALUES(?,?,?,5,2,10)
                    """, consumption, fixture.get("product_id"), fixture.get("batch_id"));
            Long item = jdbcTemplate.queryForObject("SELECT MAX(item_id) FROM department_consumption_item WHERE consumption_id=?", Long.class, consumption);
            jdbcTemplate.update("""
                    INSERT INTO settlement_bill_item(settlement_id,source_biz_type,source_biz_id,product_id,batch_id,quantity,unit_price,amount)
                    VALUES(?,'department_consumption_item',?,?,?,5,3,15)
                    """, bill, item, fixture.get("product_id"), fixture.get("batch_id"));
        }
        var page = readModel.list("settlement", Map.of("goodsType", "quota_package", "size", "200"));
        var packages = ((List<Map<String, Object>>) page.get("rows")).stream()
                .filter(row -> ((Number) row.get("packageLabelId")).longValue() == label).toList();
        assertThat(packages).hasSize(2);
        assertThat(packages.stream().mapToInt(row -> ((Number) row.get("packageCount")).intValue()).sum()).isEqualTo(1);
        assertThat(packages).allSatisfy(row -> {
            assertThat((BigDecimal) row.get("packageEquivalentCount")).isEqualByComparingTo("0.5");
            assertThat((BigDecimal) row.get("packageBaseQuantity")).isEqualByComparingTo("5");
            assertThat((BigDecimal) row.get("packageAmount")).isEqualByComparingTo("30");
            assertThat((BigDecimal) row.get("looseQuantity")).isZero();
            assertThat((BigDecimal) row.get("highValueQuantity")).isZero();
        });
        var first = readModel.list("settlement", Map.of("goodsType", "quota_package", "size", "1", "page", "1"));
        var second = readModel.list("settlement", Map.of("goodsType", "quota_package", "size", "1", "page", "2"));
        assertThat(first.get("total")).isEqualTo(page.get("total"));
        long firstId = ((Number) ((List<Map<String,Object>>) first.get("rows")).get(0).get("settlementItemId")).longValue();
        long secondId = ((Number) ((List<Map<String,Object>>) second.get("rows")).get(0).get("settlementItemId")).longValue();
        assertThat(firstId).isNotEqualTo(secondId);

        Long bill = jdbcTemplate.queryForObject("SELECT settlement_id FROM settlement_bill WHERE settlement_no=?",
                Long.class, "JS-" + suffix + "1");
        // Receipt-triggered high-value settlement has no billing callback or trace link yet.
        jdbcTemplate.update("""
                INSERT INTO settlement_bill_item(settlement_id,source_biz_type,source_biz_id,product_id,batch_id,quantity,unit_price,amount)
                VALUES(?,'receiving_order_item',?,?,?,1,10,10)
                """, bill, 100000000L + label, fixture.get("product_id"), fixture.get("batch_id"));
        Long highValueItem = jdbcTemplate.queryForObject("SELECT MAX(item_id) FROM settlement_bill_item WHERE settlement_id=?", Long.class, bill);
        var highValuePage = readModel.list("settlement", Map.of("goodsType", "high_value", "size", "200"));
        var highValueRows = (List<Map<String, Object>>) highValuePage.get("rows");
        assertThat(highValueRows).filteredOn(row -> ((Number) row.get("settlementItemId")).longValue() == highValueItem)
                .singleElement().satisfies(row -> {
                    assertThat((BigDecimal) row.get("highValueQuantity")).isEqualByComparingTo("1");
                    assertThat((BigDecimal) row.get("looseQuantity")).isZero();
                });

        String looseCode = "LOOSE-" + suffix;
        jdbcTemplate.update("""
                INSERT INTO product(product_code,product_name,spec_model,category_id,unit,is_high_value)
                SELECT ?,'散货测试','测试规格',category_id,'支',0 FROM product WHERE product_id=?
                """, looseCode, fixture.get("product_id"));
        Long looseProduct = jdbcTemplate.queryForObject("SELECT product_id FROM product WHERE product_code=?", Long.class, looseCode);
        jdbcTemplate.update("""
                INSERT INTO settlement_bill_item(settlement_id,source_biz_type,source_biz_id,product_id,quantity,unit_price,amount)
                VALUES(?,'receiving_order_item',?,?,2,10,20)
                """, bill, 200000000L + label, looseProduct);
        Long looseItem = jdbcTemplate.queryForObject("SELECT MAX(item_id) FROM settlement_bill_item WHERE settlement_id=?", Long.class, bill);
        var loosePage = readModel.list("settlement", Map.of("goodsType", "loose", "size", "200"));
        assertThat((List<Map<String, Object>>) loosePage.get("rows"))
                .filteredOn(row -> ((Number) row.get("settlementItemId")).longValue() == looseItem)
                .singleElement().satisfies(row -> {
                    assertThat((BigDecimal) row.get("looseQuantity")).isEqualByComparingTo("2");
                    assertThat((BigDecimal) row.get("highValueQuantity")).isZero();
                });
    }

    private long createPackageFixture(Map<String, Object> fixture, String status, String quantity) {
        String labelNo = "QP-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO quota_package_label(label_no,template_id,warehouse_id,product_id,package_quantity,status)
                VALUES(?,0,?,?,?,?)
                """, labelNo, fixture.get("warehouse_id"), fixture.get("product_id"), new BigDecimal(quantity), status);
        return jdbcTemplate.queryForObject("SELECT label_id FROM quota_package_label WHERE label_no=?", Long.class, labelNo);
    }

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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void concurrentBillingCallbacksDeductInventoryExactlyOnce(boolean sameExternalNo) throws Exception {
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
            var sequence = new java.util.concurrent.atomic.AtomicInteger();
            Callable<Object> callback = () -> {
                ready.countDown();
                start.await();
                try {
                    return module.receiveBillingCallback(Map.of(
                        "externalChargeNo", externalNo + (sameExternalNo ? "" : sequence.incrementAndGet()),
                        "uniqueCode", uniqueCode,
                        "productCode", fixture.get("productCode"),
                        "warehouseName", fixture.get("warehouseName"),
                        "deptName", "并发测试科室",
                        "patientNo", "IT-PATIENT",
                        "amount", new BigDecimal("120.12345"),
                        "quantity", BigDecimal.ONE));
                } catch (IllegalArgumentException ex) {
                    return ex;
                }
            };
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Object> first = executor.submit(callback);
                Future<Object> second = executor.submit(callback);
                ready.await();
                start.countDown();
                List<Object> results = List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(20, java.util.concurrent.TimeUnit.SECONDS));
                if (sameExternalNo) {
                    assertThat(results).allMatch(result -> result instanceof Map);
                    assertThat(results).extracting(result -> (Boolean) ((Map<?, ?>) result).get("idempotent"))
                            .containsExactlyInAnyOrder(false, true);
                } else {
                    assertThat(results.stream().filter(result -> result instanceof Map).count()).isEqualTo(1);
                    assertThat(results.stream().filter(result -> result instanceof IllegalArgumentException).count()).isEqualTo(1);
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM high_value_charge WHERE external_charge_no LIKE ?", Integer.class, externalNo + "%"))
                    .isEqualTo(1);
            BigDecimal after = jdbcTemplate.queryForObject(
                    "SELECT SUM(available_qty) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?",
                    BigDecimal.class, fixture.get("warehouseId"), fixture.get("productId"));
            assertThat(after).isEqualByComparingTo(totalBefore.subtract(BigDecimal.ONE));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trace_code_id FROM high_value_charge WHERE external_charge_no LIKE ?", Long.class, externalNo + "%"))
                    .isEqualTo(traceId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT amount FROM high_value_charge WHERE external_charge_no LIKE ?", BigDecimal.class, externalNo + "%"))
                    .isEqualByComparingTo("120.1235");
        } finally {
            // The entire isolated database is removed after this test class, including immutable events.
        }
    }
}
