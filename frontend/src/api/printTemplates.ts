import { getData, putData } from './http'

export interface PrintTemplateField {
  code: string
  label: string
  enabled: boolean
  value?: string
}

export interface PrintTemplateRow {
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

export function fetchPrintTemplates() {
  return getData<PrintTemplateRow[]>('/print-templates')
}

export function fetchPrintTemplate(templateType: string) {
  return getData<PrintTemplateRow>(`/print-templates/${templateType}`)
}

export function updatePrintTemplate(templateType: string, payload: PrintTemplatePayload) {
  return putData<{ templateType: string }>(`/print-templates/${templateType}`, payload)
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
