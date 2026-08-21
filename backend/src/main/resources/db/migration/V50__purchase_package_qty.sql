-- 医院目录/待审批目录新增采购包装数量字段（换算系数更名为中包装数量，沿用 conversion_rate 列）
ALTER TABLE `product`
  ADD COLUMN `purchase_package_qty` DECIMAL(18,3) NULL COMMENT '采购包装数量' AFTER `conversion_rate`;

ALTER TABLE `pending_product_application`
  ADD COLUMN `purchase_package_qty` DECIMAL(18,3) NULL COMMENT '采购包装数量' AFTER `conversion_rate`;
