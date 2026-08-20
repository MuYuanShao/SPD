<script setup lang="ts">
import {
  Ban,
  Database,
  Download,
  Edit3,
  FileDown,
  FileUp,
  Link2,
  ListPlus,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings,
  Trash2,
  Upload
} from '@lucide/vue'
import { useHospitalProductManagement } from '../../composables/useHospitalProductManagement'
import { useMasterDataExchange } from '../../composables/useMasterDataExchange'
import { useMasterDataWorkbench } from '../../composables/useMasterDataWorkbench'
import { useHospitalCatalogScrollSync } from '../../composables/useHospitalCatalogScrollSync'
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
  hospitalPageJumpInput,
  catalogCurrentPage,
  catalogPageSize,
  catalogPageJumpInput,
  hospitalTotal,
  hospitalPageTotal,
  hospitalPageSizeOptions,
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
  isSupplierManagement,
  isManufacturerManagement,
  isCampusManagement,
  isDepartmentManagement,
  isDepartmentWarehouseCatalog,
  isWarehouseManagement,
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
  submitHospitalPageJump,
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
  clearActionState
} = useMasterDataWorkbench()

const {
  columnNavTrackRef,
  columnNavDragging,
  hospitalTableWrapRef,
  tableScrollTrackRef,
  hospitalTableScrollState,
  hospitalTableThumbDragging,
  fallbackHospitalTableScrollWidth,
  hospitalTableScrollThumbStyle,
  startColumnNavDrag,
  moveColumnNavDrag,
  endColumnNavDrag,
  syncHospitalRailFromTable,
  scrollToHospitalColumnInTable,
  applyColumnGroup,
  startHospitalTableTrackDrag,
  moveHospitalTableTrackDrag,
  endHospitalTableTrackDrag
} = useHospitalCatalogScrollSync({
  isHospitalCatalog,
  loading,
  error,
  sortedColumns,
  activeHospitalColumn,
  activeColumnGroup,
  hospitalColumnGroups,
  hospitalColumnConfigs
})

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
  submitSelectedProducts,
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
    <header class="master-page-title">
      <div>
        <p>主数据与定数</p>
        <h2>{{ page?.title || '主数据' }}</h2>
        <span>{{ page?.description || '正在加载主数据...' }}</span>
      </div>
      <button class="btn" type="button" @click="loadPage">
        <RefreshCw :size="17" />
        刷新
      </button>
    </header>

    <section v-if="isHospitalCatalog" class="hospital-catalog-panel">
      <div class="hospital-action-row">
        <button v-if="authStore.canWrite(code)" type="button" class="btn btn-primary" @click="openCreateProduct">
          <Plus :size="17" />
          新增
        </button>
        <button v-if="authStore.canWrite(code)" class="btn" type="button" @click="openEditProduct()">
          <Pencil :size="17" />
          修改
        </button>
        <button v-if="authStore.canWrite(code)" class="btn" type="button" @click="batchEditSelectedProducts">
          <Edit3 :size="17" />
          批量修改
        </button>
        <button class="btn" type="button" @click="exportHospitalCatalog">
          <Download :size="17" />
          导出
        </button>
        <button v-if="authStore.canWrite(code)" type="button" class="btn btn-danger" @click="disableProducts()">
          <Ban :size="17" />
          停用
        </button>
        <button v-if="authStore.canWrite(code)" class="btn" type="button" @click="submitSelectedProducts">
          <Send :size="17" />
          提交
        </button>
        <el-popover :visible="columnSettingsOpen" @update:visible="columnSettingsOpen = $event" trigger="click" placement="bottom" width="240">
          <template #reference>
            <button class="btn" type="button" @click="columnSettingsOpen = !columnSettingsOpen">
              <Settings :size="17" />
              列设置</button>
          </template>
          <div class="column-settings-list">
            <label v-for="col in hospitalColumnConfigs" :key="col.prop" class="column-setting-item">
              <el-checkbox v-model="col.visible" size="small" />
              <span>{{ col.label }}</span>
            </label>
          </div>
        </el-popover>
      </div>

      <form class="hospital-query-grid" @submit.prevent="submitHospitalQuery">
        <label>
          <span>商品编码</span>
          <input v-model.trim="hospitalQuery.productCode" type="text" placeholder="模糊查询商品编码" />
        </label>
        <label>
          <span>商品名称</span>
          <input v-model.trim="hospitalQuery.productName" type="text" placeholder="模糊查询商品名称" />
        </label>
        <label>
          <span>规格</span>
          <input v-model.trim="hospitalQuery.specModel" type="text" placeholder="规格" />
        </label>
        <label>
          <span>型号</span>
          <input v-model.trim="hospitalQuery.model" type="text" placeholder="型号" />
        </label>
        <label>
          <span>厂家</span>
          <input v-model.trim="hospitalQuery.manufacturerName" type="text" placeholder="厂家名称" />
        </label>
        <label>
          <span>供应商</span>
          <input v-model.trim="hospitalQuery.supplierName" type="text" placeholder="供应商名称" />
        </label>
        <label>
          <span>单价</span>
          <input v-model.trim="hospitalQuery.price" type="text" placeholder="单价" />
        </label>
        <label>
          <span>是否集采</span>
          <select v-model="hospitalQuery.isCentralized">
            <option value="">全部</option>
            <option value="是">是</option>
            <option value="否">否</option>
          </select>
        </label>
        <label>
          <span>是否国产</span>
          <select v-model="hospitalQuery.isDomestic">
            <option value="">全部</option>
            <option value="是">是</option>
            <option value="否">否</option>
          </select>
        </label>
        <label>
          <span>招采子编码</span>
          <input v-model.trim="hospitalQuery.tenderCode" type="text" placeholder="招采子编码" />
        </label>
        <div class="hospital-query-actions">
          <button class="btn btn-primary" type="submit">
            <Search :size="17" />
            查询
          </button>
          <button class="btn" type="button" @click="resetHospitalQuery">重置</button>
        </div>
      </form>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isSupplierManagement" class="hospital-catalog-panel supplier-management-panel">
      <div class="supplier-toolbar" aria-label="供应商操作">
        <div class="supplier-toolbar-primary">
          <button type="button" class="btn btn-primary" @click="openCreateSupplier">
            <Plus :size="17" />
            新增供应商
          </button>
          <button class="btn" type="button" @click="openEditSupplier()">
            <Pencil :size="17" />
            修改
          </button>
        </div>
        <div class="supplier-toolbar-secondary">
          <details class="batch-edit-menu">
            <summary class="btn" aria-label="打开供应商导入菜单">
              <Upload :size="17" />
              导入
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
          <button class="btn" type="button" @click="exportSupplierRows">
            <Download :size="17" />
            导出
          </button>
          <button type="button" class="btn btn-danger" @click="disableSuppliers()">
            <Ban :size="17" />
            停用
          </button>
          <input ref="importInput" class="hidden-file-input" type="file" accept=".csv" @change="handleSupplierImport" />
        </div>
      </div>

      <div class="supplier-filter-panel">
        <div class="supplier-filter-heading">
          <div class="supplier-filter-title">
            <Search :size="17" aria-hidden="true" />
            <span>筛选供应商</span>
          </div>
          <small>支持按名称、业务类型和启停状态组合查询</small>
        </div>
        <form class="hospital-query-grid supplier-query-grid" role="search" @submit.prevent="loadPage">
          <label>
            <span>供应商名称</span>
            <input
              v-model.trim="supplierQuery.supplierName"
              type="search"
              placeholder="输入供应商名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>供应商类型</span>
            <input
              v-model.trim="supplierQuery.supplierType"
              type="search"
              placeholder="如：配送商、生产商"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <select v-model="supplierQuery.status" aria-label="筛选供应商启停状态">
              <option value="">全部状态</option>
              <option value="启用">启用</option>
              <option value="停用">停用</option>
            </select>
          </label>
          <div class="hospital-query-actions">
            <button class="btn btn-primary" type="submit">
              <Search :size="17" />
              查询
            </button>
            <button class="btn" type="button" @click="resetSupplierQuery">重置</button>
          </div>
        </form>
      </div>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isManufacturerManagement" class="hospital-catalog-panel manufacturer-management-panel">
      <div class="manufacturer-toolbar" aria-label="厂家操作">
        <div class="manufacturer-toolbar-primary">
          <button type="button" class="btn btn-primary" @click="openCreateManufacturer">
            <Plus :size="17" />
            新增厂家
          </button>
          <button class="btn" type="button" @click="openEditManufacturer()">
            <Pencil :size="17" />
            修改
          </button>
        </div>
        <div class="manufacturer-toolbar-secondary">
          <details class="batch-edit-menu">
            <summary class="btn" aria-label="打开厂家导入菜单">
              <Upload :size="17" />
              导入
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
          <button class="btn" type="button" @click="exportManufacturerRows">
            <Download :size="17" />
            导出
          </button>
          <button type="button" class="btn btn-danger" @click="disableManufacturers()">
            <Ban :size="17" />
            停用
          </button>
          <input
            ref="manufacturerImportInput"
            class="hidden-file-input"
            type="file"
            accept=".csv"
            @change="handleManufacturerImport"
          />
        </div>
      </div>

      <div class="manufacturer-filter-panel">
        <div class="manufacturer-filter-heading">
          <div class="manufacturer-filter-title">
            <Search :size="17" aria-hidden="true" />
            <span>筛选厂家档案</span>
          </div>
          <small>支持按厂家名称、生产许可证号和启停状态组合查询</small>
        </div>
        <form class="hospital-query-grid manufacturer-query-grid" role="search" @submit.prevent="loadPage">
          <label>
            <span>厂家名称</span>
            <input
              v-model.trim="manufacturerQuery.manufacturerName"
              type="search"
              placeholder="输入厂家名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>生产许可证号</span>
            <input
              v-model.trim="manufacturerQuery.licenseNo"
              type="search"
              placeholder="输入生产许可证号"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <select v-model="manufacturerQuery.status" aria-label="筛选厂家启停状态">
              <option value="">全部状态</option>
              <option value="启用">启用</option>
              <option value="停用">停用</option>
            </select>
          </label>
          <div class="hospital-query-actions">
            <button class="btn btn-primary" type="submit">
              <Search :size="17" />
              查询
            </button>
            <button class="btn" type="button" @click="resetManufacturerQuery">重置</button>
          </div>
        </form>
      </div>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isCampusManagement" class="hospital-catalog-panel campus-management-panel">
      <div class="campus-toolbar" aria-label="院区操作">
        <div class="campus-toolbar-primary">
          <button type="button" class="btn btn-primary" @click="openCreateCampus">
            <Plus :size="17" />
            新增院区
          </button>
          <button class="btn" type="button" @click="openEditCampus()">
            <Pencil :size="17" />
            编辑
          </button>
        </div>
        <div class="campus-toolbar-secondary">
          <button type="button" class="btn btn-danger" @click="deleteSelectedCampuses()">
            <Trash2 :size="17" />
            删除
          </button>
        </div>
      </div>

      <div class="campus-filter-panel">
        <div class="campus-filter-heading">
          <div class="campus-filter-title">
            <Search :size="17" aria-hidden="true" />
            <span>筛选院区档案</span>
          </div>
          <small>支持按院区编码、名称和启停状态组合查询</small>
        </div>
        <form class="hospital-query-grid campus-query-grid" role="search" @submit.prevent="loadPage">
          <label>
            <span>院区编码</span>
            <input
              v-model.trim="campusQuery.campusCode"
              type="search"
              placeholder="输入院区编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>院区名称</span>
            <input
              v-model.trim="campusQuery.campusName"
              type="search"
              placeholder="输入院区名称"
              autocomplete="off"
            />
          </label>
          <label>
            <span>启停状态</span>
            <select v-model="campusQuery.status" aria-label="筛选院区启停状态">
              <option value="">全部状态</option>
              <option value="启用">启用</option>
              <option value="停用">停用</option>
            </select>
          </label>
          <div class="hospital-query-actions">
            <button class="btn btn-primary" type="submit">
              <Search :size="17" />
              查询
            </button>
            <button class="btn" type="button" @click="resetCampusQuery">重置</button>
          </div>
        </form>
      </div>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isDepartmentManagement" class="hospital-catalog-panel department-management-panel">
      <div class="department-toolbar" aria-label="科室操作">
        <div class="department-toolbar-primary">
          <button type="button" class="btn btn-primary" @click="openCreateDepartment">
            <Plus :size="17" />
            新增科室
          </button>
          <button class="btn" type="button" @click="openEditDepartment()">
            <Pencil :size="17" />
            编辑
          </button>
          <button class="btn" type="button" @click="openDepartmentWarehouseDialog()">
            <Link2 :size="17" />
            关联库房
          </button>
        </div>
        <div class="department-toolbar-secondary">
          <details class="batch-edit-menu">
            <summary class="btn" aria-label="打开科室导入菜单">
              <Upload :size="17" />
              导入
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
          <button class="btn" type="button" @click="exportDepartmentRows">
            <Download :size="17" />
            导出
          </button>
          <button type="button" class="btn btn-danger" @click="deleteSelectedDepartments()">
            <Trash2 :size="17" />
            删除
          </button>
          <input
            ref="departmentImportInput"
            class="hidden-file-input"
            type="file"
            accept=".csv"
            @change="handleDepartmentImport"
          />
        </div>
      </div>

      <div class="department-filter-panel">
        <div class="department-filter-heading">
          <div class="department-filter-title">
            <Search :size="17" aria-hidden="true" />
            <span>筛选科室档案</span>
          </div>
          <small>支持按科室、类型、状态、源标识和关联库房组合查询</small>
        </div>
        <form class="hospital-query-grid department-management-query-grid" role="search" @submit.prevent="loadPage">
          <label>
            <span>科室名称 / 编码</span>
            <input
              v-model.trim="departmentQuery.deptKeyword"
              type="search"
              placeholder="输入科室名称或编码"
              autocomplete="off"
            />
          </label>
          <label>
            <span>科室类型</span>
            <input v-model.trim="departmentQuery.deptType" type="search" placeholder="如：临床科室" autocomplete="off" />
          </label>
          <label>
            <span>科室状态</span>
            <select v-model="departmentQuery.status" aria-label="筛选科室状态">
              <option value="">全部状态</option>
              <option value="正常">正常</option>
              <option value="停用">停用</option>
            </select>
          </label>
          <label>
            <span>源数据标识</span>
            <input v-model.trim="departmentQuery.sourceFlag" type="search" placeholder="输入源标识" autocomplete="off" />
          </label>
          <label>
            <span>关联库房</span>
            <input
              v-model.trim="departmentQuery.warehouseName"
              type="search"
              placeholder="输入库房名称"
              autocomplete="off"
            />
          </label>
          <div class="hospital-query-actions">
            <button class="btn btn-primary" type="submit">
              <Search :size="17" />
              查询
            </button>
            <button class="btn" type="button" @click="resetDepartmentQuery">重置</button>
          </div>
        </form>
      </div>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section
      v-else-if="isDepartmentWarehouseCatalog"
      class="hospital-catalog-panel department-warehouse-catalog-panel"
    >
      <div class="catalog-toolbar" aria-label="科室库房目录操作">
        <div class="catalog-toolbar-primary">
          <button type="button" class="btn btn-primary" @click="openCreateDepartmentWarehouseCatalog">
            <Plus :size="17" />
            新增目录
          </button>
          <button type="button" class="btn" @click="openBatchDepartmentWarehouseCatalog">
            <ListPlus :size="17" />
            批量维护
          </button>
          <button class="btn" type="button" @click="openEditDepartmentWarehouseCatalog()">
            <Pencil :size="17" />
            编辑
          </button>
        </div>
        <div class="catalog-toolbar-secondary">
          <button class="btn" type="button" @click="updateSelectedDepartmentWarehouseCatalogStatus(1)">
            <RefreshCw :size="17" />
            启用
          </button>
          <button type="button" class="btn btn-danger" @click="updateSelectedDepartmentWarehouseCatalogStatus(0)">
            <Ban :size="17" />
            停用
          </button>
          <button type="button" class="btn btn-danger" @click="deleteSelectedDepartmentWarehouseCatalogs()">
            <Trash2 :size="17" />
            删除
          </button>
        </div>
      </div>

      <div class="catalog-filter-panel">
        <div class="catalog-filter-heading">
          <div class="catalog-filter-title">
            <Search :size="17" aria-hidden="true" />
            <span>筛选可申领目录</span>
          </div>
          <small>按科室与库房定位申领范围，再通过商品编码、名称或状态缩小结果</small>
        </div>
        <form
          class="hospital-query-grid department-warehouse-catalog-query-grid"
          role="search"
          @submit.prevent="submitDepartmentWarehouseCatalogQuery"
        >
          <label>
            <span>科室</span>
            <input v-model.trim="departmentWarehouseCatalogQuery.deptName" type="search" placeholder="输入科室名称" autocomplete="off" />
          </label>
          <label>
            <span>库房</span>
            <input v-model.trim="departmentWarehouseCatalogQuery.warehouseName" type="search" placeholder="输入关联库房" autocomplete="off" />
          </label>
          <label>
            <span>商品</span>
            <input v-model.trim="departmentWarehouseCatalogQuery.productKeyword" type="search" placeholder="商品编码或名称" autocomplete="off" />
          </label>
          <label>
            <span>目录状态</span>
            <select v-model="departmentWarehouseCatalogQuery.status" aria-label="筛选目录状态">
              <option value="">全部状态</option>
              <option value="启用">启用</option>
              <option value="停用">停用</option>
            </select>
          </label>
          <div class="hospital-query-actions">
            <button class="btn btn-primary" type="submit">
              <Search :size="17" />
              查询
            </button>
            <button class="btn" type="button" @click="resetDepartmentWarehouseCatalogQuery">重置</button>
          </div>
        </form>
      </div>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
    </section>

    <section v-else-if="isWarehouseManagement" class="hospital-catalog-panel department-panel">
      <div class="hospital-action-row department-action-row">
        <button type="button" class="btn btn-primary" @click="openCreateWarehouse">
          <Plus :size="17" />
          新增
        </button>
        <button class="btn" type="button" @click="openEditWarehouse()">
          <Pencil :size="17" />
          编辑
        </button>
        <button type="button" class="btn btn-danger" @click="deleteSelectedWarehouses()">
          <Trash2 :size="17" />
          删除
        </button>
        <details class="batch-edit-menu">
          <summary class="btn">
            <Upload :size="17" />
            新导入Excel
          </summary>
          <div class="supplier-import-menu">
            <button class="btn-text" type="button" @click="downloadWarehouseTemplate">
              <FileDown :size="16" />
              导入模板下载
            </button>
            <button class="btn-text" type="button" @click="triggerWarehouseImport">
              <FileUp :size="16" />
              上传模板导入
            </button>
          </div>
        </details>
        <button class="btn" type="button" @click="exportWarehouseRows">
          <Download :size="17" />
          导出Excel
        </button>
        <button class="btn" type="button" @click="openWarehouseLocationDialog()">
          <Link2 :size="17" />
          维护货位
        </button>
        <input
          ref="warehouseImportInput"
          class="hidden-file-input"
          type="file"
          accept=".csv"
          @change="handleWarehouseImport"
        />
      </div>

      <form class="hospital-query-grid department-query-grid" @submit.prevent="loadPage">
        <label>
          <span>库房名称/编码</span>
          <input v-model.trim="warehouseQuery.warehouseKeyword" type="text" placeholder="请输入" />
        </label>
        <label>
          <span>库房类型</span>
          <input v-model.trim="warehouseQuery.warehouseType" type="text" placeholder="请输入" />
        </label>
        <label>
          <span>所属院区</span>
          <input v-model.trim="warehouseQuery.campusName" type="text" placeholder="请输入" />
        </label>
        <label>
          <span>关联科室</span>
          <input v-model.trim="warehouseQuery.deptName" type="text" placeholder="请输入" />
        </label>
        <label>
          <span>库房状态</span>
          <select v-model="warehouseQuery.status">
            <option value="">全部</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </label>
        <div class="hospital-query-actions">
          <button class="btn btn-primary" type="submit">
            <Search :size="17" />
            查询
          </button>
          <button class="btn" type="button" @click="resetWarehouseQuery">重置</button>
        </div>
      </form>
      <p v-if="actionError" class="error-text">{{ actionError }}</p>
      <p v-if="actionMessage" class="success-text">{{ actionMessage }}</p>
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

    <section class="master-table-card">
      <div v-if="isSupplierManagement" class="section-title supplier-list-heading">
        <div>
          <Database :size="20" />
          <h3>供应商档案</h3>
        </div>
        <div class="supplier-list-meta" aria-live="polite">
          <span>共 {{ page?.total ?? 0 }} 家</span>
          <span v-if="selectedCodes.length" class="selected">已选 {{ selectedCodes.length }} 家</span>
        </div>
      </div>
      <div v-else-if="isManufacturerManagement" class="section-title manufacturer-list-heading">
        <div>
          <Database :size="20" />
          <h3>生产厂家档案</h3>
        </div>
        <div class="manufacturer-list-meta" aria-live="polite">
          <span>共 {{ page?.total ?? 0 }} 家</span>
          <span v-if="selectedCodes.length" class="selected">已选 {{ selectedCodes.length }} 家</span>
        </div>
      </div>
      <div v-else-if="isCampusManagement" class="section-title campus-list-heading">
        <div>
          <Database :size="20" />
          <h3>院区基础档案</h3>
        </div>
        <div class="campus-list-meta" aria-live="polite">
          <span>共 {{ page?.total ?? 0 }} 个</span>
          <span v-if="selectedCodes.length" class="selected">已选 {{ selectedCodes.length }} 个</span>
        </div>
      </div>
      <div v-else-if="isDepartmentManagement" class="section-title department-list-heading">
        <div>
          <Database :size="20" />
          <h3>科室组织档案</h3>
        </div>
        <div class="department-list-meta" aria-live="polite">
          <span>共 {{ page?.total ?? 0 }} 个</span>
          <span v-if="selectedCodes.length" class="selected">已选 {{ selectedCodes.length }} 个</span>
        </div>
      </div>
      <div v-else-if="isDepartmentWarehouseCatalog" class="section-title catalog-list-heading">
        <div>
          <Database :size="20" />
          <h3>科室可申领商品目录</h3>
        </div>
        <div class="catalog-list-meta" aria-live="polite">
          <span>共 {{ catalogTotal }} 条</span>
          <span v-if="selectedCodes.length" class="selected">已选 {{ selectedCodes.length }} 条</span>
        </div>
      </div>
      <div v-else class="section-title">
        <Database :size="20" />
        <h3>{{ page?.title || '数据列表' }}</h3>
      </div>

      <p v-if="loading" class="approval-empty">正在加载...</p>
      <p v-else-if="error" class="approval-empty">{{ error }}</p>

      <div v-if="isHospitalCatalog && !loading && !error" class="column-nav-panel">
        <div class="column-nav-head">
          <strong>字段导航</strong>
          <span>已展示 {{ sortedColumns.length }} / {{ hospitalColumnConfigs.length }} 个字段</span>
        </div>
        <div class="subnav-tabs">
          <button
            v-for="group in hospitalColumnGroups"
            :key="group.key"
            type="button"
            :class="{ active: activeColumnGroup === group.key }"
            @click="applyColumnGroup(group.key)">
            {{ group.label }}
          </button>
        </div>
        <div
          ref="columnNavTrackRef"
          class="subnav-chips"
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
            @click="scrollToHospitalColumnInTable(col.prop)">
            {{ col.label }}
          </button>
        </div>
      </div>

      <!-- Hospital catalog: Element Plus dynamic columns -->
      <div
        v-if="isHospitalCatalog && !loading && !error"
        ref="hospitalTableWrapRef"
        class="hospital-product-table-wrap"
        @scroll="syncHospitalRailFromTable"
      >
        <table class="hospital-product-table" :style="{ minWidth: `${fallbackHospitalTableScrollWidth}px` }">
          <colgroup>
            <col style="width: 42px" />
            <col v-for="col in sortedColumns" :key="`col-${col.prop}`" :style="{ width: `${col.width}px` }" />
            <col style="width: 210px" />
          </colgroup>
          <thead>
            <tr>
              <th class="hospital-selection-cell">
                <input type="checkbox" :checked="allVisibleSelected" @change="toggleAllRows" />
              </th>
              <th v-for="col in sortedColumns" :key="col.prop">{{ col.label }}</th>
              <th class="hospital-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in page?.rows ?? []" :key="String(row.code)">
              <td class="hospital-selection-cell">
                <input v-model="selectedCodes" type="checkbox" :value="String(row.code)" />
              </td>
              <td v-for="col in sortedColumns" :key="col.prop" :title="String(row[col.prop] ?? '-')">
                {{ row[col.prop] ?? '-' }}
              </td>
              <td class="hospital-action-cell">
                <RouterLink
                  class="btn-text"
                  :to="{ name: 'hospital-product-detail', params: { productCode: String(row.code) } }"
                >查看</RouterLink>
                <button v-if="authStore.canWrite(code)" type="button" class="btn-text" @click="openEditProduct(String(row.code), false)">修改</button>
                <button v-if="authStore.canWrite(code)" type="button" class="btn-text btn-text-danger" @click="disableProducts([String(row.code)])">停用</button>
              </td>
            </tr>
            <tr v-if="!(page?.rows ?? []).length">
              <td class="approval-empty" :colspan="sortedColumns.length + 2">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-if="isHospitalCatalog && !loading && !error"
        class="hospital-table-scroll-rail"
      >
        <div
          ref="tableScrollTrackRef"
          class="hospital-table-scroll-track"
          :class="{ dragging: hospitalTableThumbDragging }"
          role="scrollbar"
          aria-orientation="horizontal"
          :aria-valuenow="Math.round(hospitalTableScrollState.scrollLeft)"
          :aria-valuemax="Math.max(0, hospitalTableScrollState.scrollWidth - hospitalTableScrollState.clientWidth)"
          tabindex="0"
          @pointerdown="startHospitalTableTrackDrag"
          @pointermove="moveHospitalTableTrackDrag"
          @pointerup="endHospitalTableTrackDrag"
          @pointercancel="endHospitalTableTrackDrag"
        >
          <span
            class="hospital-table-scroll-thumb"
            :style="hospitalTableScrollThumbStyle"
          ></span>
        </div>
      </div>
      <div v-if="isHospitalCatalog && !loading && !error" class="hospital-pagination">
        <div class="hospital-pagination-summary">
          共 {{ hospitalTotal }} 条，每页
          <select v-model.number="hospitalPageSize" @change="changeHospitalPageSize">
            <option v-for="size in hospitalPageSizeOptions" :key="size" :value="size">{{ size }}</option>
          </select>
          条
        </div>
        <div class="hospital-pagination-controls">
          <button
            class="btn"
            type="button"
            :disabled="hospitalCurrentPage <= 1"
            @click="loadHospitalPage(hospitalCurrentPage - 1)"
          >
            上一页
          </button>
          <span class="hospital-page-indicator">第 {{ hospitalCurrentPage }} / {{ hospitalPageTotal }} 页</span>
          <button
            class="btn"
            type="button"
            :disabled="hospitalCurrentPage >= hospitalPageTotal"
            @click="loadHospitalPage(hospitalCurrentPage + 1)"
          >
            下一页
          </button>
          <form class="hospital-page-jump" @submit.prevent="submitHospitalPageJump">
            <span>跳至</span>
            <input
              v-model.trim="hospitalPageJumpInput"
              type="number"
              min="1"
              :max="hospitalPageTotal"
              inputmode="numeric"
            />
            <span>页</span>
            <button class="btn" type="submit">跳转</button>
          </form>
        </div>
      </div>

      <div v-else-if="isSupplierManagement && page" class="supplier-table-wrap">
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
              <td class="approval-empty" colspan="11">暂无符合条件的供应商</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="isManufacturerManagement && page" class="manufacturer-table-wrap">
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

      <div v-else-if="isCampusManagement && page" class="campus-table-wrap">
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

      <div v-else-if="isDepartmentManagement && page" class="department-table-wrap">
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

      <div v-else-if="isDepartmentWarehouseCatalog && page" class="department-warehouse-catalog-table-wrap">
        <table class="master-table department-warehouse-catalog-table">
          <thead>
            <tr>
              <th class="selection-cell">
                <input
                  type="checkbox"
                  :checked="allVisibleSelected"
                  aria-label="选择当前页全部科室库房目录"
                  @change="toggleAllRows"
                />
              </th>
              <th>序号</th>
              <th>科室 / 库房</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>规格型号</th>
              <th>生产厂家</th>
              <th>维护来源</th>
              <th>状态</th>
              <th>更新时间</th>
              <th class="catalog-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in page.rows" :key="String(row.code)">
              <td class="selection-cell">
                <input
                  v-model="selectedCodes"
                  type="checkbox"
                  :value="String(row.code)"
                  :aria-label="`选择 ${String(row.deptName)} ${String(row.productName)} 目录`"
                />
              </td>
              <td class="catalog-sequence-cell">{{ row.sequenceNo }}</td>
              <td>
                <div class="catalog-scope-cell">
                  <strong>{{ row.deptName || '-' }}</strong>
                  <span>{{ row.warehouseName || '未关联库房' }}</span>
                </div>
              </td>
              <td class="catalog-code-cell">{{ row.productCode || '-' }}</td>
              <td>
                <div class="catalog-product-cell">
                  <strong>{{ row.productName || '-' }}</strong>
                  <span>科室可申领商品</span>
                </div>
              </td>
              <td class="catalog-spec-cell" :title="String(row.specModel || '-')">{{ row.specModel || '-' }}</td>
              <td class="catalog-manufacturer-cell" :title="String(row.manufacturerName || '-')">
                {{ row.manufacturerName || '-' }}
              </td>
              <td><span class="catalog-source-chip">{{ formatBusinessText(row.sourceType) }}</span></td>
              <td>
                <span class="catalog-status-chip" :class="String(row.status) === '启用' ? 'enabled' : 'disabled'">
                  {{ formatStatusText(row.status) }}
                </span>
              </td>
              <td class="catalog-update-time">{{ row.updateTime || '-' }}</td>
              <td class="catalog-action-cell">
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
              </td>
            </tr>
            <tr v-if="!page.rows.length">
              <td class="approval-empty" colspan="11">暂无符合条件的科室库房目录</td>
            </tr>
          </tbody>
        </table>
      </div>

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
              <template v-else-if="code === 'warehouse-location-management'">
                <button type="button" class="btn-text" @click="openEditWarehouse(row, false)">编辑</button>
                <button
                  type="button"
                  class="btn-text btn-text-danger"
                  @click="deleteSelectedWarehouses([String(row.code)])"
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
      <div v-if="isDepartmentWarehouseCatalog && page && !loading && !error" class="hospital-pagination">
        <div class="hospital-pagination-summary">
          共 {{ catalogTotal }} 条，每页
          <select v-model.number="catalogPageSize" @change="changeDepartmentWarehouseCatalogPageSize">
            <option v-for="size in catalogPageSizeOptions" :key="size" :value="size">{{ size }}</option>
          </select>
          条
        </div>
        <div class="hospital-pagination-controls">
          <button
            class="btn"
            type="button"
            :disabled="catalogCurrentPage <= 1"
            @click="loadDepartmentWarehouseCatalogPage(catalogCurrentPage - 1)"
          >
            上一页
          </button>
          <span class="hospital-page-indicator">第 {{ catalogCurrentPage }} / {{ catalogPageTotal }} 页</span>
          <button
            class="btn"
            type="button"
            :disabled="catalogCurrentPage >= catalogPageTotal"
            @click="loadDepartmentWarehouseCatalogPage(catalogCurrentPage + 1)"
          >
            下一页
          </button>
          <form class="hospital-page-jump" @submit.prevent="submitDepartmentWarehouseCatalogPageJump">
            <span>跳至</span>
            <input
              v-model.trim="catalogPageJumpInput"
              type="number"
              min="1"
              :max="catalogPageTotal"
              inputmode="numeric"
            />
            <span>页</span>
            <button class="btn" type="submit">跳转</button>
          </form>
        </div>
      </div>
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
      @close="closeWarehouseLocationDialog"
      @refresh="loadWarehouseLocations"
      @create="startCreateWarehouseLocation"
      @edit="startEditWarehouseLocation"
      @save="saveWarehouseLocation"
      @remove="removeWarehouseLocation"
    />
  </section>
</template>
