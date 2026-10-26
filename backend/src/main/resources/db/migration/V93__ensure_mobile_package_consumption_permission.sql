-- Restore only a missing consumption permission definition; never change existing status or grant roles.
INSERT INTO sys_permission(parent_id,perm_name,perm_code,perm_type,path,sort_order,status,deleted)
SELECT 0,'科室消耗写操作','department-consumption:write',2,NULL,287,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE perm_code='department-consumption:write');
