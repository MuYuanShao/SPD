<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  Calendar,
  CheckCircle2,
  Flame,
  Package,
  Snowflake,
  Thermometer,
  Warehouse
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import {
  createColdChainException,
  fetchClosureList,
  fetchClosureOptions,
  type ClosureOptions
} from '../../api/operationalClosure'
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
const submitting = ref(false)
const exceptionOpen = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'success' | 'warning' | 'error'>('info')
const rows = ref<Record<string, unknown>[]>([])
const options = ref<ClosureOptions>({
  departments: [],
  warehouses: [],
  products: [],
  balances: []
})
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)

const filterForm = reactive({
  warehouseName: '',
  severity: '',
  status: ''
})

const form = reactive({
  warehouseName: '',
  productCode: '',
  temperature: '',
  severity: 'high',
  note: ''
})

const selectedProduct = computed(() =>
  options.value.products.find((product) => product.productCode === form.productCode)
)

function severityText(value: unknown) {
  const status = String(value || '')
  const labels: Record<string, string> = {
    low: '低',
    medium: '中',
    high: '高'
  }
  return labels[status] || status || '-'
}

function severityTone(value: unknown): 'high' | 'medium' | 'low' | 'gray' {
  const v = String(value || '').toLowerCase()
  if (v === 'high') return 'high'
  if (v === 'medium') return 'medium'
  if (v === 'low') return 'low'
  return 'gray'
}

function severityIcon(value: unknown) {
  const tone = severityTone(value)
  if (tone === 'high') return Flame
  if (tone === 'medium') return AlertTriangle
  if (tone === 'low') return Snowflake
  return Thermometer
}

function statusText(value: unknown) {
  const status = String(value || '')
  const labels: Record<string, string> = {
    pending_dispose: '待处置',
    disposed: '已处置',
    closed: '已关闭'
  }
  return labels[status] || status || '-'
}

/**
 * 冷链处置状态 → 全站通用状态条色调（SpdStatusChip 的 tone）。
 * 浅色底 + 深色字由组件统一提供，页面只负责语义映射。
 */
function statusTone(value: unknown): SpdStatusChipTone {
  const v = String(value || '').toLowerCase()
  if (v === 'pending_dispose') return 'warning'
  if (v === 'disposed') return 'info'
  return 'neutral'
}

function pushMessage(text: string, tone: 'info' | 'success' | 'warning' | 'error' = 'info') {
  message.value = text
  messageTone.value = tone
}

async function loadOptions() {
  options.value = await fetchClosureOptions()
  if (!form.warehouseName) form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  if (!form.productCode) form.productCode = options.value.products[0]?.productCode || ''
  if (!filterForm.warehouseName) filterForm.warehouseName = options.value.warehouses[0]?.warehouseName || ''
}

async function loadData() {
  loading.value = true
  try {
    const page = await fetchClosureList('cold-chain', {
      page: String(currentPage.value),
      size: String(pageSize.value)
    })
    rows.value = page.rows
    totalItems.value = page.total
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '冷链异常记录加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadData()
}

async function applyFilter() {
  currentPage.value = 1
  await loadData()
}

async function resetFilter() {
  filterForm.warehouseName = ''
  filterForm.severity = ''
  filterForm.status = ''
  currentPage.value = 1
  await loadData()
}

async function resetForm() {
  form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  form.productCode = options.value.products[0]?.productCode || ''
  form.temperature = ''
  form.severity = 'high'
  form.note = ''
}

function openExceptionDialog() {
  resetForm()
  exceptionOpen.value = true
}

async function submitException() {
  if (!form.warehouseName || !form.productCode || !form.temperature) {
    pushMessage('请填写库房、商品和温度', 'warning')
    return
  }
  submitting.value = true
  try {
    const result = await createColdChainException({
      warehouseName: form.warehouseName,
      productCode: form.productCode,
      temperature: form.temperature,
      severity: form.severity
    })
    exceptionOpen.value = false
    currentPage.value = 1
    await loadData()
    pushMessage(`冷链异常已登记：${result.eventNo || ''}`, 'success')
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '冷链异常登记失败', 'error')
  } finally {
    submitting.value = false
  }
}

async function changePage(page: number) {
  const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadData()
}

async function changePageSize(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

const summary = computed(() => {
  const total = totalItems.value
  let pending = 0
  let disposed = 0
  let high = 0
  let medium = 0
  for (const row of rows.value) {
    if (String(row.status || '') === 'pending_dispose') pending++
    if (String(row.status || '') === 'disposed') disposed++
    if (String(row.severity || '') === 'high') high++
    if (String(row.severity || '') === 'medium') medium++
  }
  return [
    { label: '异常总数', value: total, tone: 'blue', icon: AlertOctagon },
    { label: '待处置', value: pending, tone: 'amber', icon: AlertTriangle },
    { label: '已处置', value: disposed, tone: 'green', icon: CheckCircle2 },
    { label: '高严重度', value: high, tone: 'red', icon: Flame }
  ]
})

onMounted(async () => {
  await loadOptions()
  await loadData()
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const COLDCHAIN_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'exceptionNo', label: '异常编号', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'product', label: '商品信息', width: 200, visible: true, flexible: true, idealWidth: 250, weight: 6, minWidth: 140, maxWidth: 340 },
  { key: 'temperature', label: '温度', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'severity', label: '严重程度', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'recordTime', label: '登记时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220, fill: true }
]

const coldChainCols = useListColumns({
  storageKey: 'spd-cold-chain-cols-v1',
  defaults: COLDCHAIN_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「商品信息」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const coldChainTableRef = coldChainCols.tableRef
const isColdChainColVisible = (key: string) => coldChainCols.isColumnVisible(key)
const coldChainColBinding = (key: string) => coldChainCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const coldChainColSettingsAnchorRef = ref<HTMLElement | null>(null)
const coldChainColSettingsPopRef = ref<HTMLElement | null>(null)
const coldChainColSettingsPop = useColSettingsPopover({
  open: coldChainCols.settingsOpen,
  anchorRef: coldChainColSettingsAnchorRef,
  popRef: coldChainColSettingsPopRef
})

</script>

<template>
  <main class="cold-chain-page spd-unified-list">
    <MdPageHeader
      crumb="供应链业务"
      title="冷链监控"
      :loading="loading"
      @refresh="refresh"
    />

    <!-- 提示消息：不放在顶部条，根据 tone 着色 -->
    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

    <!-- ============ 汇总条 ============ -->
    <section class="cold-summary" aria-label="冷链异常汇总">
      <div
        v-for="item in summary"
        :key="item.label"
        class="cold-summary-item"
        :class="`tone-${item.tone}`"
      >
        <span class="cold-summary-icon"><component :is="item.icon" :size="14" /></span>
        <span class="cold-summary-label">{{ item.label }}</span>
        <strong class="cold-summary-value">{{ item.value }}</strong>
      </div>
    </section>

    <!-- ============ 面板：异常记录 + 顶部「登记异常」按钮 ============ -->
    <section class="cold-panel" :aria-busy="loading">
      <header class="cold-panel-header">
        <div class="cold-panel-title">
          <span class="cold-panel-icon"><Snowflake :size="16" /></span>
          <div>
            <h3>冷链异常记录</h3>
            <p>共 {{ totalItems }} 条 · 当前第 {{ currentPage }} / {{ Math.max(1, Math.ceil(totalItems / pageSize)) }} 页</p>
          </div>
        </div>
        <div class="cold-panel-actions">
          <SpdButton label="登记异常" tone="primary" class="cold-register-btn" @click="openExceptionDialog">
          <template #icon><Thermometer :size="16" /></template>
        </SpdButton>
        </div>
      </header>

      <!-- 筛选：已统一改用 <SpdFilterBar>，与「科室库房目录」等页面完全一致
           （同一外壳底色 / 边框 / 字段高度 / 标签排版 / 操作区位置）。 -->
      <SpdFilterBar @submit="applyFilter" @reset="resetFilter">
        <label>
          <span class="cold-filter-tag is-warehouse"><Warehouse :size="12" /> 库房</span>
          <select v-model="filterForm.warehouseName" @change="applyFilter">
            <option value="">全部库房</option>
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-filter-tag is-severity"><Flame :size="12" /> 严重程度</span>
          <select v-model="filterForm.severity" @change="applyFilter">
            <option value="">全部程度</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
        <label>
          <span class="cold-filter-tag is-status"><AlertTriangle :size="12" /> 状态</span>
          <select v-model="filterForm.status" @change="applyFilter">
            <option value="">全部状态</option>
            <option value="pending_dispose">待处置</option>
            <option value="disposed">已处置</option>
            <option value="closed">已关闭</option>
          </select>
        </label>
      </SpdFilterBar>

            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="coldChainColSettingsAnchorRef"
           
           
            @click="coldChainCols.settingsOpen.value = !coldChainCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <div
            v-if="coldChainCols.settingsOpen.value"
            ref="coldChainColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': coldChainColSettingsPop.align.value === 'right', 'is-align-up': coldChainColSettingsPop.vertical.value === 'up' }"
            :style="coldChainColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="coldChainCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in coldChainCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && coldChainCols.visibleColumnCount.value <= 1"
                  @change="coldChainCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="coldChainCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="coldChainTableRef"
            :data="rows"
            row-key="exceptionNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无冷链异常记录'"
            @header-dragend="(width: number, oldWidth: number, column: any) => coldChainCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="exceptionNo"
              v-if="isColdChainColVisible('exceptionNo')"
              label="异常编号"
              v-bind="coldChainColBinding('exceptionNo')"
            >
              <template #header>
                <span class="ulc-th-text">异常编号</span>
              </template>
              <template #default="{ row }">
                <strong class="cold-biz-no ulc-wrap-text">{{ row.bizNo || '-' }}</strong>
              </template>
            </el-table-column>
            <el-table-column
              column-key="warehouseName"
              v-if="isColdChainColVisible('warehouseName')"
              label="库房"
              v-bind="coldChainColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">库房</span>
              </template>
              <template #default="{ row }">
                <span class="cold-cell-warehouse ulc-wrap-text">
                  <Warehouse :size="12" />
                  {{ row.warehouseName || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isColdChainColVisible('product')"
              label="商品信息"
              v-bind="coldChainColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品信息</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.productName || '-' }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ row.productCode || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="temperature"
              v-if="isColdChainColVisible('temperature')"
              label="温度"
              v-bind="coldChainColBinding('temperature')"
            >
              <template #header>
                <span class="ulc-th-text">温度</span>
              </template>
              <template #default="{ row }">
                <span class="cold-temp" :class="`is-${severityTone(row.severity)}`">
                  <component :is="severityIcon(row.severity)" :size="14" />
                  <strong>{{ row.temperature || '-' }}</strong>
                  <em>℃</em>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="severity"
              v-if="isColdChainColVisible('severity')"
              label="严重程度"
              v-bind="coldChainColBinding('severity')"
            >
              <template #header>
                <span class="ulc-th-text">严重程度</span>
              </template>
              <template #default="{ row }">
                <span class="severity-pill" :class="`is-${severityTone(row.severity)}`">
                  <component :is="severityIcon(row.severity)" :size="12" />
                  {{ severityText(row.severity) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isColdChainColVisible('status')"
              label="状态"
              v-bind="coldChainColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone(row.status)">
                  {{ statusText(row.status) }}
                </SpdStatusChip>
              </template>
            </el-table-column>
            <el-table-column
              column-key="recordTime"
              v-if="isColdChainColVisible('recordTime')"
              label="登记时间"
              v-bind="coldChainColBinding('recordTime')"
            >
              <template #header>
                <span class="ulc-th-text">登记时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><Calendar :size="12" />
                {{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无冷链异常记录" />
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

    <el-dialog v-model="exceptionOpen" title="登记冷链异常" width="min(860px, 94vw)" append-to-body class="cold-chain-dialog">
      <div class="cold-chain-form">
        <label>
          <span class="cold-form-tag is-warehouse"><Warehouse :size="12" /> 库房</span>
          <select v-model="form.warehouseName">
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-form-tag is-product"><Package :size="12" /> 商品</span>
          <select v-model="form.productCode">
            <option v-for="product in options.products" :key="product.productCode" :value="product.productCode">
              {{ product.productName }} / {{ product.productCode }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-form-tag is-temp"><Thermometer :size="12" /> 当前温度（℃）</span>
          <input v-model="form.temperature" type="number" step="0.1" placeholder="例如 9.5" />
        </label>
        <label>
          <span class="cold-form-tag is-severity"><Flame :size="12" /> 严重程度</span>
          <select v-model="form.severity">
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
      </div>
      <p v-if="selectedProduct" class="cold-chain-product">
        <span class="cold-form-tag is-product"><Package :size="12" /> 当前商品</span>
        {{ selectedProduct.productName }}，规格 {{ selectedProduct.specModel || '-' }}，单位 {{ selectedProduct.unit || '-' }}
      </p>
      <div class="cold-chain-dialog-actions">
        <SpdButton tone="primary" class="cold-dialog-save" :disabled="submitting" @click="submitException">
          <template #icon><CheckCircle2 :size="16" /></template>
          {{ submitting ? '保存中...' : '保存异常' }}
        </SpdButton>
        <SpdButton label="重置" tone="neutral" class="cold-dialog-reset" :disabled="submitting" @click="resetForm"></SpdButton>
        <SpdButton label="取消" tone="neutral" class="cold-dialog-cancel" :disabled="submitting" @click="exceptionOpen = false"></SpdButton>
      </div>
    </el-dialog>
  </main>
</template>

<style scoped>
/* =====================================================================
   冷链监控（重制版）
   - 登记异常按钮移到面板顶部，不再放在页面标题条
   - 顶部汇总条（4 项彩色 chips）
   - 筛选条（库房/严重度/状态）
   - 表格温度用大字彩色块 + 图标
   ===================================================================== */
.cold-chain-page {
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

.cold-chain-page h3,
.cold-chain-page p {
  margin: 0;
}

/* ---------- 汇总 chips ---------- */
.cold-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cold-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 140px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 999px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-sm);
  transition: transform 0.1s ease;
}

.cold-summary-item:hover {
  transform: translateY(-1px);
}

.cold-summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.cold-summary-label {
  color: var(--spd-text-secondary);
}

.cold-summary-value {
  margin-left: auto;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 「异常总数」用中性石板档位（与其它页面的「总量」一致），
   不再使用信息语义蓝，避免全站出现品牌蓝。 */
.cold-summary-item.tone-blue {
  border-color: var(--spd-p-slate-border);
  background: var(--spd-p-slate-bg);
}
.cold-summary-item.tone-blue .cold-summary-icon {
  color: var(--spd-p-slate-strong);
  background: var(--spd-p-bg-muted);
}
.cold-summary-item.tone-blue .cold-summary-value {
  color: var(--spd-p-slate-strong);
}

.cold-summary-item.tone-amber {
  border-color: var(--spd-p-warning-border);
  background: var(--spd-p-warning-bg);
}
.cold-summary-item.tone-amber .cold-summary-icon {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
}
.cold-summary-item.tone-amber .cold-summary-value {
  color: var(--spd-p-warning-text);
}

.cold-summary-item.tone-green {
  border-color: var(--spd-p-success-border);
  background: var(--spd-p-primary-bg);
}
.cold-summary-item.tone-green .cold-summary-icon {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
}
.cold-summary-item.tone-green .cold-summary-value {
  color: var(--spd-p-success-strong);
}

.cold-summary-item.tone-red {
  border-color: var(--spd-p-danger-border);
  background: var(--spd-p-danger-bg);
}
.cold-summary-item.tone-red .cold-summary-icon {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
}
.cold-summary-item.tone-red .cold-summary-value {
  color: var(--spd-p-danger-strong);
}

/* ---------- 面板 ---------- */
.cold-panel {
  box-sizing: border-box;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.cold-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.cold-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cold-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--spd-p-primary-hover);
  background: var(--spd-p-info-bg);
  border-radius: 6px;
}

.cold-panel-title h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.cold-panel-title p {
  margin: 2px 0 0;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.cold-panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 「登记异常」按钮：完全服从 SpdButton 统一几何与配色，
   这里只保留图标间距，不再覆盖高度 / 内边距 / 字号 / 颜色。 */
.cold-register-btn {
  gap: 5px;
}

/* ---------- 筛选条 ----------
   本页筛选区已迁移为统一筛选栏组件 <SpdFilterBar>：
   外壳底色 / 边框 / 字段高度 / 标签排版 / 操作区位置
   全部由 styles/components/spd-filter-bar.css 的「标准规则」提供，
   与「科室库房目录」完全一致，本页不再自造筛选栅格。
   下面只保留本页特有的「图标 + 文字」标签配色，
   以及迁移前 .cold-filter-bar 的下间距（保持原有版面不变）。 */
.cold-panel > .spd-filter-bar {
  margin-bottom: 12px;
}

.cold-filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
  padding: 1px 8px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  border-radius: 4px;
}

.cold-filter-tag.is-warehouse {
  /* 中性石板档位（非品牌蓝） */
  color: var(--spd-p-slate-strong);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-slate-border);
}

.cold-filter-tag.is-severity {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
}

.cold-filter-tag.is-status {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border: 1px solid var(--spd-p-warning-border);
}

/* ---------- 表格 ---------- */
.cold-chain-scroll {
  width: 100%;
  overflow-x: auto;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
}

.cold-chain-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.cold-chain-table thead th {
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

.cold-chain-table tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid var(--spd-p-text-strong) !important;
  color: var(--spd-p-text-secondary) !important;
  font-size: var(--spd-font-size-base) !important;
  background: var(--spd-p-bg);
  vertical-align: middle;
}

.cold-chain-table tbody tr:nth-child(even) td {
  background: var(--spd-p-row-stripe);
}

.cold-chain-table tbody tr:hover td {
  background: var(--spd-p-info-bg) !important;
}

.cold-chain-table tbody tr.is-row-high td {
  background: var(--spd-p-danger-bg);
}

.cold-chain-table tbody tr.is-row-high:hover td {
  background: var(--spd-p-danger-bg) !important;
}

.cold-chain-table tbody tr.is-row-medium td {
  background: var(--spd-p-warning-bg);
}

.cold-chain-table tbody tr.is-row-medium:hover td {
  background: var(--spd-p-warning-bg) !important;
}

.cold-biz-no {
  font-family: var(--spd-font-family-mono);
  color: var(--spd-p-success-text) !important;
  font-size: var(--spd-font-size-base) !important;
  font-weight: 600 !important;
}

.cold-cell-warehouse {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cold-cell-warehouse :deep(svg) {
  color: var(--spd-p-primary-hover);
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
}

.cold-chain-time {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.cold-chain-time :deep(svg) {
  color: var(--spd-p-text-muted);
}

/* ---------- 温度大色块 ---------- */
.cold-temp {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 92px;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 8px;
  font-variant-numeric: tabular-nums;
}

.cold-temp strong {
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: 1;
}

.cold-temp em {
  font-size: var(--spd-font-size-xs);
  font-style: normal;
  font-weight: 500;
}

.cold-temp.is-high {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border-color: var(--spd-p-danger-border);
}

.cold-temp.is-medium {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

.cold-temp.is-low {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success-border);
}

.cold-temp.is-gray {
  /* 中性灰温标：浅底 + 深字（此前误用近黑底 + 深灰字，几乎不可见） */
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-border);
}

/* ---------- 严重度 pill ---------- */
.severity-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 22px;
  padding: 0 9px;
  border: 1px solid;
  border-radius: 999px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  white-space: nowrap;
}

.severity-pill.is-high {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border-color: var(--spd-p-danger-border);
}

.severity-pill.is-medium {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

.severity-pill.is-low {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success-border);
}

.severity-pill.is-gray {
  /* 中性灰状态条：浅底 + 深字（此前误用近黑底 + 深灰字，几乎不可见） */
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-border);
}

/* ---------- 状态行 ---------- */
.cold-state-row td {
  padding: 0 !important;
  border-bottom: 0 !important;
}

.cold-state-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
}

.cold-state-cell.is-loading {
  color: var(--spd-p-success-text);
}

.cold-state-cell .spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- 登记弹窗 ---------- */
.cold-chain-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px 16px;
}

.cold-chain-form label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  margin: 0;
}

.cold-form-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
  padding: 1px 8px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  border-radius: 4px;
}

.cold-form-tag.is-warehouse {
  /* 中性石板档位（非品牌蓝） */
  color: var(--spd-p-slate-strong);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-info-border);
}

.cold-form-tag.is-product {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success-border);
}

.cold-form-tag.is-temp {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
}

.cold-form-tag.is-severity {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border: 1px solid var(--spd-p-warning-border);
}

.cold-chain-form select,
.cold-chain-form input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.cold-chain-form select:focus,
.cold-chain-form input:focus {
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.cold-chain-product {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--spd-p-primary-bg);
  border: 1px solid var(--spd-p-success-border);
  color: var(--spd-p-primary-strong);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-loose);
}

.cold-chain-dialog-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--spd-border-subtle);
}

/* 弹窗底部按钮：完全服从 SpdButton 统一几何与配色，不再覆盖任何尺寸。 */
.cold-dialog-save {
  gap: 4px;
}

.cold-dialog-reset,
.cold-dialog-cancel {
  gap: 4px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .cold-chain-page {
    padding: 12px 12px 20px;
  }
  .cold-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
}
</style>
