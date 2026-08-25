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
      <article><span class="kpi-symbol cyan"><Database :size="26" /></span><div><p>本月耗材金额</p><strong>{{ formatWan(data.summary.currentAmount) }}<em>万元</em></strong><small>上月 {{ formatWan(data.summary.previousAmount) }} 万元</small></div></article>
      <article><span class="kpi-symbol coral"><TrendingUp :size="26" /></span><div><p>环比增长</p><strong :class="trendTone(data.summary.monthOnMonth)">{{ formatPercent(data.summary.monthOnMonth) }}</strong><small>按科室消耗事实金额统计</small></div></article>
      <article><span class="kpi-symbol blue"><Building2 :size="26" /></span><div><p>消耗科室</p><strong>{{ data.summary.departmentCount }}<em>个</em></strong><small>统计月份内产生消耗的科室</small></div></article>
      <article><span class="kpi-symbol amber"><BellRing :size="26" /></span><div><p>预警事项</p><strong>{{ data.summary.warningCount }}<em>条</em></strong><small>重点监控耗材库存预警</small></div></article>
    </section>

    <section class="cockpit-grid">
      <article class="cockpit-panel category-panel">
        <header><h2>耗材消费构成</h2><span>单位：万元</span></header>
        <div class="category-content">
          <div class="category-donut" :style="{ background: categoryGradient }"><div><strong>{{ formatWan(totalCategoryAmount) }}</strong><span>万元</span></div></div>
          <div class="category-legend">
            <div v-for="(item, index) in data.categories" :key="item.category">
              <i :class="`color-${index}`"></i><span>{{ item.category }}</span><strong>{{ formatWan(item.currentAmount) }}</strong><small>{{ totalCategoryAmount ? (item.currentAmount / totalCategoryAmount * 100).toFixed(1) : '0.0' }}%</small>
            </div>
            <p v-if="!data.categories.length">暂无目录分类消耗数据</p>
          </div>
        </div>
      </article>

      <article class="cockpit-panel trend-panel">
        <header><h2>月度使用趋势</h2><span class="legend-line"><i></i>消耗金额</span></header>
        <div class="trend-chart">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="近十二个月耗材使用趋势">
            <defs><linearGradient id="cockpitTrendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#20c7c1" stop-opacity=".42"/><stop offset="1" stop-color="#20c7c1" stop-opacity="0"/></linearGradient></defs>
            <line v-for="line in [20, 44, 68, 92]" :key="line" x1="5" x2="95" :y1="line" :y2="line" />
            <polygon :points="trendArea" fill="url(#cockpitTrendFill)" />
            <polyline :points="trendPolyline" />
            <circle v-for="point in trendPoints" :key="point.month" :cx="point.x" :cy="point.y" r="1.35"><title>{{ point.month }}：{{ formatWan(point.amount) }}万元</title></circle>
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
            <span class="rank-no">{{ index + 1 }}</span><strong :title="item.name">{{ item.name }}</strong>
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
            <div class="focus-bars"><i class="current" :style="{ height: `${Math.max(2, Number(item.currentAmount) / maxFocusedProduct * 100)}%` }"><span>{{ formatWan(item.currentAmount) }}</span></i><i class="previous" :style="{ height: `${Math.max(2, Number(item.previousAmount) / maxFocusedProduct * 100)}%` }"></i></div>
            <strong :title="item.name">{{ item.name }}</strong>
          </div>
          <p v-if="!data.focusedProducts.length" class="cockpit-empty">医院目录暂未勾选重点监控耗材，或统计月暂无消耗</p>
        </div>
      </article>

      <article class="cockpit-panel compare-panel">
        <header><h2>耗材分类对比</h2><span>单位：万元</span></header>
        <table>
          <thead><tr><th>分类</th><th>本月</th><th>上月</th><th>环比</th></tr></thead>
          <tbody>
            <tr v-for="item in data.categories" :key="item.category"><td>{{ item.category }}</td><td>{{ formatWan(item.currentAmount) }}</td><td>{{ formatWan(item.previousAmount) }}</td><td :class="trendTone(item.monthOnMonth)">{{ formatPercent(item.monthOnMonth) }}</td></tr>
          </tbody>
          <tfoot><tr><td>合计</td><td>{{ formatWan(data.summary.currentAmount) }}</td><td>{{ formatWan(data.summary.previousAmount) }}</td><td :class="trendTone(data.summary.monthOnMonth)">{{ formatPercent(data.summary.monthOnMonth) }}</td></tr></tfoot>
        </table>
        <p class="caliber-note">口径：高值耗材；定数管理且收费；定数管理且不收费。</p>
      </article>

      <article class="cockpit-panel alert-panel">
        <header><h2><AlertTriangle :size="18" />库存与异常提醒</h2><button type="button" :disabled="loading" @click="loadCockpit"><RefreshCw :size="15" />刷新</button></header>
        <div class="alert-list">
          <div v-for="item in data.alerts" :key="item.productCode"><span>{{ item.alertType }}</span><div><strong>{{ item.productName }}</strong><small>{{ item.productCode }} · 可用 {{ item.availableQty }} / 阈值 {{ item.threshold }}</small></div></div>
          <p v-if="!data.alerts.length" class="cockpit-empty">重点监控耗材暂无库存异常</p>
        </div>
        <footer><span><AlertTriangle :size="16" />库存不足 <strong>{{ data.summary.warningCount }}</strong> 条</span><small>数据来源：医院目录、库存余额、科室消耗</small></footer>
      </article>
    </section>
  </main>
</template>
