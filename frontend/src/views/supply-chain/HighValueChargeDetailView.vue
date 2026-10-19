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
import PaginationControls from '../../components/common/PaginationControls.vue'
import HighValueOperationsPanel from '../../components/supply-chain/HighValueOperationsPanel.vue'
import { useChargeDetailQuery } from '../../composables/useChargeDetailQuery'
import { useAuthStore } from '../../stores/auth'
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

const tableScrollRef = ref<HTMLElement | null>(null)
const tableScrollState = ref({ left: 0, max: 0 })
let resizeObserver: ResizeObserver | undefined

function updateTableScrollState() {
  const el = tableScrollRef.value
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
  const el = tableScrollRef.value
  if (!el) return
  const max = Math.max(0, el.scrollWidth - el.clientWidth)
  el.scrollLeft = Math.min(max, Math.max(0, left))
  updateTableScrollState()
}

function scrollChargeTable(direction: 'left' | 'right') {
  const el = tableScrollRef.value
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
onMounted(() => {
  resizeObserver = new ResizeObserver(updateTableScrollState)
  if (tableScrollRef.value) {
    resizeObserver.observe(tableScrollRef.value)
    const table = tableScrollRef.value.querySelector('table')
    if (table) resizeObserver.observe(table)
  }
  updateTableScrollState()
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <section class="charge-detail-page" aria-label="收费耗材明细查询">
    <MdPageHeader
      crumb="供应链业务"
      title="收费耗材明细查询"
      description="按患者、商品、日期、唯一码、厂家供应商等条件查询高值耗材计费明细"
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
        <div class="charge-query-grid">
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
        </div>
        <div class="charge-filter-actions">
          <button class="btn btn-primary charge-action-search" type="submit">
            <Search :size="15" />
            查询
          </button>
          <button class="btn charge-action-reset" type="button" @click="reset">
            <CircleX :size="15" />
            重置
          </button>
          <button class="btn charge-action-toggle" type="button" :aria-expanded="showAllFilters" @click="showAllFilters = !showAllFilters">
            <Filter :size="14" />
            {{ showAllFilters ? '收起条件' : '更多条件' }}
          </button>
        </div>
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
            <button class="tool-btn tool-refresh" type="button" :disabled="loading" @click="loadData">
              <RefreshCw :size="14" />
              刷新
            </button>
            <button class="tool-btn tool-export" type="button" :disabled="!rows.length" @click="exportRows">
              <Download :size="14" />
              导出 CSV
            </button>
            <button class="tool-btn tool-print" type="button" :disabled="!rows.length" @click="printTable">
              <Printer :size="14" />
              打印
            </button>
            <div class="charge-scroll-actions" aria-label="收费明细横向滚动控制">
              <button class="btn-icon" type="button" aria-label="向左滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('left')">
                <ChevronLeft :size="16" />
              </button>
              <button class="btn-icon" type="button" aria-label="向右滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('right')">
                <ChevronRight :size="16" />
              </button>
            </div>
          </div>
        </div>

        <div id="charge-detail-table-scroll" ref="tableScrollRef" class="charge-detail-scroll" @scroll="updateTableScrollState">
          <table class="charge-detail-table">
            <thead>
              <tr>
                <th>计费单号</th>
                <th>患者信息</th>
                <th>商品信息</th>
                <th>供应商 / 厂家</th>
                <th>注册证</th>
                <th>UDI / 唯一码</th>
                <th>数量</th>
                <th>单价</th>
                <th>金额</th>
                <th>计费时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading" class="charge-state-row">
                <td colspan="10">
                  <div class="charge-state-cell is-loading">
                    <RefreshCw :size="20" class="spin" />
                    <span>正在加载收费耗材明细...</span>
                  </div>
                </td>
              </tr>
              <tr v-else-if="errorMessage" class="charge-state-row">
                <td colspan="10">
                  <div class="charge-state-cell is-error">
                    <AlertCircle :size="20" />
                    <span>{{ errorMessage }}</span>
                  </div>
                </td>
              </tr>
              <tr v-else-if="!rows.length" class="charge-state-row">
                <td colspan="10">
                  <div class="charge-state-cell is-empty">
                    <Info :size="20" />
                    <span>暂无收费耗材明细，请调整查询条件后重试</span>
                  </div>
                </td>
              </tr>
              <template v-else>
                <template v-for="row in rows" :key="String(row.bizNo)">
                  <tr
                    :class="['charge-data-row', { selected: expandedRow === String(row.bizNo) }]"
                    @click="toggleRow(row)"
                  >
                    <td>
                      <strong class="charge-biz-no">{{ row.bizNo }}</strong>
                      <span class="muted-cell" v-if="row.externalChargeNo">外单 {{ row.externalChargeNo }}</span>
                    </td>
                    <td>
                      <div class="charge-cell-stack">
                        <strong>{{ row.patientName || '-' }}</strong>
                        <span class="muted-cell">
                          <User :size="11" />
                          {{ row.patientNo || '-' }}
                        </span>
                        <span class="muted-cell">
                          <Building2 :size="11" />
                          {{ row.deptName || '-' }}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div class="charge-cell-stack">
                        <strong>{{ row.productName || '-' }}</strong>
                        <span class="muted-cell">
                          <Hash :size="11" />
                          {{ row.productCode || '-' }}
                        </span>
                        <span class="muted-cell">
                          {{ row.specModel || '-' }} · {{ row.unit || '-' }}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div class="charge-cell-stack">
                        <strong>{{ row.supplierName || '-' }}</strong>
                        <span class="muted-cell">{{ row.manufacturerName || '-' }}</span>
                      </div>
                    </td>
                    <td>
                      <div class="charge-cell-stack">
                        <strong>{{ row.registrationNo || '-' }}</strong>
                        <span class="muted-cell" v-if="row.registrationExpireDate">
                          到期 {{ row.registrationExpireDate }}
                        </span>
                        <span v-else class="muted-cell">—</span>
                      </div>
                    </td>
                    <td>
                      <div class="charge-cell-stack">
                        <span class="charge-mono">
                          <Tag :size="11" />
                          {{ row.uniqueCode || '-' }}
                        </span>
                        <span class="charge-mono">
                          <ShieldCheck :size="11" />
                          {{ row.udiCode || '-' }}
                        </span>
                      </div>
                    </td>
                    <td class="numeric-cell">
                      <strong>{{ row.chargeQuantity ?? '-' }}</strong>
                    </td>
                    <td class="numeric-cell charge-money">
                      <span>{{ money(row.unitPrice, 4) }}</span>
                    </td>
                    <td class="numeric-cell charge-amount">
                      <strong>¥{{ money(row.chargeAmount) }}</strong>
                    </td>
                    <td>
                      <span class="time-pill" :class="`tone-${timeTone(row.chargeTime || row.createTime)}`">
                        {{ timeText(row.chargeTime || row.createTime) }}
                      </span>
                      <span class="muted-cell charge-time">
                        <Calendar :size="11" />
                        {{ row.chargeTime || row.createTime || '-' }}
                      </span>
                    </td>
                  </tr>
                  <tr v-if="expandedRow === String(row.bizNo)" class="charge-detail-row">
                    <td colspan="10">
                      <div class="charge-detail-grid">
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">计费单号</span>
                          <span class="charge-detail-value charge-mono">{{ row.bizNo }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">外部单号</span>
                          <span class="charge-detail-value">{{ row.externalChargeNo || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">患者号</span>
                          <span class="charge-detail-value">{{ row.patientNo || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">患者姓名</span>
                          <span class="charge-detail-value">{{ row.patientName || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">科室</span>
                          <span class="charge-detail-value">{{ row.deptName || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">商品编码</span>
                          <span class="charge-detail-value charge-mono">{{ row.productCode || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">商品名称</span>
                          <span class="charge-detail-value">{{ row.productName || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">规格 / 单位</span>
                          <span class="charge-detail-value">{{ row.specModel || '—' }} / {{ row.unit || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">供应商</span>
                          <span class="charge-detail-value">{{ row.supplierName || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">生产厂家</span>
                          <span class="charge-detail-value">{{ row.manufacturerName || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">注册证号</span>
                          <span class="charge-detail-value charge-mono">{{ row.registrationNo || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">注册证到期</span>
                          <span class="charge-detail-value">{{ row.registrationExpireDate || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">唯一码</span>
                          <span class="charge-detail-value charge-mono">{{ row.uniqueCode || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">UDI</span>
                          <span class="charge-detail-value charge-mono">{{ row.udiCode || '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">数量</span>
                          <span class="charge-detail-value">{{ row.chargeQuantity ?? '—' }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">单价</span>
                          <span class="charge-detail-value">¥{{ money(row.unitPrice, 4) }}</span>
                        </div>
                        <div class="charge-detail-item charge-amount-cell">
                          <span class="charge-detail-label">金额</span>
                          <span class="charge-detail-value charge-amount-strong">¥{{ money(row.chargeAmount) }}</span>
                        </div>
                        <div class="charge-detail-item">
                          <span class="charge-detail-label">计费时间</span>
                          <span class="charge-detail-value">{{ row.chargeTime || row.createTime || '—' }}</span>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </template>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="currentPage"
          :size="pageSize"
          :total="totalItems"
          :loading="loading"
          @change-page="changePage"
          @change-size="changePageSize"
        />
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
  color: var(--spd-text-secondary, #607285);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.12s ease, border-color 0.12s ease, background 0.12s ease;
}

.charge-tab:hover {
  color: var(--spd-text-heading, #14213a);
  background: rgba(15, 83, 79, 0.05);
}

.charge-tab.is-active {
  color: #1677ff;
  border-bottom-color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
}

.charge-tab:focus-visible {
  outline: 2px solid rgba(22, 119, 255, 0.35);
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
  border-left: 3px solid #1677ff;
  background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
}

.charge-filter-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e0e7ff;
}

.charge-filter-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #1677ff;
  background: #dbeafe;
  border-radius: 8px;
}

.charge-filter-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 600;
}

.charge-filter-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.charge-query-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px 14px;
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
  font-size: 12px;
  font-weight: 500;
}

.charge-query-grid label > span :deep(svg) {
  color: #94a3b8;
}

.charge-query-grid input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.charge-query-grid input::placeholder {
  color: var(--spd-text-placeholder);
}

.charge-query-grid input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.charge-filters .charge-filter-extra {
  display: none;
}

.charge-filters.is-expanded .charge-filter-extra {
  display: grid;
}

.charge-filter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--spd-border-subtle);
}

.charge-action-search,
.charge-action-reset,
.charge-action-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
}

.charge-action-search {
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border: 1px solid #1677ff;
  box-shadow: 0 2px 6px rgba(22, 119, 255, 0.25);
}

.charge-action-search:hover:not(:disabled) {
  background: linear-gradient(135deg, #0958d9 0%, #1677ff 100%);
  box-shadow: 0 4px 10px rgba(22, 119, 255, 0.3);
}

.charge-action-reset {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.charge-action-reset:hover:not(:disabled) {
  color: #0f172a;
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.charge-action-toggle {
  margin-left: auto;
  color: #475569;
  background: #fff;
  border: 1px dashed #cbd5e1;
}

.charge-action-toggle:hover {
  color: #1677ff;
  border-color: #1677ff;
  background: #eff6ff;
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
  background: #fff;
  font-size: 12px;
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
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.charge-summary-item.tone-blue {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.charge-summary-item.tone-blue .charge-summary-icon {
  color: #1d4ed8;
  background: #dbeafe;
}
.charge-summary-item.tone-blue .charge-summary-value {
  color: #1d4ed8;
}

.charge-summary-item.tone-green {
  border-color: #a7f3d0;
  background: #ecfdf5;
}
.charge-summary-item.tone-green .charge-summary-icon {
  color: #047857;
  background: #d1fae5;
}
.charge-summary-item.tone-green .charge-summary-value {
  color: #065f46;
}

.charge-summary-item.tone-amber {
  border-color: #fde68a;
  background: #fffbeb;
}
.charge-summary-item.tone-amber .charge-summary-icon {
  color: #b45309;
  background: #fef3c7;
}
.charge-summary-item.tone-amber .charge-summary-value {
  color: #b45309;
}

.charge-summary-item.tone-cyan {
  border-color: #a5f3fc;
  background: #ecfeff;
}
.charge-summary-item.tone-cyan .charge-summary-icon {
  color: #0e7490;
  background: #cffafe;
}
.charge-summary-item.tone-cyan .charge-summary-value {
  color: #155e75;
}

.charge-summary-item.tone-purple {
  border-color: #c4b5fd;
  background: #f5f3ff;
}
.charge-summary-item.tone-purple .charge-summary-icon {
  color: #6d28d9;
  background: #ede9fe;
}
.charge-summary-item.tone-purple .charge-summary-value {
  color: #5b21b6;
}

.charge-summary-item.tone-red {
  border-color: #fecaca;
  background: #fef2f2;
}
.charge-summary-item.tone-red .charge-summary-icon {
  color: #b91c1c;
  background: #fee2e2;
}
.charge-summary-item.tone-red .charge-summary-value {
  color: #b91c1c;
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
  color: #1677ff;
  background: #dbeafe;
  border-radius: 6px;
}

.charge-section-title h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.charge-section-title p {
  margin: 2px 0 0;
  color: var(--spd-text-secondary);
  font-size: 12px;
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
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.tool-refresh {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}
.tool-refresh:hover:not(:disabled) {
  color: #0f172a;
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.tool-export {
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}
.tool-export:hover:not(:disabled) {
  color: #064e3b;
  background: #d1fae5;
  border-color: #6ee7b7;
}

.tool-print {
  color: #6d28d9;
  background: #f5f3ff;
  border: 1px solid #c4b5fd;
}
.tool-print:hover:not(:disabled) {
  color: #4c1d95;
  background: #ede9fe;
  border-color: #a78bfa;
}

.charge-scroll-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-left: 6px;
  margin-left: 2px;
  border-left: 1px solid #e2e8f0;
}

.charge-scroll-actions .btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s ease;
}

.charge-scroll-actions .btn-icon:hover:not(:disabled) {
  border-color: #1677ff;
  color: #1677ff;
  background: #eff6ff;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.charge-detail-scroll::-webkit-scrollbar {
  height: 8px;
}

.charge-detail-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.charge-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.charge-detail-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.charge-detail-table {
  width: 100%;
  min-width: 1780px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.charge-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #1e293b !important;
  background: linear-gradient(180deg, #f0f7ff 0%, #e0efff 100%) !important;
  border-bottom: 1px solid #c7d2fe !important;
  text-align: left;
  white-space: nowrap;
  vertical-align: middle;
}

.charge-detail-table tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  color: #334155 !important;
  font-size: 13px !important;
  background: #fff;
  vertical-align: top;
}

.charge-data-row {
  cursor: pointer;
  transition: background 0.12s ease;
}

.charge-data-row td {
  background: #fff;
}

.charge-data-row:nth-child(even) td {
  background: #fafbfc;
}

.charge-data-row:hover td {
  background: #f0f7ff !important;
}

.charge-data-row.selected td {
  background: #e0efff !important;
  box-shadow: inset 3px 0 0 #1677ff;
}

.charge-data-row.selected:hover td {
  background: #d1e5ff !important;
}

.charge-cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.charge-cell-stack strong {
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.charge-biz-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #1d4ed8 !important;
  font-size: 12px !important;
  font-weight: 600 !important;
}

.muted-cell {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 1px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

.muted-cell :deep(svg) {
  color: #94a3b8;
  flex-shrink: 0;
}

.charge-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace !important;
  color: #475569 !important;
  font-size: 12px !important;
}

.numeric-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.numeric-cell strong {
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 600;
}

.charge-money {
  color: #475569;
  font-size: 12px !important;
}

.charge-amount strong {
  color: #b91c1c;
  font-size: 14px;
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
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.time-pill.tone-green {
  color: #047857;
  background: #d1fae5;
  border: 1px solid #a7f3d0;
}

.time-pill.tone-blue {
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.time-pill.tone-amber {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.time-pill.tone-gray {
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
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
  color: #64748b;
  font-size: 13px;
  background: #fafbfc;
}

.charge-state-cell.is-loading {
  color: #1d4ed8;
}

.charge-state-cell.is-error {
  color: #b91c1c;
  background: #fef2f2;
}

.charge-state-cell.is-empty {
  color: #94a3b8;
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
  border-bottom: 1px solid #c7d2fe !important;
  background: linear-gradient(180deg, #f8faff 0%, #eef4ff 100%) !important;
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
  font-size: 12px;
  font-weight: 500;
}

.charge-detail-value {
  min-width: 0;
  color: var(--spd-text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.charge-amount-strong {
  color: #b91c1c !important;
  font-size: 14px !important;
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
