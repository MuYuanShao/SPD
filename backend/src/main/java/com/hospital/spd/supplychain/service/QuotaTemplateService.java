package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.supplychain.QuotaTemplateRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maintains quota package templates and validates whether catalog items can be packaged for departments.
 */
@Service
public class QuotaTemplateService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public QuotaTemplateService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    public Map<String, Object> templates(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1 AND qpt.deleted = 0 AND qpti.deleted = 0
                """);
        appendLike(where, args, "qpt.template_code", params.get("templateCode"));
        appendLike(where, args, "qpt.template_name", params.get("templateName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));

        String fromClause = """
                  FROM quota_package_template qpt
                  JOIN (
                    SELECT template_id, MAX(item_id) AS item_id
                      FROM quota_package_template_item
                     WHERE deleted = 0
                     GROUP BY template_id
                  ) current_item ON current_item.template_id = qpt.template_id
                  JOIN quota_package_template_item qpti ON qpti.item_id = current_item.item_id
                  JOIN product p ON p.product_id = qpti.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpt.template_id AS templateId, qpt.template_code AS templateCode,
                       p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName,
                       qpti.quantity, qpti.unit,
                       CASE qpt.status WHEN 1 THEN '启用' ELSE '禁用' END AS status,
                       DATE_FORMAT(qpt.update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM quota_package_template qpt
                  JOIN (
                    SELECT template_id, MAX(item_id) AS item_id
                      FROM quota_package_template_item
                     WHERE deleted = 0
                     GROUP BY template_id
                  ) current_item ON current_item.template_id = qpt.template_id
                  JOIN quota_package_template_item qpti ON qpti.item_id = current_item.item_id
                  JOIN product p ON p.product_id = qpti.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                """ + where + " ORDER BY qpt.update_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> createTemplate(QuotaTemplateRequest request) {

        if (isBlank(request.productCode())) {
            throw new IllegalArgumentException("product code is required");
        }
        BigDecimal quantity = positive(request.quantity(), "定数包模板数量必须大于零");
        Map<String, Object> product = findEligibleProduct(request.productCode());
        BigDecimal normalizedQuantity = normalizeTemplateQuantity(request.quantity(), request.unit(), product);
        String baseUnit = String.valueOf(product.get("unit"));
        // 定数包名称由系统自动生成：商品名称 + 定数包；不再维护适用科室
        String templateName = String.valueOf(product.get("productName")).trim() + "定数包";
        String productCode = String.valueOf(product.get("productCode")).trim();
        String templateCode = isBlank(request.templateCode())
                ? nextTemplateCode(productCode)
                : request.templateCode().trim();
        ensureTemplateNotDuplicate(templateCode, null, ((Number) product.get("productId")).longValue());

        Long existingTemplateId = findTemplateId(templateCode);
        Long templateId;
        if (existingTemplateId == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO quota_package_template (template_code, template_name, dept_id, status)
                        VALUES (?, ?, NULL, 1)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, templateCode);
                ps.setString(2, templateName);
                return ps;
            }, keyHolder);
            templateId = jdbcTemplate.queryForObject(
                    "SELECT template_id FROM quota_package_template WHERE template_code = ?",
                    Long.class,
                    templateCode);
        } else {
            templateId = existingTemplateId;
            jdbcTemplate.update("""
                    UPDATE quota_package_template
                       SET template_name = ?, dept_id = NULL, status = 1, deleted = 0
                     WHERE template_id = ?
                    """, templateName, templateId);
            jdbcTemplate.update("""
                    UPDATE quota_package_template_item
                       SET deleted = 1
                     WHERE template_id = ? AND deleted = 0
                    """, templateId);
        }
        jdbcTemplate.update("""
                INSERT INTO quota_package_template_item (template_id, product_id, quantity, unit)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), unit = VALUES(unit), deleted = 0
                """, templateId, product.get("productId"), normalizedQuantity, baseUnit);
        if (existingTemplateId == null) {
            support.writeAudit("quota_package", "create_quota_template", templateId, templateCode, "create quota template");
        } else {
            support.writeAudit("quota_package", "update_quota_template", templateId, templateCode, "update quota template");
        }
        return Map.of("templateCode", templateCode);
    }

    private Long findTemplateId(String templateCode) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT template_id FROM quota_package_template WHERE template_code = ? LIMIT 1",
                Long.class,
                templateCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional
    public Map<String, Object> disableTemplate(String templateCode) {

        Long templateId = jdbcTemplate.queryForObject(
                "SELECT template_id FROM quota_package_template WHERE template_code = ? AND deleted = 0",
                Long.class,
                templateCode.trim());
        jdbcTemplate.update("UPDATE quota_package_template SET status = 0 WHERE template_id = ?", templateId);
        support.writeAudit("quota_package", "disable_quota_template", templateId, templateCode, "disable quota template");
        return Map.of("templateCode", templateCode, "status", "disabled");
    }

    @Transactional
    public Map<String, Object> enableTemplate(String templateCode) {

        Long templateId = jdbcTemplate.queryForObject(
                "SELECT template_id FROM quota_package_template WHERE template_code = ? AND deleted = 0",
                Long.class,
                templateCode.trim());
        jdbcTemplate.update("UPDATE quota_package_template SET status = 1 WHERE template_id = ?", templateId);
        support.writeAudit("quota_package", "enable_quota_template", templateId, templateCode, "enable quota template");
        return Map.of("templateCode", templateCode, "status", "enabled");
    }

    /**
     * 科室申领目录以已启用的科室库房目录为准。新科室无需先产生消耗历史，
     * 目录维护完成后即可发起首次申领；已停用目录或商品不展示。
     */
    public Map<String, Object> requisitionCatalog(Map<String, String> params) {

        PageRequest pageReq = PageRequest.from(params);
        String deptName = params.getOrDefault("deptName", "");
        String warehouseName = params.getOrDefault("warehouseName", "");
        assertNoRequisitionTemplateConflicts(deptName);
        List<Object> args = new ArrayList<>();
        Long sourceWarehouseId = sourceWarehouseId(
                params.get("sourceWarehouseId"), deptName, warehouseName);
        StringBuilder sql = new StringBuilder("""
                SELECT p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName,
                       catalog_warehouse.warehouse_name AS warehouseName,
                       __SOURCE_WAREHOUSE__ AS sourceWarehouseId,
                       p.spec_model AS specModel, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName, p.unit AS baseUnit,
                       p.purchase_unit AS purchaseUnit, p.conversion_rate AS conversionRate,
                       p.purchase_price AS unitPrice, p.is_quota_managed AS quotaManaged,
                       p.is_high_value AS highValue,
                       p.is_centralized_procurement AS centralized, p.is_chargeable AS chargeable,
                       COALESCE(loose.available_qty, 0) AS looseAvailableQty,
                        COALESCE(pkg.package_count, 0) AS packageAvailableQty,
                        COALESCE(hv.unique_code_count, 0) AS uniqueCodeAvailableQty,
                       COALESCE(t.template_code, '-') AS templateCode,
                       COALESCE(t.template_name, '-') AS templateName,
                       t.quantity AS packageQuantity,
                       COALESCE(t.unit, p.unit) AS packageUnit,
                        CASE
                          WHEN p.is_high_value = 1 THEN 'unique_code'
                          WHEN p.is_quota_managed = 1 AND COALESCE(t.template_id, 0) > 0 AND COALESCE(pkg.package_count, 0) > 0
                           THEN 'quota_package'
                         ELSE 'loose'
                       END AS defaultMode,
                        CASE
                          WHEN p.is_high_value = 1 AND COALESCE(hv.unique_code_count, 0) > 0 THEN '高值唯一码申领'
                          WHEN p.is_high_value = 1 THEN '高值唯一码缺货'
                          WHEN p.is_quota_managed = 1 AND COALESCE(t.template_id, 0) > 0 AND COALESCE(pkg.package_count, 0) = 0
                           THEN '定数包缺货，可转散货'
                         WHEN p.is_quota_managed = 1 AND COALESCE(t.template_id, 0) = 0
                           THEN '未配置科室定数包模板'
                         WHEN p.is_quota_managed = 1
                           THEN '定数包优先'
                         ELSE '散货申领'
                       END AS requisitionStatus
                  FROM department_warehouse_catalog dwc
                  JOIN sys_dept catalog_dept ON catalog_dept.dept_id = dwc.dept_id
                   AND catalog_dept.deleted = 0
                  JOIN warehouse catalog_warehouse ON catalog_warehouse.warehouse_id = dwc.warehouse_id
                   AND catalog_warehouse.deleted = 0
                  JOIN product p ON p.product_id = dwc.product_id
                   AND p.deleted = 0
                   AND p.status = 1
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                  LEFT JOIN (
                    SELECT product_id, warehouse_id, SUM(available_qty) AS available_qty
                      FROM inventory_balance
                     GROUP BY product_id, warehouse_id
                  ) loose ON loose.product_id = p.product_id AND loose.warehouse_id = __SOURCE_WAREHOUSE__
                  LEFT JOIN (
                    SELECT product_id, warehouse_id, COUNT(*) AS package_count
                      FROM quota_package_label
                     WHERE quota_package_label.status = 'available'
                     GROUP BY product_id, warehouse_id
                   ) pkg ON pkg.product_id = p.product_id AND pkg.warehouse_id = __SOURCE_WAREHOUSE__
                  LEFT JOIN (
                    SELECT ib.product_id, ibtc.current_warehouse_id AS warehouse_id, COUNT(*) AS unique_code_count
                      FROM udi_trace_code utc
                      JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = utc.trace_code_id
                      JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                     WHERE utc.current_status = 'in_stock' AND ibtc.lifecycle_status = 'in_stock'
                     GROUP BY ib.product_id, ibtc.current_warehouse_id
                  ) hv ON hv.product_id = p.product_id AND hv.warehouse_id = __SOURCE_WAREHOUSE__
                  LEFT JOIN (
                    SELECT template_id, template_code, template_name, dept_id, product_id, quantity, unit
                      FROM (
                        SELECT qpt.template_id, qpt.template_code, qpt.template_name, qpt.dept_id,
                               qpti.product_id, qpti.quantity, qpti.unit,
                               ROW_NUMBER() OVER (
                                 PARTITION BY qpti.product_id
                                 ORDER BY CASE WHEN d.dept_name = ? THEN 0 WHEN qpt.dept_id IS NULL THEN 1 ELSE 2 END,
                                          qpt.update_time DESC
                               ) AS rn
                          FROM quota_package_template qpt
                          JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
                          LEFT JOIN sys_dept d ON d.dept_id = qpt.dept_id
                         WHERE qpt.status = 1 AND qpt.deleted = 0
                           AND (? = '' OR d.dept_name = ? OR qpt.dept_id IS NULL)
                      ) ranked_template
                     WHERE rn = 1
                  ) t ON t.product_id = p.product_id
                 WHERE (? = '' OR catalog_dept.dept_name = ?)
                   AND (? = '' OR catalog_warehouse.warehouse_name = ?)
                   AND dwc.status = 1
                   AND dwc.deleted = 0
                """.replace("__SOURCE_WAREHOUSE__", sourceWarehouseId == null ? "dwc.warehouse_id" : "?"));
        if (sourceWarehouseId != null) {
            args.add(sourceWarehouseId);
            args.add(sourceWarehouseId);
            args.add(sourceWarehouseId);
            args.add(sourceWarehouseId);
        }
        args.add(deptName.trim());
        args.add(deptName.trim());
        args.add(deptName.trim());
        args.add(deptName.trim());
        args.add(deptName.trim());
        args.add(warehouseName.trim());
        args.add(warehouseName.trim());
        appendLike(sql, args, "p.product_code", params.get("productCode"));
        appendLike(sql, args, "p.product_name", params.get("productName"));
        appendLike(sql, args, "p.spec_model", params.get("specModel"));
        appendLike(sql, args, "m.manufacturer_name", params.get("manufacturerName"));
        if (!isBlank(params.get("mode"))) {
            if ("quota_package".equals(params.get("mode"))) {
                sql.append(" AND p.is_quota_managed = 1 AND t.template_id IS NOT NULL");
            } else if ("unique_code".equals(params.get("mode"))) {
                sql.append(" AND p.is_high_value = 1");
            } else if ("loose".equals(params.get("mode"))) {
                sql.append(" AND p.is_high_value = 0 AND (p.is_quota_managed = 0 OR t.template_id IS NULL OR COALESCE(pkg.package_count, 0) = 0)");
            }
        }
        String baseSql = sql.toString();
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + baseSql + ") catalog_count",
                Long.class,
                args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(baseSql + """
                 ORDER BY productName, productId
                 LIMIT ? OFFSET ?
                """, queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    private Long sourceWarehouseId(String rawValue, String deptName, String warehouseName) {
        if (isBlank(rawValue)) {
            if (isBlank(deptName) && isBlank(warehouseName)) return null;
            List<Long> candidates = jdbcTemplate.queryForList("""
                    SELECT DISTINCT source.warehouse_id
                      FROM warehouse source
                      JOIN warehouse target ON source.campus_name = target.campus_name
                      LEFT JOIN sys_dept target_dept ON target_dept.dept_id = target.dept_id
                     WHERE source.deleted = 0 AND source.status = 1
                       AND target.deleted = 0 AND target.status = 1
                       AND (source.warehouse_type LIKE '%一级%' OR source.warehouse_type LIKE '%中心%')
                       AND ((? <> '' AND target.warehouse_name = ?)
                            OR (? <> '' AND target_dept.dept_name = ?))
                     ORDER BY source.warehouse_id
                    """, Long.class, warehouseName.trim(), warehouseName.trim(), deptName.trim(), deptName.trim());
            if (candidates.size() != 1) {
                throw new IllegalArgumentException("当前院区无法唯一确定中心库，请明确提供 sourceWarehouseId");
            }
            return candidates.get(0);
        }
        final Long sourceId;
        try {
            sourceId = Long.valueOf(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("sourceWarehouseId must be a number");
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_id = ? AND deleted = 0 AND status = 1
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                """, Long.class, sourceId);
        if (ids.size() != 1) throw new IllegalArgumentException("source warehouse must be an enabled central warehouse");
        return ids.get(0);
    }

    private void assertNoRequisitionTemplateConflicts(String deptName) {
        Integer conflicts;
        if (isBlank(deptName)) {
            conflicts = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT qpti.product_id
                        FROM quota_package_template qpt
                        JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
                       WHERE qpt.status = 1 AND qpt.deleted = 0 AND qpt.dept_id IS NULL
                       GROUP BY qpti.product_id
                      HAVING COUNT(*) > 1
                    ) template_conflicts
                    """, Integer.class);
        } else {
            conflicts = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT qpti.product_id
                        FROM quota_package_template qpt
                        JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
                        CROSS JOIN (
                          SELECT dept_id FROM sys_dept
                           WHERE dept_name = ? AND deleted = 0 AND status = 1
                           ORDER BY dept_id LIMIT 1
                        ) requested_dept
                       WHERE qpt.status = 1 AND qpt.deleted = 0
                         AND (qpt.dept_id = requested_dept.dept_id OR qpt.dept_id IS NULL)
                       GROUP BY qpti.product_id
                      HAVING SUM(qpt.dept_id = requested_dept.dept_id) > 1
                         OR (SUM(qpt.dept_id = requested_dept.dept_id) = 0
                             AND SUM(qpt.dept_id IS NULL) > 1)
                    ) template_conflicts
                    """, Integer.class, deptName.trim());
        }
        if (conflicts != null && conflicts > 0) {
            throw new IllegalArgumentException("定数包模板配置冲突：同一商品存在多个同优先级启用模板");
        }
    }

    // ---- 私有辅助方法 ----

    private Map<String, Object> findEligibleProduct(String productCode) {
        Map<String, Object> product = jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_unit AS purchaseUnit, conversion_rate AS conversionRate,
                       is_quota_managed AS quotaManaged, is_high_value AS highValue, is_cold_chain AS coldChain
                  FROM product
                 WHERE product_code = ? AND deleted = 0 AND status = 1
                   FOR UPDATE
                """, productCode.trim());
        if (((Number) product.get("quotaManaged")).intValue() != 1) {
            throw new IllegalArgumentException("product is not quota managed");
        }
        if (((Number) product.get("highValue")).intValue() == 1 || ((Number) product.get("coldChain")).intValue() == 1) {
            throw new IllegalArgumentException("high value and cold chain product are not allowed for quota package template");
        }
        return product;
    }

    private String nextTemplateCode(String productCode) {
        if (productCode.length() > 47) {
            throw new IllegalArgumentException("商品编码过长，无法生成定数包模板编码");
        }
        Integer currentSequence = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(template_code, 3) AS UNSIGNED)), 0)
                  FROM quota_package_template
                 WHERE CHAR_LENGTH(template_code) = CHAR_LENGTH(?) + 3
                   AND LEFT(template_code, CHAR_LENGTH(?)) = ?
                   AND RIGHT(template_code, 3) REGEXP '^[0-9]{3}$'
                """, Integer.class, productCode, productCode, productCode);
        int nextSequence = (currentSequence == null ? 0 : currentSequence) + 1;
        if (nextSequence > 999) {
            throw new IllegalArgumentException("该商品的定数包模板编码序号已用完");
        }
        return productCode + "%03d".formatted(nextSequence);
    }

    private BigDecimal normalizeTemplateQuantity(BigDecimal value, String unit, Map<String, Object> product) {
        BigDecimal quantity = positive(value, "package quantity must be greater than zero");
        String baseUnit = String.valueOf(product.get("unit"));
        String requestUnit = isBlank(unit) ? baseUnit : unit.trim();
        if (requestUnit.equals(baseUnit)) {
            return quantity;
        }
        String purchaseUnit = product.get("purchaseUnit") == null ? "" : String.valueOf(product.get("purchaseUnit"));
        if (!purchaseUnit.isBlank() && requestUnit.equals(purchaseUnit)) {
            BigDecimal conversionRate = (BigDecimal) product.get("conversionRate");
            if (conversionRate == null || conversionRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("product conversion rate must be greater than zero");
            }
            return quantity.multiply(conversionRate);
        }
        throw new IllegalArgumentException("quota template unit must be product base unit or purchase unit");
    }

    private void ensureTemplateNotDuplicate(String templateCode, Long deptId, Long productId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM quota_package_template qpt
                  JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
                 WHERE qpt.status = 1
                   AND qpti.product_id = ?
                   AND qpt.template_code <> ?
                   AND ((? IS NULL AND qpt.dept_id IS NULL) OR qpt.dept_id = ?)
                """, Integer.class, productId, templateCode, deptId, deptId);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("same active quota template already exists");
        }
    }

    private Long ensureDept(String deptName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 LIMIT 1",
                Long.class,
                deptName.trim());
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        throw new IllegalArgumentException("department does not exist or is disabled");
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (!isBlank(value)) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }
}
