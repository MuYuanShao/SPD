<script setup lang="ts">
import { ref } from 'vue'
import {
  Ban,
  Columns3,
  Database,
  Download,
  Edit3,
  Factory,
  FileDown,
  FileUp,
  Link2,
  ListPlus,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  Truck,
  Upload,
  X
} from '@lucide/vue'
import { useHospitalProductManagement } from '../../composables/useHospitalProductManagement'
import { useMasterDataExchange } from '../../composables/useMasterDataExchange'
import { useMasterDataWorkbench } from '../../composables/useMasterDataWorkbench'
import { useOrganizationMasterData } from '../../composables/useOrganizationMasterData'
import { usePartnerMasterData } from '../../composables/usePartnerMasterData'
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

const {
  page,
  loading,
  error,
  actionError,
  actionMessage,
  selectedCodes,
  hospitalColumnConfigs,
  activeColumnGroup,
  activeHospitalColumn,
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
  hospitalColumnGroups,
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

/** 字段导航水平拖动滚动：仅维护一个原生滚动容器，不再绑定表格滚动 */
const columnNavTrackRef = ref<HTMLElement | null>(null)
const columnNavDragging = ref(false)
let columnNavDragStartX = 0
let columnNavDragStartLeft = 0

function startColumnNavDrag(event: PointerEvent) {
  const target = columnNavTrackRef.value
  if (!target) return
  if ((event.target as HTMLElement | null)?.closest('.subnav-chip')) {
    return
  }
  columnNavDragging.value = true
  columnNavDragStartX = event.clientX
  columnNavDragStartLeft = target.scrollLeft
  target.setPointerCapture?.(event.pointerId)
}

function moveColumnNavDrag(event: PointerEvent) {
  const target = columnNavTrackRef.value
  if (!target || !columnNavDragging.value) return
  target.scrollLeft = columnNavDragStartLeft - (event.clientX - columnNavDragStartX)
}

function endColumnNavDrag(event: PointerEvent) {
  columnNavDragging.value = false
  columnNavTrackRef.value?.releasePointerCapture?.(event.pointerId)
}

function scrollHospitalColumnTo(prop: string) {
  activeHospitalColumn.value = prop
  // 不再联动表格滚动定位（避免再出现双滚动条 / 高度溢出），点击仅作为字段高亮。
}

function applyColumnGroupLocal(groupKey: string) {
  activeColumnGroup.value = groupKey
  const group = hospitalColumnGroups.find((item) => item.key === groupKey)
  hospitalColumnConfigs.value = hospitalColumnConfigs.value.map((column) => ({ ...column, visible: true }))
  if (!group || group.key === 'all') {
    activeHospitalColumn.value = ''
    return
  }
  const firstMatch = hospitalColumnConfigs.value.find((column) => group.fields.includes(column.label))
  if (firstMatch) {
    activeHospitalColumn.value = firstMatch.prop
  }
}

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
  disableManufacturers
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
  loadPage
})

const {
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

</script>

<template>
  <section class="master-page">
    <header v-if="isPartnerCombined" class="md-header md-header--partner">
      <div class="md-header-inner">
        <div class="md-header-text">
          <div class="md-header-crumbs">
            <strong>主数据与定数</strong>
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

    <section v-if="isHospitalCatalog" class="md-hospital-page">
      <MdPageHeader
        title="医院商品目录"
        description="维护本院商品目录、字段可见性与分页。"
        :loading="loading"
        @refresh="loadPage"
      />

      <MdStatsRow
        :items="[
          { label: '医院商品总数', value: Number(hospitalStats.scopeTotal ?? 0), tone: 'primary', hint: '条' },
          { label: '集采商品', value: Number(hospitalStats.centralizedTotal ?? 0), tone: 'success', hint: '条' },
          { label: '国产商品', value: Number(hospitalStats.domesticTotal ?? 0), tone: 'warning', hint: '条' },
          { label: '已停用商品', value: Number(hospitalStats.disabledTotal ?? 0), tone: 'danger', hint: '条' }
        ]"
      />

      <form class="md-hospital-filter" @submit.prevent="submitHospitalQuery">
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
          <span>规格</span>
          <input
            v-model.trim="hospitalQuery.specModel"
            class="md-input"
            type="search"
            placeholder="规格"
            autocomplete="off"
          />
        </label>
        <label>
          <span>型号</span>
          <input
            v-model.trim="hospitalQuery.model"
            class="md-input"
            type="search"
            placeholder="型号"
            autocomplete="off"
          />
        </label>
        <label>
          <span>厂家</span>
          <input
            v-model.trim="hospitalQuery.manufacturerName"
            class="md-input"
            type="search"
            placeholder="厂家名称"
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
          <span>是否国产</span>
          <select v-model="hospitalQuery.isDomestic" class="md-select">
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
        <div class="md-hospital-filter-actions">
          <button type="button" class="md-btn" @click="resetHospitalQuery">
            <RefreshCw :size="14" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="14" />
            <span>查询</span>
          </button>
        </div>
      </form>

      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isSupplierManagement" class="md-partner-page">
      <MdPageHeader
        v-if="!isPartnerCombined"
        title="供应商管理"
        description="维护供应商档案，统一管理名称、类型、联系人、信用代码与启停状态。"
        :loading="loading"
        @refresh="loadPage"
      />

      <MdStatsRow
        :items="[
          { label: '当前页记录', value: partnerStats.total, tone: 'primary', hint: '条' },
          { label: '启用', value: partnerStats.enabled, tone: 'success', hint: '条' },
          { label: '停用', value: partnerStats.disabled, tone: 'danger', hint: '条' },
          { label: '已选', value: partnerStats.selected, tone: 'warning', hint: '条' }
        ]"
      />

      <form class="md-hospital-filter" @submit.prevent="loadPage">
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
          <select v-model="supplierQuery.status" class="md-select" aria-label="筛选供应商启停状态">
            <option value="">全部状态</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="md-hospital-filter-actions">
          <button type="button" class="md-btn" @click="resetSupplierQuery">
            <RefreshCw :size="14" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="14" />
            <span>查询</span>
          </button>
        </div>
      </form>
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isManufacturerManagement" class="md-partner-page">
      <MdPageHeader
        v-if="!isPartnerCombined"
        title="厂家管理"
        description="维护生产厂家档案，统一管理名称、生产许可证号、联系人与启停状态。"
        :loading="loading"
        @refresh="loadPage"
      />

      <MdStatsRow
        :items="[
          { label: '当前页记录', value: partnerStats.total, tone: 'primary', hint: '条' },
          { label: '启用', value: partnerStats.enabled, tone: 'success', hint: '条' },
          { label: '停用', value: partnerStats.disabled, tone: 'danger', hint: '条' },
          { label: '已选', value: partnerStats.selected, tone: 'warning', hint: '条' }
        ]"
      />

      <form class="md-hospital-filter" @submit.prevent="loadPage">
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
          <select v-model="manufacturerQuery.status" class="md-select" aria-label="筛选厂家启停状态">
            <option value="">全部状态</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="md-hospital-filter-actions">
          <button type="button" class="md-btn" @click="resetManufacturerQuery">
            <RefreshCw :size="14" />
            <span>重置</span>
          </button>
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="14" />
            <span>查询</span>
          </button>
        </div>
      </form>
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isCampusManagement" class="md-partner-page">
      <MdPageHeader
        title="院区管理"
        description="维护医院院区档案，统一管理编码、名称、地址与启停状态。"
        :loading="loading"
        @refresh="loadPage"
      />

      <MdStatsRow
        :items="[
          { label: '当前页记录', value: partnerStats.total, tone: 'primary', hint: '个' },
          { label: '启用', value: partnerStats.enabled, tone: 'success', hint: '个' },
          { label: '停用', value: partnerStats.disabled, tone: 'danger', hint: '个' },
          { label: '已选', value: partnerStats.selected, tone: 'warning', hint: '个' }
        ]"
      />

      <form class="md-hospital-filter" @submit.prevent="loadPage">
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
          <select v-model="campusQuery.status" class="md-input" aria-label="筛选院区启停状态">
            <option value="">全部状态</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="md-hospital-filter-actions">
          <button type="submit" class="md-btn md-btn-primary">
            <Search :size="16" />
            <span>查询</span>
          </button>
          <button type="button" class="md-btn" @click="resetCampusQuery">
            <RefreshCw :size="16" />
            <span>重置</span>
          </button>
        </div>
      </form>
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isDepartmentManagement" class="md-partner-page">
      <MdPageHeader
        title="科室管理"
        description="维护医院科室档案，统一管理编码、类型、关联库房与状态。"
        :loading="loading"
        @refresh="loadPage"
      />

      <MdStatsRow
        :items="[
          { label: '当前页记录', value: partnerStats.total, tone: 'primary', hint: '个' },
          { label: '启用', value: partnerStats.enabled, tone: 'success', hint: '个' },
          { label: '停用', value: partnerStats.disabled, tone: 'danger', hint: '个' },
          { label: '已选', value: partnerStats.selected, tone: 'warning', hint: '个' }
        ]"
      />

      <form class="md-hospital-filter" @submit.prevent="loadPage">
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
          <select v-model="departmentQuery.status" class="md-input" aria-label="筛选科室状态">
            <option value="">全部状态</option>
            <option value="正常">正常</option>
            <option value="停用">停用</option>
          </select>
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
        <div class="md-hospital-filter-actions">
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
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isDepartmentWarehouseCatalog" class="md-partner-page">
      <MdPageHeader
        title="科室库房目录"
        description="维护科室与库房的商品目录关系，按科室、库房、商品、状态快速检索。"
        :loading="loading"
        @refresh="loadPage"
      />
      <form class="md-hospital-filter" @submit.prevent="submitDepartmentWarehouseCatalogQuery">
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
          <select v-model="departmentWarehouseCatalogQuery.status" class="md-input" aria-label="筛选目录状态">
            <option value="">全部状态</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="md-hospital-filter-actions">
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
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isWarehouseManagement" class="md-partner-page">
      <MdPageHeader
        title="库房 / 货位管理"
        description="维护医院库房与货位档案，按库房名称、编码、状态快速检索。"
        :loading="loading"
        @refresh="loadPage"
      />
      <form class="md-hospital-filter" @submit.prevent="loadPage">
        <label>
          <span>库房名称 / 编码</span>
          <input
            v-model.trim="warehouseQuery.warehouseKeyword"
            class="md-input"
            type="search"
            placeholder="请输入"
            autocomplete="off"
          />
        </label>
        <label>
          <span>库房类型</span>
          <input
            v-model.trim="warehouseQuery.warehouseType"
            class="md-input"
            type="search"
            placeholder="请输入"
            autocomplete="off"
          />
        </label>
        <label>
          <span>所属院区</span>
          <input
            v-model.trim="warehouseQuery.campusName"
            class="md-input"
            type="search"
            placeholder="请输入"
            autocomplete="off"
          />
        </label>
        <label>
          <span>关联科室</span>
          <input
            v-model.trim="warehouseQuery.deptName"
            class="md-input"
            type="search"
            placeholder="请输入"
            autocomplete="off"
          />
        </label>
        <label>
          <span>库房状态</span>
          <select v-model="warehouseQuery.status" class="md-input">
            <option value="">全部</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="md-hospital-filter-actions">
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
      <p v-if="actionError" class="md-error">{{ actionError }}</p>
      <p v-if="actionMessage" class="md-message">{{ actionMessage }}</p>
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
      class="master-table-card"
      :class="{
        'department-warehouse-catalog-list-card': isDepartmentWarehouseCatalog,
        'master-table-card-flat':
          isHospitalCatalog ||
          isSupplierManagement ||
          isManufacturerManagement ||
          isCampusManagement ||
          isDepartmentManagement
      }"
    >
      <StatusMessage v-if="loading" message="正在加载..." tone="info" />
      <StatusMessage v-else-if="error" :message="error" tone="error" />

      <div v-if="isHospitalCatalog && !loading && !error" class="column-nav-panel">
        <div class="column-nav-head">
          <strong>
            <Database :size="16" aria-hidden="true" />
            字段导航
          </strong>
          <span>已展示 {{ sortedColumns.length }} / {{ hospitalColumnConfigs.length }} 个字段</span>
        </div>
        <div class="subnav-tabs column-nav-groups">
          <button
            v-for="group in hospitalColumnGroups"
            :key="group.key"
            type="button"
            :class="{ active: activeColumnGroup === group.key }"
            @click="applyColumnGroupLocal(group.key)"
          >
            {{ group.label }}
          </button>
        </div>
        <div
          ref="columnNavTrackRef"
          class="subnav-chips column-nav-chips"
          :class="{ dragging: columnNavDragging }"
          @pointerdown="startColumnNavDrag"
          @pointermove="moveColumnNavDrag"
          @pointerup="endColumnNavDrag"
          @pointercancel="endColumnNavDrag"
          @pointerleave="endColumnNavDrag"
        >
          <button
            v-for="col in hospitalColumnConfigs"
            :key="col.prop"
            type="button"
            class="subnav-chip"
            :class="{ active: activeHospitalColumn === col.prop }"
            @click="scrollHospitalColumnTo(col.prop)"
          >
            {{ col.label }}
          </button>
        </div>
      </div>

      <MdDataTableCard
        v-if="isHospitalCatalog"
        :meta-count="`共 ${hospitalTotal} 条`"
        :loading="loading"
      >
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
            class="md-btn"
            :disabled="selectedCodes.length !== 1"
            @click="openEditProduct()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
            :disabled="!selectedCodes.length"
            @click="batchEditSelectedProducts"
          >
            <Edit3 :size="16" />
            <span>批量修改</span>
          </button>
          <button type="button" class="md-btn" @click="exportHospitalCatalog">
            <Download :size="16" />
            <span>导出</span>
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
          <el-popover
            :visible="columnSettingsOpen"
            placement="bottom-end"
            :width="280"
            trigger="click"
            @update:visible="columnSettingsOpen = $event"
          >
            <template #reference>
              <button
                type="button"
                class="md-btn"
                aria-label="列设置"
                @click="columnSettingsOpen = !columnSettingsOpen"
              >
                <Columns3 :size="16" />
                <span>列设置</span>
              </button>
            </template>
            <div class="md-column-settings">
              <header class="md-column-settings-head">
                <strong>可见字段</strong>
                <small>共 {{ hospitalColumnConfigs.length }} 个</small>
              </header>
              <label
                v-for="col in hospitalColumnConfigs"
                :key="col.prop"
                class="md-column-setting-item"
              >
                <el-checkbox v-model="col.visible" size="small" />
                <span>{{ col.label }}</span>
              </label>
              <footer class="md-column-settings-foot">
                <button type="button" class="md-btn-text" @click="columnSettingsOpen = false">
                  <X :size="14" />
                  <span>关闭</span>
                </button>
              </footer>
            </div>
          </el-popover>
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <MdEmptyState
          v-if="loading"
          text="正在加载医院目录..."
          :loading="true"
        />
        <MdEmptyState
          v-else-if="error"
          :text="error"
        />
        <div v-else class="md-hospital-table-wrap">
          <table class="md-hospital-table">
            <colgroup>
              <col style="width: 42px" />
              <col
                v-for="col in sortedColumns"
                :key="`col-${col.prop}`"
                :style="{ width: `${col.width}px` }"
              />
              <col style="width: 200px" />
            </colgroup>
            <thead>
              <tr>
                <th class="md-hospital-sticky-left">
                  <input
                    type="checkbox"
                    :checked="allVisibleSelected"
                    aria-label="选择当前页全部商品"
                    @change="toggleAllRows"
                  />
                </th>
                <th
                  v-for="col in sortedColumns"
                  :key="col.prop"
                  :class="{ 'md-hospital-col-active': activeHospitalColumn === col.prop }"
                >{{ col.label }}</th>
                <th class="md-hospital-sticky-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page?.rows ?? []" :key="String(row.code)">
                <td class="md-hospital-sticky-left">
                  <input
                    v-model="selectedCodes"
                    type="checkbox"
                    :value="String(row.code)"
                    :aria-label="`选择 ${String(row.code)}`"
                  />
                </td>
                <td
                  v-for="col in sortedColumns"
                  :key="col.prop"
                  :class="{ 'md-hospital-col-active': activeHospitalColumn === col.prop }"
                  :title="String(row[col.prop] ?? '-')"
                >
                  {{ row[col.prop] ?? '-' }}
                </td>
                <td class="md-hospital-sticky-right">
                  <div class="md-row-actions">
                    <RouterLink
                      class="md-btn-text"
                      :to="{ name: 'hospital-product-detail', params: { productCode: String(row.code) } }"
                    >
                      <Search :size="13" />
                      <span>查看</span>
                    </RouterLink>
                    <button
                      v-if="authStore.canWrite(code)"
                      type="button"
                      class="md-btn-text"
                      @click="openEditProduct(String(row.code), false)"
                    >
                      <Pencil :size="13" />
                      <span>修改</span>
                    </button>
                    <button
                      v-if="authStore.canWrite(code)"
                      type="button"
                      class="md-btn-text md-btn-text-danger"
                      @click="disableProducts([String(row.code)])"
                    >
                      <Ban :size="13" />
                      <span>停用</span>
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!(page?.rows ?? []).length">
                <td :colspan="sortedColumns.length + 2">
                  <MdEmptyState text="暂无医院目录数据" hint="尝试调整筛选条件或导入新商品" />
                </td>
              </tr>
            </tbody>
          </table>
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

      <MdDataTableCard
        v-else-if="isSupplierManagement && page"
        :meta-count="`共 ${partnerTotal} 条`"
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
            class="md-btn"
            :disabled="selectedCodes.length !== 1"
            @click="openEditSupplier()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn" aria-label="打开供应商导入菜单">
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
          <button type="button" class="md-btn" @click="exportSupplierRows">
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
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="supplier-table-wrap">
          <table class="master-table supplier-table">
            <thead>
              <tr>
                <th class="selection-cell">
                  <input
                    type="checkbox"
                    :checked="allVisibleSelected"
                    aria-label="选择当前页全部供应商"
                    @change="toggleAllRows"
                  />
                </th>
                <th>供应商编码</th>
                <th>供应商名称</th>
                <th>统一社会信用代码</th>
                <th>经营许可证号</th>
                <th>类型</th>
                <th>等级</th>
                <th>联系人</th>
                <th>邮箱</th>
                <th>地址</th>
                <th>状态</th>
                <th class="supplier-action-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page.rows" :key="String(row.code)">
                <td class="selection-cell">
                  <input
                    v-model="selectedCodes"
                    type="checkbox"
                    :value="String(row.code)"
                    :aria-label="`选择供应商 ${String(row.name)}`"
                  />
                </td>
                <td class="supplier-code-cell">{{ row.code }}</td>
                <td class="supplier-name-cell" :title="String(row.name)">
                  <strong>{{ row.name }}</strong>
                </td>
                <td class="supplier-code-cell">{{ row.creditCode }}</td>
                <td class="supplier-code-cell">{{ row.businessLicenseNo || '-' }}</td>
                <td><span class="supplier-type-chip">{{ row.type || '-' }}</span></td>
                <td>
                  <span class="supplier-grade-chip" :class="{ 'grade-empty': row.grade === '-' }">
                    {{ row.grade || '-' }}
                  </span>
                </td>
                <td>
                  <div class="supplier-contact-cell">
                    <strong>{{ row.contactName || '-' }}</strong>
                    <span>{{ row.contactPhone || '-' }}</span>
                  </div>
                </td>
                <td>{{ row.email || '-' }}</td>
                <td class="supplier-address-cell" :title="String(row.address || '-')">{{ row.address || '-' }}</td>
                <td>
                  <span
                    class="supplier-status-chip"
                    :class="String(row.status) === '启用' ? 'enabled' : 'disabled'"
                  >
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td class="supplier-action-cell">
                  <button type="button" class="btn-text" @click="openEditSupplier(row, false)">修改</button>
                  <button
                    type="button"
                    class="btn-text btn-text-danger"
                    @click="disableSuppliers([String(row.code)])"
                  >
                    停用
                  </button>
                </td>
              </tr>
              <tr v-if="!page.rows.length">
                <td class="approval-empty" colspan="12">暂无符合条件的供应商</td>
              </tr>
            </tbody>
          </table>
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
        :meta-count="`共 ${partnerTotal} 条`"
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
            class="md-btn"
            :disabled="selectedCodes.length !== 1"
            @click="openEditManufacturer()"
          >
            <Pencil :size="16" />
            <span>修改</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn" aria-label="打开厂家导入菜单">
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
          <button type="button" class="md-btn" @click="exportManufacturerRows">
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
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="manufacturer-table-wrap">
          <table class="master-table manufacturer-table">
            <thead>
              <tr>
                <th class="selection-cell">
                  <input
                    type="checkbox"
                    :checked="allVisibleSelected"
                    aria-label="选择当前页全部厂家"
                    @change="toggleAllRows"
                  />
                </th>
                <th>厂家编码</th>
                <th>厂家名称</th>
                <th>统一社会信用代码</th>
                <th>生产许可证号</th>
                <th>联系人</th>
                <th>地址</th>
                <th>状态</th>
                <th class="manufacturer-action-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page.rows" :key="String(row.code)">
                <td class="selection-cell">
                  <input
                    v-model="selectedCodes"
                    type="checkbox"
                    :value="String(row.code)"
                    :aria-label="`选择厂家 ${String(row.name)}`"
                  />
                </td>
                <td class="manufacturer-code-cell">{{ row.code }}</td>
                <td class="manufacturer-name-cell" :title="String(row.name)">
                  <strong>{{ row.name }}</strong>
                </td>
                <td class="manufacturer-code-cell">{{ row.creditCode || '-' }}</td>
                <td>
                  <span class="manufacturer-license-chip" :class="{ empty: row.licenseNo === '-' }">
                    {{ row.licenseNo || '-' }}
                  </span>
                </td>
                <td>
                  <div class="manufacturer-contact-cell">
                    <strong>{{ row.contactName || '-' }}</strong>
                    <span>{{ row.contactPhone || '-' }}</span>
                  </div>
                </td>
                <td class="manufacturer-address-cell" :title="String(row.address || '-')">
                  {{ row.address || '-' }}
                </td>
                <td>
                  <span
                    class="manufacturer-status-chip"
                    :class="String(row.status) === '启用' ? 'enabled' : 'disabled'"
                  >
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td class="manufacturer-action-cell">
                  <button type="button" class="btn-text" @click="openEditManufacturer(row, false)">修改</button>
                  <button
                    type="button"
                    class="btn-text btn-text-danger"
                    @click="disableManufacturers([String(row.code)])"
                  >
                    停用
                  </button>
                </td>
              </tr>
              <tr v-if="!page.rows.length">
                <td class="approval-empty" colspan="9">暂无符合条件的厂家</td>
              </tr>
            </tbody>
          </table>
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
        :meta-count="`共 ${listTotal} 个`"
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
            class="md-btn"
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
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="campus-table-wrap">
        <table class="master-table campus-table">
          <thead>
            <tr>
              <th class="selection-cell">
                <input
                  type="checkbox"
                  :checked="allVisibleSelected"
                  aria-label="选择当前页全部院区"
                  @change="toggleAllRows"
                />
              </th>
              <th>院区编码</th>
              <th>院区名称</th>
              <th>院区地址</th>
              <th>负责人</th>
              <th>排序</th>
              <th>状态</th>
              <th>修改时间</th>
              <th class="campus-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in page.rows" :key="String(row.code)">
              <td class="selection-cell">
                <input
                  v-model="selectedCodes"
                  type="checkbox"
                  :value="String(row.code)"
                  :aria-label="`选择院区 ${String(row.name)}`"
                />
              </td>
              <td class="campus-code-cell">{{ row.code }}</td>
              <td class="campus-name-cell" :title="String(row.name)">
                <strong>{{ row.name }}</strong>
                <span>组织基础档案</span>
              </td>
              <td class="campus-address-cell" :title="String(row.address || '-')">{{ row.address || '-' }}</td>
              <td>
                <div class="campus-contact-cell">
                  <strong>{{ row.manager || '-' }}</strong>
                  <span>{{ row.phone || '-' }}</span>
                </div>
              </td>
              <td><span class="campus-sort-chip">{{ row.sortOrder ?? 0 }}</span></td>
              <td>
                <span
                  class="campus-status-chip"
                  :class="String(row.status) === '启用' ? 'enabled' : 'disabled'"
                >
                  {{ formatStatusText(row.status) }}
                </span>
              </td>
              <td class="campus-update-time">{{ row.updateTime || '-' }}</td>
              <td class="campus-action-cell">
                <button type="button" class="btn-text" @click="openEditCampus(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedCampuses([String(row.code)])"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="!page.rows.length">
              <td class="approval-empty" colspan="9">暂无符合条件的院区</td>
            </tr>
          </tbody>
        </table>
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
            class="md-btn"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditDepartment()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
            :disabled="!selectedCodes.length || loading"
            @click="openDepartmentWarehouseDialog()"
          >
            <Link2 :size="16" />
            <span>关联库房</span>
          </button>
          <details class="batch-edit-menu">
            <summary class="md-btn" aria-label="打开科室导入菜单">
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
          <button type="button" class="md-btn" @click="exportDepartmentRows">
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
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="department-table-wrap">
        <table class="master-table department-table">
          <thead>
            <tr>
              <th class="selection-cell">
                <input
                  type="checkbox"
                  :checked="allVisibleSelected"
                  aria-label="选择当前页全部科室"
                  @change="toggleAllRows"
                />
              </th>
              <th>科室编码</th>
              <th>科室名称</th>
              <th>组织归属</th>
              <th>财务科室映射</th>
              <th>负责人</th>
              <th>科室地址</th>
              <th>关联库房</th>
              <th>原数据标识</th>
              <th>状态</th>
              <th>修改时间</th>
              <th class="department-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in page.rows" :key="String(row.code)">
              <td class="selection-cell">
                <input
                  v-model="selectedCodes"
                  type="checkbox"
                  :value="String(row.code)"
                  :aria-label="`选择科室 ${String(row.name)}`"
                />
              </td>
              <td class="department-code-cell">{{ row.code }}</td>
              <td class="department-name-cell" :title="String(row.name)">
                <strong>{{ row.name }}</strong>
                <span>{{ row.deptAttribute || '科室档案' }}</span>
              </td>
              <td>
                <div class="department-organization-cell">
                  <strong>{{ row.campus || '未归属院区' }}</strong>
                  <div class="department-organization-meta">
                    <span class="department-type-chip">{{ row.deptType || '-' }}</span>
                    <span class="department-attribute-chip">{{ row.deptAttribute || '-' }}</span>
                  </div>
                </div>
              </td>
              <td>
                <div class="department-finance-cell">
                  <strong>{{ row.financeDept || '-' }}</strong>
                  <code>{{ row.financeDeptCode || '-' }}</code>
                </div>
              </td>
              <td>
                <div class="department-contact-cell">
                  <strong>{{ row.manager || '-' }}</strong>
                  <span>{{ row.phone || '-' }}</span>
                </div>
              </td>
              <td class="department-address-cell" :title="String(row.address || '-')">{{ row.address || '-' }}</td>
              <td class="department-warehouse-cell" :title="String(row.relatedWarehouse || '-')">
                {{ row.relatedWarehouse || '-' }}
              </td>
              <td class="department-source-cell">{{ row.sourceFlag || '-' }}</td>
              <td>
                <span
                  class="department-status-chip"
                  :class="String(row.status) === '正常' ? 'enabled' : 'disabled'"
                >
                  {{ formatStatusText(row.status) }}
                </span>
              </td>
              <td class="department-update-time">{{ row.updateTime || '-' }}</td>
              <td class="department-action-cell">
                <button type="button" class="btn-text" @click="openEditDepartment(row, false)">编辑</button>
                <button type="button" class="btn-text" @click="openDepartmentWarehouseDialog(row, false)">关联库房</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedDepartments([String(row.code)])"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="!page.rows.length">
              <td class="approval-empty" colspan="12">暂无符合条件的科室</td>
            </tr>
          </tbody>
        </table>
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
            class="md-btn"
            :disabled="!selectedCodes.length || loading"
            @click="openBatchDepartmentWarehouseCatalog"
          >
            <ListPlus :size="16" />
            <span>批量维护</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openEditDepartmentWarehouseCatalog()"
          >
            <Pencil :size="16" />
            <span>编辑</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
            :disabled="!selectedCodes.length || loading"
            @click="updateSelectedDepartmentWarehouseCatalogStatus(1)"
          >
            <RefreshCw :size="16" />
            <span>启用</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
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
        </template>
        <el-table
          class="department-warehouse-catalog-table"
          :data="page.rows"
          row-key="code"
          border
          stripe
          style="width: 100%"
          empty-text="暂无符合条件的科室库房目录"
        >
          <el-table-column width="54" fixed="left" align="center">
            <template #header>
                <input
                  type="checkbox"
                  :checked="allVisibleSelected"
                  aria-label="选择当前页全部科室库房目录"
                  @change="toggleAllRows"
                />
            </template>
            <template #default="{ row }">
                <input
                  v-model="selectedCodes"
                  type="checkbox"
                  :value="String(row.code)"
                  :aria-label="`选择 ${String(row.deptName)} ${String(row.productName)} 目录`"
                />
            </template>
          </el-table-column>
          <el-table-column prop="sequenceNo" label="序号" width="64" align="center">
            <template #default="{ row }"><span class="catalog-sequence-cell">{{ row.sequenceNo }}</span></template>
          </el-table-column>
          <el-table-column label="科室 / 库房" min-width="160">
            <template #default="{ row }">
                <div class="catalog-scope-cell">
                  <strong>{{ row.deptName || '-' }}</strong>
                  <span>{{ row.warehouseName || '未关联库房' }}</span>
                </div>
            </template>
          </el-table-column>
          <el-table-column prop="productCode" label="商品编码" min-width="120">
            <template #default="{ row }"><span class="catalog-code-cell">{{ row.productCode || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="商品名称" min-width="150">
            <template #default="{ row }">
                <div class="catalog-product-cell">
                  <strong>{{ row.productName || '-' }}</strong>
                  <span>科室可申领商品</span>
                </div>
            </template>
          </el-table-column>
          <el-table-column prop="specModel" label="规格型号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }"><span class="catalog-spec-cell">{{ row.specModel || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="manufacturerName" label="生产厂家" min-width="150" show-overflow-tooltip>
            <template #default="{ row }"><span class="catalog-manufacturer-cell">{{ row.manufacturerName || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="维护来源" min-width="100">
            <template #default="{ row }"><span class="catalog-source-chip">{{ formatBusinessText(row.sourceType) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
                <span class="catalog-status-chip" :class="String(row.status) === '启用' ? 'enabled' : 'disabled'">
                  {{ formatStatusText(row.status) }}
                </span>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="150">
            <template #default="{ row }"><span class="catalog-update-time">{{ row.updateTime || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="catalog-row-actions">
                <button type="button" class="btn-text" @click="openEditDepartmentWarehouseCatalog(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text"
                  @click="updateSelectedDepartmentWarehouseCatalogStatus(String(row.status) === '停用' ? 1 : 0, [String(row.code)])"
                >
                  {{ String(row.status) === '停用' ? '启用' : '停用' }}
                </button>
                <button type="button" class="btn-text btn-text-danger" @click="deleteSelectedDepartmentWarehouseCatalogs([String(row.code)])">
                  删除
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
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
        :meta-count="`共 ${listTotal} 个`"
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
            class="md-btn"
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
            <summary class="md-btn" aria-label="打开库房导入菜单">
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
          <button type="button" class="md-btn" @click="exportWarehouseRows">
            <Download :size="16" />
            <span>导出 Excel</span>
          </button>
          <button
            v-if="authStore.canWrite(code)"
            type="button"
            class="md-btn"
            :disabled="selectedCodes.length !== 1 || loading"
            @click="openWarehouseLocationDialog()"
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
        </template>
        <template #meta-extra>
          <span v-if="selectedCodes.length" class="md-chip primary">已选 {{ selectedCodes.length }} 条</span>
        </template>
        <div class="warehouse-table-wrap">
          <table class="master-table warehouse-table">
            <thead>
              <tr>
                <th class="selection-cell">
                  <input
                    type="checkbox"
                    :checked="allVisibleSelected"
                    aria-label="选择当前页全部库房"
                    @change="toggleAllRows"
                  />
                </th>
                <th>库房编码</th>
                <th>库房名称</th>
                <th>库房类型</th>
                <th>所属院区</th>
                <th>关联科室</th>
                <th>统计</th>
                <th>允许收货</th>
                <th>统计分类</th>
                <th>状态</th>
                <th>修改时间</th>
                <th>货位数量</th>
                <th class="warehouse-action-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page.rows" :key="String(row.code)">
                <td class="selection-cell">
                  <input
                    v-model="selectedCodes"
                    type="checkbox"
                    :value="String(row.code)"
                    :aria-label="`选择库房 ${String(row.name)}`"
                  />
                </td>
                <td class="warehouse-code-cell">{{ row.code }}</td>
                <td class="warehouse-name-cell" :title="String(row.name)">
                  <strong>{{ row.name }}</strong>
                  <span v-if="row.needsDepartmentFix" class="warehouse-fix-chip">需补科室</span>
                </td>
                <td>
                  <span class="warehouse-type-chip">{{ row.type || '-' }}</span>
                </td>
                <td class="warehouse-campus-cell">{{ row.campus || '未归属院区' }}</td>
                <td class="warehouse-dept-cell" :title="row.dept ? `${row.dept} (${row.deptCode || '-'})` : ''">
                  <strong>{{ row.dept || '-' }}</strong>
                  <code v-if="row.deptCode">{{ row.deptCode }}</code>
                </td>
                <td>
                  <span class="warehouse-flag-chip" :class="row.participateStats === '参与' ? 'enabled' : 'muted'">
                    {{ row.participateStats || '-' }}
                  </span>
                </td>
                <td>
                  <span class="warehouse-flag-chip" :class="row.receivingEnabled ? 'enabled' : 'muted'">
                    {{ row.receivingEnabled ? '允许' : '不允许' }}
                  </span>
                </td>
                <td class="warehouse-stats-cell" :title="String(row.statsCategories || '-')">
                  {{ row.statsCategories || '-' }}
                </td>
                <td>
                  <span
                    class="warehouse-status-chip"
                    :class="String(row.status) === '启用' ? 'enabled' : 'disabled'"
                  >
                    {{ row.status }}
                  </span>
                </td>
                <td class="warehouse-update-time">{{ row.updateTime || '-' }}</td>
                <td class="warehouse-location-count-cell">{{ row.locationCount ?? 0 }}</td>
                <td class="warehouse-action-cell">
                  <div class="warehouse-row-actions">
                    <button
                      v-if="authStore.canWrite(code)"
                      type="button"
                      class="btn-text"
                      @click="openEditWarehouse(row, false)"
                    >
                      编辑
                    </button>
                    <button
                      v-if="authStore.canWrite(code)"
                      type="button"
                      class="btn-text"
                      @click="openWarehouseLocationDialog(row)"
                    >
                      货位
                    </button>
                    <button
                      v-if="authStore.canWrite(code)"
                      type="button"
                      class="btn-text btn-text-danger"
                      @click="deleteSelectedWarehouses([String(row.code)])"
                    >
                      删除
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!page.rows.length">
                <td class="approval-empty" :colspan="13">暂无数据</td>
              </tr>
            </tbody>
          </table>
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

    <ProductDialog
      :open="productDialogOpen"
      :mode="productDialogMode"
      :form="productForm"
      :manufacturer-options="manufacturerOptions"
      :supplier-options="supplierOptions"
      @close="productDialogOpen = false"
      @save="saveProduct"
    />

    <BatchProductDialog
      :open="batchProductDialogOpen"
      :form="batchProductForm"
      @close="batchProductDialogOpen = false"
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
.md-hospital-page {
  display: grid;
  gap: 16px;
  padding: 0 24px 32px;
  min-width: 0;
  max-width: 100%;
  background: var(--md-bg-page);
  color: var(--md-text);
  font-family: var(--md-font);
  font-size: 14px;
  line-height: 1.5;
}

/* ===== 筛选行（对齐待审批目录 .approval-filter-row 的视觉与行为） ===== */
.md-hospital-filter {
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

.md-hospital-filter label {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.md-hospital-filter label > span {
  color: var(--md-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.md-hospital-filter-actions {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px dashed var(--md-border-soft);
  margin-top: 4px;
}

@media (max-width: 1400px) {
  .md-hospital-filter {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .md-hospital-filter {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .md-hospital-filter {
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
  padding: 0 24px 32px;
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
</style>
