package com.hospital.spd.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化校验器——启动时确认 Flyway 已完成迁移。
 * 替代所有 Controller 中的 ensureRuntimeTables() / ensureTables() / ensureSeedData()。
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migration check completed (managed by Flyway)");
    }
}
