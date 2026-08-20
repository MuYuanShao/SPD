import { getData, postData, putData, type PageResult } from './http'

export interface InventoryBalanceRow {
  balanceId: number
  warehouseName: string
  productCode: string
  productName: string
  specModel: string
  systemBatchNo: string
  productionBatchNo?: string
  expireDate?: string
  batchUnitPrice: number
  availableQty: number
  lockedQty: number
  inTransitQty: number
  isolatedQty: number
  ownershipType: string
  settlementMode: string
  updateTime: string
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
 * 查询库存交易流水记录
 * @param params - 查询参数
 * @returns 库存交易流水列表
 */
export async function fetchInventoryEvents(params: Record<string, string>) {
  return getData<PageResult<Record<string, unknown>>>('/inventory/events', { params })
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
