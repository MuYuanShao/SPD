package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SeedAccountSecurityGuardTest {

    @Test
    void productionDisablesOnlyAccountsStillUsingKnownSeedHashes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new SeedAccountSecurityGuard(new MockEnvironment().withProperty("spring.profiles.active", "prod"), jdbc)
                .run(null);

        verify(jdbc).update(contains("password IN (?, ?)"),
                eq(SeedAccountSecurityGuard.ADMIN123_HASH), eq(SeedAccountSecurityGuard.LEGACY_SEED_HASH));
    }

    @Test
    void localProfileLeavesSeedAccountsAvailableForDevelopment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new SeedAccountSecurityGuard(new MockEnvironment().withProperty("spring.profiles.active", "local"), jdbc)
                .run(null);

        verify(jdbc, never()).update(contains("UPDATE sys_user"),
                org.mockito.ArgumentMatchers.any(Object[].class));
    }
}
