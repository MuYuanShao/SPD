<script setup lang="ts">
/**
 * 科室库房目录 —— 方案 E「静墨宣纸」（独立页面）
 * ---------------------------------------------------------------------
 * 设计说明：
 * 1. 本页面是「科室库房目录」浅色原版的**第三套设计方案对照页**，
 *    功能、数据源、列宽逻辑完全一致，只有视觉表现不同；
 * 2. 原版页面（/features/department-warehouse-catalog）不受任何影响，
 *    本页面对应路由 /features/department-warehouse-catalog-calm；
 * 3. 设计定位：**长时间注视不疲劳、配色统一不杂**。
 *    与前三套方案（A 青玉 / B 蓝灰 / C 松竹绿）只换主色相不同，
 *    本方案同时降低页面的「视觉墨量」：
 *    - 宣纸暖白底（去冷蓝倾向，降低蓝光刺激）；
 *    - 表格取消竖网格线，只保留横向分隔线；
 *    - 取消斑马纹，表头改浅墨底而非主色实底；
 *    - 状态条底色透明度从 0.4 降到 0.14，避免满屏色块；
 *    - 全局色相收敛到墨青 + 松绿 / 赭石 / 灰蓝 / 中灰共 5 个以内；
 * 4. 主题由 data-spd-theme="calm-paper" 驱动，仅在本页面挂载期间生效。
 *    该属性与用户主题切换器使用的 data-theme 完全隔离，
 *    切换器里的主色 / 主题不会被本方案覆盖，离开页面自动还原。
 */
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'
import { Ban, ChevronDown, Columns3, ListPlus, Pencil, Plus, RefreshCw, Search, Trash2 } from '@lucide/vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import {
  useMasterDataWorkbench
} from '../../composables/useMasterDataWorkbench'
import { useOrganizationMasterData } from '../../composables/useOrganizationMasterData'
import { useSpdConfirm } from '../../composables/useSpdConfirm'
import { useAuthStore } from '../../stores/auth'
import { formatBusinessText, formatStatusText } from '../../utils/chineseDisplay'
import { formatDateTime } from '../../utils/dateTime'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import DepartmentWarehouseCatalogDialog from '../../components/master-data/DepartmentWarehouseCatalogDialog.vue'
import DepartmentWarehouseCatalogBatchDialog from '../../components/master-data/DepartmentWarehouseCatalogBatchDialog.vue'
import '../../styles/master-data/department-warehouse-catalog.css'

/** 方案标识：与用户主题（data-theme）隔离，避免误伤用户已选主题 */
const CALM_THEME_ID = 'calm-paper'
const CALM_THEME_ATTRIBUTE = 'data-spd-theme'
/** 本方案在功能目录中的编码 */
const CALM_CATALOG_CODE = 'department-warehouse-catalog-calm'

const authStore = useAuthStore()
const { ask, ConfirmHost: SpdConfirmHost } = useSpdConfirm()

/* ============== 主题接管 ==============
   进入本页时切到静墨宣纸，离开时恢复用户原本的主题。

   页面被 <keep-alive> 缓存，因此除了 onMounted / onBeforeUnmount，
   还必须处理 onActivated / onDeactivated：
   否则从本页切到别的页面时，配色会残留到全站。 */
let previousThemeAttribute: string | null = null
let previousThemeValue: string | null = null
let calmThemeApplied = false

function enterCalmTheme() {
  if (calmThemeApplied) return
  const root = document.documentElement
  previousThemeAttribute = root.getAttribute(CALM_THEME_ATTRIBUTE)
  previousThemeValue = root.getAttribute(CALM_THEME_ATTRIBUTE)
  root.setAttribute(CALM_THEME_ATTRIBUTE, CALM_THEME_ID)
  calmThemeApplied = true
}

function restoreTheme() {
  if (!calmThemeApplied) return
  const root = document.documentElement
  if (previousThemeAttribute === null) {
    root.removeAttribute(CALM_THEME_ATTRIBUTE)
  } else {
    root.setAttribute(CALM_THEME_ATTRIBUTE, previousThemeValue ?? '')
  }
  previousThemeAttribute = null
  previousThemeValue = null
  calmThemeApplied = false
}

onMounted(enterCalmTheme)
onActivated(enterCalmTheme)
onDeactivated(restoreTheme)
onBeforeUnmount(restoreTheme)

/* ============== 数据源 ==============
   复用主数据工作台与组织维护能力，保证数据与浅色原版完全一致。 */
const {
  page,
  loading,
  selectedCodes,
  selectedCampus,
  selectedDepartment,
  selectedDepartmentWarehouseCatalog,
  selectedWarehouse,
  campusDialogOpen,
  campusDialogMode,
  departmentDialogOpen,
  departmentDialogMode,
  warehouseDialogOpen,
  warehouseDialogMode,
  actionError,
  actionMessage,
  departmentWarehouseCatalogDialogOpen,
  departmentWarehouseCatalogDialogMode,
  catalogCurrentPage,
  catalogPageSize,
  catalogTotal,
  catalogPageTotal,
  departmentWarehouseCatalogQuery,
  loadPage,
  loadDepartmentWarehouseCatalogPage,
  changeDepartmentWarehouseCatalogPageSize,
  resetDepartmentWarehouseCatalogQuery,
  submitDepartmentWarehouseCatalogQuery,
  clearActionState
} = useMasterDataWorkbench({ codeOverride: CALM_CATALOG_CODE })

const {
  departmentWarehouseCatalogForm,
  departmentWarehouseCatalogBatchDialogOpen,
  departmentWarehouseCatalogBatchForm,
  departmentWarehouseCatalogBatchSaving,
  departmentWarehouseCatalogOptions,
  departmentWarehouseCatalogWarehouses,
  departmentWarehouseCatalogBatchWarehouses,
  departmentWarehouseLoading,
  departmentWarehouseSaving,
  openCreateDepartmentWarehouseCatalog,
  openEditDepartmentWarehouseCatalog,
  openBatchDepartmentWarehouseCatalog,
  saveDepartmentWarehouseCatalog,
  saveBatchDepartmentWarehouseCatalog,
  deleteSelectedDepartmentWarehouseCatalogs,
  updateSelectedDepartmentWarehouseCatalogStatus,
  loadDepartmentWarehouseCatalogBatchWarehouses,
  loadDepartmentWarehouseCatalogWarehouses,
  loadDepartmentWarehouseCatalogProducts,
  searchDepartmentWarehouseCatalogProducts
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

/* ============== 列配置（与浅色原版一致） ============== */
const CATALOG_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'scope', label: '科室 / 库房', width: 141, visible: true, weight: 3, minWidth: 119, maxWidth: 175 },
  { key: 'productCode', label: '商品编码', width: 119, visible: true, weight: 2, minWidth: 104, maxWidth: 150 },
  { key: 'productName', label: '商品名称', width: 171, visible: true, flexible: true, idealWidth: 300, weight: 9, minWidth: 104, maxWidth: 560 },
  { key: 'spec', label: '规格型号', width: 141, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'manufacturer', label: '生产厂家', width: 156, visible: true, flexible: true, idealWidth: 280, weight: 7, minWidth: 104, maxWidth: 420 },
  { key: 'source', label: '维护来源', width: 71, visible: true, weight: 2, minWidth: 56, maxWidth: 105 },
  { key: 'status', label: '状态', width: 71, visible: true, weight: 2, minWidth: 56, maxWidth: 92 },
  { key: 'updateTime', label: '更新时间', width: 126, visible: true, weight: 3, minWidth: 104, maxWidth: 175 }
]

const catalogCols = useListColumns({
  // 独立存储键：本方案的列宽调整不影响浅色原版
  storageKey: 'spd-department-warehouse-catalog-calm-cols-v1',
  defaults: CATALOG_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 48,
  fixedColumns: [
    { key: 'actions', label: '操作', width: 170, weight: 3, minWidth: 150, maxWidth: 210 }
  ]
})

const isCatalogColVisible = (key: string) => catalogCols.isColumnVisible(key)
const catalogColBinding = (key: string) => catalogCols.columnBinding(key)

/** el-table 引用：供 useListColumns 测量容器宽度做按比例铺满 */
const catalogTableRef = catalogCols.tableRef

/* ============== 展示辅助 ============== */
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

function formatListDateTime(value: unknown) {
  return formatDateTime(value)
}

function onCatalogSelectionChange(selection: Array<Record<string, unknown>>) {
  selectedCodes.value = selection.map((row) => String(row.code))
}

/** 写入权限沿用原功能码，暗色方案不新增权限点 */
const canWrite = computed(() => authStore.canWrite('department-warehouse-catalog'))
</script>

<template>
  <section class="master-page master-page--aligned spd-calm-page spd-unified-list">
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

    <MdDataTableCard
      v-if="page"
      class="md-table-card-catalog department-warehouse-catalog-list-card"
      :loading="loading"
    >
      <template #toolbar>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-primary"
          @click="openCreateDepartmentWarehouseCatalog"
        >
          <Plus :size="16" />
          <span>新增目录</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-primary"
          :disabled="!selectedCodes.length || loading"
          @click="openBatchDepartmentWarehouseCatalog"
        >
          <ListPlus :size="16" />
          <span>批量维护</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-primary"
          :disabled="selectedCodes.length !== 1 || loading"
          @click="openEditDepartmentWarehouseCatalog()"
        >
          <Pencil :size="16" />
          <span>编辑</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-primary"
          :disabled="!selectedCodes.length || loading"
          @click="updateSelectedDepartmentWarehouseCatalogStatus(1)"
        >
          <RefreshCw :size="16" />
          <span>启用</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-warning"
          :disabled="!selectedCodes.length || loading"
          @click="updateSelectedDepartmentWarehouseCatalogStatus(0)"
        >
          <Ban :size="16" />
          <span>停用</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="md-btn md-btn-danger"
          :disabled="!selectedCodes.length || loading"
          @click="deleteSelectedDepartmentWarehouseCatalogs()"
        >
          <Trash2 :size="16" />
          <span>删除</span>
        </button>
        <div class="ulc-col-settings">
          <button
            type="button"
            class="md-btn md-btn-primary"
            @click="catalogCols.settingsOpen.value = !catalogCols.settingsOpen.value"
          >
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
          @header-dragend="(width: number, oldWidth: number, column: any) => catalogCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
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
                <span class="ulc-strong">{{ row.deptName || '-' }}</span>
                <span class="ulc-sub">{{ row.warehouseName ? `库房名称：${row.warehouseName}` : '未关联库房' }}</span>
                <span class="ulc-sub">库房等级：{{ row.warehouseType || '-' }}</span>
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
          <el-table-column label="操作" align="center" fixed="right" v-bind="catalogCols.columnBinding('actions')">
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
                <button
                  type="button"
                  class="ulc-action is-danger"
                  @click="deleteSelectedDepartmentWarehouseCatalogs([String(row.code)])"
                >
                  删除
                </button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <div class="spd-calm-empty">
              <span>暂无符合条件的科室库房目录</span>
              <small>尝试调整筛选条件或新增目录</small>
            </div>
          </template>
        </el-table>
      </div>

      <template #pagination>
        <MdPagination
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

    <DepartmentWarehouseCatalogBatchDialog
      :open="departmentWarehouseCatalogBatchDialogOpen"
      :form="departmentWarehouseCatalogBatchForm"
      :options="departmentWarehouseCatalogOptions"
      :warehouses="departmentWarehouseCatalogBatchWarehouses"
      :saving="departmentWarehouseCatalogBatchSaving"
      @close="departmentWarehouseCatalogBatchDialogOpen = false"
      @change-dept="loadDepartmentWarehouseCatalogBatchWarehouses"
      @change-warehouse="loadDepartmentWarehouseCatalogBatchWarehouses"
      @search-products="searchDepartmentWarehouseCatalogProducts"
      @save="saveBatchDepartmentWarehouseCatalog"
    />

    <SpdConfirmHost />
  </section>
</template>

<style scoped>
/* 空态：沿用「主标题 + 提示」两级结构 */
.spd-calm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 12px;
}

.spd-calm-empty span {
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-base);
}

.spd-calm-empty small {
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
}
</style>
