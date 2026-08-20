USE `ISPD`;

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `dept_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '科室ID',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级科室ID',
  `dept_code` VARCHAR(50) NOT NULL COMMENT '科室编码',
  `dept_name` VARCHAR(50) NOT NULL COMMENT '科室名称',
  `finance_dept_code` VARCHAR(50) DEFAULT NULL COMMENT '财务科室编码',
  `finance_dept_name` VARCHAR(50) DEFAULT NULL COMMENT '财务科室',
  `campus_name` VARCHAR(50) DEFAULT NULL COMMENT '院区',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '科室地址',
  `manager_name` VARCHAR(50) DEFAULT NULL COMMENT '负责人',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`dept_id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室表';

CREATE TABLE IF NOT EXISTS `sys_user` (
  `user_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender` TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属科室ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_time` DATETIME DEFAULT NULL COMMENT '锁定时间',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '更新人ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `role_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据范围：1-全部 2-本部门 3-本部门及子部门 4-仅本人',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `perm_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父权限ID',
  `perm_name` VARCHAR(80) NOT NULL COMMENT '权限名称',
  `perm_code` VARCHAR(100) NOT NULL COMMENT '权限标识',
  `perm_type` TINYINT NOT NULL DEFAULT 1 COMMENT '权限类型：1-菜单 2-按钮 3-接口',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
  `component` VARCHAR(200) DEFAULT NULL COMMENT '组件路径',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '菜单图标',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`perm_id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_perm_type` (`perm_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关系表';

CREATE TABLE IF NOT EXISTS `sys_role_perm` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `perm_id` BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `perm_id`),
  KEY `idx_perm_id` (`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关系表';

CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `log_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '登录日志ID',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '用户名',
  `login_ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '客户端',
  `status` TINYINT NOT NULL COMMENT '状态：0-失败 1-成功',
  `message` VARCHAR(200) DEFAULT NULL COMMENT '消息',
  `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

CREATE TABLE IF NOT EXISTS `sys_attachment` (
  `attachment_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '附件ID',
  `biz_type` VARCHAR(50) NOT NULL COMMENT '业务类型',
  `biz_id` BIGINT UNSIGNED NOT NULL COMMENT '业务ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_ext` VARCHAR(20) NOT NULL COMMENT '文件扩展名',
  `file_type` VARCHAR(20) NOT NULL COMMENT '文件类型',
  `file_size` BIGINT UNSIGNED NOT NULL COMMENT '文件大小',
  `file_path` VARCHAR(500) NOT NULL COMMENT '文件路径',
  `file_url` VARCHAR(500) NOT NULL COMMENT '访问URL',
  `category` VARCHAR(50) DEFAULT 'other' COMMENT '附件分类',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '附件描述',
  `valid_date` DATE DEFAULT NULL COMMENT '有效期',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '上传人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`attachment_id`),
  KEY `idx_biz_type_id` (`biz_type`, `biz_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件表';

CREATE TABLE IF NOT EXISTS `product_category` (
  `category_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品分类ID',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级分类ID',
  `category_code` VARCHAR(50) NOT NULL COMMENT '分类编码',
  `category_name` VARCHAR(80) NOT NULL COMMENT '分类名称',
  `level` TINYINT NOT NULL COMMENT '层级：1/2/3',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品目录分类字段表';

CREATE TABLE IF NOT EXISTS `supplier` (
  `supplier_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `supplier_code` VARCHAR(50) NOT NULL COMMENT '供应商编码',
  `supplier_name` VARCHAR(100) NOT NULL COMMENT '供应商名称',
  `credit_code` VARCHAR(18) NOT NULL COMMENT '统一社会信用代码',
  `supplier_type` VARCHAR(20) NOT NULL COMMENT '供应商类型',
  `grade` CHAR(1) DEFAULT NULL COMMENT '供应商等级：A/B/C/D',
  `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(30) NOT NULL COMMENT '联系电话',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '地址',
  `approval_status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '审批状态',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`),
  UNIQUE KEY `uk_credit_code` (`credit_code`),
  KEY `idx_supplier_name` (`supplier_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS `manufacturer` (
  `manufacturer_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '厂家ID',
  `manufacturer_code` VARCHAR(50) NOT NULL COMMENT '厂家编码',
  `manufacturer_name` VARCHAR(100) NOT NULL COMMENT '厂家名称',
  `credit_code` VARCHAR(18) DEFAULT NULL COMMENT '统一社会信用代码',
  `license_no` VARCHAR(100) DEFAULT NULL COMMENT '生产许可证号',
  `contact_name` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '地址',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`manufacturer_id`),
  UNIQUE KEY `uk_manufacturer_code` (`manufacturer_code`),
  KEY `idx_manufacturer_name` (`manufacturer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='厂家表';

CREATE TABLE IF NOT EXISTS `product` (
  `product_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `spec_model` VARCHAR(100) NOT NULL COMMENT '规格型号',
  `brand` VARCHAR(50) DEFAULT NULL COMMENT '品牌',
  `manufacturer_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '厂家ID',
  `supplier_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '默认供应商ID',
  `category_id` BIGINT UNSIGNED NOT NULL COMMENT '商品分类ID',
  `unit` VARCHAR(20) NOT NULL COMMENT '基本单位',
  `purchase_price` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '采购价',
  `retail_price` DECIMAL(18,4) DEFAULT NULL COMMENT '零售价',
  `min_purchase_qty` DECIMAL(18,4) NOT NULL DEFAULT 1 COMMENT '最小采购量',
  `purchase_unit` VARCHAR(20) DEFAULT NULL COMMENT '采购单位',
  `conversion_rate` DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '换算系数',
  `udi_code` VARCHAR(100) DEFAULT NULL COMMENT 'UDI编码',
  `registration_no` VARCHAR(100) DEFAULT NULL COMMENT '注册证号',
  `registration_expire_date` DATE DEFAULT NULL COMMENT '注册证有效期',
  `production_license_no` VARCHAR(100) DEFAULT NULL COMMENT '生产许可证号',
  `business_license_no` VARCHAR(100) DEFAULT NULL COMMENT '经营许可证号',
  `is_volume_based` TINYINT NOT NULL DEFAULT 0 COMMENT '是否带量',
  `is_centralized_procurement` TINYINT NOT NULL DEFAULT 0 COMMENT '是否集采',
  `is_domestic` TINYINT NOT NULL DEFAULT 1 COMMENT '是否国产',
  `contract_code` VARCHAR(80) DEFAULT NULL COMMENT '合同编码',
  `first_category` VARCHAR(80) DEFAULT NULL COMMENT '一级分类',
  `second_category` VARCHAR(80) DEFAULT NULL COMMENT '二级分类',
  `third_category` VARCHAR(80) DEFAULT NULL COMMENT '三级分类',
  `is_chargeable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否收费',
  `tender_sub_code` VARCHAR(80) DEFAULT NULL COMMENT '招采子编码',
  `is_high_value` TINYINT NOT NULL DEFAULT 0 COMMENT '是否高值耗材',
  `is_cold_chain` TINYINT NOT NULL DEFAULT 0 COMMENT '是否冷链',
  `is_quota_managed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否定数管理',
  `storage_condition` VARCHAR(30) DEFAULT NULL COMMENT '储存条件',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '商品图片',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_product_name` (`product_name`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_manufacturer_id` (`manufacturer_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

CREATE TABLE IF NOT EXISTS `pending_product_application` (
  `application_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `application_no` VARCHAR(50) NOT NULL COMMENT '申请单号',
  `application_type` VARCHAR(30) NOT NULL COMMENT '申请类型',
  `product_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联商品ID',
  `supplier_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '提交供应商ID',
  `product_snapshot` JSON NOT NULL COMMENT '商品申请快照',
  `change_diff` JSON DEFAULT NULL COMMENT '变更差异',
  `approval_status` VARCHAR(30) NOT NULL DEFAULT 'pending_initial' COMMENT '审批状态',
  `submit_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '提交人',
  `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `approve_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_application_no` (`application_no`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_approval_status` (`approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待审批商品目录申请表';

CREATE TABLE IF NOT EXISTS `warehouse` (
  `warehouse_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库房ID',
  `warehouse_code` VARCHAR(50) NOT NULL COMMENT '库房编码',
  `warehouse_name` VARCHAR(50) NOT NULL COMMENT '库房名称',
  `warehouse_type` VARCHAR(30) NOT NULL COMMENT '库房类型',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级库房',
  `campus_name` VARCHAR(50) NOT NULL COMMENT '所属院区',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联科室',
  `manager_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '库房负责人',
  `participate_stats` TINYINT NOT NULL DEFAULT 1 COMMENT '是否参与统计',
  `stats_categories` JSON DEFAULT NULL COMMENT '统计要求分类',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`warehouse_id`),
  UNIQUE KEY `uk_warehouse_code` (`warehouse_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_type` (`warehouse_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库房表';

CREATE TABLE IF NOT EXISTS `warehouse_location` (
  `location_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '货位ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '所属库房',
  `location_code` VARCHAR(80) NOT NULL COMMENT '货位编码',
  `location_type` VARCHAR(30) NOT NULL COMMENT '货位类型',
  `capacity_limit` DECIMAL(18,4) DEFAULT NULL COMMENT '容量上限',
  `product_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '固定关联商品',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`location_id`),
  UNIQUE KEY `uk_warehouse_location` (`warehouse_id`, `location_code`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_type` (`location_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='货位表';

CREATE TABLE IF NOT EXISTS `product_guided_location` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `primary_location_id` BIGINT UNSIGNED NOT NULL COMMENT '引导货位',
  `backup_location_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '备用货位',
  `min_stock` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '最低存量',
  `max_stock` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '最高存量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_guided_location` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='散货引导货位表';

CREATE TABLE IF NOT EXISTS `purchase_order` (
  `purchase_order_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '采购订单号',
  `supplier_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `order_source` VARCHAR(30) DEFAULT NULL COMMENT '需求来源',
  `purchase_type` VARCHAR(30) DEFAULT NULL COMMENT '采购类型',
  `order_status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '订单状态',
  `total_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '订单总额',
  `expected_arrival_date` DATE DEFAULT NULL COMMENT '期望到货日期',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  `close_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
  `close_reason` VARCHAR(500) DEFAULT NULL COMMENT '关闭原因',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`purchase_order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_order_status` (`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单表';

CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `purchase_order_id` BIGINT UNSIGNED NOT NULL COMMENT '采购订单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '采购数量',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位',
  `estimated_unit_price` DECIMAL(18,4) NOT NULL COMMENT '预估单价',
  `amount` DECIMAL(18,4) NOT NULL COMMENT '金额',
  `received_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已收货数量',
  PRIMARY KEY (`item_id`),
  KEY `idx_purchase_order_id` (`purchase_order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单明细表';

CREATE TABLE IF NOT EXISTS `purchase_demand` (
  `demand_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '采购需求ID',
  `demand_no` VARCHAR(50) NOT NULL COMMENT '采购需求号',
  `demand_source` VARCHAR(30) NOT NULL COMMENT '需求来源',
  `demand_status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '需求状态',
  `urgent_level` VARCHAR(30) NOT NULL DEFAULT 'normal' COMMENT '紧急程度',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源科室',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '需求数量',
  `approved_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '审核通过数量',
  `suggested_purchase_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '建议采购量',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`demand_id`),
  KEY `idx_purchase_demand_no` (`demand_no`),
  KEY `idx_purchase_demand_status` (`demand_status`),
  KEY `idx_purchase_demand_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购需求池表';

CREATE TABLE IF NOT EXISTS `purchase_plan` (
  `plan_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '采购计划ID',
  `plan_no` VARCHAR(50) NOT NULL COMMENT '采购计划号',
  `plan_status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '计划状态',
  `supplier_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `planned_quantity` DECIMAL(18,4) NOT NULL COMMENT '计划采购数量',
  `converted_order_no` VARCHAR(50) DEFAULT NULL COMMENT '转采购订单号',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`plan_id`),
  UNIQUE KEY `uk_purchase_plan_no` (`plan_no`),
  KEY `idx_purchase_plan_status` (`plan_status`),
  KEY `idx_purchase_plan_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购计划表';

CREATE TABLE IF NOT EXISTS `purchase_order_tracking` (
  `tracking_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单跟踪ID',
  `purchase_order_id` BIGINT UNSIGNED NOT NULL COMMENT '采购订单ID',
  `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
  `event_status` VARCHAR(50) NOT NULL COMMENT '事件状态',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tracking_id`),
  KEY `idx_purchase_tracking_order` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单跟踪表';

CREATE TABLE IF NOT EXISTS `receiving_order` (
  `receiving_order_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收货单ID',
  `receiving_no` VARCHAR(50) NOT NULL COMMENT '收货单号',
  `purchase_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '采购订单ID',
  `supplier_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '入库库房',
  `receiving_status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '收货状态',
  `receive_time` DATETIME DEFAULT NULL COMMENT '收货时间',
  `receiver_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '收货人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`receiving_order_id`),
  UNIQUE KEY `uk_receiving_no` (`receiving_no`),
  KEY `idx_purchase_order_id` (`purchase_order_id`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货验收单表';

CREATE TABLE IF NOT EXISTS `receiving_order_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `receiving_order_id` BIGINT UNSIGNED NOT NULL COMMENT '收货单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `production_batch_no` VARCHAR(50) DEFAULT NULL COMMENT '生产批号',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expire_date` DATE DEFAULT NULL COMMENT '有效期至',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '收货数量',
  `unit_price` DECIMAL(18,4) NOT NULL COMMENT '验收批次单价',
  `amount` DECIMAL(18,4) NOT NULL COMMENT '金额',
  `qualified_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '合格数量',
  `unqualified_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '不合格数量',
  PRIMARY KEY (`item_id`),
  KEY `idx_receiving_order_id` (`receiving_order_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_batch_expire` (`production_batch_no`, `expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货验收明细表';

CREATE TABLE IF NOT EXISTS `inventory_batch` (
  `batch_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统批次ID',
  `system_batch_no` VARCHAR(50) NOT NULL COMMENT '系统批次号',
  `receiving_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '收货单ID',
  `receiving_item_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '收货明细ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `supplier_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '供应商ID',
  `production_batch_no` VARCHAR(50) DEFAULT NULL COMMENT '生产批号',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expire_date` DATE DEFAULT NULL COMMENT '有效期至',
  `batch_unit_price` DECIMAL(18,4) NOT NULL COMMENT '批次单价',
  `ownership_type` VARCHAR(30) NOT NULL DEFAULT 'hospital_owned' COMMENT '所有权类型',
  `settlement_mode` VARCHAR(30) NOT NULL DEFAULT 'purchase_in' COMMENT '结算模式',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_system_batch_no` (`system_batch_no`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_expire_date` (`expire_date`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统库存批次表';

CREATE TABLE IF NOT EXISTS `inventory_balance` (
  `balance_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存余额ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `location_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '货位ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '系统批次ID',
  `available_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '可用数量',
  `locked_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '锁定数量',
  `in_transit_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '在途数量',
  `isolated_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '隔离数量',
  `last_event_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '最后事件ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`balance_id`),
  UNIQUE KEY `uk_inventory_balance` (`warehouse_id`, `location_id`, `product_id`, `batch_id`),
  KEY `idx_product_batch` (`product_id`, `batch_id`),
  KEY `idx_warehouse_product` (`warehouse_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存余额表';

CREATE TABLE IF NOT EXISTS `inventory_event` (
  `event_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存事件ID',
  `event_no` VARCHAR(50) NOT NULL COMMENT '事件编号',
  `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
  `source_biz_type` VARCHAR(50) DEFAULT NULL COMMENT '来源业务类型',
  `source_biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源业务ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `location_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '货位ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '系统批次ID',
  `qty_change` DECIMAL(18,4) NOT NULL COMMENT '数量变化',
  `qty_after` DECIMAL(18,4) NOT NULL COMMENT '变化后数量',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人',
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  KEY `idx_source_biz` (`source_biz_type`, `source_biz_id`),
  KEY `idx_product_batch` (`product_id`, `batch_id`),
  KEY `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存事件流水表';

CREATE TABLE IF NOT EXISTS `quota_package_template` (
  `template_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '定数包模板ID',
  `template_code` VARCHAR(50) NOT NULL COMMENT '模板编码',
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '适用科室',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包模板表';

CREATE TABLE IF NOT EXISTS `quota_package_template_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模板明细ID',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '模板ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '数量',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_template_product` (`template_id`, `product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包模板明细表';

CREATE TABLE IF NOT EXISTS `quota_safety_stock` (
  `safety_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '安全量ID',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '科室ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `template_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '定数包模板ID',
  `min_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '安全下限',
  `max_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '安全上限',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`safety_id`),
  UNIQUE KEY `uk_dept_product` (`dept_id`, `product_id`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数安全量表';

CREATE TABLE IF NOT EXISTS `quota_packing_task` (
  `task_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '打包任务ID',
  `task_no` VARCHAR(50) NOT NULL COMMENT '打包任务号',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '定数包模板ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '来源库房ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `package_count` DECIMAL(18,4) NOT NULL COMMENT '打包数量',
  `package_quantity` DECIMAL(18,4) NOT NULL COMMENT '每包数量',
  `planned_loose_qty` DECIMAL(18,4) NOT NULL COMMENT '计划扣减散货数量',
  `reserved_loose_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已预占散货数量',
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending_confirm' COMMENT '状态',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `confirm_time` DATETIME DEFAULT NULL COMMENT '确认时间',
  `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_quota_packing_task_no` (`task_no`),
  KEY `idx_quota_pack_status` (`status`),
  KEY `idx_quota_pack_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包打包任务表';

CREATE TABLE IF NOT EXISTS `quota_packing_task_reservation` (
  `reservation_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预占ID',
  `task_id` BIGINT UNSIGNED NOT NULL COMMENT '打包任务ID',
  `balance_id` BIGINT UNSIGNED NOT NULL COMMENT '库存余额ID',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '系统批次ID',
  `reserved_qty` DECIMAL(18,4) NOT NULL COMMENT '预占数量',
  `unit_price` DECIMAL(18,4) NOT NULL COMMENT '批次单价',
  `status` VARCHAR(30) NOT NULL DEFAULT 'reserved' COMMENT '预占状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`reservation_id`),
  KEY `idx_quota_reservation_task` (`task_id`),
  KEY `idx_quota_reservation_balance` (`balance_id`),
  KEY `idx_quota_reservation_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包打包预占明细表';

CREATE TABLE IF NOT EXISTS `quota_package_label` (
  `label_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `label_no` VARCHAR(50) NOT NULL COMMENT '定数包标签号',
  `task_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源打包任务ID',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '定数包模板ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `package_quantity` DECIMAL(18,4) NOT NULL COMMENT '包内数量',
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending_print' COMMENT '标签状态',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
  `print_count` INT NOT NULL DEFAULT 0 COMMENT '打印次数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`label_id`),
  UNIQUE KEY `uk_quota_package_label_no` (`label_no`),
  KEY `idx_quota_package_label_status` (`status`),
  KEY `idx_quota_package_label_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包标签表';

CREATE TABLE IF NOT EXISTS `quota_package_label_source` (
  `source_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签来源ID',
  `label_id` BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '来源系统批次ID',
  `source_qty` DECIMAL(18,4) NOT NULL COMMENT '来源数量',
  `unit_price` DECIMAL(18,4) NOT NULL COMMENT '来源批次单价',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '来源库房ID',
  PRIMARY KEY (`source_id`),
  KEY `idx_quota_label_source_label` (`label_id`),
  KEY `idx_quota_label_source_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包标签来源表';

CREATE TABLE IF NOT EXISTS `quota_package_event` (
  `event_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `event_no` VARCHAR(50) NOT NULL COMMENT '事件编号',
  `label_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '标签ID',
  `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
  `status_before` VARCHAR(30) DEFAULT NULL COMMENT '变更前状态',
  `status_after` VARCHAR(30) DEFAULT NULL COMMENT '变更后状态',
  `qty_change` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '数量变化',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_quota_package_event_no` (`event_no`),
  KEY `idx_quota_package_event_label` (`label_id`),
  KEY `idx_quota_package_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定数包事件表';

CREATE TABLE IF NOT EXISTS `department_requisition` (
  `requisition_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '申领单ID',
  `requisition_no` VARCHAR(50) NOT NULL COMMENT '申领单号',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '申领科室',
  `requisition_type` VARCHAR(30) NOT NULL COMMENT '申领类型',
  `expected_arrival_date` DATE DEFAULT NULL COMMENT '期望到货',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '状态',
  `applicant_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '申领人',
  `apply_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申领时间',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  PRIMARY KEY (`requisition_id`),
  UNIQUE KEY `uk_requisition_no` (`requisition_no`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室申领单表';

CREATE TABLE IF NOT EXISTS `department_requisition_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `requisition_id` BIGINT UNSIGNED NOT NULL COMMENT '申领单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '申领数量',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_requisition_id` (`requisition_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室申领明细表';

CREATE TABLE IF NOT EXISTS `department_consumption` (
  `consumption_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消耗单ID',
  `consumption_no` VARCHAR(50) NOT NULL COMMENT '消耗单号',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '消耗科室',
  `consumption_type` VARCHAR(30) NOT NULL COMMENT '消耗类型',
  `related_biz_type` VARCHAR(50) DEFAULT NULL COMMENT '关联单据类型',
  `related_biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联单据ID',
  `patient_info` JSON DEFAULT NULL COMMENT '患者信息',
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending_confirm' COMMENT '状态',
  `consume_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '消耗人',
  `consume_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消耗时间',
  PRIMARY KEY (`consumption_id`),
  UNIQUE KEY `uk_consumption_no` (`consumption_no`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`),
  KEY `idx_consume_time` (`consume_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室消耗单表';

CREATE TABLE IF NOT EXISTS `department_consumption_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `consumption_id` BIGINT UNSIGNED NOT NULL COMMENT '消耗单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `batch_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '系统批次ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '数量',
  `unit_price` DECIMAL(18,4) NOT NULL COMMENT '消耗事实单价',
  `amount` DECIMAL(18,4) NOT NULL COMMENT '金额',
  PRIMARY KEY (`item_id`),
  KEY `idx_consumption_id` (`consumption_id`),
  KEY `idx_product_batch` (`product_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室消耗明细表';

CREATE TABLE IF NOT EXISTS `settlement_bill` (
  `settlement_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '结算单ID',
  `settlement_no` VARCHAR(50) NOT NULL COMMENT '结算单号',
  `supplier_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `settlement_period` VARCHAR(20) NOT NULL COMMENT '结算期间',
  `settlement_mode` VARCHAR(30) NOT NULL COMMENT '结算模式',
  `total_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '结算金额',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '状态',
  `generate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  `confirm_time` DATETIME DEFAULT NULL COMMENT '确认时间',
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_period` (`settlement_period`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算单表';

CREATE TABLE IF NOT EXISTS `settlement_bill_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `settlement_id` BIGINT UNSIGNED NOT NULL COMMENT '结算单ID',
  `source_biz_type` VARCHAR(50) NOT NULL COMMENT '来源业务类型',
  `source_biz_id` BIGINT UNSIGNED NOT NULL COMMENT '来源业务ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `batch_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '系统批次ID',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '数量',
  `unit_price` DECIMAL(18,4) NOT NULL COMMENT '单价',
  `amount` DECIMAL(18,4) NOT NULL COMMENT '金额',
  PRIMARY KEY (`item_id`),
  KEY `idx_settlement_id` (`settlement_id`),
  KEY `idx_source_biz` (`source_biz_type`, `source_biz_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算单明细表';

CREATE TABLE IF NOT EXISTS `system_config` (
  `config_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_type` VARCHAR(50) NOT NULL COMMENT '配置类型',
  `scope_type` VARCHAR(30) NOT NULL COMMENT '适用范围',
  `scope_id` VARCHAR(64) NOT NULL COMMENT '适用对象',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` JSON NOT NULL COMMENT '配置值',
  `effective_mode` VARCHAR(20) NOT NULL DEFAULT 'realtime' COMMENT '生效方式',
  `effective_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `expire_time` DATETIME DEFAULT NULL COMMENT '失效时间',
  `risk_level` VARCHAR(20) DEFAULT NULL COMMENT '风险等级',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_scope_key` (`config_type`, `scope_type`, `scope_id`, `config_key`),
  KEY `idx_config_type` (`config_type`),
  KEY `idx_scope` (`scope_type`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `audit_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计日志ID',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人名称',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `biz_type` VARCHAR(50) NOT NULL COMMENT '业务类型',
  `biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '业务ID',
  `before_data` JSON DEFAULT NULL COMMENT '变更前数据',
  `after_data` JSON DEFAULT NULL COMMENT '变更后数据',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `operation_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`audit_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_operation_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';

INSERT IGNORE INTO `sys_role` (`role_name`, `role_code`, `description`, `data_scope`, `status`, `sort_order`) VALUES
('系统管理员', 'admin', '系统超级管理员，拥有所有权限', 1, 1, 1),
('运营管理员', 'operator', '日常运营管理，库存、采购等操作', 3, 1, 2),
('采购人员', 'purchaser', '采购计划、订单管理、供应商沟通', 2, 1, 3),
('库管人员', 'warehouse', '入库、出库、盘点、库存管理', 2, 1, 4),
('科室人员', 'dept_user', '科室申领、使用登记', 4, 1, 5),
('财务人员', 'finance', '结算对账、发票管理、付款审批', 2, 1, 6),
('质控人员', 'qc', '质量监控、追溯审计、合规检查', 1, 1, 7),
('医生/护士', 'doctor', '高值耗材使用、患者关联', 4, 1, 8),
('院领导', 'leader', '运营总览、决策分析', 1, 1, 9),
('审计人员', 'auditor', '数据查询、报表查看，无操作权限', 1, 1, 10),
('供应商', 'supplier', '外部供应商，查看订单和配送', 4, 1, 11),
('审批人员', 'approver', '各业务审批节点处理', 3, 1, 12);
