package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.DepartmentWarehouseCatalogBatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
