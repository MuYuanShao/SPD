package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.DepartmentWarehouseRelationRequest;
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
class WarehouseDepartmentAssignmentServiceTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock WarehouseCatalogBindingService catalogBindingService;
    WarehouseDepartmentAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseDepartmentAssignmentService(jdbcTemplate, catalogBindingService);
    }

    @Test
    void rejectsUnknownWarehouseBeforeAnyWrite() {
        when(jdbcTemplate.queryForList(contains("dept_id = ?"), eq(7L))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("warehouse_code IN"), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(7L,
                new DepartmentWarehouseRelationRequest(List.of("WH404"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsTakingWarehouseOwnedByAnotherDepartment() {
        when(jdbcTemplate.queryForList(contains("dept_id = ?"), eq(7L))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("warehouse_code IN"), any(Object[].class))).thenReturn(List.of(
                Map.of("warehouse_id", 11L, "warehouse_code", "WH02", "warehouse_type", "二级库", "dept_id", 8L)));

        assertThatThrownBy(() -> service.replace(7L,
                new DepartmentWarehouseRelationRequest(List.of("WH02"))))
                .hasMessageContaining("已归属其他科室");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void unbindsOldAndSynchronizesNewCatalogAtomically() {
        when(jdbcTemplate.queryForList(contains("dept_id = ?"), eq(7L))).thenReturn(List.of(
                Map.of("warehouse_id", 10L, "warehouse_code", "OLD", "dept_id", 7L)));
        when(jdbcTemplate.queryForList(contains("warehouse_code IN"), any(Object[].class))).thenReturn(List.of(
                Map.of("warehouse_id", 11L, "warehouse_code", "NEW", "warehouse_type", "科室库")));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.replace(7L,
                new DepartmentWarehouseRelationRequest(List.of("NEW")));

        assertThat(result).containsEntry("updatedRows", 2).containsEntry("warehouseCount", 1);
        verify(catalogBindingService).synchronizeDepartmentCatalog(10L, null);
        verify(catalogBindingService).synchronizeDepartmentCatalog(11L, 7L);
    }
}
