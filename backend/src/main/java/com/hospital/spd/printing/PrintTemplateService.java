package com.hospital.spd.printing;

import static com.hospital.spd.common.SqlHelper.isBlank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Owns print template configurations: field layout, custom fields, and paper settings.
 */
@Service
public class PrintTemplateService {

    private final JdbcTemplate jdbcTemplate;

    public PrintTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList("""
                SELECT template_id AS id, template_code AS templateCode, template_name AS templateName,
                       template_type AS templateType, fields_json AS fieldsJson,
                       paper_width_mm AS paperWidthMm, paper_height_mm AS paperHeightMm,
                       status, remark
                  FROM print_template
                 WHERE deleted = 0
                 ORDER BY template_id
                """);
    }

    public Map<String, Object> get(String templateType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT template_id AS id, template_code AS templateCode, template_name AS templateName,
                       template_type AS templateType, fields_json AS fieldsJson,
                       paper_width_mm AS paperWidthMm, paper_height_mm AS paperHeightMm,
                       status, remark
                  FROM print_template
                 WHERE template_type = ? AND deleted = 0
                 LIMIT 1
                """, templateType);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("打印模板不存在或已停用");
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> upsert(String templateType, PrintTemplateUpsertRequest request) {
        if (isBlank(request.templateName())) {
            throw new IllegalArgumentException("模板名称为必填项");
        }
        if (request.paperWidthMm() == null || request.paperWidthMm().compareTo(BigDecimal.ZERO) <= 0
                || request.paperHeightMm() == null || request.paperHeightMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("纸张宽度与高度必须大于 0");
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT template_id FROM print_template WHERE template_type = ? AND deleted = 0 LIMIT 1",
                Integer.class, templateType);
        String fieldsJson = isBlank(request.fieldsJson()) ? "[]" : request.fieldsJson().trim();
        int status = request.status() != null && request.status() == 0 ? 0 : 1;
        if (existing == null) {
            jdbcTemplate.update("""
                    INSERT INTO print_template (
                      template_code, template_name, template_type, fields_json,
                      paper_width_mm, paper_height_mm, status, remark
                    ) VALUES (?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?)
                    """, templateType, request.templateName().trim(), templateType, fieldsJson,
                    request.paperWidthMm(), request.paperHeightMm(), status, request.remark());
        } else {
            jdbcTemplate.update("""
                    UPDATE print_template
                       SET template_name = ?, fields_json = CAST(? AS JSON),
                           paper_width_mm = ?, paper_height_mm = ?, status = ?, remark = ?
                     WHERE template_type = ? AND deleted = 0
                    """, request.templateName().trim(), fieldsJson,
                    request.paperWidthMm(), request.paperHeightMm(), status, request.remark(), templateType);
        }
        return Map.of("templateType", templateType);
    }
}
