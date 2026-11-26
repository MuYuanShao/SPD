<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Ban,
  Building2,
  CheckSquare,
  ChevronDown,
  Columns3,
  Download,
  Edit3,
  Eye,
  Factory,
  FileDown,
  FileUp,
  Flag,
  Layers,
  Link2,
  ListPlus,
  PackageCheck,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  Truck,
  Upload,
  X
} from '@lucide/vue'
import { useHospitalProductManagement } from '../../composables/useHospitalProductManagement'
import { useMasterDataExchange } from '../../composables/useMasterDataExchange'
import { useMasterDataWorkbench } from '../../composables/useMasterDataWorkbench'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { formatDateTime } from '../../utils/dateTime'
import { useOrganizationMasterData } from '../../composables/useOrganizationMasterData'
import { useSpdConfirm } from '../../composables/useSpdConfirm'
import { usePartnerMasterData } from '../../composables/usePartnerMasterData'
import { useNotify } from '../../composables/useNotify'
import BatchProductDialog from '../../components/master-data/BatchProductDialog.vue'
import CampusDialog from '../../components/master-data/CampusDialog.vue'
import DepartmentDialog from '../../components/master-data/DepartmentDialog.vue'
import DepartmentWarehouseCatalogDialog from '../../components/master-data/DepartmentWarehouseCatalogDialog.vue'
import DepartmentWarehouseCatalogBatchDialog from '../../components/master-data/DepartmentWarehouseCatalogBatchDialog.vue'
import DepartmentWarehouseDialog from '../../components/master-data/DepartmentWarehouseDialog.vue'
import ManufacturerDialog from '../../components/master-data/ManufacturerDialog.vue'
import ProductDialog from '../../components/master-data/ProductDialog.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import MdStatus from '../../components/master-data/MdStatus.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import MdStatsRow from '../../components/master-data/MdStatsRow.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import SpdConfirmDialog from '../../components/common/ConfirmDialog.vue'
import SupplierDialog from '../../components/master-data/SupplierDialog.vue'
import WarehouseDialog from '../../components/master-data/WarehouseDialog.vue'
import WarehouseLocationDialog from '../../components/master-data/WarehouseLocationDialog.vue'
import '../../styles/master-data/supplier-management.css'
import '../../styles/master-data/manufacturer-management.css'
import '../../styles/master-data/campus-management.css'
import '../../styles/master-data/department-management.css'
import '../../styles/master-data/department-warehouse-catalog.css'
import { useAuthStore } from '../../stores/auth'
import { formatBusinessText, formatStatusText } from '../../utils/chineseDisplay'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import PaginationControls from '../../components/common/PaginationControls.vue'

const authStore = useAuthStore()

// 通用确认弹窗（注入到下面的 useOrganizationMasterData）
const { ask, ConfirmHost: SpdConfirmHost } = useSpdConfirm()

const {
  page,
  loading,
  error,
  actionError,
  actionMessage,
  selectedCodes,
  hospitalColumnConfigs,
  hospitalCurrentPage,
  hospitalPageSize,
  catalogCurrentPage,
  catalogPageSize,
  catalogPageJumpInput,
  hospitalTotal,
  hospitalPageTotal,
  catalogTotal,
  catalogPageTotal,
  catalogPageSizeOptions,
  columnSettingsOpen,
  importInput,
  manufacturerImportInput,
  departmentImportInput,
  warehouseImportInput,
  supplierDialogOpen,
  supplierDialogMode,
  productDialogOpen,
  productDialogMode,
  batchProductDialogOpen,
  manufacturerDialogOpen,
  manufacturerDialogMode,
  campusDialogOpen,
  campusDialogMode,
  departmentDialogOpen,
  departmentDialogMode,
  departmentWarehouseCatalogDialogOpen,
  departmentWarehouseCatalogDialogMode,
  warehouseDialogOpen,
  warehouseDialogMode,
  hospitalQuery,
  supplierQuery,
  manufacturerQuery,
  campusQuery,
  departmentQuery,
  departmentWarehouseCatalogQuery,
  warehouseQuery,
  code,
  isHospitalCatalog,
  isPartnerCombined,
  partnerTab,
  switchPartnerTab,
  partnerCurrentPage,
  partnerPageSize,
  partnerTotal,
  partnerPageSizeOptions,
  changePartnerPage,
  changePartnerPageSize,
  isSupplierManagement,
  isManufacturerManagement,
  isCampusManagement,
  isDepartmentManagement,
  isDepartmentWarehouseCatalog,
  isWarehouseManagement,
  listCurrentPage,
  listPageSize,
  listTotal,
  listTotalPages,
  changeListPage,
  changeListPageSize,
  selectableRows,
  rowKeys,
  queryParams,
  allVisibleSelected,
  selectedSupplier,
  selectedManufacturer,
  selectedCampus,
  selectedDepartment,
  selectedDepartmentWarehouseCatalog,
  selectedWarehouse,
  sortedColumns,
  submitHospitalQuery,
  loadHospitalPage,
  changeHospitalPageSize,
  submitDepartmentWarehouseCatalogQuery,
  loadDepartmentWarehouseCatalogPage,
  changeDepartmentWarehouseCatalogPageSize,
  submitDepartmentWarehouseCatalogPageJump,
  loadPage,
  resetHospitalQuery,
  resetSupplierQuery,
  resetManufacturerQuery,
  resetCampusQuery,
  resetDepartmentQuery,
  resetDepartmentWarehouseCatalogQuery,
  resetWarehouseQuery,
  toggleAllRows,
  clearActionState,
  hospitalStats,
  partnerStats
} = useMasterDataWorkbench()

const {
  downloadSupplierTemplate,
  exportSupplierRows,
  triggerSupplierImport,
  handleSupplierImport,
  downloadManufacturerTemplate,
  exportManufacturerRows,
  triggerManufacturerImport,
  handleManufacturerImport,
  downloadDepartmentTemplate,
  exportDepartmentRows,
  triggerDepartmentImport,
  handleDepartmentImport,
  downloadWarehouseTemplate,
  exportWarehouseRows,
  triggerWarehouseImport,
  handleWarehouseImport
} = useMasterDataExchange({
  queryParams,
  importInput,
  manufacturerImportInput,
  departmentImportInput,
  warehouseImportInput,
  actionError,
  actionMessage,
  clearActionState,
  loadPage
})

const {
  supplierForm,
  manufacturerForm,
  openCreateSupplier,
  openEditSupplier,
  saveSupplier,
  disableSuppliers,
  openCreateManufacturer,
  openEditManufacturer,
  saveManufacturer,
  disableManufacturers,
  confirmStatusChange: confirmPartnerStatusChange,
  cancelStatusChange: cancelPartnerStatusChange,
  openStatusConfirm: openPartnerStatusConfirm,
  statusConfirmTarget: partnerStatusConfirmTarget,
  statusConfirmOpen: partnerStatusConfirmOpen,
  statusConfirmLoading: partnerStatusConfirmLoading,
  statusConfirmKind: partnerStatusConfirmKind,
  statusConfirmCodes: partnerStatusConfirmCodes
} = usePartnerMasterData({
  selectedCodes,
  selectedSupplier,
  selectedManufacturer,
  supplierDialogOpen,
  supplierDialogMode,
  manufacturerDialogOpen,
  manufacturerDialogMode,
  actionError,
  actionMessage,
  clearActionState,
  loadPage
})

const partnerStatusAction = computed(() => partnerStatusConfirmTarget.value === 1 ? '启用' : '停用')
const partnerStatusKindLabel = computed(() => partnerStatusConfirmKind.value === 'supplier' ? '供应商' : '厂家')

const {
  campusForm,
  campusOptions,
  departmentForm,
  warehouseForm,
  warehouseProductOptions,
  warehouseProductOptionsLoading,
  departmentOptions,
  departmentWarehouseDialogOpen,
  departmentWarehouseLoading,
  departmentWarehouseSaving,
  departmentWarehouseRows,
  departmentWarehouseSelectedCodes,
  activeDepartmentForWarehouses,
  departmentWarehouseCatalogForm,
  departmentWarehouseCatalogBatchForm,
  departmentWarehouseCatalogBatchDialogOpen,
  departmentWarehouseCatalogBatchSaving,
  departmentWarehouseCatalogBatchWarehouses,
  departmentWarehouseCatalogOptions,
  departmentWarehouseCatalogWarehouses,
  warehouseLocationDialogOpen,
  warehouseLocationLoading,
  warehouseLocationSaving,
  warehouseLocationRows,
  warehouseLocationForm,
  warehouseLocationEditingId,
  activeWarehouseForLocations,
  openCreateCampus,
  openEditCampus,
  saveCampus,
  deleteSelectedCampuses,
  openCreateDepartment,
  openEditDepartment,
  saveDepartment,
  deleteSelectedDepartments,
  openDepartmentWarehouseDialog,
  closeDepartmentWarehouseDialog,
  loadDepartmentWarehouses,
  toggleDepartmentWarehouse,
  saveDepartmentWarehouses,
  loadDepartmentWarehouseCatalogWarehouses,
  loadDepartmentWarehouseCatalogBatchWarehouses,
  loadDepartmentWarehouseCatalogProducts,
  searchDepartmentWarehouseCatalogProducts,
  loadDepartmentWarehouseCatalogBatchProducts,
  openCreateDepartmentWarehouseCatalog,
  openBatchDepartmentWarehouseCatalog,
  saveBatchDepartmentWarehouseCatalog,
  openEditDepartmentWarehouseCatalog,
  saveDepartmentWarehouseCatalog,
  deleteSelectedDepartmentWarehouseCatalogs,
  updateSelectedDepartmentWarehouseCatalogStatus,
  openCreateWarehouse,
  openEditWarehouse,
  saveWarehouse,
  deleteSelectedWarehouses,
  openWarehouseLocationDialog,
  closeWarehouseLocationDialog,
  loadWarehouseLocations,
  startCreateWarehouseLocation,
  startEditWarehouseLocation,
  saveWarehouseLocation,
  removeWarehouseLocation
} = useOrganizationMasterData({
  selectedCodes,
  selectedCampus,
  selectedDepartment,
  selectedDepartmentWarehouseCatalog,
  selectedWarehouse,
  campusDialogOpen,
  campusDialogMode,
  departmentDialogOpen,
  departmentDialogMode,
  departmentWarehouseCatalogDialogOpen,
  departmentWarehouseCatalogDialogMode,
  warehouseDialogOpen,
  warehouseDialogMode,
  actionError,
  actionMessage,
  clearActionState,
  loadPage,
  confirm: ask
})

const {
  productSaving,
  batchProductSaving,
  submittedApplicationNo,
  closeProductDialog,
  closeBatchProductDialog,
  productForm,
  manufacturerOptions,
  supplierOptions,
  batchProductForm,
  openCreateProduct,
  openEditProduct,
  saveProduct,
  batchEditSelectedProducts,
  saveBatchProductEdit,
  disableProducts,
  enableProducts,
  productStatusAction,
  productStatusError,
  confirmProductStatusChange,
  cancelProductStatusChange,
  productStatusConfirmOpen,
  productStatusConfirmLoading,
  productStatusConfirmCount,
  exportHospitalCatalog
} = useHospitalProductManagement({
  selectedCodes,
  productDialogOpen,
  productDialogMode,
  batchProductDialogOpen,
  actionError,
  actionMessage,
  queryParams,
  clearActionState,
  loadPage
})

// ===== 操作反馈统一走 Element Plus ElMessage 顶部弹层 =====
// 不再用 <p class="md-message"> 内联在页面流里，避免提示条挤压表单与表格布局。
const notify = useNotify()

watch(actionMessage, (text) => {
  if (!text) return
  notify.success(text)
})

watch(actionError, (text) => {
  if (!text) return
  notify.error(text)
})

// ===== 医院商品目录精简版状态（对齐定数包模板维护新版：去冗余 + 抽屉展示次要字段） =====
const hospitalShowMore = ref(false)
const hospitalDetailCode = ref('')
const hospitalDrawerOpen = computed(() => hospitalDetailCode.value !== '')
const hospitalDetailRow = computed(() => {
  const rows = (page.value?.rows ?? []) as Array<Record<string, unknown>>
  return rows.find((row) => String(row.code) === hospitalDetailCode.value) ?? null
})
function openHospitalDetail(code: string) {
  hospitalDetailCode.value = code
}
function closeHospitalDetail() {
  hospitalDetailCode.value = ''
}
// ===== 医院目录单表：列宽拖拽 + 列设置（localStorage 持久化） =====
// 与科室库房目录等其他统一列表页共用 useListColumns，保证交互与持久化行为一致。
const HOSPITAL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'product', label: '商品 / 编码', width: 240, visible: true, weight: 8, minWidth: 160, maxWidth: 420 },
  { key: 'spec', label: '规格型号', width: 160, visible: true, weight: 5, minWidth: 110, maxWidth: 280 },
  { key: 'vendor', label: '厂家 / 供应商', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'price', label: '单价', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]
const hospitalCols = useListColumns({
  storageKey: 'spd-hospital-catalog-cols-v1',
  defaults: HOSPITAL_COL_DEFAULTS,
  fitMode: 'hybrid',
  // 预留勾选列宽度：<el-table-column type="selection" width="48" />
  // 该列不在列配置里，但会占用表格宽度，必须先扣掉，
  // 否则数据列按完整容器宽度分配，把表格顶出横向滚动条。
  reservedWidth: 48
})
const hospitalColBinding = (key: string) => hospitalCols.columnBinding(key)
const hospitalTableRef = hospitalCols.tableRef
const hospitalColSettingsOpen = hospitalCols.settingsOpen
const visibleHospitalCols = hospitalCols.visibleColumns
const isHospitalColVisible = (key: string) => hospitalCols.isColumnVisible(key)
function onHospitalSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String((row as Record<string, unknown>).code))
}
function hospitalAttrTags(row: Record<string, unknown>) {
  const tags: Array<{ text: string; hot: boolean }> = []
  if (String(row.centralized) === '是') tags.push({ text: '集采', hot: true })
  if (String(row.domestic) === '是') tags.push({ text: '国产', hot: false })
  if (String(row.chargeable) === '是') tags.push({ text: '收费', hot: false })
  if (String(row.keyMonitored) === '是') tags.push({ text: '重点监控', hot: true })
  if (String(row.volumeBased) === '是') tags.push({ text: '带量', hot: false })
  return tags.slice(0, 3)
}

// ===== 统一列表：科室管理 / 科室库房目录（列宽拖拽 + 列设置 + 统一斑马纹表格） =====
function formatListDateTime(value: unknown) {
  return formatDateTime(value)
}

const DEPARTMENT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '科室编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'name', label: '科室名称', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'org', label: '组织归属', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'finance', label: '财务科室映射', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'manager', label: '负责人', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'address', label: '科室地址', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'warehouse', label: '关联库房', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'source', label: '原数据标识', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

/**
 * 科室库房目录列定义 —— 按实测内容排名得到的权重比例分配
 * ---------------------------------------------------------------------
 * 列宽不是拍脑袋定的，而是用 canvas 按真实字体量出每列内容的自然宽度，
 * 再据此排出名次、换算成权重，最后按权重比例铺满内容区。
 *
 *   排名  列              内容P90   权重   保底   基准   上限
 *    1   商品名称             789     9     104     171     560
    2   生产厂家             464     7     104     156     420
    3   规格型号             188     5     104     141     280
    4   科室 / 库房          156     3     119     141     175
    5   更新时间             148     3     104     126     175
    6   商品编码             132     2     104     119     150
    7   维护来源              56     2      56      71     105
    8   状态                28     2      56      71      92
 *
 * 分配机制（useListColumns.ts → effectiveWidths）：
 *   1. 先给每列发「保底宽度」，短列不会被压到放不下表头；
 *   2. 剩余空间按「权重」比例分配 —— 字多的列权重高，自然份额大；
 *   3. 某列超过上限时把超出部分回收，重新分给还有空间的列。
 *
 * 结果：表格默认**恰好铺满内容区**，右侧不留空白、也不出现横向滚动；
 * 同时「状态」「维护来源」这类字少的列只拿到与其内容匹配的宽度，
 * 不会像早期版本那样被撑成 139–241px 的空旷大块。
 *
 * 基准合计 996（+ 选择列 48 + 操作列 200 = 1244），
 * 恰好等于 1280px 内容区宽度。
 */
const CATALOG_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'scope', label: '科室 / 库房', width: 141, visible: true, weight: 3, minWidth: 119, maxWidth: 175 },
  { key: 'productCode', label: '商品编码', width: 119, visible: true, weight: 2, minWidth: 104, maxWidth: 150 },
  { key: 'productName', label: '商品名称', width: 171, visible: true, flexible: true, idealWidth: 300, weight: 9, minWidth: 104, maxWidth: 560 },
  { key: 'spec', label: '规格型号', width: 141, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'manufacturer', label: '生产厂家', width: 156, visible: true, flexible: true, idealWidth: 280, weight: 7, minWidth: 104, maxWidth: 420 },
  { key: 'source', label: '维护来源', width: 71, visible: true, weight: 2, minWidth: 56, maxWidth: 105 },
  { key: 'status', label: '状态', width: 71, visible: true, weight: 2, minWidth: 56, maxWidth: 92 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'updateTime', label: '更新时间', width: 126, visible: true, weight: 3, minWidth: 104, maxWidth: 175 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 260 }
]

const WAREHOUSE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '库房编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'name', label: '库房名称', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'type', label: '库房类型', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'campus', label: '所属院区', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'dept', label: '关联科室', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'participateStats', label: '统计', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'receivingEnabled', label: '允许收货', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'statsCategories', label: '统计分类', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'locationCount', label: '货位数量', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const CAMPUS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '院区编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'name', label: '院区名称', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'address', label: '院区地址', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'manager', label: '负责人', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'sortOrder', label: '排序', width: 80, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const PARTNER_TIME_COLUMNS = [
  { key: 'createTime', label: '创建时间', width: 190, visible: true },
  { key: 'updateTime', label: '修改时间', width: 190, visible: true }
]

const SUPPLIER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '供应商编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'name', label: '供应商名称', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'creditCode', label: '统一社会信用代码', width: 190, visible: true, weight: 5, minWidth: 150, maxWidth: 260 },
  { key: 'businessLicenseNo', label: '经营许可证号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'type', label: '类型', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'grade', label: '等级', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'contact', label: '联系人', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'email', label: '邮箱', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'address', label: '地址', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const MANUFACTURER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '厂家编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'name', label: '厂家名称', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'creditCode', label: '统一社会信用代码', width: 190, visible: true, weight: 5, minWidth: 150, maxWidth: 260 },
  { key: 'licenseNo', label: '生产许可证号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'contact', label: '联系人', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'address', label: '地址', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const departmentCols = useListColumns({
  storageKey: 'spd-department-cols-v1',
  defaults: DEPARTMENT_COL_DEFAULTS,
  fitMode: 'hybrid',
  // 预留勾选列宽度（<el-table-column type="selection" width="48" />）：
  // 该列不在列配置里，但会占用表格宽度，必须先扣掉，
  // 否则数据列按完整容器宽度分配，把表格顶出横向滚动条。
  reservedWidth: 48
})
const departmentColBinding = (key: string) => departmentCols.columnBinding(key)
const catalogCols = useListColumns({
  storageKey: 'spd-department-warehouse-catalog-cols-v4',
  defaults: CATALOG_COL_DEFAULTS,
  // 方案 C：混合策略 —— 字多的列（商品名称 / 规格型号 / 生产厂家）优先吸收富余且受各自上限约束，富余用完后剩余量才补给字少的列，字少的列不会被撑得过大。
  fitMode: 'hybrid',
  // 预留勾选列宽度（<el-table-column type="selection" width="48" />）：
  // 该列不在列配置里，但会占用表格宽度，必须先扣掉，
  // 否则数据列按完整容器宽度分配，把表格顶出横向滚动条。
  reservedWidth: 48
})
const warehouseCols = useListColumns({
  storageKey: 'spd-warehouse-location-cols-v1',
  defaults: WAREHOUSE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 48
})
const warehouseColBinding = (key: string) => warehouseCols.columnBinding(key)
const campusCols = useListColumns({
  storageKey: 'spd-campus-cols-v1',
  defaults: CAMPUS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 48
})
const campusColBinding = (key: string) => campusCols.columnBinding(key)
const supplierCols = useListColumns({
  storageKey: 'spd-supplier-cols-v1',
  defaults: SUPPLIER_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 48
})
const supplierColBinding = (key: string) => supplierCols.columnBinding(key)
const manufacturerCols = useListColumns({
  storageKey: 'spd-manufacturer-cols-v1',
  defaults: MANUFACTURER_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 48
})

const manufacturerColBinding = (key: string) => manufacturerCols.columnBinding(key)
const departmentTableRef = departmentCols.tableRef
const catalogTableRef = catalogCols.tableRef
const warehouseTableRef = warehouseCols.tableRef
const campusTableRef = campusCols.tableRef
const supplierTableRef = supplierCols.tableRef
const manufacturerTableRef = manufacturerCols.tableRef

const isDepartmentColVisible = (key: string) => departmentCols.isColumnVisible(key)
const departmentColWidth = (key: string) => departmentCols.columnWidth(key)

const isCatalogColVisible = (key: string) => catalogCols.isColumnVisible(key)
const catalogColWidth = (key: string) => catalogCols.columnWidth(key)
/** 目录列宽绑定：使用计算后的最终列宽（弹性列受上限约束，短字段不被挤压） */
const catalogColBinding = (key: string) => catalogCols.columnBinding(key)

/* ------------------------------------------------------------------
 * 科室库房目录表格：表头固定
 * ------------------------------------------------------------------
 * 需求：页面/内容区整体滚动时表头不固定（随内容滚走）；
 *      表格内部出现滚动条时，表头固定在表格容器顶部，方便确认当前列。
 *
 * 实现：给 el-table 设置 max-height，Element Plus 会把超出的行收进
 *      表格自身的滚动容器（.el-table__body-wrapper），表头留在容器顶部不动。
 *      高度按「内容区可视高度 - 表格上方占用高度 - 分页区预留」动态计算，
 *      用 ResizeObserver 跟随视口宽度、字号档位、内容区高度变化重算，
 *      避免硬编码像素在不同分辨率下出现大片留白或表格被压扁。
 * ------------------------------------------------------------------ */

/** 表格最大高度（px），由 measureCatalogTableMaxHeight 更新 */
const catalogTableMaxHeight = ref(0)

/** 分页区预留高度（px）：分页条 + 间距 */
const CATALOG_PAGINATION_RESERVE = 76
/** 最小可用高度（px）：低于此值时不再压缩，避免表格只剩一两行 */
const CATALOG_MIN_TABLE_HEIGHT = 260

let catalogTableResizeObserver: ResizeObserver | undefined

/**
 * 计算表格可用的最大高度。
 * 以内容区（main.spd-content）为基准，减去表格上方元素（标题、筛选区、工具栏）
 * 实际占用的高度与分页区预留高度。
 */
function measureCatalogTableMaxHeight() {
  const tableEl = catalogTableRef.value?.$el as HTMLElement | undefined
  if (!tableEl) return
  const contentEl = tableEl.closest('.spd-content') as HTMLElement | null
  const container = contentEl ?? (document.querySelector('.spd-content') as HTMLElement | null)
  if (!container) return

  // 内容区可视高度
  const containerClientH = container.clientHeight
  if (!containerClientH) return

  // 表格顶部相对内容区顶部的偏移（标题 + 筛选区 + 工具栏）
  const containerRect = container.getBoundingClientRect()
  const tableRect = tableEl.getBoundingClientRect()
  const offsetTop = Math.max(0, Math.round(tableRect.top - containerRect.top + container.scrollTop))

  const available = containerClientH - offsetTop - CATALOG_PAGINATION_RESERVE
  catalogTableMaxHeight.value = Math.max(CATALOG_MIN_TABLE_HEIGHT, available)
}

/** 字号档位变化会改变行高，进而改变表格高度，需要重算可用高度 */
function setupCatalogTableResizeObserver() {
  if (typeof ResizeObserver === 'undefined') return
  const contentEl = document.querySelector('.spd-content') as HTMLElement | null
  if (!contentEl) return
  catalogTableResizeObserver = new ResizeObserver(() => {
    measureCatalogTableMaxHeight()
  })
  catalogTableResizeObserver.observe(contentEl)
}

onMounted(() => {
  measureCatalogTableMaxHeight()
  setupCatalogTableResizeObserver()
  // 数据加载完成后行数变化，需要重算
  watch(
    () => page.value?.rows?.length,
    () => {
      // 等 DOM 更新后再测量
      requestAnimationFrame(measureCatalogTableMaxHeight)
    }
  )
})

onBeforeUnmount(() => {
  catalogTableResizeObserver?.disconnect()
  catalogTableResizeObserver = undefined
})

const isWarehouseColVisible = (key: string) => warehouseCols.isColumnVisible(key)
const warehouseColWidth = (key: string) => warehouseCols.columnWidth(key)

const isCampusColVisible = (key: string) => campusCols.isColumnVisible(key)
const campusColWidth = (key: string) => campusCols.columnWidth(key)

const isSupplierColVisible = (key: string) => supplierCols.isColumnVisible(key)
const supplierColWidth = (key: string) => supplierCols.columnWidth(key)

const isManufacturerColVisible = (key: string) => manufacturerCols.isColumnVisible(key)
const manufacturerColWidth = (key: string) => manufacturerCols.columnWidth(key)

/**
 * ===== 「操作」列统一固定规则 =====
 * ---------------------------------------------------------------------
 * 所有基础数据管理列表页共用同一套策略：
 * **表格出现横向滚动条时才固定操作列；没有滚动条时不固定。**
 *
 * 没有横向滚动时操作列本来就在视野里，固定它反而会让
 * Element Plus 多渲染一份 .el-table__fixed-right 副本（像是多了一列）；
 * 出现横向滚动时才固定，保证用户往右看数据时操作按钮始终可见。
 *
 * 判定依据是实际溢出，而不是列数 —— 列宽会随拖拽、窗口大小、
 * 主题与字号档位变化，用列数判断容易与实际不符。
 *
 * 每个页面有自己的表格，因此各用一个实例分别观察自己的滚动容器。
 */
const hospitalActionFix = useActionColumnFix()
const supplierActionFix = useActionColumnFix()
const manufacturerActionFix = useActionColumnFix()
const campusActionFix = useActionColumnFix()
const departmentActionFix = useActionColumnFix()
const catalogActionFix = useActionColumnFix()
const warehouseActionFix = useActionColumnFix()

/**
 * 绑定到 el-table-column 的 fixed 属性。
 *
 * 注意：这里必须摊平成顶层 computed。
 * setup 返回的普通对象不会被 Vue 模板自动解包，
 * 模板里写 hospitalActionFix.actionFixedBinding 拿到的是
 * ComputedRef 对象本身而不是值，Element Plus 会当成「不固定」。
 */
const hospitalActionFixed = computed(() => hospitalActionFix.actionFixedBinding.value)
const supplierActionFixed = computed(() => supplierActionFix.actionFixedBinding.value)
const manufacturerActionFixed = computed(() => manufacturerActionFix.actionFixedBinding.value)
const campusActionFixed = computed(() => campusActionFix.actionFixedBinding.value)
const departmentActionFixed = computed(() => departmentActionFix.actionFixedBinding.value)
const catalogActionFixed = computed(() => catalogActionFix.actionFixedBinding.value)
const warehouseActionFixed = computed(() => warehouseActionFix.actionFixedBinding.value)

/** 表格渲染完成后开始观察横向溢出 */
watch(
  [hospitalTableRef, supplierTableRef, manufacturerTableRef, campusTableRef, departmentTableRef, catalogTableRef, warehouseTableRef],
  () => {
    hospitalActionFix.observe(hospitalTableRef.value)
    supplierActionFix.observe(supplierTableRef.value)
    manufacturerActionFix.observe(manufacturerTableRef.value)
    campusActionFix.observe(campusTableRef.value)
    departmentActionFix.observe(departmentTableRef.value)
    catalogActionFix.observe(catalogTableRef.value)
    warehouseActionFix.observe(warehouseTableRef.value)
  },
  { flush: 'post' }
)

/**
 * 维护来源状态条配色：不同来源用不同颜色区分。
 * 后端可能返回原始枚举（manual / system / warehouse_binding），
 * 也可能返回已翻译中文（手工维护 / 系统生成），这里两种都覆盖。
 */
const CATALOG_SOURCE_CHIP_CLASS: Record<string, string> = {
  manual: 'is-primary',
  '手工维护': 'is-primary',
  system: 'is-info',
  '系统生成': 'is-info',
  warehouse_binding: 'is-warning',
  '库房绑定': 'is-warning',
  batch: 'is-neutral'
}

function catalogSourceChipClass(value: unknown) {
  const key = String(value ?? '').trim().toLowerCase()
  return CATALOG_SOURCE_CHIP_CLASS[key] ?? CATALOG_SOURCE_CHIP_CLASS[formatBusinessText(value).toLowerCase()] ?? 'is-neutral'
}

function onDepartmentSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

function onCatalogSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

function onWarehouseSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

function onCampusSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

function onSupplierSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

function onManufacturerSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

/** 状态条配色：启用为成功色，停用为危险色（库房 / 院区 / 科室共用） */
function statusChipClass(value: unknown) {
  return String(value ?? '').trim() === '启用' ? 'is-success' : 'is-danger'
}

/** 库房"统计 / 允许收货"开关样式：开启为成功色，关闭为中性色 */
function warehouseFlagChipClass(enabled: unknown) {
  return enabled ? 'is-success' : 'is-neutral'
}

/* ===== 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） ===== */
const hospitalColSettingsAnchorRef = ref<HTMLElement | null>(null)
const hospitalColSettingsPopRef = ref<HTMLElement | null>(null)
const hospitalColSettingsPop = useColSettingsPopover({
  open: hospitalCols.settingsOpen,
  anchorRef: hospitalColSettingsAnchorRef,
  popRef: hospitalColSettingsPopRef
})
const supplierColSettingsAnchorRef = ref<HTMLElement | null>(null)
const supplierColSettingsPopRef = ref<HTMLElement | null>(null)
const supplierColSettingsPop = useColSettingsPopover({
  open: supplierCols.settingsOpen,
  anchorRef: supplierColSettingsAnchorRef,
  popRef: supplierColSettingsPopRef
})
const manufacturerColSettingsAnchorRef = ref<HTMLElement | null>(null)
const manufacturerColSettingsPopRef = ref<HTMLElement | null>(null)
const manufacturerColSettingsPop = useColSettingsPopover({
  open: manufacturerCols.settingsOpen,
  anchorRef: manufacturerColSettingsAnchorRef,
  popRef: manufacturerColSettingsPopRef
})
const campusColSettingsAnchorRef = ref<HTMLElement | null>(null)
const campusColSettingsPopRef = ref<HTMLElement | null>(null)
const campusColSettingsPop = useColSettingsPopover({
  open: campusCols.settingsOpen,
  anchorRef: campusColSettingsAnchorRef,
  popRef: campusColSettingsPopRef
})
const departmentColSettingsAnchorRef = ref<HTMLElement | null>(null)
const departmentColSettingsPopRef = ref<HTMLElement | null>(null)
const departmentColSettingsPop = useColSettingsPopover({
  open: departmentCols.settingsOpen,
  anchorRef: departmentColSettingsAnchorRef,
  popRef: departmentColSettingsPopRef
})
const catalogColSettingsAnchorRef = ref<HTMLElement | null>(null)
const catalogColSettingsPopRef = ref<HTMLElement | null>(null)
const catalogColSettingsPop = useColSettingsPopover({
  open: catalogCols.settingsOpen,
  anchorRef: catalogColSettingsAnchorRef,
  popRef: catalogColSettingsPopRef
})
const warehouseColSettingsAnchorRef = ref<HTMLElement | null>(null)
const warehouseColSettingsPopRef = ref<HTMLElement | null>(null)
const warehouseColSettingsPop = useColSettingsPopover({
  open: warehouseCols.settingsOpen,
  anchorRef: warehouseColSettingsAnchorRef,
  popRef: warehouseColSettingsPopRef
})

</script>

<template>
  <section
    class="master-page spd-unified-list"
    :class="{
      'master-page--aligned': isHospitalCatalog || isSupplierManagement || isManufacturerManagement || isCampusManagement || isDepartmentManagement || isDepartmentWarehouseCatalog || isWarehouseManagement
    }"
  >
    <header v-if="isPartnerCombined" class="md-header md-header--partner">
      <div class="md-header-inner">
        <div class="md-header-text">
          <div class="md-header-crumbs">
            <strong>基础数据管理</strong>
          </div>
          <h2 class="md-header-title">供应商厂家管理</h2>
          <p class="md-header-sub">统一维护供应商与生产厂家档案，切换页签可分别查看供应商、厂家信息。</p>
        </div>
        <div class="md-header-actions">
          <button
            type="button"
            class="md-btn"
            :disabled="loading"
            aria-label="刷新"
            @click="loadPage"
          >
            <RefreshCw :size="16" :class="{ 'md-spin': loading }" />
            <span>刷新</span>
          </button>
        </div>
      </div>
    </header>

    <nav v-if="isPartnerCombined" class="md-tabs sys-master-tabs" aria-label="供应商厂家页签">
      <button
        type="button"
        :class="['md-tab', { active: partnerTab === 'supplier' }]"
        @click="switchPartnerTab('supplier')"
      >
        <Truck :size="14" aria-hidden="true" />
        供应商管理
      </button>
      <button
        type="button"
        :class="['md-tab', { active: partnerTab === 'manufacturer' }]"
        @click="switchPartnerTab('manufacturer')"
      >
        <Factory :size="14" aria-hidden="true" />
        厂家管理
      </button>
    </nav>

    <section v-if="isHospitalCatalog" class="md-partner-page spd-unified-list">
      <MdPageHeader
        title="医院商品目录"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="submitHospitalQuery">
        <div class="ulc-filter-fields">
          <label>
            <span>商品编码</span>
            <input
              v-model.trim="hospitalQuery.productCode"
              class="md-input"
              type="search"
              placeholder="模糊查询商品编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>商品名称</span>
            <input
              v-model.trim="hospitalQuery.productName"
              class="md-input"
              type="search"
              placeholder="模糊查询商品名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>厂家 / 供应商</span>
            <input
              v-model.trim="hospitalQuery.vendorKeyword"
              class="md-input"
              type="search"
              placeholder="厂家或供应商名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>状态</span>
            <el-select v-model="hospitalQuery.status" placeholder="全部状态" clearable aria-label="筛选商品状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
          <template v-if="hospitalShowMore">
            <label>
              <span>规格型号</span>
              <input
                v-model.trim="hospitalQuery.specModel"
                class="md-input"
                type="search"
                placeholder="规格或型号"
                autocomplete="off"
              />
            </label>
            <label>
              <span>供应商</span>
              <input
                v-model.trim="hospitalQuery.supplierName"
                class="md-input"
                type="search"
                placeholder="供应商名称"
                autocomplete="off"
              />
            </label>
            <label>
              <span>单价</span>
              <input
                v-model.trim="hospitalQuery.price"
                class="md-input"
                type="search"
                placeholder="单价"
                autocomplete="off"
              />
            </label>
            <label>
              <span>是否集采</span>
              <el-select v-model="hospitalQuery.isCentralized" placeholder="全部" clearable aria-label="筛选是否集采">
                <el-option label="全部" value="" />
                <el-option label="是" value="是" />
                <el-option label="否" value="否" />
              </el-select>
            </label>
            <label>
              <span>招采子编码</span>
              <input
                v-model.trim="hospitalQuery.tenderCode"
                class="md-input"
                type="search"
                placeholder="招采子编码"
                autocomplete="off"
              />
            </label>
          </template>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="hospitalShowMore = !hospitalShowMore">
            {{ hospitalShowMore ? '收起' : '更多' }}
            <ChevronDown :size="16" :class="{ rotated: hospitalShowMore }" />
          </button>
          <button type="button" class="md-btn" @click="resetHospitalQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section
      v-if="isHospitalCatalog"
      class="master-table-card master-table-card-flat spd-unified-list hospital-product-list-card"
    >
      <MdDataTableCard :loading="loading" :meta-count="`共 ${hospitalTotal} 个`">
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateProduct"
          >
            <Plus :size="16" />
            <span>新增</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1"
            @click="openEditProduct()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="!selectedCodes.length"
            @click="batchEditSelectedProducts"
          >
            <Edit3 :size="16" />
            <span>批量修改</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length"
            @click="disableProducts()"
          >
            <Ban :size="16" />
            <span>停用</span>
          </button>
          <button type="button" class="md-btn md-btn-primary" @click="exportHospitalCatalog">
            <Download :size="16" />
            <span>导出</span>
          </button>
          <div class="ulc-col-settings">
            <button
              ref="hospitalColSettingsAnchorRef"
              type="button"
              class="md-btn md-btn-primary"
              @click="hospitalColSettingsOpen = !hospitalColSettingsOpen"
            >
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: hospitalColSettingsOpen }" />
            </button>
            <div
              v-if="hospitalColSettingsOpen"
              ref="hospitalColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': hospitalColSettingsPop.align.value === 'right', 'is-align-up': hospitalColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': hospitalColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': hospitalColSettingsPop.maxHeight.value + 'px' }"
            >
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="hospitalCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in hospitalCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && visibleHospitalCols.length <= 1"
                    @change="hospitalCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="hospitalColSettingsOpen = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="hospitalTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page?.rows ?? []"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无医院目录数据"
            @selection-change="onHospitalSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => hospitalCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="product"
              v-if="isHospitalColVisible('product')"
              label="商品 / 编码"
              v-bind="hospitalColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品 / 编码</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 商品名称与编码各自超长时单独换行 -->
                  <span class="ulc-strong ulc-wrap-text">{{ (row as Record<string, unknown>).name ?? '-' }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ (row as Record<string, unknown>).code }}</span>
                  <span class="ulc-inline-tags">
                    <span v-for="tag in hospitalAttrTags(row as Record<string, unknown>)" :key="tag.text" class="ulc-tag" :class="{ 'is-hot': tag.hot }">{{ tag.text }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="spec"
              v-if="isHospitalColVisible('spec')"
              label="规格型号"
              prop="spec"
              v-bind="hospitalColBinding('spec')"
            >
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ (row as Record<string, unknown>).spec ?? '-' }}</span>
              </template>
              <template #header>
                <span class="ulc-th-text">规格型号</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="vendor"
              v-if="isHospitalColVisible('vendor')"
              label="厂家 / 供应商"
              v-bind="hospitalColBinding('vendor')"
            >
              <template #header>
                <span class="ulc-th-text">厂家 / 供应商</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 厂家与供应商是两个独立字段，各自超长时单独换行 -->
                  <span class="ulc-strong ulc-wrap-text">{{ (row as Record<string, unknown>).manufacturer ?? '-' }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub">{{ (row as Record<string, unknown>).supplier ?? '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="price"
              v-if="isHospitalColVisible('price')"
              label="单价"
              v-bind="hospitalColBinding('price')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">单价</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 单价与单位是一个整体：整体放得下就保持一行，整体超出列宽才折行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ (row as Record<string, unknown>).price ?? '-' }}</span>
                    <span class="ulc-sub">{{ String((row as Record<string, unknown>).unit ?? '') }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="status"
              v-if="isHospitalColVisible('status')"
              label="状态"
              v-bind="hospitalColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="String((row as Record<string, unknown>).status) === '启用' ? 'is-success' : 'is-danger'">
                  {{ (row as Record<string, unknown>).status ?? '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isHospitalColVisible('createTime')"
              label="创建时间"
              v-bind="hospitalColBinding('createTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>

              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime((row as Record<string, unknown>).createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isHospitalColVisible('updateTime')"
              label="修改时间"
              v-bind="hospitalColBinding('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>

              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime((row as Record<string, unknown>).updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isHospitalColVisible('actions')"

              label="操作"

              v-bind="hospitalColBinding('actions')"

              :fixed="hospitalActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button type="button" class="ulc-action is-outline" @click="openHospitalDetail(String((row as Record<string, unknown>).code))">详情</button>
                  <RouterLink class="ulc-action is-neutral" :to="{ name: 'hospital-product-detail', params: { productCode: String((row as Record<string, unknown>).code) } }">查看</RouterLink>
                  <button v-if="authStore.canWrite(code)" type="button" class="ulc-action" @click="openEditProduct(String((row as Record<string, unknown>).code), false)">修改</button>
                  <template v-if="authStore.canWrite(code)">
                    <button v-if="row.status === '停用'" type="button" class="ulc-action is-info" @click="enableProducts([String(row.code)])">启用</button>
                    <button v-else type="button" class="ulc-action is-danger" @click="disableProducts([String((row as Record<string, unknown>).code)])">停用</button>
                  </template>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无医院目录数据" hint="尝试调整筛选条件或新增商品" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="hospitalCurrentPage"
            :total-pages="hospitalPageTotal"
            :total="hospitalTotal"
            :size="hospitalPageSize"
            :loading="loading"
            @update:page="loadHospitalPage"
            @update:size="changeHospitalPageSize"
          />
        </template>
      </MdDataTableCard>

      <div v-if="hospitalDrawerOpen" class="catalog-mask" @click="closeHospitalDetail"></div>
      <aside v-if="hospitalDrawerOpen && hospitalDetailRow" class="catalog-drawer" role="dialog" aria-label="商品详情">
        <header class="drawer-head">
          <div>
            <h3>{{ String(hospitalDetailRow.name ?? '-') }}</h3>
            <p>{{ String(hospitalDetailRow.code ?? '-') }} · {{ String(hospitalDetailRow.spec ?? '-') }}</p>
          </div>
          <button class="drawer-close" aria-label="关闭" @click="closeHospitalDetail"><X :size="16" /></button>
        </header>
        <div class="drawer-body">
          <section class="d-section">
            <h5>基本信息</h5>
            <div class="d-grid">
              <div class="d-item"><span>商品编码</span><strong class="mono">{{ String(hospitalDetailRow.code ?? '-') }}</strong></div>
              <div class="d-item"><span>商品名称</span><strong>{{ String(hospitalDetailRow.name ?? '-') }}</strong></div>
              <div class="d-item"><span>规格型号</span><strong>{{ String(hospitalDetailRow.spec ?? '-') }}</strong></div>
              <div class="d-item"><span>单位 / 单价</span><strong>{{ String(hospitalDetailRow.unit ?? '-') }} · ¥ {{ String(hospitalDetailRow.price ?? '-') }}</strong></div>
              <div class="d-item"><span>厂家</span><strong>{{ String(hospitalDetailRow.manufacturer ?? '-') }}</strong></div>
              <div class="d-item"><span>供应商</span><strong>{{ String(hospitalDetailRow.supplier ?? '-') }}</strong></div>
              <div class="d-item"><span>状态</span><strong>{{ String(hospitalDetailRow.status ?? '-') }}</strong></div>
              <div class="d-item"><span>创建时间</span><strong>{{ formatListDateTime(hospitalDetailRow.createTime) }}</strong></div>
              <div class="d-item"><span>修改时间</span><strong>{{ formatListDateTime(hospitalDetailRow.updateTime) }}</strong></div>
              <div class="d-item"><span>品牌</span><strong>{{ String(hospitalDetailRow.brand ?? '-') }}</strong></div>
            </div>
          </section>
          <section class="d-section">
            <h5>分类与证照</h5>
            <div class="d-grid">
              <div class="d-item"><span>一级分类</span><strong>{{ String(hospitalDetailRow.firstCategory ?? '-') }}</strong></div>
              <div class="d-item"><span>二级分类</span><strong>{{ String(hospitalDetailRow.secondCategory ?? '-') }}</strong></div>
              <div class="d-item full"><span>三级分类</span><strong>{{ String(hospitalDetailRow.thirdCategory ?? '-') }}</strong></div>
              <div class="d-item"><span>注册证号</span><strong class="mono">{{ String(hospitalDetailRow.registrationNo ?? '-') }}</strong></div>
              <div class="d-item"><span>招采子编码</span><strong class="mono">{{ String(hospitalDetailRow.tenderSubCode ?? '-') }}</strong></div>
              <div class="d-item full"><span>合同编码</span><strong class="mono">{{ String(hospitalDetailRow.contractCode ?? '-') }}</strong></div>
            </div>
          </section>
          <section class="d-section">
            <h5>属性标签</h5>
            <div class="d-tags">
              <span class="tag" :class="{ hl: String(hospitalDetailRow.centralized) === '是' }">集采 {{ String(hospitalDetailRow.centralized ?? '-') }}</span>
              <span class="tag" :class="{ hl: String(hospitalDetailRow.domestic) === '是' }">国产 {{ String(hospitalDetailRow.domestic ?? '-') }}</span>
              <span class="tag" :class="{ hl: String(hospitalDetailRow.chargeable) === '是' }">收费 {{ String(hospitalDetailRow.chargeable ?? '-') }}</span>
              <span class="tag" :class="{ hl: String(hospitalDetailRow.keyMonitored) === '是' }">重点监控 {{ String(hospitalDetailRow.keyMonitored ?? '-') }}</span>
              <span class="tag" :class="{ hl: String(hospitalDetailRow.volumeBased) === '是' }">带量 {{ String(hospitalDetailRow.volumeBased ?? '-') }}</span>
              <span class="tag" :class="{ hl: String(hospitalDetailRow.quotaManaged) === '是' }">定数 {{ String(hospitalDetailRow.quotaManaged ?? '-') }}</span>
            </div>
          </section>
        </div>
      </aside>
    </section>

    <section v-else-if="isSupplierManagement" class="md-partner-page spd-unified-list">
      <MdPageHeader
        v-if="!isPartnerCombined"
        title="供应商管理"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="loadPage">
        <div class="ulc-filter-fields">
          <label>
            <span>供应商名称</span>
            <input
              v-model.trim="supplierQuery.supplierName"
              class="md-input"
              type="search"
              placeholder="输入供应商名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>供应商类型</span>
            <input
              v-model.trim="supplierQuery.supplierType"
              class="md-input"
              type="search"
              placeholder="如：配送商、生产商"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <el-select v-model="supplierQuery.status" placeholder="全部状态" clearable aria-label="筛选供应商启停状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetSupplierQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else-if="isManufacturerManagement" class="md-partner-page spd-unified-list">
      <MdPageHeader
        v-if="!isPartnerCombined"
        title="厂家管理"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="loadPage">
        <div class="ulc-filter-fields">
          <label>
            <span>厂家名称</span>
            <input
              v-model.trim="manufacturerQuery.manufacturerName"
              class="md-input"
              type="search"
              placeholder="输入厂家名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>生产许可证号</span>
            <input
              v-model.trim="manufacturerQuery.licenseNo"
              class="md-input"
              type="search"
              placeholder="输入生产许可证号"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <el-select v-model="manufacturerQuery.status" placeholder="全部状态" clearable aria-label="筛选厂家启停状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetManufacturerQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else-if="isCampusManagement" class="md-partner-page spd-unified-list">
      <MdPageHeader
        title="院区管理"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="loadPage">
        <div class="ulc-filter-fields">
          <label>
            <span>院区编码</span>
            <input
              v-model.trim="campusQuery.campusCode"
              class="md-input"
              type="search"
              placeholder="输入院区编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>院区名称</span>
            <input
              v-model.trim="campusQuery.campusName"
              class="md-input"
              type="search"
              placeholder="输入院区名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <el-select v-model="campusQuery.status" placeholder="全部状态" clearable aria-label="筛选院区启停状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetCampusQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else-if="isDepartmentManagement" class="md-partner-page spd-unified-list">
      <MdPageHeader
        title="科室管理"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="loadPage">
        <div class="ulc-filter-fields">
          <label>
            <span>科室名称 / 编码</span>
            <input
              v-model.trim="departmentQuery.deptKeyword"
              class="md-input"
              type="search"
              placeholder="输入科室名称或编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>科室类型</span>
            <input
              v-model.trim="departmentQuery.deptType"
              class="md-input"
              type="search"
              placeholder="如：临床科室"
              autocomplete="off"
            />
          </label>
          <label>
            <span>科室状态</span>
            <el-select v-model="departmentQuery.status" placeholder="全部状态" clearable aria-label="筛选科室状态">
              <el-option label="全部状态" value="" />
              <el-option label="正常" value="正常" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
          <label>
            <span>源数据标识</span>
            <input
              v-model.trim="departmentQuery.sourceFlag"
              class="md-input"
              type="search"
              placeholder="输入源标识"
              autocomplete="off"
            />
          </label>
          <label>
            <span>关联库房</span>
            <input
              v-model.trim="departmentQuery.warehouseName"
              class="md-input"
              type="search"
              placeholder="输入库房名称"
              autocomplete="off"
            />
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetDepartmentQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else-if="isDepartmentWarehouseCatalog" class="md-partner-page spd-unified-list">
      <MdPageHeader
        title="科室库房目录"
        :loading="loading"
        @refresh="loadPage"
      />
      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="submitDepartmentWarehouseCatalogQuery">
        <div class="ulc-filter-fields">
          <label>
            <span>科室</span>
            <input
              v-model.trim="departmentWarehouseCatalogQuery.deptName"
              class="md-input"
              type="search"
              placeholder="输入科室名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>库房</span>
            <input
              v-model.trim="departmentWarehouseCatalogQuery.warehouseName"
              class="md-input"
              type="search"
              placeholder="输入关联库房"
              autocomplete="off"
            />
          </label>
          <label>
            <span>商品</span>
            <input
              v-model.trim="departmentWarehouseCatalogQuery.productKeyword"
              class="md-input"
              type="search"
              placeholder="商品编码或名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>目录状态</span>
            <el-select v-model="departmentWarehouseCatalogQuery.status" placeholder="全部状态" clearable aria-label="筛选目录状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetDepartmentWarehouseCatalogQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else-if="isWarehouseManagement" class="md-partner-page spd-unified-list">
      <MdPageHeader
        title="库房 / 货位管理"
        :loading="loading"
        @refresh="loadPage"
      />
      <form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="loadPage">
        <div class="ulc-filter-fields">
          <label>
            <span>库房名称 / 编码</span>
            <input
              v-model.trim="warehouseQuery.warehouseKeyword"
              class="md-input"
              type="search"
              placeholder="输入库房名称或编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>库房类型</span>
            <input
              v-model.trim="warehouseQuery.warehouseType"
              class="md-input"
              type="search"
              placeholder="如：药库"
              autocomplete="off"
            />
          </label>
          <label>
            <span>所属院区</span>
            <input
              v-model.trim="warehouseQuery.campusName"
              class="md-input"
              type="search"
              placeholder="输入院区名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>关联科室</span>
            <input
              v-model.trim="warehouseQuery.deptName"
              class="md-input"
              type="search"
              placeholder="输入科室名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>库房状态</span>
            <el-select v-model="warehouseQuery.status" placeholder="全部状态" clearable aria-label="筛选库房状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="启用" />
              <el-option label="停用" value="停用" />
            </el-select>
          </label>
        </div>
        <div class="md-hospital-filter-actions ulc-filter-actions">
          <button type="button" class="md-btn" @click="resetWarehouseQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
        </div>
      </form>
    </section>

    <section v-else class="master-toolbar">
      <div class="master-search">
        <Search :size="18" />
        <input type="text" placeholder="搜索编码、名称、联系人..." />
      </div>
      <button class="btn btn-primary" type="button">
        <Plus :size="17" />
        新增
      </button>
    </section>

    <section
      v-if="!isHospitalCatalog"
      class="master-table-card"
      :class="{
        'spd-unified-list': isDepartmentManagement || isDepartmentWarehouseCatalog || isWarehouseManagement || isCampusManagement || isSupplierManagement || isManufacturerManagement,
        'department-warehouse-catalog-list-card': isDepartmentWarehouseCatalog,
        'department-management-list-card': isDepartmentManagement,
        'warehouse-location-list-card': isWarehouseManagement,
        'campus-management-list-card': isCampusManagement,
        'supplier-management-list-card': isSupplierManagement,
        'manufacturer-management-list-card': isManufacturerManagement,
        'master-table-card-flat':
          isDepartmentWarehouseCatalog ||
          isSupplierManagement ||
          isManufacturerManagement ||
          isCampusManagement ||
          isDepartmentManagement ||
          isWarehouseManagement
      }"
    >
      <StatusMessage v-if="loading" message="正在加载..." tone="info" />
      <StatusMessage v-else-if="error" :message="error" tone="error" />



      <MdDataTableCard
        v-if="isSupplierManagement && page"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateSupplier"
          >
            <Plus :size="16" />
            <span>新增供应商</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1"
            @click="openEditSupplier()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn md-btn-primary" aria-label="打开供应商导入菜单">
              <Upload :size="16" />
              <span>导入</span>
            </summary>
            <div class="supplier-import-menu">
              <button class="btn-text" type="button" @click="downloadSupplierTemplate">
                <FileDown :size="16" />
                下载导入模板
              </button>
              <button class="btn-text" type="button" @click="triggerSupplierImport">
                <FileUp :size="16" />
                上传 CSV 文件
              </button>
            </div>
          </details>
          <button type="button" class="md-btn md-btn-primary" @click="exportSupplierRows">
            <Download :size="16" />
            <span>导出</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length"
            @click="disableSuppliers()"
          >
            <Ban :size="16" />
            <span>停用</span>
          </button>
          <input ref="importInput" class="hidden-file-input" type="file" accept=".csv" @change="handleSupplierImport" />
          <div class="ulc-col-settings">
            <button type="button" ref="supplierColSettingsAnchorRef" @click="supplierCols.settingsOpen.value = !supplierCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: supplierCols.settingsOpen.value }" />
            </button>
            <div v-if="supplierCols.settingsOpen.value" ref="supplierColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': supplierColSettingsPop.align.value === 'right', 'is-align-up': supplierColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': supplierColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': supplierColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="supplierCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in supplierCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && supplierCols.visibleColumnCount.value <= 1"
                    @change="supplierCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="supplierCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="supplierTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page.rows"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无符合条件的供应商"
            @selection-change="onSupplierSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => supplierCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="code"
              v-if="isSupplierColVisible('code')"
              label="供应商编码"
              v-bind="supplierColBinding('code')"
            >
              <template #header>
                <span class="ulc-th-text">供应商编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="name"
              v-if="isSupplierColVisible('name')"
              label="供应商名称"
              v-bind="supplierColBinding('name')"
            >
              <template #header>
                <span class="ulc-th-text">供应商名称</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="creditCode"
              v-if="isSupplierColVisible('creditCode')"
              label="统一社会信用代码"
              v-bind="supplierColBinding('creditCode')"
            >
              <template #header>
                <span class="ulc-th-text">统一社会信用代码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.creditCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="businessLicenseNo"
              v-if="isSupplierColVisible('businessLicenseNo')"
              label="经营许可证号"
              v-bind="supplierColBinding('businessLicenseNo')"
            >
              <template #header>
                <span class="ulc-th-text">经营许可证号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.businessLicenseNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="type"
              v-if="isSupplierColVisible('type')"
              label="类型"
              v-bind="supplierColBinding('type')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.type || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="grade"
              v-if="isSupplierColVisible('grade')"
              label="等级"
              v-bind="supplierColBinding('grade')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">等级</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="row.grade && row.grade !== '-' ? 'is-primary' : 'is-neutral'">
                  {{ row.grade || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="contact"
              v-if="isSupplierColVisible('contact')"
              label="联系人"
              v-bind="supplierColBinding('contact')"
            >
              <template #header>
                <span class="ulc-th-text">联系人</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 联系人与电话是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.contactName || '-' }}</span>
                    <span class="ulc-sub">{{ row.contactPhone || '-' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="email"
              v-if="isSupplierColVisible('email')"
              label="邮箱"
              v-bind="supplierColBinding('email')"
            >
              <template #header>
                <span class="ulc-th-text">邮箱</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.email || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="address"
              v-if="isSupplierColVisible('address')"
              label="地址"
              v-bind="supplierColBinding('address')"
            >
              <template #header>
                <span class="ulc-th-text">地址</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <template v-for="column in PARTNER_TIME_COLUMNS" :key="column.key">
              <el-table-column :column-key="column.key" v-if="isSupplierColVisible(column.key)" :label="column.label" v-bind="supplierColBinding(column.key)">
                <template #header>
                  <span class="ulc-th-text">{{ column.label }}</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-time">{{ formatListDateTime(row[column.key]) }}</span>
                </template>
              </el-table-column>
            </template>
            <el-table-column
            column-key="status"
              v-if="isSupplierColVisible('status')"
              label="状态"
              v-bind="supplierColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isSupplierColVisible('actions')"

              label="操作"

              v-bind="supplierColBinding('actions')"

              :fixed="supplierActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action"
                    @click="openEditSupplier(row, false)"
                  >
                    修改
                  </button>
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action" :class="{ 'is-danger': row.status !== '停用' }"
                    @click="openPartnerStatusConfirm('supplier', [String(row.code)], row.status === '停用' ? 1 : 0)"
                  >
                    {{ row.status === '停用' ? '启用' : '停用' }}
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无符合条件的供应商" hint="尝试调整筛选条件或新增供应商" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="partnerCurrentPage"
            :total-pages="Math.max(1, Math.ceil(partnerTotal / partnerPageSize))"
            :total="partnerTotal"
            :size="partnerPageSize"
            :loading="loading"
            @update:page="changePartnerPage"
            @update:size="changePartnerPageSize"
          />
        </template>
      </MdDataTableCard>

      <MdDataTableCard
        v-else-if="isManufacturerManagement && page"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateManufacturer"
          >
            <Plus :size="16" />
            <span>新增厂家</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1"
            @click="openEditManufacturer()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn md-btn-primary" aria-label="打开厂家导入菜单">
              <Upload :size="16" />
              <span>导入</span>
            </summary>
            <div class="supplier-import-menu">
              <button class="btn-text" type="button" @click="downloadManufacturerTemplate">
                <FileDown :size="16" />
                下载导入模板
              </button>
              <button class="btn-text" type="button" @click="triggerManufacturerImport">
                <FileUp :size="16" />
                上传 CSV 文件
              </button>
            </div>
          </details>
          <button type="button" class="md-btn md-btn-primary" @click="exportManufacturerRows">
            <Download :size="16" />
            <span>导出</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length"
            @click="disableManufacturers()"
          >
            <Ban :size="16" />
            <span>停用</span>
          </button>
          <input
            ref="manufacturerImportInput"
            class="hidden-file-input"
            type="file"
            accept=".csv"
            @change="handleManufacturerImport"
          />
          <div class="ulc-col-settings">
            <button type="button" ref="manufacturerColSettingsAnchorRef" @click="manufacturerCols.settingsOpen.value = !manufacturerCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: manufacturerCols.settingsOpen.value }" />
            </button>
            <div v-if="manufacturerCols.settingsOpen.value" ref="manufacturerColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': manufacturerColSettingsPop.align.value === 'right', 'is-align-up': manufacturerColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': manufacturerColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': manufacturerColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="manufacturerCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in manufacturerCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && manufacturerCols.visibleColumnCount.value <= 1"
                    @change="manufacturerCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="manufacturerCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="manufacturerTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page.rows"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无符合条件的厂家"
            @selection-change="onManufacturerSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => manufacturerCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="code"
              v-if="isManufacturerColVisible('code')"
              label="厂家编码"
              v-bind="manufacturerColBinding('code')"
            >
              <template #header>
                <span class="ulc-th-text">厂家编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="name"
              v-if="isManufacturerColVisible('name')"
              label="厂家名称"
              v-bind="manufacturerColBinding('name')"
            >
              <template #header>
                <span class="ulc-th-text">厂家名称</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="creditCode"
              v-if="isManufacturerColVisible('creditCode')"
              label="统一社会信用代码"
              v-bind="manufacturerColBinding('creditCode')"
            >
              <template #header>
                <span class="ulc-th-text">统一社会信用代码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.creditCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="licenseNo"
              v-if="isManufacturerColVisible('licenseNo')"
              label="生产许可证号"
              v-bind="manufacturerColBinding('licenseNo')"
            >
              <template #header>
                <span class="ulc-th-text">生产许可证号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.licenseNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="contact"
              v-if="isManufacturerColVisible('contact')"
              label="联系人"
              v-bind="manufacturerColBinding('contact')"
            >
              <template #header>
                <span class="ulc-th-text">联系人</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 联系人与电话是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.contactName || '-' }}</span>
                    <span class="ulc-sub">{{ row.contactPhone || '-' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="address"
              v-if="isManufacturerColVisible('address')"
              label="地址"
              v-bind="manufacturerColBinding('address')"
            >
              <template #header>
                <span class="ulc-th-text">地址</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <template v-for="column in PARTNER_TIME_COLUMNS" :key="column.key">
              <el-table-column :column-key="column.key" v-if="isManufacturerColVisible(column.key)" :label="column.label" v-bind="manufacturerColBinding(column.key)">
                <template #header>
                  <span class="ulc-th-text">{{ column.label }}</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-time">{{ formatListDateTime(row[column.key]) }}</span>
                </template>
              </el-table-column>
            </template>
            <el-table-column
            column-key="status"
              v-if="isManufacturerColVisible('status')"
              label="状态"
              v-bind="manufacturerColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isManufacturerColVisible('actions')"

              label="操作"

              v-bind="manufacturerColBinding('actions')"

              :fixed="manufacturerActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action"
                    @click="openEditManufacturer(row, false)"
                  >
                    修改
                  </button>
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action" :class="{ 'is-danger': row.status !== '停用' }"
                    @click="openPartnerStatusConfirm('manufacturer', [String(row.code)], row.status === '停用' ? 1 : 0)"
                  >
                    {{ row.status === '停用' ? '启用' : '停用' }}
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无符合条件的厂家" hint="尝试调整筛选条件或新增厂家" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="partnerCurrentPage"
            :total-pages="Math.max(1, Math.ceil(partnerTotal / partnerPageSize))"
            :total="partnerTotal"
            :size="partnerPageSize"
            :loading="loading"
            @update:page="changePartnerPage"
            @update:size="changePartnerPageSize"
          />
        </template>
      </MdDataTableCard>

      <MdDataTableCard
        v-else-if="isCampusManagement && page"
        class="md-table-card-campus"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateCampus"
          >
            <Plus :size="16" />
            <span>新增院区</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditCampus()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length || loading"
            @click="deleteSelectedCampuses()"
          >
            <Trash2 :size="16" />
            <span>删除</span>
          </button>
          <div class="ulc-col-settings">
            <button type="button" ref="campusColSettingsAnchorRef" @click="campusCols.settingsOpen.value = !campusCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: campusCols.settingsOpen.value }" />
            </button>
            <div v-if="campusCols.settingsOpen.value" ref="campusColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': campusColSettingsPop.align.value === 'right', 'is-align-up': campusColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': campusColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': campusColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="campusCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in campusCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && campusCols.visibleColumnCount.value <= 1"
                    @change="campusCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="campusCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="campusTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page.rows"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无符合条件的院区"
            @selection-change="onCampusSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => campusCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="code"
              v-if="isCampusColVisible('code')"
              label="院区编码"
              v-bind="campusColBinding('code')"
            >
              <template #header>
                <span class="ulc-th-text">院区编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="name"
              v-if="isCampusColVisible('name')"
              label="院区名称"
              v-bind="campusColBinding('name')"
            >
              <template #header>
                <span class="ulc-th-text">院区名称</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.name || '-' }}</span>
                  <span class="ulc-sub">组织基础档案</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="address"
              v-if="isCampusColVisible('address')"
              label="院区地址"
              v-bind="campusColBinding('address')"
            >
              <template #header>
                <span class="ulc-th-text">院区地址</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="manager"
              v-if="isCampusColVisible('manager')"
              label="负责人"
              v-bind="campusColBinding('manager')"
            >
              <template #header>
                <span class="ulc-th-text">负责人</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 负责人与电话是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.manager || '-' }}</span>
                    <span class="ulc-sub">{{ row.phone || '-' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="sortOrder"
              v-if="isCampusColVisible('sortOrder')"
              label="排序"
              v-bind="campusColBinding('sortOrder')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">排序</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.sortOrder ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="status"
              v-if="isCampusColVisible('status')"
              label="状态"
              v-bind="campusColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column column-key="createTime"v-if="isCampusColVisible('createTime')" label="创建时间" v-bind="campusColBinding('createTime')">
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="updateTime"
              v-if="isCampusColVisible('updateTime')"
              label="修改时间"
              v-bind="campusColBinding('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isCampusColVisible('actions')"

              label="操作"

              v-bind="campusColBinding('actions')"

              :fixed="campusActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action"
                    @click="openEditCampus(row, false)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action is-danger"
                    @click="deleteSelectedCampuses([String(row.code)])"
                  >
                    删除
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无符合条件的院区" hint="尝试调整筛选条件或新增院区" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="listCurrentPage"
            :total-pages="listTotalPages"
            :total="listTotal"
            :size="listPageSize"
            :loading="loading"
            @update:page="changeListPage"
            @update:size="changeListPageSize"
          />
        </template>
      </MdDataTableCard>

      <MdDataTableCard
        v-else-if="isDepartmentManagement && page"
        class="md-table-card-department"
        :meta-count="`共 ${listTotal} 个`"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateDepartment"
          >
            <Plus :size="16" />
            <span>新增科室</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditDepartment()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="!selectedCodes.length || loading"
            @click="openDepartmentWarehouseDialog()"
          >
            <Link2 :size="16" />
            <span>关联库房</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn md-btn-primary" aria-label="打开科室导入菜单">
              <Upload :size="16" />
              <span>导入</span>
            </summary>
            <div class="supplier-import-menu">
              <button class="btn-text" type="button" @click="downloadDepartmentTemplate">
                <FileDown :size="16" />
                下载导入模板
              </button>
              <button class="btn-text" type="button" @click="triggerDepartmentImport">
                <FileUp :size="16" />
                上传 CSV 文件
              </button>
            </div>
          </details>
          <button type="button" class="md-btn md-btn-primary" @click="exportDepartmentRows">
            <Download :size="16" />
            <span>导出</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length || loading"
            @click="deleteSelectedDepartments()"
          >
            <Trash2 :size="16" />
            <span>删除</span>
          </button>
          <input
            ref="departmentImportInput"
            class="hidden-file-input"
            type="file"
            accept=".csv"
            @change="handleDepartmentImport"
          />
          <div class="ulc-col-settings">
            <button type="button" ref="departmentColSettingsAnchorRef" @click="departmentCols.settingsOpen.value = !departmentCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: departmentCols.settingsOpen.value }" />
            </button>
            <div v-if="departmentCols.settingsOpen.value" ref="departmentColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': departmentColSettingsPop.align.value === 'right', 'is-align-up': departmentColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': departmentColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': departmentColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="departmentCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in departmentCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && departmentCols.visibleColumnCount.value <= 1"
                    @change="departmentCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="departmentCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="departmentTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page.rows"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无符合条件的科室"
            @selection-change="onDepartmentSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => departmentCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="code"
              v-if="isDepartmentColVisible('code')"
              label="科室编码"
              v-bind="departmentColBinding('code')"
            >
              <template #header>
                <span class="ulc-th-text">科室编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="name"
              v-if="isDepartmentColVisible('name')"
              label="科室名称"
              v-bind="departmentColBinding('name')"
            >
              <template #header>
                <span class="ulc-th-text">科室名称</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.name }}</span>
                  <span class="ulc-sub">{{ row.deptAttribute || '科室档案' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="org"
              v-if="isDepartmentColVisible('org')"
              label="组织归属"
              v-bind="departmentColBinding('org')"
            >
              <template #header>
                <span class="ulc-th-text">组织归属</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.campus || '未归属院区' }}</span>
                  <div class="ulc-inline-tags">
                    <span class="ulc-tag">{{ row.deptType || '-' }}</span>
                    <span class="ulc-tag">{{ row.deptAttribute || '-' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="finance"
              v-if="isDepartmentColVisible('finance')"
              label="财务科室映射"
              v-bind="departmentColBinding('finance')"
            >
              <template #header>
                <span class="ulc-th-text">财务科室映射</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 财务科室与编码是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.financeDept || '-' }}</span>
                    <span class="ulc-sub ulc-mono">{{ row.financeDeptCode || '-' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="manager"
              v-if="isDepartmentColVisible('manager')"
              label="负责人"
              v-bind="departmentColBinding('manager')"
            >
              <template #header>
                <span class="ulc-th-text">负责人</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 负责人与电话是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.manager || '-' }}</span>
                    <span class="ulc-sub">{{ row.phone || '-' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="address"
              v-if="isDepartmentColVisible('address')"
              label="科室地址"
              v-bind="departmentColBinding('address')"
            >
              <template #header>
                <span class="ulc-th-text">科室地址</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="warehouse"
              v-if="isDepartmentColVisible('warehouse')"
              label="关联库房"
              v-bind="departmentColBinding('warehouse')"
            >
              <template #header>
                <span class="ulc-th-text">关联库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.relatedWarehouse || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="source"
              v-if="isDepartmentColVisible('source')"
              label="原数据标识"
              v-bind="departmentColBinding('source')"
            >
              <template #header>
                <span class="ulc-th-text">原数据标识</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.sourceFlag || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="status"
              v-if="isDepartmentColVisible('status')"
              label="状态"
              v-bind="departmentColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="String(row.status) === '正常' ? 'is-success' : 'is-danger'">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column column-key="createTime"v-if="isDepartmentColVisible('createTime')" label="创建时间" v-bind="departmentColBinding('createTime')">
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="updateTime"
              v-if="isDepartmentColVisible('updateTime')"
              label="修改时间"
              v-bind="departmentColBinding('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isDepartmentColVisible('actions')"

              label="操作"

              v-bind="departmentColBinding('actions')"

              :fixed="departmentActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button type="button" class="ulc-action" @click="openEditDepartment(row, false)">编辑</button>
                  <button type="button" class="ulc-action is-info" @click="openDepartmentWarehouseDialog(row, false)">关联库房</button>
                  <button
                    type="button"
                    class="ulc-action is-danger"
                    @click="deleteSelectedDepartments([String(row.code)])"
                  >
                    删除
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无符合条件的科室" hint="尝试调整筛选条件或新增科室" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="listCurrentPage"
            :total-pages="listTotalPages"
            :total="listTotal"
            :size="listPageSize"
            :loading="loading"
            @update:page="changeListPage"
            @update:size="changeListPageSize"
          />
        </template>
      </MdDataTableCard>

      <MdDataTableCard
        v-else-if="isDepartmentWarehouseCatalog && page"
        class="md-table-card-catalog"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateDepartmentWarehouseCatalog"
          >
            <Plus :size="16" />
            <span>新增目录</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="!selectedCodes.length || loading"
            @click="openBatchDepartmentWarehouseCatalog"
          >
            <ListPlus :size="16" />
            <span>批量维护</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditDepartmentWarehouseCatalog()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="!selectedCodes.length || loading"
            @click="updateSelectedDepartmentWarehouseCatalogStatus(1)"
          >
            <RefreshCw :size="16" />
            <span>启用</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-warning"
            :disabled="!selectedCodes.length || loading"
            @click="updateSelectedDepartmentWarehouseCatalogStatus(0)"
          >
            <Ban :size="16" />
            <span>停用</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length || loading"
            @click="deleteSelectedDepartmentWarehouseCatalogs()"
          >
            <Trash2 :size="16" />
            <span>删除</span>
          </button>
          <div class="ulc-col-settings">
            <button type="button" ref="catalogColSettingsAnchorRef" @click="catalogCols.settingsOpen.value = !catalogCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: catalogCols.settingsOpen.value }" />
            </button>
            <div v-if="catalogCols.settingsOpen.value" ref="catalogColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': catalogColSettingsPop.align.value === 'right', 'is-align-up': catalogColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': catalogColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': catalogColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="catalogCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in catalogCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && catalogCols.visibleColumnCount.value <= 1"
                    @change="catalogCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="catalogCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <div class="ulc-table-wrap">
        <el-table
          ref="catalogTableRef"
          v-loading="loading"
          class="ulc-table is-sticky"
          :data="page.rows"
          row-key="code"
          border
          stripe
          style="width: 100%"
          empty-text="暂无符合条件的科室库房目录"
          @selection-change="onCatalogSelectionChange"
          :max-height="catalogTableMaxHeight || undefined"
          @header-dragend="(width: number, oldWidth: number, column: any) => catalogCols.handleHeaderDragEnd(width, oldWidth, column)">
          <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
          <el-table-column
            column-key="scope"
            v-if="isCatalogColVisible('scope')"
            label="科室 / 库房"
            v-bind="catalogColBinding('scope')"
          >
            <template #header>
              <span class="ulc-th-text">科室 / 库房</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong ulc-wrap-text">{{ row.deptName || '-' }}</span>
                <span class="ulc-sub ulc-wrap-text is-sub">{{ row.warehouseName ? `库房名称：${row.warehouseName}` : '未关联库房' }}</span>
                <span class="ulc-sub ulc-wrap-text is-sub">库房等级：{{ row.warehouseType || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productCode"
            v-if="isCatalogColVisible('productCode')"
            label="商品编码"
            v-bind="catalogColBinding('productCode')"
          >
            <template #header>
              <span class="ulc-th-text">商品编码</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-mono ulc-wrap-text">{{ row.productCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productName"
            v-if="isCatalogColVisible('productName')"
            label="商品名称"
            v-bind="catalogColBinding('productName')"
          >
            <template #header>
              <span class="ulc-th-text">商品名称</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong ulc-wrap-text">{{ row.productName || '-' }}</span>
                <span class="ulc-sub ulc-wrap-text is-sub">科室可申领商品</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="spec"
            v-if="isCatalogColVisible('spec')"
            label="规格型号"
            v-bind="catalogColBinding('spec')"
          >
            <template #header>
              <span class="ulc-th-text">规格型号</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-strong ulc-wrap-text">{{ row.specModel || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="manufacturer"
            v-if="isCatalogColVisible('manufacturer')"
            label="生产厂家"
            v-bind="catalogColBinding('manufacturer')"
          >
            <template #header>
              <span class="ulc-th-text">生产厂家</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-strong ulc-wrap-text">{{ row.manufacturerName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="source"
            v-if="isCatalogColVisible('source')"
            label="维护来源"
            v-bind="catalogColBinding('source')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">维护来源</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-chip" :class="catalogSourceChipClass(row.sourceType)">
                {{ formatBusinessText(row.sourceType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="status"
            v-if="isCatalogColVisible('status')"
            label="状态"
            v-bind="catalogColBinding('status')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">状态</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-chip" :class="String(row.status) === '启用' ? 'is-success' : 'is-danger'">
                {{ formatStatusText(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column column-key="createTime"v-if="isCatalogColVisible('createTime')" label="创建时间" v-bind="catalogColBinding('createTime')">
            <template #header>
              <span class="ulc-th-text">创建时间</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ formatListDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="updateTime"
            v-if="isCatalogColVisible('updateTime')"
            label="更新时间"
            v-bind="catalogColBinding('updateTime')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">更新时间</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="actions"

            v-if="isCatalogColVisible('actions')"

            label="操作"

            

            :fixed="catalogActionFixed"
            v-bind="catalogColBinding('actions')"
          >

            <template #header>

              <span class="ulc-th-text">操作</span>

            </template>

            <template #default="{ row }">
              <div class="ulc-actions">
                <button type="button" class="ulc-action" @click="openEditDepartmentWarehouseCatalog(row, false)">编辑</button>
                <button
                  type="button"
                  class="ulc-action is-info"
                  @click="updateSelectedDepartmentWarehouseCatalogStatus(String(row.status) === '停用' ? 1 : 0, [String(row.code)])"
                >
                  {{ String(row.status) === '停用' ? '启用' : '停用' }}
                </button>
                <button type="button" class="ulc-action is-danger" @click="deleteSelectedDepartmentWarehouseCatalogs([String(row.code)])">
                  删除
                </button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <MdEmptyState text="暂无符合条件的科室库房目录" hint="尝试调整筛选条件或新增目录" />
          </template>
        </el-table>
        </div>
        <template #pagination>
          <MdPagination
            v-if="isDepartmentWarehouseCatalog"
            :page="catalogCurrentPage"
            :total-pages="catalogPageTotal"
            :total="catalogTotal"
            :size="catalogPageSize"
            :loading="loading"
            @update:page="loadDepartmentWarehouseCatalogPage"
            @update:size="changeDepartmentWarehouseCatalogPageSize"
          />
        </template>
      </MdDataTableCard>

      <MdDataTableCard
        v-else-if="isWarehouseManagement && page"
        class="md-table-card-warehouse"
        :loading="loading"
      >
        <template #toolbar>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            @click="openCreateWarehouse"
          >
            <Plus :size="16" />
            <span>新增</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditWarehouse()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-danger"
            :disabled="!selectedCodes.length || loading"
            @click="deleteSelectedWarehouses()"
          >
            <Trash2 :size="16" />
            <span>删除</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn md-btn-primary" aria-label="打开库房导入菜单">
              <Upload :size="16" />
              <span>导入</span>
            </summary>
            <div class="supplier-import-menu">
              <button class="btn-text" type="button" @click="downloadWarehouseTemplate">
                <FileDown :size="16" />
                导入模板下载
              </button>
              <button class="btn-text" type="button" @click="triggerWarehouseImport">
                <FileUp :size="16" />
                上传 CSV 文件
              </button>
            </div>
          </details>
          <button type="button" class="md-btn md-btn-primary" @click="exportWarehouseRows">
            <Download :size="16" />
            <span>导出 Excel</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn md-btn-primary"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openWarehouseLocationDialog(undefined, true)"
          >
            <Link2 :size="16" />
            <span>维护货位</span>
          </button>
          <input
            ref="warehouseImportInput"
            class="hidden-file-input"
            type="file"
            accept=".csv"
            @change="handleWarehouseImport"
          />
          <div class="ulc-col-settings">
            <button type="button" ref="warehouseColSettingsAnchorRef" @click="warehouseCols.settingsOpen.value = !warehouseCols.settingsOpen.value" class="md-btn md-btn-primary">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: warehouseCols.settingsOpen.value }" />
            </button>
            <div v-if="warehouseCols.settingsOpen.value" ref="warehouseColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': warehouseColSettingsPop.align.value === 'right', 'is-align-up': warehouseColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': warehouseColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': warehouseColSettingsPop.maxHeight.value + 'px' }">
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="warehouseCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in warehouseCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && warehouseCols.visibleColumnCount.value <= 1"
                    @change="warehouseCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="warehouseCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="warehouseTableRef"
            v-loading="loading"
            class="ulc-table is-sticky"
            :data="page.rows"
            row-key="code"
            border
            stripe
            max-height="560" style="width: 100%"
            empty-text="暂无符合条件的库房"
            @selection-change="onWarehouseSelectionChange"

            @header-dragend="(width: number, oldWidth: number, column: any) => warehouseCols.handleHeaderDragEnd(width, oldWidth, column)">
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
            column-key="code"
              v-if="isWarehouseColVisible('code')"
              label="库房编码"
              v-bind="warehouseColBinding('code')"
            >
              <template #header>
                <span class="ulc-th-text">库房编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="name"
              v-if="isWarehouseColVisible('name')"
              label="库房名称"
              v-bind="warehouseColBinding('name')"
            >
              <template #header>
                <span class="ulc-th-text">库房名称</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.name || '-' }}</span>
                  <span v-if="row.needsDepartmentFix" class="ulc-chip is-warning">需补科室</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="type"
              v-if="isWarehouseColVisible('type')"
              label="库房类型"
              v-bind="warehouseColBinding('type')"
            >
              <template #header>
                <span class="ulc-th-text">库房类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.type || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="campus"
              v-if="isWarehouseColVisible('campus')"
              label="所属院区"
              v-bind="warehouseColBinding('campus')"
            >
              <template #header>
                <span class="ulc-th-text">所属院区</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.campus || '未归属院区' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="dept"
              v-if="isWarehouseColVisible('dept')"
              label="关联科室"
              v-bind="warehouseColBinding('dept')"
            >
              <template #header>
                <span class="ulc-th-text">关联科室</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <!-- 科室与编码是一个整体：整体放得下就保持一行 -->
                  <span class="ulc-joined">
                    <span class="ulc-strong">{{ row.dept || '-' }}</span>
                    <span v-if="row.deptCode" class="ulc-sub ulc-mono">{{ row.deptCode }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
            column-key="participateStats"
              v-if="isWarehouseColVisible('participateStats')"
              label="统计"
              v-bind="warehouseColBinding('participateStats')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">统计</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="warehouseFlagChipClass(row.participateStats === '参与')">
                  {{ row.participateStats || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="receivingEnabled"
              v-if="isWarehouseColVisible('receivingEnabled')"
              label="允许收货"
              v-bind="warehouseColBinding('receivingEnabled')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">允许收货</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="warehouseFlagChipClass(row.receivingEnabled)">
                  {{ row.receivingEnabled ? '允许' : '不允许' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="statsCategories"
              v-if="isWarehouseColVisible('statsCategories')"
              label="统计分类"
              v-bind="warehouseColBinding('statsCategories')"
            >
              <template #header>
                <span class="ulc-th-text">统计分类</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.statsCategories || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="status"
              v-if="isWarehouseColVisible('status')"
              label="状态"
              v-bind="warehouseColBinding('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column column-key="createTime"v-if="isWarehouseColVisible('createTime')" label="创建时间" v-bind="warehouseColBinding('createTime')">
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="updateTime"
              v-if="isWarehouseColVisible('updateTime')"
              label="修改时间"
              v-bind="warehouseColBinding('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
            column-key="locationCount"
              v-if="isWarehouseColVisible('locationCount')"
              label="货位数量"
              v-bind="warehouseColBinding('locationCount')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">货位数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.locationCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isWarehouseColVisible('actions')"

              label="操作"

              v-bind="warehouseColBinding('actions')"

              :fixed="warehouseActionFixed"
            >

              <template #header>

                <span class="ulc-th-text">操作</span>

              </template>

              <template #default="{ row }">
                <div class="ulc-actions">
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action"
                    @click="openEditWarehouse(row, false)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action is-info"
                    @click="openWarehouseLocationDialog(row)"
                  >
                    货位
                  </button>
                  <button
                    v-if="authStore.canWrite(code)"
                    type="button"
                    class="ulc-action is-danger"
                    @click="deleteSelectedWarehouses([String(row.code)])"
                  >
                    删除
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无符合条件的库房" hint="尝试调整筛选条件或新增库房" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            v-if="isWarehouseManagement"
            :page="listCurrentPage"
            :total-pages="listTotalPages"
            :total="listTotal"
            :size="listPageSize"
            :loading="loading"
            @update:page="changeListPage"
            @update:size="changeListPageSize"
          />
        </template>
      </MdDataTableCard>

    </section>

    <div v-if="isHospitalCatalog && submittedApplicationNo" class="standard-status-message is-info">
      已提交审批，最终通过后生效。
      <RouterLink :to="{ name: 'pending-product-approval-detail', params: { applicationNo: submittedApplicationNo } }">查看审批单 {{ submittedApplicationNo }}</RouterLink>
    </div>
    <ProductDialog
      :open="productDialogOpen"
      :saving="productSaving"
      :error="actionError"
      :mode="productDialogMode"
      :form="productForm"
      :manufacturer-options="manufacturerOptions"
      :supplier-options="supplierOptions"
      @close="closeProductDialog"
      @save="saveProduct"
    />

    <BatchProductDialog
      :open="batchProductDialogOpen"
      :saving="batchProductSaving"
      :error="actionError"
      :form="batchProductForm"
      @close="closeBatchProductDialog"
      @save="saveBatchProductEdit"
    />

    <SupplierDialog
      :open="supplierDialogOpen"
      :mode="supplierDialogMode"
      :form="supplierForm"
      @close="supplierDialogOpen = false"
      @save="saveSupplier"
    />

    <ManufacturerDialog
      :open="manufacturerDialogOpen"
      :mode="manufacturerDialogMode"
      :form="manufacturerForm"
      @close="manufacturerDialogOpen = false"
      @save="saveManufacturer"
    />

    <CampusDialog
      :open="campusDialogOpen"
      :mode="campusDialogMode"
      :form="campusForm"
      @close="campusDialogOpen = false"
      @save="saveCampus"
    />

    <DepartmentDialog
      :open="departmentDialogOpen"
      :mode="departmentDialogMode"
      :form="departmentForm"
      :campus-options="campusOptions"
      @close="departmentDialogOpen = false"
      @save="saveDepartment"
    />

    <DepartmentWarehouseCatalogBatchDialog
      :open="departmentWarehouseCatalogBatchDialogOpen"
      :form="departmentWarehouseCatalogBatchForm"
      :options="departmentWarehouseCatalogOptions"
      :warehouses="departmentWarehouseCatalogBatchWarehouses"
      :saving="departmentWarehouseCatalogBatchSaving"
      @close="departmentWarehouseCatalogBatchDialogOpen = false"
      @change-dept="loadDepartmentWarehouseCatalogBatchWarehouses"
      @change-warehouse="loadDepartmentWarehouseCatalogBatchProducts"
      @search-products="searchDepartmentWarehouseCatalogProducts"
      @save="saveBatchDepartmentWarehouseCatalog"
    />

    <DepartmentWarehouseCatalogDialog
      :open="departmentWarehouseCatalogDialogOpen"
      :mode="departmentWarehouseCatalogDialogMode"
      :form="departmentWarehouseCatalogForm"
      :options="departmentWarehouseCatalogOptions"
      :warehouses="departmentWarehouseCatalogWarehouses"
      @close="departmentWarehouseCatalogDialogOpen = false"
      @change-dept="loadDepartmentWarehouseCatalogWarehouses"
      @change-warehouse="loadDepartmentWarehouseCatalogProducts"
      @search-products="searchDepartmentWarehouseCatalogProducts"
      @save="saveDepartmentWarehouseCatalog"
    />

    <DepartmentWarehouseDialog
      :open="departmentWarehouseDialogOpen"
      :department="activeDepartmentForWarehouses"
      :rows="departmentWarehouseRows"
      :selected-codes="departmentWarehouseSelectedCodes"
      :loading="departmentWarehouseLoading"
      :saving="departmentWarehouseSaving"
      @close="closeDepartmentWarehouseDialog"
      @refresh="loadDepartmentWarehouses"
      @toggle="toggleDepartmentWarehouse"
      @save="saveDepartmentWarehouses"
    />

    <WarehouseDialog
      :open="warehouseDialogOpen"
      :mode="warehouseDialogMode"
      :form="warehouseForm"
      :campus-options="campusOptions"
      :department-options="departmentOptions"
      :product-options="warehouseProductOptions"
      :products-loading="warehouseProductOptionsLoading"
      @close="warehouseDialogOpen = false"
      @save="saveWarehouse"
    />

    <WarehouseLocationDialog
      :open="warehouseLocationDialogOpen"
      :warehouse="activeWarehouseForLocations"
      :rows="warehouseLocationRows"
      :form="warehouseLocationForm"
      :editing-id="warehouseLocationEditingId"
      :loading="warehouseLocationLoading"
      :saving="warehouseLocationSaving"
      :product-options="warehouseProductOptions"
      @close="closeWarehouseLocationDialog"
      @refresh="loadWarehouseLocations"
      @create="startCreateWarehouseLocation"
      @edit="startEditWarehouseLocation"
      @save="saveWarehouseLocation"
      @remove="removeWarehouseLocation"
    />

    <!-- 医院商品目录：停用确认弹窗 -->
    <SpdConfirmDialog
      v-model:open="productStatusConfirmOpen"
      :title="productStatusAction === 1 ? '启用医院目录商品' : '停用医院目录商品'"
      :tone="productStatusAction === 1 ? 'primary' : 'danger'"
      :confirm-text="productStatusAction === 1 ? '提交启用申请' : '提交停用申请'"
      :loading="productStatusConfirmLoading"
      :description="productStatusAction === 1
        ? '审批通过后，商品恢复启用。'
        : [
          '停用后该商品将不可用于新订单、新建定数包、收费消耗与领用。',
          '历史库存、流水、UDI 追溯记录仍可查询。'
        ]"
      :details="`本次申请涉及 ${productStatusConfirmCount} 个商品`"
      @confirm="confirmProductStatusChange"
      @cancel="cancelProductStatusChange"
    >
      <template #extra>
        <StatusMessage v-if="productStatusError" :message="productStatusError" tone="error" role="alert" />
      </template>
    </SpdConfirmDialog>

    <SpdConfirmDialog
      v-model:open="partnerStatusConfirmOpen"
      :title="`${partnerStatusAction}${partnerStatusKindLabel}`"
      :tone="partnerStatusConfirmTarget === 1 ? 'primary' : 'danger'"
      :confirm-text="`确认${partnerStatusAction}${partnerStatusKindLabel}`"
      :loading="partnerStatusConfirmLoading"
      :description="partnerStatusConfirmTarget === 1
        ? `启用后，该${partnerStatusKindLabel}可重新用于相关业务。`
        : partnerStatusConfirmKind === 'supplier'
          ? ['停用后该供应商将不可用于新订单、定数包与采购收货。', '历史交易、合同、付款记录仍可查询。']
          : ['停用后该厂家将不可用于新商品注册、资质证照与采购。', '历史商品关联、合同记录仍可查询。']"
      :details="`本次操作将${partnerStatusAction} ${partnerStatusConfirmCodes.length} 个${partnerStatusKindLabel}`"
      @confirm="confirmPartnerStatusChange"
      @cancel="cancelPartnerStatusChange"
    >
      <template #extra>
        <StatusMessage v-if="actionError" :message="actionError" tone="error" role="alert" />
      </template>
    </SpdConfirmDialog>

    <!-- 函数式确认弹窗 host（院区/科室/库房/目录/货位 删除等共用） -->
    <SpdConfirmHost />
  </section>
</template>

<style scoped>
.department-warehouse-catalog-list-card :deep(.el-table) {
  --el-table-border-color: #dfe5ed;
  --el-table-header-bg-color: #f3f6fa;
  --el-table-header-text-color: #34415a;
  --el-table-row-hover-bg-color: #f3f8ff;
  --el-table-current-row-bg-color: #f3f8ff;
  --el-fill-color-lighter: #f8fafc;
  overflow: visible;
  border-radius: 0;
  font-size: var(--spd-font-size-base);
}

.department-warehouse-catalog-list-card :deep(.el-table__inner-wrapper) {
  overflow: visible;
  border-radius: 0;
}

.department-warehouse-catalog-list-card :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.department-warehouse-catalog-list-card :deep(.el-table__body-wrapper) {
  box-shadow: none;
}

.department-warehouse-catalog-list-card :deep(.el-table__body-wrapper .el-scrollbar__bar.is-horizontal) {
  z-index: 8;
  height: 12px;
  bottom: 2px;
}

.department-warehouse-catalog-list-card :deep(.el-table__body-wrapper .el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
  min-width: 56px;
  background: #8aa8c7;
  opacity: 0.86;
}

.department-warehouse-catalog-list-card :deep(.el-table th.el-table__cell) {
  height: 48px;
  padding: 0 16px;
  color: #34415a;
  background: #f3f6fa;
  font-weight: 700;
}

.department-warehouse-catalog-list-card :deep(.el-table td.el-table__cell) {
  height: 60px;
  padding: 8px 16px;
  color: #34415a;
}

.department-warehouse-catalog-list-card :deep(.el-table .cell) {
  padding: 0;
  line-height: var(--spd-lh-base);
}

.department-warehouse-catalog-list-card :deep(.el-table__fixed),
.department-warehouse-catalog-list-card :deep(.el-table__fixed-right),
.department-warehouse-catalog-list-card :deep(.el-table__fixed-left) {
  height: auto !important;
  bottom: 18px !important;
  box-shadow: none;
}

.department-warehouse-catalog-list-card :deep(.el-table__fixed::before),
.department-warehouse-catalog-list-card :deep(.el-table__fixed-right::before),
.department-warehouse-catalog-list-card :deep(.el-table__fixed-left::before) {
  display: none;
  height: 0;
  border: 0;
}

/* ============================================================
   医院目录（hospital-product-catalog）独立样式
   ------------------------------------------------------------
   目标：
   1. 用 md-* 主题代替过时的 spd-* 覆盖，颜色/间距统一
   2. 解决截图 bug：双滚动条 / 高度溢出 / 拖拽背景
   3. 表格 sticky 列的 z-index 与阴影统一
   ============================================================ */
.master-page--aligned {
  padding: 16px !important;
  gap: 16px !important;
  align-content: start;
}

.master-page--aligned > .sys-master-tabs {
  width: 100%;
  margin: 0 !important;
}

/* ===== 筛选行（对齐待审批目录 .approval-filter-row 的视觉与行为） ===== */
.md-hospital-filter:not(.spd-unified-list) {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px 16px;
  padding: 16px 18px;
  background: var(--md-bg-card);
  border: 1px solid var(--md-border);
  border-radius: var(--md-radius-md);
  box-shadow: var(--md-shadow-1);
  align-items: end;
  min-width: 0;
}

/* 统一样式页面：筛选框 + 重置/查询按钮保持同一行（布局由 unified-list.css 的 flex 规则负责） */
.spd-unified-list .md-hospital-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
}

.md-hospital-filter:not(.spd-unified-list) label {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.md-hospital-filter:not(.spd-unified-list) label > span {
  color: var(--md-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.md-hospital-filter-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 统一样式页面：筛选按钮与筛选框同行，不独占一行 */
.spd-unified-list .md-hospital-filter-actions {
  align-self: end;
  white-space: nowrap;
}

@media (max-width: 1400px) {
  .md-hospital-filter:not(.spd-unified-list) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .md-hospital-filter:not(.spd-unified-list) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .md-hospital-filter:not(.spd-unified-list) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.md-column-settings {
  display: grid;
  gap: 6px;
  padding: 4px 4px 8px;
  max-height: 360px;
  overflow-y: auto;
}

.md-column-settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--md-border-soft);
}

.md-column-settings-head strong {
  color: var(--md-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.md-column-settings-head small {
  color: var(--md-text-muted);
  font-size: var(--spd-font-size-xs);
}

.md-column-settings-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
  border-top: 1px solid var(--md-border-soft);
}

.md-column-setting-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--md-radius);
  cursor: pointer;
  transition: background 0.15s ease;
}

.md-column-setting-item:hover {
  background: var(--md-primary-soft);
}

.md-column-setting-item span {
  color: var(--md-text);
  font-size: var(--spd-font-size-base);
}

/* ===== 医院目录表格 ===== */
.md-hospital-table-wrap {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  /* 垂直滚动约束由 responsive-medical.css 注入；此处只保留横向滚动样式与 sticky 表头 */
  background: var(--md-bg-card);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border) transparent;
  /* 在 md-table-card (flex column) 内允许收缩并占满剩余高度 */
  flex: 1 1 auto;
  min-height: 0;
  /* 关键：让宽表格可横向滚动而不是被裁切 */
  overflow: auto;
  position: relative;
}

.md-hospital-table-wrap::-webkit-scrollbar {
  height: 8px;
}

.md-hospital-table-wrap::-webkit-scrollbar-thumb {
  background: var(--md-border);
  border-radius: 4px;
}

.md-hospital-table-wrap::-webkit-scrollbar-thumb:hover {
  background: var(--md-border-strong);
}

.md-hospital-table {
  width: max-content;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
  color: var(--md-text);
}

.md-hospital-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  color: var(--md-text-heading);
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
  letter-spacing: 0.2px;
  padding: 10px 12px;
  border-bottom: 2px solid var(--md-border-strong);
  text-align: left;
  white-space: nowrap;
}

.md-hospital-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--md-border-soft);
  color: var(--md-text);
  white-space: nowrap;
  vertical-align: middle;
  background: var(--md-bg-card);
}

.md-hospital-table tbody tr {
  transition: background 0.12s ease;
}

.md-hospital-table tbody tr:hover td {
  background: var(--md-primary-soft);
}

.md-hospital-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted);
}

.md-hospital-table tbody tr:nth-child(even):hover td {
  background: var(--md-primary-soft);
}

/* Sticky 选择列 */
.md-hospital-sticky-left {
  position: sticky;
  left: 0;
  z-index: 1;
  background: inherit;
  width: 42px;
  min-width: 42px;
  max-width: 42px;
  text-align: center;
}

.md-hospital-table thead .md-hospital-sticky-left {
  z-index: 3;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
}

/* Sticky 操作列 */
.md-hospital-sticky-right {
  position: sticky;
  right: 0;
  z-index: 1;
  background: inherit;
  min-width: 200px;
  max-width: 200px;
  width: 200px;
  text-align: left;
  box-shadow: -8px 0 12px -10px rgba(13, 93, 74, 0.2);
}

.md-hospital-table thead .md-hospital-sticky-right {
  z-index: 3;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
}

.md-hospital-table tbody tr:hover .md-hospital-sticky-right,
.md-hospital-table tbody tr:nth-child(even):hover .md-hospital-sticky-right {
  background: var(--md-primary-soft);
}

.md-hospital-col-active {
  background: var(--md-warning-soft) !important;
  position: relative;
}

.md-hospital-col-active::after {
  content: "";
  position: absolute;
  inset: 0;
  border-left: 2px solid var(--md-primary);
  pointer-events: none;
}

.md-row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.md-row-actions .md-btn-text {
  min-height: 26px;
  padding: 0 8px;
  font-size: var(--spd-font-size-sm);
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

/* ============================================================
   供应商/厂家（partner）页面：与医院目录同款布局
   ============================================================ */
.md-partner-page {
  display: grid;
  gap: 16px;
  padding: 0;
  min-width: 0;
  max-width: 100%;
  background: var(--md-bg-page);
  color: var(--md-text);
  font-family: var(--md-font);
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-base);
}

.md-partner-page .master-table-card {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
}

/* 让 supplier / manufacturer 表格卡片化（去掉旧的 panel 边框） */
.supplier-management-panel,
.manufacturer-management-panel {
  border: 0 !important;
  background: transparent !important;
  padding: 0 !important;
  box-shadow: none !important;
}

/* 走 MdDataTableCard 的页面，把外层 .master-table-card 的旧边框去掉 */
.master-table-card-flat {
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  padding: 0 !important;
  overflow: visible !important;
  box-shadow: none !important;
}

/* 把表格包装器适配到 MdDataTableCard 内部（白底 + 内边距 + 圆角） */
.md-table-card .supplier-table-wrap,
.md-table-card .manufacturer-table-wrap,
.md-table-card .campus-table-wrap,
.md-table-card .department-table-wrap {
  position: relative;
  overflow: auto;
  background: var(--md-bg-card);
  border: 1px solid var(--md-border);
  border-radius: 0;
  scrollbar-width: thin;
  scrollbar-color: var(--md-border) transparent;
}

.md-table-card .supplier-table-wrap::-webkit-scrollbar,
.md-table-card .manufacturer-table-wrap::-webkit-scrollbar,
.md-table-card .campus-table-wrap::-webkit-scrollbar,
.md-table-card .department-table-wrap::-webkit-scrollbar {
  height: 8px;
}

.md-table-card .supplier-table-wrap::-webkit-scrollbar-thumb,
.md-table-card .manufacturer-table-wrap::-webkit-scrollbar-thumb,
.md-table-card .campus-table-wrap::-webkit-scrollbar-thumb,
.md-table-card .department-table-wrap::-webkit-scrollbar-thumb {
  background: var(--md-border);
  border-radius: 4px;
}

/* 表格行 / 表头使用主数据主题的视觉 */
.md-table-card .supplier-table thead th,
.md-table-card .manufacturer-table thead th,
.md-table-card .campus-table thead th,
.md-table-card .department-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  color: var(--md-text-heading);
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
  letter-spacing: 0.2px;
  padding: 10px 12px;
  border-bottom: 2px solid var(--md-border-strong);
  text-align: left;
  white-space: nowrap;
  box-shadow: none;
}

.md-table-card .supplier-table tbody td,
.md-table-card .manufacturer-table tbody td,
.md-table-card .campus-table tbody td,
.md-table-card .department-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--md-border-soft);
  color: var(--md-text);
  white-space: nowrap;
  vertical-align: middle;
  background: var(--md-bg-card);
}

.md-table-card .supplier-table tbody tr:nth-child(even) td,
.md-table-card .manufacturer-table tbody tr:nth-child(even) td,
.md-table-card .campus-table tbody tr:nth-child(even) td,
.md-table-card .department-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted);
}

.md-table-card .supplier-table tbody tr:hover td,
.md-table-card .manufacturer-table tbody tr:hover td,
.md-table-card .campus-table tbody tr:hover td,
.md-table-card .department-table tbody tr:hover td {
  background: var(--md-primary-soft);
}

.md-table-card .supplier-table tbody tr:nth-child(even):hover td,
.md-table-card .manufacturer-table tbody tr:nth-child(even):hover td,
.md-table-card .campus-table tbody tr:nth-child(even):hover td,
.md-table-card .department-table tbody tr:nth-child(even):hover td {
  background: var(--md-primary-soft);
}

.md-table-card .supplier-table .selection-cell,
.md-table-card .manufacturer-table .selection-cell,
.md-table-card .campus-table .selection-cell,
.md-table-card .department-table .selection-cell {
  position: sticky;
  left: 0;
  z-index: 1;
  width: 44px;
  min-width: 44px;
  background: inherit;
  text-align: center;
}

.md-table-card .supplier-table thead .selection-cell,
.md-table-card .manufacturer-table thead .selection-cell,
.md-table-card .campus-table thead .selection-cell,
.md-table-card .department-table thead .selection-cell {
  z-index: 4;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
}

.md-table-card .supplier-table .supplier-action-cell,
.md-table-card .manufacturer-table .manufacturer-action-cell,
.md-table-card .campus-table .campus-action-cell,
.md-table-card .department-table .department-action-cell {
  position: sticky;
  right: 0;
  z-index: 1;
  min-width: 126px;
  background: var(--md-bg-card);
  box-shadow: -8px 0 12px -10px rgba(13, 93, 74, 0.2);
}

.md-table-card .supplier-table thead .supplier-action-cell,
.md-table-card .manufacturer-table thead .manufacturer-action-cell,
.md-table-card .campus-table thead .campus-action-cell,
.md-table-card .department-table thead .department-action-cell {
  z-index: 4;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
}

.md-table-card .supplier-table tbody tr:hover .supplier-action-cell,
.md-table-card .manufacturer-table tbody tr:hover .manufacturer-action-cell,
.md-table-card .campus-table tbody tr:hover .campus-action-cell,
.md-table-card .department-table tbody tr:hover .department-action-cell {
  background: var(--md-primary-soft);
}

.catalog-mask {
  position: fixed;
  inset: 0;
  background: rgba(9, 45, 43, 0.45);
  z-index: 90;
}

/* 医院商品目录详情抽屉（保留原有 cat-* 主题变量，跟随系统主题色） */
.catalog-drawer {
  --cat-primary: var(--primary, var(--md-primary, #0f766e));
  --cat-deep: var(--primary-strong, var(--md-primary-strong, #0b5750));
  --cat-dark: color-mix(in srgb, var(--primary-strong, #0b5750) 88%, #06231f);
  --cat-soft: color-mix(in srgb, var(--primary, #0f766e) 9%, #ffffff);
  --cat-line: color-mix(in srgb, var(--primary, #0f766e) 32%, #ffffff);
  --cat-text: #10201f;
  --cat-text2: #3d5654;
  --cat-border: color-mix(in srgb, var(--primary, #0f766e) 18%, #e2e8e8);
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 470px;
  max-width: 94vw;
  background: #fff;
  z-index: 91;
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 32px rgba(9, 45, 43, 0.24);
  animation: catalog-slide-in 0.22s ease;
}

@keyframes catalog-slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.catalog-drawer .drawer-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 20px;
  background: linear-gradient(115deg, var(--cat-dark) 0%, var(--cat-primary) 100%);
  color: #fff;
}

.catalog-drawer .drawer-head h3 {
  margin: 0;
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  color: #fff;
}

.catalog-drawer .drawer-head p {
  margin: 4px 0 0;
  font-size: var(--spd-font-size-sm);
  color: rgba(255, 255, 255, 0.85);
  font-family: var(--spd-font-family-mono);
}

.catalog-drawer .drawer-close {
  margin-left: auto;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border: 0;
  cursor: pointer;
}

.catalog-drawer .drawer-close:hover {
  background: rgba(255, 255, 255, 0.34);
}

.catalog-drawer .drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 20px;
}

.catalog-drawer .d-section {
  margin-bottom: 18px;
}

.catalog-drawer .d-section > h5 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--cat-dark);
}

.catalog-drawer .d-section > h5::before {
  content: "";
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: var(--cat-primary);
}

.catalog-drawer .d-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
  padding: 13px 15px;
  border-radius: 8px;
  background: var(--cat-soft);
  box-shadow: inset 0 0 0 1px var(--cat-line);
}

.catalog-drawer .d-item > span {
  display: block;
  color: var(--cat-deep);
  font-size: var(--spd-font-size-sm);
  margin-bottom: 3px;
  font-weight: 600;
}

.catalog-drawer .d-item > strong {
  display: block;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--cat-text);
  word-break: break-all;
}

.catalog-drawer .d-item > strong.mono {
  font-family: var(--spd-font-family-mono);
}

.catalog-drawer .d-item.full {
  grid-column: 1 / -1;
}

.catalog-drawer .d-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.catalog-drawer .tag {
  padding: 4px 13px;
  border-radius: 999px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  background: #fff;
  color: var(--cat-text2);
  box-shadow: inset 0 0 0 1px var(--cat-border);
}

.catalog-drawer .tag.hl {
  background: var(--cat-primary);
  color: #fff;
  box-shadow: none;
}

</style>
