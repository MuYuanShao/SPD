package com.hospital.spd.supplychain.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Opt-in pricing migration and transaction checks in a newly created, isolated MySQL schema only. */
@EnabledIfEnvironmentVariable(named = "SPD_PRICING_MYSQL_TESTS", matches = "true")
class QuotaPackagePricingMysqlTest {
    @TempDir static Path migrations;
    static String schema;
    static String baseUrl;
    static String user;
    static String password;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static QuotaPackagePricingService pricing;
    static SettlementPointService settlement;
    static volatile CountDownLatch sourceRead;

    @BeforeAll
    static void prepareIsolatedDatabase() throws Exception {
        baseUrl = System.getenv("SPD_TEST_MYSQL_URL");
        user = System.getenv("SPD_TEST_MYSQL_USERNAME");
        password = System.getenv("SPD_TEST_MYSQL_PASSWORD");
        if (baseUrl == null || !baseUrl.matches("jdbc:mysql://[a-zA-Z0-9.:-]+/")) {
            throw new IllegalArgumentException("测试连接只能指定 MySQL 服务，不得指定已有业务库");
        }
        schema = "spd_price_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(baseUrl, user, password); var statement = c.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        var ds = new DriverManagerDataSource(baseUrl + schema + "?serverTimezone=Asia/Shanghai", user, password);
        jdbc = new JdbcTemplate(ds) {
            @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
                var result = super.queryForList(sql, args);
                if (sourceRead != null && sql.contains("FROM department_consumption dc") && sql.contains("JOIN inventory_batch ib")) sourceRead.countDown();
                return result;
            }
        };
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        pricing = new QuotaPackagePricingService(jdbc);
        jdbc.execute("CREATE TABLE product(product_id BIGINT UNSIGNED PRIMARY KEY,product_code VARCHAR(50),purchase_price DECIMAL(18,4),unit VARCHAR(20))");
        jdbc.execute("CREATE TABLE quota_package_label(label_id BIGINT UNSIGNED PRIMARY KEY,product_id BIGINT UNSIGNED,trace_code_id BIGINT,package_quantity DECIMAL(18,4),status VARCHAR(30))");
        jdbc.execute("CREATE TABLE quota_package_label_source(source_id BIGINT PRIMARY KEY,label_id BIGINT,batch_id BIGINT,source_qty DECIMAL(18,4),unit_price DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE inventory_batch(batch_id BIGINT PRIMARY KEY,receiving_item_id BIGINT,supplier_id BIGINT DEFAULT 1,batch_unit_price DECIMAL(18,4) DEFAULT 2,settlement_mode VARCHAR(30) DEFAULT 'department_consumption')");
        jdbc.execute("CREATE TABLE settlement_bill_item(item_id BIGINT AUTO_INCREMENT PRIMARY KEY,settlement_id BIGINT,source_biz_type VARCHAR(50),source_biz_id BIGINT,product_id BIGINT,batch_id BIGINT,trace_code_id BIGINT,quantity DECIMAL(18,4),unit_price DECIMAL(18,6),amount DECIMAL(18,4),UNIQUE KEY uk_source(source_biz_type,source_biz_id))");
        jdbc.execute("CREATE TABLE settlement_bill(settlement_id BIGINT AUTO_INCREMENT PRIMARY KEY,settlement_no VARCHAR(50),supplier_id BIGINT,settlement_period VARCHAR(20),settlement_mode VARCHAR(30),total_amount DECIMAL(18,4),status VARCHAR(30))");
        jdbc.execute("CREATE TABLE department_consumption(consumption_id BIGINT PRIMARY KEY,related_biz_type VARCHAR(50),related_biz_id BIGINT,status VARCHAR(30) DEFAULT 'confirmed',consume_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE department_consumption_item(item_id BIGINT PRIMARY KEY,consumption_id BIGINT,product_id BIGINT,batch_id BIGINT,trace_code_id BIGINT,quantity DECIMAL(18,4),unit_price DECIMAL(18,4),amount DECIMAL(18,4))");
        String file = "V95__quota_package_catalog_pricing.sql";
        try (var sql = QuotaPackagePricingMysqlTest.class.getResourceAsStream("/db/migration/" + file)) {
            Files.copy(java.util.Objects.requireNonNull(sql), migrations.resolve(file));
        }
        Flyway.configure().dataSource(ds).locations("filesystem:" + migrations)
                .baselineOnMigrate(true).baselineVersion("94").load().migrate();
    }

    @AfterAll
    static void removeOwnDatabase() throws Exception {
        if (schema == null) return;
        if (!schema.matches("spd_price_test_[a-f0-9]{32}")) throw new IllegalStateException("非法测试库名");
        try (var c = DriverManager.getConnection(baseUrl, user, password); var statement = c.createStatement()) {
            statement.execute("DROP DATABASE " + schema);
        }
    }

    @BeforeEach
    void seed() {
        for (String table : List.of("quota_package_price_change_item", "catalog_price_change", "settlement_bill_item", "settlement_bill",
                "department_consumption_item", "department_consumption", "quota_package_label_source",
                "quota_package_label", "inventory_batch", "product")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO product(product_id,product_code,purchase_price,unit) VALUES(1,'P1',2,'支')");
        sourceRead = null;
        label(1, "consumed");
        jdbc.update("INSERT INTO department_consumption(consumption_id,related_biz_type,related_biz_id) VALUES(4001,'quota_package_label',1)");
        jdbc.update("INSERT INTO department_consumption_item VALUES(3001,4001,1,101,1001,20,2,40)");
        var sequence = new java.util.concurrent.atomic.AtomicLong();
        var support = new com.hospital.spd.supplychain.SupplyChainSupport(null, null, null) {
            @Override public String nextNo(com.hospital.spd.common.service.DocumentKind kind) {
                return "JS-PRICE-" + sequence.incrementAndGet();
            }
            @Override public void writeAudit(String type, String action, Long id, String no, String remark) {
                // The fixture exercises bill/pricing persistence without the unrelated audit schema.
            }
        };
        settlement = new SettlementPointService(jdbc, support);
    }

    void label(long id, String status) {
        jdbc.update("INSERT INTO quota_package_label(label_id,product_id,trace_code_id,package_quantity,status) VALUES(?,1,?,20,?)", id, id + 1000, status);
        jdbc.update("INSERT INTO inventory_batch(batch_id,receiving_item_id) VALUES(?,?)", id + 100, id + 200);
        jdbc.update("INSERT INTO quota_package_label_source VALUES(?,?,?,20,2)", id, id, id + 100);
    }

    void change(String approval, String price) {
        tx.executeWithoutResult(status -> {
            Map<String, Object> before = pricing.lockProduct(1);
            jdbc.update("UPDATE product SET purchase_price=? WHERE product_id=1", new BigDecimal(price));
            pricing.afterCatalogApproval(approval, before, 1L);
        });
    }

    BigDecimal price(long labelId) {
        return jdbc.queryForObject("SELECT settlement_unit_price FROM quota_package_label WHERE label_id=?", BigDecimal.class, labelId);
    }

    @Test
    void repricesUnbilledAndConsumedPackagesButFreezesAnyExistingSettlement() {
        label(2, "signed");
        label(3, "consumed");
        label(4, "unpacked");
        label(5, "signed");
        label(6, "consumed");
        label(7, "void");
        jdbc.update("UPDATE quota_package_label_source SET source_qty=10 WHERE label_id=5");
        jdbc.update("INSERT INTO inventory_batch(batch_id,receiving_item_id) VALUES(555,755)");
        jdbc.update("INSERT INTO quota_package_label_source VALUES(55,5,555,10,2)");
        jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id,trace_code_id) VALUES('department_consumption_item',32,1002)");
        jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id) VALUES('receiving_order_item',205)");
        jdbc.update("INSERT INTO department_consumption(consumption_id,related_biz_type,related_biz_id) VALUES(66,'quota_package_label',6)");
        jdbc.update("INSERT INTO department_consumption_item(item_id,consumption_id) VALUES(67,66)");
        jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id) VALUES('department_consumption_item',67)");
        change("APP1", "3");
        assertThat(price(1)).isEqualByComparingTo("3");
        assertThat(price(3)).isEqualByComparingTo("3");
        for (long id : List.of(2L, 4L, 5L, 6L, 7L)) assertThat(price(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT affected_packages FROM catalog_price_change", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT difference_amount FROM quota_package_price_change_item WHERE label_id=1", BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT SUM(source_qty*unit_price) FROM quota_package_label_source WHERE label_id=1", BigDecimal.class)).isEqualByComparingTo("40");
    }

    @Test
    void preservesSuccessiveAdjustmentsAndIgnoresDuplicateApproval() {
        change("APP1", "3");
        tx.executeWithoutResult(status -> pricing.afterCatalogApproval("APP1", Map.of("productId", 1L, "unitPrice", new BigDecimal("2"), "unit", "支"), 1));
        change("APP2", "4");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_price_change", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT catalog_price_version FROM product", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT difference_amount FROM quota_package_price_change_item ORDER BY item_id", BigDecimal.class))
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("20"));
    }

    @Test
    void rollsBackCatalogVersionPackagesAndLedgerTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            Map<String, Object> before = pricing.lockProduct(1);
            jdbc.update("UPDATE product SET purchase_price=3 WHERE product_id=1");
            pricing.afterCatalogApproval("ROLLBACK", before, 1);
            throw new IllegalStateException("模拟审批后续步骤失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT purchase_price FROM product", BigDecimal.class)).isEqualByComparingTo("2");
        assertThat(price(1)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_price_change", Integer.class)).isZero();
    }

    @Test
    void zeroPriceIsValidAndCostSourceRemainsUnchanged() {
        change("FREE", "0");
        assertThat(price(1)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT difference_amount FROM quota_package_price_change_item", BigDecimal.class)).isEqualByComparingTo("-40");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM quota_package_label_source", BigDecimal.class)).isEqualByComparingTo("2");
    }

    @Test
    void legacyConsumptionUsesStableLabelRelationWithoutChangingOriginalCost() {
        change("LEGACY", "3");
        jdbc.update("INSERT INTO department_consumption(consumption_id,related_biz_type,related_biz_id) VALUES(88,'quota_package_label',1)");
        jdbc.update("DELETE FROM department_consumption_item WHERE item_id=3001");
        jdbc.update("INSERT INTO department_consumption_item VALUES(89,88,1,101,NULL,20,2,40)");
        var source = Map.<String, Object>of("productId", 1L, "sourceBizType", "department_consumption_item",
                "sourceBizId", 89L, "quantity", new BigDecimal("20"), "unitPrice", new BigDecimal("2"), "amount", new BigDecimal("40"));
        var priced = tx.execute(status -> pricing.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION, List.of(source)));
        assertThat((BigDecimal) priced.get(0).get("amount")).isEqualByComparingTo("60");
        assertThat(priced.get(0).get("traceCodeId")).isEqualTo(1001L);
        assertThat((BigDecimal) source.get("amount")).isEqualByComparingTo("40");
    }

    @Test
    void newPackageUsesCurrentCatalogWhileAlreadyBilledSourceKeepsOriginalCost() {
        change("BEFORE-PACKING", "3");
        label(2, "pending_print");
        tx.executeWithoutResult(status -> pricing.initializePackage(2, 1));
        assertThat(price(2)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM quota_package_label_source WHERE label_id=2", BigDecimal.class))
                .isEqualByComparingTo("2");
        label(3, "pending_print");
        jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id) VALUES('receiving_order_item',203)");
        tx.executeWithoutResult(status -> pricing.initializePackage(3, 1));
        assertThat(price(3)).isNull();
    }

    @Test
    void oldApprovalReadSnapshotCannotMissANewlyCommittedSettlement() {
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM settlement_bill_item", Long.class);
            TransactionTemplate fresh = new TransactionTemplate(tx.getTransactionManager());
            fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            fresh.executeWithoutResult(inner -> {
                pricing.lockProduct(1);
                jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id,trace_code_id) VALUES('department_consumption_item',9001,1001)");
            });
            Map<String, Object> before = pricing.lockProduct(1);
            jdbc.update("UPDATE product SET purchase_price=3 WHERE product_id=1");
            pricing.afterCatalogApproval("SNAPSHOT-BILL", before, 1);
        });
        assertThat(price(1)).isNull();
    }

    @Test
    void oldApprovalReadSnapshotStillIncludesNewlyCommittedPackages() {
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM quota_package_label_source", Long.class);
            TransactionTemplate fresh = new TransactionTemplate(tx.getTransactionManager());
            fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            fresh.executeWithoutResult(inner -> {
                pricing.lockProduct(1);
                label(2, "pending_print");
                pricing.initializePackage(2, 1);
            });
            Map<String, Object> before = pricing.lockProduct(1);
            jdbc.update("UPDATE product SET purchase_price=3 WHERE product_id=1");
            pricing.afterCatalogApproval("SNAPSHOT-PACKAGE", before, 1);
        });
        assertThat(price(2)).isEqualByComparingTo("3");
    }

    @Test
    void settlementAndRepricingSerializeWithoutMixedVersions() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch settleStarted = new CountDownLatch(1);
        try {
            var adjustment = executor.submit(() -> tx.executeWithoutResult(status -> {
                Map<String, Object> before = pricing.lockProduct(1);
                locked.countDown();
                try { assertThat(settleStarted.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                jdbc.update("UPDATE product SET purchase_price=3 WHERE product_id=1");
                pricing.afterCatalogApproval("CONCURRENT", before, 1);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var settlement = executor.submit(() -> tx.execute(status -> {
                settleStarted.countDown();
                var rows = pricing.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION,
                        List.of(Map.of("productId", 1L, "traceCodeId", 1001L, "sourceBizId", 3001L, "quantity", new BigDecimal("20"),
                                "unitPrice", new BigDecimal("2"), "amount", new BigDecimal("40"))));
                jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id,trace_code_id,unit_price,amount) VALUES('department_consumption_item',3001,1001,?,?)",
                        rows.get(0).get("unitPrice"), rows.get(0).get("amount"));
                return rows.get(0);
            }));
            adjustment.get(10, TimeUnit.SECONDS);
            assertThat((BigDecimal) settlement.get(10, TimeUnit.SECONDS).get("amount")).isEqualByComparingTo("60");
            change("NEXT", "4");
            assertThat(price(1)).isEqualByComparingTo("3");
            assertThat(jdbc.queryForObject("SELECT amount FROM settlement_bill_item", BigDecimal.class)).isEqualByComparingTo("60");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void settlementThatWinsTheLockFreezesPriceBeforeConcurrentApproval() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch billed = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        try {
            var settlement = executor.submit(() -> tx.executeWithoutResult(status -> {
                var rows = pricing.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION,
                        List.of(Map.of("productId", 1L, "traceCodeId", 1001L, "sourceBizId", 3001L, "quantity", new BigDecimal("20"),
                                "unitPrice", new BigDecimal("2"), "amount", new BigDecimal("40"))));
                jdbc.update("INSERT INTO settlement_bill_item(source_biz_type,source_biz_id,trace_code_id,unit_price,amount) VALUES('department_consumption_item',3001,1001,?,?)",
                        rows.get(0).get("unitPrice"), rows.get(0).get("amount"));
                billed.countDown();
                try { assertThat(approvalStarted.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }));
            assertThat(billed.await(5, TimeUnit.SECONDS)).isTrue();
            var approval = executor.submit(() -> {
                approvalStarted.countDown();
                change("LATE", "3");
            });
            settlement.get(10, TimeUnit.SECONDS);
            approval.get(10, TimeUnit.SECONDS);
            assertThat(price(1)).isNull();
            assertThat(jdbc.queryForObject("SELECT amount FROM settlement_bill_item", BigDecimal.class)).isEqualByComparingTo("40");
            assertThat(jdbc.queryForObject("SELECT affected_packages FROM catalog_price_change", Integer.class)).isZero();
        } finally { executor.shutdownNow(); }
    }

    private void fractionalMixedPackage() {
        jdbc.update("UPDATE quota_package_label SET package_quantity=1 WHERE label_id=1");
        jdbc.update("UPDATE quota_package_label_source SET source_qty=0.5 WHERE label_id=1");
        jdbc.update("INSERT INTO inventory_batch(batch_id,receiving_item_id,supplier_id) VALUES(102,202,2)");
        jdbc.update("INSERT INTO quota_package_label_source VALUES(2,1,102,0.5,2)");
        jdbc.update("UPDATE department_consumption_item SET quantity=0.5,amount=1 WHERE item_id=3001");
        jdbc.update("INSERT INTO department_consumption_item VALUES(3002,4001,1,102,1001,0.5,2,1)");
        change("FRACTIONAL", "2.0101");
    }

    @Test
    void realConsumptionSettlementConservesPackageAmountAcrossSuppliers() {
        fractionalMixedPackage();
        var bills = tx.execute(status -> settlement.generateForConsumption(4001L));
        assertThat(bills).hasSize(2);
        BigDecimal target = jdbc.queryForObject("SELECT new_amount FROM quota_package_price_change_item", BigDecimal.class);
        assertThat(target).isEqualByComparingTo("2.0101");
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM settlement_bill", BigDecimal.class)).isEqualByComparingTo(target);
        assertThat(jdbc.queryForList("SELECT amount FROM settlement_bill_item ORDER BY source_biz_id", BigDecimal.class))
                .containsExactly(new BigDecimal("1.0051"), new BigDecimal("1.0050"));
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM department_consumption_item", BigDecimal.class)).isEqualByComparingTo("2");
    }

    @Test
    void supplierFilteredCatchupKeepsPreviouslyBilledPartAndConservesWholePackage() {
        fractionalMixedPackage();
        var day = java.time.LocalDate.now();
        tx.executeWithoutResult(status -> settlement.generateMissing(new ManualSettlementGenerationRequest(
                SettlementMode.DEPARTMENT_CONSUMPTION, day, day, 2L)));
        BigDecimal first = jdbc.queryForObject("SELECT amount FROM settlement_bill_item WHERE source_biz_id=3002", BigDecimal.class);
        assertThat(first).isEqualByComparingTo("1.0050");
        tx.executeWithoutResult(status -> settlement.generateMissing(new ManualSettlementGenerationRequest(
                SettlementMode.DEPARTMENT_CONSUMPTION, day, day, 1L)));
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM settlement_bill", BigDecimal.class)).isEqualByComparingTo("2.0101");
        assertThat(jdbc.queryForObject("SELECT amount FROM settlement_bill_item WHERE source_biz_id=3002", BigDecimal.class)).isEqualByComparingTo(first);
    }

    @Test
    void actualConsumptionAndCatchupEntriesLockProductsBeforeSourceRows() throws Exception {
        for (boolean manual : List.of(false, true)) {
            seed();
            var executor = Executors.newFixedThreadPool(2);
            CountDownLatch productLocked = new CountDownLatch(1);
            sourceRead = new CountDownLatch(1);
            try {
                var approval = executor.submit(() -> tx.executeWithoutResult(status -> {
                    Map<String, Object> before = pricing.lockProduct(1);
                    productLocked.countDown();
                    try { assertThat(sourceRead.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException e) { throw new IllegalStateException(e); }
                    jdbc.update("UPDATE product SET purchase_price=3 WHERE product_id=1");
                    pricing.afterCatalogApproval("ACTUAL-ENTRY", before, 1);
                }));
                assertThat(productLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var bill = executor.submit(() -> tx.executeWithoutResult(status -> {
                    if (manual) {
                        var day = java.time.LocalDate.now();
                        settlement.generateMissing(new ManualSettlementGenerationRequest(
                                SettlementMode.DEPARTMENT_CONSUMPTION, day, day, null));
                    } else {
                        settlement.generateForConsumption(4001L);
                    }
                }));
                approval.get(15, TimeUnit.SECONDS);
                bill.get(15, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM settlement_bill", BigDecimal.class)).isEqualByComparingTo("60");
                assertThat(price(1)).isEqualByComparingTo("3");
            } finally { sourceRead = null; executor.shutdownNow(); }
        }
    }
}
