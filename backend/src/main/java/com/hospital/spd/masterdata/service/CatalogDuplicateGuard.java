package com.hospital.spd.masterdata.service;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.hospital.spd.common.SqlHelper.isBlank;

/** Applies the same configured new-product identity rule to all catalog submission entry points. */
final class CatalogDuplicateGuard {
    private final JdbcTemplate jdbcTemplate;
    CatalogDuplicateGuard(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    record Identity(String productName, String specModel, String manufacturerName, String supplierName, String registrationNo) {}

    void requireCatalogAvailable(Identity request) {
        if (hospitalCatalogMatches(duplicateRuleFields(), request)) throw new IllegalArgumentException("该商品目录已存在！");
    }

    void requireAvailable(Identity request, String excludedApplicationNo) {
        List<String> fields = duplicateRuleFields();
        if (fields.isEmpty()) {
            return;
        }
        if (hospitalCatalogMatches(fields, request)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
        if (pendingCatalogMatches(fields, request, excludedApplicationNo)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    /**
     * Loads the duplicate-check field combination stored in sys_validation_rule.
     * Falls back to the documented rule when the rule table or row is unavailable.
     */
    private List<String> duplicateRuleFields() {
        try {
            String raw = jdbcTemplate.queryForObject("""
                    SELECT rule_fields
                      FROM sys_validation_rule
                     WHERE rule_code = 'pending_product_duplicate' AND status = 1
                     LIMIT 1
                    """, String.class);
            if (!isBlank(raw)) {
                List<String> fields = Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(field -> !field.isBlank() && productFieldExpression(field) != null)
                        .toList();
                if (!fields.isEmpty()) {
                    return fields;
                }
            }
        } catch (RuntimeException ignored) {
            // Rule storage unavailable; use the documented default below.
        }
        return List.of("product_name", "spec_model", "manufacturer_name", "supplier_name", "registration_no");
    }

    private boolean hospitalCatalogMatches(List<String> fields, Identity request) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted = 0");
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND ").append(productFieldExpression(field)).append(" = ?");
            args.add(requestFieldValue(request, field));
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM product p
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                """ + where + " FOR SHARE", Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private boolean pendingCatalogMatches(List<String> fields, Identity request,
                                          String excludedApplicationNo) {
        StringBuilder where = new StringBuilder("""
                 WHERE (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final', 'routing', 'returned'))
                """);
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND COALESCE(NULLIF(")
                    .append(pendingFieldExpression(field))
                    .append(", ''), '') = ?");
            args.add(requestFieldValue(request, field));
        }
        if (!isBlank(excludedApplicationNo)) {
            where.append(" AND a.application_no <> ?");
            args.add(excludedApplicationNo.trim());
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pending_product_application a
                  LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                """ + where + " FOR SHARE", Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private static String productFieldExpression(String field) {
        return switch (field) {
            case "product_name" -> "COALESCE(TRIM(p.product_name), '')";
            case "spec_model" -> "COALESCE(TRIM(p.spec_model), '')";
            case "manufacturer_name" -> "COALESCE(TRIM(m.manufacturer_name), '')";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), '')";
            case "registration_no" -> "COALESCE(TRIM(p.registration_no), '')";
            default -> null;
        };
    }

    private static String pendingFieldExpression(String field) {
        return switch (field) {
            case "product_name" -> "TRIM(a.product_name)";
            case "spec_model" -> "TRIM(a.spec_model)";
            case "manufacturer_name" -> "TRIM(a.manufacturer_name)";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), TRIM(a.supplier_name))";
            case "registration_no" -> "TRIM(a.registration_no)";
            default -> null;
        };
    }

    private static String requestFieldValue(Identity request, String field) {
        String value = switch (field) {
            case "product_name" -> request.productName();
            case "spec_model" -> request.specModel();
            case "manufacturer_name" -> request.manufacturerName();
            case "supplier_name" -> request.supplierName();
            case "registration_no" -> request.registrationNo();
            default -> null;
        };
        return value == null ? "" : value.trim();
    }

}
