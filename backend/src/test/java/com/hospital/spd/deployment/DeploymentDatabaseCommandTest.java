package com.hospital.spd.deployment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentDatabaseCommandTest {

    private static final Map<String, String> COMPLETE_ENVIRONMENT = Map.of(
            "SPD_DB_URL", "jdbc:mysql://127.0.0.1:3306/ISPD",
            "SPD_FLYWAY_USERNAME", "migration_user",
            "SPD_FLYWAY_PASSWORD", "NeverPrintThisMigrationPassword!"
    );

    @Test
    void validateRejectsMissingMigrationConfigurationWithoutLeakingAvailableSecrets() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Map<String, String> environment = Map.of(
                "SPD_FLYWAY_PASSWORD", "NeverPrintThisMigrationPassword!"
        );

        int exitCode = new DeploymentDatabaseCommand()
                .execute("validate", environment, new PrintStream(output, true, StandardCharsets.UTF_8));

        String message = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, exitCode);
        assertTrue(message.contains("SPD_DB_URL"));
        assertTrue(message.contains("SPD_FLYWAY_USERNAME"));
        assertFalse(message.contains("NeverPrintThisMigrationPassword!"));
    }

    @Test
    void rejectsUnsupportedCommandBeforeOpeningDatabaseConnection() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = new DeploymentDatabaseCommand()
                .execute("erase", COMPLETE_ENVIRONMENT,
                        new PrintStream(output, true, StandardCharsets.UTF_8));

        String message = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, exitCode);
        assertTrue(message.contains("不支持的数据库命令"));
        assertFalse(message.contains("NeverPrintThisMigrationPassword!"));
    }

    @Test
    void legacyRepairRequiresBackupEvidenceAndDatabaseNameConfirmationBeforeConnecting() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = new DeploymentDatabaseCommand()
                .execute("legacy-repair", COMPLETE_ENVIRONMENT,
                        new PrintStream(output, true, StandardCharsets.UTF_8));

        String message = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, exitCode);
        assertTrue(message.contains("SPD_BACKUP_EVIDENCE"));
        assertTrue(message.contains("SPD_LEGACY_REPAIR_CONFIRM"));
        assertFalse(message.contains("NeverPrintThisMigrationPassword!"));
    }

    @Test
    void validateReportsConnectionFailureWithoutLeakingMigrationPassword() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Map<String, String> unreachableDatabase = Map.of(
                "SPD_DB_URL", "jdbc:mysql://127.0.0.1:1/ISPD?connectTimeout=200",
                "SPD_FLYWAY_USERNAME", "migration_user",
                "SPD_FLYWAY_PASSWORD", "NeverPrintThisMigrationPassword!"
        );

        int exitCode = new DeploymentDatabaseCommand()
                .execute("validate", unreachableDatabase,
                        new PrintStream(output, true, StandardCharsets.UTF_8));

        String message = output.toString(StandardCharsets.UTF_8);
        assertEquals(4, exitCode);
        assertTrue(message.contains("数据库 validate 失败"));
        assertFalse(message.contains("NeverPrintThisMigrationPassword!"));
    }

    @Test
    void adminBootstrapRejectsMissingOrInvalidPasswordsBeforeConnecting() {
        for (String password : List.of("", "short", "中".repeat(25), "x".repeat(73))) {
            Map<String, String> environment = new HashMap<>(COMPLETE_ENVIRONMENT);
            if (!password.isEmpty()) environment.put("SPD_INITIAL_ADMIN_PASSWORD", password);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = new DeploymentDatabaseCommand().execute("bootstrap-admin", environment,
                    new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(2, exitCode);
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("SPD_INITIAL_ADMIN_PASSWORD"));
            if (!password.isEmpty()) assertFalse(output.toString(StandardCharsets.UTF_8).contains(password));
        }
    }

    @Test
    void adminBootstrapReportsConnectionFailureWithoutLeakingInitialPassword() {
        Map<String, String> environment = new HashMap<>(COMPLETE_ENVIRONMENT);
        environment.put("SPD_DB_URL", "jdbc:mysql://127.0.0.1:1/ISPD?connectTimeout=200");
        environment.put("SPD_INITIAL_ADMIN_PASSWORD", "PrivateGeneratedAdminPassword");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = new DeploymentDatabaseCommand().execute("bootstrap-admin", environment,
                new PrintStream(output, true, StandardCharsets.UTF_8));
        assertEquals(4, exitCode);
        String message = output.toString(StandardCharsets.UTF_8);
        assertTrue(message.contains("数据库 bootstrap-admin 失败"));
        assertFalse(message.contains(environment.get("SPD_INITIAL_ADMIN_PASSWORD")));
        assertFalse(message.contains(environment.get("SPD_FLYWAY_PASSWORD")));
    }

    @Test
    void runtimePrivilegeCheckRejectsDdlButAllowsDmlOnlyGrants() {
        assertTrue(DeploymentDatabaseCommand.containsDdlPrivilege(List.of(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `ISPD`.* TO `spd`@`%`",
                "GRANT CREATE, ALTER ON `ISPD`.* TO `spd`@`%`"
        )));
        assertFalse(DeploymentDatabaseCommand.containsDdlPrivilege(List.of(
                "GRANT USAGE ON *.* TO `spd`@`%`",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `ISPD`.* TO `spd`@`%`"
        )));
        assertFalse(DeploymentDatabaseCommand.containsDdlPrivilege(List.of(
                "GRANT SELECT ON `create`.* TO `alter`@`%`", "GRANT `drop`@`%` TO `runtime`@`localhost`")));
    }

    @Test
    void expandsEnabledRolesAndQuotesRoleIdentifiersBeforeCheckingDdl() throws Exception {
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        var statement = org.mockito.Mockito.mock(java.sql.Statement.class);
        var roles = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        var grants = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(connection.createStatement()).thenReturn(statement);
        org.mockito.Mockito.when(statement.executeQuery("SELECT ROLE_NAME, ROLE_HOST FROM information_schema.ENABLED_ROLES")).thenReturn(roles);
        org.mockito.Mockito.when(roles.next()).thenReturn(true, false);
        org.mockito.Mockito.when(roles.getString("ROLE_NAME")).thenReturn("maintenance`role");
        org.mockito.Mockito.when(roles.getString("ROLE_HOST")).thenReturn("%");
        org.mockito.Mockito.when(statement.executeQuery("SHOW GRANTS FOR CURRENT_USER USING `maintenance``role`@`%`")).thenReturn(grants);
        org.mockito.Mockito.when(grants.next()).thenReturn(true, false);
        org.mockito.Mockito.when(grants.getString(1)).thenReturn("GRANT CREATE, ALTER, DROP ON `ISPD`.* TO `runtime`@`localhost`");
        assertTrue(DeploymentDatabaseCommand.containsDdlPrivilege(DeploymentDatabaseCommand.currentUserGrants(connection)));
        org.mockito.Mockito.verify(statement).execute("SET ROLE ALL");
    }
}
