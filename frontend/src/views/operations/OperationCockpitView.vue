<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Activity, AlertTriangle, BellRing, Building2, Database, RefreshCw, TrendingUp
} from '@lucide/vue'
import { fetchOperationCockpit, type OperationCockpitData } from '../../api/operationCockpit'

const now = new Date()
const selectedMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const clock = ref('')
const loading = ref(false)
const error = ref('')
const data = ref<OperationCockpitData>(emptyData(selectedMonth.value))

let clockTimer = 0

const totalCategoryAmount = computed(() =>
  data.value.categories.reduce((sum, item) => sum + Number(item.currentAmount || 0), 0)
)
const categoryGradient = computed(() => {
  const colors = ['#2f8cff', '#20c7c1', '#62cf8a']
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
  error.value = ''
  try {
    data.value = await fetchOperationCockpit(selectedMonth.value)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '运营驾驶舱数据加载失败'
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
  <main class="operation-cockpit-page">
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

    <p v-if="error" class="cockpit-error">{{ error }}</p>

    <section class="cockpit-kpis" :aria-busy="loading">
      <article class="kpi-card cyan">
        <span class="kpi-symbol cyan"><Database :size="28" /></span>
        <div class="kpi-body">
          <p>本月耗材金额</p>
          <strong>{{ formatWan(data.summary.currentAmount) }}<em>万元</em></strong>
          <small>上月 {{ formatWan(data.summary.previousAmount) }} 万元</small>
        </div>
        <span class="kpi-scan" style="color: var(--neon-cyan);"></span>
      </article>
      <article class="kpi-card coral">
        <span class="kpi-symbol coral"><TrendingUp :size="28" /></span>
        <div class="kpi-body">
          <p>环比增长</p>
          <strong :class="trendTone(data.summary.monthOnMonth)">{{ formatPercent(data.summary.monthOnMonth) }}</strong>
          <small>按科室消耗事实金额统计</small>
        </div>
        <span class="kpi-scan" style="color: var(--neon-pink);"></span>
      </article>
      <article class="kpi-card blue">
        <span class="kpi-symbol blue"><Building2 :size="28" /></span>
        <div class="kpi-body">
          <p>消耗科室</p>
          <strong>{{ data.summary.departmentCount }}<em>个</em></strong>
          <small>统计月份内产生消耗的科室</small>
        </div>
        <span class="kpi-scan" style="color: var(--neon-blue);"></span>
      </article>
      <article class="kpi-card amber">
        <span class="kpi-symbol amber"><BellRing :size="28" /></span>
        <div class="kpi-body">
          <p>预警事项</p>
          <strong>{{ data.summary.warningCount }}<em>条</em></strong>
          <small>重点监控耗材库存预警</small>
        </div>
        <span class="kpi-scan" style="color: var(--neon-amber);"></span>
      </article>
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
            <defs><linearGradient id="cockpitTrendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#20c7c1" stop-opacity=".55"/><stop offset="1" stop-color="#20c7c1" stop-opacity="0"/></linearGradient></defs>
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
        <div class="compare-scroll">
          <table>
            <thead><tr><th>分类</th><th>本月</th><th>上月</th><th>环比</th></tr></thead>
            <tbody>
              <tr v-for="item in data.categories" :key="item.category"><td>{{ item.category }}</td><td>{{ formatWan(item.currentAmount) }}</td><td>{{ formatWan(item.previousAmount) }}</td><td :class="trendTone(item.monthOnMonth)">{{ formatPercent(item.monthOnMonth) }}</td></tr>
            </tbody>
            <tfoot><tr><td>合计</td><td>{{ formatWan(data.summary.currentAmount) }}</td><td>{{ formatWan(data.summary.previousAmount) }}</td><td :class="trendTone(data.summary.monthOnMonth)">{{ formatPercent(data.summary.monthOnMonth) }}</td></tr></tfoot>
          </table>
          <p class="caliber-note">口径：高值耗材；定数管理且收费；定数管理且不收费。</p>
        </div>
      </article>

      <article class="cockpit-panel alert-panel">
        <header><h2><AlertTriangle :size="18" />库存与异常提醒</h2><button type="button" :disabled="loading" @click="loadCockpit"><RefreshCw :size="15" />刷新</button></header>
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
.main-area:has(.operation-cockpit-page) { padding: 0; overflow: auto; background: #02060f; }
.main-area:has(.operation-cockpit-page) > .topbar { display: none; }

.operation-cockpit-page {
  --bg-base: #02060f;
  --bg-deep: #050a1a;
  --neon-cyan: #00f0ff;
  --neon-blue: #3a8bff;
  --neon-purple: #8a5cff;
  --neon-pink: #ff4d8d;
  --neon-amber: #ffb84d;
  --neon-green: #2bff9d;
  --neon-red: #ff4d6d;
  --panel-stroke: rgba(0, 240, 255, .38);
  --panel-stroke-soft: rgba(0, 240, 255, .16);
  --panel-bg: linear-gradient(135deg, rgba(8, 22, 48, .88), rgba(2, 6, 15, .78));
  --text-main: #d8f4ff;
  --text-dim: #6a8aa0;
  min-width: 1280px;
  min-height: 100vh;
  color: var(--text-main);
  background:
    radial-gradient(ellipse 80% 50% at 20% 0%, rgba(58, 139, 255, .22), transparent 70%),
    radial-gradient(ellipse 60% 50% at 80% 100%, rgba(138, 92, 255, .18), transparent 70%),
    radial-gradient(ellipse 50% 40% at 50% 50%, rgba(0, 240, 255, .08), transparent 70%),
    linear-gradient(180deg, #02060f 0%, #050a1a 50%, #02060f 100%);
  padding: 18px 22px 22px;
  font-family: "Microsoft YaHei UI", "Source Han Sans CN", sans-serif;
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
    linear-gradient(rgba(0, 240, 255, .045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 240, 255, .045) 1px, transparent 1px),
    radial-gradient(circle, rgba(0, 240, 255, .12) 1px, transparent 1.5px);
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
  box-shadow: 0 0 18px var(--neon-cyan), 0 0 38px rgba(0, 240, 255, .35);
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
  color: #02060f;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: 0 0 24px rgba(0, 240, 255, .45), inset 0 1px rgba(255, 255, 255, .25);
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
  font-size: 18px;
  letter-spacing: .08em;
  background: linear-gradient(90deg, #ffffff, var(--neon-cyan));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 800;
}
.cockpit-brand small { color: var(--text-dim); font-size: 11px; letter-spacing: .14em; }

.cockpit-month {
  display: flex;
  align-items: center;
  height: 42px;
  border: 1px solid var(--panel-stroke);
  border-radius: 6px;
  background: linear-gradient(180deg, rgba(0, 240, 255, .06), rgba(0, 240, 255, .02));
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
.cockpit-month:focus-within { border-color: var(--neon-cyan); box-shadow: 0 0 0 3px rgba(0, 240, 255, .14), 0 0 22px rgba(0, 240, 255, .25); }
.cockpit-month span { padding: 0 14px; color: var(--neon-cyan); font-size: 12px; letter-spacing: .12em; font-weight: 700; }
.cockpit-month input {
  width: 140px;
  height: 100%;
  border: 0;
  border-left: 1px solid var(--panel-stroke-soft);
  color: white;
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
  font-size: clamp(26px, 2.4vw, 38px);
  letter-spacing: .22em;
  font-weight: 800;
  background: linear-gradient(180deg, #ffffff 0%, var(--neon-cyan) 60%, var(--neon-blue) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 26px rgba(0, 240, 255, .35);
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
  box-shadow: 0 0 10px rgba(0, 240, 255, .5);
}

.cockpit-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.cockpit-clock span { color: var(--neon-cyan); font-size: 11px; letter-spacing: .18em; }
.cockpit-clock strong {
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  color: white;
  text-shadow: 0 0 12px var(--neon-cyan), 0 0 24px rgba(0, 240, 255, .55);
  letter-spacing: .08em;
  font-weight: 700;
  line-height: 1;
}

.cockpit-error {
  margin: 8px 0 0;
  border: 1px solid rgba(255, 77, 109, .55);
  color: #ffd0d8;
  background: linear-gradient(90deg, rgba(255, 77, 109, .14), rgba(255, 77, 109, .04));
  border-radius: 4px;
  padding: 9px 16px;
  font-size: 13px;
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

/* ---------- KPI 卡（带六边形辉光 + 角标） ---------- */
.cockpit-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 14px 0;
}
.kpi-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 116px;
  border: 1px solid var(--panel-stroke);
  border-radius: 6px;
  background-color: #050a1a !important;
  background-image: linear-gradient(135deg, rgba(0, 240, 255, .08), rgba(58, 139, 255, .04) 50%, rgba(2, 6, 15, .9)) !important;
  padding: 18px 22px;
  overflow: hidden;
  transition: transform .3s cubic-bezier(.4, 0, .2, 1), border-color .3s, box-shadow .3s;
}
/* 角标：左上 + 右下 */
.kpi-card::before,
.kpi-card::after {
  content: "";
  position: absolute;
  width: 14px; height: 14px;
  border: 2px solid var(--neon-cyan);
  pointer-events: none;
  transition: width .25s, height .25s;
}
.kpi-card::before { top: -1px; left: -1px; border-right: 0; border-bottom: 0; }
.kpi-card::after { bottom: -1px; right: -1px; border-left: 0; border-top: 0; }
.kpi-card:hover { transform: translateY(-3px); border-color: var(--neon-cyan); box-shadow: 0 0 0 1px var(--neon-cyan), 0 12px 38px rgba(0, 240, 255, .28); }
.kpi-card:hover::before,
.kpi-card:hover::after { width: 18px; height: 18px; }
.kpi-card.cyan::before, .kpi-card.cyan::after { border-color: var(--neon-cyan); }
.kpi-card.coral::before, .kpi-card.coral::after { border-color: var(--neon-pink); }
.kpi-card.blue::before, .kpi-card.blue::after { border-color: var(--neon-blue); }
.kpi-card.amber::before, .kpi-card.amber::after { border-color: var(--neon-amber); }

.kpi-card.cyan:hover { box-shadow: 0 0 0 1px var(--neon-cyan), 0 12px 38px rgba(0, 240, 255, .35); border-color: var(--neon-cyan); }
.kpi-card.coral:hover { box-shadow: 0 0 0 1px var(--neon-pink), 0 12px 38px rgba(255, 77, 141, .35); border-color: var(--neon-pink); }
.kpi-card.blue:hover { box-shadow: 0 0 0 1px var(--neon-blue), 0 12px 38px rgba(58, 139, 255, .35); border-color: var(--neon-blue); }
.kpi-card.amber:hover { box-shadow: 0 0 0 1px var(--neon-amber), 0 12px 38px rgba(255, 184, 77, .35); border-color: var(--neon-amber); }

/* 卡片内的扫描光带 */
.kpi-card .kpi-scan {
  position: absolute;
  left: 0; right: 0;
  top: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, currentColor, transparent);
  opacity: .5;
  animation: cockpit-card-scan 3.5s linear infinite;
}
@keyframes cockpit-card-scan {
  0% { transform: translateY(0); opacity: 0; }
  10% { opacity: .7; }
  90% { opacity: .7; }
  100% { transform: translateY(116px); opacity: 0; }
}

.kpi-symbol {
  position: relative;
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, currentColor 0%, transparent 70%);
  background-color: rgba(0, 240, 255, .08);
  box-shadow: 0 0 22px currentColor, inset 0 0 0 1px currentColor;
  flex: 0 0 auto;
}
.kpi-symbol.cyan { color: var(--neon-cyan); }
.kpi-symbol.coral { color: var(--neon-pink); }
.kpi-symbol.blue { color: var(--neon-blue); }
.kpi-symbol.amber { color: var(--neon-amber); }

.kpi-body { display: grid; gap: 5px; min-width: 0; }
.kpi-body p { margin: 0; color: var(--text-dim); font-size: 12px; letter-spacing: .14em; }
.kpi-body strong {
  display: block;
  color: white;
  font-size: 32px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: .04em;
  line-height: 1.1;
  text-shadow: 0 0 16px currentColor;
}
.kpi-body strong em { margin-left: 6px; font-size: 13px; font-style: normal; font-weight: 500; color: var(--text-dim); }
.kpi-body small { color: var(--text-dim); font-size: 12px; letter-spacing: .04em; }

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
  background-color: #050a1a !important;
  background-image: linear-gradient(135deg, rgba(0, 240, 255, .06), rgba(2, 6, 15, .82)) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 1px rgba(0, 240, 255, .08), 0 6px 20px rgba(0, 0, 0, .35);
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
  background: linear-gradient(90deg, rgba(0, 240, 255, .12), transparent 70%);
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
  font-size: 15px;
  letter-spacing: .12em;
  color: white;
  font-weight: 700;
}
.cockpit-panel h2::before {
  content: "";
  width: 4px; height: 16px;
  background: linear-gradient(180deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: 0 0 8px var(--neon-cyan);
  border-radius: 1px;
}
.cockpit-panel > header > span { color: var(--text-dim); font-size: 12px; letter-spacing: .08em; }

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
  filter: drop-shadow(0 0 18px rgba(0, 240, 255, .35));
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
  background: radial-gradient(circle at 50% 50%, rgba(2, 6, 15, .9), rgba(2, 6, 15, .65));
  box-shadow: inset 0 0 18px rgba(0, 240, 255, .25), inset 0 0 0 1px var(--panel-stroke);
}
.category-donut strong {
  font-size: 22px;
  font-weight: 800;
  color: white;
  text-shadow: 0 0 12px var(--neon-cyan);
  font-variant-numeric: tabular-nums;
}
.category-donut span { color: var(--text-dim); font-size: 11px; letter-spacing: .12em; }
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
.category-legend .legend-label { font-size: 13px; color: var(--text-main); letter-spacing: .04em; }
.category-legend strong { font-size: 15px; font-variant-numeric: tabular-nums; color: white; }
.category-legend small { grid-column: 3 / 5; color: var(--text-dim); text-align: right; font-size: 12px; font-variant-numeric: tabular-nums; }

/* 月度趋势 */
.trend-chart { height: 236px; padding: 18px 18px 0; display: flex; flex-direction: column; min-height: 0; }
.trend-chart svg { width: 100%; flex: 1; min-height: 0; overflow: visible; }
.trend-grid line { stroke: rgba(0, 240, 255, .18); stroke-width: .35; stroke-dasharray: 2 2; }
.trend-chart polyline {
  fill: none;
  stroke: var(--neon-cyan);
  stroke-width: 1.6;
  vector-effect: non-scaling-stroke;
  filter: drop-shadow(0 0 6px var(--neon-cyan));
}
.trend-chart circle { fill: var(--neon-cyan); stroke: var(--bg-deep); stroke-width: .6; filter: drop-shadow(0 0 4px var(--neon-cyan)); }
.trend-labels { display: grid; grid-template-columns: repeat(12, 1fr); color: var(--text-dim); font-size: 10px; text-align: center; padding-top: 6px; letter-spacing: .04em; }
.trend-panel footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--panel-stroke-soft);
  padding: 10px 18px;
  color: var(--text-dim);
  font-size: 12px;
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
  font-size: 11px;
  letter-spacing: .14em;
  flex: 0 0 auto;
}
.department-ranking { display: grid; gap: 9px; padding: 6px 14px 12px; overflow: auto; align-content: start; }
.department-row {
  display: grid;
  grid-template-columns: 22px 84px minmax(70px, 1fr) 56px 50px 60px;
  align-items: center;
  gap: 8px;
  font-size: 12px;
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
  color: #02060f;
  background: var(--neon-cyan);
  font-weight: 800;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 0 10px var(--neon-cyan);
}
.rank-no.rank-1 {
  background: linear-gradient(135deg, #fff7a0, var(--neon-amber));
  color: #4a2800;
  box-shadow: 0 0 14px rgba(255, 184, 77, .65);
}
.rank-no.rank-2 {
  background: linear-gradient(135deg, #f0f4ff, #a8b8d4);
  color: #1a2030;
  box-shadow: 0 0 12px rgba(168, 184, 212, .5);
}
.rank-no.rank-3 {
  background: linear-gradient(135deg, #ffd0a0, #b67a4a);
  color: #3a1a00;
  box-shadow: 0 0 12px rgba(182, 122, 74, .55);
}
.department-row > strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-main); }
.rank-bar {
  position: relative;
  height: 14px;
  background: rgba(0, 240, 255, .08);
  border: 1px solid var(--panel-stroke-soft);
  border-radius: 2px;
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 0 1px 1px 0;
  background: linear-gradient(90deg, var(--neon-cyan), var(--neon-blue));
  box-shadow: 0 0 10px rgba(0, 240, 255, .55);
  position: relative;
}
.rank-bar i::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 30%, rgba(255, 255, 255, .4) 50%, transparent 70%);
  animation: cockpit-shine 2.5s linear infinite;
}
@keyframes cockpit-shine { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }
.department-row > span, .department-row > small, .department-row > em { font-variant-numeric: tabular-nums; text-align: right; }
.department-row > small { color: var(--text-dim); }
.department-row > em { font-style: normal; font-weight: 700; font-size: 12px; }

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
  background: linear-gradient(180deg, transparent 0%, rgba(0, 240, 255, .03) 50%, transparent 100%);
  pointer-events: none;
}
.focus-bars i { position: relative; width: 36%; min-height: 4px; border-radius: 2px 2px 0 0; transition: filter .2s; }
.focus-bars .current { background: linear-gradient(180deg, var(--neon-cyan), #0a4a6a); box-shadow: 0 0 14px rgba(0, 240, 255, .45); }
.focus-bars .previous { background: linear-gradient(180deg, var(--neon-blue), #0a2a5a); }
.focus-bars .current span {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  font-weight: 800;
  color: var(--neon-cyan);
  background: rgba(2, 6, 15, .85);
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  border: 1px solid var(--panel-stroke-soft);
  text-shadow: 0 0 6px var(--neon-cyan);
}
.focus-item > strong { overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-size: 11px; color: var(--text-main); letter-spacing: .04em; }

/* 耗材分类对比 */
.compare-panel { grid-column: 2 / 4; }
.compare-scroll { display: flex; flex-direction: column; min-height: 0; flex: 1; }
.compare-panel table { width: calc(100% - 28px); margin: 12px 14px 0; border-collapse: collapse; font-size: 12px; }
.compare-panel th, .compare-panel td {
  border: 1px solid var(--panel-stroke-soft);
  padding: 10px 12px;
  text-align: right;
  background: transparent;
  color: var(--text-main);
}
.compare-panel th:first-child, .compare-panel td:first-child { text-align: left; }
.compare-panel th {
  color: var(--neon-cyan);
  background: linear-gradient(180deg, rgba(0, 240, 255, .14), rgba(0, 240, 255, .04)) !important;
  font-weight: 700;
  letter-spacing: .08em;
  text-shadow: 0 0 6px rgba(0, 240, 255, .35);
}
.compare-panel tbody tr:hover td { background: rgba(0, 240, 255, .05) !important; }
.compare-panel tbody td:nth-child(2) { color: var(--neon-cyan); font-weight: 700; text-shadow: 0 0 8px rgba(0, 240, 255, .45); }
.compare-panel tfoot td { color: var(--neon-amber) !important; font-weight: 800; background: linear-gradient(180deg, rgba(255, 184, 77, .12), rgba(255, 184, 77, .02)) !important; text-shadow: 0 0 8px rgba(255, 184, 77, .35); }
.caliber-note { margin: 10px 16px 12px; color: var(--text-dim); font-size: 11px; letter-spacing: .08em; }

/* 库存与异常提醒 */
.alert-panel > header button {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--panel-stroke);
  border-radius: 4px;
  color: var(--neon-cyan);
  background: rgba(0, 240, 255, .06);
  padding: 5px 12px;
  font: inherit;
  font-size: 12px;
  letter-spacing: .1em;
  cursor: pointer;
  transition: background .2s, border-color .2s, box-shadow .2s;
}
.alert-panel > header button:hover:not(:disabled) {
  background: rgba(0, 240, 255, .16);
  border-color: var(--neon-cyan);
  box-shadow: 0 0 12px rgba(0, 240, 255, .45);
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
  background: rgba(255, 77, 109, .14);
  padding: 5px 8px;
  font-size: 11px;
  text-align: center;
  font-weight: 700;
  letter-spacing: .12em;
  border: 1px solid rgba(255, 77, 109, .45);
  text-shadow: 0 0 6px rgba(255, 77, 109, .45);
}
.alert-info { display: grid; gap: 4px; min-width: 0; }
.alert-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--text-main); }
.alert-info small { color: var(--text-dim); font-size: 11px; font-variant-numeric: tabular-nums; }
.alert-panel > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--panel-stroke-soft);
  padding: 10px 14px;
  color: var(--text-dim);
  font-size: 11px;
  letter-spacing: .12em;
  flex: 0 0 auto;
}
.alert-panel > footer span { display: flex; align-items: center; gap: 6px; }
.alert-panel > footer strong { color: var(--neon-red); font-weight: 800; text-shadow: 0 0 6px rgba(255, 77, 109, .5); }

.cockpit-empty {
  grid-column: 1 / -1;
  align-self: center;
  margin: auto;
  color: var(--text-dim);
  text-align: center;
  font-size: 13px;
  letter-spacing: .14em;
  padding: 18px 0;
}

@media (max-width: 1400px) {
  .operation-cockpit-page { min-width: 1080px; padding: 14px; }
  .cockpit-header { grid-template-columns: 220px 210px 1fr 200px; }
  .cockpit-grid { grid-template-columns: 1fr 1.28fr 1.05fr; }
  .department-row { grid-template-columns: 20px 70px minmax(55px, 1fr) 50px 46px 54px; gap: 5px; }
  .cockpit-title h1 { font-size: 26px; }
  .kpi-body strong { font-size: 28px; }
}

/* 暗色滚动条 */
.operation-cockpit-page ::-webkit-scrollbar { width: 8px; height: 8px; }
.operation-cockpit-page ::-webkit-scrollbar-track { background: rgba(0, 240, 255, .04); }
.operation-cockpit-page ::-webkit-scrollbar-thumb { background: linear-gradient(180deg, var(--neon-cyan), var(--neon-blue)); border-radius: 4px; box-shadow: 0 0 6px rgba(0, 240, 255, .35); }
.operation-cockpit-page ::-webkit-scrollbar-thumb:hover { background: linear-gradient(180deg, var(--neon-cyan), var(--neon-purple)); }

/* Element Plus tooltip 暗色化（环形图 hover 显示的"高值/定数..."） */
.operation-cockpit-page .el-popper,
.operation-cockpit-page .el-tooltip__popper,
.operation-cockpit-page .el-tooltip-popper {
  background: rgba(2, 6, 15, .92) !important;
  color: var(--text-main) !important;
  border: 1px solid var(--panel-stroke) !important;
  box-shadow: 0 0 14px rgba(0, 240, 255, .35) !important;
  backdrop-filter: blur(6px);
}
.operation-cockpit-page .el-popper .el-popper__arrow::before,
.operation-cockpit-page .el-tooltip__popper .el-popper__arrow::before {
  background: rgba(2, 6, 15, .92) !important;
  border-color: var(--panel-stroke) !important;
}

/* 选区颜色 */
.operation-cockpit-page ::selection { background: rgba(0, 240, 255, .35); color: white; }
</style>
