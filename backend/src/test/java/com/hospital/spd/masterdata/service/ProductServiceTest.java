package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductService service;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    @BeforeEach
    void setUp() {
        service = new ProductService(jdbcTemplate);
    }

    @Nested
    @DisplayName("分页查询商品目录 hospitalProducts()")
    class HospitalProductsTest {

        private final Map<String, String> emptyParams = Map.of();

        @Test
        @DisplayName("无筛选条件分页查询全部商品")
        void should_return_paginated_products_when_no_filters() {
            // Arrange
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                    .thenReturn(50L);

            List<Map<String, Object>> rows = List.of(
                    Map.of("code", "P001", "name", "测试商品A", "spec", "10ml/支",
                            "manufacturer", "厂家A", "supplier", "供应商A",
                            "price", BigDecimal.valueOf(100), "status", "启用"),
                    Map.of("code", "P002", "name", "测试商品B", "spec", "20ml/支",
                            "manufacturer", "厂家B", "supplier", "供应商B",
                            "price", BigDecimal.valueOf(200), "status", "启用")
            );
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(rows);

            // Act
            MasterDataPage result = service.hospitalProducts(emptyParams);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("医院目录");
            assertThat(result.total()).isEqualTo(50);
            assertThat(result.rows()).hasSize(2);
            assertThat(result.rows().get(0)).containsEntry("code", "P001");
            assertThat(result.columns()).contains("商品编码", "商品名称", "规格型号");
        }

        @Test
        @DisplayName("按商品编码和名称模糊搜索")
        void should_filter_products_when_search_params_provided() {
            // Arrange
            Map<String, String> params = Map.of(
                    "productCode", "P001",
                    "productName", "测试"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(5L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "P001", "name", "测试商品A")
                    ));

            // Act
            MasterDataPage result = service.hospitalProducts(params);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.total()).isEqualTo(5);
            assertThat(result.rows()).hasSize(1);
        }

        @Test
        @DisplayName("按厂商和供应商筛选")
        void should_filter_products_by_manufacturer_and_supplier() {
            // Arrange
            Map<String, String> params = Map.of(
                    "manufacturerName", "厂家A",
                    "supplierName", "供应商A"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "P001", "name", "测试商品A")
                    ));

            // Act
            MasterDataPage result = service.hospitalProducts(params);

            // Assert
            assertThat(result.total()).isEqualTo(3);
        }

        @Test
        @DisplayName("按集采和国产布尔字段筛选")
        void should_filter_products_by_boolean_fields() {
            // Arrange
            Map<String, String> params = Map.of(
                    "isCentralized", "是",
                    "isDomestic", "是"
            );

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            // Act
            MasterDataPage result = service.hospitalProducts(params);

            // Assert
            assertThat(result.total()).isEqualTo(10);
            assertThat(result.rows()).isEmpty();
        }

        @Test
        @DisplayName("按招采子编码筛选")
        void should_filter_products_by_tender_code() {
            Map<String, String> params = Map.of("tenderCode", "TENDER001");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "P001", "name", "测试商品A")
                    ));

            MasterDataPage result = service.hospitalProducts(params);
            assertThat(result.total()).isEqualTo(1);
        }

        @Test
        @DisplayName("按规格型号筛选")
        void should_filter_products_by_spec_model() {
            Map<String, String> params = Map.of("specModel", "10ml");

            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(2L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(
                            Map.of("code", "P001", "name", "测试商品A")
                    ));

            MasterDataPage result = service.hospitalProducts(params);
            assertThat(result.total()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("商品详情 hospitalProductDetail()")
    class ProductDetailTest {

        @Test
        @DisplayName("查询商品详情含证照附件")
        void should_return_detail_with_attachments() {
            // Arrange
            List<ProductAttachment> attachments = List.of(
                    new ProductAttachment("注册证附件.pdf", "注册证", "可查看", "/files/reg.pdf", "2025-12-31"),
                    new ProductAttachment("生产许可证附件.pdf", "生产许可", "可查看", "/files/prod.pdf", "")
            );
            ProductDetail expected = new ProductDetail(
                    1L, "P001", "测试商品A", "10ml/支",
                    "品牌A", "分类A", "厂家A", "供应商A",
                    "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                    BigDecimal.ONE, "箱", BigDecimal.valueOf(10), null,
                    "UDI001", "注册证号001", "2025-12-31",
                    "生产许可001", "经营许可001",
                    true, true, true, "合同001",
                    "一级", "二级", "三级", true, "招采001",
                    true, false, true, "常温", "启用",
                    attachments
            );

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("P001")))
                    .thenReturn(expected);

            // Act
            ProductDetail result = service.hospitalProductDetail("P001");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.productCode()).isEqualTo("P001");
            assertThat(result.productName()).isEqualTo("测试商品A");
            assertThat(result.manufacturerName()).isEqualTo("厂家A");
            assertThat(result.supplierName()).isEqualTo("供应商A");
            assertThat(result.attachments()).hasSize(2);
            assertThat(result.attachments().get(0).fileName()).isEqualTo("注册证附件.pdf");
            assertThat(result.statusLabel()).isEqualTo("启用");
            assertThat(result.highValue()).isTrue();
            assertThat(result.coldChain()).isFalse();
        }

        @Test
        @DisplayName("商品详情中价格和数量字段正确")
        void should_return_detail_with_correct_numeric_fields() {
            ProductDetail expected = new ProductDetail(
                    1L, "P001", "测试商品A", "10ml/支",
                    "-", "-", "-", "-",
                    "支", BigDecimal.valueOf(99.99), BigDecimal.valueOf(199.99),
                    BigDecimal.valueOf(5), "-", BigDecimal.ONE, null,
                    "-", "-", "-",
                    "-", "-",
                    false, false, false, "-",
                    "-", "-", "-", false, "-",
                    false, false, false, "-", "启用",
                    List.of()
            );

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString()))
                    .thenReturn(expected);

            ProductDetail result = service.hospitalProductDetail("P001");

            assertThat(result.purchasePrice()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
            assertThat(result.retailPrice()).isEqualByComparingTo(BigDecimal.valueOf(199.99));
            assertThat(result.minPurchaseQty()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }
    }
    @Test
    @DisplayName("目录伙伴选项只读取厂家和供应商管理表")
    void should_return_partner_options_from_managed_tables() {
        List<Map<String, Object>> manufacturers = List.of(
                Map.of("code", "MFR001", "name", "厂家A", "status", 1)
        );
        List<Map<String, Object>> suppliers = List.of(
                Map.of("code", "SUP001", "name", "供应商A", "status", 1)
        );
        when(jdbcTemplate.queryForList(contains("FROM manufacturer"))).thenReturn(manufacturers);
        when(jdbcTemplate.queryForList(contains("FROM supplier"))).thenReturn(suppliers);

        Map<String, List<Map<String, Object>>> result = service.partnerOptions();

        assertThat(result.get("manufacturers")).containsExactlyElementsOf(manufacturers);
        assertThat(result.get("suppliers")).containsExactlyElementsOf(suppliers);
        verify(jdbcTemplate, times(2)).queryForList(contains("WHERE deleted = 0"));
        verify(jdbcTemplate, times(2)).queryForList(anyString());
    }


    @Nested
    @DisplayName("创建商品 createHospitalProduct()")
    class CreateHospitalProductTest {

        private ProductCreateRequest validRequest() {
            return new ProductCreateRequest(
                    "P001", "测试商品A", "10ml/支",
                    "品牌A", "厂家A", "供应商A",
                    "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                    BigDecimal.ONE, "箱", BigDecimal.valueOf(10), null,
                    "UDI001", "注册证号001", "2025-12-31",
                    "生产许可001", "经营许可001",
                    true, true, true, "合同001",
                    "一级", "二级", "三级", true, "招采001",
                    false, false, true, "常温"
            );
        }

        @Test
        @DisplayName("创建商品成功返回申请单号")
        void should_create_product_and_return_application_no() {
            // Arrange
            ProductCreateRequest request = validRequest();

            // mock nextApplicationNo: SELECT COUNT(*) FROM pending_product_application WHERE application_no LIKE ?
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                    .thenReturn(5);

            // mock findIdByName manufacturer: SELECT manufacturer_id FROM manufacturer WHERE manufacturer_name = ? AND deleted = 0 LIMIT 1
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("manufacturer_id FROM manufacturer")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(10L));

            // mock findIdByName supplier: SELECT supplier_id FROM supplier WHERE supplier_name = ? AND deleted = 0 LIMIT 1
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("supplier_id FROM supplier")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(20L));

            // mock ensureCategory: SELECT category_id FROM product_category WHERE category_name = ? AND deleted = 0 ORDER BY level DESC, category_id DESC LIMIT 1
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("FROM product_category")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(30L));

            // mock the INSERT
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            // Act
            Map<String, Object> result = service.createHospitalProduct(request);

            // Assert
            assertThat(result).containsKey("productCode");
            assertThat(result).containsKey("applicationNo");
            assertThat(result.get("productCode")).isEqualTo("P001");
            assertThat((String) result.get("applicationNo")).startsWith("SP");

            // Verify the INSERT was called
            verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("缺少必填字段抛出异常")
        void should_throw_when_required_fields_missing() {
            ProductCreateRequest request = new ProductCreateRequest(
                    "", "测试商品A", "10ml/支",
                    null, null, null,
                    "支", null, null,
                    null, null, null, null,
                    null, null, null,
                    null, null,
                    false, false, true, null,
                    null, null, null, false, null,
                    false, false, false, null
            );

            assertThatThrownBy(() -> service.createHospitalProduct(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("必填项");
        }

        @Test
        @DisplayName("高值耗材设置为定数管理抛出异常")
        void should_throw_when_high_value_and_quota_managed() {
            ProductCreateRequest request = new ProductCreateRequest(
                    "P001", "测试商品A", "10ml/支",
                    null, null, null,
                    "支", null, null,
                    null, null, null, null,
                    null, null, null,
                    null, null,
                    false, false, true, null,
                    null, null, null, false, null,
                    true, false, true, null
            );

            assertThatThrownBy(() -> service.createHospitalProduct(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("高值耗材或冷链耗材不能设置为定数管理");
        }

        @Test
        @DisplayName("冷链耗材设置为定数管理抛出异常")
        void should_throw_when_cold_chain_and_quota_managed() {
            ProductCreateRequest request = new ProductCreateRequest(
                    "P001", "测试商品A", "10ml/支",
                    null, null, null,
                    "支", null, null,
                    null, null, null, null,
                    null, null, null,
                    null, null,
                    false, false, true, null,
                    null, null, null, false, null,
                    false, true, true, null
            );

            assertThatThrownBy(() -> service.createHospitalProduct(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("高值耗材或冷链耗材不能设置为定数管理");
        }
    }

    @Nested
    @DisplayName("更新医院目录商品 updateHospitalProduct()")
    class UpdateHospitalProductTest {

        @Test
        @DisplayName("信息变更没有字段差异时不生成审批单")
        void should_reject_information_change_when_no_field_diff() {
            ProductCreateRequest request = new ProductCreateRequest(
                    "P001", "测试商品A", "10ml/支",
                    "品牌A", "厂家A", "供应商A",
                    "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                    BigDecimal.ONE, "箱", BigDecimal.valueOf(10), null,
                    "UDI001", "注册证号001", "2025-12-31",
                    "生产许可001", "经营许可001",
                    true, true, true, "合同001",
                    "一级", "二级", "三级", true, "招采001",
                    false, false, true, "常温"
            );
            ProductDetail current = new ProductDetail(
                    1L, "P001", "测试商品A", "10ml/支",
                    "品牌A", "分类A", "厂家A", "供应商A",
                    "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                    BigDecimal.ONE, "箱", BigDecimal.valueOf(10), null,
                    "UDI001", "注册证号001", "2025-12-31",
                    "生产许可001", "经营许可001",
                    true, true, true, "合同001",
                    "一级", "二级", "三级", true, "招采001",
                    false, false, true, "常温", "启用",
                    List.of()
            );

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("P001")))
                    .thenReturn(current);
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("purchase_price")),
                    eq(BigDecimal.class),
                    anyString()))
                    .thenReturn(List.of(BigDecimal.valueOf(100)));

            assertThatThrownBy(() -> service.updateHospitalProduct("P001", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未检测到与医院目录当前数据的差异");
            verify(jdbcTemplate, never()).update(contains("INSERT INTO pending_product_application"), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("更新商品状态 updateHospitalProductStatus()")
    class UpdateHospitalProductStatusTest {

        @Test
        @DisplayName("停用多个商品成功")
        void should_update_status_when_valid_request() {
            // Arrange
            ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(
                    List.of("P001", "P002"), 0
            );

            // mock hospitalProductDetail call for each product code
            ProductDetail detail = new ProductDetail(
                    1L, "P001", "测试商品A", "10ml/支",
                    "品牌A", "分类A", "厂家A", "供应商A",
                    "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150),
                    BigDecimal.ONE, "箱", BigDecimal.valueOf(10), null,
                    "UDI001", "注册证号001", "2025-12-31",
                    "生产许可001", "经营许可001",
                    true, true, true, "合同001",
                    "一级", "二级", "三级", true, "招采001",
                    false, false, true, "常温", "启用",
                    List.of()
            );

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString()))
                    .thenReturn(detail);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                    .thenReturn(0);

            // mock findIdByName
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("manufacturer_id FROM manufacturer")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("supplier_id FROM supplier")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForList(
                    argThat(sql -> sql != null && ((String) sql).contains("FROM product_category")),
                    eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            // Act
            Map<String, Object> result = service.updateHospitalProductStatus(request);

            // Assert
            assertThat(result).containsKey("updatedRows");
            assertThat(result.get("updatedRows")).isEqualTo(2);
        }

        @Test
        @DisplayName("空商品编码列表抛出异常")
        void should_throw_when_product_codes_empty() {
            ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(
                    List.of(), 0
            );

            assertThatThrownBy(() -> service.updateHospitalProductStatus(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要停用的商品");
        }

        @Test
        @DisplayName("空商品编码列表为null抛出异常")
        void should_throw_when_product_codes_null() {
            ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(
                    null, 0
            );

            assertThatThrownBy(() -> service.updateHospitalProductStatus(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请选择需要停用的商品");
        }
    }
}
