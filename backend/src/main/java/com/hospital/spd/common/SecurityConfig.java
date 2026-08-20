package com.hospital.spd.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 配置——JWT 无状态认证。
 * 公开端点：/api/auth/login、/api/health
 * 受保护端点：所有其他 /api/** 路径
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;
    private final RbacAuthorizationService rbacAuthorizationService;

    public SecurityConfig(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate, RbacAuthorizationService rbacAuthorizationService) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
        this.rbacAuthorizationService = rbacAuthorizationService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 创建 JWT 认证过滤器（非 @Bean，避免被自动注册为全局 Servlet 过滤器）
        OncePerRequestFilter jwtFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();

                // 跳过公开端点（request.getRequestURI() 包含 context-path）
                if (path.endsWith("/auth/login") || path.endsWith("/health") || path.endsWith("/actuator/health")) {
                    chain.doFilter(request, response);
                    return;
                }

                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未认证，请先登录\",\"data\":null,\"timestamp\":\"\"}");
                    return;
                }

                String token = authHeader.substring(7);
                if (!jwtUtil.validateToken(token)) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"令牌无效或已过期，请重新登录\",\"data\":null,\"timestamp\":\"\"}");
                    return;
                }

                // 将用户信息存入请求属性，供 Controller 使用
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                OperatorAccess operatorAccess = loadOperatorAccess(userId);
                if (operatorAccess == null) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"operator is disabled or missing\",\"data\":null,\"timestamp\":\"\"}");
                    return;
                }
                List<String> roles = operatorAccess.roles();
                List<String> permissions = rbacAuthorizationService.permissionCodes(userId);
                if (!rbacAuthorizationService.isAllowed(userId, roles, request.getMethod(), path)) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无权访问该功能\",\"data\":null,\"timestamp\":\"\"}");
                    return;
                }
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUsername", username);
                request.setAttribute("currentRoles", roles);
                request.setAttribute("currentPermissions", permissions);
                request.setAttribute("currentDeptId", operatorAccess.deptId());
                request.setAttribute("currentDataScope", operatorAccess.dataScope());

                // 设置 SecurityContext，使 Spring Security 认可已认证状态
                List<SimpleGrantedAuthority> authorities = java.util.stream.Stream.concat(
                        roles.stream(), permissions.stream().map(permission -> "PERM_" + permission))
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                try {
                    chain.doFilter(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private OperatorAccess loadOperatorAccess(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.dept_id AS deptId,
                       COALESCE(MIN(r.data_scope), 4) AS dataScope,
                       GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.role_code) AS roleCodes
                  FROM sys_user u
                  LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
                  LEFT JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0 AND r.status = 1
                 WHERE u.user_id = ? AND u.deleted = 0 AND u.status = 1
                 GROUP BY u.user_id, u.dept_id
                """, userId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Long deptId = row.get("deptId") instanceof Number number ? number.longValue() : null;
        Integer dataScope = row.get("dataScope") instanceof Number number
                ? number.intValue()
                : OperatorContext.DATA_SCOPE_SELF;
        return new OperatorAccess(roleNames(row.get("roleCodes")), deptId, dataScope);
    }

    private static List<String> roleNames(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of("ROLE_USER");
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> "ROLE_" + role.toUpperCase())
                .toList();
    }

    private record OperatorAccess(List<String> roles, Long deptId, Integer dataScope) {
    }
}
