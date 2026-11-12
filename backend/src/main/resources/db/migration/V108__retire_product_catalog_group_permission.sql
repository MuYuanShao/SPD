UPDATE sys_permission child
JOIN sys_permission catalog ON catalog.perm_id = child.parent_id
   SET child.parent_id = catalog.parent_id
 WHERE catalog.perm_code = 'product-catalog'
   AND child.perm_code IN ('pending-product-catalog', 'hospital-product-catalog')
   AND child.deleted = 0;

UPDATE sys_permission
   SET status = 0
 WHERE perm_code = 'product-catalog' AND deleted = 0;

UPDATE sys_permission
   SET perm_name = '待审批目录'
 WHERE perm_code = 'pending-product-catalog' AND deleted = 0;
