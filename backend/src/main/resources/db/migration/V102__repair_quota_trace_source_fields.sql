-- Repair only missing display fields; the stable source relationships and historical costs remain intact.
UPDATE udi_trace_code trace
JOIN quota_package_label label ON label.trace_code_id = trace.trace_code_id
JOIN (
    SELECT source.label_id, MIN(batch.expire_date) AS expire_date,
           SUBSTRING_INDEX(GROUP_CONCAT(batch.system_batch_no ORDER BY batch.expire_date IS NULL,
               batch.expire_date, source.source_id SEPARATOR ','), ',', 1) AS batch_no
      FROM quota_package_label_source source
      JOIN inventory_batch batch ON batch.batch_id = source.batch_id
     GROUP BY source.label_id
) origin ON origin.label_id = label.label_id
SET trace.batch_no = COALESCE(trace.batch_no, origin.batch_no),
    trace.expire_date = COALESCE(trace.expire_date, origin.expire_date)
WHERE trace.trace_scope = 'low_value_quota_pack'
  AND (trace.batch_no IS NULL OR trace.expire_date IS NULL);
