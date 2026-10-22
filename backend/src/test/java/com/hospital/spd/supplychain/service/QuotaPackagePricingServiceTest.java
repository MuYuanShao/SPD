package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaPackagePricingServiceTest {
    @Mock JdbcTemplate jdbc;
    QuotaPackagePricingService service;

    @BeforeEach
    void setup() { service = new QuotaPackagePricingService(jdbc); }

    Map<String, Object> before() {
        return Map.of("productId", 1L, "unitPrice", new BigDecimal("2"), "unit", "支");
    }

    @Test
    void unchangedPriceDoesNotCreateAdjustment() {
        when(jdbc.queryForMap(contains("FROM product WHERE product_id"), eq(1L)))
                .thenReturn(Map.of("unitPrice", new BigDecimal("2.0000"), "unit", "支"));
        service.afterCatalogApproval("APP1", before(), 1);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void admissionWithoutPreviousCatalogDoesNotReprice() {
        service.afterCatalogApproval("NEW", null, 1);
        verifyNoInteractions(jdbc);
    }

    @Test
    void unitChangeAbortsCombinedPriceAdjustment() {
        when(jdbc.queryForMap(contains("FROM product WHERE product_id"), eq(1L)))
                .thenReturn(Map.of("unitPrice", new BigDecimal("3"), "unit", "盒"));
        assertThatThrownBy(() -> service.afterCatalogApproval("APP1", before(), 1))
                .hasMessageContaining("不能同时变更基本单位");
    }

    @Test
    void repeatedApprovalDoesNotIncrementVersion() {
        when(jdbc.queryForMap(contains("FROM product WHERE product_id"), eq(1L)))
                .thenReturn(Map.of("unitPrice", new BigDecimal("3"), "unit", "支"));
        when(jdbc.queryForList(contains("SELECT change_id"), eq(Long.class), eq("APP1"))).thenReturn(List.of(9L));
        service.afterCatalogApproval("APP1", before(), 1);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void sourceWithoutSettlementStillRequiresSourceIdentity() {
        assertThat(service.isSettlementLocked(1)).isTrue();
        verify(jdbc, never()).queryForList(contains("FROM settlement_bill_item"), eq(Long.class), any(), any(), any());
    }

    @Test
    void newSettlementUsesCurrentPackagePriceWithoutOverwritingConsumptionCost() {
        when(jdbc.queryForList(contains("WHERE trace_code_id = ?"), eq(10L)))
                .thenReturn(List.of(Map.of("labelId", 1L, "unitPrice", new BigDecimal("3"))));
        when(jdbc.queryForList(contains("bill.amount AS billedAmount"), eq(1L)))
                .thenReturn(List.of(Map.of("itemId", 41L, "quantity", new BigDecimal("20"), "packageQuantity", new BigDecimal("20"))));
        Map<String, Object> source = Map.of("productId", 1L, "traceCodeId", 10L,
                "sourceBizId", 41L, "quantity", new BigDecimal("20"), "unitPrice", new BigDecimal("2"), "amount", new BigDecimal("40"));
        var priced = service.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION, List.of(source));
        assertThat((BigDecimal) priced.get(0).get("amount")).isEqualByComparingTo("60");
        assertThat((BigDecimal) source.get("amount")).isEqualByComparingTo("40");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void zeroPackagePriceDoesNotFallBackToCost() {
        when(jdbc.queryForList(contains("WHERE trace_code_id = ?"), eq(10L)))
                .thenReturn(List.of(Map.of("labelId", 1L, "unitPrice", BigDecimal.ZERO)));
        when(jdbc.queryForList(contains("bill.amount AS billedAmount"), eq(1L)))
                .thenReturn(List.of(Map.of("itemId", 42L, "quantity", BigDecimal.TEN, "packageQuantity", BigDecimal.TEN)));
        var priced = service.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION,
                List.of(Map.of("productId", 1L, "traceCodeId", 10L, "sourceBizId", 42L, "quantity", BigDecimal.TEN,
                        "unitPrice", BigDecimal.ONE, "amount", BigDecimal.TEN)));
        assertThat((BigDecimal) priced.get(0).get("amount")).isEqualByComparingTo("0");
    }

    @Test
    void mixedSourcesAllocateRoundingOnceForTheWholePackage() {
        when(jdbc.queryForList(contains("WHERE trace_code_id = ?"), eq(10L)))
                .thenReturn(List.of(Map.of("labelId", 1L, "unitPrice", new BigDecimal("2.0101"))));
        when(jdbc.queryForList(contains("bill.amount AS billedAmount"), eq(1L)))
                .thenReturn(List.of(
                        Map.of("itemId", 41L, "quantity", new BigDecimal("0.5"), "packageQuantity", BigDecimal.ONE),
                        Map.of("itemId", 42L, "quantity", new BigDecimal("0.5"), "packageQuantity", BigDecimal.ONE)));
        var priced = service.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION, List.of(
                Map.of("productId", 1L, "traceCodeId", 10L, "sourceBizId", 42L, "quantity", new BigDecimal("0.5")),
                Map.of("productId", 1L, "traceCodeId", 10L, "sourceBizId", 41L, "quantity", new BigDecimal("0.5"))));
        assertThat((BigDecimal) priced.get(0).get("amount")).isEqualByComparingTo("1.0050");
        assertThat((BigDecimal) priced.get(1).get("amount")).isEqualByComparingTo("1.0051");
        assertThat(priced.stream().map(row -> (BigDecimal) row.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("2.0101");
    }

    @Test
    void partialSettlementKeepsBilledAmountAndAssignsOnlyRemainingBudget() {
        when(jdbc.queryForList(contains("WHERE trace_code_id = ?"), eq(10L)))
                .thenReturn(List.of(Map.of("labelId", 1L, "unitPrice", new BigDecimal("2.0101"))));
        when(jdbc.queryForList(contains("bill.amount AS billedAmount"), eq(1L)))
                .thenReturn(List.of(
                        Map.of("itemId", 41L, "quantity", new BigDecimal("0.5"), "packageQuantity", BigDecimal.ONE),
                        Map.of("itemId", 42L, "quantity", new BigDecimal("0.5"), "packageQuantity", BigDecimal.ONE,
                                "billedAmount", new BigDecimal("1.0051"))));
        var priced = service.settlementRows(SettlementPointService.DEPARTMENT_CONSUMPTION,
                List.of(Map.of("productId", 1L, "traceCodeId", 10L, "sourceBizId", 41L, "quantity", new BigDecimal("0.5"))));
        assertThat((BigDecimal) priced.get(0).get("amount")).isEqualByComparingTo("1.0050");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
