package com.hospital.spd.masterdata.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Protects active stock and trace relationships when a catalog approval changes high-value classification. */
@Service
public class ProductTraceabilityPolicy {
    private final JdbcTemplate jdbc;
    public ProductTraceabilityPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireSafeTransition(String productCode, boolean requestedHighValue) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT product_id AS productId, is_high_value AS highValue
                  FROM product WHERE product_code = ? AND deleted = 0 FOR UPDATE
                """, productCode);
        if (rows.isEmpty()) return;
        Map<String, Object> product = rows.get(0);
        boolean highValue = Boolean.TRUE.equals(product.get("highValue"))
                || product.get("highValue") instanceof Number n && n.intValue() == 1;
        if (highValue == requestedHighValue) return;
        Long id = ((Number) product.get("productId")).longValue();
        Long active = jdbc.queryForObject("""
                SELECT (
                  EXISTS(SELECT 1 FROM inventory_balance WHERE product_id = ?
                    AND (available_qty > 0 OR locked_qty > 0 OR in_transit_qty > 0 OR isolated_qty > 0) FOR SHARE)
                  OR EXISTS(SELECT 1 FROM quota_package_label WHERE product_id = ?
                    AND status NOT IN ('void','unpacked','cancelled','terminated','settled') FOR SHARE)
                  OR EXISTS(SELECT 1 FROM inventory_batch_trace_code tc JOIN inventory_batch b ON b.batch_id=tc.batch_id
                    WHERE b.product_id = ? AND tc.lifecycle_status NOT IN ('consumed','settled','void','cancelled') FOR SHARE)
                  OR EXISTS(SELECT 1 FROM department_requisition_item i JOIN department_requisition r ON r.requisition_id=i.requisition_id
                    WHERE i.product_id = ? AND r.status IN ('pending_approval','approved','partial_picked') FOR SHARE)
                  OR EXISTS(SELECT 1 FROM spd_delivery_order WHERE product_code = ? AND status = 'picked' FOR SHARE)
                )
                """, Long.class, id, id, id, id, productCode);
        if (active != null && active != 0) {
            throw new IllegalArgumentException("商品仍有库存、有效定数包、唯一码或未完结业务，不能切换高值属性；请先完成库存及追溯转换");
        }
    }

    public void requirePackageProduct(long productId) {
        Long eligible = jdbc.queryForObject("""
                SELECT COUNT(*) FROM product WHERE product_id = ? AND deleted = 0 AND status = 1
                  AND is_high_value = 0 AND is_cold_chain = 0 FOR SHARE
                """, Long.class, productId);
        if (eligible == null || eligible != 1) {
            throw new IllegalArgumentException("商品已停用或属于高值、冷链耗材，不能按普通定数包打包或消耗");
        }
    }
}
