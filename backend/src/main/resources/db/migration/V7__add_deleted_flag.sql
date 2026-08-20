-- ============================================================
-- V7：为缺少 deleted 字段的参考/配置表添加软删除标记
-- ============================================================

-- product_guided_location：货位引导配置
ALTER TABLE `product_guided_location`
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'
    AFTER `max_stock`;

-- quota_package_template：定数包模板
ALTER TABLE `quota_package_template`
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'
    AFTER `status`;

-- quota_package_template_item：模板明细
ALTER TABLE `quota_package_template_item`
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'
    AFTER `quantity`;

-- quota_safety_stock：安全库存配置
ALTER TABLE `quota_safety_stock`
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'
    AFTER `status`;

-- system_config：系统配置
ALTER TABLE `system_config`
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'
    AFTER `status`;
