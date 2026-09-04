import { getData, postData, putData, type PageResult } from './http'

export interface InventoryBalanceRow {
  balanceId: number
  warehouseName: string
  deptName?: string
  productCode: string
  productName: string
  specModel: string
  registrationNo?: string
  unitPrice: number
  unit?: string
  qty: number
  amount?: number
  manufacturerName?: string
  supplierName?: string
  systemBatchNo: string
  productionBatchNo?: string
  expireDate?: string
  lockedQty?: number
  inTransitQty?: number
  isolatedQty?: number
  ownershipType?: string
  settlementMode?: string
  updateTime?: string
}
/**
 * 分页查询库存余额列表
 * @param params - 查询参数
 * @returns 库存余额列表及汇总信息
 */
export async function fetchInventoryBalances(params: Record<string, string>) {
  return getData<PageResult<InventoryBalanceRow>>(
    '/inventory/balances',
    { params }
  )
}

/**
 * 定数包库存查询
 */
export async function fetchQuotaPackageStock(params: Record<string, string>) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/quota-package-stock', { params })
}

/**
 * 唯一码库存查询
 */
export async function fetchUniqueCodeStock(params: Record<string, string>) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/unique-code-stock', { params })
}

export interface InventoryEventSummary {
  inboundQty: number
  outboundQty: number
  netQty: number
  movementAmount: number
  valuationChange: number
}

export interface InventoryEventRow {
  eventNo: string
  eventType: string
  transactionTypeCode: string
  transactionTypeName: string
  eventCategory: 'quantity' | 'valuation'
  deptName?: string
  warehouseCode?: string
  warehouseName: string
  productCode: string
  productName: string
  specModel?: string
  registrationNo?: string
  batchNo?: string
  productionBatchNo?: string
  unitPrice?: number
  unit?: string
  qtyChange: number
  amount?: number
  qtyAfter?: number
  oldUnitPrice?: number
  newUnitPrice?: number
  affectedQty?: number
  valueChange?: number
  manufacturerName?: string
  supplierName?: string
  traceCount: number
  sourceBizType?: string
  sourceBizId?: number
  sourceBizNo?: string
  snapshotOrigin: 'captured' | 'legacy_backfill'
  remark?: string
  eventTime: string
}

export interface InventoryEventTraceCode {
  traceCodeId: number
  traceType: string
  linkedQuantity: number
  traceScope?: string
  uniqueCode?: string
  udiCode?: string
  packageLabelNo?: string
  currentStatus?: string
}

export interface InventoryEventDetail extends InventoryEventRow {
  traceCodes: InventoryEventTraceCode[]
}

export interface InventoryTransactionTypeOption {
  code: string
  label: string
  category: 'quantity' | 'valuation'
}

export interface InventoryEventQuery {
  page: number
  size: number
  startTime?: string
  endTime?: string
  transactionTypeCode?: string
  warehouseName?: string
  productName?: string
  deptName?: string
  productCode?: string
  batchNo?: string
  productionBatchNo?: string
  manufacturerName?: string
  supplierName?: string
  sourceBizNo?: string
}
/**
 * 查询库存交易流水记录
 * @param params - 查询参数
 * @returns 库存交易流水列表
 */
export async function fetchInventoryEvents(params: InventoryEventQuery) {
  return getData<PageResult<InventoryEventRow> & { summary?: InventoryEventSummary }>('/inventory/events', { params })
}

export async function fetchInventoryEventDetail(eventNo: string) {
  return getData<InventoryEventDetail>(`/inventory/events/${encodeURIComponent(eventNo)}`)
}

export async function fetchInventoryTransactionTypes() {
  return getData<{ rows: InventoryTransactionTypeOption[] }>('/inventory/events/options/transaction-types')
}

/**
 * 查询库存批次信息
 * @param params - 查询参数
 * @returns 库存批次列表
 */
export async function fetchInventoryBatches(params: Record<string, string>) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/batches', { params })
}

/**
 * 创建盘点记录
 * @param payload - 盘点信息（库房、批次、实际数量、原因）
 * @returns 盘点编号及差异数量
 */
export async function createStocktaking(payload: { warehouseName: string; systemBatchNo: string; actualQty: number; reason: string }) {
  return postData<{ stocktakingNo: string; diffQty: number }>('/inventory/stocktaking', payload)
}

/**
 * 审批盘点记录
 * @param stocktakingNo - 盘点编号
 * @returns 审批后的盘点状态
 */
export async function approveStocktaking(stocktakingNo: string) {
  return putData<{ stocktakingNo: string; status: string }>(
    `/inventory/stocktaking/${stocktakingNo}/approve`
  )
}

/**
 * 获取盘点记录列表
 * @returns 盘点记录列表
 */
export async function fetchStocktakingList(params: Record<string, string> = {}) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/stocktaking', { params })
}

export interface StocktakingSheetItem {
  itemId: number
  productCode: string
  productName: string
  specModel: string
  manufacturerName: string
  unit: string
  systemQty: number
  actualQty: number | null
  diffQty: number | null
}

/**
 * 新增盘点表：按所选商品范围（高值/可收费/不可收费/定数包）生成盘点明细。
 */
export async function createStocktakingSheet(payload: {
  warehouseName: string
  deptName?: string
  scopes: string[]
}) {
  return postData<{ stocktakingNo: string; rowCount: number }>('/inventory/stocktaking/sheets', payload)
}

export async function previewStocktakingSheet(payload: {
  warehouseName: string
  deptName?: string
  scopes: string[]
}) {
  return postData<{ rows: StocktakingSheetItem[] }>('/inventory/stocktaking/sheets/preview', payload)
}

/**
 * 盘点表明细（商品、库存数量、盘点数量；差异 = 库存 - 盘点）。
 */
export async function fetchStocktakingItems(stocktakingNo: string) {
  return getData<{ rows: StocktakingSheetItem[] }>(`/inventory/stocktaking/${stocktakingNo}/items`)
}

/**
 * 保存盘点数量（写入实盘数量并计算差异）。
 */
export async function updateStocktakingItems(stocktakingNo: string, items: Array<{ itemId: number; actualQty: number }>) {
  return putData<{ stocktakingNo: string; updatedRows: number }>(
    `/inventory/stocktaking/${stocktakingNo}/items`,
    { items }
  )
}

/**
 * 创建批量调价申请
 * @param payload - 调价信息（批次、新单价、原因）
 * @returns 调价编号及影响数量
 */
export async function createBatchPriceAdjustment(payload: { systemBatchNo: string; newUnitPrice: number; reason: string }) {
  return postData<{ adjustmentNo: string; affectedQty: number }>(
    '/inventory/batch-price-adjustments',
    payload
  )
}

/**
 * 审批批量调价申请
 * @param adjustmentNo - 调价编号
 * @returns 审批后的调价状态
 */
export async function approveBatchPriceAdjustment(adjustmentNo: string) {
  return putData<{ adjustmentNo: string; status: string }>(
    `/inventory/batch-price-adjustments/${adjustmentNo}/approve`
  )
}

/**
 * 获取批量调价记录列表
 * @returns 调价记录列表
 */
export async function fetchBatchPriceAdjustments(params: Record<string, string> = {}) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/batch-price-adjustments', { params })
}
