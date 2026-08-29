# Database constraint exceptions

The following identifiers intentionally have no foreign key because they are polymorphic references:

- `inventory_event.source_biz_id`, paired with `source_biz_type`
- `settlement_bill_item.source_biz_id`, paired with `source_biz_type` and protected by `uk_settlement_source_item`
- generic audit fields such as `audit_log.biz_id`, paired with their business type

New relationship columns ending in `_id` require a foreign key, or an explicit entry here with ownership and deletion semantics.
