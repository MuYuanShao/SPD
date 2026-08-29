package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationGuardTest {

    @Test
    void productionRejectsMissingOrKnownDefaultSecrets() {
        MockEnvironment missing = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(missing).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPD_DB_PASSWORD");

        MockEnvironment defaults = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("SPD_DB_PASSWORD", "admin123")
                .withProperty("SPD_JWT_SECRET", "aB3xK7mN9pQ2rT5vW8yZ0cE4fH6jL1oM8sA2dD4gG6kJ8nB0eV2rR5tY7uI9oP3");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(defaults).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionAcceptsExplicitStrongSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("SPD_DB_PASSWORD", "a-unique-database-password-2026")
                .withProperty("SPD_JWT_SECRET", "mNwj7IiGrpapYS1Y9is0tt3NA25FcHwfaGO6b8fUC1EAffSR");

        assertThatCode(() -> new ProductionConfigurationGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void localProfileAllowsLocalDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "local")
                .withProperty("SPD_DB_PASSWORD", "admin123")
                .withProperty("SPD_JWT_SECRET", "local-development-secret");

        assertThatCode(() -> new ProductionConfigurationGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
