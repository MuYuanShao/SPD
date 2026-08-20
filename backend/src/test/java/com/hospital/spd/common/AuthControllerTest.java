package com.hospital.spd.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuthController controller = new AuthController(
            jdbcTemplate,
            mock(PasswordEncoder.class),
            mock(JwtUtil.class)
    );

    @Test
    void administratorReceivesWildcardMenuAndFeaturePermissions() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentRoles")).thenReturn(List.of("ROLE_ADMIN"));
        when(request.getAttribute("currentPermissions")).thenReturn(List.of("dashboard"));
        when(request.getAttribute("currentDataScope")).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "userId", 1L,
                "username", "admin",
                "realName", "系统管理员",
                "status", 1
        )));

        ApiResponse<Map<String, Object>> response = controller.me(request);

        assertThat(response.code()).isZero();
        assertThat(response.data().get("roles")).isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(response.data().get("permissionCodes")).isEqualTo(List.of("*"));
        assertThat(response.data().get("menuCodes")).isEqualTo(List.of("*"));
    }
}
