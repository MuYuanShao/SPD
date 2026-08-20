package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalSettlementModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalSettlementModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalSettlementModule(jdbcTemplate, support);
    }

    @Test
    void confirmsPendingSettlement() {
        when(jdbcTemplate.queryForMap(contains("FROM settlement_bill"), eq("JS20260601003")))
                .thenReturn(Map.of("settlementId", 79L, "status", "pending_confirm"));
        when(jdbcTemplate.update(contains("UPDATE settlement_bill"), eq("JS20260601003"))).thenReturn(1);

        Map<String, Object> result = module.confirmSettlement("JS20260601003");

        assertThat(result)
                .containsEntry("settlementNo", "JS20260601003")
                .containsEntry("status", "confirmed");
        verify(jdbcTemplate).update(contains("UPDATE settlement_bill"), eq("JS20260601003"));
        verify(support).writeAudit("settlement_bill", "confirm_settlement", 79L,
                "JS20260601003", "confirm settlement bill");
    }

    @Test
    void rejectsConfirmWhenSettlementIsNotPending() {
        when(jdbcTemplate.queryForMap(contains("FROM settlement_bill"), eq("JS20260601004")))
                .thenReturn(Map.of("settlementId", 80L, "status", "confirmed"));

        assertThatThrownBy(() -> module.confirmSettlement("JS20260601004"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending settlement");
    }
}
