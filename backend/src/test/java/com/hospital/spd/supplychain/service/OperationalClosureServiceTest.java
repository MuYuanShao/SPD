package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.supplychain.CreateRequisitionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OperationalClosureService 单元测试——覆盖日清列表、总览、缺货补货、申领、消耗、结算等业务。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationalClosureServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalClosureService service;

    @BeforeEach
    void setUp() {
        service = new OperationalClosureService(new OperationalClosureReadModel(jdbcTemplate),
                new OperationalShortageModule(jdbcTemplate, support),
                new OperationalSettlementModule(jdbcTemplate, support),
                new OperationalPdaModule(jdbcTemplate, support),
                new OperationalRiskModule(jdbcTemplate, support),
                new OperationalHighValueModule(jdbcTemplate, support),
                new OperationalDeliveryModule(jdbcTemplate, support),
                new OperationalRequisitionModule(jdbcTemplate, support),
                new OperationalConsumptionModule(jdbcTemplate, support),
                new SettlementPointService(jdbcTemplate, support));
        lenient().when(support.nextNo(any(DocumentKind.class)))
                .thenAnswer(invocation -> ((DocumentKind) invocation.getArgument(0)).prefix() + "20260601001");
        lenient().when(jdbcTemplate.queryForList(contains("SELECT source.warehouse_id"),
                eq(Long.class), any())).thenReturn(List.of(1L));
    }

    private void populateKeyHolder(KeyHolder kh, Long keyValue) throws Exception {
        Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
        keyListField.setAccessible(true);
        keyListField.set(kh, List.of(Map.of("GENERATED_KEY", keyValue)));
    }

    private static CreateRequisitionRequest requisitionRequest(Map<String, Object> body) {
        return new CreateRequisitionRequest(
                String.valueOf(body.get("deptName")), String.valueOf(body.get("warehouseName")),
                null, null, null, String.valueOf(body.get("productCode")),
                (BigDecimal) body.get("quantity"), null, null, null);
    }

    // ==================== list() — 8 种类型参数化测试 ====================

    @ParameterizedTest
    @CsvSource({
            "shortage,    shortage_replenishment_task",
            "delivery,    spd_delivery_order",
            "requisition, department_requisition",
            "consumption, department_consumption",
            "settlement,  settlement_bill",
            "pda,         pda_offline_record",
            "risk,        cold_chain_exception",
            "high-value,  high_value_charge"
    })
    @DisplayName("按类型查询日清列表——每种类型返回正确结构")
    void shouldListByType(String type, String expectedTable) {
        Map<String, String> params = Map.of("page", "1", "size", "20");

        when(jdbcTemplate.queryForList(contains(expectedTable), eq(20), eq(0)))
                .thenReturn(List.of(Map.of("bizNo", "001")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L);

        List<Map<String, Object>> result = rows(service.list(type, params));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("bizNo");
        verify(jdbcTemplate).queryForList(contains(expectedTable), eq(20), eq(0));
    }

    @Test
    @DisplayName("查询列表——未知类型返回空列表")
    void shouldReturnEmptyForUnknownType() {
        List<Map<String, Object>> result = rows(service.list("unknown", Map.of("page", "1")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询列表——每种类型使用正确的分页参数")
    void shouldListWithCustomPagination() {
        Map<String, String> params = Map.of("page", "2", "size", "10");

        when(jdbcTemplate.queryForList(contains("shortage_replenishment_task"), eq(10), eq(10)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(0L);

        service.list("shortage", params);

        verify(jdbcTemplate).queryForList(contains("shortage_replenishment_task"), eq(10), eq(10));
    }

    // ==================== overview() ====================

    @Test
    @DisplayName("总览统计——返回所有汇总数据和最近事件")
    void shouldReturnOverview() {
        // 6 count() calls
        when(jdbcTemplate.queryForObject(contains("shortage_replenishment_task"), eq(Integer.class)))
                .thenReturn(3);
        when(jdbcTemplate.queryForObject(contains("spd_delivery_order"), eq(Integer.class)))
                .thenReturn(5);
        when(jdbcTemplate.queryForObject(contains("department_consumption"), eq(Integer.class)))
                .thenReturn(10);
        when(jdbcTemplate.queryForObject(contains("settlement_bill"), eq(Integer.class)))
                .thenReturn(2);
        when(jdbcTemplate.queryForObject(contains("pda_offline_record"), eq(Integer.class)))
                .thenReturn(8);
        // cold_chain_exception + recall_event
        when(jdbcTemplate.queryForObject(contains("cold_chain_exception"), eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("recall_event"), eq(Integer.class)))
                .thenReturn(2);

        // inventory_event recent list
        when(jdbcTemplate.queryForList(contains("FROM inventory_event")))
                .thenReturn(List.of(Map.of("eventNo", "EVT001", "eventType", "delivery_out")));

        Map<String, Object> result = service.overview();

        assertThat(result).containsKey("summary");
        assertThat(result).containsKey("events");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary).containsKey("shortageTasks");
        assertThat(summary).containsKey("pendingDeliveries");
        assertThat(summary).containsKey("consumptions");
        assertThat(summary).containsKey("settlements");
        assertThat(summary).containsKey("pdaRecords");
        assertThat(summary).containsKey("riskEvents");
        // riskEvents = 1 (cold_chain) + 2 (recall) = 3
        assertThat(summary.get("riskEvents")).isEqualTo(3);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        assertThat(events).hasSize(1);
    }

    // ==================== generateShortage() ====================

    @Test
    @DisplayName("生成缺货补货任务——使用显式参数")
    void shouldGenerateShortageWithExplicitValues() {
        Map<String, Object> body = Map.of(
                "deptName", "外科",
                "productCode", "PC001",
                "minQty", BigDecimal.valueOf(10),
                "currentQty", BigDecimal.valueOf(3),
                "replenishQty", BigDecimal.valueOf(7)
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器"));
        when(jdbcTemplate.queryForObject(contains("department_consumption"),
                eq(BigDecimal.class), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        when(support.nextNo(eq("QH"), eq("shortage_replenishment_task"), eq("task_no")))
                .thenReturn("QH20260601001");

        Map<String, Object> result = service.generateShortage(body);

        assertThat(result)
                .containsEntry("taskNo", "QH20260601001")
                .containsEntry("status", "pending_replenish");
        verify(jdbcTemplate).update(contains("INSERT INTO shortage_replenishment_task"),
                eq("QH20260601001"), eq("外科"), eq("PC001"), anyString(),
                eq(BigDecimal.valueOf(10)), eq(BigDecimal.valueOf(3)), eq(BigDecimal.valueOf(7)),
                eq(7), eq(BigDecimal.ZERO), eq(new BigDecimal("0.0000")),
                eq(BigDecimal.ZERO), eq(1), anyString());
    }

    @Test
    @DisplayName("生成缺货补货任务——拒绝缺少必填值")
    void shouldGenerateShortageWithDefaults() {
        assertThatThrownBy(() -> service.generateShortage(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deptName is required");
    }

    // ==================== createRequisition() ====================

    @Test
    @DisplayName("创建科室申领——成功创建并返回申领单号")
    void shouldCreateRequisitionSuccessfully() throws Exception {
        Map<String, Object> body = Map.of(
                "deptName", "外科",
                "warehouseName", "外科库",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(5)
        );

        // Java eagerly evaluates fallback arguments to text() helper
        when(jdbcTemplate.queryForObject(contains("SELECT dept_name FROM sys_dept"),
                eq(String.class)))
                .thenReturn("内科");
        when(jdbcTemplate.queryForObject(contains("SELECT product_code FROM product"),
                eq(String.class)))
                .thenReturn("PC001");

        // ensureDept — exists
        when(jdbcTemplate.queryForList(contains("FROM sys_dept WHERE dept_name = ?"), anyString()))
                .thenReturn(List.of(Map.of("deptId", 10L, "deptCode", "SURG", "deptName", "外科")));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"),
                eq(Long.class), eq("外科库"))).thenReturn(List.of(20L));

        // findProduct
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器", "unit", "支", "purchasePrice", BigDecimal.TEN));
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"),
                eq(Long.class), eq(10L), eq(20L), eq(100L))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM warehouse"),
                eq(Long.class), eq(20L), eq(10L))).thenReturn(1L);


        when(support.nextNo(eq("SL"), eq("department_requisition"), eq("requisition_no")))
                .thenReturn("SL20260601001");

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 99L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Map<String, Object> result = service.createRequisition(requisitionRequest(body));

        assertThat(result)
                .containsEntry("requisitionNo", "SL20260601001")
                .containsEntry("status", "pending_approval");
    }

    @Test
    @DisplayName("创建科室申领——无科室时拒绝创建")
    void shouldCreateRequisitionAndAutoCreateDept() throws Exception {
        Map<String, Object> body = Map.of(
                "deptName", "新科室",
                "warehouseName", "新科室库",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(3)
        );

        // Java eagerly evaluates fallback arguments to text() helper
        when(jdbcTemplate.queryForObject(contains("SELECT dept_name FROM sys_dept"),
                eq(String.class)))
                .thenReturn("内科");
        when(jdbcTemplate.queryForObject(contains("SELECT product_code FROM product"),
                eq(String.class)))
                .thenReturn("PC001");

        // ensureDept — not found, need to create
        when(jdbcTemplate.queryForList(contains("FROM sys_dept WHERE dept_name = ?"), anyString()))
                .thenReturn(List.of());

        // count() for new dept code generation
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM sys_dept WHERE 1 = 1"), eq(Integer.class)))
                .thenReturn(0);

        // queryForObject after insert to get new dept_id
        when(jdbcTemplate.queryForObject(contains("SELECT dept_id FROM sys_dept WHERE dept_code = ?"),
                eq(Long.class), anyString()))
                .thenReturn(20L);

        // findProduct
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器", "unit", "支"));

        when(support.nextNo(eq("SL"), eq("department_requisition"), eq("requisition_no")))
                .thenReturn("SL20260601002");

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 99L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        assertThatThrownBy(() -> service.createRequisition(requisitionRequest(body)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("科室不存在或已停用");
    }

    // ==================== createConsumption() ====================

    @Test
    @DisplayName("创建消耗记录——成功创建并返回消耗单号和金额")
    void shouldCreateConsumptionSuccessfully() throws Exception {
        Map<String, Object> body = Map.of(
                "deptName", "外科",
                "warehouseName", "外科库",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(10)
        );

        // ensureDept — exists
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), anyString()))
                .thenReturn(List.of(10L));
        when(jdbcTemplate.queryForObject(contains("JOIN sys_dept"), eq(Long.class), eq("外科"), eq("外科库")))
                .thenReturn(20L);

        // findProduct
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器", "unit", "支",
                        "purchasePrice", BigDecimal.valueOf(15)));

        when(support.nextNo(eq("XH"), eq("department_consumption"), eq("consumption_no")))
                .thenReturn("XH20260601001");

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 88L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        when(support.consumeAvailableFifo(eq(20L), eq(100L), eq(BigDecimal.TEN), anyString(), anyString(), eq(88L), anyString()))
                .thenReturn(List.of(new SupplyChainSupport.InventoryDeduction(200L, BigDecimal.TEN, BigDecimal.valueOf(15))));

        Map<String, Object> result = service.createConsumption(body);

        assertThat(result)
                .containsEntry("consumptionNo", "XH20260601001")
                .containsEntry("amount", BigDecimal.valueOf(150));
        verify(jdbcTemplate).update(contains("INSERT INTO department_consumption_item"),
                eq(88L), eq(100L), eq(200L), eq(BigDecimal.valueOf(10)),
                eq(BigDecimal.valueOf(15)), eq(BigDecimal.valueOf(150)));
    }

    @Test
    @DisplayName("创建消耗记录——拒绝缺少仓库和数量")
    void shouldCreateConsumptionWithDefaultQuantity() throws Exception {
        Map<String, Object> body = Map.of(
                "deptName", "外科",
                "productCode", "PC001"
        );

        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), anyString()))
                .thenReturn(List.of(10L));

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器", "unit", "支",
                        "purchasePrice", BigDecimal.valueOf(20)));

        when(support.nextNo(eq("XH"), eq("department_consumption"), eq("consumption_no")))
                .thenReturn("XH20260601002");

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 88L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        assertThatThrownBy(() -> service.createConsumption(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseName is required");
    }

    // ==================== createDelivery() ====================

    @Test
    @DisplayName("创建配送出库单——成功创建")
    void shouldCreateDeliverySuccessfully() {
        Map<String, Object> body = Map.of(
                "requisitionNo", "SL001",
                "deptName", "外科",
                "warehouseName", "主仓库",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(20)
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器"));

        when(support.nextNo(eq("PS"), eq("spd_delivery_order"), eq("delivery_no")))
                .thenReturn("PS20260601001");

        Map<String, Object> result = service.createDelivery(body);

        assertThat(result)
                .containsEntry("deliveryNo", "PS20260601001")
                .containsEntry("status", "picked");
    }

    @Test
    @DisplayName("创建配送出库单——无申领单号时传入空字符串")
    void shouldCreateDeliveryWithoutRequisitionNo() {
        Map<String, Object> body = Map.of(
                "deptName", "外科",
                "warehouseName", "主仓库",
                "productCode", "PC001",
                "quantity", BigDecimal.ONE
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器"));

        when(support.nextNo(eq("PS"), eq("spd_delivery_order"), eq("delivery_no")))
                .thenReturn("PS20260601002");

        Map<String, Object> result = service.createDelivery(body);

        assertThat(result).containsEntry("deliveryNo", "PS20260601001");
        // requisitionNo was null/blank → nullIfBlank returns null → SQL receives null
        verify(jdbcTemplate).update(contains("INSERT INTO spd_delivery_order"),
                eq("PS20260601001"), isNull(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    // ==================== signDelivery() ====================

    @Test
    @DisplayName("签收配送单——成功签收并扣减库存")
    void shouldSignDeliverySuccessfully() {
        String deliveryNo = "PS001";

        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), eq(deliveryNo)))
                .thenReturn(Map.of(
                        "deliveryId", 1L, "deliveryNo", deliveryNo,
                        "deptName", "外科",
                        "warehouseName", "主仓库", "productCode", "PC001",
                        "quantity", BigDecimal.valueOf(10), "status", "picked"
                ));

        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"),
                eq(Long.class), anyString()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept"), eq(Long.class), eq("外科"), eq(1L)))
                .thenReturn(List.of(2L));
        when(jdbcTemplate.queryForObject(contains("spd_delivery_package_binding"), eq(Integer.class), eq(1L)))
                .thenReturn(0);

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器"));

        when(jdbcTemplate.update(contains("UPDATE spd_delivery_order"), eq(2L), eq(deliveryNo))).thenReturn(1);

        Map<String, Object> result = service.signDelivery(deliveryNo);

        assertThat(result)
                .containsEntry("deliveryNo", deliveryNo)
                .containsEntry("status", "signed");
        verify(support).transferAvailableFifo(eq(1L), eq(2L), eq(100L), eq(BigDecimal.valueOf(10)),
                eq("spd_delivery_order"), eq(1L), anyString());
    }

    @Test
    @DisplayName("签收配送单——已经签收时抛出异常")
    void shouldThrowWhenDeliveryAlreadySigned() {
        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), anyString()))
                .thenReturn(Map.of(
                        "deliveryId", 1L, "deliveryNo", "PS001",
                        "status", "signed"
                ));

        assertThatThrownBy(() -> service.signDelivery("PS001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only picked delivery");
    }

    // ==================== reverseConsumption() ====================

    @Test
    @DisplayName("冲销消耗记录——成功冲销并生成红冲单")
    void shouldReverseConsumptionSuccessfully() {
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), anyString()))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("FROM department_consumption_item"), eq(1L)))
                .thenReturn(List.of(Map.of("productId", 100L, "batchId", 200L, "quantity", BigDecimal.TEN)));
        when(jdbcTemplate.update(contains("UPDATE department_consumption"), anyString())).thenReturn(1);

        when(support.nextNo(eq("FXH"), eq("consumption_red_flush"), eq("flush_no")))
                .thenReturn("FXH20260601001");

        Map<String, Object> result = service.reverseConsumption("XH001");

        assertThat(result)
                .containsEntry("flushNo", "FXH20260601001")
                .containsEntry("status", "approved");
        verify(support).writeAudit(eq("operational_closure"), eq("reverse_consumption"),
                eq(1L), eq("XH001"), anyString());
    }

    // ==================== uploadPda() ====================

    @Test
    @DisplayName("上传 PDA 离线记录——成功重放")
    void shouldUploadPdaSuccessfully() {
        Map<String, Object> body = Map.of(
                "deviceNo", "PDA-002",
                "operationType", "delivery_sign"
        );

        when(support.nextNo(eq("PDA"), eq("pda_offline_record"), eq("record_no")))
                .thenReturn("PDA20260601001");

        Map<String, Object> result = service.uploadPda(body);

        assertThat(result)
                .containsEntry("recordNo", "PDA20260601001")
                .containsEntry("status", "replayed");
    }

    @Test
    @DisplayName("上传 PDA 离线记录——使用默认参数")
    void shouldUploadPdaWithDefaults() {
        when(support.nextNo(eq("PDA"), eq("pda_offline_record"), eq("record_no")))
                .thenReturn("PDA20260601002");

        Map<String, Object> result = service.uploadPda(Map.of());

        assertThat(result)
                .containsEntry("recordNo", "PDA20260601001")
                .containsEntry("status", "replayed");
        // verify defaults: deviceNo="PDA-001", operationType="delivery_sign"
    }

    // ==================== coldChainException() ====================

    @Test
    @DisplayName("创建冷链异常——成功创建")
    void shouldCreateColdChainException() {
        Map<String, Object> body = Map.of(
                "productCode", "PC001",
                "warehouseName", "冷库A",
                "temperature", BigDecimal.valueOf(12),
                "severity", "high"
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "疫苗"));

        when(support.nextNo(eq("LL"), eq("cold_chain_exception"), eq("event_no")))
                .thenReturn("LL20260601001");

        Map<String, Object> result = service.coldChainException(body);

        assertThat(result)
                .containsEntry("eventNo", "LL20260601001")
                .containsEntry("status", "pending_dispose");
    }

    // ==================== createRecall() ====================

    @Test
    @DisplayName("创建产品召回——成功隔离")
    void shouldCreateRecallSuccessfully() {
        Map<String, Object> body = Map.of(
                "productCode", "PC001",
                "scope", "primary",
                "warehouseName", "主仓库",
                "batchNo", "PC20260601",
                "reason", "质量异常"
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "注射器"));
        when(jdbcTemplate.queryForList(contains("converted_stock"), any(Object[].class)))
                .thenReturn(List.of(Map.of("warehouseId", 10L, "batchId", 66L,
                        "looseQty", BigDecimal.valueOf(50), "packageQty", BigDecimal.ZERO,
                        "totalQty", BigDecimal.valueOf(50))));
        when(jdbcTemplate.queryForMap(contains("warehouse_type LIKE '%一级%'")))
                .thenReturn(Map.of("warehouseId", 10L, "warehouseName", "主仓库"));
        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 88L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        when(support.nextNo(eq("ZH"), eq("recall_event"), eq("recall_no")))
                .thenReturn("ZH20260601001");

        Map<String, Object> result = service.createRecall(body);

        assertThat(result)
                .containsEntry("recallNo", "ZH20260601001")
                .containsEntry("status", "isolated");
    }

    // ==================== highValueCharge() ====================

    @Test
    @DisplayName("高值耗材计费——成功创建计费记录")
    void shouldCreateHighValueCharge() {
        Map<String, Object> body = Map.of(
                "productCode", "PC001",
                "deptName", "外科",
                "patientNo", "MZ-1001",
                "quantity", BigDecimal.valueOf(2)
        );

        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "支架", "purchasePrice", BigDecimal.valueOf(500)));

        when(support.nextNo(eq("GZ"), eq("high_value_charge"), eq("charge_no")))
                .thenReturn("GZ20260601001");

        Map<String, Object> result = service.highValueCharge(body);

        assertThat(result)
                .containsEntry("chargeNo", "GZ20260601001")
                .containsEntry("amount", BigDecimal.valueOf(1000));
    }

    @Test
    @DisplayName("高值耗材计费——拒绝缺少关键业务参数")
    void shouldCreateHighValueChargeWithDefaults() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001",
                        "productName", "支架", "purchasePrice", BigDecimal.valueOf(500)));

        when(support.nextNo(eq("GZ"), eq("high_value_charge"), eq("charge_no")))
                .thenReturn("GZ20260601002");

        assertThatThrownBy(() -> service.highValueCharge(Map.of("productCode", "PC001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity is required");
    }

    // ==================== options() ====================

    @Test
    @DisplayName("查询选项下拉——返回全部选项列表")
    void shouldReturnOptions() {
        when(jdbcTemplate.queryForList(contains("FROM sys_dept"))).thenReturn(List.of(Map.of("deptName", "外科")));
        when(jdbcTemplate.queryForList(contains("FROM warehouse"))).thenReturn(List.of(Map.of("warehouseName", "主仓库")));
        when(jdbcTemplate.queryForList(contains("FROM product p"))).thenReturn(List.of(Map.of("productCode", "PC001")));
        when(jdbcTemplate.queryForList(contains("FROM inventory_balance bal"))).thenReturn(List.of(Map.of("availableQty", BigDecimal.TEN)));

        Map<String, Object> result = service.options();

        assertThat(result)
                .containsKey("departments")
                .containsKey("warehouses")
                .containsKey("products")
                .containsKey("balances");
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }
}
