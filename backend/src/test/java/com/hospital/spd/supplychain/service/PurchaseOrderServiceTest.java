package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.PurchaseOrderActionRequest;
import com.hospital.spd.supplychain.PurchaseOrderItemRequest;
import com.hospital.spd.supplychain.PurchaseOrderRequest;
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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderService 单元测试")
class PurchaseOrderServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(jdbcTemplate, support);
    }

    // ==================== 采购订单列表 ====================

    @Nested
    @DisplayName("listOrders() 采购订单分页查询")
    class ListOrdersTest {

        @Test
        @DisplayName("无筛选条件时返回全部订单")
        void shouldReturnAllOrdersWithoutFilters() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            Map<String, Object> summaryMap = Map.of(
                    "totalOrders", 100L, "totalAmount", BigDecimal.valueOf(50000),
                    "pendingApproval", 5L, "sentOrders", 10L, "closedOrders", 3L
            );
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(50L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("orderNo", "CG20260601001", "supplierName", "测试供应商"),
                    Map.of("orderNo", "CG20260601002", "supplierName", "测试供应商2")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(summaryMap);

            Map<String, Object> result = service.listOrders(params);

            assertThat(result).isNotNull();
            assertThat(result.get("total")).isEqualTo(50L);
            assertThat(result.get("page")).isEqualTo(1);
            assertThat(result.get("size")).isEqualTo(20);
            assertThat(result.get("rows")).asList().hasSize(2);
            assertThat(result.get("summary")).isNotNull();
        }

        @Test
        @DisplayName("按订单号筛选时返回过滤结果")
        void shouldReturnFilteredOrdersByOrderNo() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "orderNo", "CG2026");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("orderNo", "CG20260601001", "supplierName", "测试供应商")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalOrders", 1L, "totalAmount", BigDecimal.valueOf(1000),
                            "pendingApproval", 0L, "sentOrders", 0L, "closedOrders", 0L));

            Map<String, Object> result = service.listOrders(params);

            assertThat(result.get("total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("按状态筛选时返回过滤结果")
        void shouldReturnFilteredOrdersByStatus() {
            Map<String, String> params = Map.of("page", "1", "size", "20", "status", "draft");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("orderNo", "CG20260601005", "supplierName", "测试供应商")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalOrders", 3L, "totalAmount", BigDecimal.valueOf(3000),
                            "pendingApproval", 0L, "sentOrders", 0L, "closedOrders", 0L));

            Map<String, Object> result = service.listOrders(params);

            assertThat(result.get("total")).isEqualTo(3L);
        }

        @Test
        @DisplayName("关键词搜索时返回匹配结果")
        void shouldReturnOrdersByKeyword() {
            Map<String, String> params = Map.of("page", "1", "size", "20", "keyword", "消毒液");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("orderNo", "CG20260601010", "supplierName", "医药供应商")
            ));
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalOrders", 2L, "totalAmount", BigDecimal.valueOf(2000),
                            "pendingApproval", 2L, "sentOrders", 0L, "closedOrders", 0L));

            Map<String, Object> result = service.listOrders(params);

            assertThat(result.get("total")).isEqualTo(2L);
        }

        @Test
        @DisplayName("无匹配数据时返回空列表")
        void shouldReturnEmptyWhenNoOrdersFound() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalOrders", 0L, "totalAmount", BigDecimal.ZERO,
                            "pendingApproval", 0L, "sentOrders", 0L, "closedOrders", 0L));

            Map<String, Object> result = service.listOrders(params);

            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("rows")).asList().isEmpty();
        }

        @Test
        @DisplayName("count 为 null 时返回 total=0")
        void shouldReturnZeroTotalWhenCountIsNull() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(null);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbcTemplate.queryForMap(anyString())).thenReturn(
                    Map.of("totalOrders", 0L, "totalAmount", BigDecimal.ZERO,
                            "pendingApproval", 0L, "sentOrders", 0L, "closedOrders", 0L));

            Map<String, Object> result = service.listOrders(params);

            assertThat(((Number) result.get("total")).intValue()).isZero();
        }
    }

    // ==================== 采购订单详情 ====================

    @Nested
    @DisplayName("getDetail() 采购订单详情")
    class GetDetailTest {

        @Test
        @DisplayName("成功返回订单详情、明细行和操作记录")
        void shouldReturnOrderDetailWithItemsAndTracking() {
            String orderNo = "CG20260601001";
            Map<String, Object> orderMap = Map.of(
                    "orderId", 100L, "orderNo", orderNo, "supplierName", "测试供应商",
                    "orderStatus", "draft", "totalAmount", BigDecimal.valueOf(1000),
                    "orderQuantity", BigDecimal.TEN, "receivedQuantity", BigDecimal.ZERO,
                    "remainingQuantity", BigDecimal.TEN
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(orderMap);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("productCode", "P001", "productName", "测试产品", "quantity", BigDecimal.TEN)
            ));

            Map<String, Object> result = service.getDetail(orderNo);

            assertThat(result).containsKeys("order", "items", "tracking");
            assertThat(result.get("items")).asList().hasSize(1);
            assertThat(result.get("tracking")).asList().hasSize(1);
        }

        @Test
        @DisplayName("订单不存在时抛出 EmptyResultDataAccessException")
        void shouldThrowWhenOrderNotFound() {
            String orderNo = "NONEXISTENT";
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenThrow(new EmptyResultDataAccessException(1));

            assertThatThrownBy(() -> service.getDetail(orderNo))
                    .isInstanceOf(EmptyResultDataAccessException.class);
        }
    }

    // ==================== 创建采购订单 ====================

    @Nested
    @DisplayName("createOrder() 创建采购订单")
    class CreateOrderTest {

        private PurchaseOrderRequest validRequest;

        @BeforeEach
        void setUpRequest() {
            validRequest = new PurchaseOrderRequest(
                    "测试供应商",
                    "manual",
                    "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("P001", BigDecimal.TEN, "个", BigDecimal.valueOf(100)))
            );
            // findSupplierId
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            // findProduct called from insertItems
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001", "unit", "个",
                            "purchasePrice", BigDecimal.valueOf(100), "minPurchaseQty", BigDecimal.ONE));
            // nextNo
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("CG20260601001");
            // KeyHolder insert: purchase_order
            mockKeyHolderInsert(jdbcTemplate, 1L);
            // All regular updates: insertItems, appendTracking, writeAudit
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        }

        @Test
        @DisplayName("合法参数时成功创建草稿订单")
        void shouldCreateDraftOrderSuccessfully() {
            Map<String, Object> result = service.createOrder(validRequest);

            assertThat(result).containsKey("orderNo");
            assertThat(result.get("orderNo")).isEqualTo("CG20260601001");
            assertThat(result.get("totalAmount")).isEqualTo(BigDecimal.valueOf(1000));
        }
    }

    // ==================== 创建采购订单 - 参数校验 ====================

    @Nested
    @DisplayName("createOrder() 参数校验")
    class CreateOrderValidationTest {

        @Test
        @DisplayName("订单来源超过数据库字段长度时返回明确业务提示")
        void shouldRejectOrderSourceLongerThanThirtyCharacters() {
            PurchaseOrderRequest invalid = new PurchaseOrderRequest(
                    "测试供应商", "ACCEPT-PACK-PRINT-20260713-1505", "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("P001", BigDecimal.TEN, "支", BigDecimal.ONE))
            );

            assertThatThrownBy(() -> service.createOrder(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("订单来源不能超过30个字符");
        }

        @Test
        @DisplayName("明细为空时抛出异常")
        void shouldThrowWhenItemsIsEmpty() {
            PurchaseOrderRequest emptyItems = new PurchaseOrderRequest(
                    "测试供应商", "manual", "2026-06-15", List.of()
            );
            assertThatThrownBy(() -> service.createOrder(emptyItems))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("采购订单至少需要一条明细");
        }

        @Test
        @DisplayName("明细商品编码为空时抛出异常")
        void shouldThrowWhenProductCodeIsBlank() {
            PurchaseOrderRequest invalid = new PurchaseOrderRequest(
                    "测试供应商", "manual", "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("", BigDecimal.TEN, "个", BigDecimal.valueOf(100)))
            );
            assertThatThrownBy(() -> service.createOrder(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("商品编码和采购数量为必填项");
        }

        @Test
        @DisplayName("明细数量为空时抛出异常")
        void shouldThrowWhenQuantityIsNull() {
            PurchaseOrderRequest invalid = new PurchaseOrderRequest(
                    "测试供应商", "manual", "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("P001", null, "个", BigDecimal.valueOf(100)))
            );
            assertThatThrownBy(() -> service.createOrder(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("商品编码和采购数量为必填项");
        }

        @Test
        @DisplayName("明细数量为零时抛出异常")
        void shouldThrowWhenQuantityIsZero() {
            PurchaseOrderRequest invalid = new PurchaseOrderRequest(
                    "测试供应商", "manual", "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("P001", BigDecimal.ZERO, "个", BigDecimal.valueOf(100)))
            );
            assertThatThrownBy(() -> service.createOrder(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("商品编码和采购数量为必填项");
        }

        @Test
        @DisplayName("供应商不存在时抛出异常")
        void shouldThrowWhenSupplierNotFound() {
            PurchaseOrderRequest validRequest = new PurchaseOrderRequest(
                    "测试供应商", "manual", "2026-06-15",
                    List.of(new PurchaseOrderItemRequest("P001", BigDecimal.TEN, "个", BigDecimal.valueOf(100)))
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of());
            assertThatThrownBy(() -> service.createOrder(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("supplier does not exist or is disabled");
        }
    }

    // ==================== 采购订单状态流转 ====================

    @Nested
    @DisplayName("performAction() 采购订单状态流转")
    class PerformActionTest {

        private Map<String, Object> orderMap(String status, BigDecimal orderQty, BigDecimal receivedQty) {
            return Map.of(
                    "orderId", 100L,
                    "orderStatus", status,
                    "orderQuantity", orderQty,
                    "receivedQuantity", receivedQty
            );
        }

        @Test
        @DisplayName("submit：草稿 -> 待审批")
        void shouldSubmitFromDraftToPendingApproval() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("draft", BigDecimal.TEN, BigDecimal.ZERO));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performAction("CG001",
                    new PurchaseOrderActionRequest("submit", null));

            assertThat(result.get("status")).isEqualTo("pending_approval");
        }

        @Test
        @DisplayName("approve：待审批 -> 已审批")
        void shouldApproveFromPendingToApproved() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("pending_approval", BigDecimal.TEN, BigDecimal.ZERO));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performAction("CG001",
                    new PurchaseOrderActionRequest("approve", "同意"));

            assertThat(result.get("status")).isEqualTo("approved");
        }

        @Test
        @DisplayName("send：已审批 -> 已发送")
        void shouldSendFromApprovedToSent() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("approved", BigDecimal.TEN, BigDecimal.ZERO));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performAction("CG001",
                    new PurchaseOrderActionRequest("send", null));

            assertThat(result.get("status")).isEqualTo("sent");
        }

        @Test
        @DisplayName("close：已发送且全部收货 -> 已关闭")
        void shouldCloseWhenSentAndAllReceived() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("sent", BigDecimal.TEN, BigDecimal.TEN));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performAction("CG001",
                    new PurchaseOrderActionRequest("close", "采购完成"));

            assertThat(result.get("status")).isEqualTo("closed");
        }

        @Test
        @DisplayName("close：未全部收货时抛出异常")
        void shouldThrowWhenCloseWithUnreceivedItems() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("sent", BigDecimal.TEN, BigDecimal.valueOf(5)));

            assertThatThrownBy(() -> service.performAction("CG001",
                    new PurchaseOrderActionRequest("close", "采购完成")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("尚未完成数量履约");
        }

        @Test
        @DisplayName("close：缺少关闭原因时抛出异常")
        void shouldThrowWhenCloseWithoutReason() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("sent", BigDecimal.TEN, BigDecimal.TEN));

            assertThatThrownBy(() -> service.performAction("CG001",
                    new PurchaseOrderActionRequest("close", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("关闭采购订单必须填写关闭原因");
        }

        @Test
        @DisplayName("reject：待审批 -> 已驳回")
        void shouldRejectFromPendingToRejected() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("pending_approval", BigDecimal.TEN, BigDecimal.ZERO));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performAction("CG001",
                    new PurchaseOrderActionRequest("reject", "驳回"));

            assertThat(result.get("status")).isEqualTo("rejected");
        }

        @Test
        @DisplayName("草稿状态下不能审批")
        void shouldThrowWhenApprovingDraftOrder() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("draft", BigDecimal.TEN, BigDecimal.ZERO));

            assertThatThrownBy(() -> service.performAction("CG001",
                    new PurchaseOrderActionRequest("approve", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("当前状态不允许执行该操作");
        }

        @Test
        @DisplayName("无效动作时抛出异常")
        void shouldThrowWhenActionIsInvalid() {
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(orderMap("draft", BigDecimal.TEN, BigDecimal.ZERO));

            assertThatThrownBy(() -> service.performAction("CG001",
                    new PurchaseOrderActionRequest("invalid", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("采购订单动作无效");
        }
    }

    // ==================== 采购需求 ====================

    @Nested
    @DisplayName("采购需求相关方法")
    class DemandTest {

        @Test
        @DisplayName("listDemands 成功返回分页列表")
        void shouldReturnDemandList() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("demandId", 1L, "demandNo", "XQ20260601001", "demandStatus", "draft")
            ));

            Map<String, Object> result = service.listDemands(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(10L);
        }

        @Test
        @DisplayName("createDemand 成功创建需求")
        void shouldCreateDemandSuccessfully() {
            Map<String, Object> request = Map.of(
                    "productCode", "P001",
                    "quantity", BigDecimal.valueOf(50),
                    "deptName", "检验科",
                    "urgentLevel", "normal"
            );
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "minPurchaseQty", BigDecimal.ONE,
                            "purchasePrice", BigDecimal.valueOf(100)));
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("XQ20260601001");
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createDemand(request);

            assertThat(result).containsKey("demandNo");
            assertThat(result.get("demandNo")).isEqualTo("XQ20260601001");
            assertThat(result.get("suggestedPurchaseQty")).isInstanceOf(BigDecimal.class);
        }
    }

    // ==================== 采购计划 ====================

    @Nested
    @DisplayName("smartReplenishmentAnalysis() 智能补货分析")
    class SmartReplenishmentAnalysisTest {

        @Test
        @DisplayName("按一级库出二级库流水生成采购建议")
        void shouldAnalyzePrimaryWarehouseOutboundAndRecommendPurchaseQty() {
            when(support.nextNo(DocumentKind.PURCHASE_REPLENISHMENT_ANALYSIS)).thenReturn("CGFX2026070200001");
            when(jdbcTemplate.queryForList(contains("warehouse_transfer_out"))).thenReturn(List.of(
                    Map.ofEntries(
                            Map.entry("warehouseCode", "WH-CENTER"),
                            Map.entry("warehouseName", "SPD中心库"),
                            Map.entry("warehouseType", "中心库"),
                            Map.entry("productCode", "P001"),
                            Map.entry("productName", "测试耗材"),
                            Map.entry("specModel", "10ml"),
                            Map.entry("unit", "支"),
                            Map.entry("purchasePrice", BigDecimal.valueOf(3)),
                            Map.entry("minPurchaseQty", BigDecimal.valueOf(10)),
                            Map.entry("supplierName", "测试供应商"),
                            Map.entry("currentQty", BigDecimal.valueOf(12)),
                            Map.entry("issue5", BigDecimal.valueOf(3)),
                            Map.entry("issue15", BigDecimal.valueOf(15)),
                            Map.entry("issue30", BigDecimal.valueOf(25)),
                            Map.entry("issue45", BigDecimal.valueOf(40)),
                            Map.entry("issue60", BigDecimal.valueOf(60))
                    )
            ));
            mockKeyHolderInsert(jdbcTemplate, 88L);
            when(jdbcTemplate.update(contains("INSERT INTO purchase_replenishment_analysis_item"), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.smartReplenishmentAnalysis(Map.of("periodDays", "30"));

            assertThat(result).containsEntry("analysisId", 88L);
            assertThat(result).containsEntry("analysisNo", "CGFX2026070200001");
            assertThat(result).containsEntry("selectedPeriodDays", 30);
            assertThat(result.get("periodDays")).isEqualTo(List.of(5, 15, 30, 45, 60));
            assertThat(result.get("totalFormulaQty")).isEqualTo(BigDecimal.valueOf(13));
            assertThat(result.get("totalRecommendedQty")).isEqualTo(new BigDecimal("13"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0))
                    .containsEntry("selectedIssueQty", BigDecimal.valueOf(25))
                    .containsEntry("formulaReplenishQty", BigDecimal.valueOf(13))
                    .containsEntry("recommendedQty", new BigDecimal("13"));
            org.mockito.Mockito.verify(jdbcTemplate).update(contains("INSERT INTO purchase_replenishment_analysis_item"), any(Object[].class));
        }

        @Test
        @DisplayName("建议量低于最小采购量时按最小采购量补货")
        void shouldUseMinPurchaseQtyWhenFormulaQtyIsPositiveButSmall() {
            when(support.nextNo(DocumentKind.PURCHASE_REPLENISHMENT_ANALYSIS)).thenReturn("CGFX2026070200002");
            when(jdbcTemplate.queryForList(contains("warehouse_transfer_out"))).thenReturn(List.of(
                    Map.ofEntries(
                            Map.entry("warehouseCode", "WH-CENTER"),
                            Map.entry("warehouseName", "SPD中心库"),
                            Map.entry("warehouseType", "中心库"),
                            Map.entry("productCode", "P002"),
                            Map.entry("productName", "测试耗材2"),
                            Map.entry("specModel", "20ml"),
                            Map.entry("unit", "支"),
                            Map.entry("purchasePrice", BigDecimal.valueOf(3)),
                            Map.entry("minPurchaseQty", BigDecimal.valueOf(10)),
                            Map.entry("supplierName", "测试供应商"),
                            Map.entry("currentQty", BigDecimal.valueOf(24)),
                            Map.entry("issue5", BigDecimal.ZERO),
                            Map.entry("issue15", BigDecimal.ZERO),
                            Map.entry("issue30", BigDecimal.valueOf(25)),
                            Map.entry("issue45", BigDecimal.valueOf(25)),
                            Map.entry("issue60", BigDecimal.valueOf(25))
                    )
            ));
            mockKeyHolderInsert(jdbcTemplate, 89L);
            when(jdbcTemplate.update(contains("INSERT INTO purchase_replenishment_analysis_item"), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.smartReplenishmentAnalysis(Map.of());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertThat(rows.get(0))
                    .containsEntry("formulaReplenishQty", BigDecimal.ONE)
                    .containsEntry("recommendedQty", new BigDecimal("10"));
        }
    }

    @Nested
    @DisplayName("采购计划相关方法")
    class PlanTest {

        @Test
        @DisplayName("listPlans 成功返回分页列表")
        void shouldReturnPlanList() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("planId", 1L, "planNo", "JH20260601001", "planStatus", "draft")
            ));

            Map<String, Object> result = service.listPlans(params);

            assertThat((List<?>) result.get("rows")).hasSize(1);
            assertThat(result.get("total")).isEqualTo(5L);
        }

        @Test
        @DisplayName("createPlanFromDemands 成功生成采购计划")
        void shouldCreatePlanFromDemandsSuccessfully() {
            Map<String, Object> request = Map.of("supplierName", "测试供应商", "remark", "由需求生成");
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                    Map.of("demandId", 20L, "productId", 10L, "productSupplierId", 1L,
                            "plannedQuantity", BigDecimal.valueOf(100))
            ));
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("JH20260601001");
            mockKeyHolderInsert(jdbcTemplate, 30L);
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createPlanFromDemands(request);

            assertThat(result.get("createdPlans")).isEqualTo(1);
        }

        @Test
        @DisplayName("createPlanFromDemands 无已审核需求时抛出异常")
        void shouldThrowWhenNoApprovedDemands() {
            Map<String, Object> request = Map.of();
            when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

            assertThatThrownBy(() -> service.createPlanFromDemands(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no approved demands");
        }

        @Test
        @DisplayName("performPlanAction：审批计划")
        void shouldApprovePlan() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("planId", 1L, "planStatus", "draft",
                            "supplierId", 1L, "productId", 10L, "plannedQuantity", BigDecimal.valueOf(100))
            );
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performPlanAction("JH001",
                    new PurchaseOrderActionRequest("approve", null));

            assertThat(result.get("status")).isEqualTo("approved");
        }

        @Test
        @DisplayName("performPlanAction：执行计划生成订单")
        void shouldExecutePlanAndCreateOrder() {
            Map<String, Object> planMap = Map.of("planId", 1L, "planStatus", "approved",
                    "supplierId", 1L, "productId", 10L, "plannedQuantity", BigDecimal.valueOf(100));
            Map<String, Object> productMap = Map.of("productCode", "P001", "unit", "个",
                    "purchasePrice", BigDecimal.valueOf(100));
            when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                    .thenReturn(planMap, productMap);
            when(support.nextNo(any(DocumentKind.class)))
                    .thenReturn("CG20260601001");
            mockKeyHolderInsert(jdbcTemplate, 200L);
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performPlanAction("JH001",
                    new PurchaseOrderActionRequest("execute", null));

            assertThat(result.get("status")).isEqualTo("executed");
            assertThat(result).containsKey("orderNo");
        }

        @Test
        @DisplayName("performPlanAction：驳回计划")
        void shouldRejectPlan() {
            when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(
                    Map.of("planId", 1L, "planStatus", "draft",
                            "supplierId", 1L, "productId", 10L, "plannedQuantity", BigDecimal.valueOf(100))
            );
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.performPlanAction("JH001",
                    new PurchaseOrderActionRequest("reject", "不需要"));

            assertThat(result.get("status")).isEqualTo("rejected");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * Mock jdbcTemplate.update(PreparedStatementCreator, KeyHolder) so that
     * the KeyHolder receives a generated key value after the insert.
     * Uses reflection to set the private keyList field of GeneratedKeyHolder.
     */
    static void mockKeyHolderInsert(JdbcTemplate jdbcTemplate, long generatedKey) {
        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            if (kh instanceof GeneratedKeyHolder) {
                setKeyHolderValue(kh, generatedKey);
            }
            return 1;
        }).when(jdbcTemplate).update(
                any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                any(KeyHolder.class));
    }

    private static void setKeyHolderValue(KeyHolder kh, long value) {
        try {
            Field field = GeneratedKeyHolder.class.getDeclaredField("keyList");
            field.setAccessible(true);
            field.set(kh, List.of(Map.of("GENERATED_KEY", value)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set KeyHolder value", e);
        }
    }
}
