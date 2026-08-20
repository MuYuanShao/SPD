<script setup lang="ts">
import {
  AlertCircle,
  BarChart3,
  Bell,
  Box,
  ClipboardCheck,
  ClipboardList,
  Database,
  FileCheck2,
  Grid2X2,
  PackageCheck,
  QrCode,
  ScanLine,
  ShoppingCart,
  Truck,
  WalletCards
} from '@lucide/vue'
import { formatStatusText } from '../utils/chineseDisplay'

const metricCards = [
  { label: '在库品种', value: '12,845', delta: '+2.5%', trend: 'up', icon: Box, tone: 'blue' },
  { label: '今日订单', value: '342', delta: '+12.3%', trend: 'up', icon: ShoppingCart, tone: 'green' },
  { label: '低库存预警', value: '28', delta: '-5.1%', trend: 'down', icon: AlertCircle, tone: 'orange' },
  { label: '月采购总额', value: '¥ 1.2M', delta: '+4.8%', trend: 'up', icon: BarChart3, tone: 'violet' }
]

const processRows = [
  {
    label: '主数据与采购',
    steps: [
      { label: '商品准入', icon: Database, to: '/features/hospital-product-catalog' },
      { label: '目录审核', icon: FileCheck2, to: '/features/pending-product-catalog', badge: 3 },
      { label: '采购订单', icon: ShoppingCart, to: '/features/purchase-management' },
      { label: '到货验收', icon: Truck, to: '/features/receiving-acceptance' },
      { label: '批次生成', icon: QrCode, to: '/features/inventory-management' }
    ]
  },
  {
    label: '库存与定数',
    steps: [
      { label: '中心库入账', icon: Box, to: '/features/inventory-management' },
      { label: '散货 / 成包模式', icon: Grid2X2, to: '/features/quota-package-template', mode: true },
      { label: '打包任务', icon: ClipboardList, to: '/features/quota-package-template', badge: 12 },
      { label: '生成定数包', icon: PackageCheck, to: '/features/quota-safety-stock' }
    ]
  },
  {
    label: '临床与结算',
    steps: [
      { label: '科室申领', icon: ClipboardCheck, to: '/features/department-requisition' },
      { label: '配送及签收', icon: Truck, to: '/features/picking-delivery' },
      { label: '扫码消耗', icon: ScanLine, to: '/features/department-consumption' },
      { label: '消耗事实', icon: ClipboardList, to: '/features/department-consumption' },
      { label: '财务结算', icon: WalletCards, to: '/features/settlement-reconciliation' }
    ]
  }
]

const trendPoints = '0,80 90,126 180,164 270,132 360,156 450,114 540,146 630,78'
const areaPoints = `${trendPoints} 630,250 0,250`
const departments = [
  { name: '骨科', value: 45000 },
  { name: '心内科', value: 38000 },
  { name: '手术室', value: 32000 },
  { name: '重症医学', value: 28000 },
  { name: '急诊科', value: 21000 }
]

const exceptions = [
  { node: '订单价格校验', no: 'PO-20260525-01', reason: '目录价(¥12)与订单价(¥15)不符', status: '待确认' },
  { node: '打包装配', no: 'TSK-20260525-18', reason: '库存预占超时释放', status: '已取消' },
  { node: '财务结算', no: 'SET-20260524-09', reason: '存在误消耗争议需红冲', status: '处理中' }
]

const notices = [
  { title: '接口同步失败', detail: 'HIS 系统入库流水推送超时', time: '10分钟前', tone: 'red' },
  { title: '库存预警触发', detail: '骨科高值耗材低于安全定数', time: '2小时前', tone: 'orange' },
  { title: '规则引擎更新', detail: '新的自动计费防错规则已生效', time: '昨天', tone: 'blue' }
]
</script>

<template>
  <section class="workbench-hero">
    <div>
      <h2>工作台首页</h2>
      <p>欢迎回来，今日待办事项已更新，请关注系统流转状态。</p>
    </div>
  </section>

  <section class="kpi-grid">
    <article v-for="card in metricCards" :key="card.label" class="kpi-card">
      <div>
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </div>
      <div class="kpi-side">
        <span class="kpi-icon" :class="card.tone">
          <component :is="card.icon" :size="24" />
        </span>
        <span class="kpi-delta" :class="card.trend">{{ card.delta }} ↗</span>
      </div>
    </article>
  </section>

  <section class="process-card">
    <div class="section-title">
      <Grid2X2 :size="22" />
      <h2>SPD 核心业务全景图</h2>
    </div>

    <div class="process-map">
      <div v-for="row in processRows" :key="row.label" class="process-row">
        <div class="process-label">{{ row.label }}</div>
        <div class="process-steps">
          <template v-for="(step, index) in row.steps" :key="step.label">
            <RouterLink class="process-node" :to="step.to" :class="{ wide: step.mode }">
              <span v-if="step.badge" class="node-badge">{{ step.badge }}</span>
              <component :is="step.icon" :size="25" />
              <em v-if="step.mode">散货&nbsp;&nbsp; 成包模式</em>
              <strong v-else>{{ step.label }}</strong>
            </RouterLink>
            <span v-if="index < row.steps.length - 1" class="process-arrow">→</span>
          </template>
        </div>
      </div>
    </div>
  </section>

  <section class="analytics-grid">
    <article class="analytics-card">
      <div class="card-heading">
        <div class="section-title">
          <BarChart3 :size="20" />
          <h2>近7日入库与消耗趋势</h2>
        </div>
        <button class="btn" type="button">金额（元）</button>
      </div>
      <svg class="trend-chart" viewBox="0 0 640 270" role="img" aria-label="近7日入库与消耗趋势">
        <line v-for="y in [40, 90, 140, 190, 240]" :key="y" x1="0" :y1="y" x2="640" :y2="y" />
        <polygon :points="areaPoints" />
        <polyline :points="trendPoints" />
      </svg>
      <div class="chart-legend">
        <span><i class="blue-dot"></i>入库金额</span>
        <span><i class="green-dot"></i>消耗金额</span>
      </div>
    </article>

    <article class="analytics-card">
      <div class="card-heading">
        <div class="section-title">
          <BarChart3 :size="20" />
          <h2>重点科室耗材成本 TOP 5</h2>
        </div>
        <span>本月累计</span>
      </div>
      <div class="bar-ranking">
        <div v-for="dept in departments" :key="dept.name" class="bar-row">
          <span>{{ dept.name }}</span>
          <div><i :style="{ width: `${dept.value / 600}%` }"></i></div>
        </div>
      </div>
    </article>
  </section>

  <section class="bottom-grid">
    <article class="exception-card">
      <div class="card-heading">
        <h2>异常任务干预</h2>
        <RouterLink to="/features/todo-tasks">查看全部</RouterLink>
      </div>
      <table>
        <thead>
          <tr>
            <th>任务节点</th>
            <th>业务编号</th>
            <th>异常原因</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in exceptions" :key="item.no">
            <td>{{ item.node }}</td>
            <td>{{ item.no }}</td>
            <td class="danger-cell">{{ item.reason }}</td>
            <td><span class="state-chip">{{ formatStatusText(item.status) }}</span></td>
          </tr>
        </tbody>
      </table>
    </article>

    <article class="notice-card">
      <div class="section-title">
        <Bell :size="20" />
        <h2>系统监控与公告</h2>
      </div>
      <div class="notice-list">
        <div v-for="item in notices" :key="item.title" class="notice-item">
          <i :class="item.tone"></i>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.detail }}</p>
            <span>{{ item.time }}</span>
          </div>
        </div>
      </div>
    </article>
  </section>
</template>
