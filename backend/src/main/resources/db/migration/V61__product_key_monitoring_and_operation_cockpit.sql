-- 医院目录与待审批目录增加重点监控属性，供运营驾驶舱筛选重点耗材。
ALTER TABLE `product`
  ADD COLUMN `is_key_monitored` TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控' AFTER `is_quota_managed`;

ALTER TABLE `pending_product_application`
  ADD COLUMN `is_key_monitored` TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控' AFTER `is_quota_managed`;

