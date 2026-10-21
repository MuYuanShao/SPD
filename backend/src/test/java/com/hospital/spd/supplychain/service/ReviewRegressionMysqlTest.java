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
    void reversalWaitsForConcurrentSettlementConfirmationAndRejectsRestoringStock() throws Exception {
        jdbc.execute("CREATE TABLE department_consumption (consumption_id BIGINT PRIMARY KEY, consumption_no VARCHAR(40), warehouse_id BIGINT, status VARCHAR(30), related_biz_type VARCHAR(40), related_biz_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE department_consumption_item (item_id BIGINT PRIMARY KEY, consumption_id BIGINT, product_id BIGINT, batch_id BIGINT, quantity DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE settlement_bill (settlement_id BIGINT PRIMARY KEY, status VARCHAR(30), total_amount DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE settlement_bill_item (item_id BIGINT PRIMARY KEY, settlement_id BIGINT, source_biz_type VARCHAR(40), source_biz_id BIGINT, amount DECIMAL(18,4)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE consumption_red_flush (flush_no VARCHAR(40), source_consumption_no VARCHAR(40), flush_type VARCHAR(40), status VARCHAR(30), remark VARCHAR(100)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO department_consumption VALUES (1,'XH1',1,'confirmed','inventory',NULL)");
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
        jdbc.execute("CREATE TABLE warehouse (warehouse_id BIGINT, warehouse_name VARCHAR(30))");
        jdbc.update("INSERT INTO product VALUES (1,'P1','Syringe','10ml','piece',0,1)");
        jdbc.update("INSERT INTO warehouse VALUES (1,'Department')");
        jdbc.update("INSERT INTO udi_trace_code VALUES (55,'QP-PKG001','UNIQUE001')");
        jdbc.update("INSERT INTO quota_package_label VALUES ('PKG001','signed',55,10,1,1)");
        var module = new OperationalConsumptionModule(jdbc, mock(SupplyChainSupport.class));
        for (String code : java.util.List.of("PKG001", "QP-PKG001", "UNIQUE001")) {
            assertThat(module.resolveConsumptionProduct(code)).containsEntry("sourceType", "package")
                    .containsEntry("labelNo", "PKG001").containsEntry("traceCodeId", 55L);
        }
    }
}
