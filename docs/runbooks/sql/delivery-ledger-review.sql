-- Read-only review: no inventory correction or historical event mutation.
SELECT 'confirmed' AS evidence, d.delivery_no, d.status,
       GROUP_CONCAT(e.event_no ORDER BY e.event_id) AS event_nos,
       SUM(CASE WHEN e.event_type = 'delivery_loose_out' THEN -e.qty_change ELSE 0 END) AS picked_qty,
       SUM(CASE WHEN e.event_type = 'warehouse_transfer_out' THEN -e.qty_change ELSE 0 END) AS duplicate_out_qty,
       '拣配与签收均存在来源扣减事件，需人工核对后另行补偿' AS reason
FROM spd_delivery_order d JOIN inventory_event e
 ON e.source_biz_type = 'spd_delivery_order' AND e.source_biz_id = d.delivery_id
WHERE d.status = 'signed'
GROUP BY d.delivery_id
HAVING picked_qty > 0 AND duplicate_out_qty > 0;

SELECT 'review_required' AS evidence, d.delivery_no, d.destination_warehouse_id AS actual_warehouse,
       r.warehouse_id AS requested_warehouse, '已签收目标库与原申领不一致' AS reason
FROM spd_delivery_order d JOIN department_requisition r ON r.requisition_no = d.requisition_no
WHERE d.status = 'signed' AND d.destination_warehouse_id <> r.warehouse_id;

SELECT 'review_required' AS evidence, d.delivery_no, d.quantity, '待签收散货原批次缺失或数量不一致' AS reason
FROM spd_delivery_order d LEFT JOIN spd_delivery_batch b ON b.delivery_id = d.delivery_id
WHERE d.status = 'picked' AND d.delivery_type = 'loose'
GROUP BY d.delivery_id HAVING COALESCE(SUM(b.quantity), 0) <> d.quantity;

SELECT 'review_required' AS evidence, r.requisition_no, i.item_id,
       '历史定数包模板快照缺失，禁止猜测新版本' AS reason
FROM department_requisition r JOIN department_requisition_item i ON i.requisition_id = r.requisition_id
WHERE i.item_type = 'quota_package'
 AND (i.quota_template_id IS NULL OR i.quota_template_version IS NULL
      OR i.quota_package_quantity IS NULL OR i.quota_package_unit IS NULL);

SELECT 'review_required' AS evidence, e.event_no, e.source_biz_type, e.source_biz_id,
       '定数包事件尚无可安全确定的追溯关联' AS reason
FROM inventory_event e
WHERE e.event_type IN ('quota_pack_out', 'quota_unpack_in', 'quota_package_scan_out', 'quota_package_delivery_sign_in')
 AND NOT EXISTS (SELECT 1 FROM inventory_event_trace_code t WHERE t.event_id = e.event_id);
