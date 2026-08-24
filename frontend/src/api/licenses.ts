import { getData, getPage, postData, putData, deleteData, http } from './http'

export type LicenseType = 'product' | 'supplier' | 'manufacturer' | 'contract'

export interface LicenseRow {
  id: number
  licenseType: LicenseType
  licenseName: string
  licenseNo?: string
  ownerType?: string
  ownerCode?: string
  ownerName?: string
  partyA?: string
  partyB?: string
  contractAmount?: number
  issueDate?: string
  expireDate?: string
  status: number
  remark?: string
  attachmentCount?: number
}

export interface LicensePayload {
  licenseType: LicenseType
  licenseName: string
  licenseNo?: string
  ownerType?: string
  ownerId?: number | null
  ownerCode?: string
  ownerName?: string
  partyA?: string
  partyB?: string
  contractAmount?: number | null
  issueDate?: string
  expireDate?: string
  status?: number
  remark?: string
}

export interface LicenseAttachment {
  id: number
  fileName: string
  ext: string
  fileType: string
  size: number
  category: string
  description?: string
  createTime: string
}

export function fetchLicenses(type: LicenseType | '', keyword = '', page = 1, size = 20) {
  return getPage<LicenseRow>('/licenses', { params: { type, keyword, page, size } })
}

export function fetchLicenseDetail(licenseId: number) {
  return getData<LicenseRow & { attachments: LicenseAttachment[] }>(`/licenses/${licenseId}`)
}

export function createLicense(payload: LicensePayload) {
  return postData<{ id: number }>('/licenses', payload)
}

export function updateLicense(licenseId: number, payload: LicensePayload) {
  return putData<{ id: number }>(`/licenses/${licenseId}`, payload)
}

export function removeLicense(licenseId: number) {
  return deleteData<{ id: number }>(`/licenses/${licenseId}`)
}

export function fetchLicenseAttachments(licenseId: number) {
  return getData<LicenseAttachment[]>(`/licenses/${licenseId}/attachments`)
}

export function uploadLicenseAttachment(licenseId: number, file: File, category = 'other') {
  const form = new FormData()
  form.append('file', file)
  form.append('category', category)
  return postData<{ attachmentId: number }>(`/licenses/${licenseId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 以 blob 形式获取证照附件（携带认证头），返回可预览/下载的对象 URL。 */
export async function fetchLicenseAttachmentBlob(attachmentId: number) {
  const response = await http.get(`/licenses/attachments/${attachmentId}/file`, { responseType: 'blob' })
  return { blob: response.data as Blob, contentType: String(response.headers['content-type'] ?? '') }
}
