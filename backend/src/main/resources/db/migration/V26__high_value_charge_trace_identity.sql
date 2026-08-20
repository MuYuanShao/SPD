ALTER TABLE high_value_charge
  ADD COLUMN trace_code_id BIGINT UNSIGNED NULL COMMENT 'UDI trace identity' AFTER unique_code,
  ADD KEY idx_high_value_trace_code (trace_code_id),
  ADD CONSTRAINT fk_high_value_trace_code
    FOREIGN KEY (trace_code_id) REFERENCES udi_trace_code (trace_code_id);

UPDATE high_value_charge hvc
JOIN udi_trace_code utc
  ON utc.trace_scope = 'high_value'
 AND (utc.unique_code = hvc.unique_code
      OR (hvc.unique_code IS NULL AND utc.udi_code = hvc.udi_code))
SET hvc.trace_code_id = utc.trace_code_id
WHERE hvc.trace_code_id IS NULL;
