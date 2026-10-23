package com.hospital.spd.masterdata.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class ProductTraceabilityPolicyTest {
    @Test void blocksBothDirectionsWhenInventoryOrUnfinishedTraceBusinessExists() {
        for (boolean highValue : new boolean[]{false, true}) {
            var jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(contains("is_high_value AS highValue"), eq("P1")))
                    .thenReturn(List.of(Map.of("productId", 1L, "highValue", highValue ? 1 : 0)));
            when(jdbc.queryForObject(contains("EXISTS(SELECT 1 FROM inventory_balance"), eq(Long.class),
                    eq(1L), eq(1L), eq(1L), eq(1L), eq("P1"))).thenReturn(1L);
            assertThatThrownBy(() -> new ProductTraceabilityPolicy(jdbc).requireSafeTransition("P1", !highValue))
                    .hasMessageContaining("不能切换高值属性");
        }
    }

    @Test void allowsAnEmptyProductToChangeWithoutRewritingHistoricalTrace() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq("P1"))).thenReturn(List.of(Map.of("productId", 1L, "highValue", 0)));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any())).thenReturn(0L);
        new ProductTraceabilityPolicy(jdbc).requireSafeTransition("P1", true);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void legacyHighValuePackagesCannotUseLowValueConsumptionOrPacking() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L))).thenReturn(0L);
        assertThatThrownBy(() -> new ProductTraceabilityPolicy(jdbc).requirePackageProduct(1L)).hasMessageContaining("高值");
    }
}
