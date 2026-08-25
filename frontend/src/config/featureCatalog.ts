export type FeatureIcon =
  | 'alert-triangle'
  | 'archive-restore'
  | 'bar-chart-3'
  | 'boxes'
  | 'clipboard-check'
  | 'clipboard-list'
  | 'database'
  | 'factory'
  | 'file-warning'
  | 'history'
  | 'home'
  | 'landmark'
  | 'layout-dashboard'
  | 'package-check'
  | 'package-search'
  | 'receipt-text'
  | 'scan-line'
  | 'settings'
  | 'shield-check'
  | 'truck'
  | 'users'
  | 'warehouse'

export type FeatureViewFamily =
  | 'dashboard'
  | 'feature'
  | 'invoice-management'
  | 'inventory-workbench'
  | 'login'
  | 'master-data-list'
  | 'operational-closure'
  | 'pending-product-catalog'
  | 'quota-package'
  | 'supply-chain-department-requisition'
  | 'supply-chain-purchase-management'
  | 'supply-chain-receiving-acceptance'
  | 'udi-traceability'
  | 'license-management'
  | 'system-config'
  | 'system-field-option-management'
  | 'system-config-hit-explanation'
  | 'system-approval-flow-settings'
  | 'system-user-role-management'
  | 'print-template-settings'
  | 'report-center'

export type FeatureChunkFamily = 'catalog' | 'master-data' | 'quota-package' | 'supply-chain' | 'system'
export type FeatureNodeKind = 'navigation' | 'deferred' | 'business'

export interface FeatureCatalogEntry {
  code: string
  title: string
  icon: FeatureIcon
  routeTarget: string
  viewFamily: FeatureViewFamily
  nodeKind?: FeatureNodeKind
  chunkFamily?: FeatureChunkFamily
  aliases?: readonly string[]
  children?: readonly string[]
}

export interface FeatureChunkRule {
  chunkFamily: FeatureChunkFamily
  chunkName: string
  viewPathIncludes: readonly string[]
}

export const featureCatalog = [
  {
    code: 'login',
    title: '登录页',
    icon: 'shield-check',
    routeTarget: '/login',
    viewFamily: 'login'
  },
  {
    code: 'dashboard',
    title: '首页 / 工作台',
    icon: 'layout-dashboard',
    routeTarget: '/',
    viewFamily: 'dashboard',
    children: ['todo-tasks', 'stock-warning', 'shortage-reminder', 'settlement-exception', 'quick-entry']
  },
  {
    code: 'todo-tasks',
    title: '待办任务',
    icon: 'clipboard-check',
    routeTarget: '/features/todo-tasks',
    viewFamily: 'feature',
    nodeKind: 'deferred'
  },
  {
    code: 'stock-warning',
    title: '库存预警',
    icon: 'alert-triangle',
    routeTarget: '/features/stock-warning',
    viewFamily: 'feature',
    nodeKind: 'deferred'
  },

  {
    code: 'settlement-exception',
    title: '结算异常',
    icon: 'receipt-text',
    routeTarget: '/features/settlement-exception',
    viewFamily: 'feature',
    nodeKind: 'deferred'
  },
  {
    code: 'quick-entry',
    title: '快捷入口',
    icon: 'home',
    routeTarget: '/features/quick-entry',
    viewFamily: 'feature',
    nodeKind: 'deferred'
  },
  {
    code: 'master-data-quota',
    title: '主数据与定数',
    icon: 'database',
    routeTarget: '/features/master-data-quota',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: [
      'product-catalog',
      'supplier-manufacturer-management',
      'campus-management',
      'department-management',
      'department-warehouse-catalog',
      'warehouse-location-management',
      'license-management',
      'quota-package-template',
      'quota-safety-stock'
    ]
  },
  {
    code: 'product-catalog',
    title: '商品目录',
    icon: 'package-search',
    routeTarget: '/',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['pending-product-catalog', 'hospital-product-catalog']
  },
  {
    code: 'pending-product-catalog',
    title: '待审批目录',
    icon: 'clipboard-list',
    routeTarget: '/features/pending-product-catalog',
    viewFamily: 'pending-product-catalog',
    chunkFamily: 'catalog'
  },
  {
    code: 'hospital-product-catalog',
    title: '医院目录',
    icon: 'package-check',
    routeTarget: '/features/hospital-product-catalog',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'supplier-management',
    title: '供应商管理',
    icon: 'truck',
    routeTarget: '/features/supplier-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'manufacturer-management',
    title: '厂家管理',
    icon: 'factory',
    routeTarget: '/features/manufacturer-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'supplier-manufacturer-management',
    title: '供应商厂家管理',
    icon: 'truck',
    routeTarget: '/features/supplier-manufacturer-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data',
    aliases: ['supplier-management', 'manufacturer-management']
  },
  {
    code: 'campus-management',
    title: '院区管理',
    icon: 'landmark',
    routeTarget: '/features/campus-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'department-management',
    title: '科室管理',
    icon: 'users',
    routeTarget: '/features/department-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'department-warehouse-catalog',
    title: '科室库房目录',
    icon: 'clipboard-list',
    routeTarget: '/features/department-warehouse-catalog',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'warehouse-location-management',
    title: '库房 / 货位管理',
    icon: 'warehouse',
    routeTarget: '/features/warehouse-location-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'license-management',
    title: '证照管理',
    icon: 'shield-check',
    routeTarget: '/features/license-management',
    viewFamily: 'license-management',
    chunkFamily: 'master-data'
  },
  {
    code: 'quota-package-template',
    title: '定数包模板',
    icon: 'boxes',
    routeTarget: '/features/quota-template-maintenance',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package',
    children: [
      'quota-template-maintenance',
      'packing-task-confirmation',
      'quota-label-unpack',
      'quota-package-events',
      'packable-loose-snapshot',
      'print-template-settings'
    ]
  },
  {
    code: 'quota-template-maintenance',
    title: '定数包模板维护',
    icon: 'boxes',
    routeTarget: '/features/quota-template-maintenance',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'packing-task-confirmation',
    title: '打包任务确认',
    icon: 'package-check',
    routeTarget: '/features/packing-task-confirmation',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'quota-label-unpack',
    title: '定数包标签与解包',
    icon: 'archive-restore',
    routeTarget: '/features/quota-label-unpack',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'quota-package-events',
    title: '定数包事件',
    icon: 'history',
    routeTarget: '/features/quota-package-events',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'packable-loose-snapshot',
    title: '可打包散货快照',
    icon: 'shield-check',
    routeTarget: '/features/packable-loose-snapshot',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'print-template-settings',
    title: '打印模板调整',
    icon: 'settings',
    routeTarget: '/features/print-template-settings',
    viewFamily: 'print-template-settings',
    chunkFamily: 'system'
  },
  {
    code: 'quota-safety-stock',
    title: '定数安全量',
    icon: 'alert-triangle',
    routeTarget: '/features/quota-safety-stock',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'supply-chain',
    title: '供应链业务',
    icon: 'boxes',
    routeTarget: '/features/supply-chain',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: [
      'purchase-management',
      'receiving-acceptance',
      'inventory-management',
      'inventory-events',
      'replenishment-task',
      'picking-delivery',
      'department-requisition',
      'department-consumption',
      'stocktaking-management'
    ]
  },
  {
    code: 'purchase-management',
    title: '采购管理',
    icon: 'clipboard-list',
    routeTarget: '/features/purchase-management',
    viewFamily: 'supply-chain-purchase-management',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'receiving-acceptance',
    title: '收货验收',
    icon: 'clipboard-check',
    routeTarget: '/features/receiving-acceptance',
    viewFamily: 'supply-chain-receiving-acceptance',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'inventory-management',
    title: '库存管理',
    icon: 'warehouse',
    routeTarget: '/features/inventory-management',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'inventory-events',
    title: '库存交易流水',
    icon: 'history',
    routeTarget: '/features/inventory-events',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'replenishment-task',
    title: '补货任务',
    icon: 'file-warning',
    routeTarget: '/features/replenishment-task',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'picking-delivery',
    title: '拣配配送',
    icon: 'truck',
    routeTarget: '/features/picking-delivery',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'department-requisition',
    title: '科室申领',
    icon: 'users',
    routeTarget: '/features/department-requisition',
    viewFamily: 'supply-chain-department-requisition',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'department-consumption',
    title: '科室消耗',
    icon: 'package-check',
    routeTarget: '/features/department-consumption',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'stocktaking-management',
    title: '盘点管理',
    icon: 'clipboard-check',
    routeTarget: '/features/stocktaking-management',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'special-compliance',
    title: '专项与合规',
    icon: 'shield-check',
    routeTarget: '/features/special-compliance',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['udi-traceability', 'high-value-consumables', 'cold-chain-monitoring', 'recall-isolation', 'pda-offline-record']
  },
  {
    code: 'udi-traceability',
    title: 'UDI / 唯一码追溯',
    icon: 'scan-line',
    routeTarget: '/features/udi-traceability',
    viewFamily: 'udi-traceability',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'high-value-consumables',
    title: '收费耗材明细查询',
    icon: 'package-check',
    routeTarget: '/features/high-value-consumables',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'cold-chain-monitoring',
    title: '冷链监控',
    icon: 'alert-triangle',
    routeTarget: '/features/cold-chain-monitoring',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'recall-isolation',
    title: '召回与隔离',
    icon: 'shield-check',
    routeTarget: '/features/recall-isolation',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },

  {
    code: 'settlement-finance',
    title: '结算与财务',
    icon: 'landmark',
    routeTarget: '/features/settlement-finance',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['settlement-reconciliation', 'red-flush-management', 'batch-price-adjustment', 'invoice-management']
  },
  {
    code: 'settlement-reconciliation',
    title: '结算对账',
    icon: 'receipt-text',
    routeTarget: '/features/settlement-reconciliation',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },

  {
    code: 'batch-price-adjustment',
    title: '价格调整',
    icon: 'clipboard-list',
    routeTarget: '/features/batch-price-adjustment',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'invoice-management',
    title: '发票管理',
    icon: 'receipt-text',
    routeTarget: '/features/invoice-management',
    viewFamily: 'invoice-management',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'operations-decision',
    title: '运营与决策',
    icon: 'bar-chart-3',
    routeTarget: '/features/operations-decision',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['operation-cockpit', 'report-center']
  },
  {
    code: 'operation-cockpit',
    title: '运营驾驶舱',
    icon: 'bar-chart-3',
    routeTarget: '/features/operation-cockpit',
    viewFamily: 'feature',
    nodeKind: 'business'
  },
  {
    code: 'report-center',
    title: '报表中心',
    icon: 'clipboard-list',
    routeTarget: '/features/spd-his-reconciliation-report',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: [
      'spd-his-reconciliation-report',
      'supplier-delivery-ledger-report',
      'centralized-procurement-progress-report',
      'inventory-movement-summary-report'
    ]
  },
  {
    code: 'spd-his-reconciliation-report',
    title: 'SPD-HIS收费核对',
    icon: 'clipboard-check',
    routeTarget: '/features/spd-his-reconciliation-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'supplier-delivery-ledger-report',
    title: '供应商供货明细台账',
    icon: 'truck',
    routeTarget: '/features/supplier-delivery-ledger-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'centralized-procurement-progress-report',
    title: '集采执行进度报表',
    icon: 'bar-chart-3',
    routeTarget: '/features/centralized-procurement-progress-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'inventory-movement-summary-report',
    title: '全院物资进销存汇总表',
    icon: 'warehouse',
    routeTarget: '/features/inventory-movement-summary-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'system-management',
    title: '系统管理',
    icon: 'settings',
    routeTarget: '/features/system-management',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['user-management', 'role-permission', 'approval-flow-settings', 'system-config', 'field-option-management', 'config-hit-explanation', 'audit-log']
  },
  {
    code: 'user-management',
    title: '用户管理',
    icon: 'users',
    routeTarget: '/features/user-management',
    viewFamily: 'system-user-role-management',
    chunkFamily: 'system',
    aliases: ['user-list-query']
  },
  {
    code: 'role-permission',
    title: '角色权限',
    icon: 'shield-check',
    routeTarget: '/features/role-permission',
    viewFamily: 'system-user-role-management',
    chunkFamily: 'system',
    aliases: ['role-list-query']
  },
  {
    code: 'approval-flow-settings',
    title: '审批流设置',
    icon: 'shield-check',
    routeTarget: '/features/approval-flow-settings',
    viewFamily: 'system-approval-flow-settings',
    chunkFamily: 'system'
  },
  {
    code: 'system-config',
    title: '系统配置',
    icon: 'settings',
    routeTarget: '/features/system-config',
    viewFamily: 'system-config',
    chunkFamily: 'system'
  },
  {
    code: 'field-option-management',
    title: '字段管理',
    icon: 'settings',
    routeTarget: '/features/field-option-management',
    viewFamily: 'system-field-option-management',
    chunkFamily: 'system'
  },
  {
    code: 'config-hit-explanation',
    title: '配置命中说明',
    icon: 'clipboard-check',
    routeTarget: '/features/config-hit-explanation',
    viewFamily: 'system-config-hit-explanation',
    chunkFamily: 'system'
  },
  {
    code: 'audit-log',
    title: '审计日志',
    icon: 'clipboard-list',
    routeTarget: '/features/audit-log',
    viewFamily: 'feature',
    nodeKind: 'deferred'
  },
  {
    code: 'shortage-reminder',
    title: '缺货提醒',
    icon: 'file-warning',
    routeTarget: '/features/shortage-reminder',
    viewFamily: 'operational-closure',
    nodeKind: 'business',
    chunkFamily: 'supply-chain',
    aliases: ['shortage-alert']
  },
  {
    code: 'picking-distribution',
    title: '拣货配送',
    icon: 'truck',
    routeTarget: '/features/picking-distribution',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain',
    aliases: ['picking-delivery']
  },
  {
    code: 'reverse-consumption',
    title: '消耗冲销',
    icon: 'package-check',
    routeTarget: '/features/reverse-consumption',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'red-flush-management',
    title: '红冲管理',
    icon: 'file-warning',
    routeTarget: '/features/red-flush-management',
    viewFamily: 'operational-closure',
    nodeKind: 'business',
    chunkFamily: 'supply-chain',
    aliases: ['reversal-management']
  },
  {
    code: 'supplier-detail-caliber',
    title: '供应商明细口径',
    icon: 'truck',
    routeTarget: '/features/supplier-detail-caliber',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'pda-offline-record',
    title: 'PDA 离线记录',
    icon: 'clipboard-list',
    routeTarget: '/features/pda-offline-record',
    viewFamily: 'operational-closure',
    nodeKind: 'business',
    chunkFamily: 'supply-chain',
    aliases: ['pda-offline-records']
  }
] as const satisfies readonly FeatureCatalogEntry[]

export const primaryFeatureCodes = ['login', 'dashboard'] as const

export const menuGroupFeatureCodes = [
  'master-data-quota',
  'supply-chain',
  'special-compliance',
  'settlement-finance',
  'operations-decision',
  'system-management'
] as const

export const featureCatalogRedirects = [
  { from: 'user-list-query', to: 'user-management' },
  { from: 'role-list-query', to: 'role-permission' },
  { from: 'shortage-alert', to: 'shortage-reminder' },
  { from: 'reversal-management', to: 'red-flush-management' },
  { from: 'pda-offline-records', to: 'pda-offline-record' },
  { from: 'product-catalog', to: '/' },
  { from: 'quota-package-template', to: 'quota-template-maintenance' }
] as const

export const featureViewNodeGroups = {
  navigation: [
    'master-data-quota',
    'product-catalog',
    'supply-chain',
    'special-compliance',
    'settlement-finance',
    'operations-decision',
    'system-management'
  ],
  deferred: [
    'todo-tasks',
    'stock-warning',
    'settlement-exception',
    'quick-entry',
    'audit-log'
  ],
  business: [
    { from: 'shortage-alert', to: 'shortage-reminder' },
    { from: 'reversal-management', to: 'red-flush-management' },
    { from: 'pda-offline-records', to: 'pda-offline-record' }
  ]
} as const

export const featureChunkRules = [
  {
    chunkFamily: 'quota-package',
    chunkName: 'feature-quota-package',
    viewPathIncludes: ['/src/views/supply-chain/QuotaPackageView.vue']
  },
  {
    chunkFamily: 'supply-chain',
    chunkName: 'feature-supply-chain',
    viewPathIncludes: ['/src/views/supply-chain/']
  },
  {
    chunkFamily: 'master-data',
    chunkName: 'feature-master-data',
    viewPathIncludes: ['/src/views/master-data/']
  },
  {
    chunkFamily: 'catalog',
    chunkName: 'feature-catalog',
    viewPathIncludes: ['/src/views/catalog/']
  },
  {
    chunkFamily: 'system',
    chunkName: 'feature-system',
    viewPathIncludes: ['/src/views/system/']
  }
] as const satisfies readonly FeatureChunkRule[]

export function featureCodesByViewFamily(viewFamily: FeatureViewFamily): string[] {
  return featureCatalog.filter((feature) => feature.viewFamily === viewFamily).map((feature) => feature.code)
}

export function findFeature(code: string): FeatureCatalogEntry | undefined {
  return featureCatalog.find((feature) => feature.code === code)
}

export function featureRouteTarget(code: string): string {
  return findFeature(code)?.routeTarget ?? `/features/${code}`
}
