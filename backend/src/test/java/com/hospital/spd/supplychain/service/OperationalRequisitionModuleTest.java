package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.system.service.ApprovalFlowGuard;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalRequisitionModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;
    @Mock
    private ApprovalFlowGuard approvalFlowGuard;
    @Mock
    private OperatorContextProvider operatorContextProvider;
    @Mock
    private HighValueTraceFlowService traceFlowService;

    private OperationalRequisitionModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalRequisitionModule(jdbcTemplate, support);
        lenient().when(jdbcTemplate.queryForList(contains("SELECT source.warehouse_id"),
                eq(Long.class), any())).thenReturn(List.of(30L));
    }

    @Test
    void validatesCatalogAgainstDestinationButHighValueCodesAgainstSourceWarehouse() throws Exception {
        module = new OperationalRequisitionModule(jdbcTemplate, support, approvalFlowGuard,
                operatorContextProvider, traceFlowService);
        when(operatorContextProvider.current()).thenReturn(OperatorContext.system());
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), eq("Surgery"))).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("warehouse_id = ?"), eq(Long.class), eq(20L)))
                .thenReturn(List.of(20L));
        when(jdbcTemplate.queryForList(contains("warehouse_type LIKE '%中心%'"), eq(Long.class), eq(30L)))
                .thenReturn(List.of(30L));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("HV001")))
                .thenReturn(Map.of("productId", 100L, "productCode", "HV001", "productName", "Implant",
                        "unit", "piece", "purchasePrice", BigDecimal.TEN, "highValue", 1));
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(100L))).thenReturn(1L);
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601001");
        mockGeneratedKey(99L);
        when(jdbcTemplate.queryForObject(contains("SELECT item_id FROM department_requisition_item"),
                eq(Long.class), eq(99L))).thenReturn(199L);
        List<HighValueTraceFlowService.TraceUnit> units = List.of(
                new HighValueTraceFlowService.TraceUnit(1L, "UDI-HV-1", 7L));
        when(traceFlowService.requireUnits(any(), eq(BigDecimal.ONE), eq(100L), eq(30L), eq(List.of("in_stock"))))
                .thenReturn(units);

        module.createRequisition(Map.of(
                "deptName", "Surgery",
                "destinationWarehouseId", 20L,
                "sourceWarehouseId", 30L,
                "items", List.of(Map.of("productCode", "HV001", "quantity", BigDecimal.ONE,
                        "uniqueCodes", List.of("UDI-HV-1")))
        ));

        verify(jdbcTemplate).queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(100L));
        verify(traceFlowService).requireUnits(any(), eq(BigDecimal.ONE), eq(100L), eq(30L),
                eq(List.of("in_stock")));
    }

    @Test
    void createsRequisitionForExistingDepartment() throws Exception {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), anyString())).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), eq("Main Warehouse")))
                .thenReturn(List.of(20L));
        mockProduct();
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class), eq(10L), eq(20L), eq(100L))).thenReturn(1L);
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601001");
        mockGeneratedKey(99L);

        Map<String, Object> result = module.createRequisition(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(5)
        ));

        assertThat(result)
                .containsEntry("requisitionNo", "SL20260601001")
                .containsEntry("status", "pending_approval");
        verify(jdbcTemplate).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), eq(100L), eq(BigDecimal.valueOf(5)), eq("loose"), eq("piece"), eq(BigDecimal.TEN),
                eq(BigDecimal.valueOf(50)), eq("department requisition"));
    }

    @Test
    void rejectsDepartmentWhenMissing() {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), anyString())).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptName", "New Department",
                "warehouseName", "Main Warehouse",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(3)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("department does not exist");
    }

    @Test
    void rejectsProductOutsideCurrentDepartmentWarehouseCatalog() {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), eq("Surgery"))).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), eq("Main Warehouse")))
                .thenReturn(List.of(20L));
        mockProduct();
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(100L))).thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptName", "Surgery", "warehouseName", "Main Warehouse",
                "productCode", "PC001", "quantity", BigDecimal.valueOf(3)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("科室库房目录");
    }

    @Test
    void createsOneRequisitionHeaderForMultipleItems() throws Exception {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), eq("Surgery"))).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), eq("Main Warehouse")))
                .thenReturn(List.of(20L));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("PC001")))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "Syringe",
                        "unit", "piece", "purchasePrice", BigDecimal.TEN, "highValue", 0));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("PC002")))
                .thenReturn(Map.of("productId", 101L, "productCode", "PC002", "productName", "Gauze",
                        "unit", "piece", "purchasePrice", BigDecimal.valueOf(2), "highValue", 0));
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), any())).thenReturn(1L);
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601001");
        mockGeneratedKey(99L);

        Map<String, Object> result = module.createRequisition(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "items", List.of(
                        Map.of("productCode", "PC001", "quantity", BigDecimal.valueOf(5), "requisitionMode", "loose"),
                        Map.of("productCode", "PC002", "quantity", BigDecimal.TEN, "requisitionMode", "quota_package")
                )
        ));

        assertThat(result).containsEntry("requisitionNo", "SL20260601001").containsEntry("itemCount", 2);
        verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbcTemplate, times(2)).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), any(), any(), anyString(), any(), any(), any(), eq("department requisition"));
    }

    @Test
    void rejectsCreatingRequisitionForAnotherDepartmentBeforeWriting() {
        module = new OperationalRequisitionModule(jdbcTemplate, support, approvalFlowGuard,
                operatorContextProvider, traceFlowService);
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), eq("Surgery"))).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("warehouse_id = ?"), eq(Long.class), eq(20L)))
                .thenReturn(List.of(20L));
        when(jdbcTemplate.queryForList(contains("warehouse_type LIKE '%中心%'"), eq(Long.class), eq(30L)))
                .thenReturn(List.of(30L));
        when(operatorContextProvider.current()).thenReturn(new OperatorContext(
                8L, "dept-user", "127.0.0.1", List.of("ROLE_DEPT_USER"), 11L,
                OperatorContext.DATA_SCOPE_DEPT));

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptName", "Surgery",
                "destinationWarehouseId", 20L,
                "sourceWarehouseId", 30L,
                "productCode", "PC001",
                "quantity", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前登录科室");

        verifyNoInteractions(support);
        verify(jdbcTemplate, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void validatesEveryItemBeforeCreatingRequisitionHeader() {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), eq("Surgery"))).thenReturn(List.of(10L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class),
                eq("Main Warehouse"))).thenReturn(List.of(20L));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("PC001")))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "Syringe",
                        "unit", "piece", "purchasePrice", BigDecimal.TEN, "highValue", 0));
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq("PC002")))
                .thenReturn(Map.of("productId", 101L, "productCode", "PC002", "productName", "Gauze",
                        "unit", "piece", "purchasePrice", BigDecimal.ONE, "highValue", 0));
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(100L))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(101L))).thenReturn(0L);

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "items", List.of(
                        Map.of("productCode", "PC001", "quantity", BigDecimal.ONE),
                        Map.of("productCode", "PC002", "quantity", BigDecimal.ONE)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("科室库房目录");

        verifyNoInteractions(support);
        verify(jdbcTemplate, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO department_requisition_item"),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void mockProduct() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L,
                        "productCode", "PC001",
                        "productName", "Syringe",
                        "unit", "piece",
                        "purchasePrice", BigDecimal.TEN
                ));
    }

    private void mockGeneratedKey(Long key) throws Exception {
        lenient().doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
            keyListField.setAccessible(true);
            keyListField.set(keyHolder, List.of(Map.of("GENERATED_KEY", key)));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }
}
