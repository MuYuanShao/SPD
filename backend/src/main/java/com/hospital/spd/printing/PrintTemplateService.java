package com.hospital.spd.printing;

import static com.hospital.spd.common.SqlHelper.isBlank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns print template configurations: field layout, custom fields, and paper settings.
 */
@Service
public class PrintTemplateService {
    private static final BigDecimal MAX_PAPER_DIMENSION = new BigDecimal("999999.99");

    private final JdbcTemplate jdbcTemplate;

    public PrintTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList("""
                SELECT template_id AS id, template_code AS templateCode, template_name AS templateName,
                       template_type AS templateType, fields_json AS fieldsJson,
                       paper_width_mm AS paperWidthMm, paper_height_mm AS paperHeightMm,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS updateTime,
                       status, remark
                  FROM print_template
                 WHERE deleted = 0
                 ORDER BY template_id
                """);
    }

    public Map<String, Object> get(String templateType) {
        return findDefault(templateType).orElseThrow(() -> new IllegalArgumentException("打印模板不存在或已停用"));
    }

    private Optional<Map<String, Object>> findDefault(String templateType) {
        return jdbcTemplate.queryForList("""
                SELECT template_id AS id, template_code AS templateCode, template_name AS templateName,
                       template_type AS templateType, fields_json AS fieldsJson,
                       paper_width_mm AS paperWidthMm, paper_height_mm AS paperHeightMm,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS updateTime,
                       status, remark
                  FROM print_template
                 WHERE template_type = ? AND deleted = 0
                 ORDER BY template_id
                 LIMIT 1
                """, templateType).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> activeQuotaLabelTemplates() {
        var templates = jdbcTemplate.queryForList("""
                SELECT template_id AS id, template_code AS templateCode, template_name AS templateName,
                       template_type AS templateType, fields_json AS fieldsJson,
                       paper_width_mm AS paperWidthMm, paper_height_mm AS paperHeightMm, status
                  FROM print_template WHERE template_type = 'quota_label' AND deleted = 0 AND status = 1
                 ORDER BY template_id
                """);
        Object defaultTemplateId = findDefault("quota_label")
                .map(row -> row.get("id")).filter(id -> templates.stream().anyMatch(row -> id.equals(row.get("id"))))
                .orElse(0L);
        return Map.of("templates", templates, "defaultTemplateId", defaultTemplateId);
    }

    @Transactional
    public Map<String, Object> upsert(String templateType, PrintTemplateUpsertRequest request) {
        validate(request);
        var existing = findDefault(templateType);
        if (existing.isEmpty()) {
            insert(templateType, templateType, request);
        } else {
            update(((Number) existing.get().get("id")).longValue(), request);
        }
        return Map.of("templateType", templateType);
    }

    @Transactional
    public Map<String, Object> createQuotaLabel(String code, PrintTemplateUpsertRequest request) {
        if (code == null || !code.trim().matches("[a-zA-Z0-9_-]{1,50}")) {
            throw new IllegalArgumentException("模板编码须为 1 至 50 位字母、数字、下划线或短横线");
        }
        validate(request);
        String templateCode = code.trim();
        insert("quota_label", templateCode, request);
        return Map.of("templateCode", templateCode);
    }

    @Transactional
    public Map<String, Object> update(long id, PrintTemplateUpsertRequest request) {
        validate(request);
        int updated = jdbcTemplate.update("""
                UPDATE print_template
                   SET template_name = ?, fields_json = CAST(? AS JSON),
                       paper_width_mm = ?, paper_height_mm = ?, status = ?, remark = ?
                 WHERE template_id = ? AND deleted = 0
                """, request.templateName().trim(), fieldsJson(request),
                request.paperWidthMm(), request.paperHeightMm(), status(request), request.remark(), id);
        if (updated == 0 && jdbcTemplate.queryForList(
                "SELECT template_id FROM print_template WHERE template_id = ? AND deleted = 0",
                Long.class, id).isEmpty()) {
            throw new IllegalArgumentException("打印模板不存在");
        }
        return Map.of("id", id);
    }

    private void validate(PrintTemplateUpsertRequest request) {
        if (request == null || isBlank(request.templateName())) {
            throw new IllegalArgumentException("模板名称为必填项");
        }
        if (!validPaperDimension(request.paperWidthMm()) || !validPaperDimension(request.paperHeightMm())) {
            throw new IllegalArgumentException("纸张宽度和高度须为 0.01 至 999999.99 mm，最多两位小数");
        }
        if (request.templateName().trim().length() > 100 || (request.remark() != null && request.remark().length() > 500)) {
            throw new IllegalArgumentException("模板名称最多 100 字，备注最多 500 字");
        }
    }

    private boolean validPaperDimension(BigDecimal value) {
        return value != null && value.signum() > 0 && value.compareTo(MAX_PAPER_DIMENSION) <= 0
                && value.stripTrailingZeros().scale() <= 2;
    }

    private void insert(String templateType, String templateCode, PrintTemplateUpsertRequest request) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO print_template (
                      template_code, template_name, template_type, fields_json,
                      paper_width_mm, paper_height_mm, status, remark
                    ) VALUES (?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?)
                    """, templateCode, request.templateName().trim(), templateType, fieldsJson(request),
                    request.paperWidthMm(), request.paperHeightMm(), status(request), request.remark());
        } catch (DuplicateKeyException error) {
            throw new IllegalArgumentException("模板编码已存在");
        }
    }

    private String fieldsJson(PrintTemplateUpsertRequest request) {
        return isBlank(request.fieldsJson()) ? "[]" : request.fieldsJson().trim();
    }

    private int status(PrintTemplateUpsertRequest request) {
        return Integer.valueOf(0).equals(request.status()) ? 0 : 1;
    }
}
