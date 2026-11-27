import { isAxiosError } from 'axios'
import { deleteData, getData, http, postData, putData } from './http'
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
  source?: 'application' | 'license'
  licenseName?: string
  licenseNo?: string
  ownerName?: string
  licenseType?: string
  expireDate?: string
  licenseStatus?: string
}

export interface PendingProductTypeCount {
  key: string
  label: string
  count: number
}

export interface PendingProductApplicationRow {
  createTime: string
  updateTime: string
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
  summary?: { typeCounts?: PendingProductTypeCount[] }
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
  createTime: string
  updateTime: string
  applicationNo: string
  applicationType: string
  approvalStatus: string
  statusLabel: string
  productName: string
  productCode: string
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
  canResubmit: boolean
  enableOnly?: boolean
}

export interface PendingProductApplicationPayload {
  applicationType: string
  productCode: string
  productName: string
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

export interface PendingProductSourceRow {
  productCode: string
  productName: string
  specModel: string
  unit: string
  purchasePrice: number
  status: number
  manufacturerName: string
  supplierName: string
}

export interface PendingProductSourcePage {
  rows: PendingProductSourceRow[]
  total: number
  page: number
  size: number
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

export function fetchPendingProductQualificationAttachments(applicationNo: string) {
  return getData<PendingProductAttachment[]>(`/pending-product-applications/${encodeURIComponent(applicationNo)}/qualification-attachments`)
}

export async function fetchPendingProductQualificationBlob(applicationNo: string, attachmentId: number) {
  try {
    const response = await http.get<Blob>(
      `/pending-product-applications/${encodeURIComponent(applicationNo)}/qualification-attachments/${attachmentId}/file`,
      { responseType: 'blob' }
    )
    if (response.data.type.includes('json')) {
      const result = JSON.parse(await response.data.text())
      throw new Error(result.message || '附件读取失败')
    }
    return response.data
  } catch (error) {
    if (isAxiosError(error) && error.response?.data instanceof Blob) {
      let message = ''
      try { message = JSON.parse(await error.response.data.text()).message || '' } catch { /* Binary transport error. */ }
      if (message) throw new Error(message)
    }
    throw error
  }
}

export async function fetchPendingProductAttachmentBlob(attachmentId: number) {
  const response = await http.get<Blob>(
    `/pending-product-applications/attachments/${attachmentId}/file`,
    { responseType: 'blob' }
  )
  return response.data
}

/**
 * 待审批目录申请单里允许用户自由输入（可能含空格/换行）的文本字段。
 * 后端 WriteFieldWhitespace 会拒绝含空白的写入请求，
 * 因此这些字段在 http 拦截器里会被「就地清洗」（去掉空白）后再提交。
 *
 * productCode 也在列表内：它是用户可填的商品编码（如「SP 2024-001」），
 * 会被后端 trim 后落库，不会因为清洗而改变主键语义。
 */
export const PENDING_PRODUCT_FREE_TEXT_FIELDS = [
  'productName',
  'productCode',
  'brand',
  'manufacturerName',
  'supplierName',
  'unit',
  'purchaseUnit',
  'storageCondition',
  'changeReason',
  'contractCode',
  'registrationNo',
  'udiCode',
  'firstCategory',
  'secondCategory',
  'thirdCategory',
  'tenderSubCode',
  'productionLicenseNo',
  'businessLicenseNo',
  'registrationExpireDate',
  'applicationType'
] as const

/**
 * 待审批目录申请单的**提交字段白名单**。
 *
 * 背景：选择医院目录商品时，后端 `/source-products/{productCode}` 会返回
 * `specModel`、`status` 等只读快照字段。前端此前用 `Object.assign(createForm, detail)`
 * 把整份快照灌进表单，导致这些后端并不接收的字段（如含空格的 `specModel`）
 * 混进提交载荷，最终被后端 WriteFieldWhitespace 以
 * 「不支持空格及换行符存在！」拒绝。
 *
 * 这里显式声明允许提交的字段，提交前先做白名单投影：
 * 既杜绝未知字段泄漏，也保证载荷只包含后端 `PendingProductApplicationRequest`
 * 真实定义的字段。
 */
export const PENDING_PRODUCT_PAYLOAD_FIELDS = [
  'applicationType',
  'productCode',
  'productName',
  'brand',
  'manufacturerName',
  'supplierName',
  'unit',
  'purchasePrice',
  'retailPrice',
  'minPurchaseQty',
  'purchaseUnit',
  'conversionRate',
  'purchasePackageQty',
  'udiCode',
  'registrationNo',
  'registrationExpireDate',
  'productionLicenseNo',
  'businessLicenseNo',
  'volumeBased',
  'centralizedProcurement',
  'domestic',
  'contractCode',
  'firstCategory',
  'secondCategory',
  'thirdCategory',
  'chargeable',
  'tenderSubCode',
  'qualificationAttachmentCount',
  'highValue',
  'coldChain',
  'quotaManaged',
  'keyMonitored',
  'storageCondition',
  'changeReason'
] as const

/**
 * 按白名单裁剪申请单载荷，丢弃后端未接收的只读快照字段。
 */
export function toPendingProductPayload(
  source: Partial<PendingProductApplicationPayload>
): PendingProductApplicationPayload {
  const payload: Record<string, unknown> = {}
  for (const field of PENDING_PRODUCT_PAYLOAD_FIELDS) {
    const value = (source as Record<string, unknown>)[field]
    if (value !== undefined) payload[field] = value
  }
  return payload as unknown as PendingProductApplicationPayload
}

export async function createPendingProductApplication(payload: PendingProductApplicationPayload) {
  return postData<{ applicationNo: string; productCode?: string }>(
    '/pending-product-applications',
    toPendingProductPayload(payload),
    { freeTextFields: PENDING_PRODUCT_FREE_TEXT_FIELDS }
  )
}

export async function uploadPendingProductAttachment(applicationNo: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return postData<{ attachmentId: number }>(
    `/pending-product-applications/${applicationNo}/attachments`,
    form,
    // multipart：文件名允许含空格，同时放宽超时（大附件 10s 会中断）
    { multipart: true, uploadTimeout: 60000 }
  )
}

export async function deletePendingProductAttachment(applicationNo: string, attachmentId: number) {
  return deleteData<{ attachmentId: number }>(
    `/pending-product-applications/${applicationNo}/attachments/${attachmentId}`
  )
}

export async function fetchPendingProductPartnerOptions() {
  return getData<PendingProductPartnerOptions>('/pending-product-applications/partner-options')
}

export async function fetchPendingProductSourceProducts(keyword = '', page = 1, size = 10) {
  return getData<PendingProductSourcePage>('/pending-product-applications/source-products', {
    params: { keyword, page, size }
  })
}

export async function fetchPendingProductSourceDetail(productCode: string) {
  return getData<PendingProductApplicationPayload & { status: number }>(
    `/pending-product-applications/source-products/${encodeURIComponent(productCode)}`
  )
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
    toPendingProductPayload(payload),
    { freeTextFields: PENDING_PRODUCT_FREE_TEXT_FIELDS }
  )
}

export async function resubmitPendingProductApplication(
  applicationNo: string,
  payload: PendingProductApplicationPayload | Pick<PendingProductApplicationPayload, 'changeReason'>
) {
  return putData<{ applicationNo: string; status: string }>(
    `/pending-product-applications/${applicationNo}/resubmit`,
    toPendingProductPayload(payload),
    { freeTextFields: PENDING_PRODUCT_FREE_TEXT_FIELDS }
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
    formData,
    // multipart：导入文件名允许含空格，同时放宽超时
    { multipart: true, uploadTimeout: 60000 }
  )
}
