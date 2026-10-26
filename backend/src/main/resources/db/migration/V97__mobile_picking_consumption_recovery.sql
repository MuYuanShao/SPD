-- Forward migration after V95/V96. Preserve all account grants and existing permission status.
INSERT INTO sys_permission(parent_id,perm_name,perm_code,perm_type,path,sort_order,status,deleted)
SELECT 0,'科室消耗写操作','department-consumption:write',2,NULL,287,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE perm_code='department-consumption:write');
INSERT INTO sys_permission(parent_id,perm_name,perm_code,perm_type,path,sort_order,status,deleted)
SELECT 0,'科室申领拣选','department-requisition:pick',2,NULL,288,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE perm_code='department-requisition:pick');
ALTER TABLE mobile_operation_receipt DROP CHECK ck_mobile_receipt_status,
    ADD CONSTRAINT ck_mobile_receipt_status CHECK (status IN ('pending','succeeded','rejected'));
