package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.supplychain.QuotaSafetyRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maintains department safety stock thresholds and links them to quota package templates when applicable.
 */
@Service
public class SafetyStockService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public SafetyStockService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    public Map<String, Object> safety(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1 AND qss.deleted = 0
                """);
        appendLike(where, args, "sd.dept_name", params.get("deptName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));

        String fromClause = """
                  FROM quota_safety_stock qss
                  JOIN sys_dept sd ON sd.dept_id = qss.dept_id
                  JOIN product p ON p.product_id = qss.product_id
                  LEFT JOIN quota_package_template qpt ON qpt.template_id = qss.template_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qss.safety_id AS safetyId, sd.dept_name AS deptName,
                       p.product_code AS productCode, p.product_name AS productName,
                       COALESCE(qpt.template_code, '-') AS templateCode,
                       COALESCE(qpt.template_name, '-') AS templateName,
                       qss.min_qty AS minQty, qss.max_qty AS maxQty,
                       CASE qss.status WHEN 1 THEN '启用' ELSE '禁用' END AS status,
                       DATE_FORMAT(qss.update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM quota_safety_stock qss
                  JOIN sys_dept sd ON sd.dept_id = qss.dept_id
                  JOIN product p ON p.product_id = qss.product_id
                  LEFT JOIN quota_package_template qpt ON qpt.template_id = qss.template_id
                """ + where + " ORDER BY qss.update_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> saveSafety(QuotaSafetyRequest request) {

        if (isBlank(request.deptName()) || isBlank(request.productCode())) {
            throw new IllegalArgumentException("department name and product code are required");
        }
        BigDecimal minQty = nonNegative(request.minQty());
        BigDecimal maxQty = nonNegative(request.maxQty());
        if (maxQty.compareTo(minQty) < 0) {
            throw new IllegalArgumentException("max quantity must be greater than or equal to min quantity");
        }
        Long deptId = ensureDept(request.deptName());
        Map<String, Object> product = findEligibleProduct(request.productCode());
        Long templateId = null;
        if (!isBlank(request.templateCode())) {
            templateId = jdbcTemplate.queryForObject(
                    "SELECT template_id FROM quota_package_template WHERE template_code = ? AND deleted = 0",
                    Long.class,
                    request.templateCode().trim());
        }
        jdbcTemplate.update("""
                INSERT INTO quota_safety_stock (dept_id, product_id, template_id, min_qty, max_qty, status)
                VALUES (?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), min_qty = VALUES(min_qty),
                                        max_qty = VALUES(max_qty), status = 1, deleted = 0
                """, deptId, product.get("productId"), templateId, minQty, maxQty);
        support.writeAudit("quota_package", "save_quota_safety", ((Number) product.get("productId")).longValue(), request.productCode(), "save quota safety stock");
        return Map.of("productCode", request.productCode().trim(), "deptName", request.deptName().trim());
    }

    // ---- 私有辅助方法 ----

    private Map<String, Object> findEligibleProduct(String productCode) {
        Map<String, Object> product = jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_unit AS purchaseUnit, conversion_rate AS conversionRate,
                       is_quota_managed AS quotaManaged, is_high_value AS highValue, is_cold_chain AS coldChain
                  FROM product
                 WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode.trim());
        if (((Number) product.get("quotaManaged")).intValue() != 1) {
            throw new IllegalArgumentException("product is not quota managed");
        }
        if (((Number) product.get("highValue")).intValue() == 1 || ((Number) product.get("coldChain")).intValue() == 1) {
            throw new IllegalArgumentException("high value and cold chain product are not allowed for quota package template");
        }
        return product;
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
