package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.supplychain.QuotaSafetyRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DataScopeService dataScopeService;
    private final QuotaPermissionGuard permissionGuard;

    public SafetyStockService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, new DataScopeService(OperatorContext::system),
                new QuotaPermissionGuard(jdbcTemplate, OperatorContext::system));
    }

    @Autowired
    public SafetyStockService(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                              DataScopeService dataScopeService, QuotaPermissionGuard permissionGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.dataScopeService = dataScopeService;
        this.permissionGuard = permissionGuard;
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
                SELECT qss.safety_id AS safetyId, sd.dept_code AS deptCode, sd.dept_name AS deptName,
                       p.product_code AS productCode, p.product_name AS productName,
                       qpt.template_id AS templateId,
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
        permissionGuard.require("quota-safety-stock:write");

        if ((isBlank(request.deptCode()) && isBlank(request.deptName())) || isBlank(request.productCode())) {
            throw new IllegalArgumentException("department name and product code are required; deptCode is preferred");
        }
        BigDecimal minQty = nonNegative(request.minQty());
        BigDecimal maxQty = nonNegative(request.maxQty());
        if (maxQty.compareTo(minQty) < 0) {
            throw new IllegalArgumentException("max quantity must be greater than or equal to min quantity");
        }
        Long deptId = ensureDept(request);
        Map<String, Object> product = findEligibleProduct(request.productCode());
        Long templateId = null;
        if (request.templateId() != null || !isBlank(request.templateCode())) {
            List<Long> ids = request.templateId() != null
                    ? jdbcTemplate.queryForList("""
                        SELECT qpt.template_id FROM quota_package_template qpt
                        JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id AND qpti.deleted = 0
                        WHERE qpt.template_id = ? AND qpt.is_current = 1 AND qpt.status = 1 AND qpt.deleted = 0
                          AND qpti.product_id = ?
                        """, Long.class, request.templateId(), product.get("productId"))
                    : jdbcTemplate.queryForList("""
                        SELECT qpt.template_id FROM quota_package_template qpt
                        JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id AND qpti.deleted = 0
                        WHERE qpt.template_code = ? AND qpt.is_current = 1 AND qpt.status = 1 AND qpt.deleted = 0
                          AND qpti.product_id = ?
                        """, Long.class, request.templateCode().trim(), product.get("productId"));
            if (ids.size() != 1) throw new IllegalArgumentException("template is disabled or does not match product");
            templateId = ids.get(0);
        }
        jdbcTemplate.update("""
                INSERT INTO quota_safety_stock (dept_id, product_id, template_id, min_qty, max_qty, status)
                VALUES (?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), min_qty = VALUES(min_qty),
                                        max_qty = VALUES(max_qty), status = 1, deleted = 0
                """, deptId, product.get("productId"), templateId, minQty, maxQty);
        Long safetyId = jdbcTemplate.queryForObject(
                "SELECT safety_id FROM quota_safety_stock WHERE dept_id = ? AND product_id = ? AND deleted = 0",
                Long.class, deptId, ((Number) product.get("productId")).longValue());
        support.writeAudit("quota_package", "save_quota_safety", safetyId, request.productCode(), "save quota safety stock");
        String deptName = !isBlank(request.deptCode())
                ? jdbcTemplate.queryForObject("SELECT dept_name FROM sys_dept WHERE dept_id = ?", String.class, deptId)
                : request.deptName().trim();
        if (!isBlank(request.deptName()) && !request.deptName().trim().equals(deptName)) {
            throw new IllegalArgumentException("deptCode and deptName do not refer to the same department");
        }
        return Map.of("productCode", request.productCode().trim(), "deptName", deptName);
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

    private Long ensureDept(QuotaSafetyRequest request) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 AND status = 1");
        if (!isBlank(request.deptCode())) {
            where.append(" AND dept_code = ?");
            args.add(request.deptCode().trim());
        } else {
            where.append(" AND dept_name = ?");
            args.add(request.deptName().trim());
        }
        dataScopeService.appendScope(where, args, "dept_id", null);
        List<Long> ids = jdbcTemplate.queryForList("SELECT dept_id FROM sys_dept" + where, Long.class, args.toArray());
        if (ids.size() == 1) {
            return ids.get(0);
        }
        throw new IllegalArgumentException(ids.isEmpty()
                ? "department does not exist, is disabled, or is outside current data scope"
                : "department name is ambiguous; use deptCode");
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (!isBlank(value)) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }
}
