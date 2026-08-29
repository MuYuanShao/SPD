package com.hospital.spd.common;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Fails closed when a non-local deployment is started with missing or well-known credentials. */
@Component
public class ProductionConfigurationGuard implements InitializingBean {
    private static final Set<String> UNSAFE_DATABASE_PASSWORDS = Set.of("admin123", "root123", "password");
    private static final Set<String> UNSAFE_JWT_SECRETS = Set.of(
            "change-me-to-a-strong-64-byte-secret-before-production",
            "aB3xK7mN9pQ2rT5vW8yZ0cE4fH6jL1oM8sA2dD4gG6kJ8nB0eV2rR5tY7uI9oP3"
    );
    private final Environment environment;

    public ProductionConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) return;
        String databasePassword = firstNonBlank(environment.getProperty("SPD_DB_PASSWORD"),
                environment.getProperty("spring.datasource.password"));
        if (databasePassword == null || databasePassword.length() < 16
                || UNSAFE_DATABASE_PASSWORDS.contains(databasePassword)) {
            throw new IllegalStateException("SPD_DB_PASSWORD is required and must be a unique value of at least 16 characters");
        }
        String jwtSecret = firstNonBlank(environment.getProperty("SPD_JWT_SECRET"),
                environment.getProperty("spd.jwt.secret"));
        if (jwtSecret == null || jwtSecret.length() < 48 || UNSAFE_JWT_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException("SPD_JWT_SECRET is required and must be a unique value of at least 48 characters");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }
}
