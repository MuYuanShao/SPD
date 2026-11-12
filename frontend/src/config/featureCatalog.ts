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
  // ── 细分业务图标：让每个菜单项拥有语义化且互不重复的图标 ──
  | 'book-open-text'
  | 'badge-check'
  | 'banknote'
  | 'bell-ring'
  | 'boxes-stacked'
  | 'building-2'
  | 'clipboard-clock'
  | 'clipboard-pen'
  | 'clipboard-plus'
  | 'clipboard-type'
  | 'coins'
  | 'file-clock'
  | 'file-check-2'
  | 'file-minus'
  | 'file-plus-2'
  | 'file-search-2'
  | 'file-spreadsheet'
  | 'file-text'
  | 'flask-conical'
  | 'git-branch'
  | 'grid-2x2'
  | 'heart-pulse'
  | 'key-round'
  | 'layers-3'
  | 'list-checks'
  | 'package-open'
  | 'package-plus'
  | 'printer'
  | 'radio-tower'
  | 'receipt'
  | 'refresh-cw'
  | 'scroll-text'
  | 'shield-alert'
  | 'stethoscope'
  | 'tags'
  | 'thermometer-snowflake'
  | 'trending-up'
  | 'user-cog'
  | 'user-round-check'
  | 'wrench'
  | 'zap'

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
  | 'system-audit-log'
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
    title: '基础数据管理',
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
      'license-management'
    ]
  },
  {
    code: 'product-catalog',
    title: '商品目录',
    icon: 'grid-2x2',
    routeTarget: '/',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: ['pending-product-catalog', 'hospital-product-catalog']
  },
  {
    code: 'pending-product-catalog',
    title: '待审批目录',
    icon: 'clipboard-clock',
    routeTarget: '/features/pending-product-catalog',
    viewFamily: 'pending-product-catalog',
    chunkFamily: 'catalog'
  },
  {
    code: 'hospital-product-catalog',
    title: '医院目录',
    icon: 'badge-check',
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
    icon: 'boxes-stacked',
    routeTarget: '/features/supplier-manufacturer-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data',
    aliases: ['supplier-management', 'manufacturer-management']
  },
  {
    code: 'campus-management',
    title: '院区管理',
    icon: 'building-2',
    routeTarget: '/features/campus-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'department-management',
    title: '科室管理',
    icon: 'stethoscope',
    routeTarget: '/features/department-management',
    viewFamily: 'master-data-list',
    chunkFamily: 'master-data'
  },
  {
    code: 'department-warehouse-catalog',
    title: '科室库房目录',
    icon: 'layers-3',
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
    icon: 'file-check-2',
    routeTarget: '/features/license-management',
    viewFamily: 'license-management',
    chunkFamily: 'master-data'
  },
  {
    code: 'quota-package-template',
    title: '定数包管理',
    icon: 'boxes',
    routeTarget: '/features/quota-template-maintenance',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package',
    nodeKind: 'navigation',
    children: [
      'quota-template-maintenance',
      'quota-safety-stock',
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
    icon: 'package-plus',
    routeTarget: '/features/packing-task-confirmation',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'quota-label-unpack',
    title: '定数包标签与解包',
    icon: 'package-open',
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
    icon: 'scroll-text',
    routeTarget: '/features/packable-loose-snapshot',
    viewFamily: 'quota-package',
    chunkFamily: 'quota-package'
  },
  {
    code: 'print-template-settings',
    title: '打印模板调整',
    icon: 'printer',
    routeTarget: '/features/print-template-settings',
    viewFamily: 'print-template-settings',
    chunkFamily: 'system'
  },
  {
    code: 'quota-safety-stock',
    title: '定数安全量',
    icon: 'shield-alert',
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
      'picking-records',
      'department-requisition',
      'department-consumption',
      'stocktaking-management'
    ]
  },
  {
    code: 'purchase-management',
    title: '采购管理',
    icon: 'clipboard-plus',
    routeTarget: '/features/purchase-management',
    viewFamily: 'supply-chain-purchase-management',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'receiving-acceptance',
    title: '收货验收',
    icon: 'package-check',
    routeTarget: '/features/receiving-acceptance',
    viewFamily: 'supply-chain-receiving-acceptance',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'inventory-management',
    title: '库存管理',
    icon: 'boxes-stacked',
    routeTarget: '/features/inventory-management',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'inventory-events',
    title: '库存交易流水',
    icon: 'refresh-cw',
    routeTarget: '/features/inventory-events',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'replenishment-task',
    title: '补货任务',
    icon: 'clipboard-type',
    routeTarget: '/features/replenishment-task',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'picking-delivery',
    title: '拣配配送',
    icon: 'list-checks',
    routeTarget: '/features/picking-delivery',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'picking-records',
    title: '拣配记录',
    icon: 'file-clock',
    routeTarget: '/features/picking-records',
    viewFamily: 'feature',
    nodeKind: 'business',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'department-requisition',
    title: '科室申领',
    icon: 'file-plus-2',
    routeTarget: '/features/department-requisition',
    viewFamily: 'supply-chain-department-requisition',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'department-consumption',
    title: '科室消耗',
    icon: 'heart-pulse',
    routeTarget: '/features/department-consumption',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'stocktaking-management',
    title: '盘点管理',
    icon: 'file-search-2',
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
    children: ['udi-traceability', 'high-value-consumables', 'cold-chain-monitoring', 'recall-isolation', 'pda-offline-record', 'audit-log']
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
    icon: 'banknote',
    routeTarget: '/features/high-value-consumables',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'cold-chain-monitoring',
    title: '冷链监控',
    icon: 'thermometer-snowflake',
    routeTarget: '/features/cold-chain-monitoring',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'recall-isolation',
    title: '召回与隔离',
    icon: 'radio-tower',
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
    icon: 'coins',
    routeTarget: '/features/settlement-reconciliation',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },

  {
    code: 'batch-price-adjustment',
    title: '价格调整',
    icon: 'tags',
    routeTarget: '/features/batch-price-adjustment',
    viewFamily: 'inventory-workbench',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'invoice-management',
    title: '发票管理',
    icon: 'receipt',
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
    icon: 'trending-up',
    routeTarget: '/features/operation-cockpit',
    viewFamily: 'feature',
    nodeKind: 'business'
  },
  {
    code: 'report-center',
    title: '报表中心',
    icon: 'file-spreadsheet',
    routeTarget: '/features/spd-his-reconciliation-report',
    viewFamily: 'feature',
    nodeKind: 'navigation',
    children: [
      'spd-his-reconciliation-report',
      'supplier-delivery-ledger-report',
      'centralized-procurement-progress-report',
      'inventory-movement-summary-report',
      'inventory-product-detail-report'
    ]
  },
  {
    code: 'spd-his-reconciliation-report',
    title: 'SPD-HIS收费核对',
    icon: 'book-open-text',
    routeTarget: '/features/spd-his-reconciliation-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'supplier-delivery-ledger-report',
    title: '供应商供货明细台账',
    icon: 'file-check-2',
    routeTarget: '/features/supplier-delivery-ledger-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'centralized-procurement-progress-report',
    title: '集采执行进度报表',
    icon: 'trending-up',
    routeTarget: '/features/centralized-procurement-progress-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'inventory-movement-summary-report',
    title: '全院物资进销存汇总表',
    icon: 'bar-chart-3',
    routeTarget: '/features/inventory-movement-summary-report',
    viewFamily: 'report-center',
    nodeKind: 'business',
    chunkFamily: 'system'
  },
  {
    code: 'inventory-product-detail-report',
    title: '进销存商品明细',
    icon: 'layers-3',
    routeTarget: '/features/inventory-product-detail-report',
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
    children: ['user-management', 'role-permission', 'approval-flow-settings', 'system-config', 'field-option-management', 'config-hit-explanation']
  },
  {
    code: 'user-management',
    title: '用户管理',
    icon: 'user-round-check',
    routeTarget: '/features/user-management',
    viewFamily: 'system-user-role-management',
    chunkFamily: 'system',
    aliases: ['user-list-query']
  },
  {
    code: 'role-permission',
    title: '角色权限',
    icon: 'key-round',
    routeTarget: '/features/role-permission',
    viewFamily: 'system-user-role-management',
    chunkFamily: 'system',
    aliases: ['role-list-query']
  },
  {
    code: 'approval-flow-settings',
    title: '审批流设置',
    icon: 'git-branch',
    routeTarget: '/features/approval-flow-settings',
    viewFamily: 'system-approval-flow-settings',
    chunkFamily: 'system'
  },
  {
    code: 'system-config',
    title: '系统配置',
    icon: 'wrench',
    routeTarget: '/features/system-config',
    viewFamily: 'system-config',
    chunkFamily: 'system'
  },
  {
    code: 'field-option-management',
    title: '字段管理',
    icon: 'clipboard-pen',
    routeTarget: '/features/field-option-management',
    viewFamily: 'system-field-option-management',
    chunkFamily: 'system'
  },
  {
    code: 'config-hit-explanation',
    title: '配置命中说明',
    icon: 'file-text',
    routeTarget: '/features/config-hit-explanation',
    viewFamily: 'system-config-hit-explanation',
    chunkFamily: 'system'
  },
  {
    code: 'audit-log',
    title: '系统操作记录',
    icon: 'scroll-text',
    routeTarget: '/features/audit-log',
    viewFamily: 'system-audit-log',
    chunkFamily: 'system',
    nodeKind: 'business'
  },
  {
    code: 'shortage-reminder',
    title: '缺货提醒',
    icon: 'bell-ring',
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
    icon: 'refresh-cw',
    routeTarget: '/features/reverse-consumption',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'red-flush-management',
    title: '红冲管理',
    icon: 'file-minus',
    routeTarget: '/features/red-flush-management',
    viewFamily: 'operational-closure',
    nodeKind: 'business',
    chunkFamily: 'supply-chain',
    aliases: ['reversal-management']
  },
  {
    code: 'supplier-detail-caliber',
    title: '供应商明细口径',
    icon: 'flask-conical',
    routeTarget: '/features/supplier-detail-caliber',
    viewFamily: 'operational-closure',
    chunkFamily: 'supply-chain'
  },
  {
    code: 'pda-offline-record',
    title: 'PDA 离线记录',
    icon: 'radio-tower',
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
  'quota-package-template',
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
    'quota-package-template',
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
    'quick-entry'
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
  // 精确匹配优先；其次匹配别名（aliases 指向主条目，主条目包含完整 title）
  const direct = featureCatalog.find((feature) => feature.code === code)
  if (direct) return direct
  for (const feature of featureCatalog) {
    const aliases = (feature as { aliases?: readonly string[] }).aliases
    if (aliases && aliases.includes(code)) return feature
  }
  return undefined
}

export function featureRouteTarget(code: string): string {
  return findFeature(code)?.routeTarget ?? `/features/${code}`
}
