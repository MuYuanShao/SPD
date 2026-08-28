import { featureRouteTarget } from './featureCatalog'

export type AssistantActionKey = 'inventory' | 'consumption' | 'replenishment' | 'udi'

export interface AssistantReply {
  title: string
  lines: string[]
  actionLabel?: string
  actionRoute?: string
}

export const assistantQuickActions: Array<{ key: AssistantActionKey; label: string }> = [
  { key: 'inventory', label: '查询科室库存' },
  { key: 'consumption', label: '分析耗材消耗' },
  { key: 'replenishment', label: '生成补货建议' },
  { key: 'udi', label: '追踪UDI唯一码' }
]

/**
 * The assistant is a navigation aid until a dedicated assistant backend is available.
 * Never present fabricated quantities or amounts as live hospital data.
 */
export const assistantReplies: Record<AssistantActionKey, AssistantReply> = {
  inventory: {
    title: '查看实时库存与预警信息',
    lines: ['库存结果由库存管理页面按当前账号的数据权限从业务数据库实时加载。'],
    actionLabel: '查看实时库存',
    actionRoute: featureRouteTarget('inventory-management')
  },
  consumption: {
    title: '查看实时耗材消耗分析',
    lines: ['消耗数量与金额以运营驾驶舱和监管报表中的实时查询结果为准。'],
    actionLabel: '查看运营驾驶舱',
    actionRoute: featureRouteTarget('operation-cockpit')
  },
  replenishment: {
    title: '查看实时补货建议',
    lines: ['补货任务会根据当前库存、预警阈值及账号权限加载最新业务数据。'],
    actionLabel: '进入补货任务',
    actionRoute: featureRouteTarget('replenishment-task')
  },
  udi: {
    title: '进入 UDI / 唯一码追溯',
    lines: ['追溯页面将从业务数据库查询入库批次、当前位置、使用记录及患者关联信息。'],
    actionLabel: '进入UDI追溯',
    actionRoute: featureRouteTarget('udi-traceability')
  }
}

export function assistantReplyForPrompt(value: string): AssistantReply {
  if (/UDI|唯一码|追溯/i.test(value)) return assistantReplies.udi
  if (/补货|补库|补充库存/.test(value)) return assistantReplies.replenishment
  if (/消耗|金额|环比|趋势/.test(value)) return assistantReplies.consumption
  if (/缺货|不足|库存|预警/.test(value)) return assistantReplies.inventory
  return {
    title: '请选择对应业务入口查看实时数据',
    lines: ['支持库存与预警、耗材消耗、补货任务及 UDI / 唯一码追溯。']
  }
}
