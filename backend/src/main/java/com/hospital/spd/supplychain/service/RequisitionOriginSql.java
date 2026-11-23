package com.hospital.spd.supplychain.service;

import org.springframework.jdbc.core.JdbcTemplate;

/** Resolves requisition types using signed deliveries and verifiable stock origins. */
public final class RequisitionOriginSql {
    private RequisitionOriginSql() {}

    static String signedTrace(String traceId, String consumedAt, String scope) {
        return """
                (SELECT COALESCE(delivery.requisition_type, req.requisition_type)
                   FROM spd_delivery_trace_code trace
                   JOIN spd_delivery_order delivery ON delivery.delivery_id = trace.delivery_id
                   JOIN department_requisition req ON req.requisition_no = delivery.requisition_no
                  WHERE trace.trace_code_id = %s
                    AND delivery.sign_time <= %s
                    AND %s
                  ORDER BY delivery.sign_time DESC, delivery.delivery_id DESC LIMIT 1)
                """.formatted(traceId, consumedAt, scope).strip();
    }

    // Use the signed binding of the exact package consumed by the department.
    private static final String SIGNED_PACKAGE = """
            (SELECT COALESCE(delivery.requisition_type, req.requisition_type)
               FROM quota_package_label label
               JOIN spd_delivery_package_binding binding ON binding.label_id = label.label_id
               JOIN spd_delivery_order delivery ON delivery.delivery_id = binding.delivery_id
               JOIN department_requisition req ON req.requisition_id = binding.requisition_id
              WHERE dc.related_biz_type = 'quota_package_label'
                AND label.label_id = dc.related_biz_id AND label.product_id = dci.product_id
                AND delivery.destination_warehouse_id = dc.warehouse_id
                AND delivery.sign_time <= dc.consume_time
              ORDER BY delivery.sign_time DESC, delivery.delivery_id DESC LIMIT 1)
            """.strip();

    private static final String RECEIPT_TYPE = """
            COALESCE(delivery.requisition_type, req.requisition_type,
              CASE WHEN receipt.event_type = 'department_consumption_reverse_in'
                         AND receipt.source_biz_type = 'department_consumption'
                   THEN receipt.requisition_type END)
            """.strip();

    // Evaluate the no-location stock cycle for the actual deduction event. Located stock has no signed-origin allocation.
    private static final String SIGNED_LOOSE = """
            (SELECT MIN(%1$s)
               FROM inventory_event issue_start
               JOIN inventory_event receipt ON receipt.warehouse_id = issue_start.warehouse_id
                 AND receipt.product_id = issue_start.product_id AND receipt.batch_id = issue_start.batch_id
                 AND receipt.event_id < issue_start.event_id
               LEFT JOIN spd_delivery_order delivery ON receipt.source_biz_type = 'spd_delivery_order'
                 AND receipt.event_type = 'delivery_sign_in' AND delivery.delivery_id = receipt.source_biz_id
                 AND receipt.location_id IS NULL AND delivery.destination_warehouse_id = dc.warehouse_id
                 AND delivery.sign_time <= dc.consume_time AND delivery.delivery_type = 'loose'
               LEFT JOIN department_requisition req ON req.requisition_no = delivery.requisition_no
              WHERE issue_start.event_id = dci.inventory_issue_event_id
                AND issue_start.location_id IS NULL
                AND receipt.event_id > COALESCE((
                    SELECT MAX(zero.event_id) FROM inventory_event zero
                     WHERE zero.warehouse_id = issue_start.warehouse_id AND zero.product_id = issue_start.product_id
                       AND zero.batch_id = issue_start.batch_id AND zero.location_id IS NULL
                       AND zero.qty_change < 0 AND zero.qty_after = 0 AND zero.event_id < issue_start.event_id), 0)
                AND receipt.warehouse_id = dc.warehouse_id AND receipt.product_id = dci.product_id
                AND receipt.batch_id = dci.batch_id AND receipt.event_time <= dc.consume_time
                AND receipt.location_id IS NULL AND receipt.qty_change > 0
                AND dc.related_biz_type = 'inventory' AND dci.trace_code_id IS NULL
             HAVING COUNT(*) = COUNT(%1$s)
                AND COUNT(DISTINCT %1$s) = 1
                AND SUM(receipt.qty_change) >= dci.quantity
                AND MIN(receipt.event_id) = MIN(CASE WHEN receipt.qty_after = receipt.qty_change THEN receipt.event_id END))
            """.formatted(RECEIPT_TYPE).strip();

    // Save the origin after inventory deduction and before generating settlement items.
    private static final String CAPTURE_CONSUMPTION = """
            UPDATE department_consumption_item dci
            JOIN department_consumption dc ON dc.consumption_id = dci.consumption_id
               SET dci.requisition_type = COALESCE(%s, %s, %s)
             WHERE dc.consumption_id = ? AND dc.status = 'confirmed'
            """.formatted(SIGNED_PACKAGE, signedTrace("dci.trace_code_id", "dc.consume_time",
                    "delivery.destination_warehouse_id = dc.warehouse_id"), SIGNED_LOOSE);

    public static void captureConsumption(JdbcTemplate jdbc, long consumptionId) {
        jdbc.update(CAPTURE_CONSUMPTION, consumptionId);
    }

    static final String HIGH_VALUE_CHARGE_TYPE = signedTrace("hvc.trace_code_id", "hvc.create_time", """
            EXISTS (SELECT 1 FROM inventory_event issue
                     JOIN inventory_event_trace_code link ON link.event_id = issue.event_id
                    WHERE issue.source_biz_type = 'high_value_charge' AND issue.source_biz_id = hvc.charge_id
                      AND issue.event_type = 'high_value_billing_deduct' AND issue.qty_change < 0
                      AND issue.warehouse_id = delivery.destination_warehouse_id
                      AND link.trace_code_id = hvc.trace_code_id AND delivery.sign_time <= issue.event_time)
            """);
}
