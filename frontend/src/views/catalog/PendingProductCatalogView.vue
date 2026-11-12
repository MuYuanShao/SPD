<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, CheckCircle2, ChevronDown, ClipboardCheck, Clock3, Columns3, Download, FileDown, FileUp, Plus, RefreshCw, Search, Send, Upload, UserRound, X, XCircle } from '@lucide/vue'
import PendingProductQualificationAttachments from '../../components/catalog/PendingProductQualificationAttachments.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useApprovalTableScroll } from '../../composables/useApprovalTableScroll'
import { usePendingProductBatchApproval } from '../../composables/usePendingProductBatchApproval'
import { usePendingProductCreateForm } from '../../composables/usePendingProductCreateForm'
import { usePendingProductImportExport } from '../../composables/usePendingProductImportExport'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { formatDateTime } from '../../utils/dateTime'
import {
  approvalRiskLabels,
  approvalWideField
} from '../../composables/usePendingProductCatalogDisplay'
import { usePendingProductCatalogNavigation } from '../../composables/usePendingProductCatalogNavigation'
import { usePendingProductCatalogPagination } from '../../composables/usePendingProductCatalogPagination'
import {
  fetchPendingProductApplications,
  fetchPendingProductPartnerOptions,
  fetchPendingProductSourceDetail,
  fetchPendingProductSourceProducts,
  batchApprovePendingProductApplications,
  type PendingProductApplicationRow,
  type PendingProductSourceRow,
  type PendingProductTypeCount
} from '../../api/pendingProductApplications'
import type { PartnerOption } from '../../api/masterData'
import { useNotify } from '../../composables/useNotify'
import { useCatalogApprovalRefreshStore } from '../../stores/catalogApprovalRefresh'

const notify = useNotify()


const route = useRoute()
const router = useRouter()
const refreshStore = useCatalogApprovalRefreshStore()
const catalogQuery = ref({ ...route.query })
const active = ref(true)
let needsReload = true
let requestVersion = 0
let scrollPosition = 0
watch(() => route.fullPath, () => {
  if (route.name === 'pending-product-catalog') catalogQuery.value = { ...route.query }
})
const importMenuRef = ref<HTMLDetailsElement | null>(null)
const qualificationApplicationNo = ref('')
const qualificationModalOpen = ref(false)
const { selectType, selectScope, selectMineStatus } = usePendingProductCatalogNavigation({ route, router })

const approvalTypes = [
  {
    key: 'new',
    label: '新品准入',

    description: '审核新商品首次进入医院目录的准入资料、价格、资质与仓储属性。',
    columns: ['商品基础信息', '供应商资质', '注册证有效期', '采购价', '是否定数管理']
  },
  {
    key: 'change',
    label: '信息变更',

    description: '审核已入库商品的名称、规格、单位、厂家、供应商等字段变更。',
    columns: ['变更字段', '变更前', '变更后', '变更原因', '影响范围']
  },
  {
    key: 'qualification',
    label: '资质更新',

    description: '审核注册证、生产许可证、经营许可证、授权文件等资质材料更新。',
    columns: ['资质类型', '原有效期', '新有效期', '附件数量', '到期风险']
  },
  {
    key: 'price',
    label: '价格调整',
    description: '审核医院目录商品采购价调整及变更原因。',
    columns: ['商品信息', '原采购价', '新采购价', '变更原因']
  },
  {
    key: 'disable',
    label: '停用申请',
    description: '审核启用商品的停用申请及影响范围。',
    columns: ['商品信息', '当前状态', '停用原因']
  }
]

const activeTypeKey = computed(() => {
  const value = typeof catalogQuery.value.type === 'string' ? catalogQuery.value.type : 'new'
  return approvalTypes.some((item) => item.key === value) ? value : 'new'
})
const activeScope = computed(() => {
  const value = typeof catalogQuery.value.scope === 'string' ? catalogQuery.value.scope : 'todo'
  return value === 'mine' || value === 'handled' ? value : 'todo'
})
const mineStatusOptions = [
  {
    key: 'pending',
    label: '待审批目录',
    description: '查看当前用户发起且仍在初审或复审中的审批单据。'
  },
  {
    key: 'returned',
    label: '退回修改目录',
    description: '查看被退回修改、需要补充资料后重新提交的审批单据。'
  },
  {
    key: 'approved',
    label: '审批通过目录',
    description: '查看当前用户发起且已经审批通过的单据。'
  }
]
const activeMineStatus = computed(() => {
  const value = typeof catalogQuery.value.mineStatus === 'string' ? catalogQuery.value.mineStatus : 'pending'
  return mineStatusOptions.some((item) => item.key === value) ? value : 'pending'
})
const activeMineOption = computed(
  () => mineStatusOptions.find((item) => item.key === activeMineStatus.value) ?? mineStatusOptions[0]
)

const activeType = computed(() => approvalTypes.find((item) => item.key === activeTypeKey.value) ?? approvalTypes[0])
const isReturnedMineDirectory = computed(() => activeScope.value === 'mine' && activeMineStatus.value === 'returned')

const rows = ref<PendingProductApplicationRow[]>([])
const typeCounts = ref<PendingProductTypeCount[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
/** 申请类型筛选：与左侧审批队列联动 */
const approvalTypeFilter = ref('')
/** 审批状态筛选：后端暂不支持该维度，改为前端本地过滤 */
const approvalStatusFilter = ref('')
const message = ref('')
const showCreateModal = ref(false)
const totalItems = ref(0)
const selectedNos = ref<string[]>([])
const {
  createForm,
  createAttachments,
  attachmentInput,
  resetCreateForm,
  handleCreateAttachments,
  removeCreateAttachment,
  submitCreateForm
} = usePendingProductCreateForm({
  message,
  showCreateModal,
  reload: loadApplications
})

const duplicateAlertVisible = ref(false)
const manufacturerOptions = ref<PartnerOption[]>([])
const supplierOptions = ref<PartnerOption[]>([])
const sourceKeyword = ref('')
const sourceRows = ref<PendingProductSourceRow[]>([])
const sourceLoading = ref(false)
const sourceSelected = ref(false)
const isNewApplication = computed(() => createForm.applicationType === '新品准入')
const canEditInformation = computed(() => isNewApplication.value || createForm.applicationType === '信息变更')
const canEditPrice = computed(() => canEditInformation.value || createForm.applicationType === '价格调整')
const canEditQualification = computed(() => canEditInformation.value || createForm.applicationType === '资质更新')

async function searchSourceProducts() {
  if (isNewApplication.value) return
  sourceLoading.value = true
  try {
    const page = await fetchPendingProductSourceProducts(sourceKeyword.value)
    sourceRows.value = page.rows
  } catch (err) {
    message.value = err instanceof Error ? err.message : '医院目录商品搜索失败'
    sourceRows.value = []
  } finally {
    sourceLoading.value = false
  }
}

async function selectSourceProduct(row: PendingProductSourceRow) {
  try {
    const type = createForm.applicationType
    const detail = await fetchPendingProductSourceDetail(row.productCode)
    Object.assign(createForm, detail, { applicationType: type, qualificationAttachmentCount: 0 })
    sourceSelected.value = true
    sourceKeyword.value = `${row.productCode} ${row.productName}`
    sourceRows.value = []
  } catch (err) {
    message.value = err instanceof Error ? err.message : '医院目录商品详情加载失败'
  }
}

async function handleApplicationTypeChange() {
  sourceSelected.value = isNewApplication.value
  sourceKeyword.value = ''
  sourceRows.value = []
  if (!isNewApplication.value) await searchSourceProducts()
}

function withRetainedOption(list: PartnerOption[], current: string | undefined) {
  if (!current || list.some((item) => item.name === current)) {
    return list
  }
  return [...list, { code: '-', name: current, status: 0 }]
}

/**
 * 选择厂家时自动回填该厂家的生产许可证号。
 */
function handleManufacturerChange() {
  const name = createForm.manufacturerName ?? ''
  const option = manufacturerOptions.value.find((item) => item.name === name)
  if (option) {
    createForm.productionLicenseNo = option.licenseNo ?? ''
  }
}

/**
 * 选择供应商时自动回填该供应商的经营许可证号。
 */
function handleSupplierChange() {
  const name = createForm.supplierName ?? ''
  const option = supplierOptions.value.find((item) => item.name === name)
  if (option) {
    createForm.businessLicenseNo = option.businessLicenseNo ?? ''
  }
}

async function loadPartnerOptions() {
  try {
    const options = await fetchPendingProductPartnerOptions()
    manufacturerOptions.value = options.manufacturers
    supplierOptions.value = options.suppliers
  } catch (err) {
    console.error('厂家与供应商选项加载失败', err)
    manufacturerOptions.value = []
    supplierOptions.value = []
  }
}

function triggerAttachmentPicker() {
  const input = attachmentInput.value as HTMLInputElement | null
  if (!input || input.disabled) return
  input.click()
}

async function openCreateModal() {
  notify.success('')
  resetCreateForm()
  showCreateModal.value = true
  sourceSelected.value = true
  await loadPartnerOptions()
}

function handleImportMenuToggle(e: Event) {
  /* 关闭其他打开的 menu（虽然这里只有一个，但保持模式一致） */
  if ((e.target as HTMLDetailsElement).open) {
    const all = document.querySelectorAll('.import-menu')
    all.forEach((el) => { if (el !== e.target) (el as HTMLDetailsElement).open = false })
  }
}

function handleOutsideClick(e: MouseEvent) {
  if (!importMenuRef.value) return
  if (!importMenuRef.value.contains(e.target as Node)) {
    importMenuRef.value.open = false
  }
}

async function handleCreateSubmit() {
  notify.success('')
  if (!isNewApplication.value && !sourceSelected.value) {
    notify.warning('请先搜索并选择医院目录商品')
    return
  }
  try {
    await submitCreateForm()
  } catch (err) {
    const text = err instanceof Error ? err.message : ''
    if (text.includes('商品目录已存在')) {
      duplicateAlertVisible.value = true
    } else {
      message.value = text || '提交失败，请稍后重试'
    }
  }
}

const {
  importInput,
  downloadTemplate,
  handleImportFile,
  exportRows
} = usePendingProductImportExport({
  activeTypeKey,
  activeScope,
  keyword,
  message,
  error,
  reload: loadApplications
})

const typeCountMap = computed(() => new Map(typeCounts.value.map((item) => [item.key, item.count])))

const taskGroups = computed(() => [
  {
    label: '待我审批',
    count: approvalTypes.reduce((total, item) => total + (typeCountMap.value.get(item.key) ?? 0), 0),
    icon: Clock3,
    tone: 'orange',
    children: approvalTypes.map((item) => ({
      ...item,
      count: typeCountMap.value.get(item.key) ?? 0,
      mineStatus: ''
    }))
  },
  {
    label: '我发起的',
    count:
      (typeCountMap.value.get('mine-pending') ?? 0) +
      (typeCountMap.value.get('mine-returned') ?? 0) +
      (typeCountMap.value.get('mine-approved') ?? 0),
    icon: UserRound,
    tone: 'blue',
    scope: 'mine',
    children: mineStatusOptions.map((item) => ({
      ...item,
      count: typeCountMap.value.get(`mine-${item.key}`) ?? 0,
      mineStatus: item.key
    }))
  },
  {
    label: '已处理/抄送',
    count: typeCountMap.value.get('handled') ?? 0,
    icon: CheckCircle2,
    tone: 'green',
    scope: 'handled'
  }
])

const allVisibleSelected = computed(() => {
  return rows.value.length > 0 && rows.value.every((row) => selectedNos.value.includes(row.no))
})

const {
  approvalTableShellRef,
  approvalScrollTrackRef,
  approvalScrollDragging,
  approvalScrollState,
  updateApprovalScrollState,
  handleApprovalScroll,
  jumpApprovalTableScroll,
  startApprovalScrollDrag,
  moveApprovalScrollDrag,
  endApprovalScrollDrag
} = useApprovalTableScroll()

const { currentPage, pageSize, changePage, changePageSize, resetPage } =
  usePendingProductCatalogPagination({
    totalItems,
    reload: loadApplications
  })

function toggleAllRows() {
  selectedNos.value = allVisibleSelected.value ? [] : rows.value.map((row) => row.no)
}

const {
  batchApproveLoading,
  showBatchApproveModal,
  batchApproveAction,
  batchApproveOpinion,
  openBatchApprove,
  confirmBatchApprove
} = usePendingProductBatchApproval({
  selectedNos,
  error,
  message,
  batchApprove: batchApprovePendingProductApplications,
  reload: loadApplications
})

// ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
// 固定列（不允许隐藏）：审批单号、商品名称 / 规格、操作
const APPROVAL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'type', label: '申请类型', width: 120, visible: true },
  { key: 'applicant', label: '申请人', width: 110, visible: true },
  { key: 'time', label: '提交时间', width: 180, visible: true },
  { key: 'productCode', label: '商品编码', width: 150, visible: true },
  { key: 'specModel', label: '规格型号', width: 150, visible: true },
  { key: 'supplier', label: '供应商', width: 180, visible: true },
  { key: 'manufacturer', label: '厂家', width: 180, visible: true },
  { key: 'registrationNo', label: '注册证号', width: 160, visible: true },
  { key: 'contractCode', label: '合同编码', width: 150, visible: true },
  { key: 'firstCategory', label: '一级分类', width: 130, visible: true },
  { key: 'secondCategory', label: '二级分类', width: 130, visible: true },
  { key: 'thirdCategory', label: '三级分类', width: 130, visible: true },
  { key: 'volumeBased', label: '是否带量', width: 110, visible: true },
  { key: 'centralized', label: '是否集采', width: 110, visible: true },
  { key: 'domestic', label: '是否国产', width: 110, visible: true },
  { key: 'chargeable', label: '是否收费', width: 110, visible: true },
  { key: 'keyMonitored', label: '重点监控', width: 120, visible: true },
  { key: 'purchasePrice', label: '采购价', width: 120, visible: true },
  { key: 'udiCode', label: 'UDI 编码', width: 180, visible: true },
  { key: 'risk', label: '风险标签', width: 180, visible: true },
  { key: 'wait', label: '节点 / 等待', width: 150, visible: true },
  { key: 'changeSummary', label: '变更记录', width: 200, visible: true }
]

const approvalCols = useListColumns({
  storageKey: 'spd-pending-product-approval-cols-v1',
  defaults: APPROVAL_COL_DEFAULTS
})

const isColVisible = (key: string) => approvalCols.isColumnVisible(key)
const approvalColWidth = (key: string) => approvalCols.columnWidth(key)
const startApprovalColDrag = (key: string, event: MouseEvent) => approvalCols.startColumnDrag(key, event)
const visibleDataColumnCount = computed(() => approvalCols.visibleColumnCount.value)
const approvalTableSpan = computed(() => visibleDataColumnCount.value + 4)

/** 提交时间统一输出 YYYY-MM-DD HH:MM:SS */
function approvalTimeText(row: PendingProductApplicationRow) {
  return formatDateTime(row.time)
}

/** 申请类型筛选：与左侧审批队列保持同步 */
function onApprovalTypeFilterChange(value: string) {
  if (value && value !== activeTypeKey.value) {
    selectType(value)
  } else if (!value && activeScope.value === 'todo') {
    selectType('new')
  }
}

/** 重置筛选条件 */
function resetApprovalFilter() {
  keyword.value = ''
  approvalStatusFilter.value = ''
  approvalTypeFilter.value = activeScope.value === 'todo' ? activeTypeKey.value : ''
  currentPage.value = 1
  void loadApplications()
}

/** 申请类型状态条配色 */
function approvalTypeChipClass(type: string): string {
  const map: Record<string, string> = {
    新品准入: 'is-primary',
    信息变更: 'is-info',
    资质更新: 'is-warning',
    价格调整: 'is-success',
    停用申请: 'is-danger'
  }
  return map[type] ?? 'is-neutral'
}

/** 风险标签状态条配色 */
function approvalRiskChipClass(label: string): string {
  if (label.includes('临期') || label.includes('价格') || label.includes('库房') || label.includes('重点')) return 'is-warning'
  if (label.includes('冷链') || label.includes('高值')) return 'is-danger'
  if (label.includes('变更')) return 'is-info'
  return 'is-success'
}

/** 审批节点状态条配色 */
function approvalStatusChipClass(tone?: string): string {
  const map: Record<string, string> = {
    orange: 'is-warning',
    blue: 'is-info',
    red: 'is-danger',
    green: 'is-success'
  }
  return map[tone ?? ''] ?? 'is-neutral'
}

/** 审批状态本地过滤：仅保留匹配状态的行 */
const filteredRows = computed(() => {
  const status = approvalStatusFilter.value
  if (!status) return rows.value
  return rows.value.filter((row) => row.status === status)
})

/** 本地过滤后的总数（状态筛选不改变分页总数口径） */
const displayTotal = computed(() => {
  if (!approvalStatusFilter.value) return totalItems.value
  return filteredRows.value.length
})

/** 表格最小宽度 = 固定列（勾选 48 + 审批单号 170 + 商品 250 + 操作 250）+ 各可见数据列宽之和 */
const FIXED_COL_WIDTH = 48 + 170 + 250 + 250
const approvalTableMinWidth = computed(() => {
  const dynamicWidth = approvalCols.visibleColumns.value.reduce((sum, col) => sum + col.width, 0)
  return FIXED_COL_WIDTH + dynamicWidth
})

async function loadApplications() {
  if (!active.value || route.name !== 'pending-product-catalog') { needsReload = true; return }
  const request = ++requestVersion
  loading.value = true
  error.value = ''

  try {
    const data = await fetchPendingProductApplications(
      activeTypeKey.value,
      activeScope.value,
      keyword.value,
      activeMineStatus.value,
      currentPage.value,
      pageSize.value
    )
    if (request !== requestVersion || !active.value) return
    const lastPage = Math.max(1, Math.ceil(data.total / pageSize.value))
    if (currentPage.value > lastPage) {
      currentPage.value = lastPage
      await loadApplications()
      return
    }
    needsReload = false
    rows.value = data.rows
    typeCounts.value = data.summary?.typeCounts ?? data.typeCounts
    totalItems.value = data.total
    selectedNos.value = []
  } catch (err) {
    if (request !== requestVersion || !active.value) return
    needsReload = true
    rows.value = []
    typeCounts.value = []
    totalItems.value = 0
    error.value = err instanceof Error ? err.message : '待审批目录加载失败'
  } finally {
    if (request === requestVersion) loading.value = false
  }
}

function rowPrimaryActionLabel() {
  if (isReturnedMineDirectory.value) return '修改'
  if (activeScope.value !== 'todo') return '查看'
  return '审批'
}

function rowDetailRoute(row: PendingProductApplicationRow) {
  return {
    name: 'pending-product-approval-detail',
    params: { applicationNo: row.no },
    query: { ...catalogQuery.value }
  }
}

function rowEditRoute(row: PendingProductApplicationRow) {
  return {
    name: 'pending-product-approval-detail',
    params: { applicationNo: row.no },
    query: { ...catalogQuery.value, ...(isReturnedMineDirectory.value ? { edit: '1' } : {}) }
  }
}

onMounted(() => {
  void loadApplications()
  requestAnimationFrame(updateApprovalScrollState)
  document.addEventListener('click', handleOutsideClick)
})
watch([activeTypeKey, activeScope, activeMineStatus], () => {
  resetPage()
  // 侧边栏切换类型时，同步顶部申请类型筛选框
  if (activeScope.value === 'todo') {
    approvalTypeFilter.value = activeTypeKey.value
  } else {
    approvalTypeFilter.value = ''
  }
  void loadApplications()
})
watch(() => refreshStore.applicationsVersion, () => {
  needsReload = true
  if (active.value && route.name === 'pending-product-catalog' && !batchApproveLoading.value) void loadApplications()
})
onBeforeRouteLeave(() => {
  if (batchApproveLoading.value) return false
  scrollPosition = document.querySelector('.spd-content')?.scrollTop ?? 0
})
onActivated(async () => {
  active.value = true
  if (needsReload && !loading.value) await loadApplications()
  await nextTick()
  if (!active.value || route.name !== 'pending-product-catalog') return
  const container = document.querySelector('.spd-content')
  if (container) container.scrollTop = scrollPosition
})
onDeactivated(() => {
  active.value = false
  requestVersion++
  if (loading.value) needsReload = true
  loading.value = false
})
onUnmounted(() => document.removeEventListener('click', handleOutsideClick))
watch(rows, () => requestAnimationFrame(updateApprovalScrollState))
</script>

<template>
  <section class="approval-page spd-unified-list">
    <MdPageHeader
      title="待审批目录"
      description="审核商品准入、信息变更、资质更新与价格调整申请。"
      :loading="loading"
      @refresh="loadApplications"
    />

    <div class="approval-workspace">
      <aside class="approval-sidebar-card">
        <div class="approval-sidebar-title">
          <ClipboardCheck :size="22" />
          <div>
            <h3>审批队列</h3>
            <p>按处理责任和任务类型筛选。</p>
          </div>
        </div>

        <div class="approval-task-list">
          <section v-for="group in taskGroups" :key="group.label" class="approval-task-group">
            <button
              v-if="group.scope"
              type="button"
              class="approval-task-item scope-button"
              :class="[group.tone, { active: activeScope === group.scope }]"
              @click="selectScope(group.scope)"
            >
              <div>
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </div>
              <strong>{{ group.count }}</strong>
            </button>
            <div v-else class="approval-task-item" :class="group.tone">
              <div>
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </div>
              <strong>{{ group.count }}</strong>
            </div>

            <div v-if="group.children" class="approval-sub-list">
              <button
                v-for="child in group.children"
                :key="child.key"
                type="button"
                :class="{ active: group.scope === 'mine' ? child.mineStatus === activeMineStatus && activeScope === 'mine' : child.key === activeTypeKey && activeScope === 'todo' }"
                @click="group.scope === 'mine' ? selectMineStatus(child.mineStatus) : selectType(child.key)"
              >
                <span>{{ child.label }}</span>
                <em>{{ child.count }}</em>
              </button>
            </div>
          </section>
        </div>
      </aside>

      <main class="approval-list-card">
        <StatusMessage :message="message" tone="info" />

        <!-- 筛选区：与统一列表其他页面保持一致（字段 + 重置/查询） -->
        <form class="approval-filter-row md-hospital-filter ulc-filter" @submit.prevent="currentPage = 1; loadApplications()">
          <div class="ulc-filter-fields">
            <label>
              <span>审批单号 / 商品名称</span>
              <div class="approval-search">
                <Search :size="16" />
                <input v-model="keyword" type="search" placeholder="输入审批单号、商品名称" autocomplete="off" />
              </div>
            </label>
            <label>
              <span>申请类型</span>
              <el-select
                v-model="approvalTypeFilter"
                placeholder="全部类型"
                clearable
                class="ulc-filter-select"
                aria-label="申请类型"
                @change="onApprovalTypeFilterChange"
              >
                <el-option label="全部类型" value="" />
                <el-option
                  v-for="item in approvalTypes"
                  :key="item.key"
                  :label="item.label"
                  :value="item.key"
                />
              </el-select>
            </label>
            <label>
              <span>审批状态</span>
              <el-select v-model="approvalStatusFilter" placeholder="全部状态" clearable class="ulc-filter-select" aria-label="审批状态">
                <el-option label="全部状态" value="" />
                <el-option label="初审中" value="初审中" />
                <el-option label="复审中" value="复审中" />
              </el-select>
            </label>
          </div>
          <div class="md-hospital-filter-actions ulc-filter-actions">
            <button type="button" class="md-btn" @click="resetApprovalFilter">
              <RefreshCw :size="16" />
              <span>重置</span>
            </button>
            <button type="submit" class="md-btn md-btn-primary">
              <Search :size="16" />
              <span>查询</span>
            </button>
          </div>
        </form>

        <!-- 工具栏按钮：与统一列表其他页面保持一致 -->
        <div class="approval-action-row md-table-toolbar">
          <div class="md-table-toolbar-main">
            <template v-if="activeScope === 'todo'">
              <button type="button" class="md-btn md-btn-primary" @click="openCreateModal">
                <Plus :size="16" />
                新增
              </button>
              <details class="import-menu" ref="importMenuRef" @toggle="handleImportMenuToggle">
                <summary class="md-btn md-btn-primary" tabindex="0">
                  <FileUp :size="16" />
                  <span>导入</span>
                  <ChevronDown :size="14" />
                </summary>
                <div>
                  <button class="btn-text" type="button" @click="downloadTemplate">
                    <FileDown :size="15" />
                    模板导出
                  </button>
                  <button class="btn-text" type="button" @click="importInput?.click()">
                    <FileUp :size="15" />
                    按模板导入
                  </button>
                </div>
              </details>
              <input ref="importInput" type="file" accept=".xlsx" hidden @change="handleImportFile" />
            </template>
            <button type="button" class="md-btn md-btn-primary" @click="exportRows">
              <Download :size="16" />
              <span>导出</span>
            </button>
            <div class="ulc-col-settings">
              <button type="button" class="md-btn md-btn-primary" @click="approvalCols.settingsOpen.value = !approvalCols.settingsOpen.value">
                <Columns3 :size="16" />
                <span>列设置</span>
                <ChevronDown :size="14" :class="{ rotated: approvalCols.settingsOpen.value }" />
              </button>
              <div v-if="approvalCols.settingsOpen.value" class="ulc-col-settings-pop">
                <header>
                  <strong>列展示设置</strong>
                  <button type="button" class="ulc-link" @click="approvalCols.resetColumns()">恢复默认</button>
                </header>
                <div class="ulc-col-settings-list">
                  <label v-for="col in approvalCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                    <input
                      type="checkbox"
                      :checked="col.visible"
                      :disabled="col.visible && approvalCols.visibleColumnCount.value <= 1"
                      @change="approvalCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                    />
                    <span>{{ col.label }}</span>
                    <small>{{ col.width }}px</small>
                  </label>
                </div>
                <footer>
                  <button type="button" class="ulc-action" @click="approvalCols.settingsOpen.value = false">确定</button>
                </footer>
              </div>
            </div>
          </div>
        </div>

        <div ref="approvalTableShellRef" class="approval-wide-table-shell" @scroll="handleApprovalScroll">
          <table class="approval-wide-table" :style="{ minWidth: approvalTableMinWidth + 'px' }">
            <thead>
              <tr>
                <th class="approval-col-check">
                  <input type="checkbox" :checked="allVisibleSelected" @change="toggleAllRows" />
                </th>
                <th class="approval-col-no">审批单号</th>
                <th class="approval-col-product">商品名称 / 规格</th>
                <th v-if="isColVisible('type')" :style="{ width: approvalColWidth('type') + 'px' }">
                  <span class="ulc-th-text">申请类型</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('type', $event)" />
                </th>
                <th v-if="isColVisible('applicant')" :style="{ width: approvalColWidth('applicant') + 'px' }">
                  <span class="ulc-th-text">申请人</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('applicant', $event)" />
                </th>
                <th v-if="isColVisible('time')" :style="{ width: approvalColWidth('time') + 'px' }">
                  <span class="ulc-th-text">提交时间</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('time', $event)" />
                </th>
                <th v-if="isColVisible('productCode')" :style="{ width: approvalColWidth('productCode') + 'px' }">
                  <span class="ulc-th-text">商品编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('productCode', $event)" />
                </th>
                <th v-if="isColVisible('specModel')" :style="{ width: approvalColWidth('specModel') + 'px' }">
                  <span class="ulc-th-text">规格型号</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('specModel', $event)" />
                </th>
                <th v-if="isColVisible('supplier')" :style="{ width: approvalColWidth('supplier') + 'px' }">
                  <span class="ulc-th-text">供应商</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('supplier', $event)" />
                </th>
                <th v-if="isColVisible('manufacturer')" :style="{ width: approvalColWidth('manufacturer') + 'px' }">
                  <span class="ulc-th-text">厂家</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('manufacturer', $event)" />
                </th>
                <th v-if="isColVisible('registrationNo')" :style="{ width: approvalColWidth('registrationNo') + 'px' }">
                  <span class="ulc-th-text">注册证号</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('registrationNo', $event)" />
                </th>
                <th v-if="isColVisible('contractCode')" :style="{ width: approvalColWidth('contractCode') + 'px' }">
                  <span class="ulc-th-text">合同编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('contractCode', $event)" />
                </th>
                <th v-if="isColVisible('firstCategory')" :style="{ width: approvalColWidth('firstCategory') + 'px' }">
                  <span class="ulc-th-text">一级分类</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('firstCategory', $event)" />
                </th>
                <th v-if="isColVisible('secondCategory')" :style="{ width: approvalColWidth('secondCategory') + 'px' }">
                  <span class="ulc-th-text">二级分类</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('secondCategory', $event)" />
                </th>
                <th v-if="isColVisible('thirdCategory')" :style="{ width: approvalColWidth('thirdCategory') + 'px' }">
                  <span class="ulc-th-text">三级分类</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('thirdCategory', $event)" />
                </th>
                <th v-if="isColVisible('volumeBased')" :style="{ width: approvalColWidth('volumeBased') + 'px' }">
                  <span class="ulc-th-text">是否带量</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('volumeBased', $event)" />
                </th>
                <th v-if="isColVisible('centralized')" :style="{ width: approvalColWidth('centralized') + 'px' }">
                  <span class="ulc-th-text">是否集采</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('centralized', $event)" />
                </th>
                <th v-if="isColVisible('domestic')" :style="{ width: approvalColWidth('domestic') + 'px' }">
                  <span class="ulc-th-text">是否国产</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('domestic', $event)" />
                </th>
                <th v-if="isColVisible('chargeable')" :style="{ width: approvalColWidth('chargeable') + 'px' }">
                  <span class="ulc-th-text">是否收费</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('chargeable', $event)" />
                </th>
                <th v-if="isColVisible('keyMonitored')" :style="{ width: approvalColWidth('keyMonitored') + 'px' }">
                  <span class="ulc-th-text">重点监控</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('keyMonitored', $event)" />
                </th>
                <th v-if="isColVisible('purchasePrice')" :style="{ width: approvalColWidth('purchasePrice') + 'px' }">
                  <span class="ulc-th-text">采购价</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('purchasePrice', $event)" />
                </th>
                <th v-if="isColVisible('udiCode')" :style="{ width: approvalColWidth('udiCode') + 'px' }">
                  <span class="ulc-th-text">UDI 编码</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('udiCode', $event)" />
                </th>
                <th v-if="isColVisible('risk')" :style="{ width: approvalColWidth('risk') + 'px' }">
                  <span class="ulc-th-text">风险标签</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('risk', $event)" />
                </th>
                <th v-if="isColVisible('wait')" :style="{ width: approvalColWidth('wait') + 'px' }">
                  <span class="ulc-th-text">节点 / 等待</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('wait', $event)" />
                </th>
                <th v-if="isColVisible('changeSummary')" :style="{ width: approvalColWidth('changeSummary') + 'px' }">
                  <span class="ulc-th-text">变更记录</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startApprovalColDrag('changeSummary', $event)" />
                </th>
                <th class="approval-sticky-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td :colspan="approvalTableSpan"><StatusMessage message="正在加载待审批任务..." tone="info" /></td>
              </tr>
              <tr v-else-if="error">
                <td :colspan="approvalTableSpan"><StatusMessage :message="error" tone="error" /></td>
              </tr>
              <tr v-for="row in filteredRows" v-else :key="row.no">
                <td class="approval-col-check">
                  <input v-model="selectedNos" type="checkbox" :value="row.no" />
                </td>
                <td class="approval-col-no">
                  <div class="approval-no">
                    <AlertTriangle v-if="row.warning" :size="15" />
                    <span>{{ row.no }}</span>
                  </div>
                </td>
                <td class="approval-col-product approval-product-cell">
                  <strong :title="row.product">{{ row.product }}</strong>
                  <small :title="row.specModel || row.supplier">{{ row.specModel || '-' }} · {{ row.supplier }}</small>
                </td>
                <td v-if="isColVisible('type')"><span class="ulc-chip" :class="approvalTypeChipClass(row.type)">{{ row.type }}</span></td>
                <td v-if="isColVisible('applicant')"><span class="ulc-strong">{{ approvalWideField(row, 'applicant') }}</span></td>
                <td v-if="isColVisible('time')" class="approval-time"><span class="ulc-time">{{ approvalTimeText(row) }}</span></td>
                <td v-if="isColVisible('productCode')" class="approval-mono"><span class="ulc-mono">{{ approvalWideField(row, 'productCode') }}</span></td>
                <td v-if="isColVisible('specModel')"><span class="ulc-strong">{{ approvalWideField(row, 'specModel') }}</span></td>
                <td v-if="isColVisible('supplier')"><span class="ulc-strong">{{ approvalWideField(row, 'supplier') }}</span></td>
                <td v-if="isColVisible('manufacturer')"><span class="ulc-strong">{{ approvalWideField(row, 'manufacturer') }}</span></td>
                <td v-if="isColVisible('registrationNo')" class="approval-mono"><span class="ulc-mono">{{ approvalWideField(row, 'registrationNo') }}</span></td>
                <td v-if="isColVisible('contractCode')" class="approval-mono"><span class="ulc-mono">{{ approvalWideField(row, 'contractCode') }}</span></td>
                <td v-if="isColVisible('firstCategory')"><span class="ulc-strong">{{ approvalWideField(row, 'firstCategory') }}</span></td>
                <td v-if="isColVisible('secondCategory')"><span class="ulc-strong">{{ approvalWideField(row, 'secondCategory') }}</span></td>
                <td v-if="isColVisible('thirdCategory')"><span class="ulc-strong">{{ approvalWideField(row, 'thirdCategory') }}</span></td>
                <td v-if="isColVisible('volumeBased')"><span class="ulc-tag">{{ approvalWideField(row, 'volumeBased') }}</span></td>
                <td v-if="isColVisible('centralized')"><span class="ulc-tag">{{ approvalWideField(row, 'centralized') }}</span></td>
                <td v-if="isColVisible('domestic')"><span class="ulc-tag">{{ approvalWideField(row, 'domestic') }}</span></td>
                <td v-if="isColVisible('chargeable')"><span class="ulc-tag">{{ approvalWideField(row, 'chargeable') }}</span></td>
                <td v-if="isColVisible('keyMonitored')"><span class="ulc-tag">{{ approvalWideField(row, 'keyMonitored') }}</span></td>
                <td v-if="isColVisible('purchasePrice')" class="approval-money"><span class="ulc-strong">{{ approvalWideField(row, 'purchasePrice') }}</span></td>
                <td v-if="isColVisible('udiCode')" class="approval-mono"><span class="ulc-mono">{{ approvalWideField(row, 'udiCode') }}</span></td>
                <td v-if="isColVisible('risk')">
                  <span
                    v-for="label in approvalRiskLabels(row)"
                    :key="label"
                    class="ulc-chip"
                    :class="approvalRiskChipClass(label)"
                  >
                    {{ label }}
                  </span>
                </td>
                <td v-if="isColVisible('wait')">
                  <span class="ulc-chip" :class="approvalStatusChipClass(row.statusTone)">
                    <Clock3 v-if="row.statusTone === 'orange'" :size="12" />
                    <Send v-else-if="row.statusTone === 'blue'" :size="12" />
                    <XCircle v-else :size="12" />
                    {{ approvalWideField(row, 'wait') }}
                  </span>
                </td>
                <td v-if="isColVisible('changeSummary')" class="approval-change-summary"><span class="ulc-strong">{{ row.changeSummary || '-' }}</span></td>
                <td class="approval-sticky-action">
                  <div class="ulc-actions">
                    <button class="ulc-action" type="button" @click="qualificationApplicationNo = row.no; qualificationModalOpen = true">资质阅览</button>
                    <RouterLink
                      v-if="activeScope !== 'todo' && !isReturnedMineDirectory"
                      class="ulc-action"
                      :to="rowDetailRoute(row)"
                    >
                      查看
                    </RouterLink>
                    <template v-else>
                      <RouterLink
                        class="ulc-action"
                        :to="rowDetailRoute(row)"
                      >
                        查看
                      </RouterLink>
                      <RouterLink
                        class="ulc-action"
                        :to="rowEditRoute(row)"
                      >
                        {{ rowPrimaryActionLabel() }}
                      </RouterLink>
                    </template>
                  </div>
                </td>
              </tr>
              <tr v-if="!loading && !error && rows.length === 0">
                <td :colspan="approvalTableSpan">
                  <div class="approval-empty-state">
                    <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                    </svg>
                    <EmptyState message="当前分类暂无待审批任务" />
                    <button v-if="activeScope === 'todo'" class="btn btn-primary" type="button" @click="openCreateModal">
                      <Plus :size="17" /> 去新增
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          ref="approvalScrollTrackRef"
          class="approval-wide-scroll-rail"
          :class="{ dragging: approvalScrollDragging }"
          role="scrollbar"
          aria-orientation="horizontal"
          :aria-valuenow="Math.round(approvalScrollState.scrollLeft)"
          :aria-valuemax="Math.max(0, approvalScrollState.scrollWidth - approvalScrollState.clientWidth)"
          tabindex="0"
          @pointerdown="jumpApprovalTableScroll"
        >
          <span
            class="approval-wide-scroll-thumb"
            @pointerdown.stop="startApprovalScrollDrag"
            @pointermove="moveApprovalScrollDrag"
            @pointerup="endApprovalScrollDrag"
            @pointercancel="endApprovalScrollDrag"
          ></span>
        </div>

        <div v-if="selectedNos.length" class="approval-batch-bar">
          <span>已选择 {{ selectedNos.length }} 条</span>
          <button type="button" class="ulc-action" :disabled="batchApproveLoading" @click="openBatchApprove('approve')">
            <CheckCircle2 :size="14" /> 批量通过
          </button>
          <button type="button" class="ulc-action" :disabled="batchApproveLoading" @click="openBatchApprove('return')">
            <XCircle :size="14" /> 批量退回
          </button>
          <button type="button" class="ulc-action is-danger" :disabled="batchApproveLoading" @click="openBatchApprove('reject')">
            <XCircle :size="14" /> 批量驳回
          </button>
          <button type="button" class="ulc-action is-neutral" style="margin-left:auto" :disabled="batchApproveLoading" @click="selectedNos = []">
            清除选择
          </button>
        </div>

        <MdPagination
          :page="currentPage"
          :total-pages="Math.max(1, Math.ceil(totalItems / pageSize))"
          :total="totalItems"
          :size="pageSize"
          :loading="loading"
          @update:page="changePage"
          @update:size="changePageSize"
        />
      </main>
    </div>

    <el-dialog v-model="qualificationModalOpen" :title="`资质证照附件 · ${qualificationApplicationNo}`" width="min(980px, 94vw)" append-to-body destroy-on-close>
      <PendingProductQualificationAttachments v-if="qualificationModalOpen" :application-no="qualificationApplicationNo" />
    </el-dialog>

    <Teleport to="body">
    <div v-if="showBatchApproveModal" class="attachment-preview-mask" @click.self="!batchApproveLoading && (showBatchApproveModal = false)">
      <section class="supplier-dialog product-dialog large" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>批量审批</p>
            <h3>{{ { approve: '批量通过', return: '批量退回修改', reject: '批量驳回' }[batchApproveAction] }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" :disabled="batchApproveLoading" @click="showBatchApproveModal = false">
            <X :size="18" />
          </button>
        </header>
        <form @submit.prevent="confirmBatchApprove">
          <div class="approval-create-section">
            <p style="margin:0 0 12px;color:#475569;">已选择 <strong>{{ selectedNos.length }}</strong> 条审批单</p>
            <label>
              <span>审批意见</span>
              <textarea
                v-model="batchApproveOpinion"
                :disabled="batchApproveLoading"
                :placeholder="batchApproveAction === 'return' ? '请填写退回修改原因（必填）' : batchApproveAction === 'reject' ? '请填写驳回理由（必填）' : '填写审批意见（选填）'"
                rows="4"
                :required="batchApproveAction !== 'approve'"
              />
            </label>
          </div>
          <div class="dialog-actions">
            <button class="btn" type="button" :disabled="batchApproveLoading" @click="showBatchApproveModal = false">取消</button>
            <button class="btn btn-primary" type="submit" :disabled="batchApproveLoading">
              <Send :size="18" /> {{ batchApproveLoading ? '提交中...' : '确认' }}
            </button>
          </div>
        </form>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showCreateModal" class="attachment-preview-mask" @click.self="showCreateModal = false">
      <section class="supplier-dialog product-dialog approval-create-dialog xl" role="dialog" aria-modal="true" aria-labelledby="approval-create-title">
        <header class="approval-create-head">
          <div>
            <p>待审批目录</p>
            <h3 id="approval-create-title">新增待审批目录</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="showCreateModal = false">
            <X :size="18" />
          </button>
        </header>
        <form class="approval-create-form" @submit.prevent="handleCreateSubmit">
          <div class="approval-create-scroll">
            <section class="approval-create-section">
            <h4>申请信息</h4>
            <div class="supplier-form-grid compact">
              <label>
                <span>申请类型</span>
                <select v-model="createForm.applicationType" @change="handleApplicationTypeChange">
                  <option>新品准入</option>
                  <option>信息变更</option>
                  <option>资质更新</option>
                  <option>价格调整</option>
                  <option>停用申请</option>
                </select>
              </label>
              <label class="wide"><span>申请原因</span><textarea v-model="createForm.changeReason" /></label>
            </div>
            <div v-if="!isNewApplication" class="approval-source-picker">
              <label class="wide">
                <span>来源医院目录商品</span>
                <div class="approval-source-search">
                  <input v-model="sourceKeyword" placeholder="输入商品编码、名称或规格" @keyup.enter.prevent="searchSourceProducts" />
                  <button class="btn" type="button" :disabled="sourceLoading" @click="searchSourceProducts">
                    <Search :size="15" /> {{ sourceLoading ? '搜索中' : '搜索' }}
                  </button>
                </div>
              </label>
              <div v-if="sourceRows.length" class="approval-source-results">
                <button v-for="item in sourceRows" :key="item.productCode" type="button"
                  :disabled="createForm.applicationType === '停用申请' && item.status !== 1" @click="selectSourceProduct(item)">
                  <strong>{{ item.productCode }} · {{ item.productName }}</strong>
                  <span>{{ item.specModel }} · {{ item.manufacturerName || '未维护厂家' }} · {{ item.status === 1 ? '启用' : '停用' }}</span>
                </button>
              </div>
              <p v-if="sourceSelected" class="approval-source-selected">已载入服务器目录快照：{{ createForm.productCode }} · {{ createForm.productName }}</p>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>商品基础信息</h4>
            <div class="supplier-form-grid compact">
              <label><span>商品编码</span><input v-model="createForm.productCode" :readonly="!isNewApplication" placeholder="选填，留空取招采子编码" /></label>
              <label><span>商品名称 <i class="req-mark">*</i></span><input v-model="createForm.productName" :readonly="!canEditInformation" required /></label>
              <label><span>规格型号 <i class="req-mark">*</i></span><input v-model="createForm.specModel" :readonly="!canEditInformation" required /></label>
              <label><span>品牌</span><input v-model="createForm.brand" :readonly="!canEditInformation" /></label>
              <label>
                <span>生产厂家</span>
                <select v-model="createForm.manufacturerName" :disabled="!canEditInformation" @change="handleManufacturerChange">
                  <option value="">请选择生产厂家</option>
                  <option
                    v-for="item in withRetainedOption(manufacturerOptions, createForm.manufacturerName)"
                    :key="item.code + item.name"
                    :value="item.name"
                    :disabled="item.status !== 1"
                  >
                    {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
                  </option>
                </select>
              </label>
              <label>
                <span>供应商</span>
                <select v-model="createForm.supplierName" :disabled="!canEditInformation" @change="handleSupplierChange">
                  <option value="">请选择供应商</option>
                  <option
                    v-for="item in withRetainedOption(supplierOptions, createForm.supplierName)"
                    :key="item.code + item.name"
                    :value="item.name"
                    :disabled="item.status !== 1"
                  >
                    {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
                  </option>
                </select>
              </label>
              <label><span>单位 <i class="req-mark">*</i></span><input v-model="createForm.unit" :readonly="!canEditInformation" required /></label>
              <label><span>储存条件</span><input v-model="createForm.storageCondition" :readonly="!canEditInformation" /></label>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>价格采购与分类</h4>
            <div class="supplier-form-grid compact">
              <label><span>采购价</span><input v-model.number="createForm.purchasePrice" :readonly="!canEditPrice" type="number" step="0.0001" min="0" /></label>
              <label><span>零售价</span><input v-model.number="createForm.retailPrice" type="number" step="0.0001" min="0" /></label>
              <label><span>最小采购量</span><input v-integer-quantity v-model.number="createForm.minPurchaseQty" type="number" step="1" min="0" /></label>
              <label><span>采购单位</span><input v-model="createForm.purchaseUnit" /></label>
              <label><span>中包装数量</span><input v-integer-quantity v-model.number="createForm.conversionRate" type="number" step="1" min="0" /></label>
              <label><span>采购包装数量</span><input v-integer-quantity v-model.number="createForm.purchasePackageQty" type="number" step="1" min="0" /></label>
              <label><span>合同编码</span><input v-model="createForm.contractCode" /></label>
              <label><span>一级分类</span><input v-model="createForm.firstCategory" /></label>
              <label><span>二级分类</span><input v-model="createForm.secondCategory" /></label>
              <label><span>三级分类</span><input v-model="createForm.thirdCategory" /></label>
              <label><span>招采子编码</span><input v-model="createForm.tenderSubCode" /></label>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>资质与仓储</h4>
            <div class="supplier-form-grid compact">
              <label><span>UDI编码</span><input v-model="createForm.udiCode" /></label>
              <label><span>注册证号</span><input v-model="createForm.registrationNo" :readonly="!canEditQualification" /></label>
              <label><span>注册证有效期</span><input v-model="createForm.registrationExpireDate" :readonly="!canEditQualification" type="date" /></label>
              <label><span>生产许可证号</span><input v-model="createForm.productionLicenseNo" :readonly="!canEditQualification" /></label>
              <label><span>经营许可证号</span><input v-model="createForm.businessLicenseNo" :readonly="!canEditQualification" /></label>
              <label>
                <span>资质附件</span>
                <div class="approval-attach-field">
                  <input
                    ref="attachmentInput"
                    class="approval-attach-native"
                    :disabled="!canEditQualification"
                    type="file"
                    multiple
                    accept="image/*,.pdf"
                    @change="handleCreateAttachments"
                  />
                  <button
                    type="button"
                    class="btn btn-primary approval-attach-btn"
                    :disabled="!canEditQualification"
                    @click="triggerAttachmentPicker"
                  >
                    <Upload :size="15" /> 选择文件
                  </button>
                  <span class="approval-attach-hint">支持图片与 PDF（可多选）</span>
                </div>
              </label>
            </div>
            <div class="approval-attachment-summary">
              <strong>附件数量：{{ createAttachments.length }}</strong>
              <span>根据上传图片或 PDF 自动统计，无需人工填写。</span>
              <div v-if="createAttachments.length" class="approval-upload-list">
                <button
                  v-for="(file, index) in createAttachments"
                  :key="`${file.name}-${index}`"
                  type="button"
                  @click="removeCreateAttachment(index)"
                >
                  {{ file.name }} ×
                </button>
              </div>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>业务属性</h4>
            <div class="dialog-toggle-row">
              <label><input v-model="createForm.volumeBased" :disabled="!canEditInformation" type="checkbox" /> 是否带量</label>
              <label><input v-model="createForm.centralizedProcurement" :disabled="!canEditInformation" type="checkbox" /> 是否集采</label>
              <label><input v-model="createForm.domestic" :disabled="!canEditInformation" type="checkbox" /> 是否国产</label>
              <label><input v-model="createForm.chargeable" type="checkbox" /> 是否收费</label>
              <label><input v-model="createForm.highValue" type="checkbox" /> 高值耗材</label>
              <label><input v-model="createForm.coldChain" type="checkbox" /> 冷链</label>
              <label><input v-model="createForm.quotaManaged" type="checkbox" /> 定数管理</label>
              <label><input v-model="createForm.keyMonitored" type="checkbox" /> 重点监控</label>
            </div>
          </section>
          </div>

          <div class="dialog-actions approval-create-actions">
            <button class="btn" type="button" @click="showCreateModal = false">取消</button>
            <button class="btn btn-primary" type="submit">
              <Send :size="16" />
              提交审批
            </button>
          </div>
        </form>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="duplicateAlertVisible" class="attachment-preview-mask" @click.self="duplicateAlertVisible = false">
      <section class="duplicate-alert-dialog" role="alertdialog" aria-modal="true" aria-labelledby="duplicate-alert-title">
        <header>
          <div>
            <p>重复校验</p>
            <h3 id="duplicate-alert-title">该商品目录已存在！</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="duplicateAlertVisible = false">
            <X :size="18" />
          </button>
        </header>
        <div class="duplicate-alert-body">
          <AlertTriangle :size="40" aria-hidden="true" />
          <p>商品名称、规格型号、生产厂家、供应商、注册证号完全一致的目录已存在，请核对后修改或直接使用现有目录。</p>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-primary" type="button" @click="duplicateAlertVisible = false">知道了</button>
        </div>
      </section>
    </div>
    </Teleport>
  </section>
</template>
