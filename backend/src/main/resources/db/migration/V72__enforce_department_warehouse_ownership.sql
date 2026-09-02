-- Department-owned warehouses must not remain enabled without a valid enabled department.
UPDATE warehouse w
LEFT JOIN sys_dept d
  ON d.dept_id = w.dept_id AND d.deleted = 0 AND d.status = 1
SET w.status = 0
WHERE w.deleted = 0
  AND w.status = 1
  AND (w.warehouse_type LIKE '%二级%'
       OR w.warehouse_type LIKE '%三级%'
       OR w.warehouse_type LIKE '%科室库%')
  AND d.dept_id IS NULL;
