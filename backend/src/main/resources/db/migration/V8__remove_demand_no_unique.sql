-- ============================================================
-- V8：移除 purchase_demand.demand_no 唯一约束
-- 允许同一个需求编号下有多条商品明细
-- ============================================================

ALTER TABLE `purchase_demand`
    DROP INDEX `uk_purchase_demand_no`,
    ADD INDEX `idx_purchase_demand_no` (`demand_no`);
