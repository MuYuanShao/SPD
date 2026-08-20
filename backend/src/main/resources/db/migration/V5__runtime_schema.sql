-- Runtime schema: tables created dynamically by InventoryController, OperationalClosureSchema, etc.

-- Inventory stocktaking table (from InventoryController.ensureTables())
CREATE TABLE IF NOT EXISTS `inventory_stocktaking` (
  `stocktaking_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '盘点ID',
  `stocktaking_no` VARCHAR(50) NOT NULL COMMENT '盘点单号',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `stocktaking_type` VARCHAR(30) NOT NULL COMMENT '盘点类型',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '状态',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '原因',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`stocktaking_id`),
  UNIQUE KEY `uk_stocktaking_no` (`stocktaking_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盘点单表';

-- Inventory stocktaking item table (from InventoryController.ensureTables())
CREATE TABLE IF NOT EXISTS `inventory_stocktaking_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `stocktaking_id` BIGINT UNSIGNED NOT NULL COMMENT '盘点单ID',
  `balance_id` BIGINT UNSIGNED NOT NULL COMMENT '库存余额ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '系统批次ID',
  `system_qty` DECIMAL(18,4) NOT NULL COMMENT '系统数量',
  `actual_qty` DECIMAL(18,4) NOT NULL COMMENT '实际数量',
  `diff_qty` DECIMAL(18,4) NOT NULL COMMENT '差异数量',
  `diff_reason` VARCHAR(500) DEFAULT NULL COMMENT '差异原因',
  PRIMARY KEY (`item_id`),
  KEY `idx_stocktaking_id` (`stocktaking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盘点明细表';

-- Batch price adjustment table (from InventoryController.ensureTables())
CREATE TABLE IF NOT EXISTS `batch_price_adjustment` (
  `adjustment_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '调价ID',
  `adjustment_no` VARCHAR(50) NOT NULL COMMENT '调价单号',
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT '批次ID',
  `old_unit_price` DECIMAL(18,4) NOT NULL COMMENT '旧单价',
  `new_unit_price` DECIMAL(18,4) NOT NULL COMMENT '新单价',
  `affected_qty` DECIMAL(18,4) NOT NULL COMMENT '影响数量',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '原因',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '状态',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`adjustment_id`),
  UNIQUE KEY `uk_adjustment_no` (`adjustment_no`),
  KEY `idx_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次调价单表';

-- Shortage replenishment task table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `shortage_replenishment_task` (
  `task_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_no` VARCHAR(50) NOT NULL COMMENT '任务编号',
  `dept_name` VARCHAR(80) NOT NULL COMMENT '科室名称',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `min_qty` DECIMAL(18,4) NOT NULL COMMENT '最低库存',
  `current_qty` DECIMAL(18,4) NOT NULL COMMENT '当前库存',
  `replenish_qty` DECIMAL(18,4) NOT NULL COMMENT '补货数量',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `source_type` VARCHAR(50) DEFAULT NULL COMMENT '来源类型',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_shortage_task_no` (`task_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='缺货补货任务表';

-- SPD delivery order table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `spd_delivery_order` (
  `delivery_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配送ID',
  `delivery_no` VARCHAR(50) NOT NULL COMMENT '配送单号',
  `requisition_no` VARCHAR(50) DEFAULT NULL COMMENT '关联申领单号',
  `dept_name` VARCHAR(80) NOT NULL COMMENT '科室名称',
  `warehouse_name` VARCHAR(80) NOT NULL COMMENT '库房名称',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '配送数量',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `sign_time` DATETIME DEFAULT NULL COMMENT '签收时间',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_delivery_no` (`delivery_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPD配送单表';

-- Consumption red flush table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `consumption_red_flush` (
  `flush_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '冲销ID',
  `flush_no` VARCHAR(50) NOT NULL COMMENT '冲销单号',
  `source_consumption_no` VARCHAR(50) NOT NULL COMMENT '来源消耗单号',
  `flush_type` VARCHAR(50) NOT NULL COMMENT '冲销类型',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`flush_id`),
  UNIQUE KEY `uk_flush_no` (`flush_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消耗红冲表';

-- PDA offline record table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `pda_offline_record` (
  `record_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `record_no` VARCHAR(50) NOT NULL COMMENT '记录编号',
  `device_no` VARCHAR(50) NOT NULL COMMENT '设备编号',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `payload` JSON NOT NULL COMMENT '数据内容',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_pda_record_no` (`record_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PDA离线记录表';

-- Cold chain exception table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `cold_chain_exception` (
  `event_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `event_no` VARCHAR(50) NOT NULL COMMENT '事件编号',
  `warehouse_name` VARCHAR(80) NOT NULL COMMENT '库房名称',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `temperature` DECIMAL(10,2) NOT NULL COMMENT '温度',
  `severity` VARCHAR(30) NOT NULL COMMENT '严重程度',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_cold_event_no` (`event_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冷链异常事件表';

-- Recall event table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `recall_event` (
  `recall_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '召回ID',
  `recall_no` VARCHAR(50) NOT NULL COMMENT '召回编号',
  `warehouse_name` VARCHAR(80) NOT NULL COMMENT '库房名称',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `affected_qty` DECIMAL(18,4) NOT NULL COMMENT '影响数量',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`recall_id`),
  UNIQUE KEY `uk_recall_no` (`recall_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品召回事件表';

-- High value charge table (from OperationalClosureSchema.ensureTables())
CREATE TABLE IF NOT EXISTS `high_value_charge` (
  `charge_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '计费ID',
  `charge_no` VARCHAR(50) NOT NULL COMMENT '计费单号',
  `dept_name` VARCHAR(80) NOT NULL COMMENT '科室名称',
  `patient_no` VARCHAR(50) NOT NULL COMMENT '患者编号',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '数量',
  `amount` DECIMAL(18,4) NOT NULL COMMENT '金额',
  `status` VARCHAR(30) NOT NULL COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`charge_id`),
  UNIQUE KEY `uk_high_value_charge_no` (`charge_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='高值耗材计费表';
