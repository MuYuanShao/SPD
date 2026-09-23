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
import static org.mockito.Mockito.never;
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
    void isolationKeepsStockInOriginalWarehouse() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "耗材"));
        when(jdbcTemplate.queryForList(contains("converted_stock"), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of("warehouseId", 20L, "warehouseName", "二级库", "batchId", 66L,
                        "looseQty", BigDecimal.valueOf(5), "packageQty", BigDecimal.ZERO, "totalQty", BigDecimal.valueOf(5))));
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH-ISOLATE");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 99L);

        module.createRecall(Map.of("businessType", "isolate", "scope", "secondary", "warehouseName", "二级库",
                "productCode", "PC001", "batchNo", "B001", "reason", "质量异常"));

        verify(support).isolateSpecificBatch(20L, 100L, 66L, BigDecimal.valueOf(5), "recall_event", 99L, "质量异常");
        verify(support, never()).transferSpecificBatch(any(), any(), any(), any(), any(), anyString(), any(), anyString());
        verify(jdbcTemplate, never()).queryForMap(contains("warehouse_type LIKE '%一级%'"));
    }

    @Test
    void recallsSecondaryInventoryToPrimaryWarehouseAndIsolatesIt() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "支架"));
        when(jdbcTemplate.queryForList(contains("converted_stock"), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of(
                        "warehouseId", 20L, "batchId", 66L,
                        "looseQty", BigDecimal.valueOf(2), "packageQty", BigDecimal.ZERO,
                        "totalQty", BigDecimal.valueOf(2))));
        when(jdbcTemplate.queryForMap(contains("warehouse_type LIKE '%一级%'")))
                .thenReturn(Map.of("warehouseId", 10L, "warehouseName", "一级库"));
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH20260710001");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 88L);

        Map<String, Object> result = module.createRecall(Map.of(
                "scope", "secondary", "warehouseName", "二级库", "productCode", "PC001",
                "batchNo", "PC2026071001", "reason", "质量异常"));

        assertThat(result).containsEntry("status", "isolated");
        verify(support).transferSpecificBatch(20L, 10L, 100L, 66L, BigDecimal.valueOf(2),
                "recall_event", 88L, "召回库存回收到一级库：质量异常");
        verify(support).isolateSpecificBatch(10L, 100L, 66L, BigDecimal.valueOf(2),
                "recall_event", 88L, "质量异常");
    }

    @Test
    void isolatesPrimaryWarehouseRecallWithoutTransfer() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "支架"));
        when(jdbcTemplate.queryForList(contains("converted_stock"), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of(
                        "warehouseId", 10L, "batchId", 66L,
                        "looseQty", BigDecimal.valueOf(5), "packageQty", BigDecimal.ZERO,
                        "totalQty", BigDecimal.valueOf(5))));
        when(jdbcTemplate.queryForMap(contains("warehouse_type LIKE '%一级%'")))
                .thenReturn(Map.of("warehouseId", 10L, "warehouseName", "主仓库"));
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH20260710002");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 89L);

        Map<String, Object> result = module.createRecall(Map.of(
                "scope", "primary", "warehouseName", "主仓库", "productCode", "PC001",
                "batchNo", "PC2026071001", "reason", "批次召回"));

        assertThat(result).containsEntry("status", "isolated");
        verify(support).isolateSpecificBatch(10L, 100L, 66L, BigDecimal.valueOf(5),
                "recall_event", 89L, "批次召回");
        verify(support, never()).transferSpecificBatch(any(), any(), any(), any(), any(), anyString(), any(), anyString());
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

    @Test
    void unpacksQuotaPackageBeforePrimaryWarehouseIsolation() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "耗材"));
        when(jdbcTemplate.queryForList(contains("converted_stock"), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of(
                        "warehouseId", 10L, "batchId", 66L,
                        "looseQty", BigDecimal.ZERO, "packageQty", BigDecimal.valueOf(5),
                        "totalQty", BigDecimal.valueOf(5))));
        when(jdbcTemplate.queryForMap(contains("warehouse_type LIKE '%一级%'")))
                .thenReturn(Map.of("warehouseId", 10L, "warehouseName", "一级库"));
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label qpl"), eq(10L), eq(100L), eq(66L)))
                .thenReturn(java.util.List.of(Map.of("labelId", 7L, "labelNo", "D001", "status", "available")));
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label_source WHERE label_id"), eq(7L)))
                .thenReturn(java.util.List.of(Map.of("batchId", 66L, "sourceQty", BigDecimal.valueOf(5), "warehouseId", 10L)));
        when(support.nextNo(DocumentKind.RECALL_EVENT)).thenReturn("ZH003");
        when(support.nextNo(DocumentKind.QUOTA_PACKAGE_EVENT)).thenReturn("DS003");
        PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 90L);

        module.createRecall(Map.of("scope", "all", "productCode", "PC001",
                "batchNo", "PC2026071001", "reason", "批次召回"));

        verify(support).receiveAvailable(10L, 100L, 66L, BigDecimal.valueOf(5),
                "quota_unpack_in", "recall_event", 90L, "召回自动解包：批次召回");
        verify(jdbcTemplate).update(contains("recall_unpack_to_loose"), eq("DS003"), eq(7L), eq("available"),
                eq(BigDecimal.valueOf(5)), eq("召回自动解包：批次召回"));
    }
}
