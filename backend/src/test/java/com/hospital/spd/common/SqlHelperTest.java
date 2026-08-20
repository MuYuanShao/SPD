package com.hospital.spd.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlHelper 工具类测试")
class SqlHelperTest {

    // ========== isBlank ==========

    @Test
    @DisplayName("isBlank：null 返回 true")
    void should_return_true_when_isBlank_given_null() {
        assertThat(SqlHelper.isBlank(null)).isTrue();
    }

    @Test
    @DisplayName("isBlank：空字符串返回 true")
    void should_return_true_when_isBlank_given_empty() {
        assertThat(SqlHelper.isBlank("")).isTrue();
    }

    @Test
    @DisplayName("isBlank：空格返回 true")
    void should_return_true_when_isBlank_given_blank() {
        assertThat(SqlHelper.isBlank("   ")).isTrue();
    }

    @Test
    @DisplayName("isBlank：正常字符串返回 false")
    void should_return_false_when_isBlank_given_normal() {
        assertThat(SqlHelper.isBlank("hello")).isFalse();
    }

    // ========== nullIfBlank ==========

    @Test
    @DisplayName("nullIfBlank：null 返回 null")
    void should_return_null_when_nullIfBlank_given_null() {
        assertThat(SqlHelper.nullIfBlank(null)).isNull();
    }

    @Test
    @DisplayName("nullIfBlank：空字符串返回 null")
    void should_return_null_when_nullIfBlank_given_empty() {
        assertThat(SqlHelper.nullIfBlank("")).isNull();
    }

    @Test
    @DisplayName("nullIfBlank：空格返回 null")
    void should_return_null_when_nullIfBlank_given_blank() {
        assertThat(SqlHelper.nullIfBlank("   ")).isNull();
    }

    @Test
    @DisplayName("nullIfBlank：正常字符串返回去除空格后的字符串")
    void should_return_trimmed_when_nullIfBlank_given_normal() {
        assertThat(SqlHelper.nullIfBlank("  hello  ")).isEqualTo("hello");
    }

    // ========== appendLike(String) ==========

    @Test
    @DisplayName("appendLike：正常值自动包裹百分号")
    void should_wrap_percent_signs_when_appendLike_given_normal() {
        assertThat(SqlHelper.appendLike("hello")).isEqualTo("%hello%");
    }

    @Test
    @DisplayName("appendLike：null 值返回空百分号")
    void should_return_only_percent_when_appendLike_given_null() {
        assertThat(SqlHelper.appendLike(null)).isEqualTo("%%");
    }

    @Test
    @DisplayName("appendLike：空字符串返回空百分号")
    void should_return_only_percent_when_appendLike_given_empty() {
        assertThat(SqlHelper.appendLike("")).isEqualTo("%%");
    }

    @Test
    @DisplayName("appendLike：已含百分号的值正常包裹")
    void should_keep_existing_percent_when_appendLike_given_already_has_percent() {
        assertThat(SqlHelper.appendLike("%hello%")).isEqualTo("%%hello%%");
    }

    // ========== defaultDecimal(String, BigDecimal) ==========

    @Test
    @DisplayName("defaultDecimal：正常数字字符串返回对应 BigDecimal")
    void should_return_bigdecimal_when_defaultDecimal_given_valid_number() {
        assertThat(SqlHelper.defaultDecimal("123.45", BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("defaultDecimal：无效数字字符串返回默认值")
    void should_return_default_when_defaultDecimal_given_invalid_number() {
        assertThat(SqlHelper.defaultDecimal("abc", BigDecimal.TEN))
                .isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("defaultDecimal：null 字符串返回默认值")
    void should_return_default_when_defaultDecimal_given_null() {
        assertThat(SqlHelper.defaultDecimal((String) null, BigDecimal.ONE))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("defaultDecimal：空字符串返回默认值")
    void should_return_default_when_defaultDecimal_given_empty() {
        assertThat(SqlHelper.defaultDecimal("", BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== defaultDecimal(BigDecimal, BigDecimal) ==========

    @Test
    @DisplayName("defaultDecimal(BigDecimal)：null 返回默认值")
    void should_return_default_when_defaultDecimal_bigdecimal_given_null() {
        assertThat(SqlHelper.defaultDecimal((BigDecimal) null, BigDecimal.TEN))
                .isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("defaultDecimal(BigDecimal)：非 null 返回原值")
    void should_return_original_when_defaultDecimal_bigdecimal_given_value() {
        assertThat(SqlHelper.defaultDecimal(new BigDecimal("42"), BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("42"));
    }

    // ========== parseDate ==========

    @Test
    @DisplayName("parseDate：合法日期字符串返回 LocalDate")
    void should_return_localdate_when_parseDate_given_valid() {
        assertThat(SqlHelper.parseDate("2024-01-15")).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("parseDate：非法日期字符串返回 null")
    void should_return_null_when_parseDate_given_invalid() {
        assertThat(SqlHelper.parseDate("not-a-date")).isNull();
    }

    @Test
    @DisplayName("parseDate：null 返回 null")
    void should_return_null_when_parseDate_given_null() {
        assertThat(SqlHelper.parseDate(null)).isNull();
    }

    @Test
    @DisplayName("parseDate：空字符串返回 null")
    void should_return_null_when_parseDate_given_empty() {
        assertThat(SqlHelper.parseDate("")).isNull();
    }

    // ========== defaultText ==========

    @Test
    @DisplayName("defaultText：null 返回默认值")
    void should_return_default_when_defaultText_given_null() {
        assertThat(SqlHelper.defaultText(null, "默认")).isEqualTo("默认");
    }

    @Test
    @DisplayName("defaultText：空字符串返回默认值")
    void should_return_default_when_defaultText_given_empty() {
        assertThat(SqlHelper.defaultText("", "默认")).isEqualTo("默认");
    }

    @Test
    @DisplayName("defaultText：正常值返回原值去除空格")
    void should_return_trimmed_when_defaultText_given_normal() {
        assertThat(SqlHelper.defaultText("  你好 ", "默认")).isEqualTo("你好");
    }

    // ========== emptyToNull(List) ==========

    @Test
    @DisplayName("emptyToNull(List)：null 返回 null")
    void should_return_null_when_emptyToNull_list_given_null() {
        assertThat(SqlHelper.emptyToNull((java.util.List<String>) null)).isNull();
    }

    @Test
    @DisplayName("emptyToNull(List)：空列表返回 null")
    void should_return_null_when_emptyToNull_list_given_empty() {
        assertThat(SqlHelper.emptyToNull(java.util.List.<String>of())).isNull();
    }

    @Test
    @DisplayName("emptyToNull(List)：非空列表返回原列表")
    void should_return_list_when_emptyToNull_list_given_non_empty() {
        List<String> list = List.of("a", "b");
        assertThat(SqlHelper.emptyToNull(list)).isSameAs(list);
    }

    // ========== positive ==========

    @Test
    @DisplayName("positive：正数返回原值")
    void should_return_value_when_positive_given_positive() {
        assertThat(SqlHelper.positive(new BigDecimal("5"), "必须为正数"))
                .isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("positive：零值抛出异常")
    void should_throw_when_positive_given_zero() {
        assertThatThrownBy(() -> SqlHelper.positive(BigDecimal.ZERO, "必须为正数"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("必须为正数");
    }

    @Test
    @DisplayName("positive：负数抛出异常")
    void should_throw_when_positive_given_negative() {
        assertThatThrownBy(() -> SqlHelper.positive(new BigDecimal("-1"), "必须为正数"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("必须为正数");
    }

    @Test
    @DisplayName("positive：null 抛出异常")
    void should_throw_when_positive_given_null() {
        assertThatThrownBy(() -> SqlHelper.positive(null, "必须为正数"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("必须为正数");
    }

    // ========== nonNegative ==========

    @Test
    @DisplayName("nonNegative：正数返回原值")
    void should_return_value_when_nonNegative_given_positive() {
        assertThat(SqlHelper.nonNegative(new BigDecimal("10")))
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("nonNegative：零返回零")
    void should_return_zero_when_nonNegative_given_zero() {
        assertThat(SqlHelper.nonNegative(BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("nonNegative：负数返回零")
    void should_return_zero_when_nonNegative_given_negative() {
        assertThat(SqlHelper.nonNegative(new BigDecimal("-5")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("nonNegative：null 返回零")
    void should_return_zero_when_nonNegative_given_null() {
        assertThat(SqlHelper.nonNegative(null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== emptyToNull(String) ==========

    @Test
    @DisplayName("emptyToNull(String)：null 返回 null")
    void should_return_null_when_emptyToNull_string_given_null() {
        assertThat(SqlHelper.emptyToNull((String) null)).isNull();
    }

    @Test
    @DisplayName("emptyToNull(String)：空字符串返回 null")
    void should_return_null_when_emptyToNull_string_given_empty() {
        assertThat(SqlHelper.emptyToNull("")).isNull();
    }

    @Test
    @DisplayName("emptyToNull(String)：正常值返回去除空格后的字符串")
    void should_return_trimmed_when_emptyToNull_string_given_normal() {
        assertThat(SqlHelper.emptyToNull("  value  ")).isEqualTo("value");
    }

    // ========== nvl(Integer) ==========

    @Test
    @DisplayName("nvl：null 返回默认值")
    void should_return_default_when_nvl_given_null() {
        assertThat(SqlHelper.nvl(null, 99)).isEqualTo(99);
    }

    @Test
    @DisplayName("nvl：非 null 返回原值")
    void should_return_original_when_nvl_given_value() {
        assertThat(SqlHelper.nvl(42, 99)).isEqualTo(42);
    }

    // ========== blankDefault ==========

    @Test
    @DisplayName("blankDefault：null 返回默认值")
    void should_return_default_when_blankDefault_given_null() {
        assertThat(SqlHelper.blankDefault(null, "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("blankDefault：空格返回默认值")
    void should_return_default_when_blankDefault_given_blank() {
        assertThat(SqlHelper.blankDefault("   ", "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("blankDefault：正常值返回去除空格后的字符串")
    void should_return_trimmed_when_blankDefault_given_normal() {
        assertThat(SqlHelper.blankDefault("  hi  ", "default")).isEqualTo("hi");
    }

    // ========== nonEmpty ==========

    @Test
    @DisplayName("nonEmpty：非空列表返回原列表")
    void should_return_list_when_nonEmpty_given_non_empty() {
        List<String> list = List.of("a");
        assertThat(SqlHelper.nonEmpty(list, "列表不能为空")).isSameAs(list);
    }

    @Test
    @DisplayName("nonEmpty：空列表抛出异常")
    void should_throw_when_nonEmpty_given_empty() {
        assertThatThrownBy(() -> SqlHelper.nonEmpty(List.of(), "列表不能为空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("列表不能为空");
    }

    @Test
    @DisplayName("nonEmpty：null 抛出异常")
    void should_throw_when_nonEmpty_given_null() {
        assertThatThrownBy(() -> SqlHelper.nonEmpty(null, "列表不能为空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("列表不能为空");
    }

    // ========== placeholders ==========

    @Test
    @DisplayName("placeholders：生成正确数量的占位符")
    void should_generate_placeholders_when_placeholders_given_count() {
        assertThat(SqlHelper.placeholders(3)).isEqualTo("?,?,?");
    }

    @Test
    @DisplayName("placeholders：单个占位符")
    void should_generate_single_when_placeholders_given_one() {
        assertThat(SqlHelper.placeholders(1)).isEqualTo("?");
    }

    // ========== fallback / cleanFallback ==========

    @Test
    @DisplayName("fallback：null 返回 '-'")
    void should_return_dash_when_fallback_given_null() {
        assertThat(SqlHelper.fallback(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("fallback：空白返回 '-'")
    void should_return_dash_when_fallback_given_blank() {
        assertThat(SqlHelper.fallback("   ")).isEqualTo("-");
    }

    @Test
    @DisplayName("fallback：正常值返回原值")
    void should_return_value_when_fallback_given_normal() {
        assertThat(SqlHelper.fallback("abc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("cleanFallback：null 返回空串")
    void should_return_empty_when_cleanFallback_given_null() {
        assertThat(SqlHelper.cleanFallback(null)).isEqualTo("");
    }

    @Test
    @DisplayName("cleanFallback：'-' 返回空串")
    void should_return_empty_when_cleanFallback_given_dash() {
        assertThat(SqlHelper.cleanFallback("-")).isEqualTo("");
    }

    // ========== dateString ==========

    @Test
    @DisplayName("dateString：null 返回 '-'")
    void should_return_dash_when_dateString_given_null() {
        assertThat(SqlHelper.dateString(null)).isEqualTo("-");
    }

    // ========== jsonText ==========

    @Test
    @DisplayName("jsonText：正常值返回 JSON 数组文本")
    void should_return_json_array_when_jsonText_given_normal() {
        assertThat(SqlHelper.jsonText("hello")).isEqualTo("[\"hello\"]");
    }

    @Test
    @DisplayName("jsonText：null 返回 null")
    void should_return_null_when_jsonText_given_null() {
        assertThat(SqlHelper.jsonText(null)).isNull();
    }

    // ========== parseYesNo ==========

    @Test
    @DisplayName("parseYesNo：空值返回 fallback")
    void should_return_fallback_when_parseYesNo_given_blank() {
        assertThat(SqlHelper.parseYesNo("", true)).isTrue();
        assertThat(SqlHelper.parseYesNo(null, false)).isFalse();
    }

    @Test
    @DisplayName("parseYesNo：'是' 返回 true")
    void should_return_true_when_parseYesNo_given_yes() {
        assertThat(SqlHelper.parseYesNo("是", false)).isTrue();
    }

    @Test
    @DisplayName("parseYesNo：其他返回 false")
    void should_return_false_when_parseYesNo_given_no() {
        assertThat(SqlHelper.parseYesNo("否", true)).isFalse();
    }

    // ========== appendLike(StringBuilder) ==========

    @Test
    @DisplayName("appendLike(StringBuilder)：添加 LIKE 条件")
    void should_append_like_clause_when_appendLike_builder_given_normal() {
        StringBuilder sql = new StringBuilder("WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        SqlHelper.appendLike(sql, args, "u.name", "张三");
        assertThat(sql.toString()).contains("AND u.name LIKE ?");
        assertThat(args).containsExactly("%张三%");
    }

    @Test
    @DisplayName("appendLike(StringBuilder)：null 值不追加")
    void should_not_append_when_appendLike_builder_given_null() {
        StringBuilder sql = new StringBuilder("WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        SqlHelper.appendLike(sql, args, "u.name", null);
        assertThat(sql.toString()).isEqualTo("WHERE 1=1");
        assertThat(args).isEmpty();
    }

    // ========== prefer / firstNonBlank ==========

    @Test
    @DisplayName("prefer：非空返回原值")
    void should_return_value_when_prefer_given_non_blank() {
        assertThat(SqlHelper.prefer("hello", "fallback")).isEqualTo("hello");
    }

    @Test
    @DisplayName("prefer：空返回 fallback")
    void should_return_fallback_when_prefer_given_blank() {
        assertThat(SqlHelper.prefer("", "fallback")).isEqualTo("fallback");
    }

    @Test
    @DisplayName("firstNonBlank：返回第一个非空值")
    void should_return_first_non_blank_when_firstNonBlank_given_values() {
        assertThat(SqlHelper.firstNonBlank("", null, "  hello  ", "world")).isEqualTo("hello");
    }

    @Test
    @DisplayName("firstNonBlank：全部为空返回'未分类'")
    void should_return_uncategorized_when_firstNonBlank_given_all_blank() {
        assertThat(SqlHelper.firstNonBlank(null, "", "   ")).isEqualTo("未分类");
    }

    // ========== attachmentCategory ==========

    @Test
    @DisplayName("attachmentCategory：已知分类返回中文映射")
    void should_return_mapped_name_when_attachmentCategory_given_known() {
        assertThat(SqlHelper.attachmentCategory("registration")).isEqualTo("注册证");
        assertThat(SqlHelper.attachmentCategory("production_license")).isEqualTo("生产许可");
        assertThat(SqlHelper.attachmentCategory("business_license")).isEqualTo("经营许可");
    }

    @Test
    @DisplayName("attachmentCategory：未知分类返回原值")
    void should_return_original_when_attachmentCategory_given_unknown() {
        assertThat(SqlHelper.attachmentCategory("other")).isEqualTo("other");
    }

    @Test
    @DisplayName("attachmentCategory：null 返回默认值")
    void should_return_default_when_attachmentCategory_given_null() {
        assertThat(SqlHelper.attachmentCategory(null)).isEqualTo("资质材料");
    }

    // ========== defaultText(String[], int, String) ==========

    @Test
    @DisplayName("defaultText(String[])：正常索引返回元素")
    void should_return_element_when_defaultText_array_given_valid_index() {
        String[] cells = {"a", "b", "c"};
        assertThat(SqlHelper.defaultText(cells, 1, "default")).isEqualTo("b");
    }

    @Test
    @DisplayName("defaultText(String[])：越界索引返回默认值")
    void should_return_default_when_defaultText_array_given_out_of_bounds() {
        String[] cells = {"a"};
        assertThat(SqlHelper.defaultText(cells, 5, "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("defaultText(String[])：空白元素返回默认值")
    void should_return_default_when_defaultText_array_given_blank() {
        String[] cells = {"a", "  ", "c"};
        assertThat(SqlHelper.defaultText(cells, 1, "default")).isEqualTo("default");
    }

    // ========== toSqlDate ==========

    @Test
    @DisplayName("toSqlDate：空白返回 null")
    void should_return_null_when_toSqlDate_given_blank() {
        assertThat(SqlHelper.toSqlDate("")).isNull();
        assertThat(SqlHelper.toSqlDate(null)).isNull();
    }
}
