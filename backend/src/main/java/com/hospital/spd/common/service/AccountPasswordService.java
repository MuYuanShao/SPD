package com.hospital.spd.common.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Verifies account credentials and changes the signed-in user's password. */
@Service
public class AccountPasswordService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AccountPasswordService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                                  AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public boolean matches(String password, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")) {
            return passwordEncoder.matches(password, storedPassword);
        }
        return storedPassword.equals(password);
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("请输入当前密码");
        }
        if (newPassword == null || newPassword.codePointCount(0, newPassword.length()) < 8
                || newPassword.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF)) {
            throw new IllegalArgumentException("新密码至少需要 8 个字符");
        }
        if (newPassword.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("新密码不能包含换行或其他控制字符");
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("新密码不能超过 72 个 UTF-8 字节");
        }

        List<String> passwords = jdbcTemplate.queryForList("""
                SELECT password FROM sys_user WHERE user_id = ? AND status = 1 AND deleted = 0
                """, String.class, userId);
        String storedPassword = passwords.isEmpty() ? null : passwords.get(0);
        if (!matches(currentPassword, storedPassword)) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (matches(newPassword, storedPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        int updated = jdbcTemplate.update("""
                UPDATE sys_user SET password = ?
                 WHERE user_id = ? AND status = 1 AND deleted = 0
                   AND CAST(password AS BINARY) = CAST(? AS BINARY)
                """, passwordEncoder.encode(newPassword), userId, storedPassword);
        if (updated != 1) {
            throw new IllegalArgumentException("账户状态或密码已变更，请重新登录后重试");
        }
        auditLogService.record("user", "change_password", userId, String.valueOf(userId), "用户修改本人密码");
    }
}
