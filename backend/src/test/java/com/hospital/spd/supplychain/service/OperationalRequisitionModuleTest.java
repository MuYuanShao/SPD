package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.DocumentKind;
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
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalRequisitionModuleTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock SupplyChainSupport support;
    @Mock ApprovalFlowGuard approvalFlowGuard;
    @Mock OperatorContextProvider operatorContextProvider;
    @Mock HighValueTraceFlowService traceFlowService;
    @Mock DepartmentRequisitionAccessService accessService;

    private OperationalRequisitionModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalRequisitionModule(jdbcTemplate, support, approvalFlowGuard,
                operatorContextProvider, traceFlowService, accessService);
        lenient().when(operatorContextProvider.current()).thenReturn(OperatorContext.system());
        lenient().when(accessService.resolveDepartment(any(), any()))
                .thenReturn(Map.of("deptId", 10L, "deptCode", "SURG", "deptName", "Surgery"));
        lenient().when(jdbcTemplate.queryForList(contains("WHERE warehouse_id = ?"), eq(Long.class), eq(20L)))
                .thenReturn(List.of(20L));
        lenient().when(jdbcTemplate.queryForList(contains("warehouse_type LIKE '%中心%'"), eq(Long.class), eq(30L)))
                .thenReturn(List.of(30L));
    }

    @Test
    void createsHighValueRequisitionByQuantityWithoutBindingTraceCode() throws Exception {
        mockProduct("HV001", 100L, BigDecimal.TEN, 1);
        allowCatalog(100L);
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601001");
        mockGeneratedKey(99L);

        Map<String, Object> result = module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "items", List.of(Map.of("productCode", "HV001", "quantity", BigDecimal.valueOf(2),
                        "requisitionMode", "high_value"))));

        assertThat(result).containsEntry("itemCount", 1);
        verify(jdbcTemplate).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), eq(100L), eq(BigDecimal.valueOf(2)), eq("high_value"),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq("piece"), eq(BigDecimal.TEN),
                eq(BigDecimal.valueOf(20)), eq("department requisition"));
        verifyNoInteractions(traceFlowService);
    }

    @Test
    void rejectsHighValueUniqueCodeAtSubmissionBeforeWriting() {
        mockProduct("HV001", 100L, BigDecimal.TEN, 1);
        allowCatalog(100L);

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "HV001", "quantity", BigDecimal.ONE,
                "requisitionMode", "high_value", "uniqueCode", "UDI-HV-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("拣配阶段");

        verify(jdbcTemplate, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verifyNoInteractions(traceFlowService);
    }

    @Test
    void createsLooseRequisitionForAuthorizedDepartment() throws Exception {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        allowCatalog(100L);
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601001");
        mockGeneratedKey(99L);

        Map<String, Object> result = module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "PC001", "quantity", BigDecimal.valueOf(5)));

        assertThat(result).containsEntry("requisitionNo", "SL20260601001")
                .containsEntry("status", "pending_approval");
        verify(accessService).requirePermission("department-requisition:create");
        verify(accessService).requireDestinationWarehouse(10L, 20L);
        verify(jdbcTemplate).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), eq(100L), eq(BigDecimal.valueOf(5)), eq("loose"),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq("piece"), eq(BigDecimal.TEN),
                eq(BigDecimal.valueOf(50)), eq("department requisition"));
    }

    @Test
    void convertsQuotaPackageCountUsingServerTemplateSnapshot() throws Exception {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        allowCatalog(100L);
        when(jdbcTemplate.queryForList(contains("FROM quota_package_template qpt"),
                eq("TP001"), eq(100L), eq(10L))).thenReturn(List.of(Map.of(
                "templateId", 50L, "versionNo", 3,
                "packageQuantity", BigDecimal.TEN, "packageUnit", "piece")));
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601002");
        mockGeneratedKey(99L);

        module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "PC001", "quantity", BigDecimal.valueOf(2),
                "packageCount", BigDecimal.valueOf(2),
                "requisitionMode", "quota_package", "templateCode", "TP001"));

        verify(jdbcTemplate).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), eq(100L), eq(BigDecimal.valueOf(20)), eq("quota_package"),
                eq(50L), eq(3), eq(BigDecimal.TEN), eq("piece"), eq(BigDecimal.valueOf(2)),
                eq("piece"), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(200)),
                eq("department requisition"));
    }

    @Test
    void rejectsTemplateWhenLooseModeWasExplicitlySelected() {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        allowCatalog(100L);

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "PC001", "quantity", BigDecimal.ONE,
                "requisitionMode", "loose", "templateCode", "TP001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("散货申领不得携带");
    }

    @Test
    void rejectsProductOutsideDestinationWarehouseCatalog() {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(100L))).thenReturn(0L);

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "PC001", "quantity", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("科室库房目录");
    }

    @Test
    void createsOneHeaderForMixedLooseQuotaAndHighValueItems() throws Exception {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        mockProduct("PC002", 101L, BigDecimal.valueOf(2), 0);
        mockProduct("HV001", 102L, BigDecimal.valueOf(50), 1);
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), anyLong())).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("FROM quota_package_template qpt"),
                eq("TP001"), eq(101L), eq(10L))).thenReturn(List.of(Map.of(
                "templateId", 50L, "versionNo", 2,
                "packageQuantity", BigDecimal.valueOf(5), "packageUnit", "piece")));
        when(support.nextNo(DocumentKind.DEPARTMENT_REQUISITION)).thenReturn("SL20260601003");
        mockGeneratedKey(99L);

        Map<String, Object> result = module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "items", List.of(
                        Map.of("productCode", "PC001", "quantity", BigDecimal.valueOf(3), "requisitionMode", "loose"),
                        Map.of("productCode", "PC002", "quantity", BigDecimal.valueOf(2), "packageCount", BigDecimal.valueOf(2),
                                "requisitionMode", "quota_package", "templateCode", "TP001"),
                        Map.of("productCode", "HV001", "quantity", BigDecimal.ONE, "requisitionMode", "high_value"))));

        assertThat(result).containsEntry("itemCount", 3);
        verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbcTemplate, times(3)).update(contains("INSERT INTO department_requisition_item"),
                eq(99L), any(), any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq("department requisition"));
    }

    @Test
    void accessFailureStopsBeforeHeaderCreation() {
        doThrow(new AccessDeniedException("无权访问所选科室数据"))
                .when(accessService).resolveDepartment(any(), any());

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptCode", "OTHER", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "productCode", "PC001", "quantity", BigDecimal.ONE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("无权访问");

        verifyNoInteractions(support);
        verify(jdbcTemplate, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void validatesEveryItemBeforeCreatingHeader() {
        mockProduct("PC001", 100L, BigDecimal.TEN, 0);
        mockProduct("PC002", 101L, BigDecimal.ONE, 0);
        allowCatalog(100L);
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(101L))).thenReturn(0L);

        assertThatThrownBy(() -> module.createRequisition(Map.of(
                "deptCode", "SURG", "destinationWarehouseId", 20L, "sourceWarehouseId", 30L,
                "items", List.of(
                        Map.of("productCode", "PC001", "quantity", BigDecimal.ONE),
                        Map.of("productCode", "PC002", "quantity", BigDecimal.ONE)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("科室库房目录");

        verifyNoInteractions(support);
        verify(jdbcTemplate, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    private void allowCatalog(Long productId) {
        when(jdbcTemplate.queryForObject(contains("FROM department_warehouse_catalog"), eq(Long.class),
                eq(10L), eq(20L), eq(productId))).thenReturn(1L);
    }

    private void mockProduct(String code, Long id, BigDecimal price, int highValue) {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), eq(code)))
                .thenReturn(Map.of("productId", id, "productCode", code, "productName", code,
                        "unit", "piece", "purchasePrice", price, "highValue", highValue,
                        "quotaManaged", highValue == 0 ? 1 : 0));
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
