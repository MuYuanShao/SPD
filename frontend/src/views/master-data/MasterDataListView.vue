<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  confirmDisable: confirmDisablePartner,
  cancelDisable: cancelDisablePartner,
  disableConfirmOpen: partnerDisableConfirmOpen,
  disableConfirmLoading: partnerDisableConfirmLoading,
  disableConfirmKind: partnerDisableConfirmKind,
  disableConfirmCodes: partnerDisableConfirmCodes
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
  confirmDisableProducts,
  cancelDisableProducts,
  disableConfirmOpen,
  disableConfirmLoading,
  disableConfirmCount,
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
interface HospitalCatalogCol {
  key: 'product' | 'spec' | 'vendor' | 'price' | 'status'
  label: string
  width: number
  visible: boolean
}
const HOSPITAL_COLS_KEY = 'spd-hospital-catalog-cols-v1'
const HOSPITAL_COL_DEFAULTS: HospitalCatalogCol[] = [
  { key: 'product', label: '商品 / 编码', width: 300, visible: true },
  { key: 'spec', label: '规格型号', width: 170, visible: true },
  { key: 'vendor', label: '厂家 / 供应商', width: 250, visible: true },
  { key: 'price', label: '单价', width: 140, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true }
]
function loadHospitalCols(): HospitalCatalogCol[] {
  try {
    const raw = localStorage.getItem(HOSPITAL_COLS_KEY)
    if (!raw) return HOSPITAL_COL_DEFAULTS.map((c) => ({ ...c }))
    const saved = JSON.parse(raw) as Array<Partial<HospitalCatalogCol> & { key: HospitalCatalogCol['key'] }>
    const map = new Map(saved.map((s) => [s.key, s]))
    return HOSPITAL_COL_DEFAULTS.map((d) => ({
      key: d.key,
      label: d.label,
      width: Math.min(560, Math.max(90, Number(map.get(d.key)?.width ?? d.width) || d.width)),
      visible: map.get(d.key)?.visible !== false
    }))
  } catch {
    return HOSPITAL_COL_DEFAULTS.map((c) => ({ ...c }))
  }
}
const hospitalCols = ref<HospitalCatalogCol[]>(loadHospitalCols())
const hospitalColSettingsOpen = ref(false)
const visibleHospitalCols = computed(() => hospitalCols.value.filter((c) => c.visible))
const hospitalTableRef = ref()
function colWidth(key: HospitalCatalogCol['key']) {
  return hospitalCols.value.find((c) => c.key === key)?.width ?? 150
}
function isHospitalColVisible(key: HospitalCatalogCol['key']) {
  return hospitalCols.value.some((c) => c.key === key && c.visible)
}
function onHospitalSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String((row as Record<string, unknown>).code))
}
function onHospitalHeaderDragend(newWidth: number, _oldWidth: number, column: unknown) {
  const label = String((column as { label?: unknown }).label ?? '')
  const target = hospitalCols.value.find((c) => c.label === label)
  if (target && Number.isFinite(newWidth)) {
    target.width = Math.min(560, Math.max(90, Math.round(newWidth)))
    persistHospitalCols()
  }
}
let hospitalDragState: { key: HospitalCatalogCol['key']; startX: number; startWidth: number } | null = null
function startHospitalColDrag(key: HospitalCatalogCol['key'], ev: MouseEvent) {
  const col = hospitalCols.value.find((c) => c.key === key)
  if (!col) return
  ev.preventDefault()
  ev.stopPropagation()
  hospitalDragState = { key, startX: ev.clientX, startWidth: col.width }
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  const onMove = (e: MouseEvent) => {
    if (!hospitalDragState) return
    const delta = e.clientX - hospitalDragState.startX
    col.width = Math.min(560, Math.max(90, hospitalDragState.startWidth + delta))
    try {
      hospitalTableRef.value?.doLayout?.()
    } catch { /* 忽略 */ }
  }
  const onUp = () => {
    hospitalDragState = null
    document.body.style.userSelect = ''
    document.body.style.cursor = ''
    persistHospitalCols()
    try {
      hospitalTableRef.value?.doLayout?.()
    } catch { /* 忽略 */ }
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
function clearHospitalSelection() {
  selectedCodes.value = []
  try {
    hospitalTableRef.value?.clearSelection?.()
  } catch { /* 忽略 */ }
}
function persistHospitalCols() {
  try {
    localStorage.setItem(HOSPITAL_COLS_KEY, JSON.stringify(hospitalCols.value))
  } catch { /* 忽略持久化失败 */ }
}
function resetHospitalCols() {
  hospitalCols.value = HOSPITAL_COL_DEFAULTS.map((c) => ({ ...c }))
  persistHospitalCols()
}
function hospitalInitial(value: unknown) {
  const text = String(value ?? '').trim()
  return text ? text.slice(0, 1) : '商'
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
  { key: 'code', label: '科室编码', width: 150, visible: true },
  { key: 'name', label: '科室名称', width: 200, visible: true },
  { key: 'org', label: '组织归属', width: 220, visible: true },
  { key: 'finance', label: '财务科室映射', width: 200, visible: true },
  { key: 'manager', label: '负责人', width: 180, visible: true },
  { key: 'address', label: '科室地址', width: 220, visible: true },
  { key: 'warehouse', label: '关联库房', width: 180, visible: true },
  { key: 'source', label: '原数据标识', width: 160, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true },
  { key: 'updateTime', label: '修改时间', width: 190, visible: true }
]

const CATALOG_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'scope', label: '科室 / 库房', width: 230, visible: true },
  { key: 'productCode', label: '商品编码', width: 160, visible: true },
  { key: 'productName', label: '商品名称', width: 200, visible: true },
  { key: 'spec', label: '规格型号', width: 160, visible: true },
  { key: 'manufacturer', label: '生产厂家', width: 200, visible: true },
  { key: 'source', label: '维护来源', width: 130, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true },
  { key: 'updateTime', label: '更新时间', width: 190, visible: true }
]

const WAREHOUSE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '库房编码', width: 150, visible: true },
  { key: 'name', label: '库房名称', width: 190, visible: true },
  { key: 'type', label: '库房类型', width: 140, visible: true },
  { key: 'campus', label: '所属院区', width: 170, visible: true },
  { key: 'dept', label: '关联科室', width: 190, visible: true },
  { key: 'participateStats', label: '统计', width: 120, visible: true },
  { key: 'receivingEnabled', label: '允许收货', width: 130, visible: true },
  { key: 'statsCategories', label: '统计分类', width: 170, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true },
  { key: 'updateTime', label: '修改时间', width: 190, visible: true },
  { key: 'locationCount', label: '货位数量', width: 120, visible: true }
]

const CAMPUS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '院区编码', width: 150, visible: true },
  { key: 'name', label: '院区名称', width: 190, visible: true },
  { key: 'address', label: '院区地址', width: 240, visible: true },
  { key: 'manager', label: '负责人', width: 170, visible: true },
  { key: 'sortOrder', label: '排序', width: 110, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true },
  { key: 'updateTime', label: '修改时间', width: 190, visible: true }
]

const SUPPLIER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '供应商编码', width: 150, visible: true },
  { key: 'name', label: '供应商名称', width: 200, visible: true },
  { key: 'creditCode', label: '统一社会信用代码', width: 190, visible: true },
  { key: 'businessLicenseNo', label: '经营许可证号', width: 170, visible: true },
  { key: 'type', label: '类型', width: 130, visible: true },
  { key: 'grade', label: '等级', width: 110, visible: true },
  { key: 'contact', label: '联系人', width: 170, visible: true },
  { key: 'email', label: '邮箱', width: 190, visible: true },
  { key: 'address', label: '地址', width: 220, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true }
]

const MANUFACTURER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'code', label: '厂家编码', width: 150, visible: true },
  { key: 'name', label: '厂家名称', width: 200, visible: true },
  { key: 'creditCode', label: '统一社会信用代码', width: 190, visible: true },
  { key: 'licenseNo', label: '生产许可证号', width: 180, visible: true },
  { key: 'contact', label: '联系人', width: 170, visible: true },
  { key: 'address', label: '地址', width: 240, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true }
]

const departmentCols = useListColumns({
  storageKey: 'spd-department-cols-v1',
  defaults: DEPARTMENT_COL_DEFAULTS
})
const catalogCols = useListColumns({
  storageKey: 'spd-department-warehouse-catalog-cols-v1',
  defaults: CATALOG_COL_DEFAULTS
})
const warehouseCols = useListColumns({
  storageKey: 'spd-warehouse-location-cols-v1',
  defaults: WAREHOUSE_COL_DEFAULTS
})
const campusCols = useListColumns({
  storageKey: 'spd-campus-cols-v1',
  defaults: CAMPUS_COL_DEFAULTS
})
const supplierCols = useListColumns({
  storageKey: 'spd-supplier-cols-v1',
  defaults: SUPPLIER_COL_DEFAULTS
})
const manufacturerCols = useListColumns({
  storageKey: 'spd-manufacturer-cols-v1',
  defaults: MANUFACTURER_COL_DEFAULTS
})

const departmentTableRef = departmentCols.tableRef
const catalogTableRef = catalogCols.tableRef
const warehouseTableRef = warehouseCols.tableRef
const campusTableRef = campusCols.tableRef
const supplierTableRef = supplierCols.tableRef
const manufacturerTableRef = manufacturerCols.tableRef

const isDepartmentColVisible = (key: string) => departmentCols.isColumnVisible(key)
const departmentColWidth = (key: string) => departmentCols.columnWidth(key)
const startDepartmentColDrag = (key: string, event: MouseEvent) => departmentCols.startColumnDrag(key, event)

const isCatalogColVisible = (key: string) => catalogCols.isColumnVisible(key)
const catalogColWidth = (key: string) => catalogCols.columnWidth(key)
const startCatalogColDrag = (key: string, event: MouseEvent) => catalogCols.startColumnDrag(key, event)

const isWarehouseColVisible = (key: string) => warehouseCols.isColumnVisible(key)
const warehouseColWidth = (key: string) => warehouseCols.columnWidth(key)
const startWarehouseColDrag = (key: string, event: MouseEvent) => warehouseCols.startColumnDrag(key, event)

const isCampusColVisible = (key: string) => campusCols.isColumnVisible(key)
const campusColWidth = (key: string) => campusCols.columnWidth(key)
const startCampusColDrag = (key: string, event: MouseEvent) => campusCols.startColumnDrag(key, event)

const isSupplierColVisible = (key: string) => supplierCols.isColumnVisible(key)
const supplierColWidth = (key: string) => supplierCols.columnWidth(key)
const startSupplierColDrag = (key: string, event: MouseEvent) => supplierCols.startColumnDrag(key, event)

const isManufacturerColVisible = (key: string) => manufacturerCols.isColumnVisible(key)
const manufacturerColWidth = (key: string) => manufacturerCols.columnWidth(key)
const startManufacturerColDrag = (key: string, event: MouseEvent) => manufacturerCols.startColumnDrag(key, event)

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

</script>

<template>
  <section
    class="master-page"
    :class="{
      'master-page--hospital': isHospitalCatalog,
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

    <section v-if="isHospitalCatalog" class="md-hospital-page catalog-redesign">
      <MdPageHeader
        title="医院商品目录"
        description="维护本院正式可用商品，次要字段收进详情抽屉。"
        :loading="loading"
        @refresh="loadPage"
      />

      <form class="md-hospital-filter catalog-filter" @submit.prevent="submitHospitalQuery">
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
          <select v-model="hospitalQuery.status" class="md-select" aria-label="商品状态">
            <option value="">全部状态</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
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
            <select v-model="hospitalQuery.isCentralized" class="md-select">
              <option value="">全部</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
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
        <div class="filter-actions">
          <button type="button" class="cat-btn cat-btn-more" @click="hospitalShowMore = !hospitalShowMore">
            {{ hospitalShowMore ? '收起' : '更多' }}
            <ChevronDown :size="14" :class="{ 'rotated': hospitalShowMore }" />
          </button>
          <button type="button" class="cat-btn cat-btn-reset" @click="resetHospitalQuery">
            <RefreshCw :size="14" />
            <span>重置</span>
          </button>
          <button type="submit" class="cat-btn cat-btn-query">
            <Search :size="14" />
            <span>查询</span>
          </button>
        </div>
      </form>

      <div class="catalog-panel">
        <div class="toolbar">
          <div class="toolbar-main">
            <button
              v-if="authStore.canWrite(code)"
              type="button"
              class="cat-btn cat-btn-add"
              @click="openCreateProduct"
            >
              <Plus :size="15" />
              <span>新增</span>
            </button>
            <button
              v-if="authStore.canWrite(code)"
              type="button"
              class="cat-btn cat-btn-edit"
              :disabled="selectedCodes.length !== 1"
              @click="openEditProduct()"
            >
              <Pencil :size="14" />
              <span>修改</span>
            </button>
            <button
              v-if="authStore.canWrite(code)"
              type="button"
              class="cat-btn cat-btn-batch"
              :disabled="!selectedCodes.length"
              @click="batchEditSelectedProducts"
            >
              <Edit3 :size="14" />
              <span>批量修改</span>
            </button>
            <button
              v-if="authStore.canWrite(code)"
              type="button"
              class="cat-btn cat-btn-stop"
              :disabled="!selectedCodes.length"
              @click="disableProducts()"
            >
              <Ban :size="14" />
              <span>停用</span>
            </button>
            <button type="button" class="cat-btn cat-btn-export" @click="exportHospitalCatalog">
              <Download :size="14" />
              <span>导出</span>
            </button>
            <div class="col-setting-wrap">
              <button type="button" class="cat-btn cat-btn-cols" @click="hospitalColSettingsOpen = !hospitalColSettingsOpen">
                <Columns3 :size="14" />
                <span>列设置</span>
                <ChevronDown :size="13" :class="{ rotated: hospitalColSettingsOpen }" />
              </button>
              <div v-if="hospitalColSettingsOpen" class="col-setting-pop">
                <header>
                  <strong>列展示设置</strong>
                  <button type="button" class="link" @click="resetHospitalCols">恢复默认</button>
                </header>
                <label v-for="col in hospitalCols" :key="col.key" class="col-setting-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && visibleHospitalCols.length <= 1"
                    @change="col.visible = ($event.target as HTMLInputElement).checked; persistHospitalCols()"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
                <footer>
                  <button type="button" class="cat-btn cat-btn-query cat-btn-sm" @click="hospitalColSettingsOpen = false">确定</button>
                </footer>
              </div>
            </div>
          </div>
          <div class="toolbar-info">
            <span class="chip is-primary">共 {{ hospitalTotal }} 条</span>
            <span v-if="selectedCodes.length" class="chip is-green">已选 {{ selectedCodes.length }}</span>
          </div>
        </div>

        <div v-if="selectedCodes.length" class="batch on">
          <b>已选择 {{ selectedCodes.length }} 项</b>
          <span class="spacer"></span>
          <button type="button" class="cat-btn cat-btn-edit" :disabled="selectedCodes.length !== 1" @click="openEditProduct()">修改</button>
          <button type="button" class="cat-btn cat-btn-stop" @click="disableProducts()">批量停用</button>
          <button type="button" class="cat-btn cat-btn-reset" @click="clearHospitalSelection">取消选择</button>
        </div>

        <MdEmptyState v-if="error" :text="error" />
        <div v-else class="hospital-table-fill">
          <el-table
            ref="hospitalTableRef"
            v-loading="loading"
            :data="page?.rows ?? []"
            border
            stripe
            highlight-current-row
            style="width: 100%"
            class="hospital-el-table"
            :row-key="(row: Record<string, unknown>) => String(row.code)"
            :header-cell-style="{ background: 'var(--cat-primary)', color: '#fff', fontWeight: 700 }"
            empty-text="暂无医院目录数据"
            @selection-change="onHospitalSelectionChange"
            @header-dragend="onHospitalHeaderDragend"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isHospitalColVisible('product')"
              label="商品 / 编码"
              :min-width="colWidth('product')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="hospital-th-text">商品 / 编码</span><i class="hospital-col-grip" title="拖动调节列宽" @mousedown="startHospitalColDrag('product', $event)" />
              </template>
              <template #default="{ row }">
                <div class="tpl-cell">
                  <div class="tpl-badge">{{ hospitalInitial((row as Record<string, unknown>).name) }}</div>
                  <div class="tpl-txt">
                    <div class="tpl-name" :title="String((row as Record<string, unknown>).name)">{{ (row as Record<string, unknown>).name ?? '-' }}</div>
                    <div class="tpl-meta"><code>{{ (row as Record<string, unknown>).code }}</code><span v-for="tag in hospitalAttrTags(row as Record<string, unknown>)" :key="tag.text" class="mini-tag" :class="{ hot: tag.hot }">{{ tag.text }}</span></div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isHospitalColVisible('spec')"
              label="规格型号"
              prop="spec"
              :min-width="colWidth('spec')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="hospital-th-text">规格型号</span><i class="hospital-col-grip" title="拖动调节列宽" @mousedown="startHospitalColDrag('spec', $event)" />
              </template>
            </el-table-column>
            <el-table-column
              v-if="isHospitalColVisible('vendor')"
              label="厂家 / 供应商"
              :min-width="colWidth('vendor')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="hospital-th-text">厂家 / 供应商</span><i class="hospital-col-grip" title="拖动调节列宽" @mousedown="startHospitalColDrag('vendor', $event)" />
              </template>
              <template #default="{ row }">
                <div class="vendor-cell">
                  <strong :title="String((row as Record<string, unknown>).manufacturer ?? '-')">{{ (row as Record<string, unknown>).manufacturer ?? '-' }}</strong>
                  <span :title="String((row as Record<string, unknown>).supplier ?? '-')">{{ (row as Record<string, unknown>).supplier ?? '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isHospitalColVisible('price')"
              label="单价"
              :min-width="colWidth('price')"
              align="right"
            >
              <template #header>
                <span class="hospital-th-text">单价</span><i class="hospital-col-grip" title="拖动调节列宽" @mousedown="startHospitalColDrag('price', $event)" />
              </template>
              <template #default="{ row }">
                <span class="qty"><b>{{ (row as Record<string, unknown>).price ?? '-' }}</b><span>{{ String((row as Record<string, unknown>).unit ?? '') }}</span></span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isHospitalColVisible('status')"
              label="状态"
              :min-width="colWidth('status')"
              align="center"
            >
              <template #header>
                <span class="hospital-th-text">状态</span><i class="hospital-col-grip" title="拖动调节列宽" @mousedown="startHospitalColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="status" :class="String((row as Record<string, unknown>).status) === '启用' ? 'on' : 'off'"><i></i>{{ (row as Record<string, unknown>).status ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" align="center" fixed="right">
              <template #default="{ row }">
                <div class="row-act">
                  <button type="button" class="act act-detail" @click="openHospitalDetail(String((row as Record<string, unknown>).code))">详情</button>
                  <RouterLink class="act act-pack" :to="{ name: 'hospital-product-detail', params: { productCode: String((row as Record<string, unknown>).code) } }">查看</RouterLink>
                  <button v-if="authStore.canWrite(code)" type="button" class="act act-edit" @click="openEditProduct(String((row as Record<string, unknown>).code), false)">修改</button>
                  <button v-if="authStore.canWrite(code)" type="button" class="act act-stop" @click="disableProducts([String((row as Record<string, unknown>).code)])">停用</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无医院目录数据" hint="尝试调整筛选条件或新增商品" />
            </template>
          </el-table>
        </div>

        <div class="pager">
          <MdPagination
            :page="hospitalCurrentPage"
            :total-pages="hospitalPageTotal"
            :total="hospitalTotal"
            :size="hospitalPageSize"
            :loading="loading"
            @update:page="loadHospitalPage"
            @update:size="changeHospitalPageSize"
          />
        </div>
      </div>

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
        description="维护供应商档案，统一管理名称、类型、联系人、信用代码与启停状态。"
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
        description="维护生产厂家档案，统一管理名称、生产许可证号、联系人与启停状态。"
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
        description="维护医院院区档案，统一管理编码、名称、地址与启停状态。"
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
        description="维护医院科室档案，统一管理编码、类型、关联库房与状态。"
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
        description="维护科室与库房的商品目录关系，按科室、库房、商品、状态快速检索。"
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
        description="维护医院库房与货位档案，按库房名称、编码、状态快速检索。"
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
            <button type="button" class="md-btn md-btn-primary" @click="supplierCols.settingsOpen.value = !supplierCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: supplierCols.settingsOpen.value }" />
            </button>
            <div v-if="supplierCols.settingsOpen.value" class="ulc-col-settings-pop">
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
            class="ulc-table"
            :data="page.rows"
            row-key="code"
            border
            stripe
            style="width: 100%"
            empty-text="暂无符合条件的供应商"
            @selection-change="onSupplierSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isSupplierColVisible('code')"
              label="供应商编码"
              :min-width="supplierColWidth('code')"
            >
              <template #header>
                <span class="ulc-th-text">供应商编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('code', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('name')"
              label="供应商名称"
              :min-width="supplierColWidth('name')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">供应商名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('name', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('creditCode')"
              label="统一社会信用代码"
              :min-width="supplierColWidth('creditCode')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">统一社会信用代码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('creditCode', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.creditCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('businessLicenseNo')"
              label="经营许可证号"
              :min-width="supplierColWidth('businessLicenseNo')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">经营许可证号</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('businessLicenseNo', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.businessLicenseNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('type')"
              label="类型"
              :min-width="supplierColWidth('type')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">类型</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('type', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.type || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('grade')"
              label="等级"
              :min-width="supplierColWidth('grade')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">等级</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('grade', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="row.grade && row.grade !== '-' ? 'is-primary' : 'is-neutral'">
                  {{ row.grade || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('contact')"
              label="联系人"
              :min-width="supplierColWidth('contact')"
            >
              <template #header>
                <span class="ulc-th-text">联系人</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('contact', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.contactName || '-' }}</span>
                  <span class="ulc-sub">{{ row.contactPhone || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('email')"
              label="邮箱"
              :min-width="supplierColWidth('email')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">邮箱</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('email', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.email || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('address')"
              label="地址"
              :min-width="supplierColWidth('address')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">地址</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('address', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isSupplierColVisible('status')"
              label="状态"
              :min-width="supplierColWidth('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startSupplierColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" :min-width="220" align="center" fixed="right">
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
                    class="ulc-action is-danger"
                    @click="disableSuppliers([String(row.code)])"
                  >
                    停用
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
            <button type="button" class="md-btn md-btn-primary" @click="manufacturerCols.settingsOpen.value = !manufacturerCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: manufacturerCols.settingsOpen.value }" />
            </button>
            <div v-if="manufacturerCols.settingsOpen.value" class="ulc-col-settings-pop">
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
            class="ulc-table"
            :data="page.rows"
            row-key="code"
            border
            stripe
            style="width: 100%"
            empty-text="暂无符合条件的厂家"
            @selection-change="onManufacturerSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isManufacturerColVisible('code')"
              label="厂家编码"
              :min-width="manufacturerColWidth('code')"
            >
              <template #header>
                <span class="ulc-th-text">厂家编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('code', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('name')"
              label="厂家名称"
              :min-width="manufacturerColWidth('name')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">厂家名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('name', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('creditCode')"
              label="统一社会信用代码"
              :min-width="manufacturerColWidth('creditCode')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">统一社会信用代码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('creditCode', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.creditCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('licenseNo')"
              label="生产许可证号"
              :min-width="manufacturerColWidth('licenseNo')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">生产许可证号</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('licenseNo', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.licenseNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('contact')"
              label="联系人"
              :min-width="manufacturerColWidth('contact')"
            >
              <template #header>
                <span class="ulc-th-text">联系人</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('contact', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.contactName || '-' }}</span>
                  <span class="ulc-sub">{{ row.contactPhone || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('address')"
              label="地址"
              :min-width="manufacturerColWidth('address')"
              show-overflow-to-toololtip
            >
              <template #header>
                <span class="ulc-th-text">地址</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('address', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isManufacturerColVisible('status')"
              label="状态"
              :min-width="manufacturerColWidth('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startManufacturerColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" :min-width="220" align="center" fixed="right">
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
                    class="ulc-action is-danger"
                    @click="disableManufacturers([String(row.code)])"
                  >
                    停用
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
            <button type="button" class="md-btn md-btn-primary" @click="campusCols.settingsOpen.value = !campusCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: campusCols.settingsOpen.value }" />
            </button>
            <div v-if="campusCols.settingsOpen.value" class="ulc-col-settings-pop">
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
            class="ulc-table"
            :data="page.rows"
            row-key="code"
            border
            stripe
            style="width: 100%"
            empty-text="暂无符合条件的院区"
            @selection-change="onCampusSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isCampusColVisible('code')"
              label="院区编码"
              :min-width="campusColWidth('code')"
            >
              <template #header>
                <span class="ulc-th-text">院区编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('code', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('name')"
              label="院区名称"
              :min-width="campusColWidth('name')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">院区名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('name', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.name || '-' }}</span>
                  <span class="ulc-sub">组织基础档案</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('address')"
              label="院区地址"
              :min-width="campusColWidth('address')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">院区地址</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('address', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('manager')"
              label="负责人"
              :min-width="campusColWidth('manager')"
            >
              <template #header>
                <span class="ulc-th-text">负责人</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('manager', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.manager || '-' }}</span>
                  <span class="ulc-sub">{{ row.phone || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('sortOrder')"
              label="排序"
              :min-width="campusColWidth('sortOrder')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">排序</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('sortOrder', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.sortOrder ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('status')"
              label="状态"
              :min-width="campusColWidth('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isCampusColVisible('updateTime')"
              label="修改时间"
              :min-width="campusColWidth('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCampusColDrag('updateTime', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" :min-width="220" align="center" fixed="right">
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
            <button type="button" class="md-btn md-btn-primary" @click="departmentCols.settingsOpen.value = !departmentCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: departmentCols.settingsOpen.value }" />
            </button>
            <div v-if="departmentCols.settingsOpen.value" class="ulc-col-settings-pop">
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
            class="ulc-table"
            :data="page.rows"
            row-key="code"
            border
            stripe
            style="width: 100%"
            empty-text="暂无符合条件的科室"
            @selection-change="onDepartmentSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isDepartmentColVisible('code')"
              label="科室编码"
              :min-width="departmentColWidth('code')"
            >
              <template #header>
                <span class="ulc-th-text">科室编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('code', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('name')"
              label="科室名称"
              :min-width="departmentColWidth('name')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">科室名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('name', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.name }}</span>
                  <span class="ulc-sub">{{ row.deptAttribute || '科室档案' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('org')"
              label="组织归属"
              :min-width="departmentColWidth('org')"
            >
              <template #header>
                <span class="ulc-th-text">组织归属</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('org', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.campus || '未归属院区' }}</span>
                  <div class="ulc-inline-tags">
                    <span class="ulc-tag">{{ row.deptType || '-' }}</span>
                    <span class="ulc-tag">{{ row.deptAttribute || '-' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('finance')"
              label="财务科室映射"
              :min-width="departmentColWidth('finance')"
            >
              <template #header>
                <span class="ulc-th-text">财务科室映射</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('finance', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.financeDept || '-' }}</span>
                  <span class="ulc-sub ulc-mono">{{ row.financeDeptCode || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('manager')"
              label="负责人"
              :min-width="departmentColWidth('manager')"
            >
              <template #header>
                <span class="ulc-th-text">负责人</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('manager', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.manager || '-' }}</span>
                  <span class="ulc-sub">{{ row.phone || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('address')"
              label="科室地址"
              :min-width="departmentColWidth('address')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">科室地址</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('address', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.address || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('warehouse')"
              label="关联库房"
              :min-width="departmentColWidth('warehouse')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">关联库房</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('warehouse', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.relatedWarehouse || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('source')"
              label="原数据标识"
              :min-width="departmentColWidth('source')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">原数据标识</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('source', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.sourceFlag || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('status')"
              label="状态"
              :min-width="departmentColWidth('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="String(row.status) === '正常' ? 'is-success' : 'is-danger'">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isDepartmentColVisible('updateTime')"
              label="修改时间"
              :min-width="departmentColWidth('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startDepartmentColDrag('updateTime', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" :min-width="260" align="center" fixed="right">
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
            <button type="button" class="md-btn md-btn-primary" @click="catalogCols.settingsOpen.value = !catalogCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: catalogCols.settingsOpen.value }" />
            </button>
            <div v-if="catalogCols.settingsOpen.value" class="ulc-col-settings-pop">
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
          class="ulc-table"
          :data="page.rows"
          row-key="code"
          border
          stripe
          style="width: 100%"
          empty-text="暂无符合条件的科室库房目录"
          @selection-change="onCatalogSelectionChange"
        >
          <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
          <el-table-column
            v-if="isCatalogColVisible('scope')"
            label="科室 / 库房"
            :min-width="catalogColWidth('scope')"
          >
            <template #header>
              <span class="ulc-th-text">科室 / 库房</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('scope', $event)" />
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong">{{ row.deptName || '-' }}</span>
                <span class="ulc-sub">{{ row.warehouseName ? `库房名称：${row.warehouseName}` : '未关联库房' }}</span>
                <span class="ulc-sub">库房等级：{{ row.warehouseType || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('productCode')"
            label="商品编码"
            :min-width="catalogColWidth('productCode')"
          >
            <template #header>
              <span class="ulc-th-text">商品编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('productCode', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-mono">{{ row.productCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('productName')"
            label="商品名称"
            :min-width="catalogColWidth('productName')"
            show-overflow-tooltip
          >
            <template #header>
              <span class="ulc-th-text">商品名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('productName', $event)" />
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong">{{ row.productName || '-' }}</span>
                <span class="ulc-sub">科室可申领商品</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('spec')"
            label="规格型号"
            :min-width="catalogColWidth('spec')"
            show-overflow-tooltip
          >
            <template #header>
              <span class="ulc-th-text">规格型号</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('spec', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-strong">{{ row.specModel || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('manufacturer')"
            label="生产厂家"
            :min-width="catalogColWidth('manufacturer')"
            show-overflow-tooltip
          >
            <template #header>
              <span class="ulc-th-text">生产厂家</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('manufacturer', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-strong">{{ row.manufacturerName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('source')"
            label="维护来源"
            :min-width="catalogColWidth('source')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">维护来源</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('source', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-chip" :class="catalogSourceChipClass(row.sourceType)">
                {{ formatBusinessText(row.sourceType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('status')"
            label="状态"
            :min-width="catalogColWidth('status')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('status', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-chip" :class="String(row.status) === '启用' ? 'is-success' : 'is-danger'">
                {{ formatStatusText(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isCatalogColVisible('updateTime')"
            label="更新时间"
            :min-width="catalogColWidth('updateTime')"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">更新时间</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startCatalogColDrag('updateTime', $event)" />
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" :min-width="260" align="center" fixed="right">
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
            <button type="button" class="md-btn md-btn-primary" @click="warehouseCols.settingsOpen.value = !warehouseCols.settingsOpen.value">
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: warehouseCols.settingsOpen.value }" />
            </button>
            <div v-if="warehouseCols.settingsOpen.value" class="ulc-col-settings-pop">
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
            class="ulc-table"
            :data="page.rows"
            row-key="code"
            border
            stripe
            style="width: 100%"
            empty-text="暂无符合条件的库房"
            @selection-change="onWarehouseSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
            <el-table-column
              v-if="isWarehouseColVisible('code')"
              label="库房编码"
              :min-width="warehouseColWidth('code')"
            >
              <template #header>
                <span class="ulc-th-text">库房编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('code', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-mono">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('name')"
              label="库房名称"
              :min-width="warehouseColWidth('name')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">库房名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('name', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.name || '-' }}</span>
                  <span v-if="row.needsDepartmentFix" class="ulc-chip is-warning">需补科室</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('type')"
              label="库房类型"
              :min-width="warehouseColWidth('type')"
            >
              <template #header>
                <span class="ulc-th-text">库房类型</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('type', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ row.type || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('campus')"
              label="所属院区"
              :min-width="warehouseColWidth('campus')"
            >
              <template #header>
                <span class="ulc-th-text">所属院区</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('campus', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.campus || '未归属院区' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('dept')"
              label="关联科室"
              :min-width="warehouseColWidth('dept')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">关联科室</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('dept', $event)" />
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong">{{ row.dept || '-' }}</span>
                  <span v-if="row.deptCode" class="ulc-sub ulc-mono">{{ row.deptCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('participateStats')"
              label="统计"
              :min-width="warehouseColWidth('participateStats')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">统计</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('participateStats', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="warehouseFlagChipClass(row.participateStats === '参与')">
                  {{ row.participateStats || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('receivingEnabled')"
              label="允许收货"
              :min-width="warehouseColWidth('receivingEnabled')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">允许收货</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('receivingEnabled', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="warehouseFlagChipClass(row.receivingEnabled)">
                  {{ row.receivingEnabled ? '允许' : '不允许' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('statsCategories')"
              label="统计分类"
              :min-width="warehouseColWidth('statsCategories')"
              show-overflow-tooltip
            >
              <template #header>
                <span class="ulc-th-text">统计分类</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('statsCategories', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.statsCategories || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('status')"
              label="状态"
              :min-width="warehouseColWidth('status')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('status', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-chip" :class="statusChipClass(row.status)">
                  {{ formatStatusText(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('updateTime')"
              label="修改时间"
              :min-width="warehouseColWidth('updateTime')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('updateTime', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatListDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="isWarehouseColVisible('locationCount')"
              label="货位数量"
              :min-width="warehouseColWidth('locationCount')"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">货位数量</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startWarehouseColDrag('locationCount', $event)" />
              </template>
              <template #default="{ row }">
                <span class="ulc-strong">{{ row.locationCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" :min-width="260" align="center" fixed="right">
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

      <!-- Other pages: native table -->
      <table v-else-if="page" class="master-table">
        <thead>
          <tr>
            <th v-if="selectableRows" class="selection-cell">
              <input type="checkbox" :checked="allVisibleSelected" @change="toggleAllRows" />
            </th>
            <th v-for="column in page?.columns" :key="column">{{ column }}</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in page?.rows" :key="rowIndex">
            <td v-if="selectableRows" class="selection-cell">
              <input v-model="selectedCodes" type="checkbox" :value="String(row.code)" />
            </td>
            <td v-for="key in rowKeys" :key="key">{{ row[key] }}</td>
            <td>
              <RouterLink
                v-if="code === 'hospital-product-catalog'"
                class="btn-text"
                :to="{ name: 'hospital-product-detail', params: { productCode: String(row.code) } }"
              >
                查看
              </RouterLink>
              <button
                v-if="code === 'hospital-product-catalog'"
                type="button"
                class="btn-text"
                @click="openEditProduct(String(row.code), false)"
              >
                修改
              </button>
              <button
                v-if="code === 'hospital-product-catalog'"
                type="button"
                class="btn-text btn-text-danger"
                @click="disableProducts([String(row.code)])"
              >
                停用
              </button>
              <template v-else-if="code === 'supplier-management'">
                <button type="button" class="btn-text" @click="openEditSupplier(row, false)">修改</button>
                <button type="button" class="btn-text btn-text-danger" @click="disableSuppliers([String(row.code)])">
                  停用
                </button>
              </template>
              <template v-else-if="code === 'manufacturer-management'">
                <button type="button" class="btn-text" @click="openEditManufacturer(row, false)">修改</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="disableManufacturers([String(row.code)])"
                >
                  停用
                </button>
              </template>
              <template v-else-if="code === 'campus-management'">
                <button type="button" class="btn-text" @click="openEditCampus(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedCampuses([String(row.code)])"
                >
                  删除
                </button>
              </template>
              <template v-else-if="code === 'department-management'">
                <button type="button" class="btn-text" @click="openEditDepartment(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedDepartments([String(row.code)])"
                >
                  删除
                </button>
              </template>
              <template v-else-if="code === 'department-warehouse-catalog'">
                <button type="button" class="btn-text" @click="openEditDepartmentWarehouseCatalog(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text"
                  @click="updateSelectedDepartmentWarehouseCatalogStatus(String(row.status) === '停用' ? 1 : 0, [String(row.code)])"
                >
                  {{ String(row.status) === '停用' ? '启用' : '停用' }}
                </button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedDepartmentWarehouseCatalogs([String(row.code)])"
                >
                  删除
                </button>
              </template>
              <button v-else type="button" class="btn-text">查看</button>
            </td>
          </tr>
          <tr v-if="!page?.rows.length">
            <td class="approval-empty" :colspan="(page?.columns.length || 0) + (selectableRows ? 2 : 1)">暂无数据</td>
          </tr>
        </tbody>
      </table>
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
      v-model:open="disableConfirmOpen"
      title="停用医院目录商品"
      tone="danger"
      confirm-text="确认停用"
      :loading="disableConfirmLoading"
      :description="[
        '停用后该商品将不可用于新订单、新建定数包、收费消耗与领用。',
        '历史库存、流水、UDI 追溯记录不会被删除，仍可查询。'
      ]"
      :details="[
        `本次操作将停用 ${disableConfirmCount} 个商品`,
        '如需重新启用，请联系目录管理员'
      ]"
      @confirm="confirmDisableProducts"
      @cancel="cancelDisableProducts"
    />

    <!-- 供应商 / 厂家：停用确认弹窗 -->
    <SpdConfirmDialog
      v-model:open="partnerDisableConfirmOpen"
      :title="partnerDisableConfirmKind === 'supplier' ? '停用供应商' : '停用生产厂家'"
      tone="danger"
      :confirm-text="partnerDisableConfirmKind === 'supplier' ? '确认停用供应商' : '确认停用厂家'"
      :loading="partnerDisableConfirmLoading"
      :description="partnerDisableConfirmKind === 'supplier'
        ? [
            '停用后该供应商将不可用于新订单、定数包与采购收货。',
            '历史交易、合同、付款记录仍可查询。'
          ]
        : [
            '停用后该厂家将不可用于新商品注册、资质证照与采购。',
            '历史商品关联、合同记录仍可查询。'
          ]"
      :details="[
        `本次操作将停用 ${partnerDisableConfirmCodes.length} 个${partnerDisableConfirmKind === 'supplier' ? '供应商' : '厂家'}`,
        '如需重新启用，请联系主数据管理员'
      ]"
      @confirm="confirmDisablePartner"
      @cancel="cancelDisablePartner"
    />

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
  font-size: 13px;
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
  line-height: 1.5;
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

.md-hospital-page {
  display: grid;
  gap: 16px;
  padding: 0;
  min-width: 0;
  max-width: 100%;
  background: var(--md-bg-page);
  color: var(--md-text);
  font-family: var(--md-font);
  font-size: 14px;
  line-height: 1.5;
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
  font-size: 12px;
  font-weight: 500;
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
  font-size: 13px;
  font-weight: 600;
}

.md-column-settings-head small {
  color: var(--md-text-muted);
  font-size: 12px;
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
  font-size: 13px;
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
  font-size: 13px;
  color: var(--md-text);
}

.md-hospital-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  color: var(--md-text-heading);
  font-weight: 600;
  font-size: 12px;
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
  font-size: 12px;
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
  font-size: 14px;
  line-height: 1.5;
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
  font-weight: 600;
  font-size: 12px;
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

/* ============================================================
   医院商品目录精简版（对齐定数包模板维护新版 mockup）
   主题深青 #0f766e / 白底行 / 表头主题色 / 圆角实色按钮
   ============================================================ */
.catalog-redesign {
  /* 跟随系统主题：默认墨绿，切换主题时自动跟随 --primary */
  --cat-primary: var(--primary, var(--md-primary, #0f766e));
  --cat-deep: var(--primary-strong, var(--md-primary-strong, #0b5750));
  --cat-dark: color-mix(in srgb, var(--primary-strong, #0b5750) 88%, #06231f);
  --cat-mid: color-mix(in srgb, var(--primary, #0f766e) 82%, #ffffff);
  --cat-soft: color-mix(in srgb, var(--primary, #0f766e) 9%, #ffffff);
  --cat-soft2: color-mix(in srgb, var(--primary, #0f766e) 15%, #ffffff);
  --cat-line: color-mix(in srgb, var(--primary, #0f766e) 32%, #ffffff);
  --cat-text: #10201f;
  --cat-text2: #3d5654;
  --cat-text3: #4e6361;
  --cat-card: #ffffff;
  --cat-border: color-mix(in srgb, var(--primary, #0f766e) 18%, #e2e8e8);
  --cat-border-soft: #eaf0ef;
  display: grid;
  gap: 14px;
}

/* 医院商品目录筛选区。
   注意：`.md-hospital-filter:not(.spd-unified-list)`（特异性 0,3,0）会覆盖 display，
   所以这里用同等特异性 + 后置顺序接管该容器的布局。

   布局用 grid：前 4 列为筛选字段（1fr），第 5 列用 max-content 专门放操作按钮组，
   保证「更多 / 重置 / 查询」有足够宽度；按钮组用 grid-row:1 固定在右上角，
   展开更多条件时也不会换行到其他行、更不会与「状态」输入框重叠。 */
.md-hospital-filter.catalog-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr)) max-content;
  gap: 10px;
  align-items: end;
  padding: 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--cat-soft) 0%, var(--cat-soft2) 100%);
  box-shadow: inset 0 0 0 1px var(--cat-line);
  border: 0;
}

.md-hospital-filter.catalog-filter label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  background: transparent;
  border: 0;
  padding: 0;
  box-shadow: none;
}

.md-hospital-filter.catalog-filter label > span {
  color: var(--cat-dark);
  font-size: 13px;
  font-weight: 700;
}

.md-hospital-filter.catalog-filter .md-input,
.md-hospital-filter.catalog-filter .md-select,
.md-hospital-filter.catalog-filter input,
.md-hospital-filter.catalog-filter select {
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--cat-line);
  border-radius: 6px;
  background: #fff;
  color: var(--cat-text);
  font-size: 14px;
}

.md-hospital-filter.catalog-filter input:hover,
.md-hospital-filter.catalog-filter select:hover {
  border-color: var(--cat-primary);
}

.md-hospital-filter.catalog-filter input:focus,
.md-hospital-filter.catalog-filter select:focus {
  outline: 0;
  border-color: var(--cat-primary);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.18);
}

/* 操作按钮组：固定在第一行最后一列，不再随字段数量换行。 */
.md-hospital-filter.catalog-filter .filter-actions {
  grid-column: 5;
  grid-row: 1;
  align-self: end;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 8px;
  min-width: max-content;
  margin: 0;
  padding: 0 0 1px;
  border: 0;
}

/* 筛选操作区：布局由 .md-hospital-filter.catalog-filter .filter-actions 接管，
   这里只保留旋转图标动效。 */
.catalog-redesign .filter-actions .rotated {
  transform: rotate(180deg);
}

.catalog-redesign .cat-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--cat-primary);
  color: #fff;
  font-size: 14.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: filter 0.15s, box-shadow 0.15s, transform 0.05s;
}

.catalog-redesign .cat-btn:hover:not(:disabled) {
  filter: brightness(0.93);
  box-shadow: 0 2px 8px rgba(16, 40, 40, 0.18);
}

.catalog-redesign .cat-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.catalog-redesign .cat-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.catalog-redesign .cat-btn-add,
.catalog-redesign .cat-btn-query,
.catalog-redesign .cat-btn-edit,
.catalog-redesign .cat-btn-batch,
.catalog-redesign .cat-btn-cols,
.catalog-redesign .cat-btn-more {
  background: var(--cat-primary);
  color: #fff;
}

.catalog-redesign .cat-btn-stop {
  background: #c0392b;
  color: #fff;
}

.catalog-redesign .cat-btn-export {
  background: #0e7490;
  color: #fff;
}

.catalog-redesign .cat-btn-reset {
  background: #5b7371;
  color: #fff;
}

/* 全局 master-data-list.override.css 用 !important 把 .master-page 内按钮漂白，
   这里用更高优先级 + !important 抢回医院目录的实色按钮 */
.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn,
.master-page.master-page--hospital .catalog-redesign .catalog-filter .filter-actions .cat-btn,
.master-page.master-page--hospital .catalog-redesign .catalog-panel .batch .cat-btn,
.master-page.master-page--hospital .catalog-redesign .col-setting-pop footer .cat-btn {
  background: var(--cat-primary) !important;
  border-color: transparent !important;
  color: #fff !important;
  box-shadow: 0 2px 8px rgba(16, 40, 40, 0.18) !important;
}

.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-add,
.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-query,
.master-page.master-page--hospital .catalog-redesign .catalog-filter .filter-actions .cat-btn-query,
.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-edit,
.master-page.master-page--hospital .catalog-redesign .catalog-panel .batch .cat-btn-edit,
.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-batch,
.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-cols,
.master-page.master-page--hospital .catalog-redesign .catalog-filter .filter-actions .cat-btn-more,
.master-page.master-page--hospital .catalog-redesign .col-setting-pop footer .cat-btn-query {
  background: var(--cat-primary) !important;
  border-color: transparent !important;
  color: #fff !important;
}

.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-stop,
.master-page.master-page--hospital .catalog-redesign .catalog-panel .batch .cat-btn-stop {
  background: #c0392b !important;
  border-color: transparent !important;
  color: #fff !important;
}

.master-page.master-page--hospital .catalog-redesign .toolbar .toolbar-main .cat-btn-export {
  background: #0e7490 !important;
  border-color: transparent !important;
  color: #fff !important;
}

.master-page.master-page--hospital .catalog-redesign .catalog-filter .filter-actions .cat-btn-reset,
.master-page.master-page--hospital .catalog-redesign .catalog-panel .batch .cat-btn-reset {
  background: #5b7371 !important;
  border-color: transparent !important;
  color: #fff !important;
}

.catalog-redesign .cat-btn-sm {
  height: 30px;
  padding: 0 14px;
  font-size: 13.5px;
}

.catalog-redesign .col-setting-wrap {
  position: relative;
}

.catalog-redesign .col-setting-wrap .rotated {
  transform: rotate(180deg);
}

.catalog-redesign .col-setting-pop {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  width: 250px;
  padding: 12px 12px 10px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--cat-border);
  box-shadow: 0 12px 32px rgba(9, 45, 43, 0.18);
}

.catalog-redesign .col-setting-pop header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--cat-dark);
  font-size: 13.5px;
  font-weight: 700;
}

.catalog-redesign .col-setting-pop header .link {
  border: 0;
  background: transparent;
  color: var(--cat-primary);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.catalog-redesign .col-setting-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 6px;
  border-radius: 6px;
  color: var(--cat-text);
  font-size: 13.5px;
  cursor: pointer;
}

.catalog-redesign .col-setting-item:hover {
  background: var(--cat-soft);
}

.catalog-redesign .col-setting-item input {
  width: 15px;
  height: 15px;
  accent-color: var(--cat-primary);
  cursor: pointer;
}

.catalog-redesign .col-setting-item small {
  margin-left: auto;
  color: var(--cat-text3);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.catalog-redesign .col-setting-pop footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.catalog-panel table.catalog-resizable thead th {
  position: relative;
  overflow: visible;
}

.catalog-panel table.catalog-resizable .th-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.catalog-panel table.catalog-resizable .col-resizer {
  position: absolute;
  top: 6px;
  bottom: 6px;
  right: -1px;
  width: 9px;
  margin-right: -4px;
  border-radius: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  touch-action: none;
}

.catalog-panel table.catalog-resizable .col-resizer::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 4px;
  width: 2px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.45);
}

.catalog-panel table.catalog-resizable thead th:hover .col-resizer::after {
  background: #fff;
}

.catalog-panel table.catalog-resizable thead th:hover .col-resizer {
  background: rgba(255, 255, 255, 0.18);
}

.catalog-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 14px 16px 16px;
  border-radius: 12px;
  background: var(--cat-card);
  box-shadow: 0 2px 6px rgba(16, 40, 40, 0.09);
  border: 0;
}

.catalog-panel .toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 2px;
  flex-wrap: wrap;
}

.catalog-panel .toolbar-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.catalog-panel .toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.catalog-panel .chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 13px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.catalog-panel .chip.is-primary {
  background: var(--cat-primary);
  color: #fff;
}

.catalog-panel .chip.is-green {
  background: #e6f6ef;
  color: #046b4c;
  box-shadow: inset 0 0 0 1px #b6e2d0;
}

.catalog-panel .batch {
  display: none;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--cat-soft) 0%, var(--cat-soft2) 100%);
  box-shadow: inset 0 0 0 1px var(--cat-line);
}

.catalog-panel .batch.on {
  display: flex;
}

.catalog-panel .batch b {
  color: var(--cat-dark);
  font-weight: 700;
  font-size: 14px;
}

.catalog-panel .batch .spacer,
.catalog-panel .spacer {
  flex: 1;
}

.catalog-panel .table-scroll {
  overflow-x: auto;
  border: 1px solid var(--cat-border);
  border-radius: 8px;
  background: #fff;
}

.catalog-panel {
  width: 100%;
}

.catalog-redesign .hospital-table-fill {
  width: 100%;
  border: 1px solid var(--cat-border);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.catalog-redesign .hospital-table-fill .hospital-el-table {
  width: 100%;
}

.catalog-redesign .hospital-el-table .el-table__header th {
  background: var(--cat-primary) !important;
}

.catalog-redesign .hospital-el-table .el-table__header th .cell {
  color: #fff;
  font-weight: 700;
}

.catalog-redesign .hospital-el-table .el-table__header th .cell:has(.hospital-col-grip) {
  display: flex;
  align-items: center;
  padding-right: 14px;
  overflow: visible;
}

.catalog-redesign .hospital-th-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-redesign .hospital-col-grip {
  position: absolute;
  top: 0;
  right: -5px;
  width: 10px;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
  z-index: 5;
}

.catalog-redesign .hospital-col-grip::after {
  content: "";
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 4px;
  width: 1px;
  border-radius: 1px;
  background: rgba(255, 255, 255, 0.28);
  transition: background 0.15s, box-shadow 0.15s, width 0.15s;
}

.catalog-redesign .hospital-el-table .el-table__header th:hover .hospital-col-grip::after {
  left: 4px;
  width: 2px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.18);
}

.catalog-redesign .hospital-el-table .el-table__header th {
  position: relative;
}

.catalog-redesign .hospital-el-table .el-table__body td {
  border-right: 1px solid var(--el-table-border-color, #e5e7eb);
}

.catalog-redesign .hospital-el-table .el-table__body td .cell {
  overflow: hidden;
}

.catalog-panel table.grid {
  display: block;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  --cols: 48px minmax(0, 2fr) minmax(0, 0.9fr) minmax(0, 1.1fr) minmax(0, 0.75fr) minmax(0, 0.7fr) 320px;
}

.catalog-panel table.catalog-grid {
  font-size: 14px;
}

.catalog-panel .grid thead,
.catalog-panel .grid tbody {
  display: block;
}

.catalog-panel .grid tr {
  display: grid;
  grid-template-columns: var(--cols);
  align-items: stretch;
}

.catalog-panel .grid thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  padding: 11px 13px;
  background: linear-gradient(180deg, var(--cat-mid) 0%, var(--cat-primary) 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
  border: 0;
  box-shadow: inset 0 -1px 0 rgba(255, 255, 255, 0.22);
}

.catalog-panel .grid thead th.c-act {
  justify-content: center;
  text-align: center;
}

.catalog-panel .grid tbody td {
  display: flex;
  align-items: center;
  padding: 11px 13px;
  min-width: 0;
  background: #fff;
  border: 0;
  border-bottom: 1px solid var(--cat-border-soft);
  vertical-align: middle;
  color: var(--cat-text);
  font-size: 14px;
  white-space: normal;
}

.catalog-panel .grid tbody tr,
.catalog-panel .grid tbody tr:nth-child(even),
.catalog-panel .grid tbody tr:hover,
.catalog-panel .grid tbody tr.sel,
.catalog-panel .grid tbody tr.sel:hover {
  background: #fff;
}

.catalog-panel .grid tbody tr:hover td,
.catalog-panel .grid tbody tr.sel td,
.catalog-panel .grid tbody tr.sel:hover td {
  background: #fff;
}

.catalog-panel .grid tbody tr:last-child td {
  border-bottom: 0;
}

.catalog-panel .grid td.c-act {
  justify-content: center;
  text-align: center;
}

.catalog-panel .col-sel {
  justify-content: center;
  text-align: center;
}

.catalog-panel .col-sel input {
  width: 16px;
  height: 16px;
  accent-color: var(--cat-primary);
  cursor: pointer;
  vertical-align: middle;
}

.catalog-panel .tpl-cell {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.catalog-panel .tpl-badge {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--cat-primary) 0%, var(--cat-deep) 100%);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  flex-shrink: 0;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--cat-primary) 35%, transparent);
}

.catalog-panel .tpl-txt {
  min-width: 0;
}

.catalog-panel .tpl-name {
  font-weight: 700;
  font-size: 14.5px;
  color: var(--cat-text);
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.catalog-panel .tpl-meta {
  margin-top: 3px;
  color: var(--cat-text3);
  font-size: 12.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 5px;
}

.catalog-panel .tpl-meta code {
  font-family: ui-monospace, Consolas, monospace;
  padding: 1px 7px;
  border-radius: 4px;
  background: #eef1f1;
  color: var(--cat-text2);
  font-size: 12px;
  font-weight: 700;
}

.catalog-panel .mini-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  background: #eef1f1;
  color: var(--cat-text2);
  font-size: 12px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px var(--cat-border);
}

.catalog-panel .mini-tag.hot {
  background: var(--cat-primary);
  color: #fff;
  box-shadow: none;
}

.catalog-panel .vendor-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.catalog-panel .vendor-cell strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--cat-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.catalog-panel .vendor-cell span {
  font-size: 12.5px;
  color: var(--cat-text3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.catalog-panel .qty {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.catalog-panel .qty b {
  font-size: 16px;
  font-weight: 700;
  color: var(--cat-text);
}

.catalog-panel .qty span {
  margin-left: 4px;
  color: var(--cat-text3);
  font-size: 13px;
}

.catalog-panel .status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.catalog-panel .status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 7px;
}

.catalog-panel .status.on {
  color: #046b4c;
}

.catalog-panel .status.off {
  color: #5f6d73;
}

.catalog-panel .row-act {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.catalog-panel .act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 29px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  text-decoration: none;
  transition: filter 0.15s, box-shadow 0.15s, transform 0.05s;
}

.catalog-panel .act:hover {
  filter: brightness(0.93);
  box-shadow: 0 2px 7px rgba(16, 40, 40, 0.2);
}

.catalog-panel .act:active {
  transform: translateY(1px);
}

.catalog-panel .act-detail {
  background: #0e7490;
  color: #fff;
}

.catalog-panel .act-edit {
  background: #0958d9;
  color: #fff;
}

.catalog-panel .act-pack {
  background: var(--cat-primary);
  color: #fff;
}

.catalog-panel .act-stop {
  background: #c0392b;
  color: #fff;
}

.catalog-panel .pager {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 2px 0;
  color: var(--cat-text2);
  font-size: 13.5px;
}

.catalog-mask {
  position: fixed;
  inset: 0;
  background: rgba(9, 45, 43, 0.45);
  z-index: 90;
}

.catalog-drawer {
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
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.catalog-drawer .drawer-head p {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  font-family: ui-monospace, Consolas, monospace;
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
  font-size: 14px;
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
  font-size: 12.5px;
  margin-bottom: 3px;
  font-weight: 600;
}

.catalog-drawer .d-item > strong {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--cat-text);
  word-break: break-all;
}

.catalog-drawer .d-item > strong.mono {
  font-family: ui-monospace, Consolas, monospace;
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
  font-size: 13px;
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

@media (max-width: 1200px) {
  /* 窄屏：字段改为 3 列，操作按钮组独占一行右对齐 */
  .md-hospital-filter.catalog-filter {
    grid-template-columns: repeat(3, minmax(120px, 1fr)) max-content;
  }
  .md-hospital-filter.catalog-filter .filter-actions {
    grid-column: 1 / -1;
    grid-row: auto;
    justify-content: flex-end;
  }
  .catalog-panel table.grid {
    --cols: 48px minmax(0, 1.6fr) minmax(0, 0.9fr) minmax(0, 1fr) minmax(0, 0.75fr) minmax(0, 0.7fr) 300px;
  }
}
</style>
