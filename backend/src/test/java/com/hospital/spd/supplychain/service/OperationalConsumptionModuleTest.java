package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalConsumptionModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;
    @Mock
    private QuotaPackageTraceFlowService quotaPackageTraceFlowService;

    private OperationalConsumptionModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalConsumptionModule(jdbcTemplate, support, quotaPackageTraceFlowService);
        org.mockito.Mockito.lenient().when(jdbcTemplate.queryForList(contains("FROM supplier_invoice"), eq(Long.class), anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void rejectsFractionalConsumptionBeforeLookingUpInventory() {
        assertThatThrownBy(() -> module.createConsumption(Map.of("deptName", "A", "warehouseName", "W",
                "productCode", "P", "quantity", new BigDecimal("1.5"))))
                .hasMessageContaining("必须为整数");
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate, support);
    }

    @Test
    void restrictedDepartmentCannotConsumeOrReverseAnotherDepartmentsStock() {
        var operator = new com.hospital.spd.common.OperatorContext(11L, "nurse", "127.0.0.1", List.of("ROLE_DEPT_USER"), 10L, 2);
        module = new OperationalConsumptionModule(jdbcTemplate, support, () -> operator,
                org.mockito.Mockito.mock(SettlementPointService.class), quotaPackageTraceFlowService);
        when(jdbcTemplate.queryForList(contains("SELECT w.warehouse_id AS warehouseId"), any(Object[].class)))
                .thenReturn(List.of(Map.of("deptId", 20L, "warehouseId", 30L)));
        assertThatThrownBy(() -> module.createConsumption(Map.of("deptName", "同名科室", "warehouseName", "B库",
                "productCode", "P", "quantity", BigDecimal.ONE)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("OTHER")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 30L, "deptId", 20L, "warehouseDeptId", 20L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("WHERE consumption.consumption_no = ?"), eq(Long.class), eq("OTHER"))).thenReturn(List.of());
        assertThatThrownBy(() -> module.reverseConsumption("OTHER"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(support);
    }

    @Test
    void createsConsumptionFromExplicitValues() throws Exception {
        mockExistingDepartment(20L);
        mockProduct(BigDecimal.valueOf(15));
        when(support.consumeAvailableFifo(eq(20L), eq(100L), eq(BigDecimal.TEN), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(List.of(new SupplyChainSupport.InventoryDeduction(300L, BigDecimal.TEN, BigDecimal.valueOf(15))));
        when(support.nextNo(DocumentKind.DEPARTMENT_CONSUMPTION)).thenReturn("XH20260601001");
        mockGeneratedKey(88L);

        Map<String, Object> result = module.createConsumption(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Department Warehouse",
                "productCode", "PC001",
                "quantity", BigDecimal.TEN
        ));

        assertThat(result)
                .containsEntry("consumptionNo", "XH20260601001")
                .containsEntry("amount", BigDecimal.valueOf(150));
        verify(support).consumeAvailableFifo(eq(20L), eq(100L), eq(BigDecimal.TEN),
                eq("department_consumption_out"), eq("department_consumption"), eq(88L), anyString());
        verify(jdbcTemplate).update(contains("INSERT INTO department_consumption_item"),
                eq(88L), eq(100L), eq(300L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(15)), eq(BigDecimal.valueOf(150)));
        var creator = org.mockito.ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbcTemplate).update(creator.capture(), any(KeyHolder.class));
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        var statement = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(java.sql.Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        verify(statement).setLong(2, 20L);
    }

    @Test
    void rejectsMissingConsumptionQuantity() {
        assertThatThrownBy(() -> module.createConsumption(Map.of(
                "deptName", "Surgery", "warehouseName", "Department Warehouse", "productCode", "PC001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity is required");
    }

    @Test
    void rejectsHighValueConsumptionOutsideBillingCallback() {
        mockExistingDepartment(10L);
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("HV001")))
                .thenReturn(Map.of("productId", 101L, "productCode", "HV001", "productName", "High Value Implant",
                        "unit", "piece", "purchasePrice", BigDecimal.valueOf(5000), "highValue", 1));

        assertThatThrownBy(() -> module.createConsumption(Map.of(
                "deptName", "Surgery", "warehouseName", "Department Warehouse",
                "productCode", "HV001", "quantity", BigDecimal.ONE
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("高值耗材");

        verify(support, never()).nextNo(DocumentKind.DEPARTMENT_CONSUMPTION);
        verify(support, never()).consumeAvailableFifo(
                anyLong(), anyLong(), any(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void reversesConsumptionAndAuditsAfterDurableWrites() {
        mockReversalProductLock();
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("XH001")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "deptId", 10L, "warehouseDeptId", 10L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("FROM department_consumption_item"), eq(1L)))
                .thenReturn(List.of(Map.of("productId", 100L, "batchId", 300L, "quantity", BigDecimal.TEN)));
        when(jdbcTemplate.queryForList(contains("SELECT sb.settlement_id"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.update(contains("UPDATE department_consumption SET status = 'reversed'"), eq("XH001")))
                .thenReturn(1);
        when(support.nextNo(DocumentKind.CONSUMPTION_RED_FLUSH)).thenReturn("FXH20260601001");

        Map<String, Object> result = module.reverseConsumption("XH001");

        assertThat(result)
                .containsEntry("flushNo", "FXH20260601001")
                .containsEntry("status", "approved");
        InOrder ordered = inOrder(jdbcTemplate, support);
        ordered.verify(jdbcTemplate).queryForMap(contains("FROM product WHERE product_id"), eq(100L));
        ordered.verify(jdbcTemplate).queryForMap(contains("FROM department_consumption"), eq("XH001"));
        ordered.verify(support).receiveAvailable(eq(20L), eq(100L), eq(300L), eq(BigDecimal.TEN),
                eq("department_consumption_reverse_in"), eq("department_consumption"), eq(1L), anyString());
        ordered.verify(jdbcTemplate).update(contains("UPDATE department_consumption SET status = 'reversed'"), eq("XH001"));
        ordered.verify(support).nextNo(DocumentKind.CONSUMPTION_RED_FLUSH);
        ordered.verify(jdbcTemplate).update(contains("INSERT INTO consumption_red_flush"),
                eq("FXH20260601001"), eq("XH001"));
        ordered.verify(support).writeAudit(eq("operational_closure"), eq("reverse_consumption"),
                eq(1L), eq("XH001"), eq("reverse consumption red flush"));
    }

    @Test
    void rejectsReversalWhenConsumptionHasConfirmedSettlement() {
        mockReversalProductLock();
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("XH001")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "deptId", 10L, "warehouseDeptId", 10L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("SELECT sb.settlement_id"), eq(1L)))
                .thenReturn(List.of(Map.of("settlementId", 77L, "status", "confirmed")));

        assertThatThrownBy(() -> module.reverseConsumption("XH001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已确认结算");

        verify(support, never()).receiveAvailable(anyLong(), anyLong(), anyLong(), any(),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void removesConsumptionItemsFromPendingSettlementBeforeRestoringInventory() {
        mockReversalProductLock();
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("XH001")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "deptId", 10L, "warehouseDeptId", 10L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("SELECT sb.settlement_id"), eq(1L)))
                .thenReturn(List.of(Map.of("settlementId", 77L, "status", "pending_confirm")));
        when(jdbcTemplate.queryForList(contains("FROM department_consumption_item"), eq(1L)))
                .thenReturn(List.of(Map.of("productId", 100L, "batchId", 300L, "quantity", BigDecimal.TEN)));
        when(jdbcTemplate.update(contains("UPDATE department_consumption SET status = 'reversed'"), eq("XH001")))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE sbi FROM settlement_bill_item sbi"), eq(1L))).thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE settlement_bill sb"), eq(77L))).thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE FROM settlement_bill"), eq(77L))).thenReturn(1);
        when(support.nextNo(DocumentKind.CONSUMPTION_RED_FLUSH)).thenReturn("FXH20260601001");

        module.reverseConsumption("XH001");

        InOrder ordered = inOrder(jdbcTemplate, support);
        ordered.verify(jdbcTemplate).update(contains("DELETE sbi FROM settlement_bill_item sbi"), eq(1L));
        ordered.verify(jdbcTemplate).update(contains("UPDATE settlement_bill sb"), eq(77L));
        ordered.verify(jdbcTemplate).update(contains("DELETE FROM settlement_bill"), eq(77L));
        ordered.verify(support).receiveAvailable(eq(20L), eq(100L), eq(300L), eq(BigDecimal.TEN),
                eq("department_consumption_reverse_in"), eq("department_consumption"), eq(1L), anyString());
    }

    @Test
    void resolvesQuotaPackageWithItsFullBaseQuantity() {
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label qpl"), eq("PKG001"), eq("PKG001"), eq("PKG001")))
                .thenReturn(List.of(new java.util.HashMap<>(Map.of(
                        "labelNo", "PKG001", "labelStatus", "signed",
                        "productCode", "PC001", "productName", "Syringe",
                        "packageQuantity", BigDecimal.TEN))));

        Map<String, Object> result = module.resolveConsumptionProduct("PKG001");

        assertThat(result).containsEntry("sourceType", "package")
                .containsEntry("packageQuantity", BigDecimal.TEN);
        verify(jdbcTemplate).queryForList(contains("qpl.package_quantity AS packageQuantity"), eq("PKG001"), eq("PKG001"), eq("PKG001"));
    }

    @Test
    void restoresQuotaPackageTraceWhenItsConsumptionIsReversed() {
        mockReversalProductLock();
        when(jdbcTemplate.queryForObject(contains("SELECT product_id FROM quota_package_label"), eq(Long.class), eq(55L)))
                .thenReturn(100L);
        when(jdbcTemplate.queryForMap(contains("FROM quota_package_label WHERE label_id"), eq(55L)))
                .thenReturn(Map.of("status", "signed"));
        when(jdbcTemplate.queryForList(contains("SELECT source_id FROM quota_package_label_source"), eq(Long.class), eq(55L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("XH001")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "deptId", 10L, "warehouseDeptId", 10L, "status", "confirmed",
                        "relatedBizType", "quota_package_label", "relatedBizId", 55L));
        when(jdbcTemplate.queryForList(contains("SELECT sb.settlement_id"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM department_consumption_item"), eq(1L)))
                .thenReturn(List.of(Map.of("productId", 100L, "batchId", 300L, "quantity", BigDecimal.TEN)));
        when(jdbcTemplate.update(contains("UPDATE department_consumption SET status = 'reversed'"), eq("XH001")))
                .thenReturn(1);
        when(support.nextNo(DocumentKind.CONSUMPTION_RED_FLUSH)).thenReturn("FXH20260601001");

        module.reverseConsumption("XH001");

        verify(quotaPackageTraceFlowService).reverseConsumption(55L, "XH001");
    }

    private void mockExistingDepartment(Long deptId) {
        when(jdbcTemplate.queryForList(contains("SELECT w.warehouse_id AS warehouseId"), any(Object[].class)))
                .thenReturn(List.of(Map.of("deptId", deptId, "warehouseId", 20L)));
    }

    private void mockProduct(BigDecimal purchasePrice) {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L,
                        "productCode", "PC001",
                        "productName", "Syringe",
                        "unit", "piece",
                        "purchasePrice", purchasePrice
                ));
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
    @Test
    void resolvesPackageUdiThroughTheSameLabelIdentity() {
        org.mockito.Mockito.lenient().when(jdbcTemplate.queryForList(contains("FROM quota_package_label qpl"),
                eq("QP-PKG001"), eq("QP-PKG001"), eq("QP-PKG001")))
                .thenReturn(List.of(new java.util.HashMap<>(Map.of("labelNo", "PKG001",
                        "traceCodeId", 55L, "packageQuantity", BigDecimal.TEN))));
        assertThat(module.resolveConsumptionProduct("QP-PKG001"))
                .containsEntry("sourceType", "package").containsEntry("traceCodeId", 55L)
                .containsEntry("packageQuantity", BigDecimal.TEN);
    }

    private void mockReversalProductLock() {
        when(jdbcTemplate.queryForList(contains("WHERE consumption.consumption_no = ?"), eq(Long.class), eq("XH001")))
                .thenReturn(List.of(100L));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_id"), eq(100L)))
                .thenReturn(Map.of("productId", 100L));
    }

}
