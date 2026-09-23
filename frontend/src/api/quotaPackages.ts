import { getData, getPage, postData, putData } from './http'

export interface QuotaTemplateRow {
  templateId: number
  templateCode: string
  templateName: string
  deptName?: string
  productCode: string
  productName: string
  specModel: string
  manufacturerName: string
  supplierName: string
  quantity: number
  unit: string
  status: string
  updateTime: string
  versionNo: number
  currentVersion: number
}

export interface QuotaSafetyRow {
  safetyId: number
  deptCode: string
  deptName: string
  productCode: string
  productName: string
  templateCode: string
  templateId?: number
  templateName: string
  minQty: number
  maxQty: number
  status: string
  updateTime: string
}

export interface PackingTaskRow {
  taskNo: string
  status: string
  templateCode: string
  templateName: string
  warehouseName: string
  productCode: string
  productName: string
  packageCount: number
  packageQuantity: number
  plannedLooseQty: number
  reservedLooseQty: number
  reservationSummary?: string
  remark?: string
  createTime: string
  confirmTime?: string
  cancelTime?: string
}

export interface PackageLabelRow {
  labelNo: string
  status: string
  templateCode: string
  templateName: string
  warehouseName: string
  productCode: string
  productName: string
  packageQuantity: number
  printCount: number
  createTime: string
  sourceBatches?: string
}

export interface DepartmentRequisitionCatalogRow {
  productId: number
  sourceWarehouseId: number
  productCode: string
  productName: string
  specModel: string
  manufacturerName: string
  supplierName: string
  baseUnit: string
  purchaseUnit: string
  conversionRate: number
  unitPrice: number
  quotaManaged: number
  highValue: number
  centralized: number
  chargeable: number
  looseAvailableQty: number
  packageAvailableQty: number
  uniqueCodeAvailableQty: number
  templateCode: string
  templateName: string
  packageQuantity: number | null
  packageUnit: string
  defaultMode: 'loose' | 'quota_package' | 'high_value'
  allowedModes: Array<'loose' | 'quota_package' | 'high_value'>
  requisitionStatus: string
}

/**
 * 分页查询定数包模板列表
 * @param params - 查询参数
 * @returns 定数包模板列表
 */
export async function fetchQuotaTemplates(params: Record<string, string>) {
  return getPage<QuotaTemplateRow>('/quota-packages/templates', { params })
}

/**
 * 查询启用了定数管理的商品目录（用于定数包模板选择商品）
 * @param query - 查询参数（productCode、productName 等）
 * @returns 已启用定数管理的商品列表
 */
export async function fetchQuotaManagedProducts(query: Record<string, string> = {}) {
  return getData<{ rows: Record<string, unknown>[]; total: number }>('/master-data/hospital-products', {
    params: { page: '1', size: '10', ...query, isQuotaManaged: '是' }
  })
}

/**
 * 保存定数包模板（新增或更新）
 * @param payload - 模板信息
 * @returns 保存的模板编码
 */
export async function saveQuotaTemplate(payload: {
  templateCode?: string
  templateName?: string
  deptName?: string
  productCode: string
  quantity: number
  unit?: string
}) {
  return postData<{ templateCode: string }>('/quota-packages/templates', payload)
}

/**
 * 禁用定数包模板
 * @param templateCode - 模板编码
 * @returns 禁用后的状态
 */
export async function disableQuotaTemplate(templateCode: string) {
  return putData<{ templateCode: string; status: string }>(
    `/quota-packages/templates/${templateCode}/disable`
  )
}

/**
 * 启用定数包模板
 * @param templateCode - 模板编码
 * @returns 启用后的状态
 */
export async function enableQuotaTemplate(templateCode: string) {
  return putData<{ templateCode: string; status: string }>(
    `/quota-packages/templates/${templateCode}/enable`
  )
}

/**
 * 分页查询定数包安全库存列表
 * @param params - 查询参数
 * @returns 安全库存列表
 */
export async function fetchQuotaSafety(params: Record<string, string>) {
  return getPage<QuotaSafetyRow>('/quota-packages/safety', { params })
}

/**
 * 保存定数包安全库存设置
 * @param payload - 安全库存信息
 * @returns 保存结果
 */
export async function saveQuotaSafety(payload: {
  deptCode?: string
  deptName: string
  templateId?: number
  templateCode?: string
  productCode: string
  minQty: number
  maxQty: number
}) {
  return postData<{ productCode: string; deptName: string }>('/quota-packages/safety', payload)
}

/**
 * 按验收单号查询可分配的散货库存
 */
export async function fetchReceivingLooseStock(receivingNo: string) {
  return getData<Array<Record<string, unknown>>>('/quota-packages/receiving-loose-stock', {
    params: { receivingNo }
  })
}

/**
 * 打包任务的验收单分配明细
 */
export async function fetchPackingTaskAllocations(taskNo: string) {
  return getData<Array<Record<string, unknown>>>(`/quota-packages/packing-tasks/${taskNo}/allocations`)
}

/**
 * 按验收单号分配散货库存到打包任务（quantity 为空表示全部打包分配）
 */
export async function allocatePackingTaskFromReceiving(
  taskNo: string,
  payload: { receivingNo: string; quantity?: number | null }
) {
  return postData<Record<string, unknown>>(
    `/quota-packages/packing-tasks/${taskNo}/allocate-from-receiving`,
    payload
  )
}

/**
 * 获取打包作业相关选项（库房、待打包候选列表）
 * @returns 打包选项
 */
export async function fetchPackingOptions() {
  return getData<{
    warehouses: Array<{ warehouseName: string; warehouseType: string }>
    candidates: Array<Record<string, unknown>>
  }>('/quota-packages/packing-options')
}

/**
 * 查询科室申领目录（科室可申领的商品列表）
 * @param params - 查询参数
 * @returns 科室申领目录列表
 */
export async function fetchDepartmentRequisitionCatalog(params: Record<string, string>) {
  return getPage<DepartmentRequisitionCatalogRow>('/quota-packages/requisition-catalog', { params })
}

/**
 * 获取打包任务列表
 * @returns 打包任务列表
 */
export async function fetchPackingTasks(params: Record<string, string> = {}) {
  return getPage<PackingTaskRow>(
    '/quota-packages/packing-tasks',
    { params }
  )
}

/**
 * 创建打包任务
 * @param payload - 任务信息（模板、库房、包数等）
 * @returns 创建的任务编号及计划散件数量
 */
export async function createPackingTask(payload: {
  templateCode: string
  warehouseName: string
  packageCount: number
  remark?: string
  allowPartial?: boolean
  expectedPackableCount?: number
}) {
  return postData<{
    created: boolean
    requiresConfirmation: boolean
    taskNo?: string
    requestedPackageCount: number
    packageCount?: number
    packablePackageCount?: number
    availableLooseQty?: number
    shortagePackageCount?: number
    plannedLooseQty?: number
    reservedLooseQty?: number
  }>(
    '/quota-packages/packing-tasks',
    payload
  )
}

/**
 * 确认打包任务完成
 * @param taskNo - 任务编号
 * @returns 确认结果及生成的标签列表
 */
export async function confirmPackingTask(taskNo: string) {
  return putData<{ taskNo: string; labels: string[] }>(
    `/quota-packages/packing-tasks/${taskNo}/confirm`
  )
}

/**
 * 取消打包任务
 * @param taskNo - 任务编号
 * @param reason - 取消原因
 * @returns 取消后的任务状态
 */
export async function cancelPackingTask(taskNo: string, reason: string) {
  return putData<{ taskNo: string; status: string }>(
    `/quota-packages/packing-tasks/${taskNo}/cancel`,
    { reason }
  )
}

/**
 * 终止打包任务并将库存退回散货状态
 * @param taskNo - 任务编号
 * @param reason - 终止原因
 * @returns 终止后的任务状态和退回散货数量
 */
export async function terminatePackingTask(taskNo: string, reason: string) {
  return putData<{ taskNo: string; status: string; restoredLooseQty: number }>(
    `/quota-packages/packing-tasks/${taskNo}/terminate`,
    { reason }
  )
}

/**
 * 重新计算打包任务预留量
 * @param taskNo - 任务编号
 * @returns 重新计算后的预留数量
 */
export async function recalculatePackingTask(taskNo: string) {
  return putData<{ taskNo: string; reservedLooseQty: number }>(
    `/quota-packages/packing-tasks/${taskNo}/recalculate`
  )
}

/**
 * 查询打包任务的库存预留明细
 * @param taskNo - 任务编号
 * @returns 预留明细列表
 */
export async function fetchPackingTaskReservations(taskNo: string) {
  return getData<Array<Record<string, unknown>>>(
    `/quota-packages/packing-tasks/${taskNo}/reservations`
  )
}

/**
 * 分页查询定数包标签列表
 * @param params - 查询参数
 * @returns 标签列表
 */
export async function fetchPackageLabels(params: Record<string, string>) {
  return getPage<PackageLabelRow>('/quota-packages/labels', { params })
}

/**
 * 拆包（解绑定数包标签）
 * @param labelNo - 标签编号
 * @param reason - 拆包原因
 * @returns 拆包后的标签状态
 */
export async function unpackPackageLabel(labelNo: string, reason: string) {
  return putData<{ labelNo: string; status: string; restoredLooseQty: number }>(
    `/quota-packages/labels/${labelNo}/unpack`,
    { reason }
  )
}

/**
 * 打印定数包标签
 * @param labelNo - 标签编号
 * @returns 打印后的标签状态和打印次数
 */
export async function printPackageLabel(labelNo: string) {
  return putData<{ labelNo: string; status: string; printCount: number }>(
    `/quota-packages/labels/${labelNo}/print`
  )
}

/**
 * 查询定数包事件流水
 * @returns 事件列表
 */
export async function fetchPackageEvents(params: Record<string, string> = {}) {
  return getPage<Record<string, unknown>>(
    '/quota-packages/events',
    { params }
  )
}

export async function fetchPackableLooseStock(params: Record<string, string>) {
  return getData<{ rows: Record<string, unknown>[]; total: number; page: number; size: number }>(
    '/quota-packages/packable-loose-stock', { params })
}
