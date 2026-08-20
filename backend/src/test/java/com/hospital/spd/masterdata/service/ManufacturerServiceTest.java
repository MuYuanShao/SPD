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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManufacturerServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DocumentNumberService documentNumberService;

    private ManufacturerService service;

    @BeforeEach
    void setUp() {
        service = new ManufacturerService(jdbcTemplate, documentNumberService);
    }

    @Nested
    @DisplayName("分页查询厂家 manufacturers()")
    class ManufacturersTest {

        @Test
        @DisplayName("无筛选条件分页查询全部厂家")
        void should_return_paginated_manufacturers_when_no_filters() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(20L);

            List<Map<String, Object>> rows = List.of(
                    Map.of("code", "MFR001", "name", "厂家A", "licenseNo", "SC2020001",
                            "contactName", "王五", "contactPhone", "13900139001",
                            "status", "启用"),
                    Map.of("code", "MFR002", "name", "厂家B", "licenseNo", "SC2020002",
                            "contactName", "赵六", "contactPhone", "13900139002",
                            "status", "停用")
            );
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(rows);

            MasterDataPage result = service.manufacturers(Map.of());

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("厂家管理");
            assertThat(result.total()).isEqualTo(20);
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0)).containsEntry("code", "MFR001");
            assertThat(result.columns()).contains("厂家编码", "厂家名称", "生产许可证号");
        }

        @Test
        @DisplayName("按厂家名称和许可证号筛选")
        void should_filter_manufacturers_by_name_and_license() {
            Map<String, String> params = Map.of(
                    "manufacturerName", "厂家A",
                    "licenseNo", "SC2020"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "MFR001", "name", "厂家A", "licenseNo", "SC2020001")
                    ));

            MasterDataPage result = service.manufacturers(params);

            assertThat(result.total()).isEqualTo(3);
            assertThat(result.rows()).hasSize(1);
        }

        @Test
        @DisplayName("按状态筛选厂家")
        void should_filter_manufacturers_by_status() {
            Map<String, String> params = Map.of("status", "启用");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(15L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.manufacturers(params);

            assertThat(result.total()).isEqualTo(15);
        }

        @Test
        @DisplayName("查询结果为空时返回空列表")
        void should_return_empty_list_when_no_manufacturers() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            MasterDataPage result = service.manufacturers(Map.of());

            assertThat(result.total()).isZero();
            assertThat(result.rows()).isEmpty();
        }
    }

    @Nested
    @DisplayName("创建厂家 createManufacturer()")
    class CreateManufacturerTest {

        private ManufacturerUpsertRequest validRequest() {
            return new ManufacturerUpsertRequest(
                    "MANUAL-MFR", "厂家A", "91110000MA001",
                    "SC2020001", "王五", "13900139001", "北京市海淀区"
            );
        }

        @Test
        @DisplayName("创建厂家成功返回编码")
        void should_create_manufacturer_successfully() {
            ManufacturerUpsertRequest request = validRequest();

            when(documentNumberService.next(DocumentKind.MANUFACTURER)).thenReturn("MFR2026072800001");
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createManufacturer(request);

            assertThat(result.get("manufacturerCode")).isEqualTo("MFR2026072800001");
            verify(jdbcTemplate).update(anyString(),
                    eq("MFR2026072800001"), eq("厂家A"), eq("91110000MA001"),
                    eq("SC2020001"), eq("王五"), eq("13900139001"), eq("北京市海淀区"));
            verify(documentNumberService).next(DocumentKind.MANUFACTURER);
        }

        @Test
        @DisplayName("缺少必填字段抛出异常")
        void should_throw_when_required_fields_missing() {
            ManufacturerUpsertRequest request = new ManufacturerUpsertRequest(
                    "", "", null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createManufacturer(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("厂家名称为必填项");
        }

        @Test
        @DisplayName("厂家名称缺失抛出异常")
        void should_throw_when_manufacturer_name_missing() {
            ManufacturerUpsertRequest request = new ManufacturerUpsertRequest(
                    "MFR001", "", null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createManufacturer(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("厂家名称为必填项");
        }
    }

    @Nested
    @DisplayName("更新厂家 updateManufacturer()")
    class UpdateManufacturerTest {

        private ManufacturerUpsertRequest validRequest() {
            return new ManufacturerUpsertRequest(
                    "MFR001", "厂家A-updated", "91110000MA001",
                    "SC2020001-X", "王五", "13900139001", "北京市朝阳区"
            );
        }

        @Test
        @DisplayName("更新厂家成功返回编码")
        void should_update_manufacturer_successfully() {
            ManufacturerUpsertRequest request = validRequest();

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.updateManufacturer("MFR001", request);

            assertThat(result.get("updatedRows")).isEqualTo(1);
            assertThat(result.get("manufacturerCode")).isEqualTo("MFR001");
        }

        @Test
        @DisplayName("更新厂家名称缺失抛出异常")
        void should_throw_when_name_missing_on_update() {
            ManufacturerUpsertRequest request = new ManufacturerUpsertRequest(
                    "MFR001", "", null, null, null, null, null
            );

            assertThatThrownBy(() -> service.updateManufacturer("MFR001", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("厂家名称为必填项");
        }

        @Test
        @DisplayName("更新不存在的厂家返回0行")
        void should_return_zero_when_manufacturer_not_found() {
            ManufacturerUpsertRequest request = validRequest();

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

            Map<String, Object> result = service.updateManufacturer("NONEXIST", request);

            assertThat(result.get("updatedRows")).isEqualTo(0);
        }
    }
}
