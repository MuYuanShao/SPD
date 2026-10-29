package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises review regressions in a disposable schema on an explicitly supplied isolated MySQL server. */
@EnabledIfEnvironmentVariable(named = "SPD_REVIEW_MYSQL_TESTS", matches = "true")
class ReviewRegressionMysqlTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private String schema;

    @BeforeEach
    void createIsolatedSchema() {
        String url = System.getenv("SPD_REVIEW_JDBC_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("An explicit isolated loopback MySQL URL is required");
        }
        String base = url.substring(0, url.indexOf('/', "jdbc:mysql://".length()));
        String password = System.getenv("SPD_REVIEW_ROOT_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(base + "/mysql", "root", password));
        schema = "SPD_REVIEW_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema);
        dataSource = new DriverManagerDataSource(base + "/" + schema, "root", password);
        jdbc = spy(new JdbcTemplate(dataSource));
    }

    @AfterEach
    void dropOwnedSchema() {
        if (admin != null && schema != null && schema.matches("SPD_REVIEW_[a-f0-9]{32}")) {
            admin.execute("DROP DATABASE " + schema);
        }
    }

    @Test
    void stocktakingQuantityAndEventAnchorShareOneSnapshot() {
        jdbc.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY,purchase_price DECIMAL(18,4),catalog_price_version BIGINT,unit VARCHAR(20)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE warehouse(warehouse_id BIGINT PRIMARY KEY,warehouse_name VARCHAR(30)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE inventory_batch(batch_id BIGINT PRIMARY KEY,system_batch_no VARCHAR(30)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE inventory_balance(balance_id BIGINT PRIMARY KEY,warehouse_id BIGINT,product_id BIGINT,batch_id BIGINT,available_qty DECIMAL(18,4),locked_qty DECIMAL(18,4) DEFAULT 0) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE inventory_event(event_id BIGINT PRIMARY KEY,warehouse_id BIGINT,product_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE inventory_stocktaking(stocktaking_id BIGINT AUTO_INCREMENT PRIMARY KEY,stocktaking_no VARCHAR(40),warehouse_id BIGINT,stocktaking_type VARCHAR(30),status VARCHAR(30),reason VARCHAR(100)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE inventory_stocktaking_item(item_id BIGINT AUTO_INCREMENT PRIMARY KEY,stocktaking_id BIGINT,balance_id BIGINT,product_id BIGINT,batch_id BIGINT,system_qty DECIMAL(18,4),actual_qty DECIMAL(18,4),diff_qty DECIMAL(18,4),diff_reason VARCHAR(100),snapshot_event_id BIGINT) ENGINE=InnoDB");
        jdbc.update("INSERT INTO product VALUES (1,1,1,'unit')");
        jdbc.update("INSERT INTO warehouse VALUES (1,'W1')");
        jdbc.update("INSERT INTO inventory_batch VALUES (1,'B1')");
        jdbc.update("INSERT INTO inventory_balance(balance_id,warehouse_id,product_id,batch_id,available_qty) VALUES (1,1,1,1,10)");
        jdbc.update("INSERT INTO inventory_event VALUES (1,1,1)");
        doAnswer(call -> {
            Object oldQuantity = call.callRealMethod();
            try (Connection movement = dataSource.getConnection()) {
                movement.setAutoCommit(false);
                try (var statement = movement.createStatement()) {
                    statement.executeUpdate("UPDATE inventory_balance SET available_qty=11 WHERE balance_id=1");
                    statement.executeUpdate("INSERT INTO inventory_event VALUES (2,1,1)");
                }
                movement.commit();
            }
            return oldQuantity;
        }).when(jdbc).queryForMap(contains("FROM inventory_balance bal"), eq("W1"), eq("B1"));
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        when(support.nextNo(com.hospital.spd.common.service.DocumentKind.INVENTORY_STOCKTAKING)).thenReturn("PD-SNAPSHOT");
        InventoryService subject = new InventoryService(jdbc, support);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.execute(status -> subject.createStocktaking(new com.hospital.spd.supplychain.StocktakingRequest("W1","B1",new java.math.BigDecimal("9"),"count")));
        assertThat(jdbc.queryForObject("SELECT snapshot_event_id FROM inventory_stocktaking_item", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT system_qty FROM inventory_stocktaking_item", java.math.BigDecimal.class)).isEqualByComparingTo("10");
        assertThatThrownBy(() -> tx.execute(status -> subject.approveStocktaking("PD-SNAPSHOT")))
                .hasMessageContaining("快照");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance", java.math.BigDecimal.class)).isEqualByComparingTo("11");
    }

    @Test
    void reversalWaitsForConcurrentSettlementConfirmationAndRejectsRestoringStock() throws Exception {
        jdbc.execute("CREATE TABLE product (product_id BIGINT PRIMARY KEY, purchase_price DECIMAL(18,4), catalog_price_version BIGINT, unit VARCHAR(20)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE warehouse (warehouse_id BIGINT PRIMARY KEY, dept_id BIGINT, deleted INT, status INT) ENGINE=InnoDB");
        jdbc.update("INSERT INTO product VALUES (1,1,1,'unit')");
        jdbc.update("INSERT INTO warehouse VALUES (1,1,0,1)");
        jdbc.execute("CREATE TABLE department_consumption (consumption_id BIGINT PRIMARY KEY, consumption_no VARCHAR(40), warehouse_id BIGINT, status VARCHAR(30), related_biz_type VARCHAR(40), related_biz_id BIGINT, dept_id BIGINT, consume_by BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE department_consumption_item (item_id BIGINT PRIMARY KEY, consumption_id BIGINT, product_id BIGINT, batch_id BIGINT, quantity DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE settlement_bill (settlement_id BIGINT PRIMARY KEY, status VARCHAR(30), total_amount DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE settlement_bill_item (item_id BIGINT PRIMARY KEY, settlement_id BIGINT, source_biz_type VARCHAR(40), source_biz_id BIGINT, amount DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE consumption_red_flush (flush_no VARCHAR(40), source_consumption_no VARCHAR(40), flush_type VARCHAR(40), status VARCHAR(30), remark VARCHAR(100)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO department_consumption VALUES (1,'XH1',1,'confirmed','inventory',NULL,1,1)");
        jdbc.update("INSERT INTO department_consumption_item VALUES (1,1,1,1,10)");
        jdbc.update("INSERT INTO settlement_bill VALUES (1,'pending_confirm',100)");
        jdbc.update("INSERT INTO settlement_bill_item VALUES (1,1,'department_consumption_item',1,100)");
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        OperationalConsumptionModule module = new OperationalConsumptionModule(jdbc, support);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch entered = new CountDownLatch(1);
        // Observe the real lock query without replacing its SQL execution.
        doAnswer(call -> { entered.countDown(); return call.callRealMethod(); })
                .when(jdbc).queryForList(contains("FROM settlement_bill sb"), eq(1L));
        var executor = Executors.newSingleThreadExecutor();
        try (Connection confirmation = dataSource.getConnection()) {
            confirmation.setAutoCommit(false);
            try (var statement = confirmation.createStatement()) {
                statement.executeUpdate("UPDATE settlement_bill SET status='confirmed' WHERE settlement_id=1");
                var result = executor.submit(() -> catchThrowable(() -> tx.execute(status -> module.reverseConsumption("XH1"))));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                confirmation.commit();
                assertThat(result.get(10, TimeUnit.SECONDS)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已确认结算");
            } finally {
                confirmation.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        verify(support, never()).receiveAvailable(anyLong(), anyLong(), anyLong(), any(), anyString(), anyString(), anyLong(), anyString());
        assertThat(jdbc.queryForObject("SELECT status FROM department_consumption WHERE consumption_id=1", String.class)).isEqualTo("confirmed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_bill_item", Integer.class)).isEqualTo(1);
    }

    @Test
    void labelUniqueCodeAndUdiResolveToOnePackageIdentity() {
        jdbc.execute("CREATE TABLE quota_package_label (label_no VARCHAR(40), status VARCHAR(30), trace_code_id BIGINT, package_quantity DECIMAL(18,4), product_id BIGINT, warehouse_id BIGINT)");
        jdbc.execute("CREATE TABLE udi_trace_code (trace_code_id BIGINT, udi_code VARCHAR(80), unique_code VARCHAR(80))");
        jdbc.execute("CREATE TABLE product (product_id BIGINT, product_code VARCHAR(30), product_name VARCHAR(30), spec_model VARCHAR(30), unit VARCHAR(10), deleted INT, status INT)");
        jdbc.execute("CREATE TABLE warehouse (warehouse_id BIGINT, warehouse_name VARCHAR(30), dept_id BIGINT)");
        jdbc.update("INSERT INTO product VALUES (1,'P1','Syringe','10ml','piece',0,1)");
        jdbc.update("INSERT INTO warehouse(warehouse_id,warehouse_name) VALUES (1,'Department')");
        jdbc.update("INSERT INTO udi_trace_code VALUES (55,'QP-PKG001','UNIQUE001')");
        jdbc.update("INSERT INTO quota_package_label VALUES ('PKG001','signed',55,10,1,1)");
        var module = new OperationalConsumptionModule(jdbc, mock(SupplyChainSupport.class));
        for (String code : java.util.List.of("PKG001", "QP-PKG001", "UNIQUE001")) {
            assertThat(module.resolveConsumptionProduct(code)).containsEntry("sourceType", "package")
                    .containsEntry("labelNo", "PKG001").containsEntry("traceCodeId", 55L);
        }
    }
}
