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
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'

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

/**
 * 耗材消费构成 · 分类配色
 * ---------------------------------------------------------------------
 * 原实现用固定三项色表（info / success / success）：
 *   1) 分类数量超过 3 项时，第 4 项起取到 undefined，
 *      conic-gradient 直接失效 —— 图表整块塌掉；
 *   2) 第 1、2 项共用同一个绿，图例圆点与扇形颜色对不上；
 *   3) 蓝 / 绿 / 青三色互相打架，与全站「青玉主色 + 语义色」的
 *      配色语言不一致。
 *
 * 现在改为：按主色青玉 + 语义色板循环取色，
 * 调色板长度与分类数量无关（超过板长自动循环），
 * 且**图例圆点与扇形共用同一个取色函数**，颜色永远对得上。
 * 全部取自设计令牌，不写死 hex。
 */
const CATEGORY_SERIES = [
  'var(--spd-p-primary)',
  'var(--spd-p-info)',
  'var(--spd-p-success)',
  'var(--spd-p-warning)',
  'var(--spd-p-slate)',
  'var(--spd-p-danger)'
] as const

/** 分类序号 → 配色（与图例圆点共用，保证一致） */
function categoryColor(index: number) {
  return CATEGORY_SERIES[index % CATEGORY_SERIES.length]
}

const categoryGradient = computed(() => {
  const total = Math.max(1, totalCategoryAmount.value)
  let cursor = 0
  const stops = data.value.categories.map((item, index) => {
    const start = cursor
    cursor += Number(item.currentAmount || 0) / total * 100
    return `${categoryColor(index)} ${start}% ${cursor}%`
  })
  if (!stops.length) return 'var(--spd-p-bg-muted)'
  return `conic-gradient(${stops.join(',')})`
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

/**
 * 环比走向 → 语义色调（全站统一词汇：success / warning / danger / info / neutral）
 * ---------------------------------------------------------------------
 * 旧实现把「上升」标成红色、「下降」标成绿色，
 * 颜色方向与业务含义相反（耗材消耗上升并不是异常），
 * 且整套大屏上红绿对撞，观感刺眼。
 *
 * 现在按「变化幅度」而非「方向」定调：
 *   - 基本持平（±2% 以内） → neutral（不制造噪声）
 *   - 明显上升（> +2%）     → warning（需要关注，但不是错误）
 *   - 明显下降（< -2%）     → success（消耗回落是正向结果）
 *   - 其余边界值            → info
 * 颜色全部由 SpdStatusChip / 令牌渲染，浅底深字，三套方案自动适配。
 */
const TREND_FLAT_THRESHOLD = 2
function trendTone(value: number): 'up' | 'down' | 'flat' {
  if (value > TREND_FLAT_THRESHOLD) return 'up'
  if (value < -TREND_FLAT_THRESHOLD) return 'down'
  return 'flat'
}
/** 环比走向 → SpdStatusChip 语义色调 */
function trendChipTone(value: number): SpdStatusChipTone {
  const tone = trendTone(value)
  if (tone === 'up') return 'warning'
  if (tone === 'down') return 'success'
  return 'neutral'
}

/**
 * 库存预警条目 → SpdStatusChip 语义色调。
 * 后端 alertType 形如「库存不足 / 低于阈值」，
 * 统一按 danger 呈现（预警本身就是需要处理的事项）。
 */
function alertTone(alertType: unknown): SpdStatusChipTone {
  const text = String(alertType ?? '').trim()
  if (!text) return 'neutral'
  if (/不足|低于|超限|过期|异常|告警|缺货/.test(text)) return 'danger'
  if (/预警|关注|临期/.test(text)) return 'warning'
  return 'info'
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
        <!-- 环比读数走全站通用状态条（浅底深字），
             不再用「红=上升 / 绿=下降」的自定义配色。 -->
        <SpdStatusChip :tone="trendChipTone(data.summary.monthOnMonth)" size="sm">
          {{ formatPercent(data.summary.monthOnMonth) }}
        </SpdStatusChip>
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
              <!-- 图例圆点与扇形共用同一个取色函数，颜色永远对得上 -->
              <i :style="{ background: categoryColor(index) }"></i><span class="legend-label">{{ item.category }}</span><strong>{{ formatWan(item.currentAmount) }}</strong><small>{{ totalCategoryAmount ? (item.currentAmount / totalCategoryAmount * 100).toFixed(1) : '0.0' }}%</small>
            </div>
            <p v-if="!data.categories.length" class="cockpit-empty">暂无目录分类消耗数据</p>
          </div>
        </div>
      </article>

      <article class="cockpit-panel trend-panel">
        <header><h2>月度使用趋势</h2><span class="legend-line"><i></i>消耗金额</span></header>
        <div class="trend-chart">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="近十二个月耗材使用趋势">
            <defs><linearGradient id="cockpitTrendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--spd-p-primary)" stop-opacity=".22"/><stop offset="1" stop-color="var(--spd-p-primary)" stop-opacity="0"/></linearGradient></defs>
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
            <span class="rank-no" :class="`rank-${Math.min(index + 1, 4)}`">{{ index + 1 }}</span>
            <strong :title="item.name">{{ item.name }}</strong>
            <div class="rank-bar"><i :style="{ width: `${Number(item.currentAmount) / maxDepartment * 100}%` }"></i></div>
            <span>{{ formatWan(item.currentAmount) }}</span><small>{{ formatWan(item.previousAmount) }}</small>
            <SpdStatusChip :tone="trendChipTone(comparisonRate(item.currentAmount, item.previousAmount))" size="sm">
              {{ formatPercent(comparisonRate(item.currentAmount, item.previousAmount)) }}
            </SpdStatusChip>
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
                <!-- 面板切换也走全站唯一按钮组件 SpdButton，
                     不再自绘原生 <button>（避免出现第二套按钮样式）。 -->
                <SpdButton label="科室排名" tone="primary" variant="ghost"
                  :class="{ 'is-active': cockpitTablePanel === 'department' }"
                  @click="cockpitTablePanel = 'department'" />
                <SpdButton label="分类对比" tone="primary" variant="ghost"
                  :class="{ 'is-active': cockpitTablePanel === 'category' }"
                  @click="cockpitTablePanel = 'category'" />
              </div>
              <div class="ulc-col-settings">
                <SpdButton label="列设置" tone="neutral" ref="cockpitColSettingsAnchorRef"
                 
                 
                  @click="activeCockpitCols.settingsOpen.value = !activeCockpitCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
                <SpdColumnSettings
v-if="activeCockpitCols.settingsOpen.value"
ref="cockpitColSettingsPopRef"
:class="{ 'is-align-right': cockpitColSettingsPop.align.value === 'right', 'is-align-up': cockpitColSettingsPop.vertical.value === 'up' }"
:style="cockpitColSettingsPop.popStyle.value"
:columns="activeCockpitCols.columns.value"
:visible-column-count="activeCockpitCols.visibleColumnCount.value"
@toggle="activeCockpitCols.setColumnVisible"
@move="activeCockpitCols.moveColumn"
@reset="activeCockpitCols.resetColumns()"
@confirm="activeCockpitCols.settingsOpen.value = false"
          />
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
                    <SpdStatusChip :tone="trendChipTone(comparisonRate(row.currentAmount, row.previousAmount))" size="sm">
                      {{ formatPercent(comparisonRate(row.currentAmount, row.previousAmount)) }}
                    </SpdStatusChip>
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
                    <SpdStatusChip :tone="trendChipTone(row.monthOnMonth)" size="sm">
                      {{ formatPercent(row.monthOnMonth) }}
                    </SpdStatusChip>
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
            <!-- 预警类型走全站通用状态条（浅底深字），
                 不再自绘「深色底 + 发光文字」的告警块。 -->
            <SpdStatusChip :tone="alertTone(item.alertType)" size="sm">{{ item.alertType }}</SpdStatusChip>
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
   运营驾驶舱 OperationCockpitView
   ---------------------------------------------------------------------
   【配色重构说明】
   旧版是「科技风大屏」：纯色霓虹描边 + 玻璃拟态 + 渐变文字 + 扫描线，
   颜色七零八落（青 / 蓝 / 紫 / 粉 / 琥珀 / 绿），与全站
   「青玉主色 + 语义色」的设计语言完全不是一套语言，
   在浅色底上尤其刺眼（用户报：整体配色太怪异）。

   新版配色规则（全部取自设计令牌，三套配色方案自动适配）：
     1. 中性底 —— 页面底 --spd-p-bg-page，面板 --spd-p-bg，
        边框 --spd-p-border / --spd-p-border-subtle；
     2. 唯一强调色 —— 主色青玉 --spd-p-primary，
        只用于品牌标识、主标题、图表主线、当前月柱；
     3. 语义色 —— success / warning / danger / info，
        只通过 SpdStatusChip（浅底深字）表达状态，
        与列表页、状态条、按钮完全同一套词汇；
     4. 文字层级 —— --spd-p-text-strong / --spd-p-text-secondary /
        --spd-p-text-muted，不再用「深底白字」的自定义色。
   ===================================================================== */
.main-area:has(.operation-cockpit-page) { padding: 0; overflow: auto; background: var(--spd-p-bg-page); }
.main-area:has(.operation-cockpit-page) > .topbar { display: none; }

.operation-cockpit-page {
  /* 面板层次令牌：全部由 --spd-p-* 派生，不引入新的色相 */
  --cockpit-panel-border: var(--spd-p-border);
  --cockpit-panel-border-soft: var(--spd-p-border-subtle);
  --cockpit-panel-bg: var(--spd-p-bg);
  --cockpit-text-main: var(--spd-p-text-strong);
  --cockpit-text-dim: var(--spd-p-text-muted);
  --cockpit-accent: var(--spd-p-primary);
  min-width: 1280px;
  min-height: 100vh;
  color: var(--cockpit-text-main);
  background: var(--spd-p-bg-page);
  padding: 18px 22px 22px;
  font-family: var(--spd-font-family);
  position: relative;
}

.operation-cockpit-page > * { position: relative; z-index: 1; }

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
  width: 44px;
  height: 44px;
  border-radius: 10px;
  color: var(--spd-p-text-on-primary);
  background: var(--cockpit-accent);
  transition: transform .25s ease;
}
/* 旧版这里是 conic-gradient 旋转光圈 + blur 辉光，
   在浅底上形成一圈杂色描边；这里改为纯主色方块。 */
.cockpit-brand:hover .cockpit-brand-mark { transform: scale(1.04); }
.cockpit-brand div { display: grid; gap: 4px; }
.cockpit-brand strong {
  font-size: var(--spd-font-size-lg);
  letter-spacing: .04em;
  color: var(--cockpit-text-main);
  font-weight: 800;
}
.cockpit-brand small { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-xs); letter-spacing: .08em; }

.cockpit-month {
  display: flex;
  align-items: center;
  height: var(--spd-p-field-height);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-p-field-radius);
  background: var(--spd-p-bg);
  overflow: hidden;
  transition: border-color .2s, box-shadow .2s;
}
.cockpit-month:focus-within {
  border-color: var(--spd-p-field-focus-border);
  box-shadow: 0 0 0 var(--spd-p-focus-width) var(--spd-p-field-focus-ring);
}
.cockpit-month span {
  padding: 0 12px;
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-sm);
  letter-spacing: .06em;
  font-weight: 600;
  white-space: nowrap;
}
.cockpit-month input {
  width: 140px;
  height: 100%;
  border: 0;
  border-left: 1px solid var(--spd-p-border-subtle);
  color: var(--cockpit-text-main);
  background: transparent;
  padding: 0 10px;
  font: inherit;
  font-weight: 600;
  letter-spacing: .02em;
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
  letter-spacing: .1em;
  font-weight: 800;
  /* 旧版用 background-clip:text 渐变字（白→青→蓝），
     在浅底上对比度极低；这里改为纯 --spd-p-text-strong。 */
  color: var(--cockpit-text-main);
  position: relative;
  padding: 0 4px;
}
.cockpit-title i {
  height: 1px;
  background: var(--spd-p-border-subtle);
}

/* 数据时钟：只保留信息层级，不再发光 */
.cockpit-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.cockpit-clock span { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-xs); letter-spacing: .06em; }
.cockpit-clock strong {
  font-size: var(--spd-font-size-4xl);
  font-variant-numeric: tabular-nums;
  color: var(--cockpit-text-main);
  letter-spacing: .04em;
  font-weight: 700;
  line-height: 1;
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
  border: 1px solid var(--spd-p-border-subtle);
  border-radius: var(--spd-p-field-radius);
  background: var(--spd-p-bg);
}
.cockpit-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--cockpit-accent);
  white-space: nowrap;
}
.cockpit-summary-item em {
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-sm);
  font-style: normal;
  letter-spacing: .04em;
}
.cockpit-summary-item strong {
  color: var(--cockpit-text-main);
  font-size: var(--spd-font-size-lg);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: .02em;
}
.cockpit-summary-item strong small {
  margin-left: 4px;
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: .02em;
}

/* ---------- 6 宫格面板 ---------- */
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
  /* 旧版：青玉染色描边 + 玻璃拟态（backdrop-filter）+ 主色渐变底。
     新版：标准面板边框 + 中性底，与全站列表页卡片一致。 */
  border: 1px solid var(--cockpit-panel-border);
  border-radius: var(--spd-p-field-radius);
  background: var(--cockpit-panel-bg);
  display: flex;
  flex-direction: column;
  box-shadow: var(--spd-p-shadow-1);
}
.cockpit-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  flex: 0 0 auto;
  border-bottom: 1px solid var(--cockpit-panel-border-soft);
  padding: 0 14px;
  background: var(--spd-p-bg-subtle);
  position: relative;
}
.cockpit-panel h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: var(--spd-font-size-lg);
  letter-spacing: .04em;
  color: var(--cockpit-text-main);
  font-weight: 700;
}
/* 面板标题前缀色条：唯一强调色 */
.cockpit-panel h2::before {
  content: "";
  width: 3px;
  height: 14px;
  background: var(--cockpit-accent);
  border-radius: 1px;
}
.cockpit-panel > header > span { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-sm); letter-spacing: .04em; }

/* 耗材消费构成 */
.category-content {
  display: grid;
  grid-template-columns: 46% 1fr;
  align-items: center;
  flex: 1;
  min-height: 0;
  padding: 16px 14px;
  gap: 14px;
}
.category-donut {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  max-width: 190px;
  margin: auto;
  border-radius: 50%;
}
.category-donut > div {
  display: grid;
  place-items: center;
  width: 62%;
  height: 62%;
  border-radius: 50%;
  background: var(--cockpit-panel-bg);
}
.category-donut strong {
  font-size: var(--spd-font-size-2xl);
  font-weight: 800;
  color: var(--cockpit-text-main);
  font-variant-numeric: tabular-nums;
}
.category-donut span { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-xs); letter-spacing: .06em; }
.category-legend { display: grid; gap: 8px; align-content: center; }
.category-legend .legend-item {
  display: grid;
  grid-template-columns: 10px 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--cockpit-panel-border-soft);
}
.category-legend .legend-item:last-child { border-bottom: 0; }
/* 图例圆点颜色由模板按 categoryColor(index) 内联设置，
   与扇形取色同源，这里只保留尺寸，不预设颜色。 */
.category-legend i { width: 10px; height: 10px; border-radius: 50%; }
.category-legend .legend-label { font-size: var(--spd-font-size-sm); color: var(--cockpit-text-main); letter-spacing: .02em; }
.category-legend strong { font-size: var(--spd-font-size-base); font-variant-numeric: tabular-nums; color: var(--cockpit-text-main); }
.category-legend small { grid-column: 3 / 5; color: var(--cockpit-text-dim); text-align: right; font-size: var(--spd-font-size-sm); font-variant-numeric: tabular-nums; }

/* 月度趋势 */
.trend-chart { height: 236px; padding: 16px 14px 0; display: flex; flex-direction: column; min-height: 0; }
.trend-chart svg { width: 100%; flex: 1; min-height: 0; overflow: visible; }
.trend-grid line { stroke: var(--spd-p-border-subtle); stroke-width: .35; stroke-dasharray: 2 2; }
.trend-chart polyline {
  fill: none;
  stroke: var(--cockpit-accent);
  stroke-width: 1.6;
  vector-effect: non-scaling-stroke;
}
.trend-chart circle { fill: var(--cockpit-accent); stroke: var(--cockpit-panel-bg); stroke-width: .6; }
/* M-18 趋势轴标签：承载刻度读数，按 G-9 提升到 --spd-font-size-sm（13px） */
.trend-labels { display: grid; grid-template-columns: repeat(12, 1fr); color: var(--cockpit-text-dim); font-size: var(--spd-font-size-sm); text-align: center; padding-top: 6px; letter-spacing: .02em; }
.trend-panel footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--cockpit-panel-border-soft);
  padding: 8px 14px;
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-sm);
  letter-spacing: .02em;
  flex: 0 0 auto;
}
.trend-panel footer strong { color: var(--cockpit-accent); font-weight: 700; }
.legend-line { display: flex; align-items: center; gap: 8px; }
.legend-line i { width: 22px; height: 2px; background: var(--cockpit-accent); }

/* 科室 TOP 8 */
.ranking-head {
  display: grid;
  grid-template-columns: 1fr 70px 90px;
  gap: 8px;
  padding: 10px 14px 6px 42px;
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-sm);
  letter-spacing: .04em;
  flex: 0 0 auto;
}
.department-ranking { display: grid; gap: 8px; padding: 6px 14px 12px; overflow: auto; align-content: start; }
.department-row {
  display: grid;
  grid-template-columns: 22px 84px minmax(70px, 1fr) 56px 50px auto;
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
  background: var(--cockpit-panel-border-soft);
}
/* 名次徽标：旧版 1/2/3 名分别用琥珀 / 蓝 / 琥珀三种色相，
   与青玉主色对撞且 1、3 名同色；现统一为「主色浅底 + 主色深字」。 */
.rank-no {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--spd-p-field-radius);
  color: var(--spd-p-primary-text);
  background: var(--spd-p-primary-bg);
  border: 1px solid var(--spd-p-primary-border);
  font-weight: 800;
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
}
/* 前三名用实心主色底 + 反白字，第四名及以后维持浅底深字，
   只用「深浅」区分名次，不再引入新的色相。 */
.rank-no.rank-1,
.rank-no.rank-2,
.rank-no.rank-3 {
  color: var(--spd-p-text-on-primary);
  background: var(--cockpit-accent);
  border-color: var(--cockpit-accent);
}
.department-row > strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--cockpit-text-main); }
.rank-bar {
  position: relative;
  height: 12px;
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--cockpit-panel-border-soft);
  border-radius: 2px;
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 0 1px 1px 0;
  background: var(--cockpit-accent);
}
.department-row > span, .department-row > small { font-variant-numeric: tabular-nums; text-align: right; }
/* <small> 的 UA 默认 font-size: smaller 会让上月金额跌到 10px，
   低于全站 12px 下限 —— 显式覆盖为辅助层级字号。 */
.department-row > small { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-xs); }

/* 重点监控耗材 */
.focus-panel { grid-column: 1 / 2; }
.focus-panel > header > span { display: flex; align-items: center; gap: 8px; }
.current-dot, .previous-dot { width: 10px; height: 10px; border-radius: 2px; }
/* 本月 = 主色，上月 = 中性石板色（同一色相家族的明度差） */
.current-dot { background: var(--cockpit-accent); }
.previous-dot { margin-left: 10px; background: var(--spd-p-slate); }
.focus-chart { display: flex; align-items: flex-end; gap: 14px; height: 280px; padding: 32px 14px 12px; flex: 1; min-height: 0; }
.focus-item { display: grid; grid-template-rows: 1fr 36px; flex: 1; min-width: 0; align-items: end; text-align: center; }
.focus-bars { display: flex; align-items: flex-end; justify-content: center; gap: 5px; height: 100%; border-bottom: 1px solid var(--cockpit-panel-border-soft); position: relative; }
.focus-bars i { position: relative; width: 36%; min-height: 4px; border-radius: 2px 2px 0 0; transition: filter .2s; }
/* 本月柱用主色实心，上月柱用主色浅色档 —— 同色相深浅区分，
   不再是「青→蓝渐变」与「蓝→深蓝渐变」两条彩虹。 */
.focus-bars .current { background: var(--cockpit-accent); }
.focus-bars .previous { background: var(--spd-p-primary-bg); border: 1px solid var(--spd-p-primary-border); }
.focus-bars .current span {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  /* 图表数据标签 —— 对应 --spd-chart-font-lg（标准文档 5.4） */
  font-size: var(--spd-chart-font-lg);
  font-weight: 800;
  color: var(--cockpit-accent);
  background: var(--cockpit-panel-bg);
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  border: 1px solid var(--cockpit-panel-border-soft);
}
.focus-item > strong { overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-size: var(--spd-font-size-sm); color: var(--cockpit-text-main); letter-spacing: .02em; }

/* 耗材分类对比 —— 通用表格外观由 unified-list.css 提供；
   这里只做驾驶舱面板内的表格容器适配。 */
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
  border-bottom: 1px solid var(--cockpit-panel-border-soft);
  padding: 0 0 8px;
}
.cockpit-compare-card :deep(.md-table-toolbar-main) {
  gap: 8px;
}
.cockpit-compare-card :deep(.ulc-table-wrap) {
  background: transparent;
  border-color: var(--cockpit-panel-border-soft);
  box-shadow: none;
}
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
  background: var(--spd-p-bg-muted) !important;
}
.cockpit-compare-card :deep(.el-table__row:hover > td.el-table__cell) {
  background: var(--spd-p-primary-bg) !important;
}
.cockpit-compare-card :deep(.el-table td.el-table__cell) {
  color: var(--cockpit-text-main);
  border-color: var(--cockpit-panel-border-soft) !important;
}
.cockpit-compare-card :deep(.ulc-strong) {
  color: var(--cockpit-text-main);
}
.cockpit-compare-card :deep(.el-table__empty-text) {
  color: var(--cockpit-text-dim);
}

/* 面板切换按钮（科室排名 / 分类对比）
   按钮本体由 SpdButton 渲染（ghost 形态），
   这里只负责把「选中态」表达为实心主色。 */
.cockpit-panel-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--spd-p-border-subtle);
  border-radius: var(--spd-p-field-radius);
  background: var(--spd-p-bg-muted);
}
.cockpit-panel-switch :deep(.spd-btn) {
  letter-spacing: .02em;
}
/* 选中态：实心主色底 + 反白字。
   同时覆盖 hover / active 伪类，否则共享样式表的
   ghost 变体 hover 规则会把这层实心底盖掉。 */
.cockpit-panel-switch :deep(.spd-btn.is-active),
.cockpit-panel-switch :deep(.spd-btn.is-active:hover:not(:disabled)),
.cockpit-panel-switch :deep(.spd-btn.is-active:active:not(:disabled)) {
  color: var(--spd-p-text-on-primary);
  background: var(--cockpit-accent);
  border-color: var(--cockpit-accent);
  font-weight: 700;
}

/* M-19 口径说明：承载统计口径定义，按 G-9 提升到 --spd-font-size-sm（13px） */
.caliber-note { margin: 10px 16px 12px; color: var(--cockpit-text-dim); font-size: var(--spd-font-size-sm); letter-spacing: .02em; }

/* 库存与异常提醒 */
.alert-list { display: grid; gap: 8px; padding: 12px 14px; height: calc(100% - 86px); overflow: auto; align-content: start; }
.alert-row {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--cockpit-panel-border-soft);
  padding: 8px 0;
}
.alert-info { display: grid; gap: 4px; min-width: 0; }
.alert-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--spd-font-size-sm); color: var(--cockpit-text-main); }
.alert-info small { color: var(--cockpit-text-dim); font-size: var(--spd-font-size-xs); font-variant-numeric: tabular-nums; }
.alert-panel > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--cockpit-panel-border-soft);
  padding: 10px 14px;
  color: var(--cockpit-text-dim);
  font-size: var(--spd-font-size-xs);
  letter-spacing: .04em;
  flex: 0 0 auto;
}
.alert-panel > footer span { display: flex; align-items: center; gap: 6px; }
.alert-panel > footer strong { color: var(--spd-p-danger-text); font-weight: 800; }
/* <small> 的 UA 默认 font-size: smaller 会把 11px 再缩到 9.17px，
   低于全站 12px 下限 —— 显式覆盖为辅助层级字号。 */
.alert-panel > footer small { font-size: var(--spd-font-size-xs); }

.cockpit-empty {
  grid-column: 1 / -1;
  align-self: center;
  margin: auto;
  color: var(--cockpit-text-dim);
  text-align: center;
  font-size: var(--spd-font-size-sm);
  letter-spacing: .04em;
  padding: 18px 0;
}

@media (max-width: 1400px) {
  .operation-cockpit-page { min-width: 1080px; padding: 14px; }
  .cockpit-header { grid-template-columns: 220px 210px 1fr 200px; }
  .cockpit-grid { grid-template-columns: 1fr 1.28fr 1.05fr; }
  .department-row { grid-template-columns: 20px 70px minmax(55px, 1fr) 50px 46px auto; gap: 5px; }
  /* M-15 窄屏降档：改用 -4xl 令牌，仍跟随字号档位 */
  .cockpit-title h1 { font-size: var(--spd-font-size-4xl); }
}

/* 暗色滚动条 */
.operation-cockpit-page ::-webkit-scrollbar { width: 8px; height: 8px; }
.operation-cockpit-page ::-webkit-scrollbar-track { background: var(--spd-p-bg-muted); }
.operation-cockpit-page ::-webkit-scrollbar-thumb { background: var(--spd-p-border-strong); border-radius: 4px; }
.operation-cockpit-page ::-webkit-scrollbar-thumb:hover { background: var(--spd-p-slate); }

/* 选区颜色 */
.operation-cockpit-page ::selection { background: var(--spd-p-primary-bg); color: var(--spd-p-text-strong); }
</style>
