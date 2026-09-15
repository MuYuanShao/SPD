import { getData, type PageResult } from './http'

/** Fields projected by the charge-detail read model; amounts are never recomputed in the UI. */
export interface HighValueChargeDetail {
  bizNo: string
  externalChargeNo?: string | null
  deptName?: string | null
  patientNo?: string | null
  patientName?: string | null
  productCode?: string | null
  productName?: string | null
  specModel?: string | null
  unit?: string | null
  supplierName?: string | null
  manufacturerName?: string | null
  registrationNo?: string | null
  registrationExpireDate?: string | null
  uniqueCode?: string | null
  udiCode?: string | null
  chargeQuantity: number | null
  unitPrice: number | null
  chargeAmount: number | null
  chargeTime?: string | null
  createTime?: string | null
}

export interface ChargeDetailFilters {
  patientNo: string
  patientName: string
  productCode: string
  productName: string
  dateFrom: string
  dateTo: string
  uid: string
  udi: string
  supplierName: string
  manufacturerName: string
  registrationNo: string
}

export function fetchHighValueCharges(params: ChargeDetailFilters & { page: number; size: number }, signal?: AbortSignal) {
  return getData<PageResult<HighValueChargeDetail>>('/operational-closure/lists/high-value', { params, signal })
}
