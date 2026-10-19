<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  AlertCircle, BarChart3, Box, ClipboardCheck, ClipboardList, Database,
  FileCheck2, Grid2X2, Package, PackageCheck, PackagePlus, QrCode, ScanLine,
  ShoppingCart, Truck, WalletCards, ArrowRight, RefreshCw, Activity,
  Banknote, UserRound, ChevronRight, Sparkles, TrendingUp, TrendingDown,
  ShieldAlert, Bell, ListChecks, Layers
} from '@lucide/vue'
import { fetchDashboard, type DashboardData } from '../api/dashboard'
import { useAuthStore } from '../stores/auth'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../utils/chineseDisplay'
import NoticeList, { type NoticeItem } from '../components/business/NoticeList.vue'
import TodoList, { type TodoItem } from '../components/business/TodoList.vue'
import TrendChart from '../components/business/TrendChart.vue'
import { useHomeWorkbench } from '../composables/useHomeWorkbench'
import { useLayoutNavigation } from '../layout/useLayoutNavigation'
import { flattenMenu } from '../config/menu'
import { featureRouteTarget } from '../config/featureCatalog'

/* =========================================================
 * 实时数据
 * ========================================================= */
const authStore = useAuthStore()

const loading = ref(false)
const error = ref('')
const lastUpdated = ref('')
const data = ref<DashboardData>({
  metrics: { productCount: 0, todayOrders: 0, lowStockCount: 0, monthlyPurchaseAmount: 0 },
  trend: [], departments: [], exceptions: [], notices: []
})

const heroMetrics = computed(() => {
  const m = data.value.metrics
  return [
    { key: 'product',   label: '在库品种',   value: m.productCount,         unit: '个', icon: Box,           theme: 'indigo' },
    { key: 'orders',    label: '今日订单',   value: m.todayOrders,          unit: '单', icon: ShoppingCart,  theme: 'teal'   },
    { key: 'lowStock',  label: '低库存预警', value: m.lowStockCount,        unit: '项', icon: AlertCircle,   theme: 'amber',  urgent: m.lowStockCount > 0 },
    { key: 'purchase',  label: '月采购总额', value: m.monthlyPurchaseAmount,unit: '元', icon: BarChart3,     theme: 'violet', money: true }
  ]
})

const trendMax = computed(() => {
  const items = data.value.trend
  const maxIn = Math.max(1, ...items.map(i => Number(i.receivingAmount) || 0))
  const maxOut = Math.max(1, ...items.map(i => Number(i.consumptionAmount) || 0))
  return Math.max(maxIn, maxOut)
})
const maxDepartmentAmount = computed(() =>
  Math.max(1, ...data.value.departments.map(item => Number(item.amount)))
)
const totalDepartmentAmount = computed(() =>
  data.value.departments.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)

const processRows = [
  { key: 'master', label: '主数据 · 采购', theme: 'indigo', steps: [
    { label: '商品准入', icon: Database,       to: '/features/hospital-product-catalog' },
    { label: '目录审核', icon: FileCheck2,     to: '/features/pending-product-catalog' },
    { label: '采购订单', icon: ShoppingCart,   to: '/features/purchase-management' },
    { label: '到货验收', icon: Truck,          to: '/features/receiving-acceptance' },
    { label: '批次库存', icon: QrCode,         to: '/features/inventory-management' }
  ]},
  { key: 'stock', label: '库存 · 定数', theme: 'teal', steps: [
    { label: '库存余额', icon: Box,             to: '/features/inventory-management' },
    { label: '模板维护', icon: Grid2X2,         to: '/features/quota-template-maintenance' },
    { label: '打包任务', icon: ClipboardList,   to: '/features/packing-task-confirmation' },
    { label: '定数安全量', icon: PackageCheck,  to: '/features/quota-safety-stock' }
  ]},
  { key: 'clinic', label: '临床 · 结算', theme: 'rose', steps: [
    { label: '科室申领', icon: ClipboardCheck,  to: '/features/department-requisition' },
    { label: '配送签收', icon: Truck,           to: '/features/picking-delivery' },
    { label: '扫码消耗', icon: ScanLine,        to: '/features/department-consumption' },
    { label: '结算对账', icon: WalletCards,     to: '/features/settlement-reconciliation' },
    { label: '发票管理', icon: FileCheck2,      to: '/features/invoice-management' }
  ]}
]

const visibleProcessRows = computed(() => processRows
  .map(row => ({ ...row, steps: row.steps.filter(step => authStore.canAccessMenu(step.to.split('/').pop() ?? '')) }))
  .filter(row => row.steps.length > 0))

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(Number(value || 0))
}
function formatInt(value: number) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function trendPoints(values: number[], maxValue: number, width = 600, height = 200) {
  if (!values.length) return { line: '', area: '' }
  const step = values.length > 1 ? width / (values.length - 1) : 0
  const points = values.map((v, i) => `${(i * step).toFixed(1)},${(height - (v / maxValue) * (height - 32) - 14).toFixed(1)}`)
  return { line: points.join(' '), area: `${points.join(' ')} ${width},${height} 0,${height}` }
}
const receivingTrend = computed(() => trendPoints(data.value.trend.map(i => Number(i.receivingAmount) || 0), trendMax.value))
const consumptionTrend = computed(() => trendPoints(data.value.trend.map(i => Number(i.consumptionAmount) || 0), trendMax.value))

const dataStatus = computed(() => {
  if (error.value) return { text: '数据异常 · 点击重试', tone: 'danger' as const }
  if (loading.value) return { text: '正在加载最新数据…', tone: 'info' as const }
  return { text: lastUpdated.value ? '实时数据 · ' + lastUpdated.value : '实时数据已就绪', tone: 'success' as const }
})

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchDashboard()
    data.value = result
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '首页数据加载失败' }
  finally { loading.value = false }
}

/* =========================================================
 * 复盘数据（30 天趋势 / 待办 / 公告 / 常用功能）
 * ========================================================= */
const { auth, groups } = useLayoutNavigation()
const { error: homeError, refresh: refreshHome,
  todos, todoTotal, notices, labels, movementSeries, usageSeries, status, statusDetail } = useHomeWorkbench()

const availablePages = computed(() => flattenMenu(groups.value).filter(item => !item.children?.length))
const selectedCodes = ref<string[]>([])
const configuring = ref(false)
const detail = ref<{ title: string; content: string }>()

watch(availablePages, pages => {
  const allowed = new Set(pages.map(item => item.code))
  selectedCodes.value = selectedCodes.value.length
    ? selectedCodes.value.filter(code => allowed.has(code))
    : pages.slice(0, 6).map(item => item.code)
}, { immediate: true })

const shortcuts = computed(() => availablePages.value.filter(item => selectedCodes.value.includes(item.code)))

/* 把 6 类预警打包成"指标条"展示，避免一堆小卡片 */
const reviewSummary = computed(() => {
  const total = todos.value.length
  return {
    pending: todoTotal.value,
    notice: notices.value.length,
    alerts: (data.value.exceptions || []).length
  }
})
const reviewSummaryList = computed(() => [
  { key: 'pending', label: '待办事项',  value: reviewSummary.value.pending,  icon: ListChecks,  theme: 'indigo' },
  { key: 'notice',  label: '系统公告',  value: reviewSummary.value.notice,   icon: Bell,        theme: 'teal'   },
  { key: 'alerts',  label: '业务异常',  value: reviewSummary.value.alerts,   icon: ShieldAlert, theme: 'amber',  urgent: reviewSummary.value.alerts > 0 }
])

function showNotice(item: NoticeItem) { detail.value = { title: item.title, content: item.content } }
function showTodo(item: TodoItem) {
  detail.value = { title: item.title, content: '单据编号：' + item.id + '；当前状态：' + item.status + '。请在对应业务模块处理。' }
}

onMounted(loadDashboard)
</script>

<template>
  <div class="wb" aria-label="首页工作台">

    <!-- =================================================
         Hero · 欢迎区 + 关键指标
         ================================================= -->
    <header class="wb-hero">
      <div class="wb-hero-bg" aria-hidden="true">
        <span class="blob blob-1"></span>
        <span class="blob blob-2"></span>
        <span class="blob blob-3"></span>
      </div>

      <div class="wb-hero-text">
        <div class="wb-hero-eyebrow">
          <Sparkles :size="14" />
          <span>首页工作台</span>
        </div>
        <h1>{{ auth.currentUser?.realName || auth.username }}，<em>欢迎回来</em></h1>
        <p>这里汇聚了今天的核心指标、关键业务链路、待办与异常，让你能更快做出判断。</p>
      </div>

      <div class="wb-hero-side">
        <el-tag :type="dataStatus.tone" effect="dark" round class="wb-hero-status" role="button" tabindex="0" @click="loadDashboard" @keyup.enter="loadDashboard">
          <Activity :size="12" />
          <span>{{ dataStatus.text }}</span>
        </el-tag>
        <button class="wb-hero-refresh" type="button" :disabled="loading" @click="loadDashboard">
          <RefreshCw :size="14" :class="{ 'wb-spin': loading }" />
          <span>{{ loading ? '刷新中' : '刷新数据' }}</span>
        </button>
      </div>

      <!-- 4 个核心指标卡（hero 内） -->
      <ul class="wb-hero-metrics">
        <li v-for="m in heroMetrics" :key="m.key" :data-theme="m.theme" :class="{ 'is-urgent': m.urgent }">
          <span class="wb-hero-metrics-icon">
            <component :is="m.icon" :size="20" />
          </span>
          <div class="wb-hero-metrics-body">
            <span class="wb-hero-metrics-label">{{ m.label }}</span>
            <strong>
              {{ m.money ? formatMoney(m.value) : formatInt(m.value) }}<em>{{ m.unit }}</em>
            </strong>
          </div>
          <span class="wb-hero-metrics-shine" aria-hidden="true"></span>
        </li>
      </ul>
    </header>

    <p v-if="error || homeError" class="wb-banner-error">{{ error || homeError }}</p>

    <!-- =================================================
         R2 · 业务链路 + 7 日趋势 + 科室 TOP5
         ================================================= -->
    <section class="wb-grid wb-grid-r2">
      <!-- 业务链路：跨 2 列（强调它是主轴） -->
      <article class="wb-card wb-card--process">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="indigo"><Layers :size="14" /> 业务链路</span>
            <h2>SPD 核心业务全景图</h2>
            <p>{{ visibleProcessRows.length }} 条链路 · {{ visibleProcessRows.reduce((s, r) => s + r.steps.length, 0) }} 个节点</p>
          </div>
        </header>
        <div class="wb-card-body">
          <div v-if="visibleProcessRows.length" class="wb-process">
            <div v-for="row in visibleProcessRows" :key="row.key" class="wb-process-row" :data-theme="row.theme">
              <div class="wb-process-label">
                <span class="wb-process-dot"></span>{{ row.label }}
              </div>
              <ol class="wb-process-steps">
                <template v-for="(step, idx) in row.steps" :key="step.label">
                  <li class="wb-process-step">
                    <RouterLink class="wb-process-node" :to="step.to">
                      <span class="wb-process-node-icon"><component :is="step.icon" :size="16" /></span>
                      <span class="wb-process-node-text">{{ step.label }}</span>
                    </RouterLink>
                  </li>
                  <li v-if="idx < row.steps.length - 1" class="wb-process-connector" aria-hidden="true">
                    <span class="wb-process-line"></span>
                    <span class="wb-process-arrow"><ArrowRight :size="12" /></span>
                  </li>
                </template>
              </ol>
            </div>
          </div>
          <p v-else class="wb-empty">暂无授权范围内的业务流程</p>
        </div>
      </article>

      <!-- 7 日趋势 -->
      <article class="wb-card wb-card--trend7">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="teal"><TrendingUp :size="14" /> 7 日趋势</span>
            <h2>入库与消耗趋势</h2>
            <p>最近一周的业务脉搏</p>
          </div>
          <div class="wb-legend">
            <span><i class="dot in"></i>入库金额</span>
            <span><i class="dot out"></i>消耗金额</span>
          </div>
        </header>
        <div class="wb-card-body">
          <div v-if="data.trend.length" class="wb-spark">
            <svg viewBox="0 0 600 220" preserveAspectRatio="none" role="img" aria-label="近7日入库与消耗趋势">
              <g class="wb-spark-grid">
                <line v-for="y in [40, 90, 140, 190]" :key="y" x1="0" :y1="y" x2="600" :y2="y" />
              </g>
              <polygon :points="receivingTrend.area" class="wb-spark-area in" />
              <polyline :points="receivingTrend.line" class="wb-spark-line in" />
              <polygon :points="consumptionTrend.area" class="wb-spark-area out" />
              <polyline :points="consumptionTrend.line" class="wb-spark-line out" />
              <g class="wb-spark-axis">
                <text v-for="(item, i) in data.trend" :key="item.date"
                      :x="i * (600 / Math.max(1, data.trend.length - 1))" y="212" text-anchor="middle">
                  {{ item.label }}
                </text>
              </g>
            </svg>
          </div>
          <p v-else class="wb-empty">暂无趋势数据</p>
        </div>
      </article>

      <!-- 科室 TOP5 -->
      <article class="wb-card wb-card--rank">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="violet"><BarChart3 :size="14" /> 科室排行</span>
            <h2>重点科室耗材成本 TOP 5</h2>
            <p>本月累计</p>
          </div>
        </header>
        <div class="wb-card-body">
          <ul v-if="data.departments.length" class="wb-rank">
            <li v-for="(dept, idx) in data.departments" :key="dept.deptName">
              <span class="wb-rank-no" :data-rank="idx + 1">{{ String(idx + 1).padStart(2, '0') }}</span>
              <div class="wb-rank-body">
                <div class="wb-rank-head">
                  <strong>{{ dept.deptName }}</strong>
                  <span>{{ formatMoney(dept.amount) }}</span>
                </div>
                <div class="wb-rank-bar"><i :style="{ width: `${(Number(dept.amount) / maxDepartmentAmount) * 100}%` }" /></div>
              </div>
            </li>
          </ul>
          <div v-if="data.departments.length" class="wb-rank-total">
            <span>合计</span>
            <strong>{{ formatMoney(totalDepartmentAmount) }}</strong>
          </div>
          <p v-else class="wb-empty">暂无科室消耗数据</p>
        </div>
      </article>
    </section>

    <!-- =================================================
         R3 · 30 天双图（左侧主区）
         ================================================= -->
    <section class="wb-grid wb-grid-r3">
      <article class="wb-card wb-card--chart">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="blue"><BarChart3 :size="14" /> 30 天</span>
            <h2>近 30 天出入库金额</h2>
            <p>单位：万元</p>
          </div>
        </header>
        <div class="wb-card-body">
          <TrendChart :labels="labels" :series="movementSeries" title="近30天出入库金额" :height="280" />
        </div>
      </article>

      <article class="wb-card wb-card--chart">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="rose"><TrendingDown :size="14" /> 30 天</span>
            <h2>耗用与采购趋势</h2>
            <p>近 30 天 · 单位：万元</p>
          </div>
        </header>
        <div class="wb-card-body">
          <TrendChart :labels="labels" :series="usageSeries" title="近30天耗用与采购趋势" :height="280" />
        </div>
      </article>
    </section>

    <!-- =================================================
         R4 · 待办 / 公告 / 异常 / 常用功能（4 个色块单元）
         ================================================= -->
    <section class="wb-grid wb-grid-r4">
      <!-- 待办 -->
      <article class="wb-card wb-card--todo">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="indigo"><ListChecks :size="14" /> 待办</span>
            <h2>待办事项</h2>
            <p>{{ todoTotal }} 项待处理</p>
          </div>
          <el-tag effect="plain" :type="homeError ? 'danger' : 'info'" :title="statusDetail" round>
            {{ status }}
          </el-tag>
        </header>
        <div class="wb-card-body">
          <TodoList :items="todos" @select="showTodo" />
        </div>
      </article>

      <!-- 业务异常 -->
      <article class="wb-card wb-card--alert">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="amber"><ShieldAlert :size="14" /> 异常</span>
            <h2>业务异常</h2>
            <p>需要立即处理</p>
          </div>
          <el-tag v-if="data.exceptions.length" type="danger" effect="dark" round>{{ data.exceptions.length }} 项</el-tag>
        </header>
        <div class="wb-card-body">
          <ul v-if="data.exceptions.length" class="wb-alert-list">
            <li v-for="item in data.exceptions" :key="item.bizNo">
              <div class="wb-alert-head">
                <span class="wb-alert-type">{{ item.riskEvents }}</span>
                <el-tag size="small" type="danger" effect="plain">{{ formatStatusText(item.status) }}</el-tag>
              </div>
              <p>{{ item.reason }}</p>
              <span class="wb-alert-no">业务编号：{{ item.bizNo }}</span>
            </li>
          </ul>
          <p v-else class="wb-empty">暂无业务异常</p>
        </div>
      </article>

      <!-- 系统公告 -->
      <article class="wb-card wb-card--notice">
        <header class="wb-card-head">
          <div class="wb-card-title">
            <span class="wb-card-pill" data-theme="teal"><Bell :size="14" /> 公告</span>
            <h2>系统公告</h2>
            <p>{{ notices.length }} 条</p>
          </div>
        </header>
        <div class="wb-card-body">
          <NoticeList :items="notices" @select="showNotice" />
        </div>
      </article>

      <!-- 常用功能（个人中心合并） -->
      <article class="wb-card wb-card--shortcut">
        <div class="wb-shortcut-profile">
          <span class="wb-shortcut-avatar"><UserRound :size="20" /></span>
          <div>
            <strong>{{ auth.currentUser?.realName || auth.username }}</strong>
            <span>医用耗材精益管理平台</span>
          </div>
        </div>
        <div class="wb-shortcut-list">
          <div class="wb-shortcut-head">
            <h3>常用功能</h3>
            <button type="button" class="wb-link" @click="configuring = true">设置</button>
          </div>
          <ul>
            <li v-for="item in shortcuts" :key="item.code">
              <RouterLink :to="featureRouteTarget(item.code)">
                <span>{{ item.title }}</span>
                <ChevronRight :size="14" />
              </RouterLink>
            </li>
            <li v-if="!shortcuts.length" class="wb-empty-inline">暂无已选功能</li>
          </ul>
        </div>
      </article>
    </section>

    <!-- =================================================
         R5 · 最近系统操作（横幅式）
         ================================================= -->
    <article class="wb-card wb-card--feed">
      <header class="wb-card-head">
        <div class="wb-card-title">
          <span class="wb-card-pill" data-theme="violet"><Activity :size="14" /> 动态</span>
          <h2>最近系统操作</h2>
          <p>共 {{ data.notices.length }} 条</p>
        </div>
      </header>
      <div class="wb-card-body">
        <ul v-if="data.notices.length" class="wb-feed">
          <li v-for="item in data.notices" :key="`${item.operationTime}-${item.title}`">
            <span class="wb-feed-dot" aria-hidden="true"></span>
            <div class="wb-feed-body">
              <div class="wb-feed-head">
                <strong>{{ formatBusinessText(item.title) }}</strong>
                <em>{{ item.operatorName }}</em>
              </div>
              <p>{{ formatRemarkText(item.detail) }}</p>
              <span class="wb-feed-time">{{ item.operationTime }}</span>
            </div>
          </li>
        </ul>
        <p v-else class="wb-empty">暂无操作记录</p>
      </div>
    </article>

    <!-- 弹窗：公告 / 待办详情 -->
    <el-dialog :model-value="Boolean(detail)" :title="detail?.title" class="tight" width="min(560px, 92vw)" @close="detail = undefined">
      <p>{{ detail?.content }}</p>
      <template #footer><el-button type="primary" @click="detail = undefined">知道了</el-button></template>
    </el-dialog>

    <!-- 弹窗：常用功能设置 -->
    <el-dialog v-model="configuring" title="选择常用功能" class="large" width="min(1280px, 96vw)">
      <div class="wb-shortcut-options">
        <label v-for="item in availablePages" :key="item.code" class="wb-shortcut-option">
          <input type="checkbox" :value="item.code" v-model="selectedCodes" />
          <span>{{ item.title }}</span>
        </label>
      </div>
      <template #footer>
        <el-button @click="configuring = false">取消</el-button>
        <el-button type="primary" @click="configuring = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ================================================================
 * 主题色 · 莫兰迪医疗色板
 *   每个区块 1 个主色，色相 4~5 档（50/100/300/500/600）
 * ================================================================ */
.wb {
  --indigo-50:  #eef2ff;  --indigo-100: #e0e7ff;  --indigo-300: #a5b4fc;  --indigo-500: #6366f1;  --indigo-600: #4f46e5;
  --teal-50:    #ecfdf5;  --teal-100:   #d1fae5;  --teal-300:   #6ee7b7;  --teal-500:   #10b981;  --teal-600:   #059669;
  --amber-50:   #fffbeb;  --amber-100:  #fef3c7;  --amber-300:  #fcd34d;  --amber-500:  #f59e0b;  --amber-600:  #d97706;
  --rose-50:    #fff1f2;  --rose-100:   #ffe4e6;  --rose-300:   #fda4af;  --rose-500:   #f43f5e;  --rose-600:   #e11d48;
  --violet-50:  #f5f3ff;  --violet-100: #ede9fe;  --violet-300: #c4b5fd;  --violet-500: #8b5cf6;  --violet-600: #7c3aed;
  --blue-50:    #eff6ff;  --blue-100:   #dbeafe;  --blue-300:   #93c5fd;  --blue-500:   #3b82f6;  --blue-600:   #2563eb;
  --slate-0:    #ffffff;  --slate-50:   #f8fafc;  --slate-100:  #f1f5f9;  --slate-200:  #e2e8f0;
  --slate-300:  #cbd5e1;  --slate-400:  #94a3b8;  --slate-500:  #64748b;  --slate-600:  #475569;
  --slate-700:  #334155;  --slate-800:  #1e293b;  --slate-900:  #0f172a;

  --sp-1: 4px;  --sp-2: 8px;  --sp-3: 12px; --sp-4: 16px;
  --sp-5: 20px; --sp-6: 24px; --sp-8: 32px; --sp-10: 40px;
  --r-sm: 8px;  --r-md: 12px; --r-lg: 16px; --r-xl: 20px;

  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  /* 整体外边距：让工作台不贴边，与外壳保持呼吸感 */
  padding: var(--sp-6) var(--sp-8) var(--sp-8);
  min-width: 0;
}

@media (max-width: 1280px) {
  .wb { padding: var(--sp-5) var(--sp-6) var(--sp-8); }
}
@media (max-width: 960px) {
  .wb { padding: var(--sp-4) var(--sp-4) var(--sp-6); gap: var(--sp-4); }
}

/* ================================================================
 * Hero
 * ================================================================ */
.wb-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  gap: var(--sp-5);
  padding: var(--sp-6) var(--sp-6);
  color: #fff;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 45%, #ec4899 100%);
  border-radius: var(--r-xl);
  box-shadow: 0 18px 40px -20px rgba(79, 70, 229, 0.45);
  overflow: hidden;
}
.wb-hero-bg { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.wb-hero-bg .blob {
  position: absolute;
  width: 320px; height: 320px;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.35;
}
.wb-hero-bg .blob-1 { top: -120px; right: -60px; background: #fbbf24; }
.wb-hero-bg .blob-2 { bottom: -140px; left: 30%; background: #06b6d4; }
.wb-hero-bg .blob-3 { top: 30%; left: -100px; background: #f472b6; }

.wb-hero-text { position: relative; z-index: 1; }
.wb-hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  margin-bottom: var(--sp-3);
  font-size: 12px;
  font-weight: 500;
  color: #1e1b4b;
  background: rgba(255,255,255,0.18);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 999px;
  backdrop-filter: blur(6px);
}
.wb-hero-eyebrow svg { color: #fde68a; }
.wb-hero-text h1 {
  margin: 0 0 var(--sp-2);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.wb-hero-text h1 em {
  font-style: normal;
  color: #fde68a;
  font-weight: 600;
}

.wb-hero-text p {
  margin: 0;
  color: rgba(255,255,255,0.78);
  font-size: 13px;
  line-height: 1.6;
  max-width: 520px;
}

.wb-hero-side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sp-2);
}
.wb-hero-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(255,255,255,0.18);
  border: 1px solid rgba(255,255,255,0.3);
  color: #fff !important;
  cursor: pointer;
  transition: transform 0.15s ease, background 0.15s ease;
}
.wb-hero-status:hover { transform: translateY(-1px); background: rgba(255,255,255,0.28); }
.wb-hero-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: #4f46e5;
  background: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.wb-hero-refresh:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(0,0,0,0.18); }
.wb-hero-refresh:disabled { opacity: 0.6; cursor: not-allowed; }
.wb-spin { animation: wb-spin 0.8s linear infinite; }
@keyframes wb-spin { to { transform: rotate(360deg); } }

/* Hero · 4 指标卡 */
.wb-hero-metrics {
  position: relative;
  z-index: 1;
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--sp-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.wb-hero-metrics li {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  overflow: hidden;
  color: var(--slate-800);
  background: rgba(255,255,255,0.92);
  border: 1px solid rgba(255,255,255,0.4);
  border-radius: var(--r-lg);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 24px -12px rgba(15, 23, 42, 0.25);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.wb-hero-metrics li:hover { transform: translateY(-2px); box-shadow: 0 16px 32px -16px rgba(15, 23, 42, 0.35); }
.wb-hero-metrics-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px; height: 40px;
  flex-shrink: 0;
  color: #fff;
  background: var(--indigo-500);
  border-radius: var(--r-md);
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
.wb-hero-metrics li[data-theme="indigo"] .wb-hero-metrics-icon { background: linear-gradient(135deg, var(--indigo-500), var(--indigo-600)); }
.wb-hero-metrics li[data-theme="teal"]   .wb-hero-metrics-icon { background: linear-gradient(135deg, var(--teal-500),   var(--teal-600)); }
.wb-hero-metrics li[data-theme="amber"]  .wb-hero-metrics-icon { background: linear-gradient(135deg, var(--amber-500),  var(--amber-600)); }
.wb-hero-metrics li[data-theme="violet"] .wb-hero-metrics-icon { background: linear-gradient(135deg, var(--violet-500), var(--violet-600)); }

.wb-hero-metrics-body { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.wb-hero-metrics-label { color: var(--slate-500); font-size: 12.5px; font-weight: 500; }
.wb-hero-metrics-body strong {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--slate-900);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.3px;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-hero-metrics-body strong em {
  color: var(--slate-400);
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
}
.wb-hero-metrics li.is-urgent {
  outline: 2px solid var(--amber-300);
  outline-offset: -2px;
}
.wb-hero-metrics-shine {
  position: absolute;
  top: 0; right: -50%;
  width: 60%; height: 100%;
  background: linear-gradient(120deg, transparent 30%, rgba(255,255,255,0.55) 50%, transparent 70%);
  transform: skewX(-20deg);
  pointer-events: none;
  animation: wb-shine 4s ease-in-out infinite;
}
@keyframes wb-shine {
  0%, 70% { transform: translateX(-30%) skewX(-20deg); }
  100%    { transform: translateX(180%) skewX(-20deg); }
}

.wb-banner-error {
  margin: 0;
  padding: var(--sp-2) var(--sp-3);
  color: var(--rose-600);
  font-size: 12.5px;
  background: var(--rose-50);
  border: 1px solid #fecdd3;
  border-radius: var(--r-md);
}

/* ================================================================
 * 公共 · card
 * ================================================================ */
.wb-card {
  background: #fff;
  border: 1px solid var(--slate-100);
  border-radius: var(--r-lg);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px -20px rgba(15, 23, 42, 0.18);
  overflow: hidden;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.wb-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid var(--slate-100);
}
.wb-card-title { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.wb-card-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  padding: 2px 10px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--indigo-600);
  background: var(--indigo-50);
  border-radius: 999px;
}
.wb-card-pill[data-theme="teal"]   { color: var(--teal-600);   background: var(--teal-50); }
.wb-card-pill[data-theme="amber"]  { color: var(--amber-600);  background: var(--amber-50); }
.wb-card-pill[data-theme="rose"]   { color: var(--rose-600);   background: var(--rose-50); }
.wb-card-pill[data-theme="violet"] { color: var(--violet-600); background: var(--violet-50); }
.wb-card-pill[data-theme="blue"]   { color: var(--blue-600);   background: var(--blue-50); }

.wb-card-title h2 {
  margin: 0;
  color: var(--slate-800);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}
.wb-card-title p {
  margin: 0;
  color: var(--slate-400);
  font-size: 12px;
}

.wb-card-body { padding: var(--sp-4) var(--sp-5); }
.wb-card-body--tight { padding: var(--sp-2) var(--sp-5); }

.wb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100px;
  margin: 0;
  color: var(--slate-400);
  font-size: 12.5px;
  text-align: center;
}
.wb-empty-inline { color: var(--slate-400); font-size: 12px; padding: 4px 0; }

/* ================================================================
 * R2 网格：业务链路（2 列跨） + 7 日 + TOP5
 * ================================================================ */
.wb-grid { display: grid; gap: var(--sp-4); }
.wb-grid-r2 { grid-template-columns: 1.6fr 1fr; grid-template-rows: auto auto; }
.wb-grid-r2 .wb-card--process { grid-column: 1 / 2; grid-row: 1 / 3; }
.wb-grid-r2 .wb-card--trend7  { grid-column: 2 / 3; grid-row: 1 / 2; }
.wb-grid-r2 .wb-card--rank    { grid-column: 2 / 3; grid-row: 2 / 3; }

.wb-grid-r3 { grid-template-columns: 1fr 1fr; }
.wb-grid-r4 { grid-template-columns: 1.1fr 1fr 1fr 1.1fr; }

/* ================================================================
 * 业务链路 · 美化样式
 *   整体思路：
 *   - 左侧"链路标题 + 编号" 立式 label，配同色光晕圆点
 *   - 右侧横向时间线：节点（圆形彩色图标 + 序号 + 名称）+ 渐变连接线 + 箭头
 *   - 三条链路各有主题色（蓝 / 青 / 玫红）
 *   - hover 整节点上浮，连接线高亮
 * ================================================================ */
.wb-process { display: flex; flex-direction: column; gap: var(--sp-4); }

.wb-process-row {
  position: relative;
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  align-items: stretch;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-4);
  border-radius: var(--r-md);
  background: linear-gradient(180deg, #fff 0%, var(--slate-50) 100%);
  border: 1px solid var(--slate-100);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.6);
}
.wb-process-row[data-theme="indigo"] {
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  border-color: #e0e7ff;
}
.wb-process-row[data-theme="teal"] {
  background: linear-gradient(135deg, #ecfdf5 0%, #f0fdfa 100%);
  border-color: #d1fae5;
}
.wb-process-row[data-theme="rose"] {
  background: linear-gradient(135deg, #fff1f2 0%, #fdf2f8 100%);
  border-color: #ffe4e6;
}

/* 左侧 label：编号 + 标题 */
.wb-process-label {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 6px;
  padding: var(--sp-2) var(--sp-2);
  color: var(--slate-800);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}
.wb-process-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  background: var(--indigo-500);
  box-shadow:
    0 0 0 4px rgba(99, 102, 241, 0.18),
    0 0 12px rgba(99, 102, 241, 0.45);
}
.wb-process-row[data-theme="teal"] .wb-process-dot {
  background: var(--teal-500);
  box-shadow:
    0 0 0 4px rgba(16, 185, 129, 0.18),
    0 0 12px rgba(16, 185, 129, 0.45);
}
.wb-process-row[data-theme="rose"] .wb-process-dot {
  background: var(--rose-500);
  box-shadow:
    0 0 0 4px rgba(244, 63, 94, 0.18),
    0 0 12px rgba(244, 63, 94, 0.45);
}

/* 右侧 timeline */
.wb-process-steps {
  display: flex;
  align-items: center;
  gap: 0;
  list-style: none;
  margin: 0;
  padding: 0;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.wb-process-steps::-webkit-scrollbar { height: 4px; }
.wb-process-steps::-webkit-scrollbar-thumb { background: var(--slate-200); border-radius: 2px; }

/* 节点（核心：圆形彩色图标 + 名称） */
.wb-process-step { flex: 1 1 0; min-width: 78px; max-width: 130px; }

.wb-process-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 76px;
  padding: 12px 6px 10px;
  color: var(--slate-700);
  text-decoration: none;
  background: #fff;
  border: 1px solid var(--slate-200);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}
.wb-process-node:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px -10px rgba(15, 23, 42, 0.25);
  z-index: 2;
}

/* 圆形彩色图标 */
.wb-process-node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  margin-top: 0;
  color: #fff;
  background: var(--indigo-500);
  border-radius: 50%;
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.35);
  transition: transform 0.18s ease;
}
.wb-process-row[data-theme="indigo"] .wb-process-node-icon {
  background: linear-gradient(135deg, var(--indigo-500), var(--indigo-600));
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.35);
}
.wb-process-row[data-theme="teal"] .wb-process-node-icon {
  background: linear-gradient(135deg, var(--teal-500), var(--teal-600));
  box-shadow: 0 4px 10px rgba(16, 185, 129, 0.35);
}
.wb-process-row[data-theme="rose"] .wb-process-node-icon {
  background: linear-gradient(135deg, var(--rose-500), var(--rose-600));
  box-shadow: 0 4px 10px rgba(244, 63, 94, 0.35);
}
.wb-process-node:hover .wb-process-node-icon { transform: scale(1.08); }

/* 节点名称 */
.wb-process-node-text {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.3;
  text-align: center;
  overflow-wrap: break-word;
  word-break: keep-all;
  color: var(--slate-700);
}
.wb-process-row[data-theme="indigo"] .wb-process-node:hover { border-color: var(--indigo-300); background: var(--indigo-50); }
.wb-process-row[data-theme="teal"]   .wb-process-node:hover { border-color: var(--teal-300);   background: var(--teal-50);   }
.wb-process-row[data-theme="rose"]   .wb-process-node:hover { border-color: var(--rose-300);   background: var(--rose-50);   }

/* 连接器：虚线 + 渐变箭头 */
.wb-process-connector {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 28px;
  align-self: center;
  position: relative;
  height: 24px;
  color: var(--slate-300);
}
.wb-process-line {
  flex: 1;
  height: 2px;
  background: repeating-linear-gradient(
    90deg,
    var(--slate-300) 0 6px,
    transparent 6px 10px
  );
}
.wb-process-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px; height: 16px;
  color: var(--slate-400);
  background: #fff;
  border: 1px solid var(--slate-200);
  border-radius: 50%;
  flex-shrink: 0;
}
.wb-process-row[data-theme="indigo"] .wb-process-arrow {
  color: var(--indigo-500);
  border-color: var(--indigo-200);
  background: var(--indigo-50);
}
.wb-process-row[data-theme="teal"] .wb-process-arrow {
  color: var(--teal-500);
  border-color: var(--teal-200);
  background: var(--teal-50);
}
.wb-process-row[data-theme="rose"] .wb-process-arrow {
  color: var(--rose-500);
  border-color: var(--rose-200);
  background: var(--rose-50);
}

/* ================================================================
 * 7 日趋势 SVG
 * ================================================================ */
.wb-spark { width: 100%; height: 220px; }
.wb-spark svg { width: 100%; height: 100%; display: block; }
.wb-spark-grid line { stroke: var(--slate-100); stroke-width: 1; stroke-dasharray: 3 3; }
.wb-spark-axis text { fill: var(--slate-400); font-size: 10.5px; }
.wb-spark-line { fill: none; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.wb-spark-line.in  { stroke: var(--indigo-500); }
.wb-spark-line.out { stroke: var(--teal-500); }
.wb-spark-area.in  { fill: rgba(99, 102, 241, 0.12); }
.wb-spark-area.out { fill: rgba(16, 185, 129, 0.12); }

.wb-legend {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  color: var(--slate-500);
  font-size: 12px;
}
.wb-legend span { display: inline-flex; align-items: center; gap: 6px; }
.wb-legend .dot { width: 8px; height: 8px; border-radius: 2px; display: inline-block; }
.wb-legend .dot.in  { background: var(--indigo-500); }
.wb-legend .dot.out { background: var(--teal-500); }

/* ================================================================
 * 科室 TOP5
 * ================================================================ */
.wb-rank { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
.wb-rank li { display: flex; align-items: center; gap: var(--sp-3); }
.wb-rank-no {
  font-size: 14px;
  font-weight: 700;
  color: var(--slate-400);
  font-variant-numeric: tabular-nums;
  width: 26px;
  flex-shrink: 0;
  text-align: center;
}
.wb-rank-no[data-rank="1"] { color: var(--rose-500); }
.wb-rank-no[data-rank="2"] { color: var(--amber-500); }
.wb-rank-no[data-rank="3"] { color: var(--violet-500); }

.wb-rank-body { flex: 1; min-width: 0; }
.wb-rank-head {
  display: flex; justify-content: space-between; align-items: baseline;
  gap: var(--sp-2);
  margin-bottom: 4px;
}
.wb-rank-head strong {
  color: var(--slate-800);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.wb-rank-head span {
  color: var(--indigo-600);
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.wb-rank-bar {
  height: 6px;
  background: var(--slate-100);
  border-radius: 3px;
  overflow: hidden;
}
.wb-rank-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--indigo-500), var(--violet-500));
  border-radius: 3px;
  transition: width 0.3s ease;
}
.wb-rank-total {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--slate-200);
  color: var(--slate-500);
  font-size: 12px;
}
.wb-rank-total strong {
  color: var(--slate-800);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ================================================================
 * 业务异常
 * ================================================================ */
.wb-alert-list {
  list-style: none; margin: 0; padding: 0;
  display: flex; flex-direction: column; gap: var(--sp-2);
}
.wb-alert-list li {
  display: flex; flex-direction: column; gap: 4px;
  padding: var(--sp-3);
  background: linear-gradient(90deg, var(--amber-50) 0%, #fff 100%);
  border: 1px solid #fde68a;
  border-left: 3px solid var(--amber-500);
  border-radius: var(--r-sm);
}
.wb-alert-head { display: flex; justify-content: space-between; align-items: center; gap: var(--sp-2); }
.wb-alert-type { color: #92400e; font-size: 13px; font-weight: 600; }
.wb-alert-list p {
  margin: 0;
  color: #78350f;
  font-size: 12.5px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.wb-alert-no {
  color: var(--slate-400);
  font-size: 11.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* ================================================================
 * 复盘 · 30 天卡 · 头部装饰
 * ================================================================ */
.wb-card--chart { position: relative; }
.wb-card--chart::before {
  content: "";
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 3px;
  border-radius: var(--r-lg) var(--r-lg) 0 0;
}
.wb-card--chart:first-of-type::before { background: linear-gradient(90deg, var(--indigo-500), var(--blue-500)); }
.wb-card--chart:last-of-type::before  { background: linear-gradient(90deg, var(--rose-500), var(--amber-500)); }

/* ================================================================
 * 常用功能 + 个人中心
 * ================================================================ */
.wb-card--shortcut {
  background: linear-gradient(160deg, var(--violet-50) 0%, #fff 65%);
  border-color: #ede9fe;
}
.wb-shortcut-profile {
  display: flex; align-items: center; gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid #ede9fe;
}
.wb-shortcut-avatar {
  display: inline-flex; align-items: center; justify-content: center;
  width: 40px; height: 40px;
  color: #fff;
  background: linear-gradient(135deg, var(--violet-500), var(--indigo-500));
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.32);
}
.wb-shortcut-profile > div { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.wb-shortcut-profile strong {
  color: var(--slate-800);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.wb-shortcut-profile span { color: var(--slate-400); font-size: 12px; }

.wb-shortcut-list { padding: var(--sp-3) var(--sp-5) var(--sp-4); }
.wb-shortcut-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--sp-2);
}
.wb-shortcut-head h3 {
  margin: 0;
  color: var(--slate-800);
  font-size: 13px;
  font-weight: 600;
}
.wb-link {
  background: none; border: none; padding: 0;
  color: var(--violet-600);
  font-size: 12.5px;
  cursor: pointer;
}
.wb-link:hover { color: var(--violet-500); }

.wb-shortcut-list ul {
  list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-2);
}
.wb-shortcut-list a {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 10px;
  color: var(--slate-700);
  font-size: 12.5px;
  text-decoration: none;
  background: #fff;
  border: 1px solid var(--slate-200);
  border-radius: var(--r-sm);
  transition: all 0.15s ease;
  overflow: hidden;
}
.wb-shortcut-list a > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wb-shortcut-list a:hover {
  color: var(--violet-600);
  border-color: var(--violet-300);
  background: var(--violet-50);
  transform: translateX(2px);
}

.wb-shortcut-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-2) var(--sp-3);
}
.wb-shortcut-option {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px;
  color: var(--slate-700);
  font-size: 13px;
  border-radius: var(--r-sm);
  cursor: pointer;
}
.wb-shortcut-option:hover { background: var(--slate-50); }

/* ================================================================
 * 系统操作 feed
 * ================================================================ */
.wb-feed { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.wb-feed li {
  display: flex; gap: var(--sp-3);
  padding: var(--sp-3) 0;
  border-bottom: 1px dashed var(--slate-100);
}
.wb-feed li:last-child { border-bottom: none; }
.wb-feed-dot {
  width: 8px; height: 8px;
  margin-top: 7px;
  background: linear-gradient(135deg, var(--indigo-500), var(--violet-500));
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.wb-feed-body { flex: 1; min-width: 0; }
.wb-feed-head {
  display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
  margin-bottom: 2px;
}
.wb-feed-head strong {
  color: var(--slate-800);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.wb-feed-head em {
  color: var(--slate-400);
  font-size: 11.5px;
  font-style: normal;
  flex-shrink: 0;
}
.wb-feed-body p {
  margin: 0 0 2px;
  color: var(--slate-500);
  font-size: 12.5px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.wb-feed-time {
  color: var(--slate-400);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

/* ================================================================
 * 响应式
 * ================================================================ */
@media (max-width: 1280px) {
  .wb-grid-r2 { grid-template-columns: 1fr 1fr; grid-template-rows: auto auto auto; }
  .wb-grid-r2 .wb-card--process { grid-column: 1 / -1; grid-row: 1 / 2; }
  .wb-grid-r2 .wb-card--trend7  { grid-column: 1 / 2; grid-row: 2 / 3; }
  .wb-grid-r2 .wb-card--rank    { grid-column: 2 / 3; grid-row: 2 / 3; }
  .wb-grid-r4 { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 1100px) {
  /* 业务链路在 1100 以下启用横向滚动 */
  .wb-process-row { grid-template-columns: 90px minmax(0, 1fr); }
}

@media (max-width: 960px) {
  .wb-hero { grid-template-columns: 1fr; padding: var(--sp-5); }
  .wb-hero-side { flex-direction: row; align-items: center; }
  .wb-hero-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .wb-grid-r3 { grid-template-columns: 1fr; }
  .wb-grid-r4 { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .wb-hero-text h1 { font-size: 22px; }
  .wb-hero-metrics { grid-template-columns: 1fr; }
  .wb-process-row { grid-template-columns: 1fr; gap: var(--sp-2); }
  .wb-process-step { max-width: none; }
  .wb-shortcut-list ul { grid-template-columns: 1fr; }
  .wb-shortcut-options { grid-template-columns: 1fr; }
}
</style>
