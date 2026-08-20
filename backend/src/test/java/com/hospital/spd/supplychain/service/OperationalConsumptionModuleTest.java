package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
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

    private OperationalConsumptionModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalConsumptionModule(jdbcTemplate, support);
    }

    @Test
    void createsConsumptionFromExplicitValues() throws Exception {
        mockExistingDepartment(10L);
        when(jdbcTemplate.queryForObject(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq("Department Warehouse")))
                .thenReturn(20L);
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
        when(jdbcTemplate.queryForObject(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq("Department Warehouse")))
                .thenReturn(20L);
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
        when(jdbcTemplate.queryForMap(contains("FROM department_consumption"), eq("XH001")))
                .thenReturn(Map.of("consumptionId", 1L, "warehouseId", 20L, "status", "confirmed"));
        when(jdbcTemplate.queryForList(contains("FROM department_consumption_item"), eq(1L)))
                .thenReturn(List.of(Map.of("productId", 100L, "batchId", 300L, "quantity", BigDecimal.TEN)));
        when(jdbcTemplate.update(contains("UPDATE department_consumption SET status = 'reversed'"), eq("XH001")))
                .thenReturn(1);
        when(support.nextNo(DocumentKind.CONSUMPTION_RED_FLUSH)).thenReturn("FXH20260601001");

        Map<String, Object> result = module.reverseConsumption("XH001");

        assertThat(result)
                .containsEntry("flushNo", "FXH20260601001")
                .containsEntry("status", "approved");
        InOrder ordered = inOrder(jdbcTemplate, support);
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

    private void mockExistingDepartment(Long deptId) {
        when(jdbcTemplate.queryForList(eq("SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1"),
                eq(Long.class), anyString())).thenReturn(List.of(deptId));
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
}
