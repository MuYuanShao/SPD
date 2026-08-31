package com.hospital.spd.common.service;

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
class DocumentNumberServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldGenerateDailyPaddedNumber() {
        DocumentNumberService service = new DocumentNumberService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(7L);

        String value = service.next("CG", 3);

        assertThat(value).startsWith("CG");
        assertThat(value).endsWith("007");
        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate).update(anyString(), eq(value.substring(0, value.length() - 3)), eq(1L), eq(0L));
    }

    @Test
    void shouldAdvancePastExistingBusinessNumbers() {
        DocumentNumberService service = new DocumentNumberService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(11), anyString())).thenReturn(3L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(4L);

        String value = service.next("KC", 5, "inventory_event", "event_no");

        assertThat(value).startsWith("KC");
        assertThat(value).endsWith("00004");
        verify(jdbcTemplate).update(anyString(), eq(value.substring(0, value.length() - 5)), eq(4L), eq(3L));
    }

    @Test
    void shouldExposePendingProductApplicationNumberPolicy() {
        assertThat(DocumentKind.PENDING_PRODUCT_APPLICATION.prefix()).isEqualTo("SP");
        assertThat(DocumentKind.PENDING_PRODUCT_APPLICATION.width()).isEqualTo(3);
        assertThat(DocumentKind.PENDING_PRODUCT_APPLICATION.table()).isEqualTo("pending_product_application");
        assertThat(DocumentKind.PENDING_PRODUCT_APPLICATION.column()).isEqualTo("application_no");
    }
}
