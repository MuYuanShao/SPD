package com.hospital.spd.supplychain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Owns purchase-order fulfillment updates caused by downstream receiving actions.
 */
@Service
public class PurchaseFulfillmentService {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseFulfillmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordAcceptedReceipt(Object purchaseOrderIdObject, Long productId, BigDecimal acceptedQty) {
        if (purchaseOrderIdObject == null || acceptedQty == null || acceptedQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long purchaseOrderId = ((Number) purchaseOrderIdObject).longValue();
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT item_id AS itemId, quantity - received_quantity AS remainingQuantity
                  FROM purchase_order_item
                 WHERE purchase_order_id = ? AND product_id = ?
                   AND received_quantity < quantity
                 ORDER BY item_id
                 FOR UPDATE
                """, purchaseOrderId, productId);
        BigDecimal pending = acceptedQty;
        for (Map<String, Object> item : items) {
            if (pending.signum() <= 0) {
                break;
            }
            BigDecimal remaining = (BigDecimal) item.get("remainingQuantity");
            BigDecimal allocated = pending.min(remaining);
            int changed = jdbcTemplate.update("""
                    UPDATE purchase_order_item
                       SET received_quantity = received_quantity + ?
                     WHERE item_id = ?
                       AND received_quantity + ? <= quantity
                    """, allocated, item.get("itemId"), allocated);
            if (changed != 1) {
                throw new IllegalStateException("采购订单剩余可收数量已变化，请刷新后重试");
            }
            pending = pending.subtract(allocated);
        }
        if (pending.signum() > 0) {
            throw new IllegalStateException("采购订单剩余可收数量不足，请刷新后重试");
        }
    }
}
