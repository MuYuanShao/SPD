import { getData, postData, putData, type PageResult } from './http'

export interface ClosureOptions {
  departments: Array<{ deptCode: string; deptName: string }>
  warehouses: Array<{ warehouseName: string; warehouseType?: string }>
  products: Array<{ productCode: string; productName: string; specModel: string; unit: string; purchasePrice: number }>
  balances: Array<Record<string, unknown>>
}

/**
 * 获取业务闭环概览数据
 * @returns 业务概览汇总及事件列表
 */
export async function fetchClosureOverview() {
  return getData<{ summary: Record<string, number>; events: Record<string, unknown>[] }>(
    '/operational-closure/overview'
  )
}

/**
 * 获取业务闭环操作选项（部门、库房、商品、余额）
 * @returns 操作选项
 */
export async function fetchClosureOptions() {
  return getData<ClosureOptions>('/operational-closure/options')
}

/**
 * 获取指定类型的业务闭环列表
 * @param type - 列表类型
 * @returns 业务数据列表
 */
export async function fetchClosureList(type: string, params: Record<string, string> = {}) {
  return getData<PageResult<Record<string, unknown>>>(`/operational-closure/lists/${type}`, { params })
}

/** 获取独立的拣配记录台账，沿用拣配配送只读权限。 */
export async function fetchPickingRecords(params: Record<string, string> = {}) {
  return fetchClosureList('delivery', params)
}

export async function fetchPickingRequisitions() {
  return getData<{ rows: Record<string, unknown>[] }>('/operational-closure/picking/requisitions')
}

export async function fetchPickingPackageLabels(params: Record<string, string> = {}) {
  return getData<{ rows: Record<string, unknown>[] }>('/operational-closure/picking/package-labels', { params })
}

export interface PackageLabelDetail {
  labelNo: string
  status: string
  packageQuantity: number
  printCount: number
  productCode: string
  productName: string
  specModel: string
  unit: string
  templateCode: string
  templateName: string
  warehouseName: string
  warehouseType: string
  createTime: string
  sources: Array<{ batchId: number; systemBatchNo?: string; productionBatchNo?: string; expireDate?: string; sourceQty: number; unitPrice: number }>
  bindings: Array<{ deliveryNo: string; requisitionNo?: string; packageQuantity: number; createTime: string }>
  events: Array<{ eventNo: string; eventType: string; statusBefore?: string; statusAfter?: string; qtyChange: number; remark?: string; createTime: string }>
}

export async function fetchPickingPackageLabelDetail(labelNo: string) {
  return getData<PackageLabelDetail>(
    `/operational-closure/picking/package-labels/${encodeURIComponent(labelNo)}`
  )
}

/** 拣配唯一码/UDI 货源（配对申请明细类型） */
export async function fetchPickingUniqueCodes(params: Record<string, string> = {}) {
  return getData<{ rows: Array<Record<string, unknown>> }>('/operational-closure/picking/unique-codes', { params })
}

/** 拣配散货货源（一级库无货位可用余额，按批次） */
export async function fetchPickingLooseStock(params: Record<string, string> = {}) {
  return getData<{ rows: Array<Record<string, unknown>> }>('/operational-closure/picking/loose-stock', { params })
}

/** 散货拣配确认 */
export async function confirmLoosePicking(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/picking/confirm-loose', payload)
}

/**
 * 生成缺货提醒数据
 * @param payload - 生成参数
 * @returns 生成结果
 */
export async function generateShortage(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/shortage/generate', payload)
}

export async function smartReplenishmentAnalysis(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/shortage/smart-analysis', payload)
}

/**
 * 创建申领单
 * @param payload - 申领信息
 * @returns 创建结果
 */
export async function createRequisition(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/requisitions', payload)
}

export async function processRequisition(requisitionNo: string, action: 'approve' | 'reject') {
  return putData<Record<string, unknown>>(
    `/operational-closure/requisitions/${encodeURIComponent(requisitionNo)}/action`,
    { action }
  )
}

export async function fetchRequisitionItems(requisitionNo: string) {
  return getData<{ rows: Record<string, unknown>[]; total: number; requisitionNo: string }>(
    `/operational-closure/requisitions/${encodeURIComponent(requisitionNo)}/items`
  )
}

/**
 * 创建配送单
 * @param payload - 配送信息
 * @returns 创建结果
 */
export async function createDelivery(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/deliveries', payload)
}

export async function confirmPicking(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/picking/confirm', payload)
}

/**
 * 签收配送单
 * @param deliveryNo - 配送单编号
 * @returns 签收结果
 */
export async function signDelivery(deliveryNo: string) {
  return putData<Record<string, unknown>>(`/operational-closure/deliveries/${deliveryNo}/sign`)
}

/**
 * 创建消耗记录
 * @param payload - 消耗信息
 * @returns 创建结果
 */
export async function createConsumption(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/consumptions', payload)
}

/** 科室消耗：按定数包码 / UDI / 唯一码定位商品 */
export async function resolveConsumptionProduct(queryCode: string) {
  return getData<Record<string, unknown>>('/operational-closure/consumptions/resolve', {
    params: { queryCode }
  })
}

/** 召回隔离：所选商品在该库房有可用库存的批次列表 */
export async function fetchRecallBatches(productCode: string, warehouseName: string) {
  return getData<{ rows: Array<Record<string, unknown>> }>('/operational-closure/recalls/batches', {
    params: { productCode, warehouseName }
  })
}

export async function fetchRecallInventory(params: Record<string, string>) {
  return getData<{ rows: Array<Record<string, unknown>> }>('/operational-closure/recalls/inventory', { params })
}

/**
 * 冲销消耗记录
 * @param consumptionNo - 消耗编号
 * @returns 冲销结果
 */
export async function reverseConsumption(consumptionNo: string) {
  return putData<Record<string, unknown>>(
    `/operational-closure/consumptions/${consumptionNo}/reverse`
  )
}

export async function confirmSettlement(settlementNo: string) {
  return putData<Record<string, unknown>>(
    `/operational-closure/settlements/${settlementNo}/confirm`
  )
}

/**
 * 上传 PDA 离线数据
 * @param payload - 离线数据
 * @returns 上传结果
 */
export async function uploadPdaOffline(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/pda/offline-upload', payload)
}

/**
 * 创建冷链异常记录
 * @param payload - 异常信息
 * @returns 创建结果
 */
export async function createColdChainException(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>(
    '/operational-closure/cold-chain/exceptions',
    payload
  )
}

/**
 * 创建召回记录
 * @param payload - 召回信息
 * @returns 创建结果
 */
export async function createRecall(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/recalls', payload)
}

/**
 * 创建高值耗材计费记录
 * @param payload - 计费信息
 * @returns 创建结果
 */
export async function createHighValueCharge(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/high-value/charges', payload)
}
export async function bindHighValuePatient(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/high-value/patient-bindings', payload)
}

export async function receiveHighValueBillingCallback(payload: Record<string, unknown>) {
  return postData<Record<string, unknown>>('/operational-closure/high-value/billing-callback', payload)
}
