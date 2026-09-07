package com.hospital.spd.supplychain.service;

import org.springframework.test.context.DynamicPropertyRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

/** Owns an opt-in, uniquely named MySQL database; never cleans fixtures in an existing business database. */
final class IsolatedMysqlDatabase implements AutoCloseable {
    private final String baseUrl;
    private final String user;
    private final String password;
    private final String schema = "spd_test_" + UUID.randomUUID().toString().replace("-", "");
    private boolean created;

    IsolatedMysqlDatabase() {
        baseUrl = required("SPD_TEST_MYSQL_URL");
        if (!baseUrl.matches("jdbc:mysql://[a-zA-Z0-9.:-]+/")) {
            throw new IllegalArgumentException("SPD_TEST_MYSQL_URL must name a server only, e.g. jdbc:mysql://localhost:3306/");
        }
        user = required("SPD_TEST_MYSQL_USERNAME");
        password = required("SPD_TEST_MYSQL_PASSWORD");
    }

    void register(DynamicPropertyRegistry properties) {
        try (Connection connection = DriverManager.getConnection(baseUrl, user, password);
             Statement sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT @@GLOBAL.log_bin, @@GLOBAL.log_bin_trust_function_creators")) {
                rows.next();
                if (rows.getBoolean(1) && !rows.getBoolean(2)) {
                    boolean superPrivilege = false;
                    try (var grants = connection.createStatement(); var result = grants.executeQuery("SHOW GRANTS")) {
                        while (result.next()) {
                            String grant = result.getString(1);
                            superPrivilege |= grant.contains("ALL PRIVILEGES ON *.*") || grant.contains("SUPER");
                        }
                    }
                    if (!superPrivilege) throw new IllegalStateException("测试账号缺少启用 binlog 时创建迁移触发器的权限");
                }
            }
            sql.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            created = true;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("无法创建独立 MySQL 测试库；未使用业务库", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "drop-" + schema));
        properties.add("spring.datasource.url", () -> baseUrl + schema + "?serverTimezone=Asia/Shanghai");
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
    }

    @Override
    public synchronized void close() {
        if (!created) return;
        if (!schema.matches("spd_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        try (Connection connection = DriverManager.getConnection(baseUrl, user, password);
             Statement sql = connection.createStatement()) {
            sql.execute("DROP DATABASE " + schema);
            created = false;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("测试库清理失败：" + schema, e);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("独立 MySQL 集成测试需要环境变量 " + name);
        return value;
    }
}
