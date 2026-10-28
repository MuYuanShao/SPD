package com.hospital.spd.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化校验器——启动时确认 Flyway 已完成迁移。
 * 替代所有 Controller 中的 ensureRuntimeTables() / ensureTables() / ensureSeedData()。
 */
@Component
public class DatabaseInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DatabaseInitializer(org.springframework.jdbc.core.JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            com.hospital.spd.deployment.DatabaseSchemaContract.verify(connection);
            return null;
        });
        log.info("Database migration and runtime schema contract checks completed");
    }
}
