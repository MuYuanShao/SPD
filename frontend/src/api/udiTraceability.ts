import { getData, postData, type PageResult } from './http'

export interface UdiTraceRecord {
  traceCodeId: number
  udiCode: string
  uniqueCode: string
  traceScope: 'high_value' | 'low_value_quota_pack'
  packageLabelNo?: string
  templateCode?: string
  templateName?: string
  packageQuantity?: number
  packageUnit?: string
  packageStatus?: string
  productCode: string
  productName: string
  specModel?: string
  manufacturerName?: string
  supplierName?: string
  batchNo?: string
  expireDate?: string
  currentLocation?: string
  currentDepartment?: string
  currentStatus: string
  responsiblePerson?: string
  patientNo?: string
  patientNameMasked?: string
  riskLevel: string
  lastEventName?: string
  lastEventTime?: string
}

export interface UdiTraceEvent {
  traceEventId: number
  eventNo: string
  eventType: string
  eventName: string
  bizNo?: string
  locationName?: string
  departmentName?: string
  operatorName?: string
  eventTime: string
  status: string
  remark?: string
  sortOrder: number
}

export interface UdiTraceDetail {
  record: UdiTraceRecord
  timeline: UdiTraceEvent[]
}

export interface UdiTraceOption {
  value: string
  label: string
}

export interface UdiTraceOptions {
  statuses: UdiTraceOption[]
  traceScopes: UdiTraceOption[]
  eventTypes: UdiTraceOption[]
}

export type UdiTraceSummary = Record<
  'totalCodes' | 'highValueCodes' | 'quotaPackageCodes' | 'exceptionCodes' | 'inStockCodes' | 'consumedCodes',
  number
>

export async function fetchUdiTraceSummary(params: Record<string, string> = {}) {
  return getData<UdiTraceSummary>('/udi-traceability/summary', { params })
}

export async function fetchUdiTraceRecords(params: Record<string, string> = {}) {
  return getData<PageResult<UdiTraceRecord>>('/udi-traceability/records', { params })
}

export async function fetchUdiTraceDetail(code: string) {
  return getData<UdiTraceDetail>(`/udi-traceability/records/${encodeURIComponent(code)}`)
}

export async function fetchUdiTraceOptions() {
  return getData<UdiTraceOptions>('/udi-traceability/options')
}

export async function signQuotaPackageByCode(code: string) {
  return postData<{ code: string; deliveryNo: string; status: string }>(
    '/udi-traceability/quota-packages/sign',
    { code }
  )
}

export async function consumeQuotaPackageByCode(payload: { code: string; deptName?: string }) {
  return postData<{
    code: string
    consumptionNo: string
    status: string
    quantity: number
    amount: number
    settlementEligible: boolean
  }>('/udi-traceability/quota-packages/consume', payload)
}
