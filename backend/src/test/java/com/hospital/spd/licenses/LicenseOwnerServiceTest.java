package com.hospital.spd.licenses;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LicenseOwnerServiceTest {
    private LicenseUpsertRequest request(Long id, String code) {
        return new LicenseUpsertRequest("product", "注册证", "R1", "product", id, code, "用户填写的错误名称",
                null, null, null, null, "2099-12-31", 1, null, 1);
    }
    @Test void omittedIdRetainsExistingSubjectAndUsesCanonicalName() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of("id", 7L, "code", "P7", "name", "实际商品")));
        var result = new LicenseOwnerService(jdbc).resolve("product", request(null, "P7"), Map.of("ownerType", "product", "ownerId", 7L, "ownerCode", "P7"));
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.name()).isEqualTo("实际商品");
        var legacy = new LicenseOwnerService(jdbc).resolve("product", request(null, "P7"), Map.of("ownerType", "", "ownerId", 7L, "ownerCode", "P7"));
        assertThat(legacy.id()).isEqualTo(7L);
    }
    @Test void mismatchedIdAndCodeAreRejected() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of("id", 7L, "code", "P7", "name", "实际商品")));
        assertThatThrownBy(() -> new LicenseOwnerService(jdbc).resolve("product", request(7L, "P8"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ID与编码不一致");
    }
    @Test void missingAndInactiveSubjectsAreRejected() {
        var service = new LicenseOwnerService(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> service.resolve("product", request(null, ""), null)).hasMessageContaining("选择");
        assertThatThrownBy(() -> service.resolve("product", request(7L, "P7"), null)).hasMessageContaining("已停用");
    }
}
