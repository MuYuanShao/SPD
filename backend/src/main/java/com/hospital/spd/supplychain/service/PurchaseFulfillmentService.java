package com.hospital.spd.supplychain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
        jdbcTemplate.update("""
                UPDATE purchase_order_item
                   SET received_quantity = LEAST(quantity, received_quantity + ?)
                 WHERE purchase_order_id = ? AND product_id = ?
                """, acceptedQty, purchaseOrderId, productId);
    }
}
