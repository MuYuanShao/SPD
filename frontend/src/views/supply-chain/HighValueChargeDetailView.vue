<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertCircle,
  BarChart3,
  Building2,
  Calendar,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  CircleX,
  Download,
  FileSpreadsheet,
  Filter,
  Hash,
  Info,
  Layers,
  Pill,
  Printer,
  RefreshCw,
  Search,
  ShieldCheck,
  Stethoscope,
  Tag,
  Truck,
  User,
  Wallet
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import HighValueOperationsPanel from '../../components/supply-chain/HighValueOperationsPanel.vue'
import { useChargeDetailQuery } from '../../composables/useChargeDetailQuery'
import { useAuthStore } from '../../stores/auth'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import type { HighValueChargeDetail } from '../../api/highValueCharges'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
/** Tab 切换：detail（计费明细查询）/ operations（高值耗材计费操作） */
type ChargeTab = 'detail' | 'operations'
const isOperationsTab = computed<boolean>(() => String(route.query.tab || '') === 'operations')
const activeTab = computed<ChargeTab>(() => (auth.canWrite('high-value-consumables') && isOperationsTab.value) ? 'operations' : 'detail')

function switchTab(tab: ChargeTab) {
  if (tab === activeTab.value) return
  router.replace({ path: route.path, query: tab === 'operations' ? { tab: 'operations' } : {} })
}

/** 默认只展示常用条件，UDI/供应商/厂家/注册证折叠 */
const showAllFilters = ref(false)
const errorMessage = ref('')

const { filters, rows, loading, currentPage, pageSize, totalItems,
  loadData, search, reset, changePage, changePageSize, notify, confirm } = useChargeDetailQuery()

/** 统一列表的表格容器（用于横向滚动按钮定位到 el-table 内部滚动区） */
const tableScrollRef = ref<HTMLElement | null>(null)
const tableScrollState = ref({ left: 0, max: 0 })
let resizeObserver: ResizeObserver | undefined

/**
 * 取得 el-table 内部真正负责横向滚动的容器。
 * 统一列表改造后表格由 Element Plus 自己滚动（.el-scrollbar__wrap），
 * 外层 .ulc-table-wrap 不再滚动。
 */
function getChargeTableScrollEl(): HTMLElement | null {
  const root = tableScrollRef.value
  if (!root) return null
  const wrap = root.querySelector<HTMLElement>('.el-scrollbar__wrap')
  return wrap || root
}

function updateTableScrollState() {
  const el = getChargeTableScrollEl()
  if (!el) {
    tableScrollState.value = { left: 0, max: 0 }
    return
  }
  tableScrollState.value = {
    left: el.scrollLeft,
    max: Math.max(0, el.scrollWidth - el.clientWidth)
  }
}

function setChargeTableScrollLeft(left: number) {
  const el = getChargeTableScrollEl()
  if (!el) return
  const max = Math.max(0, el.scrollWidth - el.clientWidth)
  el.scrollLeft = Math.min(max, Math.max(0, left))
  updateTableScrollState()
}

function scrollChargeTable(direction: 'left' | 'right') {
  const el = getChargeTableScrollEl()
  if (!el) return
  updateTableScrollState()
  const distance = Math.max(360, Math.round(el.clientWidth * 0.72))
  setChargeTableScrollLeft(el.scrollLeft + (direction === 'left' ? -distance : distance))
}

function money(value: number | null, digits = 2) {
  return value == null ? '-' : value.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

const summary = computed(() => {
  const list = rows.value
  const total = totalItems.value
  const totalAmount = list.reduce((sum, row) => sum + (row.chargeAmount || 0), 0)
  const totalQty = list.reduce((sum, row) => sum + (row.chargeQuantity || 0), 0)
  const udiCount = list.filter((row) => !!row.udiCode).length
  const patientSet = new Set<string>()
  for (const row of list) {
    if (row.patientNo) patientSet.add(row.patientNo)
  }
  const avgPrice = totalQty > 0 ? totalAmount / totalQty : 0
  return [
    { label: '当前明细', value: total, tone: 'blue', icon: Layers },
    { label: '收费总金额', value: '¥' + money(totalAmount), tone: 'green', icon: Wallet, raw: totalAmount },
    { label: '收费总数量', value: String(totalQty), tone: 'amber', icon: Pill, raw: totalQty },
    { label: '已绑 UDI', value: `${udiCount} 条`, tone: 'cyan', icon: ShieldCheck, raw: udiCount },
    { label: '患者数', value: `${patientSet.size} 人`, tone: 'purple', icon: User, raw: patientSet.size },
    { label: '平均单价', value: '¥' + money(avgPrice, 4), tone: 'red', icon: BarChart3, raw: avgPrice }
  ]
})

function exportRows() {
  if (!rows.value.length) {
    return
  }
  const header = ['计费单号', '外部单号', '患者号', '患者姓名', '科室', '商品编码', '商品名称', '规格', '单位', '供应商', '厂家', '注册证', '注册证到期', '唯一码', 'UDI', '收费数量', '单价', '金额', '计费时间']
  const escape = (val: unknown) => {
    const s = val === null || val === undefined ? '' : String(val)
    if (/[",\n]/.test(s)) return '"' + s.replace(/"/g, '""') + '"'
    return s
  }
  const lines = rows.value.map((row) => [
    row.bizNo,
    row.externalChargeNo,
    row.patientNo,
    row.patientName,
    row.deptName,
    row.productCode,
    row.productName,
    row.specModel,
    row.unit,
    row.supplierName,
    row.manufacturerName,
    row.registrationNo,
    row.registrationExpireDate,
    row.uniqueCode,
    row.udiCode,
    row.chargeQuantity,
    row.unitPrice,
    row.chargeAmount,
    row.chargeTime || row.createTime
  ].map(escape).join(','))
  const csv = '\uFEFF' + header.join(',') + '\n' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const stamp = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19)
  a.href = url
  a.download = `收费耗材明细-${stamp}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function printTable() {
  window.print()
}

function timeTone(value?: string | null) {
  if (!value) return 'gray'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'gray'
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const oneDay = 24 * 60 * 60 * 1000
  if (diff <= oneDay) return 'green'
  if (diff <= 7 * oneDay) return 'blue'
  if (diff <= 30 * oneDay) return 'amber'
  return 'gray'
}

function timeText(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const oneDay = 24 * 60 * 60 * 1000
  if (diff <= oneDay) return '今日'
  if (diff <= 7 * oneDay) return '7 天内'
  if (diff <= 30 * oneDay) return '30 天内'
  return '更早'
}

const expandedRow = ref<string | null>(null)
function toggleRow(row: HighValueChargeDetail) {
  const key = String(row.bizNo)
  expandedRow.value = expandedRow.value === key ? null : key
}

watch(rows, async () => { await nextTick(); updateTableScrollState() })
onMounted(async () => {
  await nextTick()
  resizeObserver = new ResizeObserver(updateTableScrollState)
  const root = tableScrollRef.value
  if (root) {
    resizeObserver.observe(root)
    const wrap = root.querySelector<HTMLElement>('.el-scrollbar__wrap')
    if (wrap) {
      resizeObserver.observe(wrap)
      wrap.addEventListener('scroll', updateTableScrollState, { passive: true })
    }
    const table = root.querySelector('table')
    if (table) resizeObserver.observe(table)
  }
  updateTableScrollState()
})
onBeforeUnmount(() => resizeObserver?.disconnect())

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const HVCHARGEDETAIL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'chargeNo', label: '计费单号', width: 180, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'patient', label: '患者信息', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 8, minWidth: 140, maxWidth: 340 },
  { key: 'product', label: '商品信息', width: 220, visible: true, flexible: true, idealWidth: 260, weight: 9, minWidth: 150, maxWidth: 380 },
  { key: 'col3', label: '供应商 / 厂家', width: 170, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'registrationNo', label: '注册证', width: 170, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'col5', label: 'UDI / 唯一码', width: 180, visible: true, flexible: true, idealWidth: 220, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'quantity', label: '数量', width: 110, visible: true, weight: 3, minWidth: 95, maxWidth: 170 },
  { key: 'unitPrice', label: '单价', width: 110, visible: true, weight: 3, minWidth: 95, maxWidth: 170 },
  { key: 'amount', label: '金额', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'chargeTime', label: '计费时间', width: 170, visible: true, weight: 4, minWidth: 140, maxWidth: 240, fill: true }
]

const hvChargeDetailCols = useListColumns({
  storageKey: 'spd-hv-charge-detail-cols-v1',
  defaults: HVCHARGEDETAIL_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「患者信息 / 商品信息 / 供应商厂家」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const hvChargeDetailTableRef = hvChargeDetailCols.tableRef
const isHvChargeDetailColVisible = (key: string) => hvChargeDetailCols.isColumnVisible(key)
const hvChargeDetailColBinding = (key: string) => hvChargeDetailCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const hvChargeDetailColSettingsAnchorRef = ref<HTMLElement | null>(null)
const hvChargeDetailColSettingsPopRef = ref<HTMLElement | null>(null)
const hvChargeDetailColSettingsPop = useColSettingsPopover({
  open: hvChargeDetailCols.settingsOpen,
  anchorRef: hvChargeDetailColSettingsAnchorRef,
  popRef: hvChargeDetailColSettingsPopRef
})

</script>

<template><section class="charge-detail-page spd-unified-list" aria-label="收费耗材明细查询">
    <MdPageHeader
      crumb="供应链业务"
      title="收费耗材明细查询"
      :loading="loading"
      @refresh="loadData"
    />

    <div v-if="auth.canWrite('high-value-consumables')" class="charge-tab-strip" role="tablist" aria-label="高值耗材计费模块">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'detail'"
        :class="['charge-tab', { 'is-active': activeTab === 'detail' }]"
        @click="switchTab('detail')"
      >
        <Search :size="16" />
        计费明细查询
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'operations'"
        :class="['charge-tab', { 'is-active': activeTab === 'operations' }]"
        @click="switchTab('operations')"
      >
        <ClipboardCheck :size="16" />
        高值耗材计费操作
      </button>
    </div>

    <HighValueOperationsPanel v-if="activeTab === 'operations'" />

    <template v-else>
      <!-- ============ 工作台：常用条件 + 更多条件 + 操作 ============ -->
      <form class="charge-panel charge-filters" :class="{ 'is-expanded': showAllFilters }" @submit.prevent="search">
        <div class="charge-filter-head">
          <span class="charge-filter-icon"><Filter :size="16" /></span>
          <div>
            <strong>查询条件</strong>
            <small>支持按患者号、姓名、商品、日期、唯一码、UDI、供应商、厂家、注册证等组合筛选</small>
          </div>
        </div>
        <SpdFilterBar
          class="charge-query-grid"
          :submit-label="'查询'"
          @submit="search"
          @reset="reset"
        >
          <label class="charge-filter-primary">
            <span><User :size="12" /> 患者号 / 住院号</span>
            <input v-model.trim="filters.patientNo" placeholder="患者号、住院号" />
          </label>
          <label class="charge-filter-primary">
            <span><Stethoscope :size="12" /> 患者姓名</span>
            <input v-model.trim="filters.patientName" placeholder="支持脱敏姓名" />
          </label>
          <label class="charge-filter-primary">
            <span><Hash :size="12" /> 商品编码</span>
            <input v-model.trim="filters.productCode" placeholder="医院目录编码" />
          </label>
          <label class="charge-filter-primary">
            <span><Pill :size="12" /> 商品名称</span>
            <input v-model.trim="filters.productName" placeholder="耗材名称" />
          </label>
          <label class="charge-filter-primary">
            <span><Calendar :size="12" /> 开始日期</span>
            <input v-model="filters.dateFrom" type="date" />
          </label>
          <label class="charge-filter-primary">
            <span><Calendar :size="12" /> 结束日期</span>
            <input v-model="filters.dateTo" type="date" />
          </label>
          <label class="charge-filter-primary">
            <span><Tag :size="12" /> 唯一码</span>
            <input v-model.trim="filters.uid" placeholder="唯一码" />
          </label>
          <label class="charge-filter-extra">
            <span><ShieldCheck :size="12" /> UDI / DI</span>
            <input v-model.trim="filters.udi" placeholder="UDI-DI / UDI" />
          </label>
          <label class="charge-filter-extra">
            <span><Truck :size="12" /> 供应商</span>
            <input v-model.trim="filters.supplierName" placeholder="供应商名称" />
          </label>
          <label class="charge-filter-extra">
            <span><Building2 :size="12" /> 生产厂家</span>
            <input v-model.trim="filters.manufacturerName" placeholder="生产厂家" />
          </label>
          <label class="charge-filter-extra">
            <span><FileSpreadsheet :size="12" /> 注册证号</span>
            <input v-model.trim="filters.registrationNo" placeholder="注册证号" />
          </label>
          <template #actions>
            <SpdButton label="重置" tone="neutral" class="charge-action-reset" @click="reset">
              <template #icon><CircleX :size="15" /></template>
            </SpdButton>
            <SpdButton label="查询" tone="primary" class="charge-action-search">
              <template #icon><Search :size="15" /></template>
            </SpdButton>
            <SpdButton tone="neutral" class="charge-action-toggle" :aria-expanded="showAllFilters" @click="showAllFilters = !showAllFilters">
              <template #icon><Filter :size="14" /></template>
              {{ showAllFilters ? '收起条件' : '更多条件' }}
            </SpdButton>
          </template>
        </SpdFilterBar>
      </form>

      <!-- ============ 汇总条 ============ -->
      <section class="charge-summary" aria-label="查询结果汇总">
        <div
          v-for="item in summary"
          :key="item.label"
          class="charge-summary-item"
          :class="`tone-${item.tone}`"
        >
          <span class="charge-summary-icon"><component :is="item.icon" :size="14" /></span>
          <span class="charge-summary-label">{{ item.label }}</span>
          <strong class="charge-summary-value">{{ item.value }}</strong>
        </div>
      </section>

      <!-- ============ 收费明细表 ============ -->
      <section class="charge-panel charge-table-panel" :aria-busy="loading">
        <div class="charge-section-title">
          <div class="charge-section-title-left">
            <span class="charge-section-icon"><ClipboardCheck :size="16" /></span>
            <div>
              <h3>收费明细</h3>
              <p>共 {{ totalItems }} 条 · 当前第 {{ currentPage }} / {{ Math.max(1, Math.ceil(totalItems / pageSize)) }} 页</p>
            </div>
          </div>
          <div class="charge-section-title-right">
            <SpdButton label="刷新" tone="neutral" :disabled="loading" @click="loadData">
              <template #icon><RefreshCw :size="14" /></template>
            </SpdButton>
            <SpdButton label="导出 CSV" tone="primary" :disabled="!rows.length" @click="exportRows">
              <template #icon><Download :size="14" /></template>
            </SpdButton>
            <SpdButton label="打印" tone="neutral" :disabled="!rows.length" @click="printTable">
              <template #icon><Printer :size="14" /></template>
            </SpdButton>
            <div class="charge-scroll-actions" aria-label="收费明细横向滚动控制">
              <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="向左滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('left')">
                <template #icon><ChevronLeft :size="16" /></template>
              </SpdButton>
              <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="向右滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('right')">
                <template #icon><ChevronRight :size="16" /></template>
              </SpdButton>
            </div>
          </div>
        </div>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="hvChargeDetailColSettingsAnchorRef"
             
             
              @click="hvChargeDetailCols.settingsOpen.value = !hvChargeDetailCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <div
              v-if="hvChargeDetailCols.settingsOpen.value"
              ref="hvChargeDetailColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': hvChargeDetailColSettingsPop.align.value === 'right', 'is-align-up': hvChargeDetailColSettingsPop.vertical.value === 'up' }"
              :style="hvChargeDetailColSettingsPop.popStyle.value"
            >
              <header>
                <strong>列展示设置</strong>
                <SpdButton label="恢复默认" variant="text" tone="neutral" @click="hvChargeDetailCols.resetColumns()"></SpdButton>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in hvChargeDetailCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && hvChargeDetailCols.visibleColumnCount.value <= 1"
                    @change="hvChargeDetailCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <SpdButton label="确定" tone="primary" @click="hvChargeDetailCols.settingsOpen.value = false"></SpdButton>
              </footer>
            </div>
          </div>
          </template>
          <div ref="tableScrollRef" class="ulc-table-wrap">
            <el-table
              ref="hvChargeDetailTableRef"
              :data="rows"
              row-key="bizNo"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'暂无计费明细'"
              @header-dragend="(width: number, oldWidth: number, column: any) => hvChargeDetailCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="chargeNo"
                v-if="isHvChargeDetailColVisible('chargeNo')"
                label="计费单号"
                v-bind="hvChargeDetailColBinding('chargeNo')"
              >
                <template #header>
                  <span class="ulc-th-text">计费单号</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-strong ulc-wrap-text">{{ row.bizNo || '-' }}</span>
                    <span class="ulc-sub ulc-wrap-text is-sub" v-if="row.externalChargeNo">外单 {{ row.externalChargeNo }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="patient"
                v-if="isHvChargeDetailColVisible('patient')"
                label="患者信息"
                v-bind="hvChargeDetailColBinding('patient')"
              >
                <template #header>
                  <span class="ulc-th-text">患者信息</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-strong ulc-wrap-text">{{ row.patientName || '-' }}</span>
                    <span class="ulc-sub ulc-wrap-text is-sub">
                      <User :size="11" />
                      {{ row.patientNo || '-' }}
                    </span>
                    <span class="ulc-sub ulc-wrap-text is-sub">
                      <Building2 :size="11" />
                      {{ row.deptName || '-' }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="product"
                v-if="isHvChargeDetailColVisible('product')"
                label="商品信息"
                v-bind="hvChargeDetailColBinding('product')"
              >
                <template #header>
                  <span class="ulc-th-text">商品信息</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-strong ulc-wrap-text">{{ row.productName || '-' }}</span>
                    <span class="ulc-mono ulc-wrap-text">
                      <Hash :size="11" />
                      {{ row.productCode || '-' }}
                    </span>
                    <span class="ulc-sub ulc-wrap-text is-sub">
                      {{ row.specModel || '-' }} · {{ row.unit || '-' }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="col3"
                v-if="isHvChargeDetailColVisible('col3')"
                label="供应商 / 厂家"
                v-bind="hvChargeDetailColBinding('col3')"
              >
                <template #header>
                  <span class="ulc-th-text">供应商 / 厂家</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-strong ulc-wrap-text">{{ row.supplierName || '-' }}</span>
                    <span class="ulc-sub ulc-wrap-text is-sub">{{ row.manufacturerName || '-' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="registrationNo"
                v-if="isHvChargeDetailColVisible('registrationNo')"
                label="注册证"
                v-bind="hvChargeDetailColBinding('registrationNo')"
              >
                <template #header>
                  <span class="ulc-th-text">注册证</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-strong ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
                    <span class="ulc-sub ulc-wrap-text is-sub" v-if="row.registrationExpireDate">
                      到期 {{ row.registrationExpireDate }}
                    </span>
                    <span v-else class="ulc-sub ulc-wrap-text is-sub">—</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="col5"
                v-if="isHvChargeDetailColVisible('col5')"
                label="UDI / 唯一码"
                v-bind="hvChargeDetailColBinding('col5')"
              >
                <template #header>
                  <span class="ulc-th-text">UDI / 唯一码</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span class="ulc-mono ulc-wrap-text" data-testid="charge-unique-code">
                      <Tag :size="11" />
                      {{ row.uniqueCode || '-' }}
                    </span>
                    <span class="ulc-mono ulc-wrap-text">
                      <ShieldCheck :size="11" />
                      {{ row.udiCode || '-' }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="quantity"
                v-if="isHvChargeDetailColVisible('quantity')"
                label="数量"
                v-bind="hvChargeDetailColBinding('quantity')"
                align="right"
              >
                <template #header>
                  <span class="ulc-th-text">数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number ulc-wrap-text"><strong>{{ row.chargeQuantity ?? '-' }}</strong></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isHvChargeDetailColVisible('unitPrice')"
                label="单价"
                v-bind="hvChargeDetailColBinding('unitPrice')"
                align="right"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number ulc-wrap-text"><span>{{ money(row.unitPrice, 4) }}</span></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="amount"
                v-if="isHvChargeDetailColVisible('amount')"
                label="金额"
                v-bind="hvChargeDetailColBinding('amount')"
                align="right"
              >
                <template #header>
                  <span class="ulc-th-text">金额</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number ulc-wrap-text"><strong>¥{{ money(row.chargeAmount) }}</strong></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="chargeTime"
                v-if="isHvChargeDetailColVisible('chargeTime')"
                label="计费时间"
                v-bind="hvChargeDetailColBinding('chargeTime')"
              >
                <template #header>
                  <span class="ulc-th-text">计费时间</span>
                </template>
                <template #default="{ row }">
                  <span class="time-pill" :class="`tone-${timeTone(row.chargeTime || row.createTime)}`">
                        {{ timeText(row.chargeTime || row.createTime) }}
                      </span>
                      <span class="muted-cell charge-time">
                        <Calendar :size="11" />
                        {{ row.chargeTime || row.createTime || '-' }}
                      </span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无计费明细" />
              </template>
            </el-table>
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
      </section>
    </template>
  </section>
</template>

<style scoped>
/* =====================================================================
   收费耗材明细查询（重制版）
   - 三段式工作台：查询条件卡 / 汇总条 / 明细表
   - 条件折叠：常用 7 项 + 更多 4 项
   - 表格行可点击展开单条详情
   - 金额/数量/UDI 字段带语义色调
   - 保留横向滚动条
   ===================================================================== */
.charge-detail-page {
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

.charge-detail-page h3,
.charge-detail-page p {
  margin: 0;
}

/* ---------- Tab 切换 ---------- */
.charge-tab-strip {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin: -2px 0 0;
  padding: 0 4px;
  border-bottom: 1px solid var(--spd-border-subtle);
  background: transparent;
}

.charge-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 6px 6px 0 0;
  background: transparent;
  color: var(--spd-text-secondary, var(--spd-p-info));
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: color 0.12s ease, border-color 0.12s ease, background 0.12s ease;
}

.charge-tab:hover {
  color: var(--spd-text-heading, var(--spd-p-text));
  background: color-mix(in srgb, var(--spd-p-primary-strong) 5%, transparent);
}

.charge-tab.is-active {
  color: var(--spd-p-info);
  border-bottom-color: var(--spd-p-info);
  background: color-mix(in srgb, var(--spd-p-info) 8%, transparent);
}

.charge-tab:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--spd-p-info) 35%, transparent);
  outline-offset: 2px;
}

/* ---------- 面板通用 ---------- */
.charge-panel {
  box-sizing: border-box;
  min-width: 0;
  padding: 14px 16px 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

/* ---------- 查询条件卡 ---------- */
.charge-filters {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-left: 3px solid var(--spd-p-info);
  background: linear-gradient(180deg, var(--spd-p-bg) 0%, var(--spd-p-bg-subtle) 100%);
}

.charge-filter-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--spd-p-border);
}

.charge-filter-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: var(--spd-p-info);
  background: var(--spd-p-border);
  border-radius: 8px;
}

.charge-filter-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.charge-filter-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

/* 本页筛选区已迁移为统一筛选栏组件 <SpdFilterBar>：
   栅格列数 / 换行规则 / 控件高度 / 标签排版 / 操作区位置
   全部由 styles/components/spd-filter-bar.css 的「标准规则」统一控制，
   本页不再覆写，只保留本页特有的图标标签与「更多条件」显隐逻辑。

   外壳底色 / 边框 / 圆角一律沿用 <SpdFilterBar> 的统一规格
   （白底 + --spd-p-border 边框 + 12px 圆角），与「科室库房目录」完全一致，
   不再由本页自行去掉边框 / 底色，避免筛选区看起来是「透明的一层」。 */
.charge-query-grid {
  padding: 16px 18px;
  border: 1px solid var(--spd-p-border);
  border-radius: 12px;
  background: var(--spd-p-bg);
}

.charge-query-grid label {
  display: grid;
  gap: 5px;
  min-width: 0;
  margin: 0;
}

.charge-query-grid label > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.charge-query-grid label > span :deep(svg) {
  color: var(--spd-p-text-muted);
}

.charge-filters .charge-filter-extra {
  display: none;
}

.charge-filters.is-expanded .charge-filter-extra {
  display: grid;
}

/* 收费明细查询区按钮：完全服从 SpdButton 统一几何与配色，
   不再覆盖高度 / 内边距 / 字号 / 颜色（此前使用了调色板之外的蓝色渐变，
   与全站青玉主色不一致）。这里只保留图标间距与靠右布局。 */
.charge-action-search,
.charge-action-reset,
.charge-action-toggle {
  gap: 4px;
}

/* ---------- 汇总 chips ---------- */
.charge-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.charge-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 150px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 999px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-sm);
  transition: transform 0.1s ease;
}

.charge-summary-item:hover {
  transform: translateY(-1px);
}

.charge-summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.charge-summary-label {
  color: var(--spd-text-secondary);
}

.charge-summary-value {
  margin-left: auto;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.charge-summary-item.tone-blue {
  border-color: var(--spd-p-info-border);
  background: var(--spd-p-info-bg);
}
.charge-summary-item.tone-blue .charge-summary-icon {
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
}
.charge-summary-item.tone-blue .charge-summary-value {
  color: var(--spd-p-info-strong);
}

.charge-summary-item.tone-green {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-primary-bg);
}
.charge-summary-item.tone-green .charge-summary-icon {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
}
.charge-summary-item.tone-green .charge-summary-value {
  color: var(--spd-p-primary-strong);
}

.charge-summary-item.tone-amber {
  border-color: var(--spd-p-warning-border);
  background: var(--spd-p-warning-bg);
}
.charge-summary-item.tone-amber .charge-summary-icon {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
}
.charge-summary-item.tone-amber .charge-summary-value {
  color: var(--spd-p-warning-text);
}

.charge-summary-item.tone-cyan {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-info-bg);
}
.charge-summary-item.tone-cyan .charge-summary-icon {
  color: var(--spd-p-success-text);
  background: var(--spd-p-info-bg);
}
.charge-summary-item.tone-cyan .charge-summary-value {
  color: var(--spd-p-info);
}

.charge-summary-item.tone-purple {
  border-color: var(--spd-p-info-border);
  background: var(--spd-p-bg-subtle);
}
.charge-summary-item.tone-purple .charge-summary-icon {
  color: var(--spd-p-primary);
  background: var(--spd-p-info-bg);
}
.charge-summary-item.tone-purple .charge-summary-value {
  color: var(--spd-p-primary-strong);
}

.charge-summary-item.tone-red {
  border-color: var(--spd-p-danger-border);
  background: var(--spd-p-danger-bg);
}
.charge-summary-item.tone-red .charge-summary-icon {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
}
.charge-summary-item.tone-red .charge-summary-value {
  color: var(--spd-p-danger-strong);
}

/* ---------- 明细表面板 ---------- */
.charge-table-panel {
  padding: 14px 16px 16px;
}

.charge-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.charge-section-title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.charge-section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--spd-p-info);
  background: var(--spd-p-border);
  border-radius: 6px;
}

.charge-section-title h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.charge-section-title p {
  margin: 2px 0 0;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.charge-section-title-right {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.15s ease;
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

.charge-scroll-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-left: 6px;
  margin-left: 2px;
  border-left: 1px solid var(--spd-p-border);
}

.charge-scroll-actions .btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.charge-scroll-actions .btn-icon:hover:not(:disabled) {
  border-color: var(--spd-p-info);
  color: var(--spd-p-info);
  background: var(--spd-p-info-bg);
}

.charge-scroll-actions .btn-icon:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ---------- 表格 ---------- */
.charge-detail-scroll {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--spd-p-border-strong) transparent;
}

.charge-detail-scroll::-webkit-scrollbar {
  height: 8px;
}

.charge-detail-scroll::-webkit-scrollbar-thumb {
  background: var(--spd-p-border-strong);
  border-radius: 999px;
}

.charge-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--spd-p-text-muted);
}

.charge-detail-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.charge-detail-table {
  width: 100%;
  min-width: 1780px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.charge-detail-table thead th {
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
  vertical-align: middle;
}

.charge-detail-table tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid var(--spd-p-text-strong) !important;
  color: var(--spd-p-text-secondary) !important;
  font-size: var(--spd-font-size-base) !important;
  background: var(--spd-p-bg);
  vertical-align: top;
}

.charge-data-row {
  cursor: pointer;
  transition: background 0.12s ease;
}

.charge-data-row td {
  background: var(--spd-p-bg);
}

.charge-data-row:nth-child(even) td {
  background: var(--spd-p-row-stripe);
}

.charge-data-row:hover td {
  background: var(--spd-p-info-bg) !important;
}

.charge-data-row.selected td {
  background: var(--spd-p-info-bg) !important;
  box-shadow: var(--spd-p-shadow-1);
}

.charge-data-row.selected:hover td {
  background: var(--spd-p-info-bg) !important;
}

.charge-cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.charge-cell-stack strong {
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.charge-biz-no {
  font-family: var(--spd-font-family-mono);
  color: var(--spd-p-info-strong) !important;
  font-size: var(--spd-font-size-base) !important;
  font-weight: 600 !important;
}

.muted-cell {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 1px;
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
}

.muted-cell :deep(svg) {
  color: var(--spd-p-text-muted);
  flex-shrink: 0;
}

.charge-mono {
  font-family: var(--spd-font-family-mono) !important;
  color: var(--spd-p-text-secondary) !important;
  font-size: var(--spd-font-size-base) !important;
}

.numeric-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.numeric-cell strong {
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.charge-money {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base) !important;
}

.charge-amount strong {
  color: var(--spd-p-danger-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
}

.charge-time {
  margin-top: 4px;
}

.time-pill {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}

.time-pill.tone-green {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success-border);
}

.time-pill.tone-blue {
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
  border: 1px solid var(--spd-p-info-border);
}

.time-pill.tone-amber {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border: 1px solid var(--spd-p-warning-border);
}

.time-pill.tone-gray {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
}

/* ---------- 状态行 ---------- */
.charge-state-row td {
  padding: 0 !important;
  border-bottom: 0 !important;
}

.charge-state-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  height: 200px;
  padding: 32px 16px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  background: var(--spd-p-row-stripe);
}

.charge-state-cell.is-loading {
  color: var(--spd-p-info-strong);
}

.charge-state-cell.is-error {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
}

.charge-state-cell.is-empty {
  color: var(--spd-p-text-muted);
}

.charge-state-cell .spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- 行详情展开 ---------- */
.charge-detail-row td {
  padding: 0 !important;
  border-bottom: 1px solid var(--spd-p-info-border) !important;
  background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%) !important;
}

.charge-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px 18px;
  padding: 14px 18px;
}

.charge-detail-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.charge-detail-label {
  flex: 0 0 auto;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.charge-detail-value {
  min-width: 0;
  color: var(--spd-text-primary);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.charge-amount-strong {
  color: var(--spd-p-danger-strong) !important;
  font-size: var(--spd-font-size-base) !important;
  font-weight: 700 !important;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .charge-detail-table {
    min-width: 1600px;
  }
}

@media (max-width: 900px) {
  .charge-detail-page {
    padding: 12px 12px 20px;
  }
  .charge-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
  .charge-section-title-right {
    width: 100%;
  }
}
</style>
