-- Freeze historical business classification; never derive it from today's product flags.
ALTER TABLE settlement_bill_item ADD COLUMN goods_type VARCHAR(20) NULL COMMENT '结算生成时货型快照';
UPDATE settlement_bill_item bill
JOIN product current_product ON current_product.product_id=bill.product_id
LEFT JOIN inventory_batch origin_batch ON origin_batch.batch_id=bill.batch_id
LEFT JOIN department_consumption_item item ON bill.source_biz_type='department_consumption_item' AND item.item_id=bill.source_biz_id
LEFT JOIN department_consumption consumption ON consumption.consumption_id=item.consumption_id
LEFT JOIN quota_package_label label ON (label.trace_code_id=bill.trace_code_id AND bill.trace_code_id IS NOT NULL)
  OR (consumption.related_biz_type='quota_package_label' AND label.label_id=consumption.related_biz_id)
SET bill.goods_type = CASE
  WHEN bill.source_biz_type='high_value_charge' THEN 'high_value'
  WHEN label.label_id IS NOT NULL THEN 'quota_package'
  WHEN EXISTS (SELECT 1 FROM inventory_batch_trace_code trace WHERE trace.batch_id=bill.batch_id) THEN 'high_value'
  WHEN (SELECT admission.is_high_value FROM pending_product_application admission
       WHERE admission.product_code=current_product.product_code AND admission.approval_status='approved'
         AND admission.approve_time <= origin_batch.create_time
       ORDER BY admission.approve_time DESC,admission.application_id DESC LIMIT 1)=0 THEN 'loose'
  WHEN (SELECT admission.is_high_value FROM pending_product_application admission
       WHERE admission.product_code=current_product.product_code AND admission.approval_status='approved'
         AND admission.approve_time <= origin_batch.create_time
       ORDER BY admission.approve_time DESC,admission.application_id DESC LIMIT 1)=1 THEN 'high_value'
  WHEN current_product.is_high_value=1 THEN 'unknown'
  ELSE 'loose' END
WHERE bill.goods_type IS NULL;
ALTER TABLE settlement_bill_item ADD CONSTRAINT ck_settlement_goods_type
  CHECK (goods_type IS NULL OR goods_type IN ('high_value','quota_package','loose','unknown'));
