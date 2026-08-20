package com.hospital.spd.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Exposes login and current-user endpoints for the authentication boundary.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户登录——验证用户名密码，返回 JWT 令牌
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (request.username() == null || request.username().trim().isEmpty()) {
            return ApiResponse.error(400, "请输入用户名");
        }
        if (request.password() == null || request.password().trim().isEmpty()) {
            return ApiResponse.error(400, "请输入密码");
        }

        // 查询用户
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT user_id, username, `password`, real_name, `status`
                  FROM sys_user
                 WHERE username = ? AND deleted = 0
                 LIMIT 1
                """, request.username().trim());

        if (users.isEmpty()) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        Map<String, Object> user = users.get(0);
        String storedPassword = (String) user.get("password");
        Long userId = ((Number) user.get("user_id")).longValue();
        String username = (String) user.get("username");
        Integer status = user.get("status") instanceof Number ? ((Number) user.get("status")).intValue() : 0;

        if (status != 1) {
            return ApiResponse.error(403, "账号已禁用，请联系管理员");
        }

        // 验证密码（兼容旧版明文密码 + BCrypt 加密密码）
        boolean passwordMatch = false;
        if (storedPassword != null) {
            if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")) {
                passwordMatch = passwordEncoder.matches(request.password(), storedPassword);
            } else {
                passwordMatch = storedPassword.equals(request.password());
            }
        }

        if (!passwordMatch) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        // 生成令牌
        String token = jwtUtil.generateToken(userId, username);

        // 写入登录日志
        jdbcTemplate.update("""
                INSERT INTO sys_login_log (user_id, username, login_time, login_ip, status, message)
                VALUES (?, ?, NOW(), ?, 1, '登录成功')
                """, userId, username, clientIp(servletRequest));

        log.info("User '{}' logged in successfully", username);
        return ApiResponse.ok(new LoginResponse(token, userId, username));
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");

        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT user_id AS userId, username, real_name AS realName,
                       phone, email, dept_id AS deptId, `status`
                  FROM sys_user
                 WHERE user_id = ? AND deleted = 0
                 LIMIT 1
                """, userId);

        if (users.isEmpty()) {
            return ApiResponse.error(404, "用户不存在");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>(users.get(0));
        List<String> roles = stringList(request.getAttribute("currentRoles"));
        List<String> permissions = roles.contains("ROLE_ADMIN")
                ? List.of("*")
                : stringList(request.getAttribute("currentPermissions"));
        result.put("roles", roles);
        result.put("permissionCodes", permissions);
        result.put("menuCodes", permissions.stream().filter(code -> !code.contains(":")).toList());
        result.put("dataScope", request.getAttribute("currentDataScope"));
        return ApiResponse.ok(result);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
