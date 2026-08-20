package com.hospital.spd.masterdata.service;

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

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private WarehouseService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseService(jdbcTemplate);
    }

    @Nested
    @DisplayName("分页查询库房 warehouses()")
    class WarehousesTest {

        @Test
        @DisplayName("无筛选条件分页查询全部库房")
        void should_return_paginated_warehouses_when_no_filters() {
            doReturn(25L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class));

            List<Map<String, Object>> rows = List.of(
                    Map.of("code", "WH001", "name", "中心库", "type", "中心库",
                            "campus", "主院区", "dept", "药剂科",
                            "status", "启用", "locationCount", 10),
                    Map.of("code", "WH002", "name", "内科二级库", "type", "科室库",
                            "campus", "主院区", "dept", "内科",
                            "status", "启用", "locationCount", 5)
            );
            doReturn(rows).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(Map.of());

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("库房 / 货位管理");
            assertThat(result.total()).isEqualTo(25);
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0)).containsEntry("code", "WH001");
            assertThat(result.columns()).contains("库房编码", "库房名称", "库房类型");
        }

        @Test
        @DisplayName("按关键词搜索库房")
        void should_filter_warehouses_by_keyword() {
            Map<String, String> params = Map.of("warehouseKeyword", "中心库");

            doReturn(3L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
            doReturn(List.of(
                    Map.of("code", "WH001", "name", "中心库")
            )).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(params);

            assertThat(result.total()).isEqualTo(3);
        }

        @Test
        @DisplayName("按库房类型和院区筛选")
        void should_filter_warehouses_by_type_and_campus() {
            Map<String, String> params = Map.of(
                    "warehouseType", "中心库",
                    "campusName", "主院区"
            );

            doReturn(2L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
            doReturn(List.of(
                    Map.of("code", "WH001", "name", "中心库")
            )).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(params);

            assertThat(result.total()).isEqualTo(2);
        }

        @Test
        @DisplayName("按关联科室筛选")
        void should_filter_warehouses_by_department() {
            Map<String, String> params = Map.of("deptName", "药剂科");

            doReturn(4L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
            doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(params);

            assertThat(result.total()).isEqualTo(4);
        }

        @Test
        @DisplayName("按状态筛选库房")
        void should_filter_warehouses_by_status() {
            Map<String, String> params = Map.of("status", "启用");

            doReturn(20L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
            doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(params);

            assertThat(result.total()).isEqualTo(20);
        }

        @Test
        @DisplayName("查询结果为空时返回空列表")
        void should_return_empty_list_when_no_warehouses() {
            doReturn(0L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class));
            doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

            MasterDataPage result = service.warehouses(Map.of());

            assertThat(result.total()).isZero();
            assertThat(result.rows()).isEmpty();
        }
    }

    @Nested
    @DisplayName("创建库房 createWarehouse()")
    class CreateWarehouseTest {

        private WarehouseUpsertRequest validRequest() {
            return new WarehouseUpsertRequest(
                    "WH001", "中心库", "中心库",
                    "主院区", "药剂科", true, "高值耗材", 1, null
            );
        }

        @Test
        @DisplayName("创建库房成功返回编码")
        void should_create_warehouse_successfully() {
            WarehouseUpsertRequest request = validRequest();

            // mock findIdByName for deptName
            doReturn(List.of(100L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("dept_id FROM sys_dept")),
                    eq(Long.class), anyString());

            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createWarehouse(request);

            assertThat(result.get("warehouseCode")).isEqualTo("WH001");
        }

        @Test
        @DisplayName("缺少必填字段抛出异常")
        void should_throw_when_required_fields_missing() {
            WarehouseUpsertRequest request = new WarehouseUpsertRequest(
                    "", "", "", "", null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createWarehouse(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("库房编码、库房名称、库房类型、所属院区为必填项");
        }

        @Test
        @DisplayName("库房名称缺失抛出异常")
        void should_throw_when_warehouse_name_missing() {
            WarehouseUpsertRequest request = new WarehouseUpsertRequest(
                    "WH001", "", "中心库", "主院区", null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createWarehouse(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("库房编码、库房名称、库房类型、所属院区为必填项");
        }

        @Test
        @DisplayName("创建库房时关联科室不存在则deptId为null")
        void should_set_null_dept_id_when_department_not_found() {
            WarehouseUpsertRequest request = new WarehouseUpsertRequest(
                    "WH002", "内科二级库", "科室库",
                    "主院区", "不存在科室", false, null, null, null
            );

            doReturn(List.of()).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("dept_id FROM sys_dept")),
                    eq(Long.class), anyString());

            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createWarehouse(request);

            assertThat(result.get("warehouseCode")).isEqualTo("WH002");
        }

        @Test
        @DisplayName("participateStats为false时存0")
        void should_store_zero_when_participate_stats_false() {
            WarehouseUpsertRequest request = new WarehouseUpsertRequest(
                    "WH003", "虚拟库", "虚拟库",
                    "主院区", null, false, null, null, null
            );

            doReturn(List.of()).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("dept_id FROM sys_dept")),
                    eq(Long.class), anyString());

            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createWarehouse(request);

            assertThat(result.get("warehouseCode")).isEqualTo("WH003");
        }

        @Test
        @DisplayName("创建库房时同步所选商品绑定")
        void should_bind_selected_products_when_creating_warehouse() {
            WarehouseUpsertRequest request = new WarehouseUpsertRequest(
                    "WH004", "手术耗材库", "中心库",
                    "主院区", "手术室", true, "耗材", 1,
                    List.of("P001", "P002")
            );
            doReturn(List.of(100L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("dept_id FROM sys_dept")),
                    eq(Long.class), eq("手术室"));
            doReturn(List.of(200L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("warehouse_id FROM warehouse")),
                    eq(Long.class), eq("WH004"));
            doReturn(List.of(
                    Map.of("product_id", 301L, "product_code", "P001"),
                    Map.of("product_id", 302L, "product_code", "P002")
            )).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("product_code IN")),
                    any(Object[].class));
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createWarehouse(request);

            assertThat(result)
                    .containsEntry("warehouseCode", "WH004")
                    .containsEntry("productCount", 2);
            verify(jdbcTemplate, times(2)).update(
                    argThat(sql -> ((String) sql).contains("INSERT INTO warehouse_product_binding")),
                    any(Object[].class));
            verify(jdbcTemplate).update(
                    argThat(sql -> ((String) sql).contains("INSERT IGNORE INTO department_warehouse_catalog")),
                    any(Object[].class));
            verify(jdbcTemplate, atLeastOnce()).update(contains("source_type = 'warehouse_binding'"), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("导入库房与货位 importWarehouses()")
    class ImportWarehousesTest {

        @Test
        @DisplayName("模板包含货位字段时同时写入库房和货位")
        void should_import_warehouse_and_location() throws Exception {
            String csv = """
                    code,name,type,campus,dept,participateStats,statsCategories,locationCode,locationType,capacityLimit,productCode,locationStatus
                    "WH-001","示例中心库","中心库","主院区","","参与","耗材","A-01","整件位","100","PROD-001","启用"
                    """;
            doReturn(1).when(jdbcTemplate).update(
                    argThat(sql -> ((String) sql).contains("INSERT INTO warehouse (")),
                    any(Object[].class));
            doReturn(List.of(10L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("warehouse_id FROM warehouse")),
                    eq(Long.class), eq("WH-001"));
            doReturn(List.of(20L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("product_id FROM product")),
                    eq(Long.class), eq("PROD-001"));
            doReturn(1).when(jdbcTemplate).update(
                    argThat(sql -> ((String) sql).contains("INSERT INTO warehouse_location")),
                    any(Object[].class));

            Map<String, Object> result = service.importWarehouses(
                    new BufferedReader(new StringReader(csv)));

            assertThat(result)
                    .containsEntry("importedRows", 1)
                    .containsEntry("importedLocations", 1);
            verify(jdbcTemplate).update(
                    argThat(sql -> ((String) sql).contains("INSERT INTO warehouse_location")),
                    eq(10L), eq("A-01"), eq("整件位"), eq(new BigDecimal("100")), eq(20L), eq(1));
        }

        @Test
        @DisplayName("货位编码为空时保持仅导入库房的兼容行为")
        void should_import_only_warehouse_when_location_is_blank() throws Exception {
            String csv = """
                    code,name,type,campus,dept,participateStats,statsCategories,locationCode,locationType,capacityLimit,productCode,locationStatus
                    "WH-002","示例虚拟库","虚拟库","主院区","","不参与","","","","","",""
                    """;
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.importWarehouses(
                    new BufferedReader(new StringReader(csv)));

            assertThat(result)
                    .containsEntry("importedRows", 1)
                    .containsEntry("importedLocations", 0);
            verify(jdbcTemplate, never()).update(
                    argThat(sql -> ((String) sql).contains("INSERT INTO warehouse_location")),
                    any(Object[].class));
        }
    }

    @Nested
    @DisplayName("删除库房 deleteWarehouses()")
    class WarehouseLocationsTest {

        @Test
        @DisplayName("查询库房货位")
        void should_list_locations_for_warehouse() {
            doReturn(List.of(10L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("warehouse_id FROM warehouse")),
                    eq(Long.class), eq("WH001"));
            doReturn(List.of(Map.of("locationId", 1L, "locationCode", "A-01")))
                    .when(jdbcTemplate).queryForList(
                            argThat(sql -> ((String) sql).contains("FROM warehouse_location")),
                            eq(10L));

            List<Map<String, Object>> result = service.warehouseLocations("WH001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("locationCode", "A-01");
        }

        @Test
        @DisplayName("新增库房货位")
        void should_create_location_for_warehouse() {
            WarehouseLocationUpsertRequest request = new WarehouseLocationUpsertRequest(
                    "A-01", "整件位", new BigDecimal("100"), "PROD-001", 1);
            doReturn(List.of(10L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("warehouse_id FROM warehouse")),
                    eq(Long.class), eq("WH001"));
            doReturn(List.of(20L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("product_id FROM product")),
                    eq(Long.class), eq("PROD-001"));
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.createWarehouseLocation("WH001", request);

            assertThat(result).containsEntry("locationCode", "A-01");
        }

        @Test
        @DisplayName("编辑库房货位")
        void should_update_location_for_warehouse() {
            WarehouseLocationUpsertRequest request = new WarehouseLocationUpsertRequest(
                    "A-02", "散货位", null, "", 0);
            doReturn(List.of(10L)).when(jdbcTemplate).queryForList(
                    argThat(sql -> ((String) sql).contains("warehouse_id FROM warehouse")),
                    eq(Long.class), eq("WH001"));
            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.updateWarehouseLocation("WH001", 9L, request);

            assertThat(result).containsEntry("updatedRows", 1);
            assertThat(result).containsEntry("locationId", 9L);
        }

        @Test
        @DisplayName("货位编码缺失时抛出异常")
        void should_throw_when_location_code_missing() {
            WarehouseLocationUpsertRequest request = new WarehouseLocationUpsertRequest(
                    "", "散货位", null, null, 1);

            assertThatThrownBy(() -> service.createWarehouseLocation("WH001", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("货位编码");
        }
    }

    @Nested
    @DisplayName("删除库房 deleteWarehouses()")
    class DeleteWarehousesTest {

        @Test
        @DisplayName("批量软删除库房成功")
        void should_soft_delete_warehouses_when_valid_codes() {
            WarehouseCodesRequest request = new WarehouseCodesRequest(List.of("WH001", "WH002"));

            doReturn(2).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.deleteWarehouses(request);

            assertThat(result.get("deletedRows")).isEqualTo(2);
        }

        @Test
        @DisplayName("空库房编码列表抛出异常")
        void should_throw_when_warehouse_codes_empty() {
            WarehouseCodesRequest request = new WarehouseCodesRequest(List.of());

            assertThatThrownBy(() -> service.deleteWarehouses(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要删除的库房");
        }

        @Test
        @DisplayName("null库房编码列表抛出异常")
        void should_throw_when_warehouse_codes_null() {
            WarehouseCodesRequest request = new WarehouseCodesRequest(null);

            assertThatThrownBy(() -> service.deleteWarehouses(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要删除的库房");
        }

        @Test
        @DisplayName("删除单个库房成功")
        void should_delete_single_warehouse() {
            WarehouseCodesRequest request = new WarehouseCodesRequest(List.of("WH001"));

            doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

            Map<String, Object> result = service.deleteWarehouses(request);

            assertThat(result.get("deletedRows")).isEqualTo(1);
        }
    }
}
