package com.hospital.spd.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageRequest 分页请求测试")
class PageRequestTest {

    @Test
    @DisplayName("from()：无参数时使用默认值 page=1, size=20")
    void should_use_defaults_when_from_given_empty_params() {
        PageRequest pr = PageRequest.from(Map.of());
        assertThat(pr.page()).isEqualTo(1);
        assertThat(pr.size()).isEqualTo(20);
        assertThat(pr.offset()).isEqualTo(0);
    }

    @Test
    @DisplayName("from()：自定义 page 和 size")
    void should_use_custom_values_when_from_given_valid_params() {
        PageRequest pr = PageRequest.from(Map.of("page", "3", "size", "15"));
        assertThat(pr.page()).isEqualTo(3);
        assertThat(pr.size()).isEqualTo(15);
        assertThat(pr.offset()).isEqualTo(30);
    }

    @Test
    @DisplayName("from()：负数 page 兜底为 1")
    void should_default_page_to_one_when_from_given_negative_page() {
        PageRequest pr = PageRequest.from(Map.of("page", "-5", "size", "20"));
        assertThat(pr.page()).isEqualTo(1);
        assertThat(pr.offset()).isEqualTo(0);
    }

    @Test
    @DisplayName("from()：负数 size 兜底为 DEFAULT_SIZE(20)")
    void should_default_size_to_default_when_from_given_negative_size() {
        PageRequest pr = PageRequest.from(Map.of("page", "1", "size", "-1"));
        assertThat(pr.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("from()：零 size 兜底为 DEFAULT_SIZE(20)")
    void should_default_size_to_default_when_from_given_zero_size() {
        PageRequest pr = PageRequest.from(Map.of("page", "1", "size", "0"));
        assertThat(pr.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("from()：size 超过 MAX_SIZE(200) 时截断")
    void should_cap_size_when_from_given_excessive_size() {
        PageRequest pr = PageRequest.from(Map.of("page", "1", "size", "500"));
        assertThat(pr.size()).isEqualTo(200);
    }

    @Test
    @DisplayName("from()：边界值 size=MAX_SIZE 不被截断")
    void should_keep_size_when_from_given_max_size() {
        PageRequest pr = PageRequest.from(Map.of("page", "1", "size", "200"));
        assertThat(pr.size()).isEqualTo(200);
    }

    @Test
    @DisplayName("from()：offset 计算正确（page=2, size=20 → offset=20）")
    void should_calculate_offset_correctly() {
        PageRequest pr = PageRequest.from(Map.of("page", "2", "size", "20"));
        assertThat(pr.offset()).isEqualTo(20);
    }

    @Test
    @DisplayName("from()：非数字参数兜底为默认值")
    void should_default_when_from_given_non_numeric_params() {
        PageRequest pr = PageRequest.from(Map.of("page", "abc", "size", "xyz"));
        assertThat(pr.page()).isEqualTo(1);
        assertThat(pr.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("from()：null 参数兜底为默认值")
    void should_default_when_from_given_null_params() {
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("page", null);
        params.put("size", null);
        PageRequest pr = PageRequest.from(params);
        assertThat(pr.page()).isEqualTo(1);
        assertThat(pr.size()).isEqualTo(20);
    }
}
