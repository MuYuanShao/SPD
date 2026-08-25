package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.StocktakingRequest;
import com.hospital.spd.supplychain.StocktakingSheetRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 单元测试")
class InventoryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(jdbcTemplate, support);
    }

    // ==================== 库存余额查询 ====================

    @Test
    @DisplayName("选择盘点范围后预览当前库房库存明细")
    void shouldPreviewCurrentWarehouseStockForSelectedScopes() {
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), eq("一级库")))
                .thenReturn(10L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "productId", 1L, "productCode", "P001", "productName", "注射器",
                "systemQty", BigDecimal.TEN)));

        Map<String, Object> result = service.previewStocktakingSheet(
                new StocktakingSheetRequest("一级库", "手术室", List.of("highValue", "quotaPackage")));

        assertThat((List<?>) result.get("rows")).hasSize(1);
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql.contains("p.is_high_value = 1")
                && sql.contains("p.is_quota_managed = 1") && sql.contains("bal.location_id IS NULL")),
                any(Object[].class));
    }

    @Nested
    @DisplayName("balances() 库存余额查询")
    class BalancesTest {

        @Test
        @DisplayName("无筛选条件时返回全部库存汇总")
        void shouldReturnAllBalances() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            Map<String, Object> summaryMap = Map.of(
                    "batchCount", 50L, "availableQty", BigDecimal.valueOf(10000),
                    "lockedQty", BigDecimal.ZERO, "isolatedQty", BigDecimal.ZERO
            );
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(50L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("productCode", "P001", "qty", BigDecimal.valueOf(200))
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(summaryMap);

            Map<String, Object> result = service.balances(params);

            assertThat(result.get("total")).isEqualTo(50L);
            assertThat(result.get("rows")).asList().hasSize(1);
            assertThat(result.get("summary")).isNotNull();
            verify(jdbcTemplate).queryForList(
                    argThat((String sql) -> sql.contains("location_id IS NULL")
                            && sql.contains("SUM(available_qty)")
                            && sql.contains("quota_package_label")
                            && sql.contains("package_quantity")
                            && sql.contains("lo.loose_qty + COALESCE(qp.packaged_qty, 0)")),
                    eq(20), eq(0));
            verify(jdbcTemplate).queryForMap(
                    argThat((String sql) -> sql.contains("available_qty <> 0")
                            && sql.contains("locked_qty <> 0")
                            && sql.contains("in_transit_qty <> 0")
                            && sql.contains("isolated_qty <> 0")));
        }

        @Test
        @DisplayName("按仓库名称筛选时返回过滤结果")
        void shouldReturnFilteredByWarehouse() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "warehouseName", "主仓库");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("balanceId", 2L, "productCode", "P002", "availableQty", BigDecimal.valueOf(500))
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("batchCount", 10L, "availableQty", BigDecimal.valueOf(500),
                            "lockedQty", BigDecimal.ZERO, "isolatedQty", BigDecimal.ZERO));

            Map<String, Object> result = service.balances(params);

            assertThat(result.get("total")).isEqualTo(10L);
        }

        @Test
        @DisplayName("无匹配数据时返回空列表")
        void shouldReturnEmptyWhenNoResults() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("batchCount", 0L, "availableQty", BigDecimal.ZERO,
                            "lockedQty", BigDecimal.ZERO, "isolatedQty", BigDecimal.ZERO));

            Map<String, Object> result = service.balances(params);

            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("rows")).asList().isEmpty();
        }
    }

    // ==================== 库存事件追溯 ====================

    @Nested
    @DisplayName("events() 库存事件追溯")
    class EventsTest {

        @Test
        @DisplayName("成功返回库存事件列表")
        void shouldReturnEventList() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(100L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("eventNo", "KC2026060100001", "eventType", "purchase_receive_in",
                            "qtyChange", BigDecimal.TEN)
            ));

            Map<String, Object> result = service.events(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(100L);
        }

        @Test
        @DisplayName("按事件类型筛选时返回过滤结果")
        void shouldReturnFilteredByEventType() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "eventType", "purchase_receive_in");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("eventNo", "KC2026060100002", "eventType", "purchase_receive_in")
            ));

            Map<String, Object> result = service.events(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(5L);
        }

        @Test
        @DisplayName("按交易类型筛选时生成交易类型映射条件")
        void shouldReturnFilteredByTransactionType() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "transactionType", "验收入库");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("eventNo", "KC2026060100003", "transactionType", "验收入库")
            ));

            Map<String, Object> result = service.events(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(3L);
            verify(jdbcTemplate).queryForObject(
                    argThat(sql -> sql.contains("'验收入库'")
                            && sql.contains("quota_pack_out")
                            && sql.contains("二级库入库")
                            && sql.contains("三级库出库")),
                    eq(Long.class), any(Object[].class));
        }
    }

    // ==================== 批次查询 ====================

    @Nested
    @DisplayName("batches() 批次查询")
    class BatchesTest {

        @Test
        @DisplayName("成功返回批次列表")
        void shouldReturnBatchList() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(30L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("batchId", 1L, "systemBatchNo", "PC2026060100001",
                            "productCode", "P001")
            ));

            Map<String, Object> result = service.batches(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(30L);
        }

        @Test
        @DisplayName("无匹配批次时返回空列表")
        void shouldReturnEmptyWhenNoResults() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

            Map<String, Object> result = service.batches(params);

            assertThat((List<?>) result.get("rows")).isEmpty();
        }
    }

    // ==================== 创建盘点 ====================

    @Nested
    @DisplayName("createStocktaking() 创建盘点")
    class CreateStocktakingTest {

        @Test
        @DisplayName("成功创建盘点单")
        void shouldCreateStocktakingSuccessfully() {
            StocktakingRequest request = new StocktakingRequest(
                    "主仓库", "PC2026060100001", BigDecimal.valueOf(150), "动态盘点"
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString(), anyString())).thenReturn(
                    Map.of("balanceId", 1L, "warehouseId", 10L,
                            "productId", 20L, "batchId", 30L,
                            "availableQty", BigDecimal.valueOf(100))
            );
            when(support.nextNo(any(DocumentKind.class))).thenReturn("PD20260601001");
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 200L);
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createStocktaking(request);

            assertThat(result).containsKey("stocktakingNo");
            assertThat(result.get("stocktakingNo")).isEqualTo("PD20260601001");
            assertThat(result.get("diffQty")).isEqualTo(BigDecimal.valueOf(50));
        }

        @Test
        @DisplayName("实际数量少于系统数量时盘亏")
        void shouldCreateStocktakingWithNegativeDiff() {
            StocktakingRequest request = new StocktakingRequest(
                    "主仓库", "PC2026060100001", BigDecimal.valueOf(80), "亏损盘点"
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString(), anyString())).thenReturn(
                    Map.of("balanceId", 1L, "warehouseId", 10L,
                            "productId", 20L, "batchId", 30L,
                            "availableQty", BigDecimal.valueOf(100))
            );
            when(support.nextNo(any(DocumentKind.class))).thenReturn("PD20260601002");
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 201L);
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createStocktaking(request);

            assertThat(result.get("diffQty")).isEqualTo(BigDecimal.valueOf(-20));
        }

        @Test
        @DisplayName("库存余额不存在时抛出异常")
        void shouldThrowWhenBalanceNotFound() {
            StocktakingRequest request = new StocktakingRequest(
                    "不存在仓库", "NONEXISTENT", BigDecimal.TEN, null
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString(), anyString()))
                    .thenThrow(new EmptyResultDataAccessException(1));

            assertThatThrownBy(() -> service.createStocktaking(request))
                    .isInstanceOf(EmptyResultDataAccessException.class);
        }
    }

    // ==================== 审批盘点 ====================

    @Nested
    @DisplayName("approveStocktaking() 审批盘点")
    class ApproveStocktakingTest {

        @Test
        @DisplayName("成功审批盘点单（盘盈）")
        void shouldApproveStocktakingWithProfit() {
            String stocktakingNo = "PD20260601001";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("stocktakingId", 200L, "warehouseId", 10L, "status", "draft"));
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("itemId", 1L, "balanceId", 1L, "productId", 20L,
                                    "batchId", 30L, "diffQty", BigDecimal.valueOf(50),
                                    "actualQty", BigDecimal.valueOf(150))
                    ));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.approveStocktaking(stocktakingNo);

            assertThat(result.get("status")).isEqualTo("approved");
            verify(support).adjustAvailable(1L, 10L, 20L, 30L, BigDecimal.valueOf(50),
                    "stocktaking_profit", "inventory_stocktaking", 200L,
                    "stocktaking approval generated inventory adjustment");
        }

        @Test
        @DisplayName("成功审批盘点单（盘亏，库存充足）")
        void shouldApproveStocktakingWithLoss() {
            String stocktakingNo = "PD20260601002";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("stocktakingId", 201L, "warehouseId", 10L, "status", "draft"));
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("itemId", 2L, "balanceId", 2L, "productId", 21L,
                                    "batchId", 31L, "diffQty", BigDecimal.valueOf(-30),
                                    "actualQty", BigDecimal.valueOf(70))
                    ));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.approveStocktaking(stocktakingNo);

            assertThat(result.get("status")).isEqualTo("approved");
            verify(support).adjustAvailable(2L, 10L, 21L, 31L, BigDecimal.valueOf(-30),
                    "stocktaking_loss", "inventory_stocktaking", 201L,
                    "stocktaking approval generated inventory adjustment");
        }

        @Test
        @DisplayName("盘亏会导致负库存时禁止审核")
        void shouldThrowWhenLossCausesNegativeStock() {
            String stocktakingNo = "PD20260601003";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("stocktakingId", 202L, "warehouseId", 10L, "status", "draft"));
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("itemId", 3L, "balanceId", 3L, "productId", 22L,
                                    "batchId", 32L, "diffQty", BigDecimal.valueOf(-200),
                                    "actualQty", BigDecimal.ZERO)
                    ));
            when(support.adjustAvailable(eq(3L), eq(10L), eq(22L), eq(32L), eq(BigDecimal.valueOf(-200)),
                    eq("stocktaking_loss"), eq("inventory_stocktaking"), eq(202L), anyString()))
                    .thenThrow(new IllegalArgumentException("盘亏会形成负库存"));

            assertThatThrownBy(() -> service.approveStocktaking(stocktakingNo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("盘亏会形成负库存");
        }

        @Test
        @DisplayName("已审批的盘点单不能重复审批")
        void shouldThrowWhenAlreadyApproved() {
            String stocktakingNo = "PD20260601004";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("stocktakingId", 203L, "warehouseId", 10L, "status", "approved"));

            assertThatThrownBy(() -> service.approveStocktaking(stocktakingNo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stocktaking document has already been processed");
        }

        @Test
        @DisplayName("盘点单不存在时抛出异常")
        void shouldThrowWhenStocktakingNotFound() {
            String stocktakingNo = "NONEXISTENT";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenThrow(new EmptyResultDataAccessException(1));

            assertThatThrownBy(() -> service.approveStocktaking(stocktakingNo))
                    .isInstanceOf(EmptyResultDataAccessException.class);
        }
    }
}
