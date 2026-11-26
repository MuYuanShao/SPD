<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Boxes,
  Building2,
  Calendar,
  CheckCircle2,
  CircleX,
  ClipboardList,
  Download,
  FileSearch,
  Filter,
  History,
  Package,
  PackageCheck,
  Printer,
  RefreshCw,
  ScanLine,
  Search,
  ShieldCheck,
  Tag,
  Truck,
  User,
  Warehouse
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import {
  consumeQuotaPackageByCode,
  fetchUdiTraceDetail,
  fetchUdiTraceOptions,
  fetchUdiTraceRecords,
  signQuotaPackageByCode,
  type UdiTraceDetail,
  type UdiTraceEvent,
  type UdiTraceOption,
  type UdiTraceRecord,
  type UdiTraceSummary
} from '../../api/udiTraceability'
import { formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const loading = ref(false)
const detailLoading = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'success' | 'warning' | 'error'>('info')
const records = ref<UdiTraceRecord[]>([])
const selected = ref<UdiTraceDetail | null>(null)
const statusOptions = ref<UdiTraceOption[]>([])
const traceScopeOptions = ref<UdiTraceOption[]>([])
const eventTypeOptions = ref<UdiTraceOption[]>([])
const summary = ref<UdiTraceSummary>({
  totalCodes: 0,
  highValueCodes: 0,
  quotaPackageCodes: 0,
  exceptionCodes: 0,
  inStockCodes: 0,
  consumedCodes: 0
})
const page = ref(1)
const size = ref(20)
const total = ref(0)
const query = reactive({
  keyword: '',
  traceScope: '',
  status: '',
  eventType: ''
})
const packageActionLoading = ref(false)
const packageAction = reactive({
  code: '',
  deptName: ''
})

const statusText: Record<string, string> = {
  in_stock: '在库',
  putaway: '已上架',
  requisitioned: '已申领',
  delivery_picked: '已拣配',
  patient_bound: '已绑定患者',
  delivered: '已配送',
  signed: '科室签收',
  consumed: '已消耗',
  settled: '已结算',
  isolated: '隔离'
}

/**
 * 追溯状态 → 全站通用状态条色调（SpdStatusChip 的 tone）。
 * 浅色底 + 深色字由组件统一提供，页面只负责语义映射。
 */
const statusTone: Record<string, SpdStatusChipTone> = {
  in_stock: 'info',
  putaway: 'info',
  delivered: 'warning',
  requisitioned: 'warning',
  delivery_picked: 'warning',
  patient_bound: 'warning',
  signed: 'warning',
  consumed: 'success',
  settled: 'success',
  isolated: 'danger'
}

const statusIcon: Record<string, typeof Package> = {
  in_stock: Warehouse,
  putaway: Warehouse,
  delivered: Truck,
  requisitioned: ClipboardList,
  delivery_picked: Package,
  patient_bound: User,
  signed: CheckCircle2,
  consumed: PackageCheck,
  settled: CheckCircle2,
  isolated: AlertTriangle
}

const scopeText: Record<string, string> = {
  high_value: '高值耗材',
  low_value_quota_pack: '低值定数包'
}

const eventText = computed(() =>
  eventTypeOptions.value.reduce<Record<string, string>>((acc, item) => {
    acc[item.value] = item.label
    return acc
  }, {})
)

const stats = computed(() => [
  { label: '追溯对象', value: summary.value.totalCodes ?? 0, tone: 'blue', icon: BarChart3 },
  { label: '高值耗材', value: summary.value.highValueCodes ?? 0, tone: 'amber', icon: Tag },
  { label: '低值定数包', value: summary.value.quotaPackageCodes ?? 0, tone: 'green', icon: Boxes },
  { label: '在库', value: summary.value.inStockCodes ?? 0, tone: 'cyan', icon: Warehouse },
  { label: '已完成消耗', value: summary.value.consumedCodes ?? 0, tone: 'success', icon: PackageCheck },
  { label: '异常 / 隔离', value: summary.value.exceptionCodes ?? 0, tone: 'red', icon: AlertTriangle }
])

const activeRecord = computed(() => selected.value?.record ?? records.value[0])
const timeline = computed(() => selected.value?.timeline ?? [])

const isHighValue = computed(() => activeRecord.value?.traceScope === 'high_value')

function labelStatus(value?: string) {
  if (!value) return '-'
  return statusText[value] ?? formatStatusText(value)
}

function pushMessage(text: string, tone: 'info' | 'success' | 'warning' | 'error' = 'info') {
  if (!text) return
  // 业务消息统一走顶部 ElMessage 弹层，避免占用布局、不与表单争抢高度，
  // 同时脱离 .main-area / 弹窗遮罩的 stacking context。
  notify.show(tone, text)
  // 兼容历史 inline-message 引用：保留本地状态以便模板或 watch 还能读取。
  message.value = text
  messageTone.value = tone
}

function selectEventType(value: string) {
  query.eventType = query.eventType === value ? '' : value
  page.value = 1
  loadRecords()
}

async function loadOptions() {
  const options = await fetchUdiTraceOptions()
  statusOptions.value = options.statuses
  traceScopeOptions.value = options.traceScopes
  eventTypeOptions.value = options.eventTypes
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await fetchUdiTraceRecords({
      keyword: query.keyword,
      traceScope: query.traceScope,
      status: query.status,
      eventType: query.eventType,
      page: String(page.value),
      size: String(size.value)
    })
    records.value = data.rows
    total.value = data.total
    summary.value = {
      ...summary.value,
      ...(data.summary as UdiTraceSummary | undefined)
    }
    if (records.value.length > 0) {
      await loadDetail(records.value[0].uniqueCode)
    } else {
      selected.value = null
    }
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '加载 UDI 追溯数据失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadDetail(code: string) {
  detailLoading.value = true
  try {
    selected.value = await fetchUdiTraceDetail(code)
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '追溯详情加载失败', 'error')
  } finally {
    detailLoading.value = false
  }
}

function submitSearch() {
  page.value = 1
  loadRecords()
}

function resetSearch() {
  query.keyword = ''
  query.traceScope = ''
  query.status = ''
  query.eventType = ''
  page.value = 1
  loadRecords()
}

function changePage(nextPage: number) {
  page.value = nextPage
  loadRecords()
}

function changeSize(nextSize: number) {
  size.value = nextSize
  page.value = 1
  loadRecords()
}

async function signQuotaPackage() {
  if (!packageAction.code.trim()) {
    pushMessage('请扫描或输入定数包唯一码', 'warning')
    return
  }
  packageActionLoading.value = true
  try {
    const result = await signQuotaPackageByCode(packageAction.code.trim())
    query.keyword = packageAction.code.trim()
    await loadRecords()
    pushMessage(`定数包已签收入库：${result.deliveryNo}`, 'success')
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '定数包签收入库失败', 'error')
  } finally {
    packageActionLoading.value = false
  }
}

async function consumeQuotaPackage() {
  if (!packageAction.code.trim()) {
    pushMessage('请扫描或输入定数包唯一码', 'warning')
    return
  }
  packageActionLoading.value = true
  try {
    const result = await consumeQuotaPackageByCode({
      code: packageAction.code.trim(),
      deptName: packageAction.deptName.trim() || undefined
    })
    query.keyword = packageAction.code.trim()
    await loadRecords()
    pushMessage(
      `定数包已消耗并进入结算：${result.consumptionNo}，金额 ¥${Number(result.amount).toFixed(2)}`,
      'success'
    )
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '定数包扫码消耗失败', 'error')
  } finally {
    packageActionLoading.value = false
  }
}

function exportRecords() {
  if (!records.value.length) {
    pushMessage('暂无可导出的追溯记录', 'warning')
    return
  }
  const header = ['唯一码', 'UDI', '对象类型', '商品编码', '商品名称', '规格', '厂家', '批次', '状态', '当前位置', '最近节点', '最近时间']
  const escape = (val: unknown) => {
    const s = val === null || val === undefined ? '' : String(val)
    if (/[",\n]/.test(s)) return '"' + s.replace(/"/g, '""') + '"'
    return s
  }
  const lines = records.value.map((row) => [
    row.uniqueCode,
    row.udiCode,
    scopeText[row.traceScope] || row.traceScope,
    row.productCode,
    row.productName,
    row.specModel,
    row.manufacturerName,
    row.batchNo,
    labelStatus(row.currentStatus),
    row.currentLocation,
    row.lastEventName,
    row.lastEventTime
  ].map(escape).join(','))
  const csv = '\uFEFF' + header.join(',') + '\n' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const stamp = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19)
  a.href = url
  a.download = `UDI追溯记录-${stamp}.csv`
  a.click()
  URL.revokeObjectURL(url)
  pushMessage(`已导出 ${records.value.length} 条追溯记录`, 'success')
}

function printDetail() {
  if (!activeRecord.value) {
    pushMessage('请先选择追溯对象', 'warning')
    return
  }
  window.print()
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})

const eventDotTone: Record<string, string> = {
  sign: 'green',
  putaway: 'blue',
  requisition: 'blue',
  pick: 'blue',
  deliver: 'amber',
  sign_in: 'amber',
  consume: 'green',
  settle: 'green',
  isolate: 'red',
  recall: 'red',
  reverse: 'red'
}

function eventTone(event: UdiTraceEvent) {
  if (eventDotTone[event.eventType]) return eventDotTone[event.eventType]
  const status = String(event.status || '').toLowerCase()
  if (status === 'failed' || status === 'reversed') return 'red'
  if (status === 'warning') return 'amber'
  return 'blue'
}

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const UDITRACE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'traceCode', label: '唯一码 / UDI', width: 180, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'objectType', label: '对象类型', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 300 },
  { key: 'batchNo', label: '批次', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'currentLocation', label: '当前位置', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'lastNode', label: '最近节点', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'actions', label: '操作', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 200, fill: true }
]

const udiTraceCols = useListColumns({
  storageKey: 'spd-udi-trace-cols-v1',
  defaults: UDITRACE_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const udiTraceTableRef = udiTraceCols.tableRef
const isUdiTraceColVisible = (key: string) => udiTraceCols.isColumnVisible(key)
const udiTraceColBinding = (key: string) => udiTraceCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const udiTraceActionFix = useActionColumnFix()
const udiTraceActionFixed = computed(() => udiTraceActionFix.actionFixedBinding.value)

watch(
  [udiTraceTableRef],
  () => {
    udiTraceActionFix.observe(udiTraceTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const udiTraceColSettingsAnchorRef = ref<HTMLElement | null>(null)
const udiTraceColSettingsPopRef = ref<HTMLElement | null>(null)
const udiTraceColSettingsPop = useColSettingsPopover({
  open: udiTraceCols.settingsOpen,
  anchorRef: udiTraceColSettingsAnchorRef,
  popRef: udiTraceColSettingsPopRef
})

</script>

<template>
  <main class="udi-page spd-unified-list">
    <MdPageHeader
      crumb="专项与合规"
      title="UDI / 唯一码追溯"
      :loading="loading"
      @refresh="loadRecords"
    />

    <!-- ============ 工作台：扫描查询 ============ -->
    <section class="udi-workbench">
      <div class="scan-card">
        <div class="scan-card-head">
          <span class="scan-card-icon"><ScanLine :size="18" /></span>
          <div>
            <strong>扫码追溯</strong>
            <small>支持 UDI / 唯一码 / 定数包码 / 批号 / 商品关键字</small>
          </div>
        </div>
        <form class="scan-form" @submit.prevent="submitSearch">
          <div class="scan-input-wrap">
            <Search :size="16" class="scan-input-icon" />
            <input
              v-model.trim="query.keyword"
              type="search"
              placeholder="扫描或输入 UDI / 唯一码 / 定数包码 / 批号 / 商品，回车定位"
              @keyup.enter="submitSearch"
            />
            <button v-if="query.keyword" type="button" class="scan-clear" aria-label="清空" @click="query.keyword = ''">
              <CircleX :size="14" />
            </button>
          </div>
          <SpdButton tone="primary" type="submit" class="scan-submit" :disabled="loading">
          <template #icon><Search :size="15" /></template>
          {{ loading ? '查询中...' : '查询追溯' }}
        </SpdButton>
          <SpdButton label="重置" tone="neutral" class="scan-reset" @click="resetSearch">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
        </form>
      </div>

      <div class="filter-card">
        <div class="filter-card-head">
          <span class="filter-card-icon"><Filter :size="16" /></span>
          <strong>筛选条件</strong>
        </div>
        <div class="filter-card-grid">
          <label>
            <span>对象类型</span>
            <select v-model="query.traceScope" @change="submitSearch">
              <option value="">全部对象</option>
              <option v-for="item in traceScopeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label>
            <span>当前状态</span>
            <select v-model="query.status" @change="submitSearch">
              <option value="">全部状态</option>
              <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
        </div>
      </div>
    </section>

    <!-- ============ 事件类型彩色 tab ============ -->
    <section class="event-tabs" aria-label="追溯事件筛选">
      <button
        type="button"
        class="event-tab is-all"
        :class="{ active: query.eventType === '' }"
        @click="selectEventType('')"
      >
        <BarChart3 :size="14" />
        全部事件
      </button>
      <button
        v-for="(item, idx) in eventTypeOptions"
        :key="item.value"
        type="button"
        class="event-tab"
        :class="[`is-tone-${idx % 5}`, { active: query.eventType === item.value }]"
        @click="selectEventType(item.value)"
      >
        {{ item.label }}
      </button>
    </section>

    <!-- ============ 定数包快捷作业 ============ -->
    <section class="quota-card">
      <div class="quota-card-head">
        <span class="quota-card-icon"><Boxes :size="18" /></span>
        <div>
          <strong>低值定数包扫码作业</strong>
          <small>签收入库后可整包消耗并进入结算，支持 UDI / 唯一码 / 定数包码</small>
        </div>
        <span class="quota-card-tag">快捷作业</span>
      </div>
      <div class="quota-card-grid">
        <label>
          <span class="quota-label-tag is-scan"><ScanLine :size="13" /> 扫描码</span>
          <input
            v-model.trim="packageAction.code"
            placeholder="UDI / 唯一码 / 定数包码"
            @keyup.enter="signQuotaPackage"
          />
        </label>
        <label>
          <span class="quota-label-tag is-dept"><Building2 :size="13" /> 消耗科室</span>
          <input v-model.trim="packageAction.deptName" placeholder="可自动识别，留空将按规则推导" />
        </label>
        <div class="quota-card-actions">
          <SpdButton label="签收入库" tone="primary" class="quota-action-sign" :disabled="packageActionLoading"
            @click="signQuotaPackage">
          <template #icon><Truck :size="16" /></template>
        </SpdButton>
          <SpdButton label="扫码消耗" tone="primary" class="quota-action-consume" :disabled="packageActionLoading"
            @click="consumeQuotaPackage">
          <template #icon><PackageCheck :size="16" /></template>
        </SpdButton>
        </div>
      </div>
    </section>

    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

    <!-- ============ 汇总 chips ============ -->
    <section class="udi-summary" aria-label="追溯汇总">
      <div
        v-for="item in stats"
        :key="item.label"
        class="udi-summary-item"
        :class="`tone-${item.tone}`"
      >
        <span class="udi-summary-icon"><component :is="item.icon" :size="14" /></span>
        <span class="udi-summary-label">{{ item.label }}</span>
        <strong class="udi-summary-value">{{ item.value }}</strong>
      </div>
    </section>

    <!-- ============ 追溯详情：时间线 + 单件详情 ============ -->
    <section class="trace-layout">
      <div class="trace-main">
        <header class="udi-panel-header">
          <div class="udi-panel-title">
            <span class="udi-panel-icon"><History :size="16" /></span>
            <div>
              <h3>追溯时间线</h3>
              <p v-if="activeRecord">
                <span class="scope-pill" :class="isHighValue ? 'is-hv' : 'is-quota'">
                  {{ scopeText[activeRecord.traceScope] || '追溯对象' }}
                </span>
                <span class="trace-code">{{ activeRecord.uniqueCode }}</span>
              </p>
              <p v-else>暂无追溯对象</p>
            </div>
          </div>
          <SpdStatusChip
            v-if="activeRecord"
            :tone="statusTone[activeRecord.currentStatus] || 'neutral'"
          >
            <component :is="statusIcon[activeRecord.currentStatus] || ShieldCheck" :size="13" />
            {{ labelStatus(activeRecord.currentStatus) }}
          </SpdStatusChip>
        </header>
        <ol v-if="timeline.length" class="timeline" :class="{ loading: detailLoading }">
          <li
            v-for="(event, idx) in timeline"
            :key="event.traceEventId"
            :class="[`tone-${eventTone(event)}`, { last: idx === timeline.length - 1 }]"
          >
            <span class="timeline-dot">
              <ShieldCheck :size="14" />
            </span>
            <div class="timeline-content">
              <div class="timeline-head">
                <strong>{{ event.eventName || eventText[event.eventType] || event.eventType }}</strong>
                <small>{{ event.eventTime }}</small>
              </div>
              <p class="timeline-meta">
                <span v-if="event.locationName"><Building2 :size="12" /> {{ event.locationName }}</span>
                <span v-if="event.operatorName"><User :size="12" /> {{ event.operatorName }}</span>
                <span v-if="event.bizNo"><Tag :size="12" /> {{ event.bizNo }}</span>
              </p>
              <em v-if="event.remark">{{ formatRemarkText(event.remark) }}</em>
            </div>
          </li>
        </ol>
        <EmptyState v-else message="暂无追溯节点" />
      </div>

      <aside class="trace-detail">
        <header class="udi-panel-header compact">
          <div class="udi-panel-title">
            <span class="udi-panel-icon"><FileSearch :size="16" /></span>
            <h3>单件详情</h3>
          </div>
          <span v-if="detailLoading" class="detail-loading-tag">加载中</span>
        </header>
        <dl v-if="activeRecord" class="detail-list">
          <dt>对象类型</dt>
          <dd>
            <span class="scope-pill" :class="isHighValue ? 'is-hv' : 'is-quota'">
              {{ scopeText[activeRecord.traceScope] || '-' }}
            </span>
          </dd>
          <dt>商品</dt>
          <dd>
            <strong>{{ activeRecord.productName }}</strong>
            <small>{{ activeRecord.productCode }} · {{ activeRecord.specModel || '-' }}</small>
          </dd>
          <dt>UDI</dt>
          <dd class="detail-mono">{{ activeRecord.udiCode }}</dd>
          <dt>唯一码</dt>
          <dd class="detail-mono detail-strong">{{ activeRecord.uniqueCode }}</dd>
          <template v-if="activeRecord.traceScope === 'low_value_quota_pack'">
            <dt>定数包码</dt>
            <dd>{{ activeRecord.packageLabelNo || '-' }}</dd>
            <dt>定数模板</dt>
            <dd>{{ activeRecord.templateName || '-' }} <small>{{ activeRecord.templateCode || '' }}</small></dd>
            <dt>包内数量</dt>
            <dd>{{ activeRecord.packageQuantity || '-' }} {{ activeRecord.packageUnit || '' }}</dd>
          </template>
          <dt>创建时间</dt>
          <dd class="detail-mono">{{ activeRecord.createTime || '-' }}</dd>
          <dt>修改时间</dt>
          <dd class="detail-mono">{{ activeRecord.updateTime || '-' }}</dd>
          <dt>系统批次</dt>
          <dd>{{ activeRecord.batchNo || '-' }}</dd>
          <dt>当前库位</dt>
          <dd>
            <Warehouse :size="12" />
            {{ activeRecord.currentLocation || '-' }}
          </dd>
          <dt>当前科室</dt>
          <dd>
            <Building2 :size="12" />
            {{ activeRecord.currentDepartment || '-' }}
          </dd>
          <dt>有效期</dt>
          <dd>
            <Calendar :size="12" />
            {{ activeRecord.expireDate || '-' }}
          </dd>
          <dt>厂家 / 供应商</dt>
          <dd>{{ activeRecord.manufacturerName || '-' }} <small>/ {{ activeRecord.supplierName || '-' }}</small></dd>
          <template v-if="isHighValue">
            <dt>患者</dt>
            <dd>
              <User :size="12" />
              {{ activeRecord.patientNo || '-' }} {{ activeRecord.patientNameMasked || '' }}
            </dd>
          </template>
          <template v-else>
            <dt>包状态</dt>
            <dd>{{ formatStatusText(activeRecord.packageStatus) }}</dd>
          </template>
        </dl>
        <EmptyState v-else message="请选择追溯记录" />
      </aside>
    </section>

    <!-- ============ 唯一码记录表 ============ -->
    <section class="record-panel">
      <header class="udi-panel-header compact">
        <div class="udi-panel-title">
          <span class="udi-panel-icon"><ClipboardList :size="16" /></span>
          <div>
            <h3>唯一码记录</h3>
            <p>共 {{ total }} 条 · 当前第 {{ page }} 页</p>
          </div>
        </div>
        <div class="record-tools">
          <SpdButton label="刷新" tone="neutral" class="tool-btn tool-refresh" :disabled="loading" @click="loadRecords">
          <template #icon><RefreshCw :size="14" /></template>
        </SpdButton>
          <SpdButton label="导出 CSV" tone="primary" class="tool-btn tool-export" :disabled="!records.length" @click="exportRecords">
          <template #icon><Download :size="14" /></template>
        </SpdButton>
          <SpdButton label="打印" tone="neutral" class="tool-btn tool-print" :disabled="!activeRecord" @click="printDetail">
          <template #icon><Printer :size="14" /></template>
        </SpdButton>
        </div>
      </header>
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="udiTraceColSettingsAnchorRef"
           
           
            @click="udiTraceCols.settingsOpen.value = !udiTraceCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="udiTraceCols.settingsOpen.value"
ref="udiTraceColSettingsPopRef"
:class="{ 'is-align-right': udiTraceColSettingsPop.align.value === 'right', 'is-align-up': udiTraceColSettingsPop.vertical.value === 'up' }"
:style="udiTraceColSettingsPop.popStyle.value"
:columns="udiTraceCols.columns.value"
:visible-column-count="udiTraceCols.visibleColumnCount.value"
@toggle="udiTraceCols.setColumnVisible"
@move="udiTraceCols.moveColumn"
@reset="udiTraceCols.resetColumns()"
@confirm="udiTraceCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="udiTraceTableRef"
            :data="records"
            row-key="uniqueCode"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无追溯记录'"
            @header-dragend="(width: number, oldWidth: number, column: any) => udiTraceCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="traceCode"
              v-if="isUdiTraceColVisible('traceCode')"
              label="唯一码 / UDI"
              v-bind="udiTraceColBinding('traceCode')"
            >
              <template #header>
                <span class="ulc-th-text">唯一码 / UDI</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-mono ulc-wrap-text">{{ row.uniqueCode }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ row.udiCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="objectType"
              v-if="isUdiTraceColVisible('objectType')"
              label="对象类型"
              v-bind="udiTraceColBinding('objectType')"
            >
              <template #header>
                <span class="ulc-th-text">对象类型</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="scope-pill" :class="row.traceScope === 'high_value' ? 'is-hv' : 'is-quota'">
                    {{ scopeText[row.traceScope] || '-' }}
                  </span>
                  <span class="ulc-mono ulc-wrap-text" v-if="row.packageLabelNo">{{ row.packageLabelNo }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isUdiTraceColVisible('product')"
              label="商品"
              v-bind="udiTraceColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.productName }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ row.productCode }} · {{ row.specModel || '-' }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub" v-if="row.templateName">{{ row.templateName }} · {{ row.packageQuantity || '-' }}{{ row.packageUnit || '' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="batchNo"
              v-if="isUdiTraceColVisible('batchNo')"
              label="批次"
              v-bind="udiTraceColBinding('batchNo')"
            >
              <template #header>
                <span class="ulc-th-text">批次</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.batchNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isUdiTraceColVisible('status')"
              label="状态"
              v-bind="udiTraceColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone[row.currentStatus] || 'neutral'">
                    <component :is="statusIcon[row.currentStatus] || ShieldCheck" :size="12" />
                    {{ labelStatus(row.currentStatus) }}
                  </SpdStatusChip>
              </template>
            </el-table-column>
            <el-table-column
              column-key="currentLocation"
              v-if="isUdiTraceColVisible('currentLocation')"
              label="当前位置"
              v-bind="udiTraceColBinding('currentLocation')"
            >
              <template #header>
                <span class="ulc-th-text">当前位置</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.currentLocation || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="lastNode"
              v-if="isUdiTraceColVisible('lastNode')"
              label="最近节点"
              v-bind="udiTraceColBinding('lastNode')"
            >
              <template #header>
                <span class="ulc-th-text">最近节点</span>
              </template>
              <template #default="{ row }">
                <span class="last-event">{{ row.lastEventName || '-' }}</span>
                  <small>{{ row.lastEventTime || '-' }}</small>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isUdiTraceColVisible('createTime')"
              label="创建时间"
              v-bind="udiTraceColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isUdiTraceColVisible('updateTime')"
              label="修改时间"
              v-bind="udiTraceColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isUdiTraceColVisible('actions')"
              label="操作"
              v-bind="udiTraceColBinding('actions')"
              :fixed="udiTraceActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button @click.stop="loadDetail(row.uniqueCode)" class="ulc-action row-action-btn" type="button"><ArrowRight :size="13" />
                    追溯</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无追溯记录" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="page"
            :total-pages="Math.max(1, Math.ceil(total / size))"
            :total="total"
            :size="size"
            :loading="loading"
            @update:page="changePage"
            @update:size="changeSize"
          />
        </template>
      </MdDataTableCard>
    </section>
  </main>
</template>

<style scoped>
/* =====================================================================
   UDI / 唯一码追溯（重制版）
   - 三段式工作台：扫码查询 / 筛选 / 事件 tab
   - 快捷作业卡片（橙色签收 + 绿色消耗）
   - 时间线节点按事件类型着色
   - 单件详情按字段分组
   - 唯一码记录表带行点击 + 状态彩色 pill
   ===================================================================== */
.udi-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  margin: 0 !important;
  padding: 16px 20px 24px;
  border: 0 !important;
  background: transparent;
  overflow: visible;
}

.udi-page h3,
.udi-page p {
  margin: 0;
}

/* ---------- 工作台：扫描 + 筛选双卡 ---------- */
.udi-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 1fr);
  gap: 12px;
  align-items: stretch;
}

.scan-card,
.filter-card,
.quota-card,
.trace-main,
.trace-detail,
.record-panel {
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.scan-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-left: 3px solid var(--primary, var(--spd-p-info));
  background: linear-gradient(120deg, var(--spd-p-info-bg) 0%, var(--spd-p-bg) 65%);
}

.scan-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.scan-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--spd-p-info);
  background: var(--spd-p-border);
  border-radius: 8px;
}

.scan-card-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.scan-card-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.scan-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.scan-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 12px 0 38px;
  border: 1px solid var(--spd-p-info-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.scan-input-wrap:focus-within {
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.scan-input-icon {
  position: absolute;
  left: 12px;
  color: var(--spd-p-info);
}

.scan-input-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--spd-text-primary);
  font: inherit;
  font-size: var(--spd-font-size-base);
}

.scan-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-left: 6px;
  color: var(--spd-p-text-muted);
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}

.scan-clear:hover {
  color: var(--spd-p-danger);
  background: var(--spd-p-danger-bg);
}

/* 扫描条按钮：完全服从 SpdButton 统一几何与配色，
   不再覆盖高度（此前 40px，高于全站 34px）/ 内边距 / 字号 / 颜色。 */
.scan-submit,
.scan-reset {
  white-space: nowrap;
}

.filter-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-left: 3px solid var(--spd-p-success);
}

.filter-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-radius: 6px;
}

.filter-card-head strong {
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.filter-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.filter-card-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.filter-card-grid label > span {
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.filter-card-grid select {
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  font: inherit;
  font-size: var(--spd-font-size-base);
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.filter-card-grid select:focus {
  outline: 0;
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 事件类型彩色 tab ---------- */
.event-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0 2px;
}

.event-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid var(--spd-p-border);
  border-radius: 999px;
  background: var(--spd-p-bg);
  color: var(--spd-text-secondary);
  font: inherit;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.event-tab:hover {
  border-color: var(--spd-p-border-strong);
  color: var(--spd-p-text-strong);
  background: var(--spd-p-bg-muted);
}

.event-tab.is-all {
  color: var(--spd-p-text-secondary);
}

.event-tab.is-tone-0 {
  border-color: var(--spd-p-info-border);
  color: var(--spd-p-info-strong);
}
.event-tab.is-tone-0:hover,
.event-tab.is-tone-0.active {
  border-color: var(--spd-p-info);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-info) 0%, var(--spd-p-info) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.event-tab.is-tone-1 {
  border-color: var(--spd-p-success-border);
  color: var(--spd-p-success-strong);
}
.event-tab.is-tone-1:hover,
.event-tab.is-tone-1.active {
  border-color: var(--spd-p-success);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-success) 0%, var(--spd-p-success) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.event-tab.is-tone-2 {
  border-color: var(--spd-p-warning-border);
  color: var(--spd-p-warning-text);
}
.event-tab.is-tone-2:hover,
.event-tab.is-tone-2.active {
  border-color: var(--spd-p-warning);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-warning) 0%, var(--spd-p-warning) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.event-tab.is-tone-3 {
  border-color: var(--spd-p-danger-border);
  color: var(--spd-p-danger-strong);
}
.event-tab.is-tone-3:hover,
.event-tab.is-tone-3.active {
  border-color: var(--spd-p-danger);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-danger) 0%, var(--spd-p-danger) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.event-tab.is-tone-4 {
  border-color: var(--spd-p-info-border);
  color: var(--spd-p-primary);
}
.event-tab.is-tone-4:hover,
.event-tab.is-tone-4.active {
  border-color: var(--spd-p-primary-strong);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-primary-strong) 0%, var(--spd-p-primary-strong) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.event-tab.is-all.active {
  border-color: var(--spd-p-text-strong);
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-text-strong) 0%, var(--spd-p-text-secondary) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 定数包快捷作业卡 ---------- */
.quota-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  border-left: 3px solid var(--spd-p-warning);
  background: linear-gradient(120deg, var(--spd-p-warning-bg) 0%, var(--spd-p-bg) 65%);
}

.quota-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quota-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-radius: 8px;
}

.quota-card-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.quota-card-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.quota-card-tag {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  padding: 2px 10px;
  color: var(--spd-p-warning-text);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  background: var(--spd-p-warning-bg);
  border: 1px solid var(--spd-p-warning-border);
  border-radius: 999px;
}

.quota-card-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(180px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.quota-card-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.quota-label-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  width: max-content;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  border-radius: 4px;
}

.quota-label-tag.is-scan {
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
  border: 1px solid var(--spd-p-info-border);
}

.quota-label-tag.is-dept {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success-border);
}

.quota-card-grid input {
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  font: inherit;
  font-size: var(--spd-font-size-base);
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.quota-card-grid input:focus {
  outline: 0;
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.quota-card-actions {
  display: flex;
  gap: 8px;
}

/* 定数包操作按钮：完全服从 SpdButton 统一几何与配色，
   仅保留最小宽度与图标间距，不再覆盖高度 / 内边距 / 字号 / 颜色。 */
.quota-action-sign,
.quota-action-consume {
  gap: 5px;
  min-width: 130px;
  white-space: nowrap;
}

/* ---------- 消息提示 ---------- */
.form-message {
  padding: 9px 12px;
  border: 1px solid;
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-primary-bg);
  color: var(--spd-text-primary);
  font-size: var(--spd-font-size-sm);
}

.form-message.is-info {
  border-color: var(--spd-p-info-border);
  background: var(--spd-p-info-bg);
  color: var(--spd-p-info-strong);
}

.form-message.is-success {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-primary-bg);
  color: var(--spd-p-success-strong);
}

.form-message.is-warning {
  border-color: var(--spd-p-warning-border);
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-warning-text);
}

.form-message.is-error {
  border-color: var(--spd-p-danger-border);
  background: var(--spd-p-danger-bg);
  color: var(--spd-p-danger-strong);
}

/* ---------- 汇总 chips ---------- */
.udi-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.udi-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 130px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 999px;
  font-size: var(--spd-font-size-sm);
  background: var(--spd-p-bg);
  transition: transform 0.1s ease;
}

.udi-summary-item:hover {
  transform: translateY(-1px);
}

.udi-summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.udi-summary-label {
  color: var(--spd-text-secondary);
}

.udi-summary-value {
  margin-left: auto;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.udi-summary-item.tone-blue {
  border-color: var(--spd-p-info-border);
  background: var(--spd-p-info-bg);
}
.udi-summary-item.tone-blue .udi-summary-icon {
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
}
.udi-summary-item.tone-blue .udi-summary-value {
  color: var(--spd-p-info-strong);
}

.udi-summary-item.tone-amber {
  border-color: var(--spd-p-warning-border);
  background: var(--spd-p-warning-bg);
}
.udi-summary-item.tone-amber .udi-summary-icon {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
}
.udi-summary-item.tone-amber .udi-summary-value {
  color: var(--spd-p-warning-text);
}

.udi-summary-item.tone-green {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-primary-bg);
}
.udi-summary-item.tone-green .udi-summary-icon {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
}
.udi-summary-item.tone-green .udi-summary-value {
  color: var(--spd-p-success-strong);
}

.udi-summary-item.tone-success {
  border-color: var(--spd-p-success);
  background: var(--spd-p-success-bg);
}
.udi-summary-item.tone-success .udi-summary-icon {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-border);
}
.udi-summary-item.tone-success .udi-summary-value {
  color: var(--spd-p-primary-strong);
}

.udi-summary-item.tone-cyan {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-info-bg);
}
.udi-summary-item.tone-cyan .udi-summary-icon {
  color: var(--spd-p-success-text);
  background: var(--spd-p-info-bg);
}
.udi-summary-item.tone-cyan .udi-summary-value {
  color: var(--spd-p-info);
}

.udi-summary-item.tone-red {
  border-color: var(--spd-p-danger-border);
  background: var(--spd-p-danger-bg);
}
.udi-summary-item.tone-red .udi-summary-icon {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
}
.udi-summary-item.tone-red .udi-summary-value {
  color: var(--spd-p-danger-strong);
}

/* ---------- 面板通用 ---------- */
.udi-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.udi-panel-header.compact {
  align-items: center;
}

.udi-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.udi-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: var(--spd-p-info);
  background: var(--spd-p-border);
  border-radius: 6px;
}

.udi-panel-header h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.udi-panel-header p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.trace-code {
  font-family: var(--spd-font-family-mono);
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.detail-loading-tag {
  padding: 2px 8px;
  color: var(--spd-p-info-strong);
  font-size: var(--spd-font-size-sm);
  background: var(--spd-p-border);
  border-radius: 999px;
}

.record-tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* 工具条按钮：完全服从 SpdButton 统一几何与配色，
   不再覆盖高度（此前 30px）/ 内边距 / 字号。 */
.tool-btn {
  gap: 4px;
}

.tool-refresh {
  color: var(--spd-p-text-secondary);
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
}
.tool-refresh:hover:not(:disabled) {
  color: var(--spd-p-text-strong);
  background: var(--spd-p-border);
  border-color: var(--spd-p-border-strong);
}

.tool-export {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-primary-bg);
  border: 1px solid var(--spd-p-success-border);
}
.tool-export:hover:not(:disabled) {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success);
}

.tool-print {
  color: var(--spd-p-primary);
  background: var(--spd-p-bg-subtle);
  border: 1px solid var(--spd-p-info-border);
}
.tool-print:hover:not(:disabled) {
  color: var(--spd-p-primary-strong);
  background: var(--spd-p-info-bg);
  border-color: var(--spd-p-primary-strong);
}

/* ---------- 时间线 ---------- */
.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 14px;
  align-items: start;
}

.trace-main {
  padding: 16px;
}

.timeline {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
  transition: opacity 0.15s ease;
}

.timeline.loading {
  opacity: 0.55;
}

.timeline li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  position: relative;
  padding-bottom: 16px;
}

.timeline li:not(.last)::before {
  content: '';
  position: absolute;
  top: 28px;
  bottom: 0;
  left: 15px;
  width: 2px;
  background: var(--spd-p-border);
}

.timeline-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 2px solid var(--spd-p-bg);
  border-radius: 50%;
  background: var(--spd-p-border);
  color: var(--spd-p-info);
  z-index: 1;
  box-shadow: var(--spd-p-shadow-1);
}

.timeline li.tone-blue .timeline-dot {
  background: var(--spd-p-border);
  color: var(--spd-p-info-strong);
  box-shadow: var(--spd-p-shadow-1);
}
.timeline li.tone-blue:not(.last)::before {
  background: linear-gradient(180deg, var(--spd-p-info-border) 0%, var(--spd-p-border) 100%);
}

.timeline li.tone-green .timeline-dot {
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
  box-shadow: var(--spd-p-shadow-1);
}
.timeline li.tone-green:not(.last)::before {
  background: linear-gradient(180deg, var(--spd-p-success-border) 0%, var(--spd-p-border) 100%);
}

.timeline li.tone-amber .timeline-dot {
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-warning-text);
  box-shadow: var(--spd-p-shadow-1);
}
.timeline li.tone-amber:not(.last)::before {
  background: linear-gradient(180deg, var(--spd-p-warning-border) 0%, var(--spd-p-border) 100%);
}

.timeline li.tone-red .timeline-dot {
  background: var(--spd-p-danger-bg);
  color: var(--spd-p-danger-strong);
  box-shadow: var(--spd-p-shadow-1);
}
.timeline li.tone-red:not(.last)::before {
  background: linear-gradient(180deg, var(--spd-p-danger-border) 0%, var(--spd-p-border) 100%);
}

.timeline-content {
  min-width: 0;
  padding: 0 0 0 4px;
}

.timeline-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-head strong {
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.timeline-head small {
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 4px 0 0;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.timeline-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.timeline em {
  display: block;
  margin-top: 4px;
  padding: 4px 8px;
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-style: normal;
  background: var(--spd-p-bg-muted);
  border-left: 2px solid var(--spd-p-border-strong);
  border-radius: 0 4px 4px 0;
}

.timeline li.tone-red em {
  background: var(--spd-p-danger-bg);
  border-left-color: var(--spd-p-danger-border);
  color: var(--spd-p-danger-strong);
}

/* ---------- 单件详情 ---------- */
.trace-detail {
  padding: 16px;
}

.detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px 10px;
  margin: 0;
}

.detail-list dt {
  display: flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 0;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.detail-list dd {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-width: 0;
  min-height: 28px;
  margin: 0;
  padding: 4px 0;
  color: var(--spd-text-primary);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.detail-list dd > svg {
  color: var(--spd-p-text-muted);
  flex-shrink: 0;
}

.detail-list dd small {
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
}

.detail-list dd strong {
  display: block;
  width: 100%;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.detail-mono {
  font-family: var(--spd-font-family-mono) !important;
  font-size: var(--spd-font-size-sm) !important;
  color: var(--spd-p-text-secondary) !important;
}

.detail-strong {
  color: var(--spd-p-info-strong) !important;
  font-weight: 600 !important;
}

/* ---------- 状态 / 范围 pill ----------
   列表状态条统一走通用组件 <SpdStatusChip>（浅色底 + 深色字），
   本页不再写局部的 .status-pill 配色覆盖。 */
.scope-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border: 1px solid;
  border-radius: 999px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  white-space: nowrap;
}

.scope-pill.is-hv {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

.scope-pill.is-quota {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success-border);
}

/* ---------- 记录表 ---------- */
.record-panel {
  padding: 16px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  scrollbar-width: thin;
}

.table-wrap::-webkit-scrollbar {
  height: 8px;
}

.table-wrap::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--spd-p-border-strong);
}

.table-wrap::-webkit-scrollbar-thumb:hover {
  background: var(--spd-p-text-muted);
}

.table-wrap::-webkit-scrollbar-track {
  background: transparent;
}

table {
  width: 100%;
  min-width: 1100px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  /* 表头文字：深色强调文字（深字浅底，保证可读）。
     此前误用 --spd-p-bg-muted（浅灰）配浅蓝底，导致表头文字几乎不可见。 */
  color: var(--spd-p-text-strong) !important;
  background: linear-gradient(180deg, var(--spd-p-bg-subtle) 0%, var(--spd-p-primary-bg) 100%) !important;
  border-bottom: 1px solid var(--spd-p-border) !important;
  text-align: left;
  white-space: nowrap;
}

tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid var(--spd-p-text-strong) !important;
  color: var(--spd-p-text-secondary) !important;
  font-size: var(--spd-font-size-base) !important;
  vertical-align: middle;
  background: var(--spd-p-bg);
}

tbody tr:nth-child(even) td {
  background: var(--spd-p-row-stripe);
}

tbody tr {
  cursor: pointer;
  transition: background 0.12s ease;
}

tbody tr:hover td {
  background: var(--spd-p-info-bg) !important;
}

tbody tr.selected td {
  background: var(--spd-p-info-bg) !important;
  box-shadow: var(--spd-p-shadow-1);
}

tbody tr.selected:hover td {
  background: var(--spd-p-info-bg) !important;
}

.cell-mono {
  font-family: var(--spd-font-family-mono) !important;
  font-size: var(--spd-font-size-base) !important;
  color: var(--spd-p-text-secondary) !important;
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
}

.last-event {
  display: block;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}

.th-action,
.td-action {
  text-align: right;
  white-space: nowrap;
}

.row-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 28px;
  padding: 0 10px;
  color: var(--spd-p-info-strong);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  background: var(--spd-p-border);
  border: 1px solid var(--spd-p-info-border);
  border-radius: 999px;
  transition: all 0.15s ease;
}

.row-action-btn:hover {
  color: var(--spd-p-bg);
  background: var(--spd-p-info);
  border-color: var(--spd-p-info);
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .udi-workbench,
  .quota-card-grid,
  .trace-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .udi-page {
    padding: 12px 12px 20px;
  }
  .scan-form,
  .filter-card-grid,
  .quota-card-grid {
    grid-template-columns: 1fr;
  }
  .quota-card-actions {
    width: 100%;
  }
  .quota-action-sign,
  .quota-action-consume {
    flex: 1;
  }
  .udi-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
  .record-tools {
    flex-wrap: wrap;
  }
}
</style>
