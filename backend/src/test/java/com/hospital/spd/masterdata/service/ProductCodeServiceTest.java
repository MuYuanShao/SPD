package com.hospital.spd.masterdata.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCodeServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAllocateGeneratedCodeWithoutRuntimeDdl() {
        ProductCodeService service = new ProductCodeService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("spd_product_code"))).thenReturn(12L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("SPD000012"))).thenReturn(0, 0);

        String code = service.resolveNewProductCode(null, null);

        assertThat(code).isEqualTo("SPD000012");
        verify(jdbcTemplate, never()).execute(anyString());
    }
}
