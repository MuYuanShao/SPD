package com.hospital.spd.foundation.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import static com.hospital.spd.common.SqlHelper.*;
import com.hospital.spd.foundation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maintains users, roles, permissions, and data scopes for the foundation administration module.
 */
@Service
public class FoundationService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;

    public FoundationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new BCryptPasswordEncoder(), new DataScopeService(OperatorContext::system));
    }

    public FoundationService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, new DataScopeService(OperatorContext::system));
    }

    @Autowired
    public FoundationService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, DataScopeService dataScopeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
    }

    public Map<String, Object> users(String username, String realName, String status, String deptName, Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE u.deleted = 0
                """);
        appendLike(where, args, "u.username", username);
        appendLike(where, args, "u.real_name", realName);
        appendLike(where, args, "d.dept_name", deptName);
        if (status != null && !status.isBlank()) {
            where.append(" AND u.status = ? ");
            args.add(Integer.parseInt(status));
        }
        dataScopeService.appendScope(where, args, "u.dept_id", "u.user_id");

        String fromClause = """
                  FROM sys_user u
                  LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                  LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
                  LEFT JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT u.user_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.user_id AS userId, u.username, u.real_name AS realName, u.phone, u.email,
                       u.gender, u.dept_id AS deptId, d.dept_name AS deptName, u.status,
                       DATE_FORMAT(u.last_login_time, '%Y-%m-%d %H:%i') AS lastLoginTime,
                       DATE_FORMAT(u.update_time, '%Y-%m-%d %H:%i') AS updateTime,
                       GROUP_CONCAT(r.role_name ORDER BY r.sort_order SEPARATOR '、') AS roleNames,
                       GROUP_CONCAT(r.role_id ORDER BY r.sort_order) AS roleIds
                  FROM sys_user u
                  LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                  LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
                  LEFT JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0
                """ + where + " GROUP BY u.user_id ORDER BY u.update_time DESC, u.user_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> createUser(UserRequest request) {
        validateUser(request, true);
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_user (username, password, real_name, phone, email, gender, dept_id, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, request.username(), passwordEncoder.encode(blankDefault(request.password(), temporaryPassword())), request.realName(),
                    emptyToNull(request.phone()), emptyToNull(request.email()), nvl(request.gender(), 0),
                    request.deptId(), nvl(request.status(), 1));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException(duplicateUserFieldMessage(ex));
        }
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM sys_user WHERE username = ?", Long.class, request.username());
        assignUserRoles(userId, request.roleIds());
        return Map.of("userId", userId);
    }

    public Map<String, Object> updateUser(Long userId, UserRequest request) {
        validateUser(request, false);
        try {
            jdbcTemplate.update("""
                    UPDATE sys_user
                       SET username = COALESCE(NULLIF(?, ''), username),
                           real_name = ?, phone = ?, email = ?, gender = ?, dept_id = ?, status = ?
                     WHERE user_id = ? AND deleted = 0
                    """, request.username(), request.realName(), emptyToNull(request.phone()), emptyToNull(request.email()),
                    nvl(request.gender(), 0), request.deptId(), nvl(request.status(), 1), userId);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException(duplicateUserFieldMessage(ex));
        }
        assignUserRoles(userId, request.roleIds());
        return Map.of("updated", 1);
    }

    public Map<String, Object> deleteUsers(UserIdsRequest request) {
        List<Long> ids = nonEmpty(request.userIds(), "请选择要删除的用户");
        String marks = placeholders(ids.size());
        jdbcTemplate.update("UPDATE sys_user SET deleted = 1 WHERE user_id IN (" + marks + ") AND username <> 'admin'", ids.toArray());
        return Map.of("deleted", ids.size());
    }

    public Map<String, Object> resetPassword(UserIdsRequest request) {
        List<Long> ids = nonEmpty(request.userIds(), "请选择要重置密码的用户");
        String temporaryPassword = temporaryPassword();
        Object[] args = new Object[ids.size() + 1];
        args[0] = passwordEncoder.encode(temporaryPassword);
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        jdbcTemplate.update("UPDATE sys_user SET password = ?, login_fail_count = 0, lock_time = NULL WHERE user_id IN (" + placeholders(ids.size()) + ")", args);
        return Map.of("reset", ids.size(), "temporaryPassword", temporaryPassword);
    }

    private static String temporaryPassword() {
        return "ChangeMe@" + java.time.LocalDate.now().getYear();
    }

    private static String duplicateUserFieldMessage(DuplicateKeyException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        String normalized = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalized.contains("uk_username") || normalized.contains("username")) {
            return "用户名已存在，请使用其他用户名";
        }
        if (normalized.contains("uk_phone") || normalized.contains("phone")) {
            return "手机号已存在，请使用其他手机号";
        }
        return "用户名或手机号已存在";
    }

    private static String generatedRoleCode() {
        return "role_" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now());
    }

    @Transactional
    public Map<String, Object> updateUserRoles(UserRoleAssignRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("请选择用户");
        }
        assignUserRoles(request.userId(), request.roleIds());
        return Map.of("assigned", true);
    }

    public Map<String, Object> roles(String roleName, String roleCode, String status, Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE r.deleted = 0
                """);
        appendLike(where, args, "r.role_name", roleName);
        appendLike(where, args, "r.role_code", roleCode);
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            args.add(Integer.parseInt(status));
        }

        String fromClause = """
                  FROM sys_role r
                  LEFT JOIN sys_user_role ur ON ur.role_id = r.role_id
                  LEFT JOIN sys_role_perm rp ON rp.role_id = r.role_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT r.role_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.role_id AS roleId, r.role_name AS roleName, r.role_code AS roleCode,
                       r.description, r.data_scope AS dataScope, r.status, r.sort_order AS sortOrder,
                       DATE_FORMAT(r.update_time, '%Y-%m-%d %H:%i') AS updateTime,
                       COUNT(DISTINCT ur.user_id) AS userCount,
                       COUNT(DISTINCT rp.perm_id) AS permissionCount,
                       GROUP_CONCAT(DISTINCT rp.perm_id ORDER BY rp.perm_id) AS permissionIds,
                       GROUP_CONCAT(DISTINCT rd.dept_id ORDER BY rd.dept_id) AS customDeptIds
                  FROM sys_role r
                  LEFT JOIN sys_user_role ur ON ur.role_id = r.role_id
                  LEFT JOIN sys_role_perm rp ON rp.role_id = r.role_id
                  LEFT JOIN sys_role_dept rd ON rd.role_id = r.role_id
                """ + where + " GROUP BY r.role_id ORDER BY r.sort_order, r.role_id LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    public Map<String, Object> createRole(RoleRequest request) {
        validateRole(request, true);
        String roleCode = blankDefault(request.roleCode(), generatedRoleCode());
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_role (role_name, role_code, description, data_scope, status, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, request.roleName(), roleCode, request.description(), nvl(request.dataScope(), 1),
                    nvl(request.status(), 1), nvl(request.sortOrder(), 99));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        Long roleId = jdbcTemplate.queryForObject("SELECT role_id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
        return Map.of("roleId", roleId);
    }

    public Map<String, Object> updateRole(Long roleId, RoleRequest request) {
        validateRole(request, false);
        jdbcTemplate.update("""
                UPDATE sys_role
                   SET role_name = ?, description = ?, data_scope = ?, status = ?, sort_order = ?
                 WHERE role_id = ? AND deleted = 0
                """, request.roleName(), request.description(), nvl(request.dataScope(), 1),
                nvl(request.status(), 1), nvl(request.sortOrder(), 99), roleId);
        return Map.of("updated", 1);
    }

    public Map<String, Object> deleteRoles(RoleIdsRequest request) {
        List<Long> ids = nonEmpty(request.roleIds(), "请选择要删除的角色");
        jdbcTemplate.update("UPDATE sys_role SET deleted = 1 WHERE role_id IN (" + placeholders(ids.size()) + ") AND role_code NOT IN ('admin')", ids.toArray());
        return Map.of("deleted", ids.size());
    }

    @Transactional
    public Map<String, Object> updateRolePermissions(RolePermissionAssignRequest request) {
        if (request.roleId() == null) {
            throw new IllegalArgumentException("请选择角色");
        }
        jdbcTemplate.update("DELETE FROM sys_role_perm WHERE role_id = ?", request.roleId());
        for (Long permissionId : request.permissionIds() == null ? List.<Long>of() : request.permissionIds()) {
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_perm (role_id, perm_id) VALUES (?, ?)", request.roleId(), permissionId);
        }
        return Map.of("assigned", true);
    }

    @Transactional
    public Map<String, Object> updateDataPermission(DataPermissionRequest request) {
        if (request.roleId() == null || request.dataScope() == null) {
            throw new IllegalArgumentException("请选择角色和数据权限");
        }
        jdbcTemplate.update("UPDATE sys_role SET data_scope = ? WHERE role_id = ? AND deleted = 0", request.dataScope(), request.roleId());
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id = ?", request.roleId());
        if (request.dataScope() == OperatorContext.DATA_SCOPE_CUSTOM) {
            for (Long deptId : request.deptIds() == null ? List.<Long>of() : request.deptIds()) {
                jdbcTemplate.update("INSERT IGNORE INTO sys_role_dept (role_id, dept_id) VALUES (?, ?)", request.roleId(), deptId);
            }
        }
        return Map.of("updated", 1);
    }

    public List<Map<String, Object>> permissions() {
        return jdbcTemplate.queryForList("""
                SELECT perm_id AS permissionId, parent_id AS parentId, perm_name AS permissionName,
                       perm_code AS permissionCode, perm_type AS permissionType, path, sort_order AS sortOrder
                  FROM sys_permission
                 WHERE deleted = 0 AND status = 1
                 ORDER BY parent_id, sort_order, perm_id
                """);
    }

    public List<Map<String, Object>> departmentOptions() {
        return jdbcTemplate.queryForList("""
                SELECT dept_id AS deptId, dept_name AS deptName, dept_code AS deptCode
                  FROM sys_dept
                 WHERE deleted = 0 AND status = 1
                 ORDER BY sort_order, dept_id
                """);
    }

    private void assignUserRoles(Long userId, List<Long> roleIds) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (Long roleId : roleIds == null ? List.<Long>of() : roleIds) {
            jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    private static void validateUser(UserRequest request, boolean create) {
        if (create && (request.username() == null || request.username().isBlank())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.realName() == null || request.realName().isBlank()) {
            throw new IllegalArgumentException("真实姓名不能为空");
        }
    }

    private static void validateRole(RoleRequest request, boolean create) {
        if (request.roleName() == null || request.roleName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
    }
}
