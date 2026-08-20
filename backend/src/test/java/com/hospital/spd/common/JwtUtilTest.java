package com.hospital.spd.common;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil 令牌工具测试")
class JwtUtilTest {

    private static final String TEST_SECRET = "my-test-secret-key-that-is-at-least-256-bits-long-for-hs256";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken：生成令牌不为 null")
    void should_generate_non_null_token_when_generateToken_given_valid_inputs() {
        String token = jwtUtil.generateToken(1L, "admin");
        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generateToken 与 getUserIdFromToken：正确提取用户 ID")
    void should_extract_user_id_when_getUserIdFromToken_given_valid_token() {
        String token = jwtUtil.generateToken(42L, "testUser");
        Long userId = jwtUtil.getUserIdFromToken(token);
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("generateToken 与 getUsernameFromToken：正确提取用户名")
    void should_extract_username_when_getUsernameFromToken_given_valid_token() {
        String token = jwtUtil.generateToken(1L, "张三");
        String username = jwtUtil.getUsernameFromToken(token);
        assertThat(username).isEqualTo("张三");
    }

    @Test
    @DisplayName("validateToken：有效令牌返回 true")
    void should_return_true_when_validateToken_given_valid_token() {
        String token = jwtUtil.generateToken(1L, "admin");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken：无效令牌返回 false")
    void should_return_false_when_validateToken_given_invalid_token() {
        assertThat(jwtUtil.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("validateToken：篡改令牌返回 false")
    void should_return_false_when_validateToken_given_tampered_token() {
        String token = jwtUtil.generateToken(1L, "admin");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateToken：过期令牌返回 false")
    void should_return_false_when_validateToken_given_expired_token() throws Exception {
        // 使用过期的 secret 创建已过期令牌：直接构造一个过期令牌
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("username", "admin")
                .issuedAt(new Date(System.currentTimeMillis() - 2000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken：空白令牌返回 false")
    void should_return_false_when_validateToken_given_blank_token() {
        assertThat(jwtUtil.validateToken("")).isFalse();
        assertThat(jwtUtil.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("generateToken 生成的令牌可循环验证")
    void should_round_trip_successfully() {
        String token = jwtUtil.generateToken(99L, "cycleUser");
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(99L);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("cycleUser");
    }
}
