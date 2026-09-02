<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArchiveRestore,
  Ban,
  Boxes,
  CheckCircle2,
  ClipboardList,
  Download,
  Edit3,
  FileDown,
  History,
  PackageCheck,
  Plus,
  Printer,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  Upload,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import { useAuthStore } from '../../stores/auth'
import { fetchMasterDataPage } from '../../api/masterData'
import { useQuotaPackageDataLoader } from '../../composables/useQuotaPackageDataLoader'
import { useQuotaPackagePagination } from '../../composables/useQuotaPackagePagination'
import { useQuotaPackingTaskActions } from '../../composables/useQuotaPackingTaskActions'
import { useQuotaPackageViewState } from '../../composables/useQuotaPackageViewState'
import { useQuotaProductSelector } from '../../composables/useQuotaProductSelector'
import { useQuotaSafetyCatalog } from '../../composables/useQuotaSafetyCatalog'
import { useQuotaSafetyDialog } from '../../composables/useQuotaSafetyDialog'
import { useQuotaSafetyImportExport } from '../../composables/useQuotaSafetyImportExport'
import { useQuotaTemplateActions } from '../../composables/useQuotaTemplateActions'
import { useQuotaTemplateSelector } from '../../composables/useQuotaTemplateSelector'
import {
  labelStatusText,
  taskStatusText
} from '../../config/quotaPackageDisplay'
import {
  type PackageLabelRow,
  type PackingTaskRow,
  type QuotaSafetyRow,
  type QuotaTemplateRow,
  allocatePackingTaskFromReceiving,
  fetchPackingTaskAllocations,
  fetchReceivingLooseStock
} from '../../api/quotaPackages'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'
import QuotaProductSelectorDialog from '../../components/supply-chain/QuotaProductSelectorDialog.vue'
import QuotaSafetyDialog from '../../components/supply-chain/QuotaSafetyDialog.vue'
import QuotaTemplateSelectorDialog from '../../components/supply-chain/QuotaTemplateSelectorDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const message = ref('')
const loadError = ref('')
const templateDialogOpen = ref(false)
const templateDialogMode = ref<'create' | 'edit'>('create')
const templateError = ref('')
const selectedTemplateCodes = ref<string[]>([])
const importInput = ref<HTMLInputElement | null>(null)
const safetyImportInput = ref<HTMLInputElement | null>(null)
const templates = ref<QuotaTemplateRow[]>([])
const safetyRows = ref<QuotaSafetyRow[]>([])
const tasks = ref<PackingTaskRow[]>([])
const labels = ref<PackageLabelRow[]>([])
const events = ref<Record<string, unknown>[]>([])
const warehouses = ref<Array<{ warehouseName: string; warehouseType: string }>>([])
const candidates = ref<Array<Record<string, unknown>>>([])
const selectedTaskNo = ref('')
const taskReservations = ref<Record<string, unknown>[]>([])
const taskLoading = ref(false)
const safetyImporting = ref(false)

const query = reactive({
  taskNo: '',
  eventNo: '',
  eventType: '',
  templateCode: '',
  templateName: '',
  productCode: '',
  productName: '',
  deptName: '',
  labelNo: ''
})
const templateForm = reactive({
  templateCode: '',
  templateName: '',
  productCode: '',
  quantity: 1,
  unit: ''
})
const safetyForm = reactive({
  deptCode: '',
  deptName: '',
  warehouseName: '',
  templateCode: '',
  templateId: undefined as number | undefined,
  productCode: '',
  minQty: 1,
  maxQty: 3
})
const safetyWarehouseOptions = ref<Array<{ name: string; dept: string; deptCode: string }>>([])

async function loadSafetyWarehouses() {
  try {
    const page = await fetchMasterDataPage('warehouse-location-management', { page: '1', size: '200' })
    safetyWarehouseOptions.value = page.rows.map((row) => ({
      name: String(row.name ?? ''),
      dept: String(row.dept ?? ''),
      deptCode: String(row.deptCode ?? '')
    }))
  } catch (err) {
    safetyWarehouseOptions.value = []
  }
}

function selectSafetyWarehouse(warehouse: { name: string; dept: string; deptCode: string }) {
  safetyForm.warehouseName = warehouse.name
  safetyForm.deptName = warehouse.dept && warehouse.dept !== '-' ? warehouse.dept : ''
  safetyForm.deptCode = warehouse.deptCode
}

// ── 按验收单分配散货 ──
const allocateTaskNo = ref('')
const receivingNo = ref('')
const allocateQty = ref<number | null>(null)
const looseStockRows = ref<Record<string, unknown>[]>([])
const receivingAllocations = ref<Record<string, unknown>[]>([])

async function searchReceivingLooseStock() {
  const no = receivingNo.value.trim()
  if (!no) {
    message.value = '请输入验收单号'
    return
  }
  try {
    looseStockRows.value = await fetchReceivingLooseStock(no)
    if (!looseStockRows.value.length) {
      message.value = '该验收单没有可分配的散货库存'
    }
  } catch (error: any) {
    message.value = error?.response?.data?.message || error?.message || '验收单散货查询失败'
  }
}

async function loadReceivingAllocations() {
  if (!allocateTaskNo.value) {
    receivingAllocations.value = []
    return
  }
  try {
    receivingAllocations.value = await fetchPackingTaskAllocations(allocateTaskNo.value)
  } catch {
    receivingAllocations.value = []
  }
}

async function doAllocateFromReceiving(full: boolean) {
  if (!allocateTaskNo.value || !receivingNo.value.trim()) {
    message.value = '请先选择打包任务并输入验收单号'
    return
  }
  try {
    const result = await allocatePackingTaskFromReceiving(allocateTaskNo.value, {
      receivingNo: receivingNo.value.trim(),
      quantity: full ? null : allocateQty.value
    })
    message.value = full
      ? `${result.taskNo} 已按验收单 ${result.receivingNo} 全部打包分配 ${result.allocatedQty}`
      : `${result.taskNo} 已按验收单 ${result.receivingNo} 分配 ${result.allocatedQty}，剩余保持散货库存`
    allocateQty.value = null
    await loadReceivingAllocations()
    await searchReceivingLooseStock()
    await loadData()
  } catch (error: any) {
    message.value = error?.response?.data?.message || error?.message || '按验收单分配失败'
  }
}

watch(allocateTaskNo, () => {
  void loadReceivingAllocations()
})
const packingForm = reactive({
  templateCode: '',
  warehouseName: '',
  packageCount: 1,
  remark: ''
})
const {
  productSelectorOpen,
  productLoading,
  productQuery,
  quotaProducts,
  selectedProductInfo,
  searchQuotaProducts,
  openProductSelector,
  selectProduct
} = useQuotaProductSelector(templateForm)
const {
  templateSelectorOpen,
  templateSelectorQuery,
  showAllTemplates,
  filteredPackingTemplates,
  openTemplateSelector,
  selectPackingTemplate,
  fillPacking
} = useQuotaTemplateSelector({ templates, warehouses, packingForm })
const {
  safetyCatalogQuery,
  safetyCatalogPagination,
  filteredSafetyTemplates,
  pagedSafetyTemplates,
  resetSafetyCatalogQuery,
  selectSafetyTemplate,
  changeSafetyCatalogPage,
  changeSafetyCatalogPageSize
} = useQuotaSafetyCatalog({ templates, safetyForm })
const {
  safetyDialogOpen,
  safetyDialogMode,
  editingSafetyId,
  fillSafety,
  resetSafetyForm,
  openSafetyDialog,
  closeSafetyDialog,
  submitSafety
} = useQuotaSafetyDialog({
  safetyForm,
  message,
  safetyCatalogQuery,
  resetSafetyCatalogQuery,
  reload: loadData
})

// 打开安全量弹窗时预加载库房选项（关联库房搜索）
watch(safetyDialogOpen, (open) => {
  if (open) {
    loadSafetyWarehouses()
  }
})
const selectedTemplates = computed(() =>
  templates.value.filter((item) => selectedTemplateCodes.value.includes(item.templateCode))
)

const selectedPackingTemplate = computed(() =>
  templates.value.find((t) => t.templateCode === packingForm.templateCode)
)

const { mode, packageSection, title, subtitle } = useQuotaPackageViewState({
  routeCode: () => String(route.params.code),
  templates,
  safetyRows,
  tasks,
  labels,
  events,
  warehouses,
  candidates
})

const {
  quotaPagination,
  changeQuotaPage,
  changeQuotaPageSize,
  resetQuotaPages
} = useQuotaPackagePagination(loadData)

const { loadData: loadQuotaPackageData, templateQuery } = useQuotaPackageDataLoader({
  mode,
  packageSection,
  loading,
  loadError,
  query,
  quotaPagination,
  packingForm,
  templates,
  safetyRows,
  tasks,
  labels,
  events,
  warehouses,
  candidates
})

async function loadData() {
  await loadQuotaPackageData()
}

async function showGeneratedLabels() {
  query.labelNo = ''
  quotaPagination.labels.page = 1
  await router.push('/features/quota-label-unpack')
}

const {
  fillTemplate,
  resetTemplateForm,
  openCreateTemplate,
  openEditTemplate,
  submitTemplate,
  disableSelectedTemplates,
  enableSelectedTemplates,
  exportTemplates,
  downloadTemplateImportFile,
  triggerTemplateImport,
  handleTemplateImport
} = useQuotaTemplateActions({
  templates,
  selectedTemplates,
  selectedTemplateCodes,
  templateForm,
  packingForm,
  selectedProductInfo,
  templateDialogOpen,
  templateDialogMode,
  templateError,
  importInput,
  message,
  reload: loadData
})

const {
  triggerSafetyImport,
  downloadSafetyImportFile,
  exportSafetyRows,
  handleSafetyImport
} = useQuotaSafetyImportExport({
  safetyImportInput,
  safetyImporting,
  safetyRows,
  safetyPagination: quotaPagination.safety,
  query,
  message,
  reload: loadData
})

const {
  submitPackingTask,
  approveTask,
  cancelTask,
  terminateTask,
  terminateTaskByTaskNo,
  recalculateTask,
  viewTaskReservations,
  unpackLabel,
  printLabel
} = useQuotaPackingTaskActions({
  packingForm,
  message,
  taskLoading,
  selectedTaskNo,
  taskReservations,
  reload: loadData,
  showGeneratedLabels
})

onMounted(loadData)
watch(() => route.params.code, () => {
  resetQuotaPages()
  loadData()
})

watch(
  () => [
    safetyCatalogQuery.templateCode,
    safetyCatalogQuery.templateName,
    safetyCatalogQuery.deptName,
    safetyCatalogQuery.productName
  ],
  () => {
    safetyCatalogPagination.page = 1
  }
)
</script>

<template>
  <section class="purchase-page quota-page">
    <PageHeader
      eyebrow="院内 SPD · 定数包"
      :title="title"
      :description="subtitle"
    >
      <template #actions>
        <button class="btn" type="button" :disabled="loading" @click="loadData">
          <RefreshCw :size="18" />刷新
        </button>
      </template>
    </PageHeader>

    <StatusMessage :message="message" tone="success" />
    <StatusMessage :message="loadError" tone="error" />

    <section v-if="mode === 'safety' || packageSection === 'quota-template-maintenance' || packageSection === 'packing-task-confirmation'" class="hospital-catalog-panel">
      <div class="section-title">
        <Search :size="20" />
        <h3>查询条件</h3>
      </div>
      <div class="hospital-query-grid quota-query-grid">
        <label v-if="packageSection === 'packing-task-confirmation'"><span>任务号</span><input v-model="query.taskNo" placeholder="模糊查询任务号" /></label>
        <label><span>模板编码</span><input v-model="query.templateCode" placeholder="模糊查询模板编码" /></label>
        <label><span>模板名称</span><input v-model="query.templateName" placeholder="模糊查询模板名称" /></label>
        <label><span>商品编码</span><input v-model="query.productCode" placeholder="模糊查询商品编码" /></label>
        <label><span>商品名称</span><input v-model="query.productName" placeholder="模糊查询商品名称" /></label>
        <button class="btn btn-primary" type="button" @click="loadData">
          <Search :size="18" />
          查询
        </button>
      </div>
    </section>

    <template v-if="mode === 'template'">
      <section v-if="packageSection === 'quota-template-maintenance'" class="hospital-catalog-panel">
        <div class="section-title">
          <Boxes :size="20" />
          <h3>定数包模板维护</h3>
        </div>
        <div class="hospital-action-row">
          <button
            v-if="authStore.hasPermission('quota-template-maintenance:write')"
            class="btn btn-primary"
            type="button"
            aria-label="新增定数包模板"
            title="新增定数包模板"
            @click="openCreateTemplate"
          >
            <Plus :size="18" />
            新增
          </button>
          <button v-if="authStore.hasPermission('quota-template-maintenance:write')" class="btn" type="button" @click="openEditTemplate()">
            <Edit3 :size="18" />
            修改
          </button>
          <button v-if="authStore.hasPermission('quota-template-maintenance:write')" class="btn btn-danger" type="button" @click="disableSelectedTemplates()">
            <Ban :size="18" />
            停用
          </button>
          <button v-if="authStore.hasPermission('quota-template-maintenance:write')" class="btn" type="button" @click="enableSelectedTemplates()">
            <CheckCircle2 :size="18" />
            启用
          </button>
          <details v-if="authStore.hasPermission('quota-template-maintenance:write')" class="batch-edit-menu">
            <summary class="btn">
              <Upload :size="18" />
              导入
            </summary>
            <div class="supplier-import-menu">
              <button class="btn-text" type="button" @click="downloadTemplateImportFile">
                <FileDown :size="16" />
                导入模板下载
              </button>
              <button class="btn-text" type="button" @click="triggerTemplateImport">
                <Upload :size="16" />
                上传模板导入
              </button>
            </div>
          </details>
          <button class="btn" type="button" @click="exportTemplates">
            <Download :size="18" />
            导出
          </button>
          <input ref="importInput" class="hidden-file-input" type="file" accept=".csv" @change="handleTemplateImport" />
        </div>
        <div v-if="false" class="hospital-query-grid quota-form-grid">
          <label><span>模板编码</span><input v-model="templateForm.templateCode" placeholder="为空时自动生成" /></label>
          <label><span>模板名称</span><input v-model="templateForm.templateName" placeholder="自动生成" /></label>
          <label><span>商品编码</span><input v-model="templateForm.productCode" placeholder="启用定数管理商品" /></label>
          <label><span>包内数量</span><input v-model.number="templateForm.quantity" type="number" min="1" /></label>
          <label><span>单位</span><input v-model="templateForm.unit" placeholder="为空时取商品单位" /></label>
          <button class="btn btn-primary" type="button" @click="submitTemplate">
            <Save :size="18" />
            保存模板
          </button>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    :checked="templates.length > 0 && selectedTemplateCodes.length === templates.length"
                    @change="selectedTemplateCodes = ($event.target as HTMLInputElement).checked ? templates.map((item) => item.templateCode) : []"
                  />
                </th>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格</th>
                <th>厂家</th>
                <th>供应商</th>
                <th>版本</th>
                <th>数量</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="12" state="loading" message="正在加载定数包模板..." />
              <TableStateRow v-else-if="templates.length === 0" :colspan="12" message="暂无符合条件的定数包模板" />
              <tr v-for="row in templates" v-else :key="row.templateId">
                <td>
                  <input v-model="selectedTemplateCodes" type="checkbox" :value="row.templateCode" />
                </td>
                <td>{{ row.templateCode }}</td>
                <td>{{ row.templateName }}</td>
                <td>{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.supplierName }}</td>
                <td>V{{ row.versionNo || 1 }}<span v-if="row.currentVersion" class="muted-hint">（当前）</span></td>
                <td>{{ row.quantity }} {{ row.unit }}</td>
                <td><span class="status-badge" :class="row.status === '禁用' ? 'disabled' : 'enabled'">{{ formatStatusText(row.status) }}</span></td>
                <td>
                  <div class="row-actions">
                    <button v-if="authStore.hasPermission('quota-template-maintenance:write')" class="btn-text" type="button" @click="openEditTemplate(row)">
                      <Edit3 :size="15" />
                      修改
                    </button>
                    <button v-if="authStore.hasPermission('quota-template-maintenance:write')" class="btn-text btn-text-danger" type="button" @click="disableSelectedTemplates(row)">
                      <Ban :size="15" />
                      停用
                    </button>
                    <button v-if="row.status === '禁用' && authStore.hasPermission('quota-template-maintenance:write')" class="btn-text" type="button" @click="enableSelectedTemplates(row)">
                      <CheckCircle2 :size="15" />
                      启用
                    </button>
                    <button v-if="authStore.hasPermission('quota-packing-task:create')" class="btn-text" type="button" @click="fillPacking(row)">
                      <PackageCheck :size="15" />
                      打包
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="quotaPagination.templates.page"
          :size="quotaPagination.templates.size"
          :total="quotaPagination.templates.total"
          :loading="loading"
          @change-page="changeQuotaPage('templates', $event)"
          @change-size="changeQuotaPageSize('templates', $event)"
        />
      </section>

      <section v-if="packageSection === 'packing-task-confirmation'" class="hospital-catalog-panel">
        <div class="section-title">
          <PackageCheck :size="20" />
          <h3>打包任务确认</h3>
        </div>
        <div class="hospital-query-grid quota-form-grid packing-task-form-grid">
          <label>
            <span>模板编码</span>
            <div class="input-with-icon">
              <input v-model="packingForm.templateCode" placeholder="选择或输入模板编码" />
              <button class="btn-icon search-trigger" type="button" title="选择模板" @click="openTemplateSelector">
                <Search :size="16" />
              </button>
            </div>
          </label>
          <label>
            <span>库房</span>
            <select v-model="packingForm.warehouseName">
              <option v-for="item in warehouses" :key="item.warehouseName" :value="item.warehouseName">
                {{ item.warehouseName }}
              </option>
            </select>
          </label>
          <label><span>打包数量</span><input v-model.number="packingForm.packageCount" type="number" min="1" /></label>
          <label><span>备注</span><input v-model="packingForm.remark" placeholder="任务说明" /></label>
          <button v-if="authStore.hasPermission('quota-packing-task:create')" class="btn btn-primary" type="button" :disabled="taskLoading" @click="submitPackingTask">
            <Save :size="18" />
            {{ taskLoading ? '创建中...' : '新建任务' }}
          </button>
          <button v-if="authStore.hasPermission('quota-packing-task:terminate')" class="btn btn-danger" type="button" @click="terminateTaskByTaskNo">
            <Ban :size="18" />
            终止任务
          </button>
        </div>
        <div v-if="selectedPackingTemplate" class="packing-template-info">
          <div class="packing-template-info-header">
            <Boxes :size="16" />
            <span>已选模板</span>
            <strong>{{ selectedPackingTemplate.templateName }}</strong>
            <span class="status-badge enabled">{{ formatStatusText(selectedPackingTemplate.status) }}</span>
          </div>
          <div class="packing-template-info-body">
            <div class="info-item">
              <span class="info-label">模板编码</span>
              <span class="info-value">{{ selectedPackingTemplate.templateCode }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">商品名称</span>
              <span class="info-value">{{ selectedPackingTemplate.productName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">规格型号</span>
              <span class="info-value">{{ selectedPackingTemplate.specModel }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">生产厂家</span>
              <span class="info-value">{{ selectedPackingTemplate.manufacturerName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">每包数量</span>
              <span class="info-value">{{ selectedPackingTemplate.quantity }} {{ selectedPackingTemplate.unit }}/包</span>
            </div>
          </div>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-table packing-task-table">
            <thead>
              <tr>
                <th>任务号</th>
                <th>状态</th>
                <th>模板</th>
                <th>库房</th>
                <th>商品</th>
                <th>包数</th>
                <th>每包数量</th>
                <th>计划扣减散货</th>
                <th>预占散货</th>
                <th>预占批次</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="12" state="loading" message="正在加载打包任务..." />
              <TableStateRow v-else-if="tasks.length === 0" :colspan="12" message="暂无符合条件的打包任务" />
              <tr v-for="row in tasks" :key="row.taskNo">
                <td>{{ row.taskNo }}</td>
                <td>
                  <span
                    class="status-badge"
                    :class="{
                      enabled: row.status === 'confirmed',
                      disabled: row.status === 'cancelled' || row.status === 'terminated',
                      warning: row.status === 'need_recalculate',
                      pending: row.status === 'pending_confirm'
                    }"
                  >
                    {{ taskStatusText[row.status] || formatStatusText(row.status) }}
                  </span>
                </td>
                <td>{{ row.templateName }}</td>
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.packageCount }}</td>
                <td>{{ row.packageQuantity }}</td>
                <td>{{ row.plannedLooseQty }}</td>
                <td>{{ row.reservedLooseQty }}</td>
                <td>{{ row.reservationSummary || '-' }}</td>
                <td>{{ row.createTime }}</td>
                <td>
                  <div class="row-actions">
                    <button v-if="row.status === 'pending_confirm' && authStore.hasPermission('quota-packing-task:confirm')" class="btn-text" type="button" @click="approveTask(row)">
                      <CheckCircle2 :size="15" />
                      确认
                    </button>
                    <button
                      v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:recalculate')"
                      class="btn-text"
                      type="button"
                      @click="recalculateTask(row)"
                    >
                      <RefreshCw :size="15" />
                      重算
                    </button>
                    <button
                      v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:cancel')"
                      class="btn-text btn-text-danger"
                      type="button"
                      @click="cancelTask(row)"
                    >
                      <X :size="15" />
                      取消
                    </button>
                    <button
                      v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate' || row.status === 'confirmed') && authStore.hasPermission('quota-packing-task:terminate')"
                      class="btn-text btn-text-danger"
                      type="button"
                      @click="terminateTask(row)"
                    >
                      <Ban :size="15" />
                      终止
                    </button>
                    <button class="btn-text" type="button" @click="viewTaskReservations(row)">
                      <ClipboardList :size="15" />
                      预占明细
                    </button>
                    <span v-if="row.status !== 'pending_confirm' && row.status !== 'need_recalculate'">
                      {{ row.confirmTime || row.cancelTime || '-' }}
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="quotaPagination.tasks.page"
          :size="quotaPagination.tasks.size"
          :total="quotaPagination.tasks.total"
          :loading="loading"
          @change-page="changeQuotaPage('tasks', $event)"
          @change-size="changeQuotaPageSize('tasks', $event)"
        />

        <div class="receiving-allocate-panel">
          <div class="section-title compact">
            <PackageCheck :size="18" />
            <h3>按验收单分配散货</h3>
            <span class="muted-hint">分配数量需为每包数量的整数倍（自动增加打包数量）；部分分配后验收单剩余量保持散货库存</span>
          </div>
          <div v-if="authStore.hasPermission('quota-packing-task:create')" class="receiving-allocate-form">
            <label>
              <span>打包任务</span>
              <select v-model="allocateTaskNo">
                <option value="">请选择待确认打包任务</option>
                <option v-for="task in tasks" :key="task.taskNo" :value="task.taskNo">
                  {{ task.taskNo }}（{{ task.productName }}，已预占 {{ task.reservedLooseQty }}）
                </option>
              </select>
            </label>
            <label>
              <span>验收单号</span>
              <input v-model.trim="receivingNo" placeholder="输入验收单号" @keydown.enter.prevent="searchReceivingLooseStock" />
            </label>
            <button class="btn" type="button" @click="searchReceivingLooseStock">
              <Search :size="16" />
              查询散货
            </button>
            <label>
              <span>分配数量</span>
              <input v-model.number="allocateQty" type="number" min="0" placeholder="留空为全部" />
            </label>
            <button class="btn" type="button" :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length" @click="doAllocateFromReceiving(false)">
              分配
            </button>
            <button class="btn btn-primary" type="button" :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length" @click="doAllocateFromReceiving(true)">
              全部打包分配
            </button>
          </div>
          <div v-if="looseStockRows.length" class="table-scroll">
            <table class="master-table compact-table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th>系统批号</th>
                  <th>生产批次</th>
                  <th>可用散货</th>
                  <th>批次单价</th>
                  <th>单位</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in looseStockRows" :key="index">
                  <td>{{ row.productName }}（{{ row.productCode }}）</td>
                  <td>{{ row.systemBatchNo }}</td>
                  <td>{{ row.productionBatchNo || '-' }}</td>
                  <td>{{ row.availableQty }}</td>
                  <td>{{ row.unitPrice }}</td>
                  <td>{{ row.unit }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="receivingAllocations.length" class="table-scroll">
            <table class="master-table compact-table">
              <thead>
                <tr>
                  <th>验收单号</th>
                  <th>系统批号</th>
                  <th>生产批次</th>
                  <th>商品</th>
                  <th>已分配</th>
                  <th>单价</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in receivingAllocations" :key="String(row.reservationId)">
                  <td>{{ row.receivingNo }}</td>
                  <td>{{ row.systemBatchNo }}</td>
                  <td>{{ row.productionBatchNo || '-' }}</td>
                  <td>{{ row.productName }}（{{ row.productCode }}）</td>
                  <td>{{ row.reservedQty }}</td>
                  <td>{{ row.unitPrice }}</td>
                  <td>{{ row.status }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div v-if="selectedTaskNo" class="reservation-panel">
          <div class="section-title compact">
            <ClipboardList :size="18" />
            <h3>预占来源批次：{{ selectedTaskNo }}</h3>
          </div>
          <div class="table-scroll">
            <table class="master-table compact-table">
              <thead>
                <tr>
                  <th>系统批次</th>
                  <th>生产批号</th>
                  <th>效期</th>
                  <th>预占数量</th>
                  <th>单价</th>
                  <th>预占时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in taskReservations" :key="String(row.reservationId)">
                  <td>{{ row.systemBatchNo }}</td>
                  <td>{{ row.productionBatchNo || '-' }}</td>
                  <td>{{ row.expireDate || '-' }}</td>
                  <td>{{ row.reservedQty }}</td>
                  <td>{{ row.unitPrice }}</td>
                  <td>{{ row.createTime }}</td>
                </tr>
                <TableStateRow v-if="taskReservations.length === 0" :colspan="6" message="暂无预占明细" />
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section v-if="packageSection === 'quota-label-unpack'" class="hospital-catalog-panel">
        <div class="section-title">
          <ArchiveRestore :size="20" />
          <h3>定数包标签与解包</h3>
        </div>
        <div class="hospital-query-grid quota-query-grid">
          <label><span>标签号</span><input v-model="query.labelNo" placeholder="DYYYYMMDD000001" /></label>
          <label><span>模板编码</span><input v-model="query.templateCode" placeholder="模糊查询模板" /></label>
          <label><span>商品编码</span><input v-model="query.productCode" placeholder="模糊查询商品编码" /></label>
          <label><span>商品名称</span><input v-model="query.productName" placeholder="模糊查询商品" /></label>
          <label><span>科室</span><input v-model="query.deptName" placeholder="模糊查询科室" /></label>
          <button class="btn btn-primary" type="button" @click="loadData">
            <Search :size="18" />
            查询标签
          </button>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>标签号</th>
                <th>状态</th>
                <th>模板</th>
                <th>库房</th>
                <th>商品</th>
                <th>包数量</th>
                <th>来源批次</th>
                <th>打印次数</th>
                <th>生成时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="10" state="loading" message="正在加载定数包标签..." />
              <tr v-for="row in labels" :key="row.labelNo">
                <td>{{ row.labelNo }}</td>
                <td>
                  <span
                    class="status-badge"
                    :class="{
                      enabled: row.status === 'available',
                      pending: row.status === 'pending_print',
                      disabled: row.status === 'void'
                    }"
                  >
                    {{ labelStatusText[row.status] || formatStatusText(row.status) }}
                  </span>
                </td>
                <td>{{ row.templateName }}</td>
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.packageQuantity }}</td>
                <td>{{ row.sourceBatches || '-' }}</td>
                <td>{{ row.printCount }}</td>
                <td>{{ row.createTime }}</td>
                <td>
                  <div class="row-actions">
                    <button
                      v-if="(row.status === 'pending_print' || row.status === 'available') && authStore.hasPermission('quota-package-label:print')"
                      class="btn-text"
                      type="button"
                      @click="printLabel(row)"
                    >
                      <Printer :size="15" />
                      {{ row.status === 'pending_print' ? '打印' : '重打' }}
                    </button>
                    <button v-if="row.status === 'available' && authStore.hasPermission('quota-label-unpack:write')" class="btn-text" type="button" @click="unpackLabel(row)">
                      <ArchiveRestore :size="15" />
                      解包
                    </button>
                    <span v-if="row.status === 'void'">已解包</span>
                  </div>
                </td>
              </tr>
              <TableStateRow v-if="!loading && labels.length === 0" :colspan="10" message="暂无已生成的定数包标签" />
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="quotaPagination.labels.page"
          :size="quotaPagination.labels.size"
          :total="quotaPagination.labels.total"
          :loading="loading"
          @change-page="changeQuotaPage('labels', $event)"
          @change-size="changeQuotaPageSize('labels', $event)"
        />
      </section>

      <section v-if="packageSection === 'quota-package-events'" class="hospital-catalog-panel">
        <div class="section-title">
          <History :size="20" />
          <h3>定数包事件</h3>
        </div>
        <div class="hospital-query-grid quota-query-grid">
          <label><span>事件号</span><input v-model="query.eventNo" placeholder="模糊查询事件号" /></label>
          <label><span>标签号</span><input v-model="query.labelNo" placeholder="模糊查询标签号" /></label>
          <label><span>事件类型</span><input v-model="query.eventType" placeholder="如：pack、sign、consume" /></label>
          <button class="btn btn-primary" type="button" @click="loadData"><Search :size="18" />查询事件</button>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-detail-table">
            <thead>
              <tr>
                <th>事件号</th>
                <th>标签号</th>
                <th>事件类型</th>
                <th>前状态</th>
                <th>后状态</th>
                <th>数量</th>
                <th>时间</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="8" state="loading" message="正在加载定数包事件..." />
              <TableStateRow v-else-if="events.length === 0" :colspan="8" message="暂无符合条件的定数包事件" />
              <tr v-for="row in events" :key="String(row.eventNo)">
                <td>{{ row.eventNo }}</td>
                <td>{{ row.labelNo || '-' }}</td>
                <td>{{ formatBusinessText(row.eventType) }}</td>
                <td>{{ formatStatusText(row.statusBefore) }}</td>
                <td>{{ formatStatusText(row.statusAfter) }}</td>
                <td>{{ row.qtyChange }}</td>
                <td>{{ row.eventTime }}</td>
                <td>{{ formatRemarkText(row.remark) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="quotaPagination.events.page"
          :size="quotaPagination.events.size"
          :total="quotaPagination.events.total"
          :loading="loading"
          @change-page="changeQuotaPage('events', $event)"
          @change-size="changeQuotaPageSize('events', $event)"
        />
      </section>

      <section v-if="packageSection === 'packable-loose-snapshot'" class="hospital-catalog-panel">
        <div class="section-title">
          <ShieldCheck :size="20" />
          <h3>可打包散货快照</h3>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-detail-table">
            <thead>
              <tr>
                <th>库房</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>系统批次</th>
                <th>生产批号</th>
                <th>有效期</th>
                <th>批次单价</th>
                <th>可用数量</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="8" state="loading" message="正在加载可打包散货..." />
              <TableStateRow v-else-if="candidates.length === 0" :colspan="8" message="当前没有可打包散货" />
              <tr v-for="row in candidates" :key="`${row.warehouseName}-${row.systemBatchNo}`">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.systemBatchNo }}</td>
                <td>{{ row.productionBatchNo || '-' }}</td>
                <td>{{ row.expireDate || '-' }}</td>
                <td>¥ {{ Number(row.batchUnitPrice || 0).toFixed(2) }}</td>
                <td>{{ row.availableQty }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="hospital-catalog-panel">
        <div class="section-title">
          <ShieldCheck :size="20" />
          <h3>安全量维护</h3>
        </div>
        <div class="hospital-action-row">
          <button v-if="authStore.hasPermission('quota-safety-stock:write')" class="btn btn-primary" type="button" @click="openSafetyDialog()">
            <Plus :size="18" />
            新增维护
          </button>
          <button v-if="authStore.hasPermission('quota-safety-stock:write')" class="btn" type="button" :disabled="safetyImporting" @click="triggerSafetyImport">
            <Upload :size="18" />
            {{ safetyImporting ? '导入中' : '导入' }}
          </button>
          <button class="btn" type="button" @click="downloadSafetyImportFile">
            <FileDown :size="18" />
            导入模板
          </button>
          <button class="btn" type="button" @click="exportSafetyRows">
            <Download :size="18" />
            导出
          </button>
          <button v-if="editingSafetyId" class="btn" type="button" @click="resetSafetyForm">
            <X :size="18" />
            取消编辑
          </button>
          <input ref="safetyImportInput" class="hidden-file-input" type="file" accept=".csv,text/csv" @change="handleSafetyImport" />
        </div>
        <div class="hospital-query-grid quota-form-grid">
          <label><span>科室名称</span><input v-model="safetyForm.deptName" placeholder="如：骨科" /></label>
          <label><span>模板编码</span><input v-model="safetyForm.templateCode" placeholder="可为空" /></label>
          <label><span>商品编码</span><input v-model="safetyForm.productCode" placeholder="启用定数管理商品" /></label>
          <label><span>安全下限</span><input v-model.number="safetyForm.minQty" type="number" min="0" /></label>
          <label><span>安全上限</span><input v-model.number="safetyForm.maxQty" type="number" min="0" /></label>
          <button class="btn btn-primary" type="button" @click="submitSafety">
            <Save :size="18" />
            {{ editingSafetyId ? '保存修改' : '保存安全量' }}
          </button>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>科室</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>安全下限</th>
                <th>安全上限</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="10" state="loading" message="正在加载安全量配置..." />
              <TableStateRow v-else-if="safetyRows.length === 0" :colspan="10" message="暂无符合条件的安全量配置" />
              <tr v-for="row in safetyRows" :key="row.safetyId">
                <td>{{ row.deptName }}</td>
                <td>{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.templateCode }}</td>
                <td>{{ row.templateName }}</td>
                <td>{{ row.minQty }}</td>
                <td>{{ row.maxQty }}</td>
                <td><span class="status-badge enabled">{{ formatStatusText(row.status) }}</span></td>
                <td>{{ row.updateTime }}</td>
                <td>
                  <div class="row-actions">
                    <button v-if="authStore.hasPermission('quota-safety-stock:write')" class="btn-text" type="button" @click="openSafetyDialog(row)">
                      <Edit3 :size="15" />
                      编辑
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="quotaPagination.safety.page"
          :size="quotaPagination.safety.size"
          :total="quotaPagination.safety.total"
          :loading="loading"
          @change-page="changeQuotaPage('safety', $event)"
          @change-size="changeQuotaPageSize('safety', $event)"
        />
      </section>
    </template>

    <QuotaSafetyDialog
      v-if="safetyDialogOpen"
      :mode="safetyDialogMode"
      :safety-form="safetyForm"
      :catalog-query="safetyCatalogQuery"
      :templates="pagedSafetyTemplates"
      :warehouses="safetyWarehouseOptions"
      :filtered-count="filteredSafetyTemplates.length"
      :page="safetyCatalogPagination.page"
      :size="safetyCatalogPagination.size"
      :loading="loading"
      @close="closeSafetyDialog"
      @submit="submitSafety"
      @select-template="selectSafetyTemplate"
      @select-warehouse="selectSafetyWarehouse"
      @change-page="changeSafetyCatalogPage"
      @change-size="changeSafetyCatalogPageSize"
    />

    <div v-if="templateDialogOpen" class="attachment-preview-mask" @click.self="templateDialogOpen = false">
      <section class="supplier-dialog quota-template-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>定数包模板维护</p>
            <h3>{{ templateDialogMode === 'create' ? '新增定数包模板' : '修改定数包模板' }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="templateDialogOpen = false">
            <X :size="18" />
          </button>
        </header>
        <div class="supplier-form-grid">
          <label>
            <span>{{ templateDialogMode === 'create' ? '模板编码（自动生成）' : '模板编码' }}</span>
            <input
              v-model="templateForm.templateCode"
              readonly
              :placeholder="templateDialogMode === 'create' ? '商品编码 + 001 起自动生成' : ''"
            />
          </label>
          <label class="wide">
            <span>定数包名称（自动生成）</span>
            <input
              readonly
              :value="templateForm.templateName || ((selectedProductInfo?.productName || '') + '定数包')"
              placeholder="选择商品后自动生成：商品名称 + 定数包"
            />
          </label>
          <label class="wide">
            <span>商品编码</span>
            <div class="input-with-icon">
              <input v-model="templateForm.productCode" readonly placeholder="点击右侧按钮选择已启用定数管理的商品" />
              <button class="btn-icon search-trigger" type="button" title="选择商品" @click="openProductSelector">
                <Search :size="16" />
              </button>
            </div>
          </label>
          <template v-if="selectedProductInfo">
            <label><span>商品名称</span><input :value="selectedProductInfo.productName" readonly /></label>
            <label><span>规格型号</span><input :value="selectedProductInfo.specModel" readonly /></label>
            <label><span>品牌</span><input :value="selectedProductInfo.brand || '-'" readonly /></label>
            <label><span>生产厂家</span><input :value="selectedProductInfo.manufacturerName" readonly /></label>
            <label><span>单价</span><input :value="selectedProductInfo.purchasePrice ? '¥ ' + selectedProductInfo.purchasePrice : '-'" readonly /></label>
            <label><span>基本单位</span><input :value="selectedProductInfo.unit" readonly /></label>
            <label><span>中包装数量</span><input :value="selectedProductInfo.middlePackageQty || '-'" readonly /></label>
          </template>
          <label>
            <span>包内数量</span>
            <input v-model.number="templateForm.quantity" type="number" min="1" />
          </label>
          <label>
            <span>套包单位</span>
            <input value="包" readonly />
          </label>
          <p v-if="templateError" class="inline-message wide">{{ templateError }}</p>
          <div class="approval-action-row wide">
            <button class="btn btn-primary" type="button" @click="submitTemplate">
              <Save :size="18" />
              保存
            </button>
            <button class="btn" type="button" @click="templateDialogOpen = false">取消</button>
          </div>
        </div>
      </section>
    </div>

    <QuotaTemplateSelectorDialog
      v-model:open="templateSelectorOpen"
      v-model:show-all="showAllTemplates"
      :template-query="templateSelectorQuery"
      :templates="filteredPackingTemplates"
      @select="selectPackingTemplate"
    />

    <QuotaProductSelectorDialog
      v-model:open="productSelectorOpen"
      :loading="productLoading"
      :product-query="productQuery"
      :products="quotaProducts"
      @search="searchQuotaProducts"
      @select="selectProduct"
    />
  </section>
</template>
