import type { PendingProductApplicationRow } from '../api/pendingProductApplications'

export const approvalFieldGroups = [
  { key: 'all', label: '全部字段' },
  { key: 'basic', label: '基础信息' },
  { key: 'qualification', label: '资质证照' },
  { key: 'category', label: '分类目录' },
  { key: 'purchase', label: '采购监管' },
  { key: 'workflow', label: '审批进度' }
]

const approvalNavigableFields = [
  { label: '申请类型', columnIndex: 3, group: 'basic' },
  { label: '申请人', columnIndex: 4, group: 'basic' },
  { label: '供应商', columnIndex: 5, group: 'qualification' },
  { label: '厂家', columnIndex: 6, group: 'qualification' },
  { label: '注册证号', columnIndex: 7, group: 'qualification' },
  { label: '合同编码', columnIndex: 8, group: 'purchase' },
  { label: '一级分类', columnIndex: 9, group: 'category' },
  { label: '二级分类', columnIndex: 10, group: 'category' },
  { label: '三级分类', columnIndex: 11, group: 'category' },
  { label: '是否带量', columnIndex: 12, group: 'purchase' },
  { label: '是否集采', columnIndex: 13, group: 'purchase' },
  { label: '是否国产', columnIndex: 14, group: 'purchase' },
  { label: '是否收费', columnIndex: 15, group: 'purchase' },
  { label: '重点监控', columnIndex: 16, group: 'purchase' },
  { label: '采购价', columnIndex: 17, group: 'purchase' },
  { label: 'UDI 编码', columnIndex: 18, group: 'qualification' },
  { label: '风险标签', columnIndex: 19, group: 'workflow' },
  { label: '节点 / 等待', columnIndex: 20, group: 'workflow' },
  { label: '变更记录', columnIndex: 21, group: 'workflow' }
]

export const approvalFieldChips = approvalNavigableFields.map(field => field.label)

export const approvalFieldColumnIndex: Record<string, number> = Object.fromEntries(
  approvalNavigableFields.map(field => [field.label, field.columnIndex])
)

export const approvalFieldGroupTargets: Record<string, string> = {
  basic: '申请类型',
  qualification: '供应商',
  category: '一级分类',
  purchase: '合同编码',
  workflow: '风险标签'
}

export const approvalFieldToGroup: Record<string, string> = Object.fromEntries(
  approvalNavigableFields.map(field => [field.label, field.group])
)

export function approvalRiskLabels(row: PendingProductApplicationRow) {
  if (row.warning) return ['资质临期', row.type === '新品准入' ? '价格复核' : '影响采购价']
  if (row.keyMonitored) return ['重点监控']
  if (row.type === '资质更新') return ['附件齐全']
  if (row.type === '信息变更') return ['供应商变更']
  return ['附件齐全']
}

export function approvalRiskTone(label: string) {
  if (label.includes('临期') || label.includes('价格') || label.includes('库房') || label.includes('重点')) return 'orange'
  if (label.includes('冷链') || label.includes('高值')) return 'red'
  if (label.includes('变更')) return 'blue'
  return 'green'
}

function yesNo(value?: boolean) {
  if (value === undefined || value === null) return '-'
  return value ? '是' : '否'
}

function money(value?: number) {
  if (value === undefined || value === null) return '-'
  return `¥ ${Number(value).toFixed(2)}`
}

export function approvalWideField(row: PendingProductApplicationRow, field: string) {
  const values: Record<string, string> = {
    applicant: row.applicant,
    supplier: row.supplier,
    manufacturer: row.manufacturerName || '-',
    registrationNo: row.registrationNo || '-',
    contractCode: row.contractCode || '-',
    firstCategory: row.firstCategory || '-',
    secondCategory: row.secondCategory || '-',
    thirdCategory: row.thirdCategory || '-',
    volumeBased: yesNo(row.volumeBased),
    centralized: yesNo(row.centralizedProcurement),
    domestic: yesNo(row.domestic),
    chargeable: yesNo(row.chargeable),
    keyMonitored: yesNo(row.keyMonitored),
    purchasePrice: money(row.purchasePrice),
    udiCode: row.udiCode || '-',
    quotaManaged: yesNo(row.quotaManaged),
    wait: row.status.includes('复审') ? '复审中 / 6小时' : row.warning ? '初审中 / 1天' : '初审中 / 18小时'
  }
  return values[field] ?? '-'
}

export function approvalTypeClass(type: string): string {
  const map: Record<string, string> = {
    '新品准入': 'new',
    '信息变更': 'change',
    '资质更新': 'qualification',
    '价格调整': 'price',
    '停用申请': 'deactivate'
  }
  return map[type] ?? ''
}
