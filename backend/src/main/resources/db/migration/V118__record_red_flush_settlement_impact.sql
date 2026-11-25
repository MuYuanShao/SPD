ALTER TABLE consumption_red_flush
  ADD COLUMN affected_settlements JSON NULL COMMENT '反消耗撤销的待确认结算单及明细金额，历史记录为空';
