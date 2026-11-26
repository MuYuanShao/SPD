<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Activity, AlertTriangle, BellRing, Building2, ChevronDown, Columns3, Database, RefreshCw, TrendingUp
} from '@lucide/vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import { fetchOperationCockpit, type OperationCockpitData } from '../../api/operationCockpit'
import { notifyError } from '../../composables/useNotify'

const now = new Date()
const selectedMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const clock = ref('')
const loading = ref(false)
const data = ref<OperationCockpitData>(emptyData(selectedMonth.value))

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   驾驶舱里的两块明细数据（科室消耗排名 / 耗材分类对比）改成统一列表：
     - fitMode 使用默认的 'fixed'：每列宽度互相独立，
       拖拽某一列不会因为容器宽度变化而影响其它列；
     - 列宽与列显隐持久化到 localStorage。
   ===================================================================== */
const DEPARTMENT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'rank', label: '排名', width: 70, visible: true, weight: 2, minWidth: 60, maxWidth: 100 },
  { key: 'name', label: '科室', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 9, minWidth: 150, maxWidth: 360 },
  { key: 'currentAmount', label: '本月金额（万元）', width: 140, visible: true, weight: 4, minWidth: 120, maxWidth: 210 },
  { key: 'previousAmount', label: '上月金额（万元）', width: 140, visible: true, weight: 4, minWidth: 120, maxWidth: 210 },
  { key: 'monthOnMonth', label: '环比', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 170, fill: true }
]

const CATEGORY_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'category', label: '分类', width: 180, visible: true, flexible: true, idealWidth: 220, weight: 8, minWidth: 130, maxWidth: 340 },
  { key: 'currentAmount', label: '本月（万元）', width: 140, visible: true, weight: 4, minWidth: 120, maxWidth: 210 },
  { key: 'previousAmount', label: '上月（万元）', width: 140, visible: true, weight: 4, minWidth: 120, maxWidth: 210 },
  { key: 'monthOnMonth', label: '环比', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 170, fill: true }
]

const departmentCols = useListColumns({
  storageKey: 'spd-operation-cockpit-department-cols-v1',
  defaults: DEPARTMENT_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「科室」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})
const departmentColBinding = (key: string) => departmentCols.columnBinding(key)

const categoryCols = useListColumns({
  storageKey: 'spd-operation-cockpit-category-cols-v1',
  defaults: CATEGORY_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「分类」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})
const categoryColBinding = (key: string) => categoryCols.columnBinding(key)

/** 当前驾驶舱表格面板（决定列设置面板展示哪一套列） */
const cockpitTablePanel = ref<'department' | 'category'>('department')

/** 当前展示的列配置（科室排名 / 分类对比 两个面板切换） */
const activeCockpitCols = computed(() => (cockpitTablePanel.value === 'department' ? departmentCols : categoryCols))

const isCockpitColVisible = (key: string) => activeCockpitCols.value.isColumnVisible(key)
const cockpitColBinding = (key: string) => activeCockpitCols.value.columnBinding(key)

/** 科室排名表 / 分类对比表各自的表格实例（用于操作列固定与拖拽回调） */
const departmentTableRef = ref<any>(null)
const categoryTableRef = ref<any>(null)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const cockpitActionFix = useActionColumnFix()
const cockpitActionFixed = computed(() => cockpitActionFix.actionFixedBinding.value)

watch(
  [departmentTableRef, categoryTableRef],
  () => {
    cockpitActionFix.observe(departmentTableRef.value ?? categoryTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const cockpitColSettingsAnchorRef = ref<HTMLElement | null>(null)
const cockpitColSettingsPopRef = ref<HTMLElement | null>(null)
const cockpitColSettingsPop = useColSettingsPopover({
  open: computed({
    get: () => activeCockpitCols.value.settingsOpen.value,
    set: (value) => { activeCockpitCols.value.settingsOpen.value = value }
  }),
  anchorRef: cockpitColSettingsAnchorRef,
  popRef: cockpitColSettingsPopRef
})

let clockTimer = 0

const totalCategoryAmount = computed(() =>
  data.value.categories.reduce((sum, item) => sum + Number(item.currentAmount || 0), 0)
)
const categoryGradient = computed(() => {
  const colors = ['var(--spd-p-info)', 'var(--spd-p-success)', 'var(--spd-p-success)']
  const total = Math.max(1, totalCategoryAmount.value)
  let cursor = 0
  return `conic-gradient(${data.value.categories.map((item, index) => {
    const start = cursor
    cursor += Number(item.currentAmount || 0) / total * 100
    return `${colors[index]} ${start}% ${cursor}%`
  }).join(',')})`
})
const maxTrend = computed(() => Math.max(1, ...data.value.trend.map(item => Number(item.amount))))
const trendPoints = computed(() => data.value.trend.map((item, index) => {
  const x = data.value.trend.length <= 1 ? 50 : 5 + index * 90 / (data.value.trend.length - 1)
  const y = 88 - Number(item.amount) / maxTrend.value * 68
  return { ...item, x, y }
}))
const trendPolyline = computed(() => trendPoints.value.map(item => `${item.x},${item.y}`).join(' '))
const trendArea = computed(() => trendPoints.value.length
  ? `5,92 ${trendPolyline.value} 95,92`
  : '')
const maxDepartment = computed(() => Math.max(1, ...data.value.departments.map(item => Number(item.currentAmount))))
const maxFocusedProduct = computed(() => Math.max(1, ...data.value.focusedProducts.flatMap(item => [Number(item.currentAmount), Number(item.previousAmount)])))

function emptyData(month: string): OperationCockpitData {
  return {
    month,
    summary: { currentAmount: 0, previousAmount: 0, monthOnMonth: 0, departmentCount: 0, warningCount: 0 },
    categories: [], trend: [], departments: [], focusedProducts: [], alerts: []
  }
}

function formatWan(value: number) {
  return (Number(value || 0) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function formatPercent(value?: number) {
  return `${Number(value || 0) >= 0 ? '+' : ''}${Number(value || 0).toFixed(2)}%`
}
function comparisonRate(current: number, previous: number) {
  if (!Number(previous)) return Number(current) ? 100 : 0
  return (Number(current) - Number(previous)) / Number(previous) * 100
}
function trendTone(value: number) {
  return value > 0 ? 'up' : value < 0 ? 'down' : 'flat'
}
function updateClock() {
  clock.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(new Date())
}
async function loadCockpit() {
  loading.value = true
  try {
    data.value = await fetchOperationCockpit(selectedMonth.value)
  } catch (reason) {
    notifyError(reason instanceof Error ? reason.message : '运营驾驶舱数据加载失败')
    data.value = emptyData(selectedMonth.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  loadCockpit()
})
onBeforeUnmount(() => window.clearInterval(clockTimer))
</script>

<template>
  <main class="operation-cockpit-page spd-unified-list">
    <header class="cockpit-header">
      <div class="cockpit-brand">
        <span class="cockpit-brand-mark"><Activity :size="25" /></span>
        <div><strong>智慧医院 SPD</strong><small>精益供应 · 智慧管理</small></div>
      </div>
      <label class="cockpit-month">
        <span>统计月份</span>
        <input v-model="selectedMonth" type="month" @change="loadCockpit" />
      </label>
      <div class="cockpit-title"><i></i><h1>全院耗材使用监控大屏</h1><i></i></div>
      <div class="cockpit-clock"><span>每日更新{{ data.statisticsDate ? ` · 数据日 ${data.statisticsDate}` : '' }}</span><strong>{{ clock }}</strong></div>
    </header>

    <!-- 请求失败统一由通用消息接口（Element Plus 消息弹层）抛出，
         不再在页面上方渲染内联错误条。 -->

    <!-- 顶部 KPI 卡已按产品方向移除；核心指标压缩为一行紧凑摘要，
         避免大屏顶部被大块卡片占满，同时保留关键数值。 -->
    <section class="cockpit-summary-strip" :aria-busy="loading" aria-label="驾驶舱核心指标">
      <span class="cockpit-summary-item">
        <Database :size="15" />
        <em>本月耗材金额</em>
        <strong>{{ formatWan(data.summary.currentAmount) }}<small>万元</small></strong>
      </span>
      <span class="cockpit-summary-item">
        <TrendingUp :size="15" />
        <em>环比增长</em>
        <strong :class="trendTone(data.summary.monthOnMonth)">{{ formatPercent(data.summary.monthOnMonth) }}</strong>
      </span>
      <span class="cockpit-summary-item">
        <Building2 :size="15" />
        <em>消耗科室</em>
        <strong>{{ data.summary.departmentCount }}<small>个</small></strong>
      </span>
      <span class="cockpit-summary-item">
        <BellRing :size="15" />
        <em>预警事项</em>
        <strong>{{ data.summary.warningCount }}<small>条</small></strong>
      </span>
    </section>

    <section class="cockpit-grid">
      <article class="cockpit-panel category-panel">
        <header><h2>耗材消费构成</h2><span>单位：万元</span></header>
        <div class="category-content">
          <div class="category-donut" :style="{ background: categoryGradient }"><div><strong>{{ formatWan(totalCategoryAmount) }}</strong><span>万元</span></div></div>
          <div class="category-legend">
            <div v-for="(item, index) in data.categories" :key="item.category" class="legend-item">
              <i :class="`color-${index}`"></i><span class="legend-label">{{ item.category }}</span><strong>{{ formatWan(item.currentAmount) }}</strong><small>{{ totalCategoryAmount ? (item.currentAmount / totalCategoryAmount * 100).toFixed(1) : '0.0' }}%</small>
            </div>
            <p v-if="!data.categories.length" class="cockpit-empty">暂无目录分类消耗数据</p>
          </div>
        </div>
      </article>

      <article class="cockpit-panel trend-panel">
        <header><h2>月度使用趋势</h2><span class="legend-line"><i></i>消耗金额</span></header>
        <div class="trend-chart">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="近十二个月耗材使用趋势">
            <defs><linearGradient id="cockpitTrendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--spd-p-success)" stop-opacity=".55"/><stop offset="1" stop-color="var(--spd-p-success)" stop-opacity="0"/></linearGradient></defs>
            <g class="trend-grid">
              <line v-for="line in [20, 44, 68, 92]" :key="line" x1="5" x2="95" :y1="line" :y2="line" />
            </g>
            <polygon :points="trendArea" fill="url(#cockpitTrendFill)" />
            <polyline :points="trendPolyline" />
            <circle v-for="point in trendPoints" :key="point.month" :cx="point.x" :cy="point.y" r="1.6"><title>{{ point.month }}：{{ formatWan(point.amount) }}万元</title></circle>
          </svg>
          <div class="trend-labels"><span v-for="item in data.trend" :key="item.month">{{ item.label }}</span></div>
        </div>
        <footer><span>近12月累计：<strong>{{ formatWan(data.trend.reduce((sum, item) => sum + Number(item.amount), 0)) }}</strong> 万元</span><span>当前统计月：{{ data.month }}</span></footer>
      </article>

      <article class="cockpit-panel department-panel">
        <header><h2>科室消耗金额 TOP 8</h2><span>单位：万元</span></header>
        <div class="ranking-head"><span>科室</span><span>本月</span><span>上月 / 环比</span></div>
        <div class="department-ranking">
          <div v-for="(item, index) in data.departments" :key="item.code" class="department-row">
            <span class="rank-no" :class="`rank-${Math.min(index + 1, 3)}`">{{ index + 1 }}</span>
            <strong :title="item.name">{{ item.name }}</strong>
            <div class="rank-bar"><i :style="{ width: `${Number(item.currentAmount) / maxDepartment * 100}%` }"></i></div>
            <span>{{ formatWan(item.currentAmount) }}</span><small>{{ formatWan(item.previousAmount) }}</small>
            <em :class="trendTone(comparisonRate(item.currentAmount, item.previousAmount))">{{ formatPercent(comparisonRate(item.currentAmount, item.previousAmount)) }}</em>
          </div>
          <p v-if="!data.departments.length" class="cockpit-empty">暂无科室消耗数据</p>
        </div>
      </article>

      <article class="cockpit-panel focus-panel">
        <header><h2>重点监控耗材使用排名</h2><span><i class="current-dot"></i>本月 <i class="previous-dot"></i>上月</span></header>
        <div class="focus-chart">
          <div v-for="item in data.focusedProducts" :key="item.code" class="focus-item">
            <div class="focus-bars">
              <i class="current" :style="{ height: `${Math.max(2, Number(item.currentAmount) / maxFocusedProduct * 100)}%` }"><span>{{ formatWan(item.currentAmount) }}</span></i>
              <i class="previous" :style="{ height: `${Math.max(2, Number(item.previousAmount) / maxFocusedProduct * 100)}%` }"></i>
            </div>
            <strong :title="item.name">{{ item.name }}</strong>
          </div>
          <p v-if="!data.focusedProducts.length" class="cockpit-empty">医院目录暂未勾选重点监控耗材，或统计月暂无消耗</p>
        </div>
      </article>

      <article class="cockpit-panel compare-panel">
        <header><h2>耗材分类对比</h2><span>单位：万元</span></header>
        <!-- 明细表改为标准统一列表：MdDataTableCard + el-table.ulc-table，
             支持列宽拖拽 / 列显隐设置 / 主题跟随。 -->
        <div class="cockpit-compare-card">
          <MdDataTableCard :loading="loading">
            <template #toolbar>
              <div class="cockpit-panel-switch">
                <button
                  type="button"
                  :class="{ 'is-active': cockpitTablePanel === 'department' }"
                  @click="cockpitTablePanel = 'department'"
                >科室排名</button>
                <button
                  type="button"
                  :class="{ 'is-active': cockpitTablePanel === 'category' }"
                  @click="cockpitTablePanel = 'category'"
                >分类对比</button>
              </div>
              <div class="ulc-col-settings">
                <SpdButton label="列设置" tone="neutral" ref="cockpitColSettingsAnchorRef"
                 
                 
                  @click="activeCockpitCols.settingsOpen.value = !activeCockpitCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
                <div
                  v-if="activeCockpitCols.settingsOpen.value"
                  ref="cockpitColSettingsPopRef"
                  class="ulc-col-settings-pop"
                  :class="{ 'is-align-right': cockpitColSettingsPop.align.value === 'right', 'is-align-up': cockpitColSettingsPop.vertical.value === 'up' }"
                  :style="cockpitColSettingsPop.popStyle.value"
                >
                  <header>
                    <strong>列展示设置</strong>
                    <SpdButton label="恢复默认" variant="text" tone="neutral" @click="activeCockpitCols.resetColumns()"></SpdButton>
                  </header>
                  <div class="ulc-col-settings-list">
                    <label v-for="col in activeCockpitCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                      <input
                        type="checkbox"
                        :checked="col.visible"
                        :disabled="col.visible && activeCockpitCols.visibleColumnCount.value <= 1"
                        @change="activeCockpitCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                      />
                      <span>{{ col.label }}</span>
                      <small>{{ col.width }}px</small>
                    </label>
                  </div>
                  <footer>
                    <SpdButton label="确定" tone="primary" @click="activeCockpitCols.settingsOpen.value = false"></SpdButton>
                  </footer>
                </div>
              </div>
            </template>
            <template #meta-extra>
              <span class="md-chip primary">共 {{ activeCockpitCols.visibleColumnCount.value }} 列</span>
            </template>

            <div class="ulc-table-wrap cockpit-table-wrap">
              <!-- 科室消耗排名 -->
              <el-table
                v-if="cockpitTablePanel === 'department'"
                ref="departmentTableRef"
                v-loading="loading"
                :data="data.departments"
                border
                stripe
                row-key="code"
                class="ulc-table is-sticky"
                max-height="220"
                style="width: 100%"
                empty-text="暂无科室消耗数据"
                @header-dragend="(width: number, oldWidth: number, column: any) => departmentCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="rank"
                  v-if="isCockpitColVisible('rank')"
                  label="排名"
                  align="center"
                  v-bind="cockpitColBinding('rank')"
                >
                  <template #header>
                    <span class="ulc-th-text">排名</span>
                  </template>
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column
                  column-key="name"
                  v-if="isCockpitColVisible('name')"
                  prop="name"
                  v-bind="cockpitColBinding('name')"
                >
                  <template #header>
                    <span class="ulc-th-text">科室</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-strong ulc-wrap-text">{{ row.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="currentAmount"
                  v-if="isCockpitColVisible('currentAmount')"
                  prop="currentAmount"
                  v-bind="cockpitColBinding('currentAmount')"
                >
                  <template #header>
                    <span class="ulc-th-text">本月金额（万元）</span>
                  </template>
                  <template #default="{ row }">{{ formatWan(row.currentAmount) }}</template>
                </el-table-column>
                <el-table-column
                  column-key="previousAmount"
                  v-if="isCockpitColVisible('previousAmount')"
                  prop="previousAmount"
                  v-bind="cockpitColBinding('previousAmount')"
                >
                  <template #header>
                    <span class="ulc-th-text">上月金额（万元）</span>
                  </template>
                  <template #default="{ row }">{{ formatWan(row.previousAmount) }}</template>
                </el-table-column>
                <el-table-column
                  column-key="monthOnMonth"
                  v-if="isCockpitColVisible('monthOnMonth')"
                  :fixed="cockpitActionFixed"
                  v-bind="cockpitColBinding('monthOnMonth')"
                >
                  <template #header>
                    <span class="ulc-th-text">环比</span>
                  </template>
                  <template #default="{ row }">
                    <em :class="trendTone(comparisonRate(row.currentAmount, row.previousAmount))">
                      {{ formatPercent(comparisonRate(row.currentAmount, row.previousAmount)) }}
                    </em>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 耗材分类对比 -->
              <el-table
                v-else
                ref="categoryTableRef"
                v-loading="loading"
                :data="data.categories"
                border
                stripe
                row-key="category"
                class="ulc-table is-sticky"
                max-height="220"
                style="width: 100%"
                empty-text="暂无目录分类消耗数据"
                @header-dragend="(width: number, oldWidth: number, column: any) => categoryCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="category"
                  v-if="isCockpitColVisible('category')"
                  prop="category"
                  v-bind="cockpitColBinding('category')"
                >
                  <template #header>
                    <span class="ulc-th-text">分类</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-strong ulc-wrap-text">{{ row.category }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="currentAmount"
                  v-if="isCockpitColVisible('currentAmount')"
                  prop="currentAmount"
                  v-bind="cockpitColBinding('currentAmount')"
                >
                  <template #header>
                    <span class="ulc-th-text">本月（万元）</span>
                  </template>
                  <template #default="{ row }">{{ formatWan(row.currentAmount) }}</template>
                </el-table-column>
                <el-table-column
                  column-key="previousAmount"
                  v-if="isCockpitColVisible('previousAmount')"
                  prop="previousAmount"
                  v-bind="cockpitColBinding('previousAmount')"
                >
                  <template #header>
                    <span class="ulc-th-text">上月（万元）</span>
                  </template>
                  <template #default="{ row }">{{ formatWan(row.previousAmount) }}</template>
                </el-table-column>
                <el-table-column
                  column-key="monthOnMonth"
                  v-if="isCockpitColVisible('monthOnMonth')"
                  :fixed="cockpitActionFixed"
                  v-bind="cockpitColBinding('monthOnMonth')"
                >
                  <template #header>
                    <span class="ulc-th-text">环比</span>
                  </template>
                  <template #default="{ row }">
                    <em :class="trendTone(row.monthOnMonth)">{{ formatPercent(row.monthOnMonth) }}</em>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </MdDataTableCard>
          <p class="caliber-note">口径：高值耗材；定数管理且收费；定数管理且不收费。</p>
        </div>
      </article>

      <article class="cockpit-panel alert-panel">
        <header><h2><AlertTriangle :size="18" />库存与异常提醒</h2><SpdButton label="刷新" :disabled="loading" @click="loadCockpit">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton></header>
        <div class="alert-list">
          <div v-for="item in data.alerts" :key="item.productCode" class="alert-row">
            <span class="alert-type">{{ item.alertType }}</span>
            <div class="alert-info">
              <strong>{{ item.productName }}</strong>
              <small>{{ item.productCode }} · 可用 {{ item.availableQty }} / 阈值 {{ item.threshold }}</small>
            </div>
          </div>
          <p v-if="!data.alerts.length" class="cockpit-empty">重点监控耗材暂无库存异常</p>
        </div>
        <footer><span><AlertTriangle :size="16" />库存不足 <strong>{{ data.summary.warningCount }}</strong> 条</span><small>数据来源：医院目录、库存余额、科室消耗</small></footer>
      </article>
    </section>
  </main>
</template>

<style scoped>
/* =====================================================================
   运营驾驶舱 OperationCockpitView · 科技风全屏
   ---------------------------------------------------------------------
   设计语言：
   - 配色：纯黑/深蓝底 + 霓虹渐变描边（青/蓝/紫）+ 玻璃拟态面板
   - 装饰：扫描线、角标、网格、粒子、辉光、渐变光柱
   - 排版：等宽数字 / 强字重 / 标题字距
   - 动效：呼吸光、扫描线、悬停抬升、徽章闪烁
   ===================================================================== */
.main-area:has(.operation-cockpit-page) { padding: 0; overflow: auto; background: var(--spd-p-bg-page); }
.main-area:has(.operation-cockpit-page) > .topbar { display: none; }

.operation-cockpit-page {
  --bg-base: var(--spd-p-bg-page);
  --bg-deep: var(--spd-p-bg-page);
  --neon-cyan: var(--spd-p-primary);
  --neon-blue: var(--spd-p-info);
  --neon-purple: var(--spd-p-primary-strong);
  --neon-pink: var(--spd-p-danger);
  --neon-amber: var(--spd-p-warning);
  --neon-green: var(--spd-p-success);
  --neon-red: var(--spd-p-danger);
  --panel-stroke: color-mix(in srgb, var(--spd-p-primary) 38%, transparent);
  --panel-stroke-soft: color-mix(in srgb, var(--spd-p-primary) 16%, transparent);
  --panel-bg: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-text-strong) 88%, transparent), color-mix(in srgb, var(--spd-p-bg-page) 78%, transparent));
  /* 本页面板底色已改为浅色（--spd-p-bg-page），
     因此正文主色必须用深色令牌。此前误取背景令牌 --spd-p-info-bg（#e9f1f5），
     导致「耗材消费构成」「心内科」等标题在浅底上几乎不可见（对比度 1.05:1 / 1.07:1）。 */
  --text-main: var(--spd-p-text-strong);
  --text-dim: var(--spd-p-text-muted);
  min-width: 1280px;
  min-height: 100vh;
  color: var(--text-main);
  background:
    radial-gradient(ellipse 80% 50% at 20% 0%, color-mix(in srgb, var(--spd-p-info) 22%, transparent), transparent 70%),
    radial-gradient(ellipse 60% 50% at 80% 100%, color-mix(in srgb, var(--spd-p-primary-strong) 18%, transparent), transparent 70%),
    radial-gradient(ellipse 50% 40% at 50% 50%, color-mix(in srgb, var(--spd-p-primary) 8%, transparent), transparent 70%),
    linear-gradient(180deg, var(--spd-p-bg-page) 0%, var(--spd-p-bg-page) 50%, var(--spd-p-bg-page) 100%);
  padding: 18px 22px 22px;
  font-family: var(--spd-font-family);
  position: relative;
  overflow: hidden;
}

/* 全屏网格底纹（科技风点阵 + 网格） */
.operation-cockpit-page::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(color-mix(in srgb, var(--spd-p-primary) 5%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--spd-p-primary) 5%, transparent) 1px, transparent 1px),
    radial-gradient(circle, color-mix(in srgb, var(--spd-p-primary) 12%, transparent) 1px, transparent 1.5px);
  background-size: 40px 40px, 40px 40px, 40px 40px;
  background-position: 0 0, 0 0, 0 0;
  mask-image: radial-gradient(ellipse 90% 70% at 50% 50%, black 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 90% 70% at 50% 50%, black 30%, transparent 100%);
  z-index: 0;
}

/* 顶部扫描线 */
.operation-cockpit-page::after {
  content: "";
  position: absolute;
  left: 0; right: 0; top: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--neon-cyan), transparent);
  box-shadow: var(--spd-p-shadow-3);
  animation: cockpit-scan 6s linear infinite;
  z-index: 1;
}
@keyframes cockpit-scan {
  0% { transform: translateY(0); opacity: 1; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(100vh); opacity: 0; }
}

.operation-cockpit-page > * { position: relative; z-index: 2; }

/* ---------- 顶部条 ---------- */
.cockpit-header {
  display: grid;
  grid-template-columns: 260px 230px minmax(380px, 1fr) 240px;
  align-items: center;
  gap: 18px;
  min-height: 72px;
  padding: 0 6px;
}
.cockpit-brand { display: flex; align-items: center; gap: 14px; }
.cockpit-brand-mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 12px;
  color: var(--spd-p-bg-page);
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: var(--spd-p-shadow-3);
  transition: transform .35s cubic-bezier(.4, 0, .2, 1);
}
.cockpit-brand-mark::before {
  content: "";
  position: absolute;
  inset: -3px;
  border-radius: 14px;
  background: conic-gradient(from 0deg, var(--neon-cyan), var(--neon-blue), var(--neon-purple), var(--neon-cyan));
  filter: blur(8px);
  opacity: .55;
  z-index: -1;
  animation: cockpit-spin 8s linear infinite;
}
@keyframes cockpit-spin { to { transform: rotate(360deg); } }
.cockpit-brand:hover .cockpit-brand-mark { transform: rotate(-12deg) scale(1.06); }
.cockpit-brand div { display: grid; gap: 4px; }
.cockpit-brand strong {
  font-size: var(--spd-font-size-lg);
  letter-spacing: .08em;
  background: linear-gradient(90deg, var(--spd-p-bg), var(--neon-cyan));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 800;
}
.cockpit-brand small { color: var(--text-dim); font-size: var(--spd-font-size-xs); letter-spacing: .14em; }

.cockpit-month {
  display: flex;
  align-items: center;
  height: 42px;
  border: 1px solid var(--panel-stroke);
  border-radius: 6px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--spd-p-primary) 6%, transparent), color-mix(in srgb, var(--spd-p-primary) 2%, transparent));
  position: relative;
  overflow: hidden;
  transition: border-color .2s, box-shadow .2s;
}
.cockpit-month::before,
.cockpit-month::after {
  content: "";
  position: absolute;
  width: 10px; height: 10px;
  border: 2px solid var(--neon-cyan);
  opacity: .9;
}
.cockpit-month::before { top: -1px; left: -1px; border-right: 0; border-bottom: 0; }
.cockpit-month::after { bottom: -1px; right: -1px; border-left: 0; border-top: 0; }
.cockpit-month:focus-within { border-color: var(--neon-cyan); box-shadow: var(--spd-p-shadow-3); }
.cockpit-month span { padding: 0 14px; color: var(--neon-cyan); font-size: var(--spd-font-size-sm); letter-spacing: .12em; font-weight: 700; }
.cockpit-month input {
  width: 140px;
  height: 100%;
  border: 0;
  border-left: 1px solid var(--panel-stroke-soft);
  color: var(--spd-p-text-strong);
  background: transparent;
  padding: 0 12px;
  color-scheme: dark;
  font: inherit;
  font-weight: 600;
  letter-spacing: .04em;
}

.cockpit-title {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 18px;
  text-align: center;
  position: relative;
}
.cockpit-title h1 {
  margin: 0;
  /* M-15 驾驶舱主标题：--spd-font-size-5xl（38/40/42px），跟随字号档位 */
  font-size: var(--spd-font-size-5xl);
  letter-spacing: .22em;
  font-weight: 800;
  background: linear-gradient(180deg, var(--spd-p-bg) 0%, var(--neon-cyan) 60%, var(--neon-blue) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 26px color-mix(in srgb, var(--spd-p-primary) 35%, transparent);
  position: relative;
  padding: 0 4px;
}
.cockpit-title h1::before,
.cockpit-title h1::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  border: 2px solid var(--neon-cyan);
  transform: translateY(-50%) rotate(45deg);
  box-shadow: 0 0 12px var(--neon-cyan);
}
.cockpit-title h1::before { left: -22px; }
.cockpit-title h1::after { right: -22px; }
.cockpit-title i {
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--neon-cyan), var(--neon-blue), var(--neon-cyan), transparent);
  box-shadow: var(--spd-p-shadow-2);
}

.cockpit-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.cockpit-clock span { color: var(--neon-cyan); font-size: var(--spd-font-size-xs); letter-spacing: .18em; }
.cockpit-clock strong {
  font-size: var(--spd-font-size-4xl);
  font-variant-numeric: tabular-nums;
  color: var(--spd-p-text-strong);
  text-shadow: 0 0 12px var(--neon-cyan), 0 0 24px color-mix(in srgb, var(--spd-p-primary) 55%, transparent);
  letter-spacing: .08em;
  font-weight: 700;
  line-height: 1;
}

.cockpit-error {
  margin: 8px 0 0;
  border: 1px solid color-mix(in srgb, var(--spd-p-danger) 55%, transparent);
  color: var(--spd-p-danger-text);
  background: linear-gradient(90deg, color-mix(in srgb, var(--spd-p-danger) 14%, transparent), color-mix(in srgb, var(--spd-p-danger) 4%, transparent));
  border-radius: 4px;
  padding: 9px 16px;
  font-size: var(--spd-font-size-sm);
  letter-spacing: .04em;
  position: relative;
}
.cockpit-error::before {
  content: "";
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--neon-red);
  box-shadow: 0 0 12px var(--neon-red);
}

/* ---------- 顶部核心指标摘要条 ----------
   顶部 KPI 大卡已按产品方向移除；核心指标压缩为一行紧凑摘要条，
   既保留关键数值，又不占用大屏顶部空间。 */
.cockpit-summary-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 26px;
  margin: 10px 0;
  padding: 8px 16px;
  border: 1px solid var(--panel-stroke-soft);
  border-radius: 6px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--spd-p-primary) 8%, transparent), color-mix(in srgb, var(--spd-p-bg-page) 55%, transparent) 60%, transparent);
}
.cockpit-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--neon-cyan);
  white-space: nowrap;
}
.cockpit-summary-item em {
  color: var(--text-dim);
  font-size: var(--spd-font-size-sm);
  font-style: normal;
  letter-spacing: .12em;
}
.cockpit-summary-item strong {
  color: var(--spd-p-text-strong);
  font-size: var(--spd-font-size-lg);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: .04em;
  text-shadow: 0 0 12px currentColor;
}
.cockpit-summary-item strong small {
  margin-left: 4px;
  color: var(--text-dim);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: .04em;
}

.operation-cockpit-page .up { color: var(--neon-red) !important; }
.operation-cockpit-page .down { color: var(--neon-green) !important; }
.operation-cockpit-page .flat { color: var(--text-dim) !important; }

/* ---------- 6 宫格面板（玻璃拟态 + 角标） ---------- */
.cockpit-grid {
  display: grid;
  grid-template-columns: 1.05fr 1.4fr 1.08fr;
  grid-template-rows: 348px 332px;
  gap: 14px;
}
.cockpit-panel {
  position: relative;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--panel-stroke);
  border-radius: 4px;
  background-color: var(--spd-p-bg-page) !important;
  background-image: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-primary) 6%, transparent), color-mix(in srgb, var(--spd-p-bg-page) 82%, transparent)) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  box-shadow: var(--spd-p-shadow-3);
}
.cockpit-panel::before,
.cockpit-panel::after {
  content: "";
  position: absolute;
  width: 16px; height: 16px;
  border: 2px solid var(--neon-cyan);
  pointer-events: none;
  z-index: 3;
}
.cockpit-panel::before { top: -1px; left: -1px; border-right: 0; border-bottom: 0; box-shadow: 0 0 8px var(--neon-cyan); }
.cockpit-panel::after { bottom: -1px; right: -1px; border-left: 0; border-top: 0; box-shadow: 0 0 8px var(--neon-cyan); }
.cockpit-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  flex: 0 0 auto;
  border-bottom: 1px solid var(--panel-stroke-soft);
  padding: 0 18px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--spd-p-primary) 12%, transparent), transparent 70%);
  position: relative;
}
.cockpit-panel > header::before {
  content: "";
  position: absolute;
  bottom: -1px; left: 0;
  width: 56px; height: 2px;
  background: linear-gradient(90deg, var(--neon-cyan), transparent);
  box-shadow: 0 0 10px var(--neon-cyan);
}
.cockpit-panel h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: var(--spd-font-size-lg);
  letter-spacing: .12em;
  /* 面板标题：面板底色已改为浅色（--spd-p-bg-page），
     不能用「主色底上的文字」令牌 --spd-p-text-on-primary（白色），
     否则标题在浅底上对比度仅 1.05:1、几乎不可见。改用深色正文令牌。 */
  color: var(--spd-p-text-strong);
  font-weight: 700;
}
.cockpit-panel h2::before {
  content: "";
  width: 4px; height: 16px;
  background: linear-gradient(180deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: 0 0 8px var(--neon-cyan);
  border-radius: 1px;
}
.cockpit-panel > header > span { color: var(--text-dim); font-size: var(--spd-font-size-sm); letter-spacing: .08em; }

/* 耗材消费构成 */
.category-content {
  display: grid;
  grid-template-columns: 46% 1fr;
  align-items: center;
  flex: 1;
  min-height: 0;
  padding: 20px 18px;
  gap: 18px;
}
.category-donut {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  max-width: 210px;
  margin: auto;
  border-radius: 50%;
  filter: drop-shadow(0 0 18px color-mix(in srgb, var(--spd-p-primary) 35%, transparent));
}
.category-donut::before {
  content: "";
  position: absolute;
  inset: -8px;
  border-radius: 50%;
  border: 1px dashed var(--panel-stroke-soft);
  animation: cockpit-spin 22s linear infinite;
}
.category-donut > div {
  display: grid;
  place-items: center;
  width: 60%;
  height: 60%;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, color-mix(in srgb, var(--spd-p-bg-page) 90%, transparent), color-mix(in srgb, var(--spd-p-bg-page) 65%, transparent));
  box-shadow: var(--spd-p-shadow-2);
}
.category-donut strong {
  font-size: var(--spd-font-size-2xl);
  font-weight: 800;
  color: var(--spd-p-text-strong);
  text-shadow: 0 0 12px var(--neon-cyan);
  font-variant-numeric: tabular-nums;
}
.category-donut span { color: var(--text-dim); font-size: var(--spd-font-size-xs); letter-spacing: .12em; }
.category-legend { display: grid; gap: 12px; align-content: center; }
.category-legend .legend-item {
  display: grid;
  grid-template-columns: 12px 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--panel-stroke-soft);
}
.category-legend .legend-item:last-child { border-bottom: 0; }
.category-legend i { width: 10px; height: 10px; border-radius: 50%; box-shadow: 0 0 8px currentColor; }
.category-legend .color-0 { background: var(--neon-blue); color: var(--neon-blue); }
.category-legend .color-1 { background: var(--neon-cyan); color: var(--neon-cyan); }
.category-legend .color-2 { background: var(--neon-green); color: var(--neon-green); }
.category-legend .legend-label { font-size: var(--spd-font-size-sm); color: var(--text-main); letter-spacing: .04em; }
.category-legend strong { font-size: var(--spd-font-size-base); font-variant-numeric: tabular-nums; color: var(--spd-p-text-strong); }
.category-legend small { grid-column: 3 / 5; color: var(--text-dim); text-align: right; font-size: var(--spd-font-size-sm); font-variant-numeric: tabular-nums; }

/* 月度趋势 */
.trend-chart { height: 236px; padding: 18px 18px 0; display: flex; flex-direction: column; min-height: 0; }
.trend-chart svg { width: 100%; flex: 1; min-height: 0; overflow: visible; }
.trend-grid line { stroke: color-mix(in srgb, var(--spd-p-primary) 18%, transparent); stroke-width: .35; stroke-dasharray: 2 2; }
.trend-chart polyline {
  fill: none;
  stroke: var(--neon-cyan);
  stroke-width: 1.6;
  vector-effect: non-scaling-stroke;
  filter: drop-shadow(0 0 6px var(--neon-cyan));
}
.trend-chart circle { fill: var(--neon-cyan); stroke: var(--bg-deep); stroke-width: .6; filter: drop-shadow(0 0 4px var(--neon-cyan)); }
/* M-18 趋势轴标签：承载刻度读数，按 G-9 提升到 --spd-font-size-sm（13px） */
.trend-labels { display: grid; grid-template-columns: repeat(12, 1fr); color: var(--text-dim); font-size: var(--spd-font-size-sm); text-align: center; padding-top: 6px; letter-spacing: .04em; }
.trend-panel footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--panel-stroke-soft);
  padding: 10px 18px;
  color: var(--text-dim);
  font-size: var(--spd-font-size-sm);
  letter-spacing: .08em;
  flex: 0 0 auto;
}
.trend-panel footer strong { color: var(--neon-cyan); text-shadow: 0 0 8px var(--neon-cyan); font-weight: 700; }
.legend-line { display: flex; align-items: center; gap: 8px; }
.legend-line i { width: 22px; height: 2px; background: var(--neon-cyan); box-shadow: 0 0 8px var(--neon-cyan); }

/* 科室 TOP 8 */
.ranking-head {
  display: grid;
  grid-template-columns: 1fr 70px 90px;
  gap: 8px;
  padding: 10px 14px 6px 42px;
  color: var(--text-dim);
  font-size: var(--spd-font-size-sm);
  letter-spacing: .14em;
  flex: 0 0 auto;
}
.department-ranking { display: grid; gap: 9px; padding: 6px 14px 12px; overflow: auto; align-content: start; }
.department-row {
  display: grid;
  grid-template-columns: 22px 84px minmax(70px, 1fr) 56px 50px 60px;
  align-items: center;
  gap: 8px;
  font-size: var(--spd-font-size-sm);
  padding: 3px 0;
  position: relative;
}
.department-row::before {
  content: "";
  position: absolute;
  left: 0; right: 0; bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--panel-stroke-soft), transparent);
}
.rank-no {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  color: var(--spd-p-bg-page);
  background: var(--neon-cyan);
  font-weight: 800;
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
  box-shadow: 0 0 10px var(--neon-cyan);
}
.rank-no.rank-1 {
  background: linear-gradient(135deg, var(--spd-p-warning-border), var(--neon-amber));
  color: var(--spd-p-warning-text);
  box-shadow: var(--spd-p-shadow-2);
}
.rank-no.rank-2 {
  background: linear-gradient(135deg, var(--spd-p-info-bg), var(--spd-p-info));
  /* 名次徽标文字：深色（此前误用浅色底令牌作文字，对比度仅 1.12:1） */
  color: var(--spd-p-text-strong);
  box-shadow: var(--spd-p-shadow-2);
}
.rank-no.rank-3 {
  background: linear-gradient(135deg, var(--spd-p-warning-border), var(--spd-p-warning));
  color: var(--spd-p-warning-text);
  box-shadow: var(--spd-p-shadow-2);
}
.department-row > strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-main); }
.rank-bar {
  position: relative;
  height: 14px;
  background: color-mix(in srgb, var(--spd-p-primary) 8%, transparent);
  border: 1px solid var(--panel-stroke-soft);
  border-radius: 2px;
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 0 1px 1px 0;
  background: linear-gradient(90deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: var(--spd-p-shadow-2);
  position: relative;
}
.rank-bar i::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 30%, color-mix(in srgb, var(--spd-p-bg) 40%, transparent) 50%, transparent 70%);
  animation: cockpit-shine 2.5s linear infinite;
}
@keyframes cockpit-shine { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }
.department-row > span, .department-row > small, .department-row > em { font-variant-numeric: tabular-nums; text-align: right; }
/* <small> 的 UA 默认 font-size: smaller 会让上月金额跌到 10px，
   低于全站 12px 下限 —— 显式覆盖为辅助层级字号。 */
.department-row > small { color: var(--text-dim); font-size: var(--spd-font-size-xs); }
.department-row > em { font-style: normal; font-weight: 700; font-size: var(--spd-font-size-sm); }

/* 重点监控耗材 */
.focus-panel { grid-column: 1 / 2; }
.focus-panel > header > span { display: flex; align-items: center; gap: 8px; }
.current-dot, .previous-dot { width: 10px; height: 10px; border-radius: 2px; }
.current-dot { background: var(--neon-cyan); box-shadow: 0 0 10px var(--neon-cyan); }
.previous-dot { margin-left: 10px; background: var(--neon-blue); box-shadow: 0 0 10px var(--neon-blue); }
.focus-chart { display: flex; align-items: flex-end; gap: 14px; height: 280px; padding: 36px 18px 12px; flex: 1; min-height: 0; }
.focus-item { display: grid; grid-template-rows: 1fr 36px; flex: 1; min-width: 0; align-items: end; text-align: center; }
.focus-bars { display: flex; align-items: flex-end; justify-content: center; gap: 5px; height: 100%; border-bottom: 1px solid var(--panel-stroke-soft); position: relative; }
.focus-bars::before {
  content: "";
  position: absolute;
  left: 0; right: 0; top: 0; bottom: 0;
  background: linear-gradient(180deg, transparent 0%, color-mix(in srgb, var(--spd-p-primary) 3%, transparent) 50%, transparent 100%);
  pointer-events: none;
}
.focus-bars i { position: relative; width: 36%; min-height: 4px; border-radius: 2px 2px 0 0; transition: filter .2s; }
.focus-bars .current { background: linear-gradient(180deg, var(--neon-cyan), var(--spd-p-info-strong)); box-shadow: var(--spd-p-shadow-2); }
.focus-bars .previous { background: linear-gradient(180deg, var(--neon-blue), var(--spd-p-info-strong)); }
.focus-bars .current span {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  /* 图表数据标签 —— 对应 --spd-chart-font-lg（标准文档 5.4） */
  font-size: var(--spd-chart-font-lg);
  font-weight: 800;
  color: var(--neon-cyan);
  background: color-mix(in srgb, var(--spd-p-bg-page) 85%, transparent);
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  border: 1px solid var(--panel-stroke-soft);
  text-shadow: 0 0 6px var(--neon-cyan);
}
.focus-item > strong { overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-size: var(--spd-font-size-sm); color: var(--text-main); letter-spacing: .04em; }

/* 耗材分类对比 —— 改为标准统一列表后，通用表格外观由 unified-list.css 提供；
   这里只做驾驶舱深色主题下的暗色化覆盖 + 面板切换按钮样式。 */
.compare-panel { grid-column: 2 / 4; }
.cockpit-compare-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  padding: 10px 14px 0;
}
.cockpit-compare-card :deep(.md-table-card) {
  background: transparent;
  border: 0;
  box-shadow: none;
}
.cockpit-compare-card :deep(.md-table-toolbar) {
  background: transparent;
  border-bottom: 1px solid var(--panel-stroke-soft);
  padding: 0 0 8px;
}
.cockpit-compare-card :deep(.md-table-toolbar-main) {
  gap: 8px;
}
.cockpit-compare-card :deep(.ulc-table-wrap) {
  background: transparent;
  border-color: var(--panel-stroke-soft);
  box-shadow: none;
}
/* 深色主题下的表格底色：让单元格与玻璃拟态面板融为一体 */
.cockpit-compare-card :deep(.el-table),
.cockpit-compare-card :deep(.el-table__inner-wrapper),
.cockpit-compare-card :deep(.el-table__body-wrapper) {
  background: transparent;
}
.cockpit-compare-card :deep(.el-table tr),
.cockpit-compare-card :deep(.el-table td.el-table__cell) {
  background: transparent;
}
.cockpit-compare-card :deep(.el-table__row--striped td.el-table__cell) {
  background: color-mix(in srgb, var(--spd-p-primary) 4%, transparent) !important;
}
.cockpit-compare-card :deep(.el-table__row:hover > td.el-table__cell) {
  background: color-mix(in srgb, var(--spd-p-primary) 7%, transparent) !important;
}
.cockpit-compare-card :deep(.el-table td.el-table__cell) {
  color: var(--text-main);
  border-color: var(--panel-stroke-soft) !important;
}
.cockpit-compare-card :deep(.ulc-strong) {
  color: var(--text-main);
}
.cockpit-compare-card :deep(.el-table__empty-text) {
  color: var(--text-dim);
}
/* 列设置浮层在深色大屏上需要暗色底 */
.cockpit-compare-card :deep(.ulc-col-settings-pop) {
  background: color-mix(in srgb, var(--spd-p-bg-page) 96%, transparent);
  border-color: var(--panel-stroke);
  color: var(--text-main);
}
.cockpit-compare-card :deep(.ulc-col-settings-pop header strong),
.cockpit-compare-card :deep(.ulc-col-settings-item span) {
  color: var(--text-main);
}
.cockpit-compare-card :deep(.ulc-col-settings-pop .ulc-link) {
  color: var(--neon-cyan);
}
/* 工具栏按钮在深色大屏上的适配
   ------------------------------------------------------------------
   驾驶舱是整屏深色科技风，但工具栏按钮仍需与其它列表页「同款」，
   否则用户会误以为是另一类控件。
   这里不用霓虹青，而是沿用标准的主色令牌（--spd-c-*），
   仅把底色压暗一档以适配深色背景，保持浅底 + 深字（或亮底 + 深字）的
   标准按钮语义不变。 */
.cockpit-compare-card :deep(.md-btn) {
  border-color: var(--spd-c-primary-strong);
  background: color-mix(in srgb, var(--spd-c-primary) 18%, transparent);
  color: var(--spd-c-primary-strong);
  letter-spacing: .08em;
}
.cockpit-compare-card :deep(.md-btn:hover:not(:disabled)) {
  background: color-mix(in srgb, var(--spd-c-primary) 32%, transparent);
  border-color: var(--spd-c-primary-strong);
  color: var(--spd-c-primary-strong);
}

/* 面板切换按钮（科室排名 / 分类对比） */
.cockpit-panel-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--panel-stroke-soft);
  border-radius: 4px;
  background: color-mix(in srgb, var(--spd-p-primary) 5%, transparent);
}
.cockpit-panel-switch button {
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--text-dim);
  padding: 5px 12px;
  font: inherit;
  font-size: var(--spd-font-size-sm);
  letter-spacing: .08em;
  cursor: pointer;
  transition: background .2s, color .2s, box-shadow .2s;
}
.cockpit-panel-switch button:hover { color: var(--neon-cyan); }
.cockpit-panel-switch button.is-active {
  color: var(--spd-p-bg-page);
  background: var(--neon-cyan);
  box-shadow: var(--spd-p-shadow-2);
  font-weight: 700;
}

/* M-19 口径说明：承载统计口径定义，按 G-9 提升到 --spd-font-size-sm（13px） */
.caliber-note { margin: 10px 16px 12px; color: var(--text-dim); font-size: var(--spd-font-size-sm); letter-spacing: .08em; }

/* 库存与异常提醒 */
.alert-panel > header button {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--panel-stroke);
  border-radius: 4px;
  color: var(--neon-cyan);
  background: color-mix(in srgb, var(--spd-p-primary) 6%, transparent);
  padding: 5px 12px;
  font: inherit;
  font-size: var(--spd-font-size-sm);
  letter-spacing: .1em;
  cursor: pointer;
  transition: background .2s, border-color .2s, box-shadow .2s;
}
.alert-panel > header button:hover:not(:disabled) {
  background: color-mix(in srgb, var(--spd-p-primary) 16%, transparent);
  border-color: var(--neon-cyan);
  box-shadow: var(--spd-p-shadow-2);
}
.alert-panel > header button:disabled { opacity: .5; cursor: not-allowed; }
.alert-list { display: grid; gap: 8px; padding: 12px 14px; height: calc(100% - 92px); overflow: auto; align-content: start; }
.alert-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--panel-stroke-soft);
  padding: 8px 0;
}
.alert-type {
  border-radius: 3px;
  color: var(--neon-red);
  background: color-mix(in srgb, var(--spd-p-danger) 14%, transparent);
  padding: 5px 8px;
  /* D-06：预警条目风险等级标记 —— 承载用户需读取判断的等级语义，
     按 G-9 / L-7 提升到 --spd-font-size-sm（13/15/17px），不再使用 xs。 */
  font-size: var(--spd-font-size-sm);
  text-align: center;
  font-weight: 700;
  letter-spacing: .12em;
  border: 1px solid color-mix(in srgb, var(--spd-p-danger) 45%, transparent);
  text-shadow: 0 0 6px color-mix(in srgb, var(--spd-p-danger) 45%, transparent);
}
.alert-info { display: grid; gap: 4px; min-width: 0; }
.alert-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--spd-font-size-sm); color: var(--text-main); }
.alert-info small { color: var(--text-dim); font-size: var(--spd-font-size-xs); font-variant-numeric: tabular-nums; }
.alert-panel > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--panel-stroke-soft);
  padding: 10px 14px;
  color: var(--text-dim);
  font-size: var(--spd-font-size-xs);
  letter-spacing: .12em;
  flex: 0 0 auto;
}
.alert-panel > footer span { display: flex; align-items: center; gap: 6px; }
.alert-panel > footer strong { color: var(--neon-red); font-weight: 800; text-shadow: 0 0 6px color-mix(in srgb, var(--spd-p-danger) 50%, transparent); }
/* <small> 的 UA 默认 font-size: smaller 会把 11px 再缩到 9.17px，
   低于全站 12px 下限 —— 显式覆盖为辅助层级字号。 */
.alert-panel > footer small { font-size: var(--spd-font-size-xs); }

.cockpit-empty {
  grid-column: 1 / -1;
  align-self: center;
  margin: auto;
  color: var(--text-dim);
  text-align: center;
  font-size: var(--spd-font-size-sm);
  letter-spacing: .14em;
  padding: 18px 0;
}

@media (max-width: 1400px) {
  .operation-cockpit-page { min-width: 1080px; padding: 14px; }
  .cockpit-header { grid-template-columns: 220px 210px 1fr 200px; }
  .cockpit-grid { grid-template-columns: 1fr 1.28fr 1.05fr; }
  .department-row { grid-template-columns: 20px 70px minmax(55px, 1fr) 50px 46px 54px; gap: 5px; }
  /* M-15 窄屏降档：改用 -4xl 令牌，仍跟随字号档位 */
  .cockpit-title h1 { font-size: var(--spd-font-size-4xl); }
  .kpi-body strong { font-size: var(--spd-font-size-4xl); }
}

/* 暗色滚动条 */
.operation-cockpit-page ::-webkit-scrollbar { width: 8px; height: 8px; }
.operation-cockpit-page ::-webkit-scrollbar-track { background: color-mix(in srgb, var(--spd-p-primary) 4%, transparent); }
.operation-cockpit-page ::-webkit-scrollbar-thumb { background: linear-gradient(180deg, var(--neon-cyan), var(--neon-blue)); border-radius: 4px; box-shadow: var(--spd-p-shadow-1); }
.operation-cockpit-page ::-webkit-scrollbar-thumb:hover { background: linear-gradient(180deg, var(--neon-cyan), var(--neon-purple)); }

/* Element Plus tooltip 暗色化（环形图 hover 显示的"高值/定数..."） */
.operation-cockpit-page .el-popper,
.operation-cockpit-page .el-tooltip__popper,
.operation-cockpit-page .el-tooltip-popper {
  background: color-mix(in srgb, var(--spd-p-bg-page) 92%, transparent) !important;
  color: var(--text-main) !important;
  border: 1px solid var(--panel-stroke) !important;
  box-shadow: var(--spd-p-shadow-2);
  backdrop-filter: blur(6px);
}
.operation-cockpit-page .el-popper .el-popper__arrow::before,
.operation-cockpit-page .el-tooltip__popper .el-popper__arrow::before {
  background: color-mix(in srgb, var(--spd-p-bg-page) 92%, transparent) !important;
  border-color: var(--panel-stroke) !important;
}

/* 选区颜色 */
.operation-cockpit-page ::selection { background: color-mix(in srgb, var(--spd-p-primary) 35%, transparent); color: var(--spd-p-text-on-primary); }
</style>
