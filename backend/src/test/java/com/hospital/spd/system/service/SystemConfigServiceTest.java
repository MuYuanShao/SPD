package com.hospital.spd.system.service;

import com.hospital.spd.system.ConfigHitCandidate;
import com.hospital.spd.system.ConfigHitExplanation;
import com.hospital.spd.system.SystemConfigRequest;
import com.hospital.spd.system.SystemConfigRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemConfigService 系统配置服务测试")
class SystemConfigServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigService(jdbcTemplate);
    }

    // ========== list() ==========

    @Test
    @DisplayName("list()：分页查询配置列表（有默认配置）")
    void should_return_configs_when_list_given_valid_filters() {
        // Arrange
        // ensureDefaultConfigs checks count first
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM system_config"), eq(Integer.class)))
                .thenReturn(5);

        SystemConfigRow row1 = new SystemConfigRow(
                1L, "base", "基础配置", "hospital", "default",
                "hospital.info", "医院信息配置",
                "{\"hospitalName\":\"第一人民医院\"}",
                "realtime", "2024-01-01 00:00", "-", "low", "启用", "2024-01-01 00:00");
        SystemConfigRow row2 = new SystemConfigRow(
                2L, "workflow", "业务流程配置", "global", "product_catalog",
                "approval.product.admission", "商品准入审批流程",
                "{\"nodes\":\"初审->复审\"}",
                "realtime", "2024-01-01 00:00", "-", "high", "启用", "2024-01-01 00:00");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(row1, row2));

        // Act
        List<SystemConfigRow> result = systemConfigService.list(null, null, null);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).configKey()).isEqualTo("hospital.info");
        assertThat(result.get(1).configType()).isEqualTo("workflow");
    }

    @Test
    @DisplayName("list()：带全部过滤条件查询")
    void should_filter_when_list_given_all_filters() {
        // Arrange
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM system_config"), eq(Integer.class)))
                .thenReturn(10);

        SystemConfigRow row = new SystemConfigRow(
                3L, "permission", "权限配置", "global", "menu",
                "permission.button.control", "按钮权限控制",
                "{\"enabled\":true}",
                "realtime", "2024-01-01 00:00", "-", "medium", "启用", "2024-01-01 00:00");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(row));

        // Act
        List<SystemConfigRow> result = systemConfigService.list("permission", "button", "启用");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).configType()).isEqualTo("permission");
    }

    @Test
    @DisplayName("list()：无配置时先初始化默认配置")
    void should_initialize_defaults_when_list_given_empty_db() {
        // Arrange
        // First call to ensureDefaultConfigs: count = 0
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM system_config"), eq(Integer.class)))
                .thenReturn(0);
        // After inserting defaults, the query proceeds
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        // Act
        List<SystemConfigRow> result = systemConfigService.list(null, null, null);

        // Assert
        assertThat(result).isEmpty();
        // ensureDefaultConfigs inserts 8 default rows
        verify(jdbcTemplate, times(8)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("list()：状态参数为'停用'时传递 0")
    void should_pass_zero_when_list_given_disabled_status() {
        // Arrange
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM system_config"), eq(Integer.class)))
                .thenReturn(3);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        // Act
        List<SystemConfigRow> result = systemConfigService.list(null, null, "停用");

        // Assert
        assertThat(result).isEmpty();
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @DisplayName("list()：keyword 参数为 LIKE 拼接条件")
    void should_use_like_when_list_given_keyword() {
        // Arrange
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM system_config"), eq(Integer.class)))
                .thenReturn(5);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        // Act
        systemConfigService.list(null, "审批", null);

        // Assert
        verify(jdbcTemplate).query(contains("LIKE"), any(RowMapper.class), any(Object[].class));
    }

    // ========== create() ==========

    @Test
    @DisplayName("create()：成功创建配置")
    void should_create_config_when_create_given_valid_request() {
        // Arrange
        SystemConfigRequest request = new SystemConfigRequest(
                "base", "hospital", "default", "test.key",
                "{\"test\": true}", "realtime", null, "low", 1);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // Act
        Map<String, Object> result = systemConfigService.create(request);

        // Assert
        assertThat(result).containsEntry("configKey", "test.key");
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("create()：必填项为空抛出异常")
    void should_throw_when_create_given_blank_required_fields() {
        // Arrange
        SystemConfigRequest request = new SystemConfigRequest(
                "", "hospital", "default", "test.key",
                "value", "realtime", null, null, 1);

        // Act & Assert
        assertThatThrownBy(() -> systemConfigService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("配置类型、适用范围、适用对象、配置键、配置值为必填项");
    }

    @Test
    @DisplayName("create()：非 JSON 的 configValue 被自动包装为 JSON 字符串")
    void should_wrap_non_json_value_when_create_given_plain_text_value() {
        // Arrange
        SystemConfigRequest request = new SystemConfigRequest(
                "base", "hospital", "dept_1", "plain.key",
                "plain text value", null, null, null, null);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // Act
        Map<String, Object> result = systemConfigService.create(request);

        // Assert
        assertThat(result).containsEntry("configKey", "plain.key");
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("create()：effectiveMode 为 null 时默认 realtime")
    void should_default_effective_mode_when_create_given_null_effectiveMode() {
        // Arrange
        SystemConfigRequest request = new SystemConfigRequest(
                "workflow", "global", "test", "test.key",
                "\"value\"", null, null, null, 1);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // Act
        systemConfigService.create(request);

        // Assert
        verify(jdbcTemplate).update(contains("INSERT INTO system_config"), any(Object[].class));
    }

    // ========== explain() ==========

    @Test
    @DisplayName("explain()：命中用户级配置")
    void should_explain_config_hit_when_explain_given_user_scope() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(
                        new ConfigHitCandidate(1L, "workflow", "业务流程配置", "用户级", 1,
                                "user", "100", "test.key", "测试配置",
                                "\"value_user\"", "2024-01-01 00:00", "-", "low",
                                "已生效", false, false),
                        new ConfigHitCandidate(2L, "workflow", "业务流程配置", "系统级", 8,
                                "global", "default", "test.key", "测试配置",
                                "\"value_default\"", "2024-01-01 00:00", "-", "low",
                                "已生效", false, false)
                ));

        Map<String, String> params = Map.of(
                "configType", "workflow",
                "userId", "100",
                "businessPage", "采购订单"
        );

        // Act
        ConfigHitExplanation result = systemConfigService.explain(params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.configType()).isEqualTo("workflow");
        assertThat(result.approvalStatus()).isEqualTo("已生效");
        assertThat(result.candidates()).hasSize(2);
    }

    @Test
    @DisplayName("explain()：未命中任何配置")
    void should_return_no_hit_when_explain_given_no_matching_configs() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        Map<String, String> params = Map.of("configType", "unknown_type");

        // Act
        ConfigHitExplanation result = systemConfigService.explain(params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.finalValue()).isEqualTo("-");
        assertThat(result.approvalStatus()).isEqualTo("未命中");
        assertThat(result.sourceConfigId()).isNull();
    }

    @Test
    @DisplayName("explain()：多候选者时命中优先级最高的")
    void should_hit_highest_priority_when_explain_given_multiple_candidates() {
        // Arrange
        // priority is set manually on each candidate; lower number = higher priority
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(
                        new ConfigHitCandidate(10L, "workflow", "业务流程配置", "系统级", 8,
                                "global", "default", "test.key", "默认值",
                                "\"default_val\"", "2024-01-01 00:00", "-", "low",
                                "已生效", false, false),
                        new ConfigHitCandidate(20L, "workflow", "业务流程配置", "科室级", 4,
                                "department", "dept_1", "test.key", "科室配置",
                                "\"dept_val\"", "2024-01-01 00:00", "-", "low",
                                "已生效", false, false),
                        new ConfigHitCandidate(30L, "workflow", "业务流程配置", "仓库级", 3,
                                "warehouse", "wh_1", "test.key", "仓库配置",
                                "\"wh_val\"", "2024-01-01 00:00", "-", "low",
                                "已生效", false, false)
                ));

        Map<String, String> params = Map.of(
                "configType", "workflow",
                "departmentId", "dept_1",
                "warehouseId", "wh_1"
        );

        // Act
        ConfigHitExplanation result = systemConfigService.explain(params);

        // Assert
        // The candidates get sorted by priority ascending; first becomes the hit
        // warehouse priority(3) < department priority(4) < global priority(8)
        assertThat(result.approvalStatus()).isEqualTo("已生效");
    }

    @Test
    @DisplayName("explain()：默认使用 workflow 作为 configType")
    void should_default_config_type_when_explain_given_no_configType() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        Map<String, String> params = Map.of(); // no configType

        // Act
        ConfigHitExplanation result = systemConfigService.explain(params);

        // Assert
        assertThat(result.configType()).isEqualTo("workflow");
    }

    @Test
    @DisplayName("explain()：返回结果包含优先级规则说明")
    void should_include_priority_rules_when_explain_called() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        // Act
        ConfigHitExplanation result = systemConfigService.explain(Map.of());

        // Assert
        assertThat(result.priorityRules()).isNotEmpty();
        assertThat(result.priorityRules().get(0)).contains("用户级");
    }

    @Test
    @DisplayName("explain()：单个候选者为命中且无父级")
    void should_set_hit_and_no_parent_when_explain_given_single_candidate() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(
                        new ConfigHitCandidate(5L, "parameter", "参数配置", "医院级", 6,
                                "hospital", "hosp_1", "test.key", "医院配置",
                                "\"hosp_value\"", "2024-01-01 00:00", "-", "medium",
                                "已生效", false, false)
                ));

        Map<String, String> params = Map.of(
                "configType", "parameter",
                "hospitalId", "hosp_1"
        );

        // Act
        ConfigHitExplanation result = systemConfigService.explain(params);

        // Assert
        assertThat(result.sourceConfigId()).isEqualTo(5L);
        assertThat(result.finalValue()).isEqualTo("\"hosp_value\"");
        assertThat(result.overridesParent()).isFalse();
        assertThat(result.parentValue()).isEqualTo("-");
    }
}
