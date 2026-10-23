package com.hospital.spd.system.service;

import com.hospital.spd.system.SystemConfigRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in configuration matching and duplicate-write checks in an owned temporary MySQL schema. */
@EnabledIfEnvironmentVariable(named = "SPD_CONFIG_MYSQL_TESTS", matches = "true")
class SystemConfigMysqlTest {
    static String schema, baseUrl, user, password;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static SystemConfigService service;
    static String ddl, deletedDdl;

    @BeforeAll static void createOwnSchema() throws Exception {
        baseUrl = Objects.requireNonNull(System.getenv("SPD_TEST_MYSQL_URL"));
        if (!baseUrl.matches("jdbc:mysql://[a-zA-Z0-9.:-]+/")) throw new IllegalArgumentException("Test URL must name a server only");
        user = Objects.requireNonNull(System.getenv("SPD_TEST_MYSQL_USERNAME"));
        password = Objects.requireNonNull(System.getenv("SPD_TEST_MYSQL_PASSWORD"));
        schema = "spd_config_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(baseUrl, user, password); var s = c.createStatement()) {
            s.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        var ds = new DriverManagerDataSource(baseUrl + schema, user, password);
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new SystemConfigService(jdbc);
        try (var stream = SystemConfigMysqlTest.class.getResourceAsStream("/db/migration/V1__core_schema.sql")) {
            String migration = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
            int start = migration.indexOf("CREATE TABLE IF NOT EXISTS `system_config`");
            ddl = migration.substring(start, migration.indexOf(';', start) + 1);
        }
        try (var stream = SystemConfigMysqlTest.class.getResourceAsStream("/db/migration/V7__add_deleted_flag.sql")) {
            String migration = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
            int start = migration.indexOf("ALTER TABLE `system_config`");
            deletedDdl = migration.substring(start, migration.indexOf(';', start) + 1);
        }
    }
    @BeforeEach void createFixtureTable() { jdbc.execute(ddl); jdbc.execute(deletedDdl); }
    @AfterEach void dropFixtureTable() { jdbc.execute("DROP TABLE IF EXISTS system_config"); }
    @AfterAll static void dropOwnSchema() throws Exception {
        if (schema == null) return;
        if (!schema.matches("spd_config_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        try (var c = DriverManager.getConnection(baseUrl, user, password); var s = c.createStatement()) { s.execute("DROP DATABASE " + schema); }
    }

    @Test void independentKeysAtTheSameScopeNeverOverrideEachOther() {
        create("approval.product.admission", "global", "default", "admission", null, 1);
        create("approval.purchase.order", "global", "default", "purchase", null, 1);
        var purchase = service.explain(query("approval.purchase.order"));
        assertThat(purchase.finalValue()).isEqualTo("purchase");
        assertThat(purchase.candidates()).hasSize(1).allSatisfy(row -> assertThat(row.configKey()).isEqualTo("approval.purchase.order"));
        assertThat(purchase.overridesParent()).isFalse();
        assertThat(service.explain(query("approval.product.admission")).finalValue()).isEqualTo("admission");
        assertThat(service.keyOptions("workflow")).hasSize(2);
    }

    @Test void disabledExpiredFutureAndDeletedOverridesFallBackToTheSameKeyDefault() {
        create("K", "global", "default", "default", null, 1);
        create("K", "department", "D1", "department", null, 1);
        Long department = jdbc.queryForObject("SELECT config_id FROM system_config WHERE scope_type='department'", Long.class);
        var query = new HashMap<>(query("K")); query.put("departmentId", "D1");
        var first = service.explain(query);
        assertThat(first.finalValue()).isEqualTo("department");
        assertThat(first.parentValue()).isEqualTo("default");
        assertThat(first.parentConfigId()).isNotNull();
        tx.executeWithoutResult(s -> service.updateStatus(department, 0));
        assertThat(service.explain(query).finalValue()).isEqualTo("default");
        tx.executeWithoutResult(s -> service.updateStatus(department, 1));
        tx.executeWithoutResult(s -> service.update(department, request("K", "department", "D1", "department", LocalDate.now().minusDays(1).toString(), 1)));
        assertThat(service.explain(query).finalValue()).isEqualTo("default");
        jdbc.update("UPDATE system_config SET expire_time=NULL,effective_time=DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE config_id=?", department);
        assertThat(service.explain(query).finalValue()).isEqualTo("default");
        jdbc.update("UPDATE system_config SET deleted=1,effective_time=NOW() WHERE config_id=?", department);
        assertThat(service.explain(query).finalValue()).isEqualTo("default");
    }

    @Test void specializedSystemScopeOverridesDefaultWithoutBeingAnEqualRankConflict() {
        create("K", "global", "default", "default", null, 1);
        create("K", "global", "purchase_order", "module", null, 1);
        var query = new HashMap<>(query("K")); query.put("moduleId", "purchase_order");
        var result = service.explain(query);
        assertThat(result.conflict()).isFalse();
        assertThat(result.finalValue()).isEqualTo("module");
        assertThat(result.parentValue()).isEqualTo("default");
    }

    @Test void sameRankCandidatesAreReportedAsConflictInsteadOfAnArbitraryWinner() {
        create("K", "global", "warning", "one", null, 1);
        create("K", "global", "purchase_order", "two", null, 1);
        var query = new HashMap<>(query("K")); query.put("moduleId", "purchase_order");
        var result = service.explain(query);
        assertThat(result.conflict()).isTrue();
        assertThat(result.sourceConfigId()).isNull();
        assertThat(result.candidates()).allSatisfy(row -> { assertThat(row.conflict()).isTrue(); assertThat(row.overridden()).isFalse(); });
    }

    @Test void createAndUpdateCannotReplaceAnotherIdentity() {
        create("K", "global", "default", "one", null, 1);
        assertThatThrownBy(() -> create("K", "global", "default", "two", null, 1)).hasMessageContaining("生效冲突");
        create("K", "department", "D1", "department", null, 1);
        Long department = jdbc.queryForObject("SELECT config_id FROM system_config WHERE scope_type='department'", Long.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.update(department, request("K", "global", "default", "replacement", null, 1))))
                .hasMessageContaining("生效冲突");
        assertThat(service.explain(query("K")).finalValue()).isEqualTo("one");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(2);
    }

    @Test void legacyDuplicateEnablingIsRejectedWithoutDeletingEitherRow() {
        create("K", "global", "default", "one", null, 1);
        // Only this owned fixture lacks the historical unique index; no business schema is altered.
        jdbc.execute("ALTER TABLE system_config DROP INDEX uk_config_scope_key");
        jdbc.update("INSERT INTO system_config(config_type,scope_type,scope_id,config_key,config_value,status) VALUES('workflow','global','default','K',JSON_QUOTE('disabled'),0)");
        Long disabled = jdbc.queryForObject("SELECT config_id FROM system_config WHERE status=0", Long.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.updateStatus(disabled, 1))).hasMessageContaining("生效冲突");
        assertThat(jdbc.queryForObject("SELECT status FROM system_config WHERE config_id=?", Integer.class, disabled)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(2);
        jdbc.update("UPDATE system_config SET status=1 WHERE config_id=?", disabled);
        assertThat(service.explain(query("K")).conflict()).isTrue();
        tx.executeWithoutResult(s -> service.updateStatus(disabled, 0));
        assertThat(service.explain(query("K")).finalValue()).isEqualTo("one");
    }

    @Test void concurrentCreatesCannotProduceTwoEffectiveRows() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<Future<Boolean>>();
            for (int i=0;i<2;i++) tasks.add(executor.submit(() -> {
                start.await();
                try { create("K", "global", "default", "value", null, 1); return true; }
                catch (IllegalArgumentException | org.springframework.dao.DataAccessException rejected) { return false; }
            }));
            start.countDown(); int successes=0;
            for (var task: tasks) if (task.get(15, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void expiredRowsRemainSelectableButDoNotMatch() {
        create("K", "global", "default", "old", LocalDate.now().minusDays(1).toString(), 1);
        assertThat(service.keyOptions("workflow")).hasSize(1);
        assertThat(service.explain(query("K")).approvalStatus()).isEqualTo("未命中");
        assertThatThrownBy(() -> service.explain(Map.of("configType", "workflow"))).hasMessageContaining("请选择");
    }

    static Map<String,String> query(String key) { return Map.of("configType", "workflow", "configKey", key); }
    static void create(String key, String scope, String object, String value, String expiry, int status) {
        tx.executeWithoutResult(s -> service.create(request(key, scope, object, value, expiry, status)));
    }
    static SystemConfigRequest request(String key, String scope, String object, String value, String expiry, int status) {
        return new SystemConfigRequest("workflow", scope, object, key, value, "realtime", expiry, "low", status);
    }
}
