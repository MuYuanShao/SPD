<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Banknote, Package, PackagePlus, Truck, HeartPulse, UserRound } from '@lucide/vue'
import StatisticCard from '../components/business/StatisticCard.vue'
import AlertCard from '../components/business/AlertCard.vue'
import NoticeList, { type NoticeItem } from '../components/business/NoticeList.vue'
import TodoList, { type TodoItem } from '../components/business/TodoList.vue'
import TrendChart from '../components/business/TrendChart.vue'
import { useHomeWorkbench } from '../composables/useHomeWorkbench'
import { useLayoutNavigation } from '../layout/useLayoutNavigation'
import { flattenMenu } from '../config/menu'
import { featureRouteTarget } from '../config/featureCatalog'

const { auth, groups } = useLayoutNavigation()
const { error, refresh,
  alerts, todos, todoTotal, notices, labels, movementSeries, usageSeries, status, statusDetail, metric } = useHomeWorkbench()
const availablePages = computed(() => flattenMenu(groups.value).filter(item => !item.children?.length))
const selectedCodes = ref<string[]>([])
watch(availablePages, pages => {
  const allowed = new Set(pages.map(item => item.code))
  selectedCodes.value = selectedCodes.value.length
    ? selectedCodes.value.filter(code => allowed.has(code))
    : pages.slice(0, 4).map(item => item.code)
}, { immediate: true })
const shortcuts = computed(() => availablePages.value.filter(item => selectedCodes.value.includes(item.code)))
const configuring = ref(false)
const detail = ref<{ title: string; content: string }>()
function showNotice(item: NoticeItem) { detail.value = { title: item.title, content: item.content } }
function showTodo(item: TodoItem) { detail.value = { title: item.title, content: '单据编号：' + item.id + '；当前状态：' + item.status + '。请在对应业务模块处理，首页只展示授权范围内的真实待办。' } }
</script>
<template>
  <section class="fli-dashboard" aria-label="首页工作台">
    <div class="fli-dashboard-heading"><h1>首页工作台</h1><el-tag size="small" :type="error ? 'danger' : 'info'" effect="plain" :title="statusDetail" role="button" tabindex="0" @click="refresh" @keyup.enter="refresh">{{ status }}</el-tag></div>
    <el-row :gutter="16" class="fli-statistics">
      <el-col :xs="12" :sm="6"><StatisticCard title="今日销售额" :value="metric('salesAmount')" unit="元" tone="orange" :icon="Banknote" /></el-col>
      <el-col :xs="12" :sm="6"><StatisticCard title="今日售出数量" :value="metric('salesQuantity')" unit="件" tone="teal" :icon="Package" /></el-col>
      <el-col :xs="12" :sm="6"><StatisticCard title="今日入库" :value="metric('inboundQuantity')" unit="件" tone="purple" :icon="PackagePlus" /></el-col>
      <el-col :xs="12" :sm="6"><StatisticCard title="今日出库" :value="metric('outboundQuantity')" unit="件" tone="blue" :icon="Truck" /></el-col>
    </el-row>
    <el-row :gutter="16" class="fli-columns">
      <el-col :xs="24" :sm="12" :md="7">
        <el-card shadow="never" class="fli-panel">
          <template #header><div class="fli-panel-heading"><h2>待办事项</h2><el-tag size="small" effect="light">{{ todoTotal }} 项待处理</el-tag></div></template>
          <TodoList :items="todos" @select="showTodo" />
        </el-card>
        <el-card shadow="never" class="fli-panel">
          <template #header><div class="fli-panel-heading"><h2>近30天出入库金额</h2><span>万元</span></div></template>
          <TrendChart :labels="labels" :series="movementSeries" title="近30天出入库金额趋势" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="10">
        <el-card shadow="never" class="fli-panel">
          <template #header><div class="fli-panel-heading"><h2>预警信息</h2><span>请及时关注</span></div></template>
          <div class="fli-alert-grid"><AlertCard v-for="alert in alerts" :key="alert.title" v-bind="alert" /></div>
        </el-card>
        <el-card shadow="never" class="fli-panel">
          <template #header><div class="fli-panel-heading"><h2>耗用与采购趋势</h2><span>近30天</span></div></template>
          <TrendChart :labels="labels" :series="usageSeries" title="近30天耗用与采购趋势" :height="225" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="7">
        <el-card shadow="never" class="fli-panel fli-profile">
          <div class="fli-profile-heading"><span class="fli-profile-avatar"><UserRound :size="26" /></span><div><h2>{{ auth.currentUser?.realName || auth.username }}，您好</h2><p>欢迎使用医用耗材精益管理平台</p></div></div>
          <div class="fli-panel-heading"><h3>可用功能</h3><el-button link type="primary" @click="configuring = true">设置</el-button></div>
          <div class="fli-shortcuts"><RouterLink v-for="item in shortcuts" :key="item.code" :to="featureRouteTarget(item.code)">{{ item.title }}</RouterLink><span v-if="!shortcuts.length">暂无已选功能</span></div>
        </el-card>
        <el-card shadow="never" class="fli-panel">
          <template #header><div class="fli-panel-heading"><h2>系统公告</h2><span>{{ notices.length }} 条</span></div></template>
          <NoticeList :items="notices" @select="showNotice" />
        </el-card>
        <aside class="fli-banner"><HeartPulse :size="38" :stroke-width="1.3" /><div><strong>精益管理 · 守护健康</strong><p>让每一份耗材，服务每一份安心</p></div></aside>
      </el-col>
    </el-row>
    <el-dialog :model-value="Boolean(detail)" :title="detail?.title" width="min(520px, 92vw)" @close="detail = undefined"><p>{{ detail?.content }}</p><template #footer><el-button type="primary" @click="detail = undefined">知道了</el-button></template></el-dialog>
    <el-dialog v-model="configuring" title="选择常用功能" width="min(640px, 92vw)">
      <el-checkbox-group v-model="selectedCodes" class="fli-feature-options"><el-checkbox v-for="item in availablePages" :key="item.code" :value="item.code">{{ item.title }}</el-checkbox></el-checkbox-group>
      <template #footer><el-button type="primary" @click="configuring = false">完成</el-button></template>
    </el-dialog>
  </section>
</template>
