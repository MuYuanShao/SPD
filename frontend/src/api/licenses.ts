import { isAxiosError } from 'axios'
import { getData, getPage, postData, putData, deleteData, http } from './http'

export type LicenseType = 'product' | 'supplier' | 'manufacturer' | 'contract'

export interface LicenseRow {
  id: number
  licenseType: LicenseType
  licenseName: string
  licenseNo?: string
  ownerType?: string
  ownerId?: number | null
  revisionNo?: number
  effectiveStatus?: string
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
  revisionNo?: number
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
  try {
    const response = await http.get(`/licenses/attachments/${attachmentId}/file`, { responseType: 'blob' })
    const contentType = String(response.headers['content-type'] ?? '')
    if (contentType.includes('json')) {
      const result = JSON.parse(await (response.data as Blob).text())
      if (result.code !== 0) throw new Error(result.message || '附件读取失败')
    }
    return { blob: response.data as Blob, contentType }
  } catch (error) {
    if (isAxiosError(error) && error.response?.data instanceof Blob) {
      let message = ''
      try { message = JSON.parse(await error.response.data.text()).message || '' } catch { /* Non-JSON transport error. */ }
      if (message) throw new Error(message)
    }
    throw error
  }
}

export interface LicenseOwnerOption { id: number; code: string; name: string }
export interface LicenseRevision { revisionNo: number; operationType: string; snapshotJson: string; operatorName: string; createTime: string }
export function fetchLicenseOwnerOptions(type: string, keyword: string, page = 1) {
  return getPage<LicenseOwnerOption>('/licenses/owner-options', { params: { type, keyword, page, size: 10 } })
}
export function fetchLicenseHistory(id: number) { return getData<{ history: LicenseRevision[] }>(`/licenses/${id}/history`) }
export function renewLicense(id: number, payload: LicensePayload) { return postData<{ id: number }>(`/licenses/${id}/renew`, payload) }
