package com.hospital.spd.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Disables unchanged demonstration credentials when the application runs outside local/test profiles. */
@Component
public class SeedAccountSecurityGuard implements ApplicationRunner {
    static final String ADMIN123_HASH = "$2a$10$uI1qPLHX19yBlWucKapwNOhj038aVPtbfw8hMPOx28KcCxr1kQK4S";
    static final String LEGACY_SEED_HASH = "$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS";

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public SeedAccountSecurityGuard(Environment environment, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE sys_user
                   SET status = 0, update_time = NOW()
                 WHERE username IN ('admin', 'operator01', 'nurse01',
                                    'rbac_catalog_reader', 'rbac_department_reader')
                   AND password IN (?, ?)
                   AND status = 1
                """, ADMIN123_HASH, LEGACY_SEED_HASH);
    }
}
