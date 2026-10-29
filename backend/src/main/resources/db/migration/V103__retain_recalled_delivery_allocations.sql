-- Preserve historical physical bindings while releasing recalled units from active fulfilment.
ALTER TABLE spd_delivery_package_binding ADD COLUMN recalled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE spd_delivery_trace_code ADD COLUMN recalled TINYINT NOT NULL DEFAULT 0;
