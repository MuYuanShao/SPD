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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementPointServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private SettlementPointService service;

    @BeforeEach
    void setUp() {
        service = new SettlementPointService(jdbcTemplate, support);
    }

    @Test
    void generatesPurchaseInSettlementWhenReceivingPointIsReached() throws Exception {
        when(jdbcTemplate.queryForList(contains("ib.settlement_mode = 'purchase_in'"), eq(31L)))
                .thenReturn(List.of(source("receiving_order_item", 41L, null)));
        when(support.nextNo(DocumentKind.SETTLEMENT_BILL)).thenReturn("JS2026072400010");
        mockGeneratedKey(51L);

        List<String> settlementNos = service.generateForReceivingOrder(31L);

        assertThat(settlementNos).containsExactly("JS2026072400010");
        verify(jdbcTemplate).update(contains("INSERT INTO settlement_bill_item"), eq(51L),
                eq("receiving_order_item"), eq(41L), eq(101L), eq(201L),
                org.mockito.ArgumentMatchers.isNull(), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(2)),
                eq(BigDecimal.valueOf(20)));
        verify(support).writeAudit("settlement_bill", "auto_generate", 51L,
                "JS2026072400010", "settlement generated automatically at purchase_in");
    }

    @Test
    void generatesConsumptionSettlementWithStableTraceIdentity() throws Exception {
        when(jdbcTemplate.queryForList(contains("ib.settlement_mode = 'department_consumption'"), eq(61L)))
                .thenReturn(List.of(source("department_consumption_item", 71L, 301L)));
        when(support.nextNo(DocumentKind.SETTLEMENT_BILL)).thenReturn("JS2026072400011");
        mockGeneratedKey(52L);

        List<String> settlementNos = service.generateForConsumption(61L);

        assertThat(settlementNos).containsExactly("JS2026072400011");
        verify(jdbcTemplate).update(contains("INSERT INTO settlement_bill_item"), eq(52L),
                eq("department_consumption_item"), eq(71L), eq(101L), eq(201L),
                eq(301L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(2)), eq(BigDecimal.valueOf(20)));
    }

    @Test
    void repeatedOrWrongPointProducesNoDuplicateSettlement() {
        when(jdbcTemplate.queryForList(contains("ib.settlement_mode = 'actual_sale'"), eq(81L)))
                .thenReturn(List.of());

        assertThat(service.generateForHighValueCharge(81L)).isEmpty();
    }

    private Map<String, Object> source(String sourceBizType, Long sourceBizId, Long traceCodeId) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("supplierId", 5L);
        row.put("settlementPeriod", "2026-07");
        row.put("sourceBizType", sourceBizType);
        row.put("sourceBizId", sourceBizId);
        row.put("productId", 101L);
        row.put("batchId", 201L);
        row.put("traceCodeId", traceCodeId);
        row.put("quantity", BigDecimal.TEN);
        row.put("unitPrice", BigDecimal.valueOf(2));
        row.put("amount", BigDecimal.valueOf(20));
        return row;
    }

    private void mockGeneratedKey(Long key) throws Exception {
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
            keyListField.setAccessible(true);
            keyListField.set(keyHolder, List.of(Map.of("GENERATED_KEY", key)));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }
}
