<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  Calendar,
  CheckCircle2,
  Filter,
  Flame,
  Package,
  Snowflake,
  Thermometer,
  Warehouse,
  XCircle
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import {
  createColdChainException,
  fetchClosureList,
  fetchColdChainOptions,
  type ColdChainOptions
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
import { useAuthStore } from '../../stores/auth'
import { formatApiErrorText } from '../../utils/chineseDisplay'

const notify = useNotify()
const auth = useAuthStore()


const loading = ref(false)
const submitting = ref(false)
const exceptionOpen = ref(false)
const formError = ref('')
const optionsError = ref('')
const optionsLoading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const options = ref<ColdChainOptions>({
  warehouses: [],
  products: []
})
const searchedWarehouses = ref<ColdChainOptions['warehouses'] | null>(null)
const searchedProducts = ref<ColdChainOptions['products'] | null>(null)
const rememberedProduct = ref<ColdChainOptions['products'][number]>()
const searchLoading = reactive({ warehouse: false, product: false })
const searchVersion = { warehouse: 0, product: 0 }
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
  temperature: '' as number | '',
  severity: 'high'
})

const selectedProduct = computed(() =>
  searchedProducts.value?.find((product) => product.productCode === form.productCode)
    ?? options.value.products.find((product) => product.productCode === form.productCode)
    ?? (rememberedProduct.value?.productCode === form.productCode ? rememberedProduct.value : undefined)
)

function apiErrorText(error: unknown, fallback: string) {
  const message = (error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message
  const text = message || (error instanceof Error ? error.message : '')
  return /\p{Script=Han}/u.test(text) ? text : formatApiErrorText(text, fallback)
}

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

function statusTone(value: unknown): 'warning' | 'info' | 'gray' {
  const v = String(value || '').toLowerCase()
  if (v === 'pending_dispose') return 'warning'
  if (v === 'disposed') return 'info'
  return 'gray'
}

async function loadOptions() {
  if (optionsLoading.value) return
  optionsLoading.value = true
  optionsError.value = ''
  try {
    options.value = await fetchColdChainOptions()
    if (!form.warehouseName) form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
    if (!form.productCode) form.productCode = options.value.products[0]?.productCode || ''
  } catch (error) {
    optionsError.value = apiErrorText(error, '加载库房和商品失败，请重试')
  } finally {
    optionsLoading.value = false
  }
}

async function searchOptions(kind: 'warehouse' | 'product', query: string) {
  const version = ++searchVersion[kind]
  const keyword = query.trim()
  if (!keyword) {
    if (kind === 'warehouse') searchedWarehouses.value = null
    else searchedProducts.value = null
    searchLoading[kind] = false
    return
  }
  searchLoading[kind] = true
  formError.value = ''
  try {
    const result = await fetchColdChainOptions(kind === 'warehouse'
      ? { warehouseKeyword: keyword } : { productKeyword: keyword })
    if (version !== searchVersion[kind]) return
    if (kind === 'warehouse') searchedWarehouses.value = result.warehouses
    else searchedProducts.value = result.products
  } catch (error) {
    if (version === searchVersion[kind]) formError.value = apiErrorText(error, '选项搜索失败，请重试')
  } finally {
    if (version === searchVersion[kind]) searchLoading[kind] = false
  }
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
    notify.error(apiErrorText(error, '冷链异常记录加载失败'))
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

function resetForm() {
  void searchOptions('warehouse', '')
  void searchOptions('product', '')
  rememberedProduct.value = undefined
  form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  form.productCode = options.value.products[0]?.productCode || ''
  form.temperature = ''
  form.severity = 'high'
  formError.value = ''
}

function openExceptionDialog() {
  resetForm()
  exceptionOpen.value = true
  if (optionsError.value) void loadOptions()
}

async function submitException() {
  if (submitting.value || optionsLoading.value || optionsError.value) return
  formError.value = ''
  const temperature = Number(form.temperature)
  if (!form.warehouseName || !form.productCode || form.temperature === '' || !Number.isFinite(temperature)) {
    formError.value = '请填写库房、商品和有效温度'
    return
  }
  submitting.value = true
  try {
    const result = await createColdChainException({
      warehouseName: form.warehouseName,
      productCode: form.productCode,
      temperature,
      severity: form.severity
    })
    exceptionOpen.value = false
    currentPage.value = 1
    await loadData()
    notify.success(`冷链异常已登记${result.eventNo ? `（${result.eventNo}）` : ''}`)
  } catch (error) {
    formError.value = apiErrorText(error, '冷链异常登记失败')
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

onMounted(() => {
  void loadOptions()
  void loadData()
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
          <button
            v-if="auth.canWrite('cold-chain-monitoring')"
            class="btn cold-register-btn"
            type="button"
            @click="openExceptionDialog"
          >
            <Thermometer :size="16" />
            登记异常
          </button>
        </div>
      </header>

      <!-- 筛选 -->
      <form class="cold-filter-bar" @submit.prevent="applyFilter">
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
        <div class="cold-filter-actions">
          <button class="btn btn-sm cold-filter-reset" type="button" @click="resetFilter">
            <XCircle :size="14" />
            重置
          </button>
        </div>
      </form>

            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <button
            ref="coldChainColSettingsAnchorRef"
            type="button"
            class="md-btn md-btn-primary"
            @click="coldChainCols.settingsOpen.value = !coldChainCols.settingsOpen.value"
          >
            <Columns3 :size="16" />
            <span>列设置</span>
            <ChevronDown :size="14" :class="{ rotated: coldChainCols.settingsOpen.value }" />
          </button>
          <div
            v-if="coldChainCols.settingsOpen.value"
            ref="coldChainColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': coldChainColSettingsPop.align.value === 'right', 'is-align-up': coldChainColSettingsPop.vertical.value === 'up' }"
            :style="{ '--ulc-pop-offset-y': coldChainColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': coldChainColSettingsPop.maxHeight.value + 'px' }"
          >
            <header>
              <strong>列展示设置</strong>
              <button type="button" class="ulc-link" @click="coldChainCols.resetColumns()">恢复默认</button>
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
              <button type="button" class="ulc-action" @click="coldChainCols.settingsOpen.value = false">确定</button>
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
                  <strong>{{ row.temperature ?? '-' }}</strong>
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
                <span class="status-pill" :class="`is-${statusTone(row.status)}`">
                  {{ statusText(row.status) }}
                </span>
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

    <el-dialog v-model="exceptionOpen" title="登记冷链异常" width="min(860px, 94vw)" append-to-body class="cold-chain-dialog"
      :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting">
      <div class="cold-chain-form">
        <label>
          <span class="cold-form-tag is-warehouse"><Warehouse :size="12" /> 库房</span>
          <el-select v-model="form.warehouseName" aria-label="库房" filterable remote
            :remote-method="(query: string) => searchOptions('warehouse', query)" :loading="searchLoading.warehouse"
            placeholder="搜索库房名称或编码" no-data-text="暂无匹配库房">
            <el-option v-for="warehouse in searchedWarehouses ?? options.warehouses" :key="warehouse.warehouseName"
              :value="warehouse.warehouseName" :label="warehouse.warehouseName" />
          </el-select>
        </label>
        <label>
          <span class="cold-form-tag is-product"><Package :size="12" /> 商品</span>
          <el-select v-model="form.productCode" aria-label="商品" filterable remote
            :remote-method="(query: string) => searchOptions('product', query)" :loading="searchLoading.product"
            @change="rememberedProduct = selectedProduct"
            placeholder="搜索商品名称或编码" no-data-text="暂无匹配商品">
            <el-option v-for="product in searchedProducts ?? options.products" :key="product.productCode"
              :value="product.productCode" :label="`${product.productName} / ${product.productCode}`" />
          </el-select>
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
      <StatusMessage :message="optionsError || formError" tone="error" role="alert" />
      <StatusMessage v-if="optionsLoading" message="正在加载库房和商品…" tone="info" role="status" />
      <div class="cold-chain-dialog-actions">
        <button class="btn btn-primary cold-dialog-save" type="button" :disabled="submitting || optionsLoading || Boolean(optionsError)" @click="submitException">
          <CheckCircle2 :size="16" />
          {{ submitting ? '保存中...' : '保存异常' }}
        </button>
        <button v-if="optionsError" class="btn" type="button" @click="loadOptions">重新加载选项</button>
        <button class="btn cold-dialog-reset" type="button" :disabled="submitting" @click="resetForm">
          重置
        </button>
        <button class="btn cold-dialog-cancel" type="button" :disabled="submitting" @click="exceptionOpen = false">
          取消
        </button>
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
  background: #fff;
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

.cold-summary-item.tone-blue {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.cold-summary-item.tone-blue .cold-summary-icon {
  color: #1d4ed8;
  background: #dbeafe;
}
.cold-summary-item.tone-blue .cold-summary-value {
  color: #1d4ed8;
}

.cold-summary-item.tone-amber {
  border-color: #fde68a;
  background: #fffbeb;
}
.cold-summary-item.tone-amber .cold-summary-icon {
  color: #b45309;
  background: #fef3c7;
}
.cold-summary-item.tone-amber .cold-summary-value {
  color: #b45309;
}

.cold-summary-item.tone-green {
  border-color: #a7f3d0;
  background: #ecfdf5;
}
.cold-summary-item.tone-green .cold-summary-icon {
  color: #047857;
  background: #d1fae5;
}
.cold-summary-item.tone-green .cold-summary-value {
  color: #047857;
}

.cold-summary-item.tone-red {
  border-color: #fecaca;
  background: #fef2f2;
}
.cold-summary-item.tone-red .cold-summary-icon {
  color: #b91c1c;
  background: #fee2e2;
}
.cold-summary-item.tone-red .cold-summary-value {
  color: #b91c1c;
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
  color: #0891b2;
  background: #cffafe;
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

.cold-register-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 16px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  border: 1px solid #f97316;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
  cursor: pointer;
  transition: all 0.15s ease;
}

.cold-register-btn:hover {
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
  box-shadow: 0 4px 10px rgba(249, 115, 22, 0.3);
}

/* ---------- 筛选条 ---------- */
.cold-filter-bar {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(140px, 0.7fr) minmax(140px, 0.8fr) auto;
  gap: 10px 12px;
  align-items: end;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.cold-filter-bar label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
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
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.cold-filter-tag.is-severity {
  color: #b91c1c;
  background: #fee2e2;
  border: 1px solid #fecaca;
}

.cold-filter-tag.is-status {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.cold-filter-bar select {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: var(--spd-font-size-base);
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.cold-filter-bar select:focus {
  outline: 0;
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.cold-filter-actions {
  display: flex;
  gap: 8px;
}

.cold-filter-reset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px;
  font-size: var(--spd-font-size-sm);
  color: #475569;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

.cold-filter-reset:hover:not(:disabled) {
  color: #0f172a;
  background: #f1f5f9;
  border-color: #94a3b8;
}

/* ---------- 表格 ---------- */
.cold-chain-scroll {
  width: 100%;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
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
  color: #1e293b !important;
  background: linear-gradient(180deg, #ecfeff 0%, #cffafe 100%) !important;
  border-bottom: 1px solid #67e8f9 !important;
  text-align: left;
  white-space: nowrap;
}

.cold-chain-table tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  color: #334155 !important;
  font-size: var(--spd-font-size-base) !important;
  background: #fff;
  vertical-align: middle;
}

.cold-chain-table tbody tr:nth-child(even) td {
  background: #fafbfc;
}

.cold-chain-table tbody tr:hover td {
  background: #ecfeff !important;
}

.cold-chain-table tbody tr.is-row-high td {
  background: #fff5f5;
}

.cold-chain-table tbody tr.is-row-high:hover td {
  background: #fee2e2 !important;
}

.cold-chain-table tbody tr.is-row-medium td {
  background: #fffaf0;
}

.cold-chain-table tbody tr.is-row-medium:hover td {
  background: #fef3c7 !important;
}

.cold-biz-no {
  font-family: var(--spd-font-family-mono);
  color: #0e7490 !important;
  font-size: var(--spd-font-size-base) !important;
  font-weight: 600 !important;
}

.cold-cell-warehouse {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cold-cell-warehouse :deep(svg) {
  color: #0891b2;
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
  color: #94a3b8;
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
  color: #b91c1c;
  background: #fee2e2;
  border-color: #fecaca;
}

.cold-temp.is-medium {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.cold-temp.is-low {
  color: #047857;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.cold-temp.is-gray {
  color: #475569;
  background: #f1f5f9;
  border-color: #e2e8f0;
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
  color: #b91c1c;
  background: #fee2e2;
  border-color: #fecaca;
}

.severity-pill.is-medium {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.severity-pill.is-low {
  color: #047857;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.severity-pill.is-gray {
  color: #475569;
  background: #f1f5f9;
  border-color: #e2e8f0;
}

/* ---------- 状态 pill ---------- */
.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 9px;
  border: 1px solid;
  border-radius: 999px;
  /* S-08 状态徽标：--spd-font-size-base + 字重 400 */
  font-size: var(--spd-font-size-base);
  font-weight: 400;
  white-space: nowrap;
}

.status-pill.is-warning {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.status-pill.is-info {
  color: #1d4ed8;
  background: #dbeafe;
  border-color: #bfdbfe;
}

.status-pill.is-gray {
  color: #64748b;
  background: #f1f5f9;
  border-color: #e2e8f0;
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
  color: #64748b;
  font-size: var(--spd-font-size-base);
}

.cold-state-cell.is-loading {
  color: #0e7490;
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
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.cold-form-tag.is-product {
  color: #047857;
  background: #d1fae5;
  border: 1px solid #a7f3d0;
}

.cold-form-tag.is-temp {
  color: #b91c1c;
  background: #fee2e2;
  border: 1px solid #fecaca;
}

.cold-form-tag.is-severity {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.cold-chain-form label > input {
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
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.cold-chain-form label > input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.cold-chain-product {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #065f46;
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

.cold-dialog-save {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 16px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  border: 1px solid #f97316;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
}

.cold-dialog-save:hover:not(:disabled) {
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
}

.cold-dialog-reset,
.cold-dialog-cancel {
  height: 34px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
  color: #475569;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

.cold-dialog-reset:hover:not(:disabled),
.cold-dialog-cancel:hover:not(:disabled) {
  color: #0f172a;
  background: #f1f5f9;
  border-color: #94a3b8;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .cold-chain-page {
    padding: 12px 12px 20px;
  }
  .cold-filter-bar {
    grid-template-columns: 1fr 1fr;
  }
  .cold-filter-actions {
    grid-column: 1 / -1;
  }
  .cold-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
}
</style>
