-- Some upgraded installations have no legacy delivery write permission although V29 is recorded.
-- Restore only the missing definition; preserve explicit disabled/deleted permissions and role assignments.
INSERT INTO sys_permission (parent_id, perm_name, perm_code, perm_type, path, sort_order, status, deleted)
SELECT 0, '配送签收写操作', 'picking-delivery:write', 2, NULL, 286, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'picking-delivery:write');
