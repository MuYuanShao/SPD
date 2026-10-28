package com.hospital.spd.supplychain.service;

import com.hospital.spd.deployment.DatabaseSchemaContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Reproduces legacy schema drift in an owned disposable database, then verifies compensation without role grants. */
@EnabledIfEnvironmentVariable(named="SPD_REVIEW_MYSQL_TESTS", matches="true")
class SchemaRepairMysqlTest {
    @Test
    void restoresMissingInvoiceAndPermissionsWithoutChangingExistingRoleGrants() throws Exception {
        String source = System.getenv("SPD_REVIEW_JDBC_URL");
        if (source == null || !source.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalArgumentException("Explicit isolated MySQL is required");
        String base = source.substring(0, source.indexOf('/', "jdbc:mysql://".length()));
        String password = System.getenv("SPD_REVIEW_ROOT_PASSWORD");
        String schema = "spd_schema_repair_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate server = new JdbcTemplate(new DriverManagerDataSource(base + "/mysql", "root", password));
        server.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        try {
            String url = base + "/" + schema + "?serverTimezone=Asia/Shanghai";
            Flyway.configure().dataSource(url,"root",password).locations("classpath:db/migration").target("99").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url,"root",password));
            jdbc.execute("DROP TABLE supplier_invoice");
            jdbc.update("DELETE rp FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id=rp.perm_id WHERE p.perm_code LIKE 'purchase%'");
            jdbc.update("DELETE FROM sys_permission WHERE perm_code LIKE 'purchase%'");
            String code = "LEGACY-" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit,is_high_value) SELECT ?,?,'legacy',category_id,'个',1 FROM product LIMIT 1", code, code);
            Long product = jdbc.queryForObject("SELECT product_id FROM product WHERE product_code=?",Long.class,code);
            Long supplier = jdbc.queryForObject("SELECT MIN(supplier_id) FROM supplier",Long.class);
            jdbc.update("INSERT INTO inventory_batch(system_batch_no,product_id,supplier_id,batch_unit_price,settlement_mode) VALUES (?,?,?,10,'purchase_in')",code,product,supplier);
            Long batch = jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE system_batch_no=?",Long.class,code);
            jdbc.update("INSERT INTO settlement_bill(settlement_no,supplier_id,settlement_period,settlement_mode,total_amount,status) VALUES (?,?,'2026-01','purchase_in',10,'confirmed')",code,supplier);
            Long bill = jdbc.queryForObject("SELECT settlement_id FROM settlement_bill WHERE settlement_no=?",Long.class,code);
            jdbc.update("INSERT INTO settlement_bill_item(settlement_id,source_biz_type,source_biz_id,product_id,batch_id,quantity,unit_price,amount) VALUES (?,'receiving_order_item',99999999,?,?,1,10,10)",bill,product,batch);
            var grants = jdbc.queryForList("SELECT role_id,perm_id FROM sys_role_perm ORDER BY role_id,perm_id");
            try (var connection=DriverManager.getConnection(url,"root",password)) {
                assertThatThrownBy(() -> DatabaseSchemaContract.verify(connection)).hasMessageContaining("补偿迁移");
            }
            var result = Flyway.configure().dataSource(url,"root",password).locations("classpath:db/migration").load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM supplier_invoice",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT goods_type FROM settlement_bill_item WHERE product_id=?",String.class,product)).isEqualTo("unknown");
            assertThat(jdbc.queryForObject("SELECT amount FROM settlement_bill_item WHERE product_id=?",java.math.BigDecimal.class,product)).isEqualByComparingTo("10");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE perm_code LIKE 'purchase%'",Long.class)).isGreaterThan(10L);
            assertThat(jdbc.queryForList("SELECT role_id,perm_id FROM sys_role_perm ORDER BY role_id,perm_id")).isEqualTo(grants);
            try (var connection=DriverManager.getConnection(url,"root",password)) { DatabaseSchemaContract.verify(connection); }
        } finally {
            if (!schema.matches("spd_schema_repair_[a-f0-9]{32}")) throw new IllegalStateException("Invalid fixture schema");
            server.execute("DROP DATABASE " + schema);
        }
    }
}
