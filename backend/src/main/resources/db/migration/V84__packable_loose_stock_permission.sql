-- Read-only stock projection gets an explicit menu/API permission.
INSERT IGNORE INTO sys_permission (parent_id, perm_name, perm_code, perm_type, path, sort_order)
VALUES (0, '可打包散货快照', 'packable-loose-snapshot', 1, '/features/packable-loose-snapshot', 25);

-- Preserve access for roles already allowed to read the quota-package module.
INSERT IGNORE INTO sys_role_perm (role_id, perm_id)
SELECT existing.role_id, target.perm_id
  FROM sys_role_perm existing
  JOIN sys_permission source ON source.perm_id = existing.perm_id
  JOIN sys_permission target ON target.perm_code = 'packable-loose-snapshot' AND target.deleted = 0
 WHERE source.perm_code = 'quota-package-template' AND source.deleted = 0;
