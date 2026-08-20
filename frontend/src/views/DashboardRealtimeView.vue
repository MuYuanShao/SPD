<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertCircle, BarChart3, Bell, Box, ClipboardCheck, ClipboardList, Database,
  FileCheck2, Grid2X2, PackageCheck, QrCode, ScanLine, ShoppingCart, Truck, WalletCards
} from '@lucide/vue'
import { fetchDashboard, type DashboardData } from '../api/dashboard'
import { useAuthStore } from '../stores/auth'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../utils/chineseDisplay'

const authStore = useAuthStore()

const loading = ref(false)
const error = ref('')
const data = ref<DashboardData>({
  metrics: { productCount: 0, todayOrders: 0, lowStockCount: 0, monthlyPurchaseAmount: 0 },
  trend: [], departments: [], exceptions: [], notices: []
})

const metricCards = computed(() => [
  { label: '在库品种', value: data.value.metrics.productCount.toLocaleString(), icon: Box, tone: 'blue' },
  { label: '今日订单', value: data.value.metrics.todayOrders.toLocaleString(), icon: ShoppingCart, tone: 'green' },
  { label: '低库存预警', value: data.value.metrics.lowStockCount.toLocaleString(), icon: AlertCircle, tone: 'orange' },
  { label: '月采购总额', value: formatMoney(data.value.metrics.monthlyPurchaseAmount), icon: BarChart3, tone: 'violet' }
])
const maxDepartmentAmount = computed(() => Math.max(1, ...data.value.departments.map(item => Number(item.amount))))

const processRows = [
  { label: '主数据与采购', steps: [
    { label: '商品准入', icon: Database, to: '/features/hospital-product-catalog' },
    { label: '目录审核', icon: FileCheck2, to: '/features/pending-product-catalog' },
    { label: '采购订单', icon: ShoppingCart, to: '/features/purchase-management' },
    { label: '到货验收', icon: Truck, to: '/features/receiving-acceptance' },
    { label: '批次库存', icon: QrCode, to: '/features/inventory-management' }
  ]},
  { label: '库存与定数', steps: [
    { label: '库存余额', icon: Box, to: '/features/inventory-management' },
    { label: '模板维护', icon: Grid2X2, to: '/features/quota-template-maintenance' },
    { label: '打包任务', icon: ClipboardList, to: '/features/packing-task-confirmation' },
    { label: '定数安全量', icon: PackageCheck, to: '/features/quota-safety-stock' }
  ]},
  { label: '临床与结算', steps: [
    { label: '科室申领', icon: ClipboardCheck, to: '/features/department-requisition' },
    { label: '配送签收', icon: Truck, to: '/features/picking-delivery' },
    { label: '扫码消耗', icon: ScanLine, to: '/features/department-consumption' },
    { label: '结算对账', icon: WalletCards, to: '/features/settlement-reconciliation' },
    { label: '发票管理', icon: FileCheck2, to: '/features/invoice-management' }
  ]}
]

const visibleProcessRows = computed(() => processRows
  .map(row => ({ ...row, steps: row.steps.filter(step => authStore.canAccessMenu(step.to.split('/').pop() ?? '')) }))
  .filter(row => row.steps.length > 0))

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(Number(value || 0))
}

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try { data.value = await fetchDashboard() }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '首页数据加载失败' }
  finally { loading.value = false }
}

onMounted(loadDashboard)
</script>

<template>
  <section class="workbench-hero">
    <div><h2>工作台首页</h2><p>以下指标、趋势与异常均来自当前 MySQL 业务数据。</p></div>
    <button class="btn" type="button" :disabled="loading" @click="loadDashboard">{{ loading ? '刷新中...' : '刷新' }}</button>
  </section>
  <p v-if="error" class="inline-message error">{{ error }}</p>

  <section class="kpi-grid">
    <article v-for="card in metricCards" :key="card.label" class="kpi-card">
      <div><p>{{ card.label }}</p><strong>{{ card.value }}</strong></div>
      <div class="kpi-side"><span class="kpi-icon" :class="card.tone"><component :is="card.icon" :size="24" /></span><span class="kpi-delta">实时</span></div>
    </article>
  </section>

  <section class="process-card">
    <div class="section-title"><Grid2X2 :size="22" /><h2>SPD 核心业务全景图</h2></div>
    <div class="process-map">
      <div v-for="row in visibleProcessRows" :key="row.label" class="process-row">
        <div class="process-label">{{ row.label }}</div>
        <div class="process-steps">
          <template v-for="(step, index) in row.steps" :key="step.label">
            <RouterLink class="process-node" :to="step.to"><component :is="step.icon" :size="25" /><strong>{{ step.label }}</strong></RouterLink>
            <span v-if="index < row.steps.length - 1" class="process-arrow">→</span>
          </template>
        </div>
      </div>
    </div>
  </section>

  <section class="analytics-grid">
    <article class="analytics-card">
      <div class="card-heading"><div class="section-title"><BarChart3 :size="20" /><h2>近 7 日入库与消耗</h2></div><span>金额（元）</span></div>
      <table class="dashboard-data-table">
        <thead><tr><th>日期</th><th>入库金额</th><th>消耗金额</th></tr></thead>
        <tbody>
          <tr v-for="item in data.trend" :key="item.date"><td>{{ item.label }}</td><td>{{ formatMoney(item.receivingAmount) }}</td><td>{{ formatMoney(item.consumptionAmount) }}</td></tr>
          <tr v-if="!data.trend.length"><td colspan="3">暂无数据</td></tr>
        </tbody>
      </table>
    </article>
    <article class="analytics-card">
      <div class="card-heading"><div class="section-title"><BarChart3 :size="20" /><h2>重点科室耗材成本 TOP 5</h2></div><span>本月累计</span></div>
      <div v-if="data.departments.length" class="bar-ranking">
        <div v-for="dept in data.departments" :key="dept.deptName" class="bar-row"><span>{{ dept.deptName }}</span><div><i :style="{ width: `${Number(dept.amount) / maxDepartmentAmount * 100}%` }"></i></div></div>
      </div>
      <p v-else class="dashboard-empty">暂无科室消耗数据</p>
    </article>
  </section>

  <section class="bottom-grid">
    <article class="exception-card">
      <div class="card-heading"><h2>业务异常</h2></div>
      <table class="dashboard-data-table">
        <thead><tr><th>类型</th><th>业务编号</th><th>异常原因</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="item in data.exceptions" :key="item.bizNo"><td>{{ item.riskEvents }}</td><td>{{ item.bizNo }}</td><td class="danger-cell">{{ item.reason }}</td><td><span class="state-chip">{{ formatStatusText(item.status) }}</span></td></tr>
          <tr v-if="!data.exceptions.length"><td colspan="4">暂无异常</td></tr>
        </tbody>
      </table>
    </article>
    <article class="notice-card">
      <div class="section-title"><Bell :size="20" /><h2>最近系统操作</h2></div>
      <div class="notice-list">
        <div v-for="item in data.notices" :key="`${item.operationTime}-${item.title}`" class="notice-item"><i class="blue"></i><div><strong>{{ formatBusinessText(item.title) }}</strong><p>{{ formatRemarkText(item.detail) }}</p><span>{{ item.operationTime }} · {{ item.operatorName }}</span></div></div>
        <p v-if="!data.notices.length" class="dashboard-empty">暂无操作记录</p>
      </div>
    </article>
  </section>
</template>

<style scoped>
.workbench-hero { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.dashboard-data-table { width:100%; border-collapse:collapse; }
.dashboard-data-table th,.dashboard-data-table td { padding:11px 10px; border-bottom:1px solid var(--line); text-align:left; }
.dashboard-data-table th { color:var(--text-muted); font-weight:600; }
.dashboard-empty { padding:32px 0; color:var(--text-muted); text-align:center; }
</style>
