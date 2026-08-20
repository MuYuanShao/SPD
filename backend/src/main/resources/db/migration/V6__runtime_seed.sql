-- Runtime seed data: foundation data (departments, users, permissions) inserted by FoundationController.ensureSeedData()

-- Seed departments (from FoundationController.ensureSeedData())
INSERT IGNORE INTO `sys_dept` (`dept_code`, `dept_name`, `finance_dept_code`, `finance_dept_name`, `campus_name`, `manager_name`, `phone`, `sort_order`)
VALUES ('XXK', '信息科', 'FIN-XXK', '信息科', '主院区', '系统管理员', '0571-100000', 1),
       ('GKS', '骨科', 'FIN-GK', '骨科', '主院区', '骨科护士长', '0571-200001', 2),
       ('SSS', '手术室', 'FIN-SSS', '手术室', '主院区', '手术室护士长', '0571-200002', 3);

-- Seed users (from FoundationController.ensureSeedData())
INSERT IGNORE INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `email`, `gender`, `dept_id`, `status`)
SELECT 'admin', '$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS', '系统管理员', '13800000000', 'admin@hospital.local', 0, MIN(dept_id), 1 FROM sys_dept;

INSERT IGNORE INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `email`, `gender`, `dept_id`, `status`)
SELECT 'operator01', '$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS', '运营管理员', '13800000001', 'operator@hospital.local', 0, MIN(dept_id), 1 FROM sys_dept;

INSERT IGNORE INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `email`, `gender`, `dept_id`, `status`)
SELECT 'nurse01', '$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS', '科室护士', '13800000002', 'nurse@hospital.local', 2, MIN(dept_id), 1 FROM sys_dept;

-- Seed permissions (from FoundationController.seedPermissions())
INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
VALUES (0, '主数据与定数', 'master-data-quota', 1, '/features/master-data-quota', 10),
       (0, '供应链业务', 'supply-chain', 1, '/features/supply-chain', 20),
       (0, '系统管理', 'system-management', 1, '/features/system-management', 90),
       (3, '用户管理', 'user-management', 1, '/features/user-management', 1),
       (3, '角色权限', 'role-permission', 1, '/features/role-permission', 2),
       (4, '新增', 'user:create', 2, null, 1),
       (4, '编辑', 'user:update', 2, null, 2),
       (4, '删除', 'user:delete', 2, null, 3),
       (4, '重置密码', 'user:reset-password', 2, null, 4),
       (4, '分配角色', 'user:assign-role', 2, null, 5),
       (5, '新增', 'role:create', 2, null, 1),
       (5, '编辑', 'role:update', 2, null, 2),
       (5, '删除', 'role:delete', 2, null, 3),
       (5, '分配权限', 'role:assign-permission', 2, null, 4),
       (5, '数据权限', 'role:data-permission', 2, null, 5);

-- Assign admin role to admin user (from FoundationController.ensureSeedData())
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.user_id, r.role_id
  FROM sys_user u, sys_role r
 WHERE u.username = 'admin' AND r.role_code = 'admin';

-- Assign all permissions to admin role (from FoundationController.ensureSeedData())
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM sys_role r, sys_permission p
 WHERE r.role_code = 'admin' AND p.deleted = 0;
