import { getData, http, postData, putData } from './http'
import type { PartnerOption } from './masterData'

export interface PendingProductPartnerOptions {
  manufacturers: PartnerOption[]
  suppliers: PartnerOption[]
}

export interface PendingProductAttachment {
  attachmentId: number
  fileName: string
  contentType: string
  fileSize: number
  category: string
  createTime: string
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
  keyMonitored?: boolean
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
  keyMonitored: boolean
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
  keyMonitored: boolean
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

export async function fetchPendingProductAttachments(applicationNo: string) {
  return getData<PendingProductAttachment[]>(`/pending-product-applications/${applicationNo}/attachments`)
}

export async function fetchPendingProductAttachmentBlob(attachmentId: number) {
  const response = await http.get<Blob>(
    `/pending-product-applications/attachments/${attachmentId}/file`,
    { responseType: 'blob' }
  )
  return response.data
}

export async function createPendingProductApplication(payload: PendingProductApplicationPayload) {
  return postData<{ applicationNo: string; productCode?: string }>('/pending-product-applications', payload)
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

export async function downloadPendingProductImportTemplate() {
  const response = await http.get<Blob>('/pending-product-applications/import-template.xlsx', {
    responseType: 'blob'
  })
  return response.data
}

export async function importPendingProductApplications(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number; skippedRows?: number; duplicateRows?: number; message?: string }>(
    '/pending-product-applications/import',
    formData
  )
}
