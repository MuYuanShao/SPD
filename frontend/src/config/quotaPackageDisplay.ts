export const taskStatusText: Record<string, string> = {
  pending_confirm: '待确认',
  need_recalculate: '需重算',
  confirmed: '已确认',
  cancelled: '已取消',
  terminated: '已终止',
  expired: '已过期'
}

export const labelStatusText: Record<string, string> = {
  pending_print: '待打印',
  available: '可用',
  void: '已解包'
}

export const quotaPackageSections = {
  'quota-template-maintenance': {
    title: '定数包模版维护',
    subtitle: '维护科室、商品、包内数量和启用状态，是后续打包任务的基础。'
  },
  'packing-task-confirmation': {
    title: '打包任务确定',
    subtitle: '根据定数包模版生成打包任务，并确认扣减散货库存生成标签。'
  },
  'quota-label-unpack': {
    title: '定数包标签与解包',
    subtitle: '查看生成的定数包标签，支持打印、重打及将可用定数包解包回散货。'
  },
  'quota-package-events': {
    title: '定数包事件',
    subtitle: '追踪定数包从打包、标签生成到解包的全流程事件。'
  },
  'packable-loose-snapshot': {
    title: '可打包散货快照',
    subtitle: '查看当前可用于打包的散货批次、批次单价和可用数量。'
  }
} as const

export type QuotaPackageSectionCode = keyof typeof quotaPackageSections
