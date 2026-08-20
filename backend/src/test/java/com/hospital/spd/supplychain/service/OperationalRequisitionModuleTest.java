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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalRequisitionModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalRequisitionModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalRequisitionModule(jdbcTemplate, support);
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
                eq(99L), eq(100L), eq(BigDecimal.valueOf(5)), eq("piece"), eq(BigDecimal.TEN),
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
