package com.hospital.spd.deployment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

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
    void runtimePrivilegeCheckRejectsDdlButAllowsDmlOnlyGrants() {
        assertTrue(DeploymentDatabaseCommand.containsDdlPrivilege(List.of(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `ISPD`.* TO `spd`@`%`",
                "GRANT CREATE, ALTER ON `ISPD`.* TO `spd`@`%`"
        )));
        assertFalse(DeploymentDatabaseCommand.containsDdlPrivilege(List.of(
                "GRANT USAGE ON *.* TO `spd`@`%`",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `ISPD`.* TO `spd`@`%`"
        )));
    }
}
