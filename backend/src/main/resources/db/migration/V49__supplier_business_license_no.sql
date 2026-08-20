-- 供应商主数据新增经营许可证号字段
ALTER TABLE `supplier`
  ADD COLUMN `business_license_no` VARCHAR(80) NULL COMMENT '经营许可证号' AFTER `credit_code`;
