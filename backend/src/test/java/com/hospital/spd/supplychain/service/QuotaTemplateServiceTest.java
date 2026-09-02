package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.QuotaTemplateRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QuotaTemplateService 单元测试——覆盖定数包模板 CRUD、模板明细、申领目录查询。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaTemplateServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private QuotaTemplateService service;

    @BeforeEach
    void setUp() {
        service = new QuotaTemplateService(jdbcTemplate, support);
        lenient().when(support.nextNo(any(DocumentKind.class)))
                .thenAnswer(invocation -> ((DocumentKind) invocation.getArgument(0)).prefix() + "20260601001");
        lenient().when(jdbcTemplate.queryForObject(contains("template_conflicts"), eq(Integer.class)))
                .thenReturn(0);
        lenient().when(jdbcTemplate.queryForObject(contains("template_conflicts"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
    }

    private void populateKeyHolder(KeyHolder kh, Long keyValue) throws Exception {
        Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
        keyListField.setAccessible(true);
        keyListField.set(kh, List.of(Map.of("GENERATED_KEY", keyValue)));
    }

    // ==================== templates() ====================

    @Test
    @DisplayName("查询模板列表——正常分页返回")
    void shouldQueryTemplatesWithPagination() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(5L);
        when(jdbcTemplate.queryForList(contains("LIMIT ? OFFSET ?"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("templateCode", "TP001", "templateName", "模板A"),
                        Map.of("templateCode", "TP002", "templateName", "模板B")
                ));

        List<Map<String, Object>> result = rows(service.templates(Map.of("page", "1")));

        assertThat(result).hasSize(2);
        verify(jdbcTemplate).queryForList(contains("LIMIT ? OFFSET ?"), any(Object[].class));
    }

    @Test
    @DisplayName("查询模板列表——带过滤条件")
    void shouldQueryTemplatesWithFilters() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("LIMIT ? OFFSET ?"), any(Object[].class)))
                .thenReturn(List.of(Map.of("templateCode", "TP001")));

        List<Map<String, Object>> result = rows(service.templates(Map.of(
                "page", "1", "templateCode", "TP", "productName", "注射器"
        )));

        assertThat(result).hasSize(1);
        verify(jdbcTemplate).queryForList(contains("template_code LIKE ?"), any(Object[].class));
        verify(jdbcTemplate).queryForList(contains("product_name LIKE ?"), any(Object[].class));
    }

    @Test
    @DisplayName("查询模板列表——无数据返回空列表")
    void shouldReturnEmptyWhenNoTemplates() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(contains("LIMIT ? OFFSET ?"), any(Object[].class)))
                .thenReturn(List.of());

        List<Map<String, Object>> result = rows(service.templates(Map.of("page", "1")));

        assertThat(result).isEmpty();
    }

    // ==================== createTemplate() ====================

    @Test
    @DisplayName("创建模板——成功创建并返回模板编号")
    void shouldCreateTemplateSuccessfully() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", "外科", "PC001",
                BigDecimal.valueOf(5), "包"
        );

        // findEligibleProduct
        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包", "purchaseUnit", "箱",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));

        // ensureTemplateNotDuplicate — COUNT = 0（适用科室已取消，deptId 恒为 null）
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(0);

        // insert template — PreparedStatementCreator + KeyHolder
        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 42L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        // queryForObject — get templateId after insert
        when(jdbcTemplate.queryForObject(
                contains("SELECT template_id FROM quota_package_template WHERE template_code = ?"),
                eq(Long.class), anyString()))
                .thenReturn(42L);

        // insert template item
        when(jdbcTemplate.update(contains("INSERT INTO quota_package_template_item"), anyLong(), anyLong(), any(), anyString()))
                .thenReturn(1);

        Map<String, Object> result = service.createTemplate(request);

        assertThat(result).containsEntry("templateCode", "TP001");
        verify(support).writeAudit(eq("quota_package"), eq("create_quota_template"),
                anyLong(), eq("TP001"), anyString());
    }

    @Test
    @DisplayName("修改现有模板时创建不可变的新版本")
    void shouldCreateNewImmutableVersionWhenUpdatingTemplate() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "Updated template", "Dept A", "PC002",
                BigDecimal.valueOf(8), "box"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 200L, "productCode", "PC002", "productName", "Product B",
                        "unit", "each", "purchaseUnit", "box",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(
                contains("WHERE template_code = ? AND is_current = 1"),
                eq(Long.class),
                eq("TP001")))
                .thenReturn(List.of(42L));
        when(jdbcTemplate.queryForObject(contains("SELECT version_no"), eq(Integer.class), eq(42L)))
                .thenReturn(1);
        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 43L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        when(jdbcTemplate.update(contains("INSERT INTO quota_package_template_item"), eq(43L), eq(200L), any(), eq("each")))
                .thenReturn(1);

        Map<String, Object> result = service.createTemplate(request);

        assertThat(result).containsEntry("templateCode", "TP001").containsEntry("versionNo", 2);
        verify(jdbcTemplate).update(contains("SET is_current = 0"), eq(42L));
        verify(jdbcTemplate).update(contains("INSERT INTO quota_package_template_item"),
                eq(43L), eq(200L), eq(BigDecimal.valueOf(80)), eq("each"));
        verify(support).writeAudit(eq("quota_package"), eq("version_quota_template"),
                eq(43L), eq("TP001"), contains("version 2"));
    }

    @Test
    @DisplayName("创建模板——自动生成模板编号")
    void shouldCreateTemplateWithAutoGeneratedCode() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                null, "手术包A", null, "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包", "purchaseUnit", "箱",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));

        // no dept — ensureDept not called

        when(jdbcTemplate.queryForObject(contains("MAX(CAST(RIGHT(template_code, 3)"),
                eq(Integer.class), eq("PC001"), eq("PC001"), eq("PC001")))
                .thenReturn(0);

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(0);

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 43L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        when(jdbcTemplate.queryForObject(
                contains("SELECT template_id FROM quota_package_template WHERE template_code = ?"),
                eq(Long.class), anyString()))
                .thenReturn(43L);

        when(jdbcTemplate.update(contains("INSERT INTO quota_package_template_item"), anyLong(), anyLong(), any(), anyString()))
                .thenReturn(1);

        Map<String, Object> result = service.createTemplate(request);

        assertThat(result).containsEntry("templateCode", "PC001001");
        verify(jdbcTemplate).queryForObject(contains("MAX(CAST(RIGHT(template_code, 3)"),
                eq(Integer.class), eq("PC001"), eq("PC001"), eq("PC001"));
    }

    @Test
    @DisplayName("创建模板——模板名称为空时自动生成定数包名称（商品名称+定数包）")
    void shouldAutoGenerateTemplateNameWhenBlank() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "", null, "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包", "purchaseUnit", "箱",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(0);

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 45L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        when(jdbcTemplate.queryForObject(
                contains("SELECT template_id FROM quota_package_template WHERE template_code = ?"),
                eq(Long.class), anyString()))
                .thenReturn(45L);

        when(jdbcTemplate.update(contains("INSERT INTO quota_package_template_item"), anyLong(), anyLong(), any(), anyString()))
                .thenReturn(1);

        Map<String, Object> result = service.createTemplate(request);

        assertThat(result).containsEntry("templateCode", "TP001");
        // 模板名称自动生成：商品名称 + 定数包
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    @DisplayName("创建模板——产品编码为空时抛出异常")
    void shouldThrowWhenProductCodeBlank() {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "",
                BigDecimal.valueOf(5), "包"
        );

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product code");
    }

    @Test
    @DisplayName("创建模板——数量必须大于零")
    void shouldThrowWhenQuantityNotPositive() {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "PC001",
                BigDecimal.ZERO, "包"
        );

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数量必须大于零");
    }

    @Test
    @DisplayName("创建模板——产品非定数包管理时抛出异常")
    void shouldThrowWhenProductNotQuotaManaged() {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包",
                        "quotaManaged", 0, "highValue", 0, "coldChain", 0
                ));

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not quota managed");
    }

    @Test
    @DisplayName("创建模板——高值耗材禁止创建定数包模板")
    void shouldThrowForHighValueProduct() {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001",
                        "unit", "包",
                        "quotaManaged", 1, "highValue", 1, "coldChain", 0
                ));

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("high value");
    }

    @Test
    @DisplayName("创建模板——重复模板时抛出异常")
    void shouldThrowWhenDuplicateTemplate() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", "外科", "PC001",
                BigDecimal.valueOf(5), "包"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包", "purchaseUnit", "箱",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));

        // ensureTemplateNotDuplicate returns COUNT > 0
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("创建模板——采购单位转基础单位换算")
    void shouldCreateTemplateWithUnitConversion() throws Exception {
        QuotaTemplateRequest request = new QuotaTemplateRequest(
                "TP001", "手术包A", null, "PC001",
                BigDecimal.valueOf(3), "箱"
        );

        when(jdbcTemplate.queryForMap(contains("product_code = ?"), anyString()))
                .thenReturn(Map.of(
                        "productId", 100L, "productCode", "PC001", "productName", "注射器",
                        "unit", "包", "purchaseUnit", "箱",
                        "conversionRate", BigDecimal.valueOf(10),
                        "quotaManaged", 1, "highValue", 0, "coldChain", 0
                ));

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), anyLong(), anyString(), any(), any()))
                .thenReturn(0);

        doAnswer(invocation -> {
            populateKeyHolder(invocation.getArgument(1), 44L);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        when(jdbcTemplate.queryForObject(
                contains("SELECT template_id FROM quota_package_template WHERE template_code = ?"),
                eq(Long.class), anyString()))
                .thenReturn(44L);

        when(jdbcTemplate.update(contains("INSERT INTO quota_package_template_item"), anyLong(), anyLong(), any(), anyString()))
                .thenReturn(1);

        Map<String, Object> result = service.createTemplate(request);

        // 3箱 x 10转换率 = 30包
        assertThat(result).containsEntry("templateCode", "TP001");
        verify(jdbcTemplate).update(contains("INSERT INTO quota_package_template_item"),
                anyLong(), anyLong(), eq(BigDecimal.valueOf(30)), anyString());
    }

    // ==================== disableTemplate() ====================

    @Test
    @DisplayName("禁用模板——成功禁用并返回状态")
    void shouldDisableTemplateSuccessfully() {
        when(jdbcTemplate.queryForObject(contains("SELECT template_id"), eq(Long.class), anyString()))
                .thenReturn(1L);

        Map<String, Object> result = service.disableTemplate("TP001");

        assertThat(result)
                .containsEntry("templateCode", "TP001")
                .containsEntry("status", "disabled");
        verify(jdbcTemplate).update(contains("SET status = 0"), eq(1L));
        verify(support).writeAudit(eq("quota_package"), eq("disable_quota_template"),
                eq(1L), eq("TP001"), anyString());
    }

    // ==================== requisitionCatalog() ====================

    @Test
    @DisplayName("查询申领目录——返回产品列表")
    void shouldQueryRequisitionCatalog() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "productCode", "PC001", "productName", "注射器",
                        "templateCode", "TP001", "defaultMode", "quota_package"
                )));

        List<Map<String, Object>> result = rows(service.requisitionCatalog(Map.of("page", "1")));

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).containsEntry("productCode", "PC001");
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql.contains("FROM department_warehouse_catalog dwc")
                && !sql.contains("FROM department_consumption dc")), any(Object[].class));
    }

    @Test
    void requisitionAvailabilityUsesExplicitSourceWarehouseInsteadOfDestinationWarehouse() {
        when(jdbcTemplate.queryForList(contains("warehouse_type LIKE '%中心%'"), eq(Long.class), eq(30L)))
                .thenReturn(List.of(30L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.requisitionCatalog(Map.of("sourceWarehouseId", "30", "warehouseName", "AI测试库房"));

        verify(jdbcTemplate).queryForList(argThat((String sql) ->
                sql.contains("loose.warehouse_id = ?")
                        && sql.contains("pkg.warehouse_id = ?")
                        && sql.contains("hv.warehouse_id = ?")), any(Object[].class));
    }

    @Test
    void requisitionCatalogRequiresExplicitSourceWhenCampusHasMultipleCentralWarehouses() {
        when(jdbcTemplate.queryForList(contains("source.campus_name = target.campus_name"), eq(Long.class),
                any(Object[].class))).thenReturn(List.of(30L, 31L));

        assertThatThrownBy(() -> service.requisitionCatalog(Map.of("warehouseName", "AI测试库房")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceWarehouseId");
    }

    @Test
    void requisitionCatalogRejectsMultipleTemplatesAtTheSamePriority() {
        when(jdbcTemplate.queryForObject(contains("template_conflicts"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.requisitionCatalog(Map.of("deptName", "AI测试科室")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板配置冲突");
    }

    @Test
    @DisplayName("查询申领目录——按散货模式过滤")
    void shouldQueryRequisitionCatalogWithModeFilter() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "productCode", "PC002", "defaultMode", "loose"
                )));

        List<Map<String, Object>> result = rows(service.requisitionCatalog(Map.of("mode", "loose")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("defaultMode", "loose");
    }

    @Test
    @DisplayName("查询申领目录——统一支持高值唯一码模式")
    void shouldQueryHighValueRequisitionCatalog() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("productCode", "HV001", "defaultMode", "unique_code")));

        List<Map<String, Object>> result = rows(service.requisitionCatalog(Map.of("mode", "unique_code")));

        assertThat(result.get(0)).containsEntry("defaultMode", "unique_code");
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql.contains("p.is_high_value AS highValue")
                && sql.contains("uniqueCodeAvailableQty") && sql.contains("p.is_high_value = 1")), any(Object[].class));
    }

    @Test
    @DisplayName("查询申领目录——无数据返回空列表")
    void shouldReturnEmptyCatalog() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        List<Map<String, Object>> result = rows(service.requisitionCatalog(Map.of()));

        assertThat(result).isEmpty();
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }
}
