package com.hospital.spd.system.service;

import com.hospital.spd.common.SqlHelper;
import com.hospital.spd.system.FieldOptionUpsertRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the dropdown option dictionary used by table select fields across the system.
 */
@Service
public class FieldOptionService {

    private final JdbcTemplate jdbcTemplate;

    public FieldOptionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> options(String fieldKey) {
        if (SqlHelper.isBlank(fieldKey)) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT option_id AS optionId, field_key AS fieldKey, field_label AS fieldLabel,
                       option_value AS optionValue, option_label AS optionLabel,
                       sort_order AS sortOrder, status, remark
                  FROM sys_field_option
                 WHERE field_key = ? AND deleted = 0
                 ORDER BY sort_order, option_id
                """, fieldKey.trim());
    }

    public List<Map<String, Object>> fields() {
        return jdbcTemplate.queryForList("""
                SELECT field_key AS fieldKey, MAX(field_label) AS fieldLabel,
                       SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS optionCount
                  FROM sys_field_option
                 GROUP BY field_key
                 ORDER BY MIN(option_id)
                """);
    }

    public Map<String, Object> create(FieldOptionUpsertRequest request) {
        requireKeyAndLabels(request);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_field_option
                 WHERE field_key = ? AND option_value = ? AND deleted = 0
                """, Integer.class, request.fieldKey().trim(), request.optionValue().trim());
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该字段下已存在相同的选项值");
        }
        jdbcTemplate.update("""
                INSERT INTO sys_field_option (
                  field_key, field_label, option_value, option_label, sort_order, status, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                request.fieldKey().trim(),
                request.fieldLabel().trim(),
                request.optionValue().trim(),
                request.optionLabel().trim(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                SqlHelper.nullIfBlank(request.remark()));
        Long optionId = jdbcTemplate.queryForObject(
                "SELECT option_id FROM sys_field_option WHERE field_key = ? AND option_value = ? AND deleted = 0 LIMIT 1",
                Long.class, request.fieldKey().trim(), request.optionValue().trim());
        return Map.of("optionId", optionId);
    }

    public Map<String, Object> update(Long optionId, FieldOptionUpsertRequest request) {
        requireOption(optionId);
        if (SqlHelper.isBlank(request.optionLabel()) && SqlHelper.isBlank(request.fieldLabel())) {
            throw new IllegalArgumentException("选项显示名或字段名称至少填写一项");
        }
        jdbcTemplate.update("""
                UPDATE sys_field_option
                   SET field_label = COALESCE(NULLIF(?, ''), field_label),
                       option_label = COALESCE(NULLIF(?, ''), option_label),
                       sort_order = ?,
                       status = ?,
                       remark = ?
                 WHERE option_id = ? AND deleted = 0
                """,
                SqlHelper.nullIfBlank(request.fieldLabel()),
                SqlHelper.nullIfBlank(request.optionLabel()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                SqlHelper.nullIfBlank(request.remark()),
                optionId);
        return Map.of("optionId", optionId);
    }

    public Map<String, Object> remove(Long optionId) {
        requireOption(optionId);
        jdbcTemplate.update("UPDATE sys_field_option SET deleted = 1 WHERE option_id = ?", optionId);
        return Map.of("optionId", optionId);
    }

    private void requireOption(Long optionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_field_option WHERE option_id = ? AND deleted = 0",
                Integer.class, optionId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("选项不存在或已删除");
        }
    }

    private static void requireKeyAndLabels(FieldOptionUpsertRequest request) {
        if (SqlHelper.isBlank(request.fieldKey())) {
            throw new IllegalArgumentException("字段键为必填项");
        }
        if (SqlHelper.isBlank(request.fieldLabel())) {
            throw new IllegalArgumentException("字段名称为必填项");
        }
        if (SqlHelper.isBlank(request.optionValue())) {
            throw new IllegalArgumentException("选项值为必填项");
        }
        if (SqlHelper.isBlank(request.optionLabel())) {
            throw new IllegalArgumentException("选项显示名为必填项");
        }
    }
}
