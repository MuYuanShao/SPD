package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Checks notice delivery against an isolated MySQL schema. */
@EnabledIfEnvironmentVariable(named = "SPD_NOTICE_MYSQL_TESTS", matches = "true")
class SystemNoticeMysqlTest {
    @Test
    void concurrentLoginsClaimOnceAndTheWinningLoginCanRetry() throws Exception {
        String base = Objects.requireNonNull(System.getenv("SPD_TEST_MYSQL_URL"));
        if (!base.matches("jdbc:mysql://[a-zA-Z0-9.:-]+/")) {
            throw new IllegalArgumentException("Test URL must name a server only");
        }
        String username = Objects.requireNonNull(System.getenv("SPD_TEST_MYSQL_USERNAME"));
        String password = Objects.requireNonNullElse(System.getenv("SPD_TEST_MYSQL_PASSWORD"), "");
        String schema = "spd_notice_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate server = new JdbcTemplate(new DriverManagerDataSource(base, username, password));
        server.execute("CREATE DATABASE " + schema);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var dataSource = new DriverManagerDataSource(base + schema, username, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE sys_user (user_id BIGINT UNSIGNED PRIMARY KEY) ENGINE=InnoDB");
            jdbc.update("INSERT INTO sys_user (user_id) VALUES (1)");
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/migration/V110__system_notices.sql"),
                    new ClassPathResource("db/migration/V111__system_notice_login_delivery.sql")
            ).execute(dataSource);
            var service = new SystemNoticeService(jdbc, OperatorContext::system, mock(AuditLogService.class));
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            transaction.executeWithoutResult(status -> service.publish("SPD 系统维护", "第一段\n第二段"));

            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return transaction.execute(status -> service.claimForLogin(firstId)); });
            var second = pool.submit(() -> { start.await(); return transaction.execute(status -> service.claimForLogin(secondId)); });
            start.countDown();
            var firstNotice = first.get(10, TimeUnit.SECONDS);
            var secondNotice = second.get(10, TimeUnit.SECONDS);
            assertThat(firstNotice == null ^ secondNotice == null).isTrue();

            UUID winner = firstNotice != null ? firstId : secondId;
            var delivered = firstNotice != null ? firstNotice : secondNotice;
            var retry = transaction.execute(status -> service.claimForLogin(winner));
            assertThat(retry).isEqualTo(delivered);
            service.acknowledge(Long.parseLong(delivered.id()));
            var afterAcknowledgment = transaction.execute(status -> service.claimForLogin(winner));
            assertThat(afterAcknowledgment).isNull();
        } finally {
            pool.shutdownNow();
            server.execute("DROP DATABASE " + schema);
        }
    }
}
