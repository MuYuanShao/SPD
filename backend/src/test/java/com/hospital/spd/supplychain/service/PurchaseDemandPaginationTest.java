package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseDemandPaginationTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SupplyChainSupport support;

    @Test
    void pagesDemandHeadersAndReturnsEveryItemOfTheSelectedDemand() {
        PurchaseOrderService service = new PurchaseOrderService(jdbcTemplate, support);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(Map.of("demandNo", "XQ20260901001")),
                        List.of(
                                Map.of("demandId", 1L, "demandNo", "XQ20260901001", "productCode", "P001"),
                                Map.of("demandId", 2L, "demandNo", "XQ20260901001", "productCode", "P002")
                        ));

        Map<String, Object> result = service.listDemands(Map.of("page", "1", "size", "20"));

        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(result.get("rows")).asList().hasSize(2);
    }
}
