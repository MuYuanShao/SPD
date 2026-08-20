import { getData, getPage, postData, putData } from './http'

export interface PurchaseOrderRow {
  orderId: number
  orderNo: string
  supplierName: string
  orderSource?: string
  purchaseType?: string
  orderStatus: string
  totalAmount: number
  expectedArrivalDate?: string
  createTime: string
  sendTime?: string
  closeTime?: string
  closeReason?: string
  itemCount: number
  orderQuantity: number
  receivedQuantity: number
  remainingQuantity: number
}

export interface PurchaseDemandRow {
  demandId: number
  demandNo: string
  demandSource: string
  demandStatus: string
  urgentLevel: string
  deptName?: string
  productCode: string
  productName: string
  specModel?: string
  quantity: number
  approvedQuantity: number
  suggestedPurchaseQty: number
  unitPrice?: number
  registrationNo?: string
  manufacturerName?: string
  createTime: string
}

export interface PurchasePlanRow {
  planId: number
  planNo: string
  planStatus: string
  supplierName: string
  productCode: string
  productName: string
  plannedQuantity: number
  convertedOrderNo?: string
  remark?: string
  createTime: string
}

export interface PurchaseProductOption {
  productCode: string
  productName: string
  specModel: string
  unit: string
  purchasePrice: number
}

export interface PurchaseSupplierOption {
  supplierName: string
}

export interface PurchaseSmartReplenishmentRow {
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  productCode: string
  productName: string
  specModel?: string
  unit?: string
  purchasePrice?: number
  minPurchaseQty?: number
  supplierName?: string
  currentQty: number
  issue5: number
  issue15: number
  issue30: number
  issue45: number
  issue60: number
  selectedIssueQty: number
  formulaReplenishQty: number
  recommendedQty: number
  formulaText: string
}

export interface PurchaseSmartReplenishmentResult {
  analysisId: number
  analysisNo: string
  selectedPeriodDays: number
  periodDays: number[]
  totalFormulaQty: number
  totalRecommendedQty: number
  rows: PurchaseSmartReplenishmentRow[]
}

export interface PurchaseOrderItemPayload {
  productCode: string
  quantity: number
  unit: string
  estimatedUnitPrice: number
}

export interface PurchaseOrderPayload {
  supplierName: string
  orderSource: string
  expectedArrivalDate: string
  items: PurchaseOrderItemPayload[]
}

/**
 * 分页查询采购订单列表
 * @param params - 查询参数
 * @returns 采购订单列表及汇总信息
 */
export async function fetchPurchaseOrders(params: Record<string, string>) {
  return getPage<PurchaseOrderRow>('/purchase-orders', { params })
}

/**
 * 获取采购订单详情
 * @param orderNo - 订单编号
 * @returns 订单详情、明细及跟踪记录
 */
export async function fetchPurchaseOrderDetail(orderNo: string) {
  return getData<{ order: PurchaseOrderRow; items: Record<string, unknown>[]; tracking: Record<string, unknown>[] }>(
    `/purchase-orders/${orderNo}`
  )
}

/**
 * 创建采购订单
 * @param payload - 采购订单信息
 * @returns 创建的订单编号和总金额
 */
export async function createPurchaseOrder(payload: PurchaseOrderPayload) {
  return postData<{ orderNo: string; totalAmount: number }>('/purchase-orders', payload)
}

/**
 * 执行采购订单操作（提交/审核/驳回等）
 * @param orderNo - 订单编号
 * @param action - 操作类型
 * @param opinion - 审批意见
 * @returns 操作后的订单状态
 */
export async function updatePurchaseOrderAction(orderNo: string, action: string, opinion = '') {
  return putData<{ orderNo: string; status: string }>(
    `/purchase-orders/${orderNo}/action`,
    { action, opinion }
  )
}

/**
 * 获取采购订单相关选项（供应商、商品）
 * @returns 供应商和商品选项
 */
export async function fetchPurchaseOptions() {
  return getData<{ suppliers: PurchaseSupplierOption[]; products: PurchaseProductOption[] }>('/purchase-orders/options')
}

export async function fetchPurchaseSmartReplenishmentAnalysis(periodDays = 30) {
  return getData<PurchaseSmartReplenishmentResult>('/purchase-orders/smart-replenishment-analysis', {
    params: { periodDays: String(periodDays) }
  })
}

/**
 * 分页查询采购需求列表
 * @param params - 查询参数
 * @returns 采购需求列表
 */
export async function fetchPurchaseDemands(params: Record<string, string>) {
  return getPage<PurchaseDemandRow>('/purchase-orders/demands', { params })
}

/**
 * 创建采购需求
 * @param payload - 需求信息
 * @returns 需求编号及建议采购量
 */
export async function createPurchaseDemand(payload: Record<string, unknown>) {
  return postData<{ demandNo: string; suggestedPurchaseQty: number }>(
    '/purchase-orders/demands',
    payload
  )
}

/**
 * 执行采购需求操作（提交/审核/驳回等）
 * @param demandNo - 需求编号
 * @param action - 操作类型
 * @param opinion - 审批意见
 * @returns 操作后的需求状态
 */
export async function updatePurchaseDemandAction(demandNo: string, action: string, opinion = '') {
  return putData<{ demandNo: string; status: string }>(
    `/purchase-orders/demands/${demandNo}/action`,
    { action, opinion }
  )
}

/**
 * 分页查询采购计划列表
 * @param params - 查询参数
 * @returns 采购计划列表
 */
export async function fetchPurchasePlans(params: Record<string, string>) {
  return getPage<PurchasePlanRow>('/purchase-orders/plans', { params })
}

/**
 * 从采购需求生成采购计划
 * @param payload - 需求到计划的转换参数
 * @returns 创建的采购计划数量
 */
export async function createPurchasePlansFromDemands(payload: Record<string, unknown>) {
  return postData<{ createdPlans: number }>(
    '/purchase-orders/plans/from-demands',
    payload
  )
}

/**
 * 执行采购计划操作（提交/审核/转订单等）
 * @param planNo - 计划编号
 * @param action - 操作类型
 * @param opinion - 审批意见
 * @returns 操作后的计划状态及关联订单号
 */
export async function updatePurchasePlanAction(planNo: string, action: string, opinion = '') {
  return putData<{ planNo: string; status: string; orderNo?: string }>(
    `/purchase-orders/plans/${planNo}/action`,
    { action, opinion }
  )
}
