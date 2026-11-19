import { getData, postData, putData } from './http'

export interface ReturnWarehouse { id: number; name: string; type?: string; campus: string }
export interface ReturnCandidate {
  id: number; code: string; batchNo: string; productCode: string; productName: string
  availableQuantity: number; packageQuantity?: number
}
export interface ReturnRecord {
  returnNo: string; kind: 'department' | 'supplier'; status: string; reason: string; quantity: number
  sourceWarehouse: string; destinationWarehouse?: string; supplierName?: string; createTime: string; confirmTime?: string
}
export type ReturnSelection = 'batch' | 'trace' | 'package'
export type ReturnRequest = {
  kind: 'department' | 'supplier'; sourceWarehouseId: number; destinationWarehouseId?: number
  supplierId?: number; selectionType: ReturnSelection; reason: string; items: Array<{ id: number; quantity: number }>
  requestKey: string
}

export const fetchStockReturnOptions = () => getData<{
  warehouses: ReturnWarehouse[]; centers: ReturnWarehouse[]; suppliers: Array<{ id: number; name: string }>
}>('/stock-returns/options')
export const fetchStockReturnCandidates = (params: Record<string, string | number | undefined>) =>
  getData<ReturnCandidate[]>('/stock-returns/candidates', { params })
export const fetchStockReturns = () => getData<ReturnRecord[]>('/stock-returns')
export const fetchWarehouseReturnQueue = () => getData<ReturnRecord[]>('/stock-returns/picking-queue')
export const submitStockReturn = (request: ReturnRequest) =>
  postData<{ returnNo: string; status: string }>('/stock-returns', request, { freeTextFields: ['reason'] })
export const confirmWarehouseReturn = (no: string) =>
  putData<{ returnNo: string; status: string }>(`/stock-returns/${encodeURIComponent(no)}/warehouse-confirm`)
export const cancelWarehouseReturn = (no: string) =>
  putData<{ returnNo: string; status: string }>(`/stock-returns/${encodeURIComponent(no)}/cancel`)
