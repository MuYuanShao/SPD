package com.hospital.spd.licenses;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LicenseEligibilityServiceTest {
    @Test void expiredCandidateRegistrationIsBlockedButUnspecifiedAndValidDatesAreAllowed() {
        var service = new LicenseEligibilityService(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> service.requireCandidateRegistration(LocalDate.now().minusDays(1).toString())).hasMessageContaining("注册证已过期");
        assertThatCode(() -> service.requireCandidateRegistration(LocalDate.now().toString())).doesNotThrowAnyException();
        assertThatCode(() -> service.requireCandidateRegistration(null)).doesNotThrowAnyException();
    }
    @Test void malformedCandidateDateIsRejected() {
        assertThatThrownBy(() -> new LicenseEligibilityService(mock(JdbcTemplate.class)).requireCandidateRegistration("bad-date"))
                .hasMessageContaining("格式不正确");
    }
    @Test void linkedLicenseErrorNamesTheBlockingDocumentAndIsRecheckedAfterRenewal() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM license_document l"), any(Object[].class)))
                .thenReturn(List.of(Map.of("licenseName", "经营许可证")), List.of());
        var service = new LicenseEligibilityService(jdbc);
        assertThatThrownBy(() -> service.requireEligible("P1", 1L, 2L, null)).hasMessageContaining("经营许可证");
        assertThatCode(() -> service.requireEligible("P1", 1L, 2L, null)).doesNotThrowAnyException();
    }
}
