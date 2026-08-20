const DISPLAY_TEXT: Record<string, string> = {
  active: '启用',
  inactive: '停用',
  enabled: '启用',
  disabled: '停用',
  normal: '正常',
  draft: '草稿',
  pending: '待处理',
  pending_initial: '待初审',
  pending_final: '待终审',
  pending_approval: '待审批',
  pending_review: '待审核',
  pending_confirm: '待确认',
  pending_print: '待打印',
  pending_verification: '待核验',
  processing: '处理中',
  approved: '已通过',
  rejected: '已驳回',
  confirmed: '已确认',
  completed: '已完成',
  done: '已完成',
  closed: '已关闭',
  cancelled: '已取消',
  canceled: '已取消',
  terminated: '已终止',
  sent: '已发送',
  signed: '已签收',
  delivered: '已配送',
  returned: '已退回',
  reversed: '已冲销',
  settled: '已结算',
  available: '可用',
  reserved: '已预留',
  picked: '已拣货',
  consumed: '已消耗',
  in_stock: '在库',
  fulfilled: '已完成',
  isolated: '已隔离',
  void: '已作废',
  loose: '散货',
  need_recalculate: '待重新计算',
  success: '成功',
  failed: '失败',
  exception: '异常',
  warning: '预警',
  risk: '风险',
  high: '高',
  medium: '中',
  low: '低',
  yes: '是',
  no: '否',
  manual: '手工维护',
  system: '系统生成',
  created: '已创建',
  submitted: '已提交',
  planned: '已计划',
  executed: '已执行',
  printed: '已打印',
  verified: '已核验',
  approved_and_stocked: '已验收并入库',
  purchase_in: '采购入库',
  warehouse_transfer_in: '库房调拨入库',
  warehouse_transfer_out: '库房调拨出库',
  stocktaking_profit: '盘点盘盈',
  stocktaking_loss: '盘点盘亏',
  delivery_out: '配送出库',
  delivery_return: '配送退回',
  dept_consumption: '科室消耗',
  reverse_consumption: '反消耗',
  recall_isolate: '召回隔离',
  quota_unpack_in: '定数包拆包入库',
  pack_confirm: '确认打包',
  label_print: '标签打印',
  unpack_to_loose: '拆包转散货',
  create_stocktaking: '创建盘点单',
  approve_stocktaking: '盘点复核通过',
  create_batch_price_adjustment: '创建批次调价单',
  approve_batch_price_adjustment: '批次调价审批通过',
  create_packing_task: '创建打包任务',
  confirm_packing_task: '确认打包任务',
  cancel_packing_task: '取消打包任务',
  terminate_packing_task: '终止打包任务',
  print_quota_package_label: '打印定数包标签',
  unpack_quota_package_label: '定数包拆包'
}

const REMARK_TEXT: Record<string, string> = {
  'create stocktaking': '创建盘点单',
  'stocktaking approval generated inventory adjustment': '盘点审批生成库存调整',
  'create batch price adjustment': '创建批次价格调整单',
  'batch price adjustment approved': '批次价格调整审批通过',
  'create packing task and reserve loose stock': '创建打包任务并预留散货库存',
  'pack confirmed and label waits for print': '打包已确认，标签等待打印',
  'confirm packing task and generate labels': '确认打包任务并生成标签',
  'cancel packing task and release reservation': '取消打包任务并释放预留库存',
  'terminate packing task and rollback stock to loose inventory': '终止打包任务并将库存退回散货',
  'recalculate packing reservation': '重新计算打包库存预留',
  'print quota package label': '打印定数包标签',
  'unpack package label back to loose stock': '拆包并退回散货库存',
  'packing task status changed, please refresh and retry': '打包任务状态已变化，请刷新后重试'
}

function normalized(value: unknown) {
  return String(value ?? '').trim()
}

function lookup(value: string) {
  return DISPLAY_TEXT[value.toLowerCase()] ?? DISPLAY_TEXT[value.toLowerCase().replace(/[\s-]+/g, '_')]
}

export function formatStatusText(value: unknown, emptyText = '-') {
  const text = normalized(value)
  if (!text) return emptyText
  return lookup(text) ?? (/[A-Za-z]/.test(text) ? '未知状态' : text)
}

export function formatBusinessText(value: unknown, emptyText = '-') {
  const text = normalized(value)
  if (!text) return emptyText
  return lookup(text) ?? (/[A-Za-z]/.test(text) ? '系统操作' : text)
}

export function formatRemarkText(value: unknown, emptyText = '-') {
  const text = normalized(value)
  if (!text) return emptyText
  const direct = REMARK_TEXT[text.toLowerCase()] ?? lookup(text)
  if (direct) return direct
  const picked = text.match(/^picked for requisition (.+), delivery (.+)$/i)
  if (picked) return `申领单 ${picked[1]} 已拣选，生成配送单 ${picked[2]}`
  const pickedPackages = text.match(/^picked quota packages for requisition (.+)$/i)
  if (pickedPackages) return `已为申领单 ${pickedPackages[1]} 拣选定数包`
  const signedDelivery = text.match(/^delivery (.+) signed into department warehouse$/i)
  if (signedDelivery) return `配送单 ${signedDelivery[1]} 已签收入科室库房`
  if (/[A-Za-z]/.test(text)) return '系统业务备注（原始记录已保留）'
  return text
}
