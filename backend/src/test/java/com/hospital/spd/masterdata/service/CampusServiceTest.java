package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.CampusUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampusServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CampusService service = new CampusService(jdbc);
    private final CampusUpsertRequest request = new CampusUpsertRequest("", "测试院区", null, null, null, 0, 1);

    @Test
    void retriesWithFreshCodeWhenAnotherRequestTakesTheGeneratedCode() {
        when(jdbc.queryForObject(contains("MAX("), eq(Long.class))).thenReturn(8L, 9L);
        when(jdbc.update(contains("INSERT INTO campus"), eq("XQ009"), eq("测试院区"),
                isNull(), isNull(), isNull(), eq(0), eq(1))).thenThrow(duplicate("uk_campus_code"));
        when(jdbc.update(contains("INSERT INTO campus"), eq("XQ010"), eq("测试院区"),
                isNull(), isNull(), isNull(), eq(0), eq(1))).thenReturn(1);

        assertThat(service.createCampus(request)).containsEntry("campusCode", "XQ010");
        verify(jdbc, times(2)).queryForObject(contains("MAX("), eq(Long.class));
    }

    @Test
    void duplicateNameIsReportedWithoutRetrying() {
        when(jdbc.queryForObject(contains("MAX("), eq(Long.class))).thenReturn(8L);
        when(jdbc.update(contains("INSERT INTO campus"), eq("XQ009"), eq("测试院区"),
                isNull(), isNull(), isNull(), eq(0), eq(1))).thenThrow(duplicate("uk_campus_name"));

        assertThatThrownBy(() -> service.createCampus(request)).hasMessage("院区名称已存在");
        verify(jdbc).queryForObject(contains("MAX("), eq(Long.class));
    }

    private static DuplicateKeyException duplicate(String index) {
        return new DuplicateKeyException("duplicate", new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'fixture' for key 'campus." + index + "'", "23000", 1062));
    }
}
