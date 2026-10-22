import type { PendingProductApplicationRow } from '../api/pendingProductApplications'

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
    wait: row.status || '-'
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
