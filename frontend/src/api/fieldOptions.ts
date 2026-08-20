import { deleteData, getData, postData, putData } from './http'

export interface FieldOption {
  optionId: number
  fieldKey: string
  fieldLabel: string
  optionValue: string
  optionLabel: string
  sortOrder: number
  status: number
  remark?: string
}

export interface FieldOptionField {
  fieldKey: string
  fieldLabel: string
  optionCount: number
}

export interface FieldOptionPayload {
  fieldKey: string
  fieldLabel: string
  optionValue: string
  optionLabel: string
  sortOrder?: number
  status?: number
  remark?: string
}

export async function fetchFieldOptions(fieldKey: string) {
  return getData<FieldOption[]>('/system/field-options', { params: { fieldKey } })
}

export async function fetchFieldOptionFields() {
  return getData<FieldOptionField[]>('/system/field-options/fields')
}

export async function createFieldOption(payload: FieldOptionPayload) {
  return postData<{ optionId: number }>('/system/field-options', payload)
}

export async function updateFieldOption(optionId: number, payload: Partial<FieldOptionPayload>) {
  return putData<{ optionId: number }>(`/system/field-options/${optionId}`, payload)
}

export async function removeFieldOption(optionId: number) {
  return putData<{ optionId: number }>(`/system/field-options/${optionId}/delete`)
}
