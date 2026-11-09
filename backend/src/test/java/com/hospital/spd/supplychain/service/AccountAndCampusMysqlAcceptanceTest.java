package com.hospital.spd.supplychain.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real password and campus HTTP writes in a disposable MySQL schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class AccountAndCampusMysqlAcceptanceTest {
    private static IsolatedMysqlDatabase database;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase();
        database.register(registry);
    }

    @AfterAll
    static void cleanup() {
        if (database != null) database.close();
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @Test
    void ordinaryAccountChangesPasswordAndCanOnlyLogInWithNewPassword() {
        String username = "account-" + UUID.randomUUID();
        String oldPassword = UUID.randomUUID().toString();
        String newPassword = UUID.randomUUID().toString();
        long userId = createUser(username, oldPassword);
        String token = token(username, oldPassword);
        String otherSession = token(username, oldPassword);

        assertThat(call(HttpMethod.PUT, "/auth/password", token,
                Map.of("currentPassword", oldPassword, "newPassword", newPassword)).path("code").asInt()).isZero();
        assertThat(login(username, oldPassword).path("code").asInt()).isEqualTo(401);
        assertThat(token(username, newPassword)).isNotBlank();
        assertThat(call(HttpMethod.GET, "/auth/me", otherSession, null)
                .path("data").path("userId").asLong()).isEqualTo(userId);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE biz_id = ? AND operation_type = 'change_password'
                """, Long.class, userId)).isEqualTo(1L);
    }

    @Test
    void twoBlankCodeCampusesReceiveDistinctCodesAndAppearInList() throws Exception {
        String username = "campus-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        long userId = createUser(username, password);
        assertThat(jdbc.update("""
                INSERT INTO sys_user_role(user_id, role_id)
                SELECT ?, role_id FROM sys_role WHERE role_code = 'ADMIN' AND deleted = 0
                """, userId)).isEqualTo(1);
        String token = token(username, password);
        String prefix = "测试院区-" + UUID.randomUUID().toString().substring(0, 12);
        var first = CompletableFuture.supplyAsync(() -> createCampus(token, prefix + "-甲"));
        var second = CompletableFuture.supplyAsync(() -> createCampus(token, prefix + "-乙"));
        String firstCode = first.get(15, TimeUnit.SECONDS);
        String secondCode = second.get(15, TimeUnit.SECONDS);
        assertThat(firstCode).matches("XQ[0-9]{3,}").isNotEqualTo(secondCode);
        assertThat(secondCode).matches("XQ[0-9]{3,}");

        JsonNode rows = call(HttpMethod.GET, "/master-data/campuses?campusName=" + prefix, token, null)
                .path("data").path("rows");
        assertThat(rows).extracting(row -> row.path("code").asText())
                .containsExactlyInAnyOrder(firstCode, secondCode);
        assertThat(call(HttpMethod.POST, "/master-data/campuses", token,
                Map.of("campusName", prefix + "-甲")).path("message").asText()).isEqualTo("院区名称已存在");
    }

    private long createUser(String username, String password) {
        jdbc.update("INSERT INTO sys_user(username, password, status) VALUES (?, ?, 1)",
                username, encoder.encode(password));
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private String createCampus(String token, String name) {
        JsonNode response = call(HttpMethod.POST, "/master-data/campuses", token,
                Map.of("campusCode", "", "campusName", name));
        assertThat(response.path("code").asInt()).as(response.path("message").asText()).isZero();
        return response.path("data").path("campusCode").asText();
    }

    private JsonNode login(String username, String password) {
        return call(HttpMethod.POST, "/auth/login", null, Map.of("username", username, "password", password));
    }

    private String token(String username, String password) {
        JsonNode response = login(username, password);
        assertThat(response.path("code").asInt()).isZero();
        return response.path("data").path("token").asText();
    }

    private JsonNode call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        var response = http.exchange("http://127.0.0.1:" + port + "/api" + path,
                method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
