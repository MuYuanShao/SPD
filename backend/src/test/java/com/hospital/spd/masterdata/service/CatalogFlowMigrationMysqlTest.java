package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.system.ApprovalFlowRequest;
import com.hospital.spd.system.ApprovalFlowStepRequest;
import com.hospital.spd.system.service.ApprovalFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Pre-upgrade clone test: only a newly named temporary schema is mutated and dropped. */
@EnabledIfEnvironmentVariable(named="SPD_APPROVAL_MIGRATION_TEST", matches="true")
class CatalogFlowMigrationMysqlTest {
    @Test void migrationFreezesExistingRoutesAndConfigurationEditsOnlyAffectNewRounds() throws Exception {
        String schema = System.getenv().getOrDefault("SPD_APPROVAL_TEST_SCHEMA", "spd_route_test_" + UUID.randomUUID().toString().replace("-", ""));
        if (!schema.matches("spd_route_test_[a-f0-9]{32}") && !"lspd".equals(schema)) throw new IllegalArgumentException("Unexpected isolated schema");
        boolean created = false;
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", System.getenv("SPD_DB_USERNAME"), System.getenv("SPD_DB_PASSWORD"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Assumptions.assumeTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ispd' AND table_name='approval_flow' AND column_name='revision_no'", Integer.class) == 0,
                    "源库已升级；本测试只用于迁移应用前的真实数据副本核验");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?", Integer.class, schema) != 0)
                throw new IllegalStateException("拒绝使用或清理已存在的数据库：" + schema);
            try {
                jdbc.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                created = true;
                connection.setCatalog(schema);
                for (String table : List.of("approval_flow", "approval_flow_step", "pending_product_application", "pending_product_approval_route_step", "flyway_schema_history")) {
                    jdbc.execute("CREATE TABLE " + table + " LIKE ispd." + table);
                    jdbc.execute("INSERT INTO " + table + " SELECT * FROM ispd." + table);
                }
                jdbc.execute("ALTER TABLE pending_product_approval_route_step ADD CONSTRAINT fk_clone_source_step FOREIGN KEY(source_step_id) REFERENCES approval_flow_step(step_id)");
                var pending = jdbc.queryForList("SELECT application_id,approval_status FROM pending_product_application WHERE approval_status LIKE 'pending_step_%'");
                ScriptUtils.executeSqlScript(connection, new FileSystemResource("src/main/resources/db/migration/V86__single_catalog_approval_flow.sql"));
                for (var row : pending) {
                    int original = Integer.parseInt(String.valueOf(row.get("approval_status")).substring("pending_step_".length()));
                    assertThat(jdbc.queryForObject("SELECT route_order FROM pending_product_approval_route_step WHERE application_id=? AND route_status='pending'", Integer.class, row.get("application_id"))).isEqualTo(original);
                }
                Long flow = jdbc.queryForObject("SELECT flow_id FROM approval_flow WHERE active_catalog_scope='global:default'", Long.class);
                var routes = new CatalogApprovalRouteService(jdbc, OperatorContext::system);
                long oldApplication = application(jdbc, "CLONE-OLD");
                routes.snapshotRoute(oldApplication, 1, "新品准入", null);
                int originalSteps = routes.configuredSteps(oldApplication, 1).size();
                assertThat(originalSteps).isEqualTo(4);
                var settings = new ApprovalFlowService(jdbc, OperatorContext::system);
                var steps = List.of(
                        new ApprovalFlowStepRequest(null, 1, "设备科", "user", null, 1L, null, 1, false, 1, 1),
                        new ApprovalFlowStepRequest(null, 2, "采购科", "user", null, 1L, null, 1, false, 1, 1),
                        new ApprovalFlowStepRequest(null, 3, "医保科", "user", null, 1L, null, 1, false, 1, 1));
                var request = new ApprovalFlowRequest("pending-product-catalog", "待审批目录", "initial-review", "目录顺序审批", "global", "default", 1, 1, null, null, steps);
                settings.update(flow, request);
                assertThat(routes.configuredSteps(oldApplication, 1)).hasSize(4);
                long nextApplication = application(jdbc, "CLONE-NEW");
                routes.snapshotRoute(nextApplication, 1, "新品准入", null);
                assertThat(routes.configuredSteps(nextApplication, 1)).hasSize(3);
                assertThat(routes.completeAndAdvance(nextApplication,1,1)).contains(2);
                assertThat(routes.completeAndAdvance(nextApplication,1,2)).contains(3);
                assertThat(routes.completeAndAdvance(nextApplication,1,3)).isEmpty();
                for (int order=1; order<4; order++) assertThat(routes.completeAndAdvance(oldApplication,1,order)).contains(order+1);
                assertThat(routes.completeAndAdvance(oldApplication,1,4)).isEmpty();
                routes.snapshotRoute(oldApplication,2,"新品准入",null);
                assertThat(routes.configuredSteps(oldApplication,1)).hasSize(4);
                assertThat(routes.configuredSteps(oldApplication,2)).hasSize(3);
                assertThatThrownBy(() -> settings.create(request)).hasMessageContaining("已有启用");
                System.out.println("MIGRATION VERIFIED: preserved " + pending.size() + " pending positions; old=4, new=3, resubmitted round=3; source-step foreign keys retained");
            } finally {
                if (created) {
                    if (!schema.matches("spd_route_test_[a-f0-9]{32}") && !"lspd".equals(schema)) throw new IllegalStateException("Unexpected clone schema");
                    connection.setCatalog("ispd");
                    jdbc.execute("DROP DATABASE " + schema);
                }
            }
        }
    }
    private long application(JdbcTemplate jdbc, String no) {
        jdbc.update("INSERT INTO pending_product_application (application_no,application_type,product_snapshot,approval_status) VALUES (?,'新品准入','{}','pending_step_1')", no);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
}
