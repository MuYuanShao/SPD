package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.DepartmentWarehouseCatalogBatchRequest;
import com.hospital.spd.masterdata.DepartmentWarehouseCatalogUpsertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentWarehouseCatalogServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DepartmentWarehouseCatalogService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentWarehouseCatalogService(jdbcTemplate);
    }

    @Test
    void batchMaintainCatalogsCreatesUpdatesAndRestoresEntries() {
        when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("手术室")))
                .thenReturn(List.of(7L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id, dept_id"), eq("手术室二级库")))
                .thenReturn(List.of(Map.of("warehouse_id", 11L, "dept_id", 7L)));
        when(jdbcTemplate.queryForList(contains("FROM product"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("product_id", 101L, "product_code", "P001"),
                        Map.of("product_id", 102L, "product_code", "P002"),
                        Map.of("product_id", 103L, "product_code", "P003")
                ));
        when(jdbcTemplate.queryForList(contains("FROM department_warehouse_catalog"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("catalog_id", 21L, "product_id", 101L, "deleted", 0),
                        Map.of("catalog_id", 22L, "product_id", 102L, "deleted", 1)
                ));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.batchMaintainCatalogs(
                new DepartmentWarehouseCatalogBatchRequest(
                        "手术室", "手术室二级库", List.of("P001", "P002", "P003", "P003"), 1
                )
        );

        assertThat(result)
                .containsEntry("createdRows", 1)
                .containsEntry("updatedRows", 1)
                .containsEntry("restoredRows", 1)
                .containsEntry("totalRows", 3);
        verify(jdbcTemplate, times(2)).update(contains("SET source_type = 'manual'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("INSERT INTO department_warehouse_catalog"), any(Object[].class));
        verify(jdbcTemplate).queryForList(contains("JOIN warehouse_product_binding"), any(Object[].class));
    }

    @Test
    void batchMaintainCatalogsRejectsMissingProductsBeforeWriting() {
        when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("手术室")))
                .thenReturn(List.of(7L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id, dept_id"), eq("手术室二级库")))
                .thenReturn(List.of(Map.of("warehouse_id", 11L, "dept_id", 7L)));
        when(jdbcTemplate.queryForList(contains("FROM product"), any(Object[].class)))
                .thenReturn(List.of(Map.of("product_id", 101L, "product_code", "P001")));

        assertThatThrownBy(() -> service.batchMaintainCatalogs(
                new DepartmentWarehouseCatalogBatchRequest(
                        "手术室", "手术室二级库", List.of("P001", "P404"), 1
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P404");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createCatalogRejectsWarehouseThatIsNotLinkedToDepartmentBeforeWriting() {
        when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("手术室")))
                .thenReturn(List.of(7L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id, dept_id"), eq("供应室库房")))
                .thenReturn(List.of(Map.of("warehouse_id", 11L, "dept_id", 8L)));

        assertThatThrownBy(() -> service.createCatalog(
                new DepartmentWarehouseCatalogUpsertRequest("手术室", "供应室库房", "P001", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("库房未关联当前科室");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createCatalogRejectsProductThatIsNotBoundToWarehouseBeforeWriting() {
        when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("手术室")))
                .thenReturn(List.of(7L));
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id, dept_id"), eq("手术室二级库")))
                .thenReturn(List.of(Map.of("warehouse_id", 11L, "dept_id", 7L)));
        when(jdbcTemplate.queryForList(contains("SELECT product_id FROM product"), eq(Long.class), eq("P001")))
                .thenReturn(List.of(101L));
        when(jdbcTemplate.queryForList(contains("FROM warehouse_product_binding"), eq(Long.class),
                eq(11L), eq(101L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.createCatalog(
                new DepartmentWarehouseCatalogUpsertRequest("手术室", "手术室二级库", "P001", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("商品未绑定当前库房");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void catalogProductOptionsUseActiveWarehouseBindingsAndExcludeExistingCatalogRows() {
        List<String> executedSql = new ArrayList<>();
        JdbcTemplate optionsJdbc = mock(JdbcTemplate.class, invocation -> {
            if (!"queryForList".equals(invocation.getMethod().getName())) {
                return RETURNS_DEFAULTS.answer(invocation);
            }
            String sql = invocation.getArgument(0);
            executedSql.add(sql);
            if (sql.contains("SELECT dept_id FROM sys_dept")) {
                return List.of(Map.of("dept_id", 7L));
            }
            if (sql.contains("SELECT warehouse_id FROM warehouse")) {
                return List.of(Map.of("warehouse_id", 11L));
            }
            return List.of();
        });
        DepartmentWarehouseCatalogService optionsService = new DepartmentWarehouseCatalogService(optionsJdbc);

        optionsService.catalogProductOptions("Surgery", "Surgery Warehouse", "Syringe");

        assertThat(executedSql).anyMatch(sql ->
                sql.contains("JOIN warehouse_product_binding")
                        && sql.contains("b.deleted = 0")
                        && sql.contains("b.status = 1")
                        && sql.contains("NOT EXISTS")
                        && sql.contains("dwc.deleted = 0"));
    }
}
