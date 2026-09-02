package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.QuotaSafetyRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyStockService 单元测试")
class SafetyStockServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private SafetyStockService service;

    @BeforeEach
    void setUp() {
        service = new SafetyStockService(jdbcTemplate, support);
        lenient().when(jdbcTemplate.queryForObject(contains("SELECT safety_id"), eq(Long.class), anyLong(), anyLong()))
                .thenReturn(99L);
    }

    // ==================== 安全库存列表 ====================

    @Nested
    @DisplayName("safety() 安全库存列表")
    class SafetyTest {

        @Test
        @DisplayName("无筛选条件时返回全部配置")
        void shouldReturnAllSafetyConfigs() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(10L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("safetyId", 1L, "deptName", "检验科",
                            "productCode", "P001", "minQty", BigDecimal.valueOf(10),
                            "maxQty", BigDecimal.valueOf(100))
            ));

            List<Map<String, Object>> result = rows(service.safety(params));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("按科室筛选时返回过滤结果")
        void shouldReturnFilteredByDept() {
            Map<String, String> params = Map.of("page", "1", "size", "10", "deptName", "检验科");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                    Map.of("safetyId", 2L, "deptName", "检验科",
                            "productCode", "P002", "minQty", BigDecimal.valueOf(5),
                            "maxQty", BigDecimal.valueOf(50))
            ));

            List<Map<String, Object>> result = rows(service.safety(params));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("无匹配数据时返回空列表")
        void shouldReturnEmptyWhenNoResults() {
            Map<String, String> params = Map.of("page", "1", "size", "20");
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

            List<Map<String, Object>> result = rows(service.safety(params));

            assertThat(result).isEmpty();
        }
    }

    // ==================== 保存安全库存配置 ====================

    @Nested
    @DisplayName("saveSafety() 保存安全库存配置")
    class SaveSafetyTest {

        @Test
        @DisplayName("成功保存安全库存配置（科室已存在）")
        void shouldSaveSafetySuccessfully() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "P001",
                    BigDecimal.valueOf(10), BigDecimal.valueOf(100)
            );
            // ensureDept: dept exists
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            // findEligibleProduct
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 1, "highValue", 0, "coldChain", 0));
            // INSERT ... ON DUPLICATE KEY UPDATE
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            // support.writeAudit is void
            doNothing().when(support).writeAudit(anyString(), anyString(), anyLong(),
                    anyString(), anyString());

            Map<String, Object> result = service.saveSafety(request);

            assertThat(result).containsKeys("productCode", "deptName");
            assertThat(result.get("productCode")).isEqualTo("P001");
            assertThat(result.get("deptName")).isEqualTo("检验科");
        }

        @Test
        @DisplayName("仅提交稳定科室编码时返回服务器科室名称")
        void shouldReturnResolvedDepartmentNameForDeptCodeRequest() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "D001", null, null, null, "P001",
                    BigDecimal.valueOf(10), BigDecimal.valueOf(100)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("D001")))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 1, "highValue", 0, "coldChain", 0));
            when(jdbcTemplate.queryForObject(contains("SELECT dept_name"), eq(String.class), eq(1L)))
                    .thenReturn("检验科");
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1);

            Map<String, Object> result = service.saveSafety(request);

            assertThat(result.get("deptName")).isEqualTo("检验科");
        }

        @Test
        @DisplayName("科室不存在时拒绝保存")
        void shouldCreateDeptWhenNotExists() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "新科室", null, "P001",
                    BigDecimal.valueOf(5), BigDecimal.valueOf(50)
            );
            // ensureDept: dept not found by name
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of());
            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("department does not exist");
        }

        @Test
        @DisplayName("含模板编码时成功保存")
        void shouldSaveWithTemplateCode() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", "TEMPLATE001", "P001",
                    BigDecimal.valueOf(20), BigDecimal.valueOf(200)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 1, "highValue", 0, "coldChain", 0));
            // find templateId
            when(jdbcTemplate.queryForList(contains("FROM quota_package_template qpt"), eq(Long.class),
                    eq("TEMPLATE001"), eq(10L))).thenReturn(List.of(5L));
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            doNothing().when(support).writeAudit(anyString(), anyString(), anyLong(),
                    anyString(), anyString());

            Map<String, Object> result = service.saveSafety(request);

            assertThat(result.get("productCode")).isEqualTo("P001");
        }

        @Test
        @DisplayName("部门名称为空时抛出异常")
        void shouldThrowWhenDeptNameIsBlank() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "", null, "P001",
                    BigDecimal.TEN, BigDecimal.valueOf(100)
            );
            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("department name and product code are required");
        }

        @Test
        @DisplayName("商品编码为空时抛出异常")
        void shouldThrowWhenProductCodeIsBlank() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "",
                    BigDecimal.TEN, BigDecimal.valueOf(100)
            );
            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("department name and product code are required");
        }

        @Test
        @DisplayName("最大数量小于最小数量时抛出异常")
        void shouldThrowWhenMaxLessThanMin() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "P001",
                    BigDecimal.valueOf(100), BigDecimal.TEN
            );
            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max quantity must be greater than or equal to min quantity");
        }

        @Test
        @DisplayName("最小数量为 null 时使用 0")
        void shouldDefaultMinQtyToZero() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "P001",
                    null, BigDecimal.valueOf(50)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 1, "highValue", 0, "coldChain", 0));
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            doNothing().when(support).writeAudit(anyString(), anyString(), anyLong(),
                    anyString(), anyString());

            Map<String, Object> result = service.saveSafety(request);

            assertThat(result.get("productCode")).isEqualTo("P001");
        }

        @Test
        @DisplayName("商品非定额管理时抛出异常")
        void shouldThrowWhenProductNotQuotaManaged() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "P001",
                    BigDecimal.TEN, BigDecimal.valueOf(100)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 0, "highValue", 0, "coldChain", 0));

            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("product is not quota managed");
        }

        @Test
        @DisplayName("高值商品不允许定额配置")
        void shouldThrowWhenProductIsHighValue() {
            QuotaSafetyRequest request = new QuotaSafetyRequest(
                    "检验科", null, "P001",
                    BigDecimal.TEN, BigDecimal.valueOf(100)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), anyString()))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForMap(anyString(), anyString()))
                    .thenReturn(Map.of("productId", 10L, "productCode", "P001",
                            "productName", "测试产品", "unit", "个",
                            "quotaManaged", 1, "highValue", 1, "coldChain", 0));

            assertThatThrownBy(() -> service.saveSafety(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("high value and cold chain product are not allowed");
        }
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }
}
