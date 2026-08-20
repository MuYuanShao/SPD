package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import com.hospital.spd.masterdata.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Note: use any(Object[].class) instead of any(Object[].class) for varargs stubs
// to ensure proper Mockito 5 varargs matching behavior

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DocumentNumberService documentNumberService;

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(jdbcTemplate, documentNumberService);
    }

    @Nested
    @DisplayName("分页查询供应商 suppliers()")
    class SuppliersTest {

        @Test
        @DisplayName("无筛选条件分页查询全部供应商")
        void should_return_paginated_suppliers_when_no_filters() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(30L);

            List<Map<String, Object>> rows = List.of(
                    Map.of("code", "SUP001", "name", "供应商A", "type", "配送商",
                            "contactName", "张三", "contactPhone", "13800138001",
                            "status", "启用"),
                    Map.of("code", "SUP002", "name", "供应商B", "type", "生产商",
                            "contactName", "李四", "contactPhone", "13800138002",
                            "status", "启用")
            );
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(rows);

            MasterDataPage result = service.suppliers(Map.of());

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("供应商管理");
            assertThat(result.total()).isEqualTo(30);
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0)).containsEntry("code", "SUP001");
            assertThat(result.columns()).contains("供应商编码", "供应商名称", "联系人");
        }

        @Test
        @DisplayName("按名称和类型筛选供应商")
        void should_filter_suppliers_by_name_and_type() {
            Map<String, String> params = Map.of(
                    "supplierName", "供应商A",
                    "supplierType", "配送商"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(5L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "SUP001", "name", "供应商A", "type", "配送商")
                    ));

            MasterDataPage result = service.suppliers(params);

            assertThat(result.total()).isEqualTo(5);
            assertThat(result.rows()).hasSize(1);
        }

        @Test
        @DisplayName("按状态筛选供应商")
        void should_filter_suppliers_by_status() {
            Map<String, String> params = Map.of("status", "启用");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.suppliers(params);

            assertThat(result.total()).isEqualTo(10);
        }

        @Test
        @DisplayName("查询结果为空时返回空列表")
        void should_return_empty_list_when_no_suppliers() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.suppliers(Map.of());

            assertThat(result.total()).isZero();
            assertThat(result.rows()).isEmpty();
        }
    }

    @Nested
    @DisplayName("创建供应商 createSupplier()")
    class CreateSupplierTest {

        private SupplierUpsertRequest validRequest() {
            return new SupplierUpsertRequest(
                    "MANUAL-SUP", "供应商A", "91110105MA01ABCD2X", "沪食药监械经营许20260001号",
                    "配送商", "A级", "张三", "13800138001",
                    "test@example.com", "北京市朝阳区"
            );
        }

        @Test
        @DisplayName("创建供应商成功返回编码")
        void should_create_supplier_successfully() {
            SupplierUpsertRequest request = validRequest();

            when(documentNumberService.next(DocumentKind.SUPPLIER)).thenReturn("SUP2026072800001");
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createSupplier(request);

            assertThat(result.get("supplierCode")).isEqualTo("SUP2026072800001");
            verify(jdbcTemplate).update(anyString(),
                    eq("SUP2026072800001"), eq("供应商A"), eq("91110105MA01ABCD2X"), eq("沪食药监械经营许20260001号"),
                    eq("配送商"), eq("A级"), eq("张三"), eq("13800138001"),
                    eq("test@example.com"), eq("北京市朝阳区"));
            verify(documentNumberService).next(DocumentKind.SUPPLIER);
        }

        @Test
        @DisplayName("统一社会信用代码超过18位时在执行数据库操作前拒绝")
        void should_reject_overlong_credit_code_before_database_update() {
            SupplierUpsertRequest request = new SupplierUpsertRequest(
                    "SUP001", "供应商A", "91110105MA01ABCD2X9", null,
                    "配送商", null, "张三", "13800138001",
                    null, null
            );

            assertThatThrownBy(() -> service.createSupplier(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("统一社会信用代码必须为18位标准代码（大写字母或数字）");
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("缺少必填字段抛出异常")
        void should_throw_when_required_fields_missing() {
            SupplierUpsertRequest request = new SupplierUpsertRequest(
                    "", "供应商A", "", "", "",
                    null, "张三", "",
                    null, null
            );

            assertThatThrownBy(() -> service.createSupplier(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("必填项");
        }

        @Test
        @DisplayName("供应商名称为空抛出异常")
        void should_throw_when_supplier_name_missing() {
            SupplierUpsertRequest request = new SupplierUpsertRequest(
                    "SUP001", "", "91110000MA001", null,
                    "配送商", null, "张三", "13800138001",
                    null, null
            );

            assertThatThrownBy(() -> service.createSupplier(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("必填项");
        }
    }

    @Nested
    @DisplayName("更新供应商状态 updateSupplierStatus()")
    class UpdateSupplierStatusTest {

        @Test
        @DisplayName("批量停用供应商成功")
        void should_update_status_when_valid_request() {
            SupplierStatusUpdateRequest request = new SupplierStatusUpdateRequest(
                    List.of("SUP001", "SUP002"), 0
            );

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(2);

            Map<String, Object> result = service.updateSupplierStatus(request);

            assertThat(result.get("updatedRows")).isEqualTo(2);
        }

        @Test
        @DisplayName("批量启用供应商成功")
        void should_enable_suppliers_when_status_is_1() {
            SupplierStatusUpdateRequest request = new SupplierStatusUpdateRequest(
                    List.of("SUP001"), 1
            );

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.updateSupplierStatus(request);

            assertThat(result.get("updatedRows")).isEqualTo(1);
            verify(jdbcTemplate).update(argThat(sql -> sql != null && ((String) sql).contains("UPDATE supplier")),
                    eq(1), eq("SUP001"));
        }

        @Test
        @DisplayName("空供应商编码列表抛出异常")
        void should_throw_when_supplier_codes_empty() {
            SupplierStatusUpdateRequest request = new SupplierStatusUpdateRequest(List.of(), 0);

            assertThatThrownBy(() -> service.updateSupplierStatus(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要停用的供应商");
        }

        @Test
        @DisplayName("null供应商编码列表抛出异常")
        void should_throw_when_supplier_codes_null() {
            SupplierStatusUpdateRequest request = new SupplierStatusUpdateRequest(null, 0);

            assertThatThrownBy(() -> service.updateSupplierStatus(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要停用的供应商");
        }

        @Test
        @DisplayName("status为null时默认停用")
        void should_default_to_disabled_when_status_is_null() {
            SupplierStatusUpdateRequest request = new SupplierStatusUpdateRequest(
                    List.of("SUP001"), null
            );

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.updateSupplierStatus(request);

            assertThat(result.get("updatedRows")).isEqualTo(1);
            verify(jdbcTemplate).update(argThat(sql -> sql != null && ((String) sql).contains("UPDATE supplier")),
                    eq(0), eq("SUP001"));
        }
    }
}
