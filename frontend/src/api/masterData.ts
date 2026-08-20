import { deleteData, getData, postData, putData } from './http'

export interface MasterDataPage {
  title: string
  description: string
  columns: string[]
  rows: Record<string, unknown>[]
  total: number
  page: number
  size: number
}

export interface ColumnConfig {
  prop: string
  label: string
  visible: boolean
  order: number
  width?: number
  fixed?: 'left' | 'right'
}

export type MasterDataQuery = Record<string, string>

export interface ProductAttachment {
  fileName: string
  category: string
  status: string
  fileUrl: string
  validDate: string
}

export interface ProductDetail {
  productId: number
  productCode: string
  productName: string
  specModel: string
  brand: string
  categoryName: string
  manufacturerName: string
  supplierName: string
  unit: string
  purchasePrice: number | string
  retailPrice: number | string | null
  minPurchaseQty: number | string
  purchaseUnit: string
  conversionRate: number | string
  udiCode: string
  registrationNo: string
  registrationExpireDate: string
  productionLicenseNo: string
  businessLicenseNo: string
  volumeBased: boolean
  centralizedProcurement: boolean
  domestic: boolean
  contractCode: string
  firstCategory: string
  secondCategory: string
  thirdCategory: string
  chargeable: boolean
  tenderSubCode: string
  highValue: boolean
  coldChain: boolean
  quotaManaged: boolean
  storageCondition: string
  statusLabel: string
  attachments: ProductAttachment[]
}

export interface ProductCreatePayload {
  productCode: string
  productName: string
  specModel: string
  brand: string
  manufacturerName: string
  supplierName: string
  unit: string
  purchasePrice: number | string
  retailPrice: number | string | null
  minPurchaseQty: number | string
  purchaseUnit: string
  conversionRate: number | string
  udiCode: string
  registrationNo: string
  registrationExpireDate: string
  productionLicenseNo: string
  businessLicenseNo: string
  volumeBased: boolean
  centralizedProcurement: boolean
  domestic: boolean
  contractCode: string
  firstCategory: string
  secondCategory: string
  thirdCategory: string
  chargeable: boolean
  tenderSubCode: string
  highValue: boolean
  coldChain: boolean
  quotaManaged: boolean
  storageCondition: string
}
export interface PartnerOption {
  code: string
  name: string
  status: number
}

export interface ProductPartnerOptions {
  manufacturers: PartnerOption[]
  suppliers: PartnerOption[]
}


export interface ProductBatchUpdatePayload {
  productCodes: string[]
  volumeBased: string
  domestic: string
  purchasePrice: number | string | null
  unit: string
  registrationNo: string
  contractCode: string
  firstCategory: string
  secondCategory: string
  thirdCategory: string
  chargeable: string
}

export interface ProductStatusUpdatePayload {
  productCodes: string[]
  status: number
}

export interface ProductSubmitPayload {
  productCodes: string[]
}

export interface SupplierPayload {
  supplierCode: string
  supplierName: string
  creditCode: string
  supplierType: string
  grade: string
  contactName: string
  contactPhone: string
  email: string
  address: string
}

export interface SupplierStatusUpdatePayload {
  supplierCodes: string[]
  status: number
}

export interface ManufacturerPayload {
  manufacturerCode: string
  manufacturerName: string
  creditCode: string
  licenseNo: string
  contactName: string
  contactPhone: string
  address: string
}

export interface ManufacturerStatusUpdatePayload {
  manufacturerCodes: string[]
  status: number
}

export interface DepartmentPayload {
  deptCode: string
  deptName: string
  financeDeptCode: string
  financeDeptName: string
  campusName: string
  address: string
  managerName: string
  phone: string
  sortOrder: number | string
  status: number
}

export interface CampusPayload {
  campusCode: string
  campusName: string
  address: string
  managerName: string
  phone: string
  sortOrder: number | string
  status: number
}

export interface CampusCodesPayload {
  campusCodes: string[]
}

export interface CampusOption {
  code: string
  name: string
}

export interface DepartmentCodesPayload {
  deptCodes: string[]
}

export interface WarehousePayload {
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  campusName: string
  deptName: string
  participateStats: boolean
  statsCategories: string
  status: number
  productCodes: string[]
}

export interface WarehouseProductOption {
  productCode: string
  productName: string
  specModel: string
  unit: string
}

export interface WarehouseCodesPayload {
  warehouseCodes: string[]
}

export interface DepartmentWarehouseRelation {
  code: string
  name: string
  type: string
  campus: string
  status: string
  relatedDepartment: string
  selected: boolean | number
}

export interface DepartmentWarehouseRelationPayload {
  warehouseCodes: string[]
}

export interface WarehouseLocation {
  locationId: number
  locationCode: string
  locationType: string
  capacityLimit: number | string | null
  productCode: string | null
  productName: string | null
  statusLabel: string
  status: number
  updateTime: string
}

export interface WarehouseLocationPayload {
  locationCode: string
  locationType: string
  capacityLimit: number | string | null
  productCode: string
  status: number
}

export interface DepartmentWarehouseCatalogPayload {
  deptName: string
  warehouseName: string
  productCode: string
  status: number
}

export interface DepartmentWarehouseCatalogBatchPayload {
  deptName: string
  warehouseName: string
  productCodes: string[]
  status: number
}

export interface DepartmentWarehouseCatalogBatchResult {
  createdRows: number
  updatedRows: number
  restoredRows: number
  totalRows: number
}

export interface DepartmentWarehouseCatalogIdsPayload {
  catalogIds: number[]
}

export interface DepartmentWarehouseCatalogStatusPayload extends DepartmentWarehouseCatalogIdsPayload {
  status: number
}

const endpointMap: Record<string, string> = {
  'hospital-product-catalog': '/master-data/hospital-products',
  'supplier-management': '/master-data/suppliers',
  'manufacturer-management': '/master-data/manufacturers',
  'campus-management': '/master-data/campuses',
  'department-management': '/master-data/departments',
  'department-warehouse-catalog': '/master-data/department-warehouse-catalogs',
  'warehouse-location-management': '/master-data/warehouses'
}

export function isMasterDataCode(code: string) {
  return Boolean(endpointMap[code])
}

/**
 * 分页查询主数据列表（商品目录、供应商、厂商、科室、仓库）
 * @param code - 主数据类型编码
 * @param query - 查询参数
 * @returns 主数据分页数据
 */
export async function fetchMasterDataPage(code: string, query: MasterDataQuery = {}) {
  const endpoint = endpointMap[code]
  if (!endpoint) {
    throw new Error(`未配置主数据接口: ${code}`)
  }

  return getData<MasterDataPage>(endpoint, { params: query })
}

/**
 * 获取医院商品目录详情
 * @param productCode - 商品编码
 * @returns 商品详情数据
 */
export async function fetchHospitalProductDetail(productCode: string) {
  return getData<ProductDetail>(
    `/master-data/hospital-products/${encodeURIComponent(productCode)}`
  )
}

export async function fetchHospitalProductPartnerOptions() {
  return getData<ProductPartnerOptions>('/master-data/hospital-products/partner-options')
}

/**
 * 新增医院商品目录
 * @param payload - 商品创建参数
 * @returns 新增商品编码和申请单号
 */
export async function createHospitalProduct(payload: ProductCreatePayload) {
  return postData<{ productCode: string; applicationNo: string }>(
    '/master-data/hospital-products',
    payload
  )
}

/**
 * 更新医院商品目录
 * @param productCode - 商品编码
 * @param payload - 商品更新参数
 * @returns 更新结果
 */
export async function updateHospitalProduct(productCode: string, payload: ProductCreatePayload) {
  return putData<{ productCode: string; applicationNo: string }>(
    `/master-data/hospital-products/${encodeURIComponent(productCode)}`,
    payload
  )
}

/**
 * 批量编辑医院商品
 * @param payload - 批量更新参数（含商品编码列表和要更新的字段）
 * @returns 更新行数
 */
export async function batchUpdateHospitalProducts(payload: ProductBatchUpdatePayload) {
  return putData<{ updatedRows: number }>(
    '/master-data/hospital-products/batch-edit',
    payload
  )
}

/**
 * 批量更新商品状态（启用/禁用）
 * @param payload - 状态更新参数（含商品编码列表和状态值）
 * @returns 更新行数
 */
export async function updateHospitalProductStatus(payload: ProductStatusUpdatePayload) {
  return putData<{ updatedRows: number }>('/master-data/hospital-products/status', payload)
}

/**
 * 提交商品审核
 * @param payload - 提交参数（含商品编码列表）
 * @returns 提交行数
 */
export async function submitHospitalProducts(payload: ProductSubmitPayload) {
  return postData<{ submittedRows: number }>(
    '/master-data/hospital-products/submit',
    payload
  )
}

/**
 * 导出医院商品目录
 * @param query - 导出筛选条件
 * @returns 商品数据列表
 */
export async function exportHospitalProducts(query: MasterDataQuery = {}) {
  return getData<Record<string, unknown>[]>('/master-data/hospital-products/export', {
    params: query
  })
}

/**
 * 新增供应商
 * @param payload - 供应商信息
 * @returns 新增供应商编码
 */
export async function createSupplier(payload: SupplierPayload) {
  return postData<{ supplierCode: string }>('/master-data/suppliers', payload)
}

/**
 * 更新供应商信息
 * @param supplierCode - 供应商编码
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateSupplier(supplierCode: string, payload: SupplierPayload) {
  return putData<{ supplierCode: string; updatedRows: number }>(
    `/master-data/suppliers/${encodeURIComponent(supplierCode)}`,
    payload
  )
}

/**
 * 批量更新供应商状态（启用/禁用）
 * @param payload - 状态更新参数
 * @returns 更新行数
 */
export async function updateSupplierStatus(payload: SupplierStatusUpdatePayload) {
  return putData<{ updatedRows: number }>('/master-data/suppliers/status', payload)
}

/**
 * 导出供应商数据
 * @param query - 导出筛选条件
 * @returns 供应商数据列表
 */
export async function exportSuppliers(query: MasterDataQuery = {}) {
  return getData<Record<string, unknown>[]>('/master-data/suppliers/export', {
    params: query
  })
}

/**
 * 导入供应商数据
 * @param file - Excel 文件
 * @returns 导入行数
 */
export async function importSuppliers(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number }>('/master-data/suppliers/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 新增厂商
 * @param payload - 厂商信息
 * @returns 新增厂商编码
 */
export async function createManufacturer(payload: ManufacturerPayload) {
  return postData<{ manufacturerCode: string }>('/master-data/manufacturers', payload)
}

/**
 * 更新厂商信息
 * @param manufacturerCode - 厂商编码
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateManufacturer(manufacturerCode: string, payload: ManufacturerPayload) {
  return putData<{ manufacturerCode: string; updatedRows: number }>(
    `/master-data/manufacturers/${encodeURIComponent(manufacturerCode)}`,
    payload
  )
}

/**
 * 批量更新厂商状态（启用/禁用）
 * @param payload - 状态更新参数
 * @returns 更新行数
 */
export async function updateManufacturerStatus(payload: ManufacturerStatusUpdatePayload) {
  return putData<{ updatedRows: number }>('/master-data/manufacturers/status', payload)
}

/**
 * 导出厂商数据
 * @param query - 导出筛选条件
 * @returns 厂商数据列表
 */
export async function exportManufacturers(query: MasterDataQuery = {}) {
  return getData<Record<string, unknown>[]>('/master-data/manufacturers/export', {
    params: query
  })
}

/**
 * 导入厂商数据
 * @param file - Excel 文件
 * @returns 导入行数
 */
export async function importManufacturers(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number }>(
    '/master-data/manufacturers/import',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' }
    }
  )
}

/**
 * 新增科室
 * @param payload - 科室信息
 * @returns 新增科室编码
 */
export async function fetchCampusOptions() {
  return getData<CampusOption[]>('/master-data/campuses/options')
}

export async function createCampus(payload: CampusPayload) {
  return postData<{ campusCode: string }>('/master-data/campuses', payload)
}

export async function updateCampus(campusCode: string, payload: CampusPayload) {
  return putData<{ campusCode: string; updatedRows: number }>(
    `/master-data/campuses/${encodeURIComponent(campusCode)}`,
    payload
  )
}

export async function deleteCampuses(payload: CampusCodesPayload) {
  return putData<{ deletedRows: number }>('/master-data/campuses/delete', payload)
}

export async function createDepartment(payload: DepartmentPayload) {
  return postData<{ deptCode: string }>('/master-data/departments', payload)
}

/**
 * 更新科室信息
 * @param deptCode - 科室编码
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateDepartment(deptCode: string, payload: DepartmentPayload) {
  return putData<{ deptCode: string; updatedRows: number }>(
    `/master-data/departments/${encodeURIComponent(deptCode)}`,
    payload
  )
}

/**
 * 批量删除科室
 * @param payload - 待删除科室编码列表
 * @returns 删除行数
 */
export async function deleteDepartments(payload: DepartmentCodesPayload) {
  return putData<{ deletedRows: number }>('/master-data/departments/delete', payload)
}

/**
 * 导出科室数据
 * @param query - 导出筛选条件
 * @returns 科室数据列表
 */
export async function exportDepartments(query: MasterDataQuery = {}) {
  return getData<Record<string, unknown>[]>('/master-data/departments/export', {
    params: query
  })
}

/**
 * 导入科室数据
 * @param file - Excel 文件
 * @returns 导入行数
 */
export async function fetchWarehouseProductOptions() {
  return getData<WarehouseProductOption[]>('/master-data/warehouses/product-options')
}

export async function fetchWarehouseProducts(warehouseCode: string) {
  return getData<WarehouseProductOption[]>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}/products`
  )
}

export async function importDepartments(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number }>(
    '/master-data/departments/import',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' }
    }
  )
}

/**
 * 新增库房
 * @param payload - 库房信息
 * @returns 新增库房编码
 */
export async function createWarehouse(payload: WarehousePayload) {
  return postData<{ warehouseCode: string; productCount: number }>('/master-data/warehouses', payload)
}

export async function fetchDepartmentWarehouses(deptCode: string) {
  return getData<DepartmentWarehouseRelation[]>(
    `/master-data/departments/${encodeURIComponent(deptCode)}/warehouses`
  )
}

export async function updateDepartmentWarehouses(
  deptCode: string,
  payload: DepartmentWarehouseRelationPayload
) {
  return putData<{ updatedRows: number; warehouseCount: number }>(
    `/master-data/departments/${encodeURIComponent(deptCode)}/warehouses`,
    payload
  )
}

/**
 * 更新库房信息
 * @param warehouseCode - 库房编码
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateWarehouse(warehouseCode: string, payload: WarehousePayload) {
  return putData<{ warehouseCode: string; updatedRows: number; productCount: number }>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}`,
    payload
  )
}

/**
 * 批量删除库房
 * @param payload - 待删除库房编码列表
 * @returns 删除行数
 */
export async function deleteWarehouses(payload: WarehouseCodesPayload) {
  return putData<{ deletedRows: number }>('/master-data/warehouses/delete', payload)
}

/**
 * 导出库房数据
 * @param query - 导出筛选条件
 * @returns 库房数据列表
 */
export async function exportWarehouses(query: MasterDataQuery = {}) {
  return getData<Record<string, unknown>[]>('/master-data/warehouses/export', {
    params: query
  })
}

/**
 * 导入库房数据
 * @param file - Excel 文件
 * @returns 导入行数
 */
export async function importWarehouses(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ importedRows: number; importedLocations: number }>(
    '/master-data/warehouses/import',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' }
    }
  )
}

export async function fetchWarehouseLocations(warehouseCode: string) {
  return getData<WarehouseLocation[]>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}/locations`
  )
}

export async function createWarehouseLocation(warehouseCode: string, payload: WarehouseLocationPayload) {
  return postData<{ locationCode: string }>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}/locations`,
    payload
  )
}

export async function updateWarehouseLocation(
  warehouseCode: string,
  locationId: number,
  payload: WarehouseLocationPayload
) {
  return putData<{ updatedRows: number; locationId: number }>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}/locations/${locationId}`,
    payload
  )
}

export async function deleteWarehouseLocation(warehouseCode: string, locationId: number) {
  return deleteData<{ deletedRows: number }>(
    `/master-data/warehouses/${encodeURIComponent(warehouseCode)}/locations/${locationId}`
  )
}

export async function batchMaintainDepartmentWarehouseCatalogs(
  payload: DepartmentWarehouseCatalogBatchPayload
) {
  return postData<DepartmentWarehouseCatalogBatchResult>(
    '/master-data/department-warehouse-catalogs/batch',
    payload
  )
}

export async function createDepartmentWarehouseCatalog(payload: DepartmentWarehouseCatalogPayload) {
  return postData<{ catalogId: number }>('/master-data/department-warehouse-catalogs', payload)
}

export async function updateDepartmentWarehouseCatalog(
  catalogId: number,
  payload: DepartmentWarehouseCatalogPayload
) {
  return putData<{ catalogId: number; updatedRows: number }>(
    `/master-data/department-warehouse-catalogs/${catalogId}`,
    payload
  )
}

export async function deleteDepartmentWarehouseCatalogs(payload: DepartmentWarehouseCatalogIdsPayload) {
  return putData<{ deletedRows: number }>('/master-data/department-warehouse-catalogs/delete', payload)
}

export async function updateDepartmentWarehouseCatalogStatus(payload: DepartmentWarehouseCatalogStatusPayload) {
  return putData<{ updatedRows: number }>('/master-data/department-warehouse-catalogs/status', payload)
}
