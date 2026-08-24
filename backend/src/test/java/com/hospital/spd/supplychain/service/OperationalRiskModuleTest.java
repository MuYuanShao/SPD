package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalRiskModuleTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SupplyChainSupport support;
    private OperationalRiskModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalRiskModule(jdbcTemplate, support);
    }

    @Test
    void createsRecallAndMovesAvailableInventoryToIsolation() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "支架"));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id"), eq(Long.class), eq("主仓库")))
                .thenReturn(10L);
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH20260710001");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 88L);

        Map<String, Object> result = module.createRecall(Map.of(
                "warehouseName", "主仓库", "productCode", "PC001",
                "quantity", BigDecimal.valueOf(2), "reason", "质量异常"));

        assertThat(result).containsEntry("status", "isolated");
        verify(support).isolateAvailableFifo(10L, 100L, BigDecimal.valueOf(2),
                "recall_event", 88L, "质量异常");
    }

    @Test
    void createsRecallFromSelectedBatchAndIsolatesThatBatch() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "支架"));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id"), eq(Long.class), eq("主仓库")))
                .thenReturn(10L);
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH20260710002");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 89L);

        Map<String, Object> result = module.createRecall(Map.of(
                "warehouseName", "主仓库", "productCode", "PC001",
                "batchId", 66L, "quantity", BigDecimal.valueOf(5), "reason", "批次召回"));

        assertThat(result).containsEntry("status", "isolated");
        verify(support).isolateSpecificBatch(10L, 100L, 66L, BigDecimal.valueOf(5),
                "recall_event", 89L, "批次召回");
    }

    @Test
    void listsAvailableBatchesForRecall() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001"));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id"), eq(Long.class), eq("主仓库")))
                .thenReturn(10L);
        when(jdbcTemplate.queryForList(contains("FROM inventory_balance bal"), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of("batchId", 66L, "systemBatchNo", "PC2026071001", "availableQty", BigDecimal.valueOf(5))));

        Map<String, Object> result = module.recallBatches("PC001", "主仓库");

        assertThat(result).containsKey("rows");
        assertThat(((java.util.List<?>) result.get("rows"))).hasSize(1);
    }
}
