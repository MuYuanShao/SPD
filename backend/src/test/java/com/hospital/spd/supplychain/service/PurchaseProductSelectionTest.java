package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseProductSelectionTest {
    @Mock JdbcTemplate jdbc;
    @Mock SupplyChainSupport support;
    PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(jdbc, support);
    }

    @Test
    void hospitalSelectionIgnoresDepartmentAndSupplierAndPaginates() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(250L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("productCode", "P001")));
        Map<String, Object> result = service.listSelectableProducts(Map.of(
                "scope", "hospital", "deptCode", "OTHER", "supplierName", "OTHER", "page", "2", "size", "20"));
        assertThat(result.get("total")).isEqualTo(250L);
        assertThat(result.get("page")).isEqualTo(2);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).doesNotContain("department_warehouse_catalog", "supplier");
        assertThat(args.getValue()).containsExactly(20, 20);
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void departmentSelectionRequiresExplicitDepartment() {
        assertThatThrownBy(() -> service.listSelectableProducts(Map.of("scope", "department")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目前没有选择发起科室，请先选择发起科室");
        verifyNoInteractions(jdbc);
    }

    @Test
    void departmentSelectionUsesResolvedDepartmentAndActiveCatalogWithoutDuplicatingProducts() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("productCode", "P001")));
        Map<String, Object> result = service.listSelectableProducts(Map.of("scope", "department", "deptCode", "SURG"));
        assertThat(result.get("rows")).asList().hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("EXISTS", "dwc.dept_id = ?", "dwc.deleted = 0", "dwc.status = 1");
        assertThat(args.getValue()[0]).isEqualTo(7L);
    }

    @Test
    void departmentOutsideAuthorizedScopeIsRejected() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.listSelectableProducts(Map.of("scope", "department", "deptCode", "OTHER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出当前操作人的数据范围");
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }
}
