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

    /**
     * Catalog identity used by the 待审批目录 flow, which no longer collects a 规格型号.
     */
    record Identity(String productName, String manufacturerName, String supplierName, String registrationNo) {}

    /**
     * Catalog identity that still carries a 规格型号. Only the 医院目录 entry point uses
     * this shape, because the hospital product catalog keeps a real specification value.
     */
    record SpecIdentity(String productName, String specModel, String manufacturerName,
                        String supplierName, String registrationNo) {}

    void requireCatalogAvailable(Identity request) {
        if (hospitalCatalogMatches(duplicateRuleFields(), request.productName(), null,
                request.manufacturerName(), request.supplierName(), request.registrationNo())) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    void requireCatalogAvailable(SpecIdentity request) {
        if (hospitalCatalogMatches(duplicateRuleFields(), request.productName(), request.specModel(),
                request.manufacturerName(), request.supplierName(), request.registrationNo())) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    void requireAvailable(Identity request, String excludedApplicationNo) {
        List<String> fields = duplicateRuleFields();
        if (fields.isEmpty()) {
            return;
        }
        if (hospitalCatalogMatches(fields, request.productName(), null,
                request.manufacturerName(), request.supplierName(), request.registrationNo())) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
        if (pendingCatalogMatches(fields, request.productName(), request.manufacturerName(),
                request.supplierName(), request.registrationNo(), excludedApplicationNo)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    void requireAvailable(SpecIdentity request, String excludedApplicationNo) {
        List<String> fields = duplicateRuleFields();
        if (fields.isEmpty()) {
            return;
        }
        if (hospitalCatalogMatches(fields, request.productName(), request.specModel(),
                request.manufacturerName(), request.supplierName(), request.registrationNo())) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
        if (pendingCatalogMatches(fields, request.productName(), request.manufacturerName(),
                request.supplierName(), request.registrationNo(), excludedApplicationNo)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    /**
     * Loads the duplicate-check field combination stored in sys_validation_rule.
     * Falls back to the documented rule when the rule table or row is unavailable.
     * The 规格型号 (spec_model) field is never part of the identity: the 待审批目录
     * flow no longer collects a specification, so a stale admin-configured rule that
     * still lists it must not reintroduce a comparison on an always-empty value.
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
        return List.of("product_name", "manufacturer_name", "supplier_name", "registration_no");
    }

    private boolean hospitalCatalogMatches(List<String> fields, String productName, String specModel,
                                           String manufacturerName, String supplierName, String registrationNo) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted = 0");
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND ").append(productFieldExpression(field)).append(" = ?");
            args.add(requestFieldValue(field, productName, specModel, manufacturerName, supplierName, registrationNo));
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM product p
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                """ + where + " FOR SHARE", Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private boolean pendingCatalogMatches(List<String> fields, String productName,
                                          String manufacturerName, String supplierName,
                                          String registrationNo, String excludedApplicationNo) {
        StringBuilder where = new StringBuilder("""
                 WHERE (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final', 'routing', 'returned'))
                """);
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND COALESCE(NULLIF(")
                    .append(pendingFieldExpression(field))
                    .append(", ''), '') = ?");
            args.add(requestFieldValue(field, productName, null, manufacturerName, supplierName, registrationNo));
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
            case "manufacturer_name" -> "COALESCE(TRIM(m.manufacturer_name), '')";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), '')";
            case "registration_no" -> "COALESCE(TRIM(p.registration_no), '')";
            default -> null;
        };
    }

    private static String pendingFieldExpression(String field) {
        return switch (field) {
            case "product_name" -> "TRIM(a.product_name)";
            case "manufacturer_name" -> "TRIM(a.manufacturer_name)";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), TRIM(a.supplier_name))";
            case "registration_no" -> "TRIM(a.registration_no)";
            default -> null;
        };
    }

    private static String requestFieldValue(String field, String productName, String specModel,
                                            String manufacturerName, String supplierName,
                                            String registrationNo) {
        String value = switch (field) {
            case "product_name" -> productName;
            case "manufacturer_name" -> manufacturerName;
            case "supplier_name" -> supplierName;
            case "registration_no" -> registrationNo;
            default -> null;
        };
        return value == null ? "" : value.trim();
    }

}
