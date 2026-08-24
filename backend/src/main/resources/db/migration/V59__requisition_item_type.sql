-- 拣配配送：申请明细类型（唯一码/定数包/散货）持久化，用于拣配时配对展示商品明细类型。
ALTER TABLE `department_requisition_item`
  ADD COLUMN `item_type` VARCHAR(30) DEFAULT NULL COMMENT '申请明细类型: unique_code/quota_package/loose' AFTER `quantity`;

UPDATE `department_requisition_item` dri
  JOIN `product` p ON p.product_id = dri.product_id
   SET dri.item_type = CASE
         WHEN EXISTS (SELECT 1 FROM `department_requisition_trace_code` rt WHERE rt.requisition_item_id = dri.item_id)
           THEN 'unique_code'
         WHEN p.is_quota_managed = 1 THEN 'quota_package'
         ELSE 'loose'
       END
 WHERE dri.item_type IS NULL;

-- 配送单记录拣配类型与关联申领明细，用于唯一单据号展示与拣配进度统计。
ALTER TABLE `spd_delivery_order`
  ADD COLUMN `requisition_item_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联申领明细ID' AFTER `requisition_no`,
  ADD COLUMN `delivery_type` VARCHAR(20) DEFAULT NULL COMMENT '拣配类型: package/unique_code/loose' AFTER `requisition_item_id`;

UPDATE `spd_delivery_order` d
   SET d.delivery_type = 'package'
 WHERE EXISTS (SELECT 1 FROM `spd_delivery_package_binding` b WHERE b.delivery_id = d.delivery_id);

UPDATE `spd_delivery_order` d
   SET d.delivery_type = 'unique_code'
 WHERE d.delivery_type IS NULL
   AND EXISTS (SELECT 1 FROM `spd_delivery_trace_code` t WHERE t.delivery_id = d.delivery_id);

