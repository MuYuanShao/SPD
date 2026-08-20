package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;

import com.hospital.spd.common.OperatorContext;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaPackageTraceFlowServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private QuotaPackageTraceFlowService service;

    @BeforeEach
    void setUp() {
        service = new QuotaPackageTraceFlowService(
                jdbcTemplate, support, () -> new OperatorContext(9L, "nurse01", "127.0.0.1", List.of("ROLE_DEPT_USER"), 10L, 2));
    }

    @Test
    void consumesSignedPackageByStableTraceIdentityAndCreatesSettlementEligibleItems() throws Exception {
        when(jdbcTemplate.queryForList(contains("qpl.label_no = ?"), eq("QP001"), eq("QP001"), eq("QP001")))
                .thenReturn(List.of(Map.of(
                        "labelId", 8L, "labelNo", "QP001", "status", "signed", "traceCodeId", 88L)));
        Map<String, Object> label = Map.ofEntries(
                Map.entry("labelId", 8L),
                Map.entry("labelNo", "QP001"),
                Map.entry("traceCodeId", 88L),
                Map.entry("status", "signed"),
                Map.entry("warehouseId", 20L),
                Map.entry("productId", 100L),
                Map.entry("packageQuantity", BigDecimal.TEN),
                Map.entry("templateCode", "TPL001"),
                Map.entry("templateName", "Surgery package"),
                Map.entry("unit", "piece"),
                Map.entry("productCode", "PC001"),
                Map.entry("productName", "Syringe"),
                Map.entry("warehouseName", "Department Warehouse"),
                Map.entry("deptId", 10L),
                Map.entry("deptName", "Surgery")
        );
        when(jdbcTemplate.queryForMap(contains("WHERE qpl.label_id = ?"), eq(8L))).thenReturn(label);
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label_source"), eq(8L)))
                .thenReturn(List.of(Map.of("batchId", 300L, "sourceQty", BigDecimal.TEN)));
        when(support.nextNo(DocumentKind.DEPARTMENT_CONSUMPTION)).thenReturn("XH001");
        when(support.nextNo(DocumentKind.UDI_TRACE_EVENT)).thenReturn("UT001");
        mockGeneratedKey(77L);
        when(support.consumeSpecificBatch(eq(20L), eq(100L), eq(300L), eq(BigDecimal.TEN),
                eq("quota_package_scan_out"), eq("department_consumption"), eq(77L), any(String.class)))
                .thenReturn(new SupplyChainSupport.InventoryDeduction(300L, BigDecimal.TEN, BigDecimal.valueOf(2)));
        lenient().doReturn(1).when(jdbcTemplate)
                .update(contains("UPDATE quota_package_label SET status = 'consumed'"), eq(8L));

        Map<String, Object> result = service.consumeByCode(Map.of("code", "QP001"));

        assertThat(result)
                .containsEntry("consumptionNo", "XH001")
                .containsEntry("status", "consumed")
                .containsEntry("amount", BigDecimal.valueOf(20))
                .containsEntry("settlementEligible", true);
        verify(jdbcTemplate).update(contains("INSERT INTO department_consumption_item"),
                eq(77L), eq(100L), eq(300L), eq(88L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(2)),
                eq(BigDecimal.valueOf(20)));
        verify(jdbcTemplate).update(contains("UPDATE udi_trace_code"),
                eq("consumed"), eq("consumed"), eq("Department Warehouse"), eq("Surgery"),
                eq("nurse01"), eq("定数包扫码消耗"), eq(88L));
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
