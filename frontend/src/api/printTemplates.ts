import { getData, postData, putData } from './http'

export interface PrintTemplateField {
  code: string
  label: string
  enabled: boolean
  value?: string
}

export interface PrintTemplateRow {
  createTime?: string
  updateTime?: string
  id: number
  templateCode: string
  templateName: string
  templateType: string
  fieldsJson: string
  paperWidthMm: number
  paperHeightMm: number
  status: number
  remark?: string
}

export interface PrintTemplatePayload {
  templateName: string
  fieldsJson: string
  paperWidthMm: number
  paperHeightMm: number
  status: number
  remark?: string
}

const templateTextOptions = { freeTextFields: ['templateName', 'fieldsJson', 'remark'] }

export function fetchPrintTemplates() {
  return getData<PrintTemplateRow[]>('/print-templates')
}

export function fetchActiveLabelPrintTemplates() {
  return getData<{ templates: PrintTemplateRow[]; defaultTemplateId: number }>('/print-templates/label-print')
}

export function updatePrintTemplate(id: number, payload: PrintTemplatePayload) {
  return putData<{ id: number }>(`/print-templates/by-id/${id}`, payload, templateTextOptions)
}

export function createPrintTemplate(templateCode: string, config: PrintTemplatePayload) {
  return postData<{ templateCode: string }>('/print-templates', { templateCode, ...config }, templateTextOptions)
}

/** 解析模板字段 JSON，返回可编辑的字段数组。 */
export function parseTemplateFields(fieldsJson: string | undefined | null): PrintTemplateField[] {
  if (!fieldsJson) return []
  try {
    const parsed = JSON.parse(fieldsJson)
    if (Array.isArray(parsed)) {
      return parsed
        .filter((item) => item && typeof item === 'object' && typeof item.code === 'string')
        .map((item) => ({
          code: String(item.code),
          label: String(item.label ?? item.code),
          enabled: item.enabled !== false,
          value: item.value == null ? undefined : String(item.value)
        }))
    }
  } catch {
    // 忽略非法配置，按空模板处理
  }
  return []
}
