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
import org.springframework.jdbc.support.KeyHolder;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalDeliveryModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalDeliveryModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalDeliveryModule(jdbcTemplate, support);
    }

    @Test
    void createsDeliveryFromExplicitValues() {
        mockProduct("PC001", "Syringe", 100L);
        when(support.nextNo(DocumentKind.DELIVERY_ORDER)).thenReturn("PS20260601001");

        Map<String, Object> result = module.createDelivery(Map.of(
                "requisitionNo", "SL001",
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "productCode", "PC001",
                "quantity", BigDecimal.valueOf(20)
        ));

        assertThat(result)
                .containsEntry("deliveryNo", "PS20260601001")
                .containsEntry("status", "picked");
        verify(jdbcTemplate).update(contains("INSERT INTO spd_delivery_order"),
                eq("PS20260601001"), eq("SL001"), eq("Surgery"), eq("Main Warehouse"),
                eq("PC001"), eq("Syringe"), eq(BigDecimal.valueOf(20)));
    }

    @Test
    void createsDeliveryWithNullRequisitionNumber() {
        mockProduct("PC001", "Syringe", 100L);
        when(support.nextNo(DocumentKind.DELIVERY_ORDER)).thenReturn("PS20260601002");

        Map<String, Object> result = module.createDelivery(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "productCode", "PC001",
                "quantity", BigDecimal.ONE
        ));

        assertThat(result).containsEntry("deliveryNo", "PS20260601002");
        verify(jdbcTemplate).update(contains("INSERT INTO spd_delivery_order"),
                eq("PS20260601002"), isNull(), eq("Surgery"), eq("Main Warehouse"),
                eq("PC001"), eq("Syringe"), eq(BigDecimal.ONE));
    }

    @Test
    void picksOneQuotaPackageWhenRequisitionQuantityIsOnePackage() {
        when(jdbcTemplate.queryForList(contains("FROM department_requisition dr"),
                eq("SL001"), eq(4L)))
                .thenReturn(List.of(Map.of(
                        "requisitionId", 3L,
                        "deptName", "Surgery",
                        "productId", 100L,
                        "quantity", BigDecimal.ONE
                )));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"),
                eq(Long.class), eq("Main Warehouse")))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label qpl"),
                any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "labelId", 8L,
                        "labelNo", "D001",
                        "status", "available",
                        "warehouseId", 1L,
                        "productId", 100L,
                        "packageQuantity", BigDecimal.TEN,
                        "productCode", "PC001",
                        "productName", "Syringe"
                )));
        when(jdbcTemplate.queryForObject(contains("delivery_type = 'loose'"),
                eq(BigDecimal.class), eq(4L), eq(4L), eq(4L)))
                .thenReturn(BigDecimal.ZERO);
        when(support.nextNo(DocumentKind.DELIVERY_ORDER)).thenReturn("PS001");
        when(support.nextNo(DocumentKind.QUOTA_PACKAGE_EVENT)).thenReturn("DS001");
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 9L));
                    return 1;
                });
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(GREATEST"),
                eq(BigDecimal.class), eq(3L)))
                .thenReturn(BigDecimal.ZERO);

        Map<String, Object> result = module.confirmPicking(Map.of(
                "requisitionNo", "SL001",
                "itemId", 4L,
                "warehouseName", "Main Warehouse",
                "labelNos", List.of("D001")
        ));

        assertThat(result)
                .containsEntry("deliveryNo", "PS001")
                .containsEntry("labelCount", 1)
                .containsEntry("quantity", BigDecimal.TEN);
    }

    @Test
    void signsDeliveryThroughInventoryMovementBeforeStatusUpdate() {
        mockPickedDelivery();
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), anyString()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq(1L)))
                .thenReturn(List.of(2L));
        mockProduct("PC001", "Syringe", 100L);
        when(support.transferAvailableFifo(anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyLong(), anyString()))
                .thenReturn(List.of(new SupplyChainSupport.InventoryDeduction(200L, BigDecimal.TEN, BigDecimal.valueOf(5))));
        when(jdbcTemplate.update(contains("UPDATE spd_delivery_order SET status = 'signed'"), eq(2L), eq("PS001")))
                .thenReturn(1);

        Map<String, Object> result = module.signDelivery("PS001");

        assertThat(result)
                .containsEntry("deliveryNo", "PS001")
                .containsEntry("status", "signed");
        verify(support).transferAvailableFifo(eq(1L), eq(2L), eq(100L), eq(BigDecimal.TEN),
                eq("spd_delivery_order"), eq(10L), eq("delivery sign transfers inventory to department warehouse"));
    }

    @Test
    void signsQuotaPackageIntoDepartmentInventoryBySourceBatch() {
        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), eq("PS001")))
                .thenReturn(Map.of(
                        "deliveryId", 10L,
                        "deliveryNo", "PS001",
                        "warehouseName", "Main Warehouse",
                        "deptName", "Surgery",
                        "productCode", "PC001",
                        "quantity", BigDecimal.TEN,
                        "status", "picked"
                ));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), anyString()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq(1L)))
                .thenReturn(List.of(2L));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM spd_delivery_package_binding"),
                eq(Integer.class), eq(10L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM spd_delivery_trace_code"),
                eq(Integer.class), eq(10L)))
                .thenReturn(0);
        mockProduct("PC001", "Syringe", 100L);
        when(jdbcTemplate.queryForList(contains("JOIN quota_package_label_source"), eq(10L)))
                .thenReturn(List.of(Map.of(
                        "labelId", 8L,
                        "productId", 100L,
                        "batchId", 200L,
                        "sourceQty", BigDecimal.TEN
                )));
        when(support.nextNo(DocumentKind.QUOTA_PACKAGE_EVENT)).thenReturn("DS001");
        org.mockito.Mockito.lenient().when(jdbcTemplate.update(contains("UPDATE spd_delivery_order SET status = 'signed'"), eq(2L), eq("PS001")))
                .thenReturn(1);

        Map<String, Object> result = module.signDelivery("PS001");

        assertThat(result).containsEntry("status", "signed");
        verify(support).receiveAvailable(eq(2L), eq(100L), eq(200L), eq(BigDecimal.TEN),
                eq("quota_package_delivery_sign_in"), eq("spd_delivery_order"), eq(10L),
                eq("signed quota package received into department warehouse"));
        verify(jdbcTemplate).update(contains("UPDATE quota_package_label SET warehouse_id"), eq(2L), eq(8L));
    }
    @Test
    void rejectsAlreadySignedDeliveryBeforeInventoryMovement() {
        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), eq("PS001")))
                .thenReturn(Map.of("deliveryId", 10L, "deliveryNo", "PS001", "status", "signed"));

        assertThatThrownBy(() -> module.signDelivery("PS001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only picked delivery");
        verify(support, never()).transferAvailableFifo(anyLong(), anyLong(), anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void doesNotMarkSignedWhenInventoryMovementFails() {
        mockPickedDelivery();
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), anyString()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq(1L)))
                .thenReturn(List.of(2L));
        mockProduct("PC001", "Syringe", 100L);
        when(support.transferAvailableFifo(anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("inventory is insufficient"));

        assertThatThrownBy(() -> module.signDelivery("PS001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inventory is insufficient");
        verify(jdbcTemplate, never()).update(contains("UPDATE spd_delivery_order SET status = 'signed'"), anyLong(), anyString());
    }

    @Test
    void explainsMissingDepartmentWarehouseConfigurationInChinese() {
        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), eq("PS001")))
                .thenReturn(Map.of(
                        "deliveryId", 10L,
                        "deliveryNo", "PS001",
                        "warehouseName", "Main Warehouse",
                        "deptName", "Surgery",
                        "productCode", "PC001",
                        "quantity", BigDecimal.TEN,
                        "status", "picked"
                ));
        when(jdbcTemplate.queryForObject(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), anyString()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept"), eq(Long.class), eq("Surgery"), eq(1L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> module.signDelivery("PS001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("科室“Surgery”未配置对应的科室库房，请先完成科室库房关联");
        verify(support, never()).transferAvailableFifo(anyLong(), anyLong(), anyLong(), any(), anyString(), anyLong(), anyString());
    }

    private void mockPickedDelivery() {
        when(jdbcTemplate.queryForMap(contains("FROM spd_delivery_order WHERE delivery_no = ?"), eq("PS001")))
                .thenReturn(Map.of(
                        "deliveryId", 10L,
                        "deliveryNo", "PS001",
                        "warehouseName", "Main Warehouse", "deptName", "Surgery",
                        "productCode", "PC001",
                        "quantity", BigDecimal.TEN,
                        "status", "picked"
                ));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM spd_delivery_package_binding"),
                eq(Integer.class), eq(10L)))
                .thenReturn(0);
    }

    private void mockProduct(String code, String name, Long productId) {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", productId, "productCode", code, "productName", name));
    }
}
