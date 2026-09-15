import { getData, getPage, http } from './http'

export type ReconciliationRiskCode =
  | 'ALL'
  | 'MISSING_CHARGE'
  | 'DUPLICATE_CHARGE'
  | 'SUSPECTED_SWAP'
  | 'CONSISTENT'

export interface SpdHisReconciliationRow {
  reconciliationDate: string
  productCode: string
  medicalInsuranceCode: string
  hisMedicalInsuranceCode: string
  productName: string
  specModel: string
  departmentName: string
  patientName: string
  patientNo: string
  spdConsumptionQuantity: number
  hisChargeQuantity: number
  differenceQuantity: number
  differenceAmount: number
  chargeTime: string
  differenceReason: string
  riskLevel: string
  riskCode: Exclude<ReconciliationRiskCode, 'ALL'>
}

export interface ReconciliationSummary {
  totalCount: number
  exceptionCount: number
  differenceAmount: number
  missingChargeCount: number
  duplicateChargeCount: number
  suspectedSwapCount: number
}

export interface ReconciliationQuery {
  dateFrom: string
  dateTo: string
  department?: string
  productKeyword?: string
  patientKeyword?: string
  riskType?: ReconciliationRiskCode
  onlyExceptions?: boolean
  page?: number
  size?: number
}

export function fetchSpdHisReconciliation(params: ReconciliationQuery) {
  return getPage<SpdHisReconciliationRow>('/report-center/spd-his-reconciliation', { params })
}

export async function exportSpdHisReconciliationExcel(params: ReconciliationQuery) {
  const response = await http.get<Blob>('/report-center/spd-his-reconciliation/export.xlsx', {
    params,
    responseType: 'blob',
  })
  return response.data
}

export function recordSpdHisPdfExport() {
  return getData<{ recorded: boolean; format: string }>(
    '/report-center/spd-his-reconciliation/export-audit',
    { params: { format: 'PDF' } },
  )
}

export interface BaseReportQuery {
  dateFrom: string
  dateTo: string
  page?: number
  size?: number
}

export interface SupplierDeliveryQuery extends BaseReportQuery {
  supplier?: string
  category?: string
  department?: string
  centralizedStatus?: 'ALL' | 'SELECTED' | 'NON_SELECTED'
}

export interface SupplierDeliveryRow {
  supplyDate: string
  supplierName: string
  manufacturerName: string
  productCode: string
  medicalInsuranceCode: string
  productName: string
  specModel: string
  registrationNo: string
  unitPrice: number
  supplyQuantity: number
  supplyAmount: number
  orderNo: string
  contractNo: string
  acceptanceDepartment: string
  acceptanceUser: string
  acceptanceTime: string
  centralizedStatus: string
}

export interface CentralizedProcurementQuery extends BaseReportQuery {
  batch?: string
  category?: string
  department?: string
}

export interface CentralizedProcurementRow {
  batchCode: string
  batchName: string
  productCode: string
  productName: string
  specModel: string
  selectedManufacturer: string
  selectedPrice: number
  annualTargetQuantity: number
  periodPurchaseQuantity: number
  cumulativePurchaseQuantity: number
  completionRate: number
  selectedFlag: string
  departmentConsumptionQuantity: number
  incompleteReason: string
}

export interface InventoryMovementQuery extends BaseReportQuery {
  warehouse?: string
  category?: string
  materialType?: 'ALL' | 'HIGH' | 'LOW' | 'REAGENT'
}

export interface InventoryMovementRow {
  productCode: string
  productName: string
  specModel: string
  manufacturerName: string
  distributorName: string
  openingQuantity: number
  inboundQuantity: number
  returnQuantity: number
  requisitionQuantity: number
  consumptionQuantity: number
  scrapQuantity: number
  closingQuantity: number
  unitPrice: number
  inventoryAmount: number
  warehouseName: string
  materialAttribute: string
}

export function fetchSupplierDeliveryLedger(params: SupplierDeliveryQuery) {
  return getPage<SupplierDeliveryRow>('/report-center/supplier-delivery-ledger', { params })
}

export function fetchCentralizedProcurementProgress(params: CentralizedProcurementQuery) {
  return getPage<CentralizedProcurementRow>('/report-center/centralized-procurement-progress', { params })
}

export function fetchInventoryMovementSummary(params: InventoryMovementQuery) {
  return getPage<InventoryMovementRow>('/report-center/inventory-movement-summary', { params })
}

async function exportReportExcel(path: string, params: BaseReportQuery) {
  const response = await http.get<Blob>(path, { params, responseType: 'blob' })
  return response.data
}

export function exportSupplierDeliveryLedgerExcel(params: SupplierDeliveryQuery) {
  return exportReportExcel('/report-center/supplier-delivery-ledger/export.xlsx', params)
}

export function exportCentralizedProcurementProgressExcel(params: CentralizedProcurementQuery) {
  return exportReportExcel('/report-center/centralized-procurement-progress/export.xlsx', params)
}

export function exportInventoryMovementSummaryExcel(params: InventoryMovementQuery) {
  return exportReportExcel('/report-center/inventory-movement-summary/export.xlsx', params)
}

export function recordRegulatoryPdfExport(reportCode: string) {
  return getData<{ recorded: boolean; format: string }>(`/report-center/${reportCode}/export-audit`, {
    params: { format: 'PDF' },
  })
}

export interface InventoryProductDetail {
  id: number
  name: string
  registrationNo: string | null
  manufacturerName: string | null
  distributorName: string | null
  expiry: string
  specification: string
  quantity: number
  location: string
}
export function fetchInventoryProductDetails(params: { page: number; size: number; keyword: string }, signal?: AbortSignal) {
  return getPage<InventoryProductDetail>('/report-center/inventory-products', { params, signal })
}
