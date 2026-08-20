package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReceivingOrderService 单元测试")
class ReceivingOrderServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private ReceivingOrderService service;

    @BeforeEach
    void setUp() {
        service = new ReceivingOrderService(jdbcTemplate, support);
    }

    // ==================== 收货单列表 ====================

    @Nested
    @DisplayName("list() 收货单分页查询")
    class ListTest {

        @Test
        @DisplayName("无筛选条件时返回全部收货单")
        void shouldReturnAllWithoutFilters() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            Map<String, Object> summaryMap = Map.of(
                    "totalReceipts", 30L, "draftCount", 5L, "approvedCount", 25L
            );
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(30L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("receivingNo", "RK20260601001", "supplierName", "测试供应商")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(summaryMap);

            Map<String, Object> result = service.list(params);

            assertThat(result.get("total")).isEqualTo(30L);
            assertThat(result.get("rows")).asList().hasSize(1);
            assertThat(result.get("summary")).isNotNull();
        }

        @Test
        @DisplayName("按条件筛选时返回过滤结果")
        void shouldReturnFilteredByReceivingNo() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "receivingNo", "RK2026");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("receivingNo", "RK20260601002", "supplierName", "测试供应商")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalReceipts", 2L, "draftCount", 1L, "approvedCount", 1L));

            Map<String, Object> result = service.list(params);

            assertThat(result.get("total")).isEqualTo(2L);
        }

        @Test
        @DisplayName("待收货分组只查询草稿单据")
        void shouldFilterPendingReceivingOrders() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(Map.of());

            service.list(Map.of("page", "1", "size", "20", "statusGroup", "pending"));

            verify(jdbcTemplate).queryForObject(contains("ro.receiving_status = 'draft'"), eq(Long.class), any(Object[].class));
        }

        @Test
        @DisplayName("已验收分组查询已入库和已拒收单据")
        void shouldFilterCompletedReceivingOrders() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(Map.of());

            service.list(Map.of("page", "1", "size", "20", "statusGroup", "completed"));

            verify(jdbcTemplate).queryForObject(contains("ro.receiving_status IN ('approved', 'rejected')"), eq(Long.class), any(Object[].class));
        }

        @Test
        @DisplayName("未知收货状态分组返回业务错误")
        void shouldRejectUnknownStatusGroup() {
            assertThatThrownBy(() -> service.list(Map.of("statusGroup", "unknown")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("收货状态分组不正确");
        }

        @Test
        @DisplayName("无匹配数据时返回空列表")
        void shouldReturnEmptyWhenNoResults() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalReceipts", 0L, "draftCount", 0L, "approvedCount", 0L));

            Map<String, Object> result = service.list(params);

            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("rows")).asList().isEmpty();
        }
    }

    // ==================== 收货单详情 ====================

    @Nested
    @DisplayName("detail() 收货单详情")
    class DetailTest {

        @Test
        @DisplayName("成功返回收货单详情和明细行")
        void shouldReturnDetailWithItems() {
            String receivingNo = "RK20260601001";
            Map<String, Object> orderMap = Map.of(
                    "receivingOrderId", 100L, "receivingNo", receivingNo,
                    "receivingStatus", "draft", "supplierName", "测试供应商"
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(orderMap);
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(100L))).thenReturn(1L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("productCode", "P001", "productName", "测试产品", "quantity", BigDecimal.TEN)
            ));

            Map<String, Object> result = service.detail(receivingNo);

            assertThat(result).containsKeys("order", "items", "total", "page", "size");
            assertThat(result.get("items")).asList().hasSize(1);
        }

        @Test
        @DisplayName("收货单不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenThrow(new EmptyResultDataAccessException(1));

            assertThatThrownBy(() -> service.detail("NONEXISTENT"))
                    .isInstanceOf(EmptyResultDataAccessException.class);
        }

        @Test
        @DisplayName("按分页参数返回明细")
        void shouldReturnPagedItems() {
            String receivingNo = "RK20260601001";
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "receivingNo", receivingNo,
                            "receivingStatus", "approved", "supplierName", "测试供应商")
            );
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(100L))).thenReturn(100L);
            when(jdbcTemplate.queryForList(anyString(), eq(100L), eq(20), eq(20))).thenReturn(List.of(
                    Map.of("productCode", "P021", "productName", "测试产品21")
            ));

            Map<String, Object> result = service.detail(receivingNo, Map.of("page", "2", "size", "20"));

            assertThat(result.get("total")).isEqualTo(100L);
            assertThat(result.get("page")).isEqualTo(2);
            assertThat(result.get("size")).isEqualTo(20);
            assertThat(result.get("items")).asList().hasSize(1);
        }
    }

    // ==================== 创建收货单 ====================

    @Nested
    @DisplayName("create() 创建收货单")
    class CreateTest {

        private ReceivingOrderRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new ReceivingOrderRequest(
                    "CG20260601001",
                    "测试供应商",
                    "主仓库",
                    "加急收货",
                    List.of(new ReceivingItemRequest("P001", "BATCH001",
                            "2026-06-01", "2028-06-01",
                            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO))
            );
            // findPurchaseOrderId
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any()))
                    .thenReturn(List.of(50L));
            when(jdbcTemplate.queryForList(contains("FROM purchase_order po"), eq(50L), eq("P001")))
                    .thenReturn(List.of(Map.of("orderStatus", "sent", "remainingQuantity", BigDecimal.TEN)));
            // findSupplierId with purchaseOrderId -> queryForObject
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            // findWarehouseId
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                    .thenReturn(10L);
            // nextNo
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("RK20260601001");
            // KeyHolder for receiving_order insert
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 100L);
            // insertReceivingItems -> findProduct
            when(jdbcTemplate.queryForList(anyString(), anyString()))
                    .thenReturn(List.of(Map.of("productId", 20L, "purchasePrice", BigDecimal.valueOf(200))));
            // all regular updates
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("合法参数时成功创建收货单")
        void shouldCreateSuccessfully() {
            Map<String, Object> result = service.create(validRequest);

            assertThat(result).containsKey("receivingNo");
            assertThat(result.get("receivingNo")).isEqualTo("RK20260601001");
        }

    }

    @Nested
    @DisplayName("update() 修改待验收收货单")
    class UpdateTest {

        @Test
        @DisplayName("draft 状态允许修改并重建明细")
        void shouldUpdateDraftReceivingOrder() {
            ReceivingOrderRequest request = new ReceivingOrderRequest(
                    null, "测试供应商", "SPD中心库", "二次修改",
                    List.of(new ReceivingItemRequest("P001", "BATCH002",
                            "2026-06-02", "2028-06-02",
                            BigDecimal.valueOf(12), BigDecimal.valueOf(12), BigDecimal.ZERO))
            );
            when(jdbcTemplate.queryForMap(anyString(), eq("RK001")))
                    .thenReturn(Map.of("receivingOrderId", 100L, "receivingStatus", "draft"));
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any()))
                    .thenReturn(List.of(1L), List.of(10L));
            when(jdbcTemplate.queryForList(anyString(), anyString()))
                    .thenReturn(List.of(Map.of("productId", 20L, "purchasePrice", BigDecimal.valueOf(200))));
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.update("RK001", request);

            assertThat(result.get("status")).isEqualTo("draft");
            verify(jdbcTemplate).update(eq("DELETE FROM receiving_order_item WHERE receiving_order_id = ?"), eq(100L));
        }

        @Test
        @DisplayName("非 draft 状态不允许修改")
        void shouldRejectUpdateWhenNotDraft() {
            ReceivingOrderRequest request = new ReceivingOrderRequest(
                    null, "测试供应商", "SPD中心库", null,
                    List.of(new ReceivingItemRequest("P001", null, null, null,
                            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO))
            );
            when(jdbcTemplate.queryForMap(anyString(), eq("RK001")))
                    .thenReturn(Map.of("receivingOrderId", 100L, "receivingStatus", "approved"));

            assertThatThrownBy(() -> service.update("RK001", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("当前收货单状态不允许执行该操作");
        }
    }

    // ==================== 创建收货单 - 无采购订单 ====================

    @Nested
    @DisplayName("create() 创建收货单 - 无采购订单")
    class CreateWithoutPoTest {

        @Test
        @DisplayName("无采购订单号时也能创建")
        void shouldCreateWithoutPurchaseOrderNo() {
            ReceivingOrderRequest noPoRequest = new ReceivingOrderRequest(
                    null, "测试供应商", "主仓库", null,
                    List.of(new ReceivingItemRequest("P001", null, null, null,
                            BigDecimal.TEN, null, null))
            );
            // findSupplierId then findWarehouseId
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any()))
                    .thenReturn(List.of(1L), List.of(10L));
            // findProduct for insertItems
            when(jdbcTemplate.queryForList(anyString(), anyString()))
                    .thenReturn(List.of(Map.of("productId", 20L, "purchasePrice", BigDecimal.valueOf(200))));
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("RK20260601002");
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 101L);

            Map<String, Object> result = service.create(noPoRequest);

            assertThat(result).containsKey("receivingNo");
        }
    }

    // ==================== 创建收货单 - 参数验证 ====================    // ==================== 创建收货单 - 参数验证 ====================

    @Nested
    @DisplayName("create() 创建收货单 - 参数验证")
    class CreateValidationTest {

        @Test
        @DisplayName("明细为空时抛出异常")
        void shouldThrowWhenItemsEmpty() {
            ReceivingOrderRequest empty = new ReceivingOrderRequest(
                    "CG20260601001", "测试供应商", "主仓库", null, List.of()
            );
            assertThatThrownBy(() -> service.create(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("receiving order requires at least one item");
        }

        @Test
        @DisplayName("明细商品编码为空时抛出异常")
        void shouldThrowWhenProductCodeBlank() {
            ReceivingOrderRequest invalid = new ReceivingOrderRequest(
                    "CG20260601001", "测试供应商", "主仓库", null,
                    List.of(new ReceivingItemRequest("", null, null, null,
                            BigDecimal.TEN, null, null))
            );
            assertThatThrownBy(() -> service.create(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("product code and receiving quantity are required");
        }

        @Test
        @DisplayName("仓库名为空时抛出异常")
        void shouldThrowWhenWarehouseBlank() {
            ReceivingOrderRequest invalid = new ReceivingOrderRequest(
                    "CG20260601001", "测试供应商", "", null,
                    List.of(new ReceivingItemRequest("P001", null, null, null,
                            BigDecimal.TEN, null, null))
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(50L));

            assertThatThrownBy(() -> service.create(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("warehouse is required");
        }

        @Test
        @DisplayName("合格数与不合格数之和必须等于收货数")
        void shouldRejectInconsistentAcceptedQuantities() {
            ReceivingOrderRequest invalid = new ReceivingOrderRequest(
                    "CG20260601001", "测试供应商", "主仓库", null,
                    List.of(new ReceivingItemRequest("P001", null, null, null,
                            BigDecimal.TEN, BigDecimal.valueOf(9), BigDecimal.valueOf(2)))
            );

            assertThatThrownBy(() -> service.create(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("qualified and unqualified quantities must equal receiving quantity");
        }

        @Test
        @DisplayName("关联采购单时合格收货数不能超过未收数量")
        void shouldRejectReceiptBeyondPurchaseOrderRemainingQuantity() {
            ReceivingOrderRequest invalid = new ReceivingOrderRequest(
                    "CG20260601001", "测试供应商", "主仓库", null,
                    List.of(new ReceivingItemRequest("P001", null, null, null,
                            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO))
            );
            when(jdbcTemplate.queryForList(contains("SELECT purchase_order_id"), eq(Long.class), eq("CG20260601001")))
                    .thenReturn(List.of(50L));
            when(jdbcTemplate.queryForList(contains("FROM purchase_order po"), eq(50L), eq("P001")))
                    .thenReturn(List.of(Map.of("orderStatus", "sent", "remainingQuantity", BigDecimal.valueOf(5))));

            assertThatThrownBy(() -> service.create(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds purchase order remaining quantity");
        }
    }

    // ==================== 收货验收动作 ====================

    @Nested
    @DisplayName("action() 收货验收动作")
    class ActionTest {

        @Test
        @DisplayName("approve：草稿 -> 已审批")
        void shouldApproveDraft() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "draft"));
            // approveReceiving: query items
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("itemId", 1L, "productId", 20L,
                                    "productionBatchNo", "B001",
                                    "productionDate", Date.valueOf(LocalDate.of(2026, 6, 1)),
                                    "expireDate", Date.valueOf(LocalDate.of(2028, 6, 1)),
                                    "quantity", BigDecimal.TEN, "qualifiedQuantity", BigDecimal.TEN,
                                    "latestCatalogPrice", BigDecimal.valueOf(200),
                                    "previewUnitPrice", BigDecimal.valueOf(200),
                                    "unitPrice", BigDecimal.valueOf(200))
                    ));
            // update receiving_order_item unit_price
            doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), anyLong());
            // createInventoryBatch -> KeyHolder
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 300L);
            // support.nextNo for system_batch_no
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("PC2026060100001");
            // applyInventoryBalance -> queryForObject for qtyAfter
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(BigDecimal.TEN);
            // applyInventoryBalance insert
            doReturn(1).when(jdbcTemplate).update(anyString(), anyLong(), anyLong(), anyLong(), any());
            // applyInventoryBalance -> KeyHolder for inventory_event
            // Will reuse the same mockKeyHolderInsert stub
            // updatePurchaseReceivedQty
            doReturn(1).when(jdbcTemplate).update(anyString(), any(), anyLong(), anyLong());
            // update inventory_balance last_event_id
            doReturn(1).when(jdbcTemplate).update(anyString(), anyLong(), anyLong(), anyLong(), anyLong());
            // update receiving_order status
            doReturn(1).when(jdbcTemplate).update(anyString(), anyString(), anyString());
            // writeAudit
            doReturn(1).when(jdbcTemplate).update(anyString(), anyString(), anyLong(), anyString(), anyString());

            Map<String, Object> result = service.action("RK001",
                    new ReceivingActionRequest("approve", "验收合格"));

            assertThat(result.get("status")).isEqualTo("approved");
            verify(support).nextNo(eq(DocumentKind.INVENTORY_BATCH));
            verify(support, never()).nextNo(eq(DocumentKind.INVENTORY_EVENT));
        }

        @Test
        @DisplayName("approve：高值耗材按合格数量逐件生成唯一码")
        void shouldGenerateOneTraceCodePerQualifiedHighValueUnit() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "draft"));
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(Map.ofEntries(
                            Map.entry("itemId", 1L), Map.entry("productId", 20L),
                            Map.entry("productCode", "A"), Map.entry("productName", "高值测试耗材"),
                            Map.entry("specModel", "1根/支"), Map.entry("highValue", 1),
                            Map.entry("manufacturerName", "测试厂家"), Map.entry("supplierName", "测试供应商"),
                            Map.entry("productionBatchNo", "B001"),
                            Map.entry("productionDate", Date.valueOf(LocalDate.of(2026, 7, 23))),
                            Map.entry("expireDate", Date.valueOf(LocalDate.of(2028, 7, 23))),
                            Map.entry("quantity", BigDecimal.TEN), Map.entry("qualifiedQuantity", BigDecimal.TEN),
                            Map.entry("latestCatalogPrice", BigDecimal.valueOf(200)),
                            Map.entry("previewUnitPrice", BigDecimal.valueOf(200)))));
            PurchaseOrderServiceTest.mockKeyHolderInsert(jdbcTemplate, 300L);
            java.util.concurrent.atomic.AtomicInteger uniqueSequence = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger eventSequence = new java.util.concurrent.atomic.AtomicInteger();
            when(support.nextNo(any(DocumentKind.class))).thenAnswer(invocation -> {
                DocumentKind kind = invocation.getArgument(0);
                if (kind == DocumentKind.INVENTORY_BATCH) {
                    return "PC2026072300001";
                }
                if (kind == DocumentKind.HIGH_VALUE_UNIQUE_CODE) {
                    return "20260723" + String.format("%06d", uniqueSequence.incrementAndGet());
                }
                if (kind == DocumentKind.UDI_TRACE_EVENT) {
                    return "UT20260723" + String.format("%06d", eventSequence.incrementAndGet());
                }
                return "UNKNOWN";
            });
            java.util.concurrent.atomic.AtomicLong traceCodeId = new java.util.concurrent.atomic.AtomicLong(500L);
            when(jdbcTemplate.queryForObject(contains("SELECT trace_code_id"), eq(Long.class), anyString()))
                    .thenAnswer(invocation -> traceCodeId.incrementAndGet());
            doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), anyLong());
            doReturn(1).when(jdbcTemplate).update(anyString(), anyString(), anyString());
            doReturn(1).when(jdbcTemplate).update(anyString(), anyString(), anyLong(), anyString(), anyString());

            Map<String, Object> result = service.action("RK001",
                    new ReceivingActionRequest("approve", "验收合格"));

            assertThat(result.get("status")).isEqualTo("approved");
            verify(support, times(10)).nextNo(DocumentKind.HIGH_VALUE_UNIQUE_CODE);
            verify(support, times(10)).nextNo(DocumentKind.UDI_TRACE_EVENT);
            verify(jdbcTemplate).queryForObject(contains("SELECT trace_code_id"), eq(Long.class), eq("20260723000001"));
            verify(jdbcTemplate).queryForObject(contains("SELECT trace_code_id"), eq(Long.class), eq("20260723000010"));
            verify(jdbcTemplate, times(10)).update(contains("INSERT INTO udi_trace_code"), any(Object[].class));
            verify(jdbcTemplate, times(10)).update(contains("INSERT INTO inventory_batch_trace_code"), any(Object[].class));
            verify(jdbcTemplate, times(10)).update(contains("INSERT INTO udi_trace_event"), any(Object[].class));
        }
        @Test
        @DisplayName("approve：高值耗材合格数量必须为整数")
        void shouldRejectFractionalQualifiedHighValueQuantity() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "draft"));
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(Map.of(
                            "itemId", 1L, "productId", 20L,
                            "qualifiedQuantity", BigDecimal.valueOf(1.5), "highValue", 1)));

            assertThatThrownBy(() -> service.action("RK001",
                    new ReceivingActionRequest("approve", "验收合格")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("高值耗材验收合格数量必须为整数");
            verify(support, never()).nextNo(DocumentKind.INVENTORY_BATCH);
        }

        @Test
        @DisplayName("reject：草稿 -> 已驳回")
        void shouldRejectDraft() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "draft"));
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.action("RK001",
                    new ReceivingActionRequest("reject", "验收不通过"));

            assertThat(result.get("status")).isEqualTo("rejected");
        }

        @Test
        @DisplayName("已审批的收货单不能重复验收")
        void shouldThrowWhenAlreadyApproved() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "approved"));

            assertThatThrownBy(() -> service.action("RK001",
                    new ReceivingActionRequest("approve", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("当前收货单状态不允许执行该操作");
        }

        @Test
        @DisplayName("无效动作时抛出异常")
        void shouldThrowWhenActionInvalid() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("receivingOrderId", 100L, "purchaseOrderId", 50L,
                            "warehouseId", 10L, "supplierId", 1L, "receivingStatus", "draft"));

            assertThatThrownBy(() -> service.action("RK001",
                    new ReceivingActionRequest("invalid", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("收货验收仅支持审核入库或拒收操作");
        }
    }
}
