ALTER TABLE pending_product_application
  ADD COLUMN create_time DATETIME NULL COMMENT '创建时间',
  ADD COLUMN update_time DATETIME NULL COMMENT '修改时间';

-- The latest submission may be a resubmission. Keep the earliest available event.
UPDATE pending_product_application a
LEFT JOIN (
  SELECT application_id, MIN(action_time) AS first_action_time, MAX(action_time) AS last_action_time
  FROM pending_product_approval_action
  GROUP BY application_id
) history ON history.application_id = a.application_id
SET a.create_time = LEAST(a.submit_time, COALESCE(history.first_action_time, a.submit_time)),
    a.update_time = GREATEST(a.submit_time, COALESCE(a.approve_time, a.submit_time),
                             COALESCE(history.last_action_time, a.submit_time));

ALTER TABLE pending_product_application
  MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间';
