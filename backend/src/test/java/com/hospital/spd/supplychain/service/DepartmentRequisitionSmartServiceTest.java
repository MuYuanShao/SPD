package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.GenerateItem;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.GenerateRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentRequisitionSmartServiceTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock OperationalRequisitionModule requisitionModule;
    @Mock DepartmentRequisitionAccessService accessService;
    @Mock OperatorContextProvider operatorContextProvider;
    @Mock SupplyChainSupport support;

    private DepartmentRequisitionSmartService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentRequisitionSmartService(jdbcTemplate, requisitionModule, accessService,
                operatorContextProvider, support);
        lenient().when(operatorContextProvider.current()).thenReturn(OperatorContext.system());
        lenient().when(jdbcTemplate.queryForList(contains("FROM replenishment_smart_analysis WHERE"), eq(88L)))
                .thenReturn(List.of(Map.of("analysisId", 88L, "createdBy", 1L, "status", "draft")));
    }

    @Test
    void recordsSelectedAndDeselectedRowsAndCreatesOnlySelectedGroup() {
        when(jdbcTemplate.queryForList(contains("FROM replenishment_smart_analysis_item"), eq(88L)))
                .thenReturn(List.of(
                        Map.of("analysisItemId", 1L, "deptId", 10L, "warehouseId", 20L,
                                "sourceWarehouseId", 30L, "productCode", "PC001", "itemMode", "loose"),
                        Map.of("analysisItemId", 2L, "deptId", 10L, "warehouseId", 20L,
                                "sourceWarehouseId", 30L, "productCode", "HV001", "itemMode", "high_value")));
        when(jdbcTemplate.queryForMap(contains("FROM sys_dept WHERE dept_id = ?"), eq(10L)))
                .thenReturn(Map.of("deptCode", "SURG", "deptName", "Surgery"));
        when(requisitionModule.createRequisition(any())).thenReturn(Map.of("requisitionNo", "SL001"));
        when(jdbcTemplate.queryForObject(contains("FROM department_requisition WHERE requisition_no"),
                eq(Long.class), eq("SL001"))).thenReturn(101L);

        Map<String, Object> result = service.generate(new GenerateRequest(88L, List.of(
                new GenerateItem(1L, BigDecimal.valueOf(7), true),
                new GenerateItem(2L, BigDecimal.ZERO, false))));

        assertThat(result).containsEntry("createdCount", 1).containsEntry("status", "generated");
        verify(jdbcTemplate).update(contains("SET selected = ?, manual_adjusted_qty = ?"),
                eq(1), eq(BigDecimal.valueOf(7)), eq(1L));
        verify(jdbcTemplate).update(contains("SET selected = ?, manual_adjusted_qty = ?"),
                eq(0), eq(BigDecimal.ZERO), eq(2L));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(requisitionModule).createRequisition(payload.capture());
        assertThat((List<Map<String, Object>>) payload.getValue().get("items"))
                .singleElement().satisfies(item -> assertThat(item)
                        .containsEntry("productCode", "PC001")
                        .containsEntry("quantity", BigDecimal.valueOf(7)));
    }

    @Test
    void quotaAdjustmentIsPackageCountAndUsesCurrentTemplate() {
        when(jdbcTemplate.queryForList(contains("FROM replenishment_smart_analysis_item"), eq(88L)))
                .thenReturn(List.of(Map.of("analysisItemId", 3L, "deptId", 10L, "warehouseId", 20L,
                        "sourceWarehouseId", 30L, "productCode", "PC002", "itemMode", "quota_package",
                        "quotaTemplateId", 50L, "packageQuantity", BigDecimal.TEN)));
        when(jdbcTemplate.queryForObject(contains("SELECT template_code FROM quota_package_template"),
                eq(String.class), eq(50L))).thenReturn("TP001");
        when(jdbcTemplate.queryForMap(contains("FROM sys_dept WHERE dept_id = ?"), eq(10L)))
                .thenReturn(Map.of("deptCode", "SURG", "deptName", "Surgery"));
        when(requisitionModule.createRequisition(any())).thenReturn(Map.of("requisitionNo", "SL002"));
        when(jdbcTemplate.queryForObject(contains("FROM department_requisition WHERE requisition_no"),
                eq(Long.class), eq("SL002"))).thenReturn(102L);

        service.generate(new GenerateRequest(88L,
                List.of(new GenerateItem(3L, BigDecimal.valueOf(3), true))));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(requisitionModule).createRequisition(payload.capture());
        assertThat((List<Map<String, Object>>) payload.getValue().get("items"))
                .singleElement().satisfies(item -> assertThat(item)
                        .containsEntry("requisitionMode", "quota_package")
                        .containsEntry("templateCode", "TP001")
                        .containsEntry("packageCount", BigDecimal.valueOf(3))
                        .containsEntry("quantity", BigDecimal.valueOf(30)));
    }

    @Test
    void rejectsOmittedAnalysisRowsBeforeAnyRequisitionIsCreated() {
        when(jdbcTemplate.queryForList(contains("FROM replenishment_smart_analysis_item"), eq(88L)))
                .thenReturn(List.of(
                        Map.of("analysisItemId", 1L, "deptId", 10L, "warehouseId", 20L,
                                "sourceWarehouseId", 30L, "productCode", "PC001", "itemMode", "loose"),
                        Map.of("analysisItemId", 2L, "deptId", 10L, "warehouseId", 20L,
                                "sourceWarehouseId", 30L, "productCode", "PC002", "itemMode", "loose")));

        assertThatThrownBy(() -> service.generate(new GenerateRequest(88L,
                List.of(new GenerateItem(1L, BigDecimal.ONE, true)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("全部明细");

        verifyNoInteractions(requisitionModule);
        verify(jdbcTemplate, never()).update(contains("manual_adjusted_qty"), any(), any(), any());
    }

    @Test
    void rejectsNonPositiveSelectedQuantityBeforeWritingAdjustments() {
        when(jdbcTemplate.queryForList(contains("FROM replenishment_smart_analysis_item"), eq(88L)))
                .thenReturn(List.of(Map.of("analysisItemId", 1L, "deptId", 10L, "warehouseId", 20L,
                        "sourceWarehouseId", 30L, "productCode", "PC001", "itemMode", "loose")));

        assertThatThrownBy(() -> service.generate(new GenerateRequest(88L,
                List.of(new GenerateItem(1L, BigDecimal.ZERO, true)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须大于零");

        verifyNoInteractions(requisitionModule);
        verify(jdbcTemplate, never()).update(contains("manual_adjusted_qty"), any(), any(), any());
    }
}
