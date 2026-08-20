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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DepartmentService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(jdbcTemplate);
    }

    @Nested
    @DisplayName("分页查询科室 departments()")
    class DepartmentsTest {

        @Test
        @DisplayName("无筛选条件分页查询全部科室")
        void should_return_paginated_departments_when_no_filters() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(15L);

            List<Map<String, Object>> rows = List.of(
                    Map.of("code", "DEPT001", "name", "内科", "deptType", "临床科室",
                            "status", "正常", "campus", "主院区",
                            "manager", "张主任", "phone", "1234"),
                    Map.of("code", "DEPT002", "name", "外科", "deptType", "临床科室",
                            "status", "正常", "campus", "东院区",
                            "manager", "李主任", "phone", "5678")
            );
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(rows);

            MasterDataPage result = service.departments(Map.of());

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("科室管理");
            assertThat(result.total()).isEqualTo(15);
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0)).containsEntry("code", "DEPT001");
            assertThat(result.columns()).contains("科室编码", "科室名称", "科室类型");
        }

        @Test
        @DisplayName("按关键词和科室类型筛选")
        void should_filter_departments_by_keyword_and_type() {
            Map<String, String> params = Map.of(
                    "deptKeyword", "内科",
                    "deptType", "临床"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "DEPT001", "name", "内科")
                    ));

            MasterDataPage result = service.departments(params);

            assertThat(result.total()).isEqualTo(3);
            assertThat(result.rows()).hasSize(1);
        }

        @Test
        @DisplayName("按院区和财务科室筛选")
        void should_filter_departments_by_campus_and_finance() {
            Map<String, String> params = Map.of(
                    "campusName", "主院区",
                    "financeDeptName", "财务部"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(5L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.departments(params);

            assertThat(result.total()).isEqualTo(5);
        }

        @Test
        @DisplayName("按库房名称筛选")
        void should_filter_departments_by_warehouse() {
            Map<String, String> params = Map.of("warehouseName", "中心库");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(2L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "DEPT001", "name", "内科")
                    ));

            MasterDataPage result = service.departments(params);

            assertThat(result.total()).isEqualTo(2);
        }

        @Test
        @DisplayName("按状态筛选科室")
        void should_filter_departments_by_status() {
            Map<String, String> params = Map.of("status", "正常");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.departments(params);

            assertThat(result.total()).isEqualTo(10);
        }

        @Test
        @DisplayName("按来源标识筛选")
        void should_filter_departments_by_source_flag() {
            Map<String, String> params = Map.of("sourceFlag", "HIS001");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "DEPT001", "name", "内科")
                    ));

            MasterDataPage result = service.departments(params);
            assertThat(result.total()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("创建/更新科室 upsertDepartment()")
    class UpsertDepartmentTest {

        private DepartmentUpsertRequest validCreateRequest() {
            return new DepartmentUpsertRequest(
                    "DEPT001", "内科", "F001", "财务一部",
                    "主院区", "1号楼3层", "张主任", "1234",
                    1, 1
            );
        }

        @Test
        @DisplayName("创建科室成功返回编码")
        void should_create_department_successfully() {
            DepartmentUpsertRequest request = validCreateRequest();

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createDepartment(request);

            assertThat(result.get("deptCode")).isEqualTo("DEPT001");
        }

        @Test
        @DisplayName("创建科室编码重复时抛出业务异常")
        void should_throw_business_error_when_department_code_exists() {
            DepartmentUpsertRequest request = validCreateRequest();

            when(jdbcTemplate.update(anyString(), any(Object[].class)))
                    .thenThrow(new DuplicateKeyException("duplicate dept_code"));

            assertThatThrownBy(() -> service.createDepartment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("科室编码已存在");
        }

        @Test
        @DisplayName("更新科室成功返回编码和行数")
        void should_update_department_successfully() {
            DepartmentUpsertRequest request = validCreateRequest();

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.updateDepartment("DEPT001", request);

            assertThat(result.get("updatedRows")).isEqualTo(1);
            assertThat(result.get("deptCode")).isEqualTo("DEPT001");
        }

        @Test
        @DisplayName("创建科室缺少必填字段抛出异常")
        void should_throw_when_creating_with_missing_fields() {
            DepartmentUpsertRequest request = new DepartmentUpsertRequest(
                    "", "", null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createDepartment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("科室编码、科室名称为必填项");
        }

        @Test
        @DisplayName("更新科室缺少名称抛出异常")
        void should_throw_when_updating_with_missing_name() {
            DepartmentUpsertRequest request = new DepartmentUpsertRequest(
                    "DEPT001", "", null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.updateDepartment("DEPT001", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("科室编码、科室名称为必填项");
        }

        @Test
        @DisplayName("创建科室时sortOrder和status使用默认值")
        void should_use_default_values_for_sort_order_and_status() {
            DepartmentUpsertRequest request = new DepartmentUpsertRequest(
                    "DEPT002", "外科", null, null,
                    null, null, null, null,
                    null, null
            );

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createDepartment(request);

            assertThat(result.get("deptCode")).isEqualTo("DEPT002");
            verify(jdbcTemplate).update(anyString(),
                    eq("DEPT002"), eq("外科"), isNull(), isNull(),
                    isNull(), isNull(), isNull(), isNull(),
                    eq(0), eq(1));
        }
    }

    @Nested
    @DisplayName("科室关联库房 departmentWarehouses()")
    class DepartmentWarehouseRelationTest {

        @Test
        @DisplayName("查询科室可关联库房并标记已关联项")
        void should_list_department_warehouses_with_selected_flag() {
            when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("DEPT001")))
                    .thenReturn(List.of(7L));
            when(jdbcTemplate.queryForList(contains("CASE WHEN w.dept_id = ?"), eq(7L)))
                    .thenReturn(List.of(
                            Map.of("code", "WH001", "name", "中心库", "selected", 1),
                            Map.of("code", "WH002", "name", "二级库", "selected", 0)
                    ));

            List<Map<String, Object>> result = service.departmentWarehouses("DEPT001");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("code", "WH001").containsEntry("selected", 1);
        }

        @Test
        @DisplayName("保存科室关联库房时先清理移除项再分配选中项")
        void should_update_department_warehouse_relations() {
            when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("DEPT001")))
                    .thenReturn(List.of(7L));
            when(jdbcTemplate.update(contains("warehouse_code NOT IN"), any(Object[].class))).thenReturn(1);
            when(jdbcTemplate.update(contains("warehouse_code IN"), any(Object[].class))).thenReturn(2);

            Map<String, Object> result = service.updateDepartmentWarehouses(
                    "DEPT001",
                    new DepartmentWarehouseRelationRequest(List.of("WH001", "WH002"))
            );

            assertThat(result).containsEntry("updatedRows", 3).containsEntry("warehouseCount", 2);
            verify(jdbcTemplate).update(contains("warehouse_code NOT IN"), any(Object[].class));
            verify(jdbcTemplate).update(contains("warehouse_code IN"), any(Object[].class));
        }

        @Test
        @DisplayName("保存空库房列表时解除该科室全部关联")
        void should_clear_department_warehouses_when_empty_selection() {
            when(jdbcTemplate.queryForList(contains("SELECT dept_id FROM sys_dept"), eq(Long.class), eq("DEPT001")))
                    .thenReturn(List.of(7L));
            when(jdbcTemplate.update(contains("SET dept_id = NULL"), eq(7L))).thenReturn(4);

            Map<String, Object> result = service.updateDepartmentWarehouses(
                    "DEPT001",
                    new DepartmentWarehouseRelationRequest(List.of())
            );

            assertThat(result).containsEntry("updatedRows", 4).containsEntry("warehouseCount", 0);
        }
    }
}
