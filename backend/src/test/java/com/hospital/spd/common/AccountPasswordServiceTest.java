package com.hospital.spd.common;

import com.hospital.spd.common.service.AccountPasswordService;
import com.hospital.spd.common.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountPasswordServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountPasswordService service = new AccountPasswordService(jdbc, encoder, audit);

    @Test
    void legacyPasswordIsReplacedWithBcryptAndOnlyNewPasswordMatches() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of("old-password"));
        when(jdbc.update(contains("CAST(password AS BINARY)"), anyString(), eq(7L), eq("old-password")))
                .thenAnswer(call -> {
                    String saved = call.getArgument(1);
                    assertThat(saved).startsWith("$2a$");
                    assertThat(service.matches("new-password", saved)).isTrue();
                    assertThat(service.matches("old-password", saved)).isFalse();
                    return 1;
                });

        service.changePassword(7L, "old-password", "new-password");

        verify(audit).record("user", "change_password", 7L, "7", "用户修改本人密码");
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutWriting() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L)))
                .thenReturn(List.of(encoder.encode("old-password")));

        assertThatThrownBy(() -> service.changePassword(7L, "wrong-password", "new-password"))
                .hasMessage("当前密码不正确");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsPasswordBeyondBcryptByteLimit() {
        assertThatThrownBy(() -> service.changePassword(7L, "old-password", "密".repeat(25)))
                .hasMessageContaining("72");
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void concurrentCredentialUpdateCannotBeOverwritten() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of("old-password"));
        when(jdbc.update(contains("CAST(password AS BINARY)"), anyString(), eq(7L), eq("old-password")))
                .thenReturn(0);

        assertThatThrownBy(() -> service.changePassword(7L, "old-password", "new-password"))
                .hasMessageContaining("已变更");
        verifyNoInteractions(audit);
    }
}
