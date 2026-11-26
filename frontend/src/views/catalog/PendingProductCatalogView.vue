<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, CheckCircle2, ChevronDown, ClipboardCheck, Clock3, Columns3, Download, FileDown, FileUp, Plus, Search, Send, Upload, UserRound, X, XCircle } from '@lucide/vue'
import PendingProductQualificationAttachments from '../../components/catalog/PendingProductQualificationAttachments.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import SpdStatusChip, { type SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import { usePendingProductBatchApproval } from '../../composables/usePendingProductBatchApproval'
import { usePendingProductCreateForm } from '../../composables/usePendingProductCreateForm'
import { usePendingProductImportExport } from '../../composables/usePendingProductImportExport'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
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

/** 是否正在由代码回灌勾选状态（避免与 `@selection-change` 形成回环） */
let syncingSelection = false

/**
 * el-table 勾选列与 `selectedNos` 的双向同步。
 * ---------------------------------------------------------------------
 * 统一列表改造后表格由 Element Plus 渲染，行勾选走 `@selection-change`；
 * 这里把选中行的审批单号回写到 `selectedNos`，批量通过 / 退回 / 驳回等
 * 业务动作仍然读同一个数组，行为不变。
 *
 * 反向同步：`selectedNos` 也可能被业务代码直接改写（例如「清除选择」），
 * 因此监听它并把状态回灌给表格，避免界面勾选与数据不一致。
 */
function onApprovalSelectionChange(selection: Array<Record<string, unknown>>) {
  if (syncingSelection) return
  selectedNos.value = selection.map((row) => String((row as Record<string, unknown>).no))
}

/** 把 `selectedNos` 的状态回灌到表格勾选列 */
function syncTableSelection() {
  const table = approvalTableRef.value
  if (!table) return
  syncingSelection = true
  table.clearSelection()
  if (selectedNos.value.length) {
    const visible = filteredRows.value
    visible.forEach((row) => {
      if (selectedNos.value.includes(row.no)) table.toggleRowSelection(row, true)
    })
  }
  nextTick(() => { syncingSelection = false })
}

watch(selectedNos, () => {
  if (syncingSelection) return
  nextTick(syncTableSelection)
}, { flush: 'post' })

/** 表头全选：勾上时选中当前页全部可见行，取消时清空 */
function toggleAllRows() {
  selectedNos.value = allVisibleSelected.value ? [] : rows.value.map((row) => row.no)
}

const { currentPage, pageSize, changePage, changePageSize, resetPage } =
  usePendingProductCatalogPagination({
    totalItems,
    reload: loadApplications
  })

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
  { key: 'no', label: '审批单号', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 220 },
  { key: 'product', label: '商品名称 / 规格', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'type', label: '申请类型', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'applicant', label: '申请人', width: 100, visible: true, weight: 3, minWidth: 85, maxWidth: 160 },
  { key: 'time', label: '提交时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'productCode', label: '商品编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'specModel', label: '规格型号', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'supplier', label: '供应商', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'manufacturer', label: '厂家', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'registrationNo', label: '注册证号', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'contractCode', label: '合同编码', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'firstCategory', label: '一级分类', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'secondCategory', label: '二级分类', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'thirdCategory', label: '三级分类', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'volumeBased', label: '是否带量', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'centralized', label: '是否集采', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'domestic', label: '是否国产', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'chargeable', label: '是否收费', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'keyMonitored', label: '重点监控', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'purchasePrice', label: '采购价', width: 100, visible: true, weight: 2, minWidth: 85, maxWidth: 140 },
  { key: 'udiCode', label: 'UDI 编码', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'risk', label: '风险标签', width: 160, visible: true, weight: 4, minWidth: 130, maxWidth: 240 },
  { key: 'wait', label: '节点 / 等待', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 190 },
  { key: 'changeSummary', label: '变更记录', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const approvalCols = useListColumns({
  storageKey: 'spd-pending-product-approval-cols-v1',
  defaults: APPROVAL_COL_DEFAULTS,
  fitMode: 'hybrid',
  // 预留勾选列宽度（<el-table-column type="selection" width="48" />）：
  // 该列不在列配置里，但会占用表格宽度，必须先扣掉，
  // 否则数据列按完整容器宽度分配，把表格顶出横向滚动条。
  reservedWidth: 48
})

const isColVisible = (key: string) => approvalCols.isColumnVisible(key)
const approvalColBinding = (key: string) => approvalCols.columnBinding(key)

/** 操作列固定：出现横向滚动条时才固定右侧 */
const approvalActionFix = useActionColumnFix()
const approvalActionFixed = computed(() => approvalActionFix.actionFixedBinding.value)
const approvalTableRef = approvalCols.tableRef
watch(approvalTableRef, (el) => {
  if (el) approvalActionFix.observe(el)
}, { immediate: true })

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const approvalColSettingsAnchorRef = ref<HTMLElement | null>(null)
const approvalColSettingsPopRef = ref<HTMLElement | null>(null)
const approvalColSettingsPop = useColSettingsPopover({
  open: approvalCols.settingsOpen,
  anchorRef: approvalColSettingsAnchorRef,
  popRef: approvalColSettingsPopRef
})

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
function approvalTypeChipTone(type: string): SpdStatusChipTone {
  const map: Record<string, SpdStatusChipTone> = {
    新品准入: 'primary',
    信息变更: 'info',
    资质更新: 'warning',
    价格调整: 'success',
    停用申请: 'danger'
  }
  return map[type] ?? 'neutral'
}

/** 风险标签状态条色调 */
function approvalRiskChipTone(label: string): SpdStatusChipTone {
  if (label.includes('临期') || label.includes('价格') || label.includes('库房') || label.includes('重点')) return 'warning'
  if (label.includes('冷链') || label.includes('高值')) return 'danger'
  if (label.includes('变更')) return 'info'
  return 'success'
}

/** 审批节点状态条色调 */
function approvalStatusChipTone(tone?: string): SpdStatusChipTone {
  const map: Record<string, SpdStatusChipTone> = {
    orange: 'warning',
    blue: 'info',
    red: 'danger',
    green: 'success'
  }
  return map[tone ?? ''] ?? 'neutral'
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
</script>

<template>
  <section class="approval-page spd-unified-list">
    <MdPageHeader
      title="待审批目录"
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

        <!-- 筛选区：统一使用 SpdFilterBar（与全站筛选标准规则一致） -->
        <SpdFilterBar @submit="currentPage = 1; loadApplications()" @reset="resetApprovalFilter">
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
            <el-select v-model="approvalStatusFilter" placeholder="全部状态" clearable aria-label="审批状态">
              <el-option label="全部状态" value="" />
              <el-option label="初审中" value="初审中" />
              <el-option label="复审中" value="复审中" />
            </el-select>
          </label>
        </SpdFilterBar>

        <MdDataTableCard>
          <template #toolbar>
            <div class="md-table-toolbar-main">
              <template v-if="activeScope === 'todo'">
                <SpdButton label="新增" tone="primary" @click="openCreateModal">
          <template #icon><Plus :size="16" /></template>
        </SpdButton>
                <details class="import-menu" ref="importMenuRef" @toggle="handleImportMenuToggle">
                  <summary class="md-btn md-btn-primary" tabindex="0">
                    <FileUp :size="16" />
                    <span>导入</span>
                    <ChevronDown :size="14" />
                  </summary>
                  <div>
                    <SpdButton label="模板导出" variant="text" tone="neutral" @click="downloadTemplate">
                      <template #icon><FileDown :size="15" /></template>
                    </SpdButton>
                    <SpdButton label="按模板导入" variant="text" tone="neutral" @click="importInput?.click()">
                      <template #icon><FileUp :size="15" /></template>
                    </SpdButton>
                  </div>
                </details>
                <input ref="importInput" type="file" accept=".xlsx" hidden @change="handleImportFile" />
              </template>
              <SpdButton label="导出" tone="neutral" @click="exportRows">
          <template #icon><Download :size="16" /></template>
        </SpdButton>
              <div class="ulc-col-settings">
                <SpdButton label="列设置" tone="neutral" ref="approvalColSettingsAnchorRef"
                 
                 
                  @click="approvalCols.settingsOpen.value = !approvalCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
                <div
                  v-if="approvalCols.settingsOpen.value"
                  ref="approvalColSettingsPopRef"
                  class="ulc-col-settings-pop"
                  :class="{ 'is-align-right': approvalColSettingsPop.align.value === 'right', 'is-align-up': approvalColSettingsPop.vertical.value === 'up' }"
                  :style="approvalColSettingsPop.popStyle.value"
                >
                  <header>
                    <strong>列展示设置</strong>
                    <SpdButton label="恢复默认" variant="text" tone="neutral" @click="approvalCols.resetColumns()"></SpdButton>
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
                    <SpdButton label="确定" tone="primary" @click="approvalCols.settingsOpen.value = false"></SpdButton>
                  </footer>
                </div>
              </div>
            </div>
          </template>
          <template #meta-extra>
            <span class="md-chip primary">共 {{ totalItems.toLocaleString('zh-CN') }} 条</span>
          </template>
          <div class="approval-wide-table-shell">
            <div class="ulc-table-wrap">
              <el-table
                ref="approvalTableRef"
                v-loading="loading"
                class="ulc-table is-sticky"
                :data="filteredRows"
                row-key="no"
                border
                stripe
                max-height="560"
                style="width: 100%"
                empty-text="当前分类暂无待审批任务"
                @selection-change="onApprovalSelectionChange"
                @header-dragend="(width: number, oldWidth: number, column: any) => approvalCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
            <el-table-column
              column-key="selection"
              type="selection"
              width="48"
              align="center"
              :reserve-selection="false"
            >
              <template #header>
                <input type="checkbox" :checked="allVisibleSelected" @change="toggleAllRows" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="no"
              v-if="isColVisible('no')"
              label="审批单号"
              v-bind="approvalColBinding('no')"
            >
              <template #header>
                <span class="ulc-th-text">审批单号</span>
              </template>
              <template #default="{ row }">
                <div class="approval-no">
                  <AlertTriangle v-if="row.warning" :size="15" />
                  <span class="ulc-wrap-text">{{ row.no }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isColVisible('product')"
              label="商品名称 / 规格"
              v-bind="approvalColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品名称 / 规格</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text" :title="row.product">{{ row.product }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub" :title="row.specModel || row.supplier">{{ row.specModel || '-' }} · {{ row.supplier }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="type"
              v-if="isColVisible('type')"
              label="申请类型"
              v-bind="approvalColBinding('type')"
            >
              <template #header>
                <span class="ulc-th-text">申请类型</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="approvalTypeChipTone(row.type)" :label="row.type" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="applicant"
              v-if="isColVisible('applicant')"
              label="申请人"
              v-bind="approvalColBinding('applicant')"
            >
              <template #header>
                <span class="ulc-th-text">申请人</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'applicant') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="time"
              v-if="isColVisible('time')"
              label="提交时间"
              v-bind="approvalColBinding('time')"
            >
              <template #header>
                <span class="ulc-th-text">提交时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ approvalTimeText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isColVisible('createTime')"
              label="创建时间"
              v-bind="approvalColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isColVisible('updateTime')"
              label="修改时间"
              v-bind="approvalColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ formatDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="productCode"
              v-if="isColVisible('productCode')"
              label="商品编码"
              v-bind="approvalColBinding('productCode')"
            >
              <template #header>
                <span class="ulc-th-text">商品编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ approvalWideField(row, 'productCode') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="specModel"
              v-if="isColVisible('specModel')"
              label="规格型号"
              v-bind="approvalColBinding('specModel')"
            >
              <template #header>
                <span class="ulc-th-text">规格型号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'specModel') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="supplier"
              v-if="isColVisible('supplier')"
              label="供应商"
              v-bind="approvalColBinding('supplier')"
            >
              <template #header>
                <span class="ulc-th-text">供应商</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'supplier') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="manufacturer"
              v-if="isColVisible('manufacturer')"
              label="厂家"
              v-bind="approvalColBinding('manufacturer')"
            >
              <template #header>
                <span class="ulc-th-text">厂家</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'manufacturer') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="registrationNo"
              v-if="isColVisible('registrationNo')"
              label="注册证号"
              v-bind="approvalColBinding('registrationNo')"
            >
              <template #header>
                <span class="ulc-th-text">注册证号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ approvalWideField(row, 'registrationNo') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="contractCode"
              v-if="isColVisible('contractCode')"
              label="合同编码"
              v-bind="approvalColBinding('contractCode')"
            >
              <template #header>
                <span class="ulc-th-text">合同编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ approvalWideField(row, 'contractCode') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="firstCategory"
              v-if="isColVisible('firstCategory')"
              label="一级分类"
              v-bind="approvalColBinding('firstCategory')"
            >
              <template #header>
                <span class="ulc-th-text">一级分类</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'firstCategory') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="secondCategory"
              v-if="isColVisible('secondCategory')"
              label="二级分类"
              v-bind="approvalColBinding('secondCategory')"
            >
              <template #header>
                <span class="ulc-th-text">二级分类</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'secondCategory') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="thirdCategory"
              v-if="isColVisible('thirdCategory')"
              label="三级分类"
              v-bind="approvalColBinding('thirdCategory')"
            >
              <template #header>
                <span class="ulc-th-text">三级分类</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'thirdCategory') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="volumeBased"
              v-if="isColVisible('volumeBased')"
              label="是否带量"
              v-bind="approvalColBinding('volumeBased')"
            >
              <template #header>
                <span class="ulc-th-text">是否带量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ approvalWideField(row, 'volumeBased') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="centralized"
              v-if="isColVisible('centralized')"
              label="是否集采"
              v-bind="approvalColBinding('centralized')"
            >
              <template #header>
                <span class="ulc-th-text">是否集采</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ approvalWideField(row, 'centralized') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="domestic"
              v-if="isColVisible('domestic')"
              label="是否国产"
              v-bind="approvalColBinding('domestic')"
            >
              <template #header>
                <span class="ulc-th-text">是否国产</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ approvalWideField(row, 'domestic') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="chargeable"
              v-if="isColVisible('chargeable')"
              label="是否收费"
              v-bind="approvalColBinding('chargeable')"
            >
              <template #header>
                <span class="ulc-th-text">是否收费</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ approvalWideField(row, 'chargeable') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="keyMonitored"
              v-if="isColVisible('keyMonitored')"
              label="重点监控"
              v-bind="approvalColBinding('keyMonitored')"
            >
              <template #header>
                <span class="ulc-th-text">重点监控</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-tag">{{ approvalWideField(row, 'keyMonitored') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="purchasePrice"
              v-if="isColVisible('purchasePrice')"
              label="采购价"
              v-bind="approvalColBinding('purchasePrice')"
            >
              <template #header>
                <span class="ulc-th-text">采购价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ approvalWideField(row, 'purchasePrice') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="udiCode"
              v-if="isColVisible('udiCode')"
              label="UDI 编码"
              v-bind="approvalColBinding('udiCode')"
            >
              <template #header>
                <span class="ulc-th-text">UDI 编码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ approvalWideField(row, 'udiCode') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="risk"
              v-if="isColVisible('risk')"
              label="风险标签"
              v-bind="approvalColBinding('risk')"
            >
              <template #header>
                <span class="ulc-th-text">风险标签</span>
              </template>
              <template #default="{ row }">
                <!-- 多值标签：整体作为一个块，超宽时整体换行，不逐个标签单独折行 -->
                <span class="ulc-inline-tags">
                  <SpdStatusChip
                    v-for="label in approvalRiskLabels(row)"
                    :key="label"
                    :tone="approvalRiskChipTone(label)"
                    :label="label"
                  />
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="wait"
              v-if="isColVisible('wait')"
              label="节点 / 等待"
              v-bind="approvalColBinding('wait')"
            >
              <template #header>
                <span class="ulc-th-text">节点 / 等待</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="approvalStatusChipTone(row.statusTone)">
                  <Clock3 v-if="row.statusTone === 'orange'" :size="12" />
                  <Send v-else-if="row.statusTone === 'blue'" :size="12" />
                  <XCircle v-else :size="12" />
                  {{ approvalWideField(row, 'wait') }}
                </SpdStatusChip>
              </template>
            </el-table-column>
            <el-table-column
              column-key="changeSummary"
              v-if="isColVisible('changeSummary')"
              label="变更记录"
              v-bind="approvalColBinding('changeSummary')"
            >
              <template #header>
                <span class="ulc-th-text">变更记录</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.changeSummary || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isColVisible('actions')"
              label="操作"
              v-bind="approvalColBinding('actions')"
              :fixed="approvalActionFixed"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
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
              </template>
            </el-table-column>
                <template #empty>
                  <StatusMessage v-if="error" :message="error" tone="error" />
                  <MdEmptyState v-else text="当前分类暂无待审批任务" hint="调整左侧审批队列或筛选条件" />
                </template>
              </el-table>
            </div>
          </div>

        <div v-if="selectedNos.length" class="approval-batch-bar">
          <span>已选择 {{ selectedNos.length }} 条</span>
          <SpdButton label="批量通过" tone="success" :disabled="batchApproveLoading" @click="openBatchApprove('approve')">
            <template #icon><CheckCircle2 :size="14" /></template>
          </SpdButton>
          <SpdButton label="批量退回" tone="warning" :disabled="batchApproveLoading" @click="openBatchApprove('return')">
            <template #icon><XCircle :size="14" /></template>
          </SpdButton>
          <SpdButton label="批量驳回" tone="danger" :disabled="batchApproveLoading" @click="openBatchApprove('reject')">
            <template #icon><XCircle :size="14" /></template>
          </SpdButton>
          <SpdButton label="清除选择" tone="neutral" class="approval-batch-clear" :disabled="batchApproveLoading" @click="selectedNos = []"></SpdButton>
        </div>

          <template #pagination>
            <MdPagination
              :page="currentPage"
              :total-pages="Math.max(1, Math.ceil(totalItems / pageSize))"
              :total="totalItems"
              :size="pageSize"
              :loading="loading"
              @update:page="changePage"
              @update:size="changePageSize"
            />
          </template>
        </MdDataTableCard>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" :disabled="batchApproveLoading" @click="showBatchApproveModal = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <form @submit.prevent="confirmBatchApprove">
          <div class="approval-create-section">
            <p style="margin:0 0 12px;color:var(--spd-p-text-secondary);">已选择 <strong>{{ selectedNos.length }}</strong> 条审批单</p>
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
            <SpdButton label="取消" tone="neutral" :disabled="batchApproveLoading" @click="showBatchApproveModal = false"></SpdButton>
            <SpdButton tone="primary" type="submit" :disabled="batchApproveLoading">
          <template #icon><Send :size="18" /></template>
          {{ batchApproveLoading ? '提交中...' : '确认' }}
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="showCreateModal = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
                  <SpdButton tone="primary" :disabled="sourceLoading" @click="searchSourceProducts">
          <template #icon><Search :size="15" /></template>
          {{ sourceLoading ? '搜索中' : '搜索' }}
        </SpdButton>
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
                  <SpdButton label="选择文件" tone="primary" class="approval-attach-btn" :disabled="!canEditQualification"
                    @click="triggerAttachmentPicker">
          <template #icon><Upload :size="15" /></template>
        </SpdButton>
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
            <SpdButton label="取消" tone="neutral" @click="showCreateModal = false"></SpdButton>
            <SpdButton label="提交审批" tone="primary" type="submit">
          <template #icon><Send :size="16" /></template>
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="duplicateAlertVisible = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <div class="duplicate-alert-body">
          <AlertTriangle :size="40" aria-hidden="true" />
          <p>商品名称、规格型号、生产厂家、供应商、注册证号完全一致的目录已存在，请核对后修改或直接使用现有目录。</p>
        </div>
        <div class="dialog-actions">
          <SpdButton label="知道了" tone="primary" @click="duplicateAlertVisible = false"></SpdButton>
        </div>
      </section>
    </div>
    </Teleport>
  </section>
</template>
