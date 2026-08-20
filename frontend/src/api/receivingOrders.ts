import { getData, postData, putData, type PageResult } from './http'

export interface ReceivingOrderRow {
  receivingOrderId: number
  receivingNo: string
  purchaseOrderNo?: string
  supplierName: string
  warehouseName: string
  receivingStatus: string
  receiveTime?: string
  createTime: string
  itemCount: number
  receiveQuantity: number
  receiveAmount: number
}

export interface ReceivingItemPayload {
  productCode: string
  productionBatchNo: string
  productionDate: string
  expireDate: string
  quantity: number
  qualifiedQuantity: number
  unqualifiedQuantity: number
}

export interface ReceivingOrderPayload {
  purchaseOrderNo?: string
  supplierName?: string
  warehouseName: string
  remark?: string
  items: ReceivingItemPayload[]
}

export interface ReceivingOptionRow {
  orderNo?: string
  supplierName?: string
  warehouseName?: string
  productCode?: string
  productName?: string
  specModel?: string
  unit?: string
  purchasePrice?: number
}

export interface SupplierOption {
  supplierName: string
}

export interface ReceivingOrderDetail {
  order: ReceivingOrderRow
  items: Record<string, unknown>[]
  total: number
  page: number
  size: number
}

/**
 * 分页查询收货单列表
 * @param params - 查询参数
 * @returns 收货单列表及汇总信息
 */
export async function fetchReceivingOrders(params: Record<string, string>) {
  return getData<PageResult<ReceivingOrderRow>>(
    '/receiving-orders',
    { params }
  )
}

/**
 * 获取收货单详情
 * @param receivingNo - 收货单编号
 * @returns 收货单信息及收货明细
 */
export async function fetchReceivingOrderDetail(receivingNo: string, params?: { page?: number; size?: number; keyword?: string }) {
  return getData<ReceivingOrderDetail>(
    `/receiving-orders/${receivingNo}`,
    { params }
  )
}

/**
 * 创建收货单
 * @param payload - 收货单信息（含明细）
 * @returns 创建的收货单编号
 */
export async function createReceivingOrder(payload: ReceivingOrderPayload) {
  return postData<{ receivingNo: string }>('/receiving-orders', payload)
}

export async function updateReceivingOrder(receivingNo: string, payload: ReceivingOrderPayload) {
  return putData<{ receivingNo: string; status: string }>(
    `/receiving-orders/${receivingNo}`,
    payload
  )
}

/**
 * 执行收货单操作（提交/审核/驳回等）
 * @param receivingNo - 收货单编号
 * @param action - 操作类型
 * @param opinion - 审批意见
 * @returns 操作后的收货单状态
 */
export async function updateReceivingAction(receivingNo: string, action: string, opinion = '') {
  return putData<{ receivingNo: string; status: string }>(
    `/receiving-orders/${receivingNo}/action`,
    { action, opinion }
  )
}

/**
 * 获取收货相关选项（采购订单、库房、商品）
 * @returns 收货选项
 */
export async function fetchReceivingOptions() {
  return getData<{
    purchaseOrders: ReceivingOptionRow[]
    warehouses: ReceivingOptionRow[]
    products: ReceivingOptionRow[]
    suppliers: SupplierOption[]
  }>('/receiving-orders/options')
}

/**
 * 获取采购订单的可收货明细
 * @param orderNo - 采购订单编号
 * @returns 订单信息及可收货明细
 */
export async function fetchPurchaseOrderReceivingItems(orderNo: string) {
  return getData<{ order: Record<string, unknown>; items: Record<string, unknown>[] }>(
    `/receiving-orders/purchase-order/${orderNo}/items`
  )
}
