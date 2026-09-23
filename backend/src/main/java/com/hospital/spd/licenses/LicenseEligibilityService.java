package com.hospital.spd.licenses;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.stream.Collectors;

/** Checks current linked licenses when admitting products or committing new purchases; never changes historical documents. */
@Service
public class LicenseEligibilityService {
    private final JdbcTemplate jdbc;
    public LicenseEligibilityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireEligible(String productCode, Long supplierId, Long manufacturerId, String contractCode, String registrationExpiry) {
        requireCandidateRegistration(registrationExpiry);
        requireEligible(productCode, supplierId, manufacturerId, contractCode);
    }

    public void requireCandidateRegistration(String expiry) {
        if (expiry == null || expiry.isBlank()) return;
        java.time.LocalDate date;
        try { date = java.time.LocalDate.parse(expiry.trim()); }
        catch (java.time.format.DateTimeParseException error) { throw new IllegalArgumentException("注册证有效期格式不正确"); }
        if (date.isBefore(java.time.LocalDate.now())) throw new IllegalArgumentException("注册证已过期，不能提交或通过新品准入，请更新有效期");
    }

    public void requireEligible(String productCode, Long supplierId, Long manufacturerId, String contractCode) {
        var blocked = jdbc.queryForList("""
                SELECT l.license_id AS licenseId, l.license_name AS licenseName, l.license_no AS licenseNo,
                       l.expire_date AS expireDate
                  FROM license_document l
                 WHERE l.deleted=0 AND (l.status=0 OR l.expire_date < CURRENT_DATE OR l.issue_date > CURRENT_DATE)
                   AND (l.license_type <> 'contract' OR l.license_no = ?)
                   AND (
                     (COALESCE(NULLIF(l.owner_type,''),l.license_type)='product' AND
                       (l.owner_id=(SELECT product_id FROM product WHERE product_code=? AND deleted=0 LIMIT 1)
                        OR (l.owner_id IS NULL AND l.owner_code=?)))
                     OR (COALESCE(NULLIF(l.owner_type,''),l.license_type)='supplier' AND
                       (l.owner_id=? OR (l.owner_id IS NULL AND l.owner_code=(SELECT supplier_code FROM supplier WHERE supplier_id=?))))
                     OR (COALESCE(NULLIF(l.owner_type,''),l.license_type)='manufacturer' AND
                       (l.owner_id=? OR (l.owner_id IS NULL AND l.owner_code=(SELECT manufacturer_code FROM manufacturer WHERE manufacturer_id=?))))
                   )
                 ORDER BY l.license_id
                """, contractCode, productCode, productCode, supplierId, supplierId, manufacturerId, manufacturerId);
        if (!blocked.isEmpty()) {
            String names = blocked.stream().map(row -> String.valueOf(row.get("licenseName"))).collect(Collectors.joining("、"));
            throw new IllegalArgumentException("关联证照已过期、失效或尚未生效，不能提交新准入或采购：" + names + "。请在证照管理续证后重试");
        }
    }

    public void requirePurchaseOrder(String orderNo) {
        var products = jdbc.queryForList("""
                SELECT p.product_code AS productCode, po.supplier_id AS supplierId,
                       p.manufacturer_id AS manufacturerId, p.contract_code AS contractCode
                  FROM purchase_order po JOIN purchase_order_item i ON i.purchase_order_id=po.purchase_order_id
                  JOIN product p ON p.product_id=i.product_id WHERE po.order_no=?
                """, orderNo);
        for (var row : products) check(row);
    }

    public void requireAdmission(String applicationNo) {
        var applications = jdbc.queryForList("""
                SELECT product_code AS productCode, supplier_id AS supplierId,
                       manufacturer_id AS manufacturerId, contract_code AS contractCode, registration_expire_date AS registrationExpiry
                  FROM pending_product_application WHERE application_no=? AND application_type='新品准入'
                """, applicationNo);
        for (var row : applications) {
            requireCandidateRegistration(row.get("registrationExpiry") == null ? null : String.valueOf(row.get("registrationExpiry")));
            check(row);
        }
    }

    private void check(Map<String, Object> row) {
        requireEligible((String) row.get("productCode"), number(row.get("supplierId")), number(row.get("manufacturerId")), (String) row.get("contractCode"));
    }
    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
