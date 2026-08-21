import { getData, postData, putData } from './http'
import type { PartnerOption } from './masterData'

export interface PendingProductPartnerOptions {
  manufacturers: PartnerOption[]
  suppliers: PartnerOption[]
}

export interface PendingProductTypeCount {
  key: string
  label: string
  count: number
}

export interface PendingProductApplicationRow {
  no: string
  type: string
  product: string
  supplier: string
  applicant: string
  time: string
  status: string
  statusTone: string
  warning: boolean
  productCode?: string
  changeSummary?: string
  manufacturerName?: string
  registrationNo?: string
  contractCode?: string
  firstCategory?: string
  secondCategory?: string
  thirdCategory?: string
  volumeBased?: boolean
  centralizedProcurement?: boolean
  domestic?: boolean
  chargeable?: boolean
  purchasePrice?: number
  purchaseUnit?: string
  udiCode?: string
  quotaManaged?: boolean
}

export interface PendingProductApplicationPage {
  typeCounts: PendingProductTypeCount[]
  rows: PendingProductApplicationRow[]
  total: number
  page: number
  size: number
}

export interface ApprovalTimelineNode {
  title: string
  operator: string
  time: string
  status: string
}

export interface PendingProductChangeItem {
  fieldName: string
  beforeValue: string
  afterValue: string
}

export interface PendingProductApplicationDetail {
  applicationNo: string
  applicationType: string
  approvalStatus: string
  statusLabel: string
  productName: string
  productCode: string
  specModel: string
  brand: string
  manufacturerName: string
  supplierName: string
  unit: string
  purchasePrice: number
  retailPrice: number
  minPurchaseQty: number
  purchaseUnit: string
  conversionRate: number
  purchasePackageQty: number | null
  udiCode: string
  registrationNo: string
  registrationExpireDate: string
  productionLicenseNo: string
  businessLicenseNo: string
  volumeBased: boolean
  centralizedProcurement: boolean
  domestic: boolean
  contractCode: string
  firstCategory: string
  secondCategory: string
  thirdCategory: string
  chargeable: boolean
  tenderSubCode: string
  qualificationAttachmentCount: number
  highValue: boolean
  coldChain: boolean
  quotaManaged: boolean
  storageCondition: string
  applicant: string
  submitTime: string
  approveOpinion?: string
  initialReviewOpinion?: string
  finalReviewOpinion?: string
  returnReason?: string
  rejectReason?: string
  changeItems: PendingProductChangeItem[]
  timeline: ApprovalTimelineNode[]
  canApprove: boolean
}

export interface PendingProductApplicationPayload {
  applicationType: string
  productCode: string
  productName: string
  specModel: string
  brand?: string
  manufacturerName?: string
  supplierName?: string
  unit: string
  purchasePrice: number
  retailPrice?: number | null
  minPurchaseQty: number
  purchaseUnit?: string
  conversionRate: number
  purchasePackageQty?: number | null
  udiCode?: string
  registrationNo?: string
  registrationExpireDate?: string
  productionLicenseNo?: string
  businessLicenseNo?: string
  volumeBased?: boolean
  centralizedProcurement?: boolean
  domestic?: boolean
  contractCode?: string
  firstCategory?: string
  secondCategory?: string
  thirdCategory?: string
  chargeable?: boolean
  tenderSubCode?: string
  qualificationAttachmentCount: number
  highValue: boolean
  coldChain: boolean
  quotaManaged: boolean
  storageCondition?: string
  changeReason?: string
}

export async function fetchPendingProductApplications(type: string, scope = 'todo', keyword = '', mineStatus = 'pending', page = 1, size = 25) {
  return getData<PendingProductApplicationPage>('/pending-product-applications', {
    params: { type, scope, keyword, mineStatus, page, size }
  })
}

export async function fetchPendingProductApplicationDetail(applicationNo: string) {
  return getData<PendingProductApplicationDetail>(
    `/pending-product-applications/${applicationNo}`
  )
}

export async function createPendingProductApplication(payload: PendingProductApplicationPayload) {
  return postData<{ applicationNo: string }>('/pending-product-applications', payload)
}

export async function fetchPendingProductPartnerOptions() {
  return getData<PendingProductPartnerOptions>('/pending-product-applications/partner-options')
}

export async function approvePendingProductApplication(applicationNo: string, action: string, opinion: string) {
  return putData<{ applicationNo: string; status: string }>(
    `/pending-product-applications/${applicationNo}/action`,
    { action, opinion }
  )
}

export async function batchApprovePendingProductApplications(applicationNos: string[], action: string, opinion: string) {
  return putData<{ successCount: number; failCount: number }>(
    '/pending-product-applications/batch-action',
    { applicationNos, action, opinion }
  )
}

export async function updatePendingProductApplication(applicationNo: string, payload: PendingProductApplicationPayload) {
  return putData<{ applicationNo: string }>(
    `/pending-product-applications/${applicationNo}/update`,
    payload
  )
}

export async function resubmitPendingProductApplication(
  applicationNo: string,
  payload: PendingProductApplicationPayload
) {
  return putData<{ applicationNo: string; status: string }>(
    `/pending-product-applications/${applicationNo}/resubmit`,
    payload
  )
}

export async function exportPendingProductApplications(type: string, scope = 'todo', keyword = '') {
  return getData<PendingProductApplicationRow[]>('/pending-product-applications/export', {
    params: { type, scope, keyword }
  })
}

export async function importPendingProductApplications(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number; skippedRows?: number; duplicateRows?: number; message?: string }>(
    '/pending-product-applications/import',
    formData
  )
}
