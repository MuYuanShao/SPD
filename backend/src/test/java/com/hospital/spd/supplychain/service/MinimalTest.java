package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.QuotaTemplateRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinimalTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SupplyChainSupport support;

    private QuotaTemplateService service;

    @BeforeEach
    void setUp() {
        service = new QuotaTemplateService(jdbcTemplate, support);
    }

    @Test
    void testAnyString() {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(anyString(), any()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001",
                        "unit", "包",
                        "quotaManaged", 1, "highValue", 1, "coldChain", 0
                ));

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("high value");
    }
}
