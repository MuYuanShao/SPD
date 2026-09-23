ALTER TABLE recall_event ADD COLUMN business_type VARCHAR(20) NOT NULL DEFAULT 'recall' COMMENT 'recall 召回，isolate 原地隔离';
