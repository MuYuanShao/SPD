package com.hospital.spd.deployment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Provides database validation and migration commands for the offline Windows deployment package. */
public final class DeploymentDatabaseCommand {

    private static final List<String> SUPPORTED_COMMANDS = List.of("validate", "migrate", "legacy-repair", "bootstrap-admin");
    private static final String SEED_PASSWORD_HASH = "$2a$10$uI1qPLHX19yBlWucKapwNOhj038aVPtbfw8hMPOx28KcCxr1kQK4S";
    private static final List<String> REQUIRED_ENVIRONMENT = List.of(
            "SPD_DB_URL", "SPD_FLYWAY_USERNAME", "SPD_FLYWAY_PASSWORD"
    );
    private static final Set<Integer> LEGACY_CHECKSUM_VERSIONS = Set.of(48, 49, 50, 51, 52, 53);
    private static final Pattern DDL_PRIVILEGE = Pattern.compile(
            "\\b(?:ALL\\s+PRIVILEGES|CREATE|ALTER|DROP)\\b", Pattern.CASE_INSENSITIVE);

    public int execute(String command, Map<String, String> environment, PrintStream output) {
        String normalizedCommand = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_COMMANDS.contains(normalizedCommand)) {
            output.println("不支持的数据库命令：" + (command == null ? "" : command));
            return 2;
        }
        List<String> missing = missingEnvironment(environment, REQUIRED_ENVIRONMENT);
        if (!missing.isEmpty()) {
            output.println("缺少数据库迁移配置：" + String.join(", ", missing));
            return 2;
        }
        if ("bootstrap-admin".equals(normalizedCommand)) {
            String password = environment.get("SPD_INITIAL_ADMIN_PASSWORD");
            if (isBlank(password) || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                output.println("SPD_INITIAL_ADMIN_PASSWORD 必须为至少 16 个字符、最多 72 字节的初始密码。");
                return 2;
            }
        }
        if ("legacy-repair".equals(normalizedCommand)) {
            List<String> legacyMissing = missingEnvironment(environment,
                    List.of("SPD_BACKUP_EVIDENCE", "SPD_LEGACY_REPAIR_CONFIRM"));
            if (!legacyMissing.isEmpty()) {
                output.println("旧库修复缺少安全确认：" + String.join(", ", legacyMissing));
                return 2;
            }
            int confirmationResult = validateLegacyConfirmation(environment, output);
            if (confirmationResult != 0) {
                return confirmationResult;
            }
        }

        try {
            return switch (normalizedCommand) {
                case "validate" -> validate(environment, output);
                case "migrate" -> migrate(environment, output);
                case "legacy-repair" -> repairLegacyHistory(environment, output);
                case "bootstrap-admin" -> bootstrapAdmin(environment, output);
                default -> 2;
            };
        } catch (Exception exception) {
            output.println("数据库 " + normalizedCommand + " 失败：" + sanitizedMessage(exception, environment));
            return 4;
        }
    }

    private int validate(Map<String, String> environment, PrintStream output) throws Exception {
        ValidateResult result = flyway(environment, true, false).validateWithResult();
        if (!result.validationSuccessful) {
            output.println("数据库 validate 失败：" + invalidMigrationSummary(result.invalidMigrations));
            return 4;
        }
        if (Boolean.parseBoolean(environment.getOrDefault("SPD_REQUIRE_NO_DDL", "false"))) {
            List<String> grants = currentUserGrants(environment);
            if (containsDdlPrivilege(grants)) {
                output.println("运行账号包含 CREATE、ALTER、DROP 或 ALL PRIVILEGES，禁止用于应用服务。");
                return 6;
            }
            output.println("运行账号权限校验通过：未发现 DDL 权限。");
        }
        output.println("数据库校验通过，已校验迁移数量：" + result.validateCount);
        return 0;
    }

    private int migrate(Map<String, String> environment, PrintStream output) {
        MigrateResult result = flyway(environment, true, false).migrate();
        output.println("数据库迁移完成，本次执行迁移数量：" + result.migrationsExecuted);
        return result.success ? 0 : 4;
    }

    // Replace only immutable migration seed credentials; never overwrite operator-managed passwords.
    private int bootstrapAdmin(Map<String, String> environment, PrintStream output) throws Exception {
        try (Connection connection = DriverManager.getConnection(environment.get("SPD_DB_URL"),
                environment.get("SPD_FLYWAY_USERNAME"), environment.get("SPD_FLYWAY_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE sys_user SET password=? WHERE username='admin' AND password=? AND deleted=0")) {
                    statement.setString(1, new BCryptPasswordEncoder().encode(environment.get("SPD_INITIAL_ADMIN_PASSWORD")));
                    statement.setString(2, SEED_PASSWORD_HASH);
                    updated = statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE sys_user SET status=0 WHERE username IN ('operator01','nurse01') AND password=? AND deleted=0")) {
                    statement.setString(1, SEED_PASSWORD_HASH);
                    statement.executeUpdate();
                }
                connection.commit();
                output.println(updated == 1 ? "初始管理员密码已安全设置，默认演示账号已禁用。" : "管理员密码已由用户管理，保持不变。");
                return 0;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private int repairLegacyHistory(Map<String, String> environment, PrintStream output) throws IOException {
        ValidateResult before = flyway(environment, true, false).validateWithResult();
        if (before.validationSuccessful) {
            output.println("数据库迁移历史已经有效，无需执行 legacy-repair。");
            writeLegacyAudit(environment, "not-required", List.of(), List.of());
            return 0;
        }
        List<Integer> invalidVersions = legacyChecksumVersions(before.invalidMigrations);
        if (invalidVersions.isEmpty() || invalidVersions.size() != before.invalidMigrations.size()) {
            output.println("校验异常不限于 V48-V53 checksum mismatch，已中止且未执行 repair。");
            writeLegacyAudit(environment, "rejected", invalidVersions, List.of());
            return 5;
        }
        List<ChecksumChange> checksumChanges = checksumChanges(environment, invalidVersions);

        Flyway repairFlyway = flyway(environment, false, true);
        repairFlyway.migrate();
        repairFlyway.repair();
        ValidateResult after = flyway(environment, true, false).validateWithResult();
        if (!after.validationSuccessful) {
            output.println("legacy-repair 后重新校验失败：" + invalidMigrationSummary(after.invalidMigrations));
            writeLegacyAudit(environment, "post-validate-failed", invalidVersions, checksumChanges);
            return 5;
        }
        writeLegacyAudit(environment, "completed", invalidVersions, checksumChanges);
        output.println("旧库兼容迁移和历史修复完成，最终校验通过。");
        return 0;
    }

    private Flyway flyway(Map<String, String> environment, boolean validateOnMigrate, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(environment.get("SPD_DB_URL"), environment.get("SPD_FLYWAY_USERNAME"),
                        environment.get("SPD_FLYWAY_PASSWORD"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(validateOnMigrate)
                .outOfOrder(outOfOrder)
                .load();
    }

    private List<String> currentUserGrants(Map<String, String> environment) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                environment.get("SPD_DB_URL"), environment.get("SPD_FLYWAY_USERNAME"),
                environment.get("SPD_FLYWAY_PASSWORD"))) {
            return currentUserGrants(connection);
        }
    }

    static List<String> currentUserGrants(Connection connection) throws Exception {
        List<String> grants = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE ALL");
            List<String> roles = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery("SELECT ROLE_NAME, ROLE_HOST FROM information_schema.ENABLED_ROLES")) {
                while (resultSet.next()) {
                    roles.add(quoteRolePart(resultSet.getString("ROLE_NAME")) + "@"
                            + quoteRolePart(resultSet.getString("ROLE_HOST")));
                }
            }
            String query = roles.isEmpty() ? "SHOW GRANTS" : "SHOW GRANTS FOR CURRENT_USER USING " + String.join(", ", roles);
            try (ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    grants.add(resultSet.getString(1));
                }
            }
        }
        return grants;
    }

    private static String quoteRolePart(String value) {
        if (value == null) throw new IllegalArgumentException("数据库角色信息不完整，无法验证运行权限");
        return "`" + value.replace("`", "``") + "`";
    }

    static boolean containsDdlPrivilege(List<String> grants) {
        Pattern privileges = Pattern.compile("^GRANT\\s+(.+?)\\s+ON\\s+", Pattern.CASE_INSENSITIVE);
        return grants.stream().filter(value -> value != null).anyMatch(value -> {
            var match = privileges.matcher(value);
            return match.find() && DDL_PRIVILEGE.matcher(match.group(1)).find();
        });
    }

    private int validateLegacyConfirmation(Map<String, String> environment, PrintStream output) {
        Path backupEvidence = Path.of(environment.get("SPD_BACKUP_EVIDENCE")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(backupEvidence)) {
            output.println("未找到数据库备份凭据，禁止执行 legacy-repair：SPD_BACKUP_EVIDENCE");
            return 2;
        }
        String databaseName = databaseName(environment.get("SPD_DB_URL"));
        if (databaseName.isBlank() || !databaseName.equals(environment.get("SPD_LEGACY_REPAIR_CONFIRM").trim())) {
            output.println("SPD_LEGACY_REPAIR_CONFIRM 必须与 JDBC URL 中的数据库名完全一致。");
            return 2;
        }
        return 0;
    }

    private List<Integer> legacyChecksumVersions(List<ValidateOutput> invalidMigrations) {
        List<Integer> versions = new ArrayList<>();
        for (ValidateOutput invalid : invalidMigrations) {
            if (invalid.version == null || invalid.errorDetails == null
                    || invalid.errorDetails.errorCode == null
                    || !invalid.errorDetails.errorCode.toString().contains("CHECKSUM_MISMATCH")) {
                return List.of();
            }
            try {
                int version = Integer.parseInt(invalid.version);
                if (!LEGACY_CHECKSUM_VERSIONS.contains(version)) {
                    return List.of();
                }
                versions.add(version);
            } catch (NumberFormatException exception) {
                return List.of();
            }
        }
        return versions.stream().distinct().sorted().toList();
    }

    private List<ChecksumChange> checksumChanges(Map<String, String> environment, List<Integer> versions) {
        return Arrays.stream(flyway(environment, false, true).info().all())
                .filter(info -> info.getVersion() != null)
                .filter(info -> {
                    try {
                        return versions.contains(Integer.parseInt(info.getVersion().getVersion()));
                    } catch (NumberFormatException exception) {
                        return false;
                    }
                })
                .map(info -> new ChecksumChange(info.getVersion().getVersion(),
                        info.getAppliedChecksum(), info.getResolvedChecksum()))
                .toList();
    }

    private void writeLegacyAudit(Map<String, String> environment, String result, List<Integer> versions,
                                  List<ChecksumChange> checksumChanges)
            throws IOException {
        Path auditDirectory = Path.of(environment.getOrDefault("SPD_AUDIT_DIR", "./logs/migration-audit"))
                .toAbsolutePath().normalize();
        Files.createDirectories(auditDirectory);
        String timestamp = OffsetDateTime.now().toString().replace(':', '-');
        String json = "{\n"
                + "  \"timestamp\": \"" + jsonEscape(OffsetDateTime.now().toString()) + "\",\n"
                + "  \"database\": \"" + jsonEscape(databaseName(environment.get("SPD_DB_URL"))) + "\",\n"
                + "  \"migrationUser\": \"" + jsonEscape(environment.get("SPD_FLYWAY_USERNAME")) + "\",\n"
                + "  \"result\": \"" + jsonEscape(result) + "\",\n"
                + "  \"legacyVersions\": " + versions + ",\n"
                + "  \"checksumChanges\": [" + checksumChanges.stream()
                .map(ChecksumChange::toJson)
                .reduce((left, right) -> left + "," + right).orElse("") + "],\n"
                + "  \"backupEvidence\": \"" + jsonEscape(Path.of(environment.get("SPD_BACKUP_EVIDENCE"))
                .toAbsolutePath().normalize().toString()) + "\"\n"
                + "}\n";
        Files.writeString(auditDirectory.resolve("legacy-flyway-" + timestamp + ".json"), json,
                StandardCharsets.UTF_8);
    }

    private List<String> missingEnvironment(Map<String, String> environment, List<String> names) {
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (isBlank(environment.get(name))) {
                missing.add(name);
            }
        }
        return missing;
    }

    private String invalidMigrationSummary(List<ValidateOutput> invalidMigrations) {
        if (invalidMigrations == null || invalidMigrations.isEmpty()) {
            return "迁移历史无效";
        }
        return invalidMigrations.stream()
                .map(item -> "V" + (item.version == null ? "?" : item.version))
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("迁移历史无效");
    }

    private String sanitizedMessage(Exception exception, Map<String, String> environment) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        for (String secretName : List.of("SPD_FLYWAY_PASSWORD", "SPD_DB_PASSWORD", "SPD_JWT_SECRET", "SPD_INITIAL_ADMIN_PASSWORD")) {
            String secret = environment.get(secretName);
            if (!isBlank(secret)) {
                message = message.replace(secret, "<redacted>");
            }
        }
        return message.length() > 800 ? message.substring(0, 800) : message;
    }

    private String databaseName(String jdbcUrl) {
        if (jdbcUrl == null) return "";
        String withoutQuery = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        return slash >= 0 && slash + 1 < withoutQuery.length() ? withoutQuery.substring(slash + 1) : "";
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ChecksumChange(String version, Integer oldChecksum, Integer newChecksum) {
        private String toJson() {
            return "{\"version\":\"" + version + "\",\"oldChecksum\":" + numberOrNull(oldChecksum)
                    + ",\"newChecksum\":" + numberOrNull(newChecksum) + "}";
        }

        private String numberOrNull(Integer value) {
            return value == null ? "null" : value.toString();
        }
    }
}
