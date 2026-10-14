<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, ClipboardCheck, Search, X } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import HighValueOperationsPanel from '../../components/supply-chain/HighValueOperationsPanel.vue'
import { useChargeDetailQuery } from '../../composables/useChargeDetailQuery'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
/** Tab 切换：detail（计费明细查询）/ operations（高值耗材计费操作）
 *  通过 ?tab=operations 在 URL 中保留状态，方便外部链接和回退 */
type ChargeTab = 'detail' | 'operations'
const isOperationsTab = computed<boolean>(() => String(route.query.tab || '') === 'operations')
const activeTab = computed<ChargeTab>(() => (auth.canWrite('high-value-consumables') && isOperationsTab.value) ? 'operations' : 'detail')

function switchTab(tab: ChargeTab) {
  if (tab === activeTab.value) return
  router.replace({ path: route.path, query: tab === 'operations' ? { tab: 'operations' } : {} })
}
/** 默认只展示常用条件，UDI/供应商/厂家/注册证折叠，避免首屏被筛选项占满 */
const showAllFilters = ref(false)
const { filters, rows, loading, message, validationMessage, currentPage, pageSize, totalItems,
  loadData, search, reset, changePage, changePageSize } = useChargeDetailQuery()
const tableScrollRef = ref<HTMLElement | null>(null)
const scrollTrackRef = ref<HTMLElement | null>(null)
const tableScrollState = ref({ left: 0, max: 0, width: 0, scrollWidth: 0 })
const scrollDragging = ref(false)
let scrollDragStartX = 0
let scrollDragStartLeft = 0
let resizeObserver: ResizeObserver | undefined

function money(value: number | null, digits = 2) {
  return value == null ? '-' : value.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

function updateTableScrollState() {
  const el = tableScrollRef.value
  if (!el) {
    tableScrollState.value = { left: 0, max: 0, width: 0, scrollWidth: 0 }
    return
  }
  tableScrollState.value = {
    left: el.scrollLeft,
    max: Math.max(0, el.scrollWidth - el.clientWidth), width: el.clientWidth, scrollWidth: el.scrollWidth
  }
}

const thumbWidthPercent = computed(() => {
  const { width, scrollWidth } = tableScrollState.value
  return scrollWidth ? Math.min(100, Math.max(8, width / scrollWidth * 100)) : 100
})
const thumbLeftPercent = computed(() => tableScrollState.value.max
  ? tableScrollState.value.left / tableScrollState.value.max * (100 - thumbWidthPercent.value) : 0)

function setChargeTableScrollLeft(left: number) {
  const el = tableScrollRef.value
  if (!el) return
  const max = Math.max(0, el.scrollWidth - el.clientWidth)
  const nextLeft = Math.min(max, Math.max(0, left))
  el.scrollLeft = nextLeft
  updateTableScrollState()
}

function scrollChargeTable(direction: 'left' | 'right') {
  const el = tableScrollRef.value
  if (!el) return
  updateTableScrollState()
  const distance = Math.max(360, Math.round(el.clientWidth * 0.72))
  setChargeTableScrollLeft(el.scrollLeft + (direction === 'left' ? -distance : distance))
}

function setChargeTableScrollFromTrack(event: PointerEvent) {
  const track = scrollTrackRef.value
  const el = tableScrollRef.value
  if (!track || !el) return
  const rect = track.getBoundingClientRect()
  const thumbWidth = rect.width * thumbWidthPercent.value / 100
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left - thumbWidth / 2) / Math.max(1, rect.width - thumbWidth)))
  setChargeTableScrollLeft((el.scrollWidth - el.clientWidth) * ratio)
}

function startChargeTrackDrag(event: PointerEvent) {
  const el = tableScrollRef.value
  if (!el) return
  scrollDragging.value = true
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  if (!(event.target as HTMLElement).classList.contains('charge-scroll-thumb')) setChargeTableScrollFromTrack(event)
  // 点击/拖拽起点以跳转后的位置为锚点，避免首次移动时回跳
  scrollDragStartX = event.clientX
  scrollDragStartLeft = el.scrollLeft
}

function moveChargeTrackDrag(event: PointerEvent) {
  if (!scrollDragging.value) return
  const track = scrollTrackRef.value
  const el = tableScrollRef.value
  if (!track || !el) return
  const max = Math.max(1, el.scrollWidth - el.clientWidth)
  const delta = ((event.clientX - scrollDragStartX) / Math.max(1, track.clientWidth * (1 - thumbWidthPercent.value / 100))) * max
  setChargeTableScrollLeft(scrollDragStartLeft + delta)
}

function endChargeTrackDrag(event: PointerEvent) {
  scrollDragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
}

watch(rows, async () => { await nextTick(); updateTableScrollState() })
onMounted(() => {
  resizeObserver = new ResizeObserver(updateTableScrollState)
  if (tableScrollRef.value) {
    resizeObserver.observe(tableScrollRef.value)
    const table = tableScrollRef.value.querySelector('table')
    if (table) resizeObserver.observe(table)
  }
  updateTableScrollState()
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <section class="charge-detail-page" aria-label="收费耗材明细查询">
    <MdPageHeader
      crumb="供应链业务"
      title="收费耗材明细查询"
      description="按患者、商品、日期、唯一码、厂家供应商等条件查询高值耗材计费明细"
      :loading="loading"
      @refresh="loadData"
    />

    <div v-if="auth.canWrite('high-value-consumables')" class="charge-tab-strip" role="tablist" aria-label="高值耗材计费模块">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'detail'"
        :class="['charge-tab', { 'is-active': activeTab === 'detail' }]"
        @click="switchTab('detail')"
      >
        <Search :size="16" />
        计费明细查询
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'operations'"
        :class="['charge-tab', { 'is-active': activeTab === 'operations' }]"
        @click="switchTab('operations')"
      >
        <ClipboardCheck :size="16" />
        高值耗材计费操作
      </button>
    </div>

    <HighValueOperationsPanel v-if="activeTab === 'operations'" />

    <template v-else>
    <StatusMessage :message="message" tone="error" role="alert" />
    <StatusMessage :message="validationMessage" tone="warning" role="alert" />

    <form class="charge-panel charge-filters" :class="{ 'is-expanded': showAllFilters }" @submit.prevent="search">
      <div class="charge-query-grid">
        <label><span>患者号/住院号</span><input v-model.trim="filters.patientNo" placeholder="患者号、住院号" /></label>
        <label><span>患者姓名</span><input v-model.trim="filters.patientName" placeholder="支持脱敏姓名" /></label>
        <label><span>商品编码</span><input v-model.trim="filters.productCode" placeholder="医院目录编码" /></label>
        <label><span>商品名称</span><input v-model.trim="filters.productName" placeholder="耗材名称" /></label>
        <label><span>开始日期</span><input v-model="filters.dateFrom" type="date" /></label>
        <label><span>结束日期</span><input v-model="filters.dateTo" type="date" /></label>
        <label><span>唯一码</span><input v-model.trim="filters.uid" placeholder="唯一码" /></label>
        <label class="charge-filter-extra"><span>UDI / DI</span><input v-model.trim="filters.udi" placeholder="UDI-DI / UDI" /></label>
        <label class="charge-filter-extra"><span>供应商</span><input v-model.trim="filters.supplierName" placeholder="供应商名称" /></label>
        <label class="charge-filter-extra"><span>生产厂家</span><input v-model.trim="filters.manufacturerName" placeholder="生产厂家" /></label>
        <label class="charge-filter-extra"><span>注册证号</span><input v-model.trim="filters.registrationNo" placeholder="注册证号" /></label>
      </div>
      <div class="charge-filter-actions">
        <button class="btn btn-primary" type="submit">
          <Search :size="16" />
          查询
        </button>
        <button class="btn" type="button" @click="reset">
          <X :size="16" />
          重置
        </button>
        <button class="btn charge-more-toggle" type="button" :aria-expanded="showAllFilters" @click="showAllFilters = !showAllFilters">
          {{ showAllFilters ? '收起条件' : '更多条件' }}
        </button>
      </div>
    </form>

    <section class="charge-panel" :aria-busy="loading">
      <div class="charge-section-title">
        <h3>收费明细</h3>
        <div class="charge-scroll-actions" aria-label="收费明细横向滚动控制">
          <button class="btn-icon" type="button" aria-label="向左滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('left')">
            <ChevronLeft :size="18" />
          </button>
          <button class="btn-icon" type="button" aria-label="向右滚动" :disabled="tableScrollState.max <= 0" @click="scrollChargeTable('right')">
            <ChevronRight :size="18" />
          </button>
        </div>
      </div>
      <div id="charge-detail-table-scroll" ref="tableScrollRef" class="table-scroll charge-detail-scroll" @scroll="updateTableScrollState">
        <table class="master-table purchase-table charge-detail-table">
          <thead>
            <tr>
              <th>计费单号</th>
              <th>患者信息</th>
              <th>商品信息</th>
              <th>供应商/厂家</th>
              <th>注册证</th>
              <th>UDI/唯一码</th>
              <th>收费数量</th>
              <th>单价</th>
              <th>金额</th>
              <th>计费时间</th>
            </tr>
          </thead>
          <tbody>
            <TableStateRow v-if="loading" :colspan="10" state="loading" message="正在加载收费耗材明细..." />
            <TableStateRow v-else-if="message" :colspan="10" state="error" message="加载失败，请点击刷新重试" />
            <TableStateRow v-else-if="!rows.length" :colspan="10" message="暂无收费耗材明细" />
            <tr v-for="row in rows" v-else :key="String(row.bizNo)">
              <td>
                <strong>{{ row.bizNo }}</strong>
                <span class="muted-cell">{{ row.externalChargeNo || '-' }}</span>
              </td>
              <td>
                <strong>{{ row.patientNo || '-' }}</strong>
                <span class="muted-cell">{{ row.patientName || '-' }}</span>
                <span class="muted-cell">{{ row.deptName || '-' }}</span>
              </td>
              <td>
                <strong>{{ row.productName || '-' }}</strong>
                <span class="muted-cell">{{ row.productCode || '-' }} / {{ row.specModel || '-' }}</span>
                <span class="muted-cell">{{ row.unit || '-' }}</span>
              </td>
              <td>
                <strong>{{ row.supplierName || '-' }}</strong>
                <span class="muted-cell">{{ row.manufacturerName || '-' }}</span>
              </td>
              <td>
                <strong>{{ row.registrationNo || '-' }}</strong>
                <span class="muted-cell">{{ row.registrationExpireDate || '-' }}</span>
              </td>
              <td>
                <strong>{{ row.uniqueCode || '-' }}</strong>
                <span class="muted-cell">{{ row.udiCode || '-' }}</span>
              </td>
              <td>{{ row.chargeQuantity ?? '-' }}</td>
              <td>{{ money(row.unitPrice, 4) }}</td>
              <td>{{ money(row.chargeAmount) }}</td>
              <td>{{ row.chargeTime || row.createTime || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="tableScrollState.max > 0" class="charge-scroll-hint">
        <span>左右滑动查看完整收费明细</span>
        <div
          ref="scrollTrackRef"
          class="charge-scroll-track"
          :class="{ dragging: scrollDragging }"
          role="scrollbar"
          aria-orientation="horizontal"
          aria-label="收费明细横向滚动条"
          aria-controls="charge-detail-table-scroll"
          :aria-valuemin="0"
          @keydown.left.prevent="scrollChargeTable('left')"
          @keydown.right.prevent="scrollChargeTable('right')"
          @keydown.home.prevent="setChargeTableScrollLeft(0)"
          @keydown.end.prevent="setChargeTableScrollLeft(tableScrollState.max)"
          :aria-valuenow="Math.round(tableScrollState.left)"
          :aria-valuemax="Math.round(tableScrollState.max)"
          tabindex="0"
          @pointerdown="startChargeTrackDrag"
          @pointermove="moveChargeTrackDrag"
          @pointerup="endChargeTrackDrag"
          @pointercancel="endChargeTrackDrag"
        >
          <span
            class="charge-scroll-thumb"
            :style="{
              width: tableScrollState.max > 0 ? `${thumbWidthPercent}%` : '100%',
              left: `${thumbLeftPercent}%`
            }"
          ></span>
        </div>
      </div>
      <PaginationControls
        :page="currentPage"
        :size="pageSize"
        :total="totalItems"
        :loading="loading"
        @change-page="changePage"
        @change-size="changePageSize"
      />
    </section>
    </template>
  </section>
</template>

<style scoped>
/* =====================================================================
   收费耗材明细查询
   全部使用项目设计令牌（--spd-*），与科室申领、UDI 追溯等页面保持一致。
   ===================================================================== */
.charge-detail-page {
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

.charge-detail-page h3,
.charge-detail-page p {
  margin: 0;
}

/* =====================================================================
   Tab 切换：计费明细查询 / 高值耗材计费操作
   - 同一页面内即时切换，无独立路由，无"丢失"问题
   - 与项目其他 Tab 控件一致，使用底部激活下划线 + 主题色描边
   ===================================================================== */
.charge-tab-strip {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin: -2px 0 0;
  padding: 0 4px;
  border-bottom: 1px solid var(--spd-border-subtle);
  background: transparent;
}

.charge-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 6px 6px 0 0;
  background: transparent;
  color: var(--spd-text-secondary, #607285);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.12s ease, border-color 0.12s ease, background 0.12s ease;
}

.charge-tab:hover {
  color: var(--spd-text-heading, #14213a);
  background: rgba(15, 83, 79, 0.05);
}

.charge-tab.is-active {
  color: var(--spd-primary, #0f534f);
  border-bottom-color: var(--spd-primary, #0f534f);
  background: rgba(15, 83, 79, 0.08);
}

.charge-tab:focus-visible {
  outline: 2px solid rgba(15, 83, 79, 0.35);
  outline-offset: 2px;
}

.charge-panel {
  box-sizing: border-box;
  min-width: 0;
  padding: 14px 16px 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

/* ---------- 查询条件 ---------- */
.charge-query-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr));
  gap: 10px 14px;
}

.charge-query-grid label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.charge-query-grid label > span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.charge-query-grid input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
}

.charge-query-grid input::placeholder {
  color: var(--spd-text-placeholder);
}

.charge-query-grid input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--spd-primary-bg);
}

/* 折叠态只保留常用条件，避免首屏被筛选项占满
   选择器需高于 .charge-query-grid label 的 display:grid 规则 */
.charge-filters .charge-filter-extra {
  display: none;
}

.charge-filters.is-expanded .charge-filter-extra {
  display: grid;
}

.charge-filter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--spd-border-subtle);
}

.charge-filter-actions .btn {
  height: 34px;
  white-space: nowrap;
}

.charge-more-toggle {
  margin-left: auto;
  color: var(--spd-text-secondary);
}

/* ---------- 面板标题 ---------- */
.charge-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.charge-section-title h3 {
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

/* ---------- 横向滚动控制 ---------- */
.charge-scroll-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.charge-scroll-actions .btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-bg-container);
  color: var(--spd-text-secondary);
  cursor: pointer;
}

.charge-scroll-actions .btn-icon:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--primary) 30%, transparent);
  color: var(--primary);
}

.charge-scroll-actions .btn-icon:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.charge-scroll-track:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

/* ---------- 表格 ---------- */
.charge-detail-scroll {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
}

.charge-detail-scroll::-webkit-scrollbar {
  display: none;
}

.charge-detail-table {
  min-width: 1780px;
  table-layout: fixed;
}

.charge-detail-table th,
.charge-detail-table td {
  white-space: normal;
  vertical-align: top;
}

.charge-detail-table th:nth-child(1),
.charge-detail-table td:nth-child(1),
.charge-detail-table th:nth-child(2),
.charge-detail-table td:nth-child(2) {
  width: 190px;
}

.charge-detail-table th:nth-child(3),
.charge-detail-table td:nth-child(3) {
  width: 300px;
}

.charge-detail-table th:nth-child(4),
.charge-detail-table td:nth-child(4) {
  width: 230px;
}

.charge-detail-table th:nth-child(5),
.charge-detail-table td:nth-child(5) {
  width: 210px;
}

.charge-detail-table th:nth-child(6),
.charge-detail-table td:nth-child(6) {
  width: 260px;
}

.charge-detail-table th:nth-child(7),
.charge-detail-table td:nth-child(7),
.charge-detail-table th:nth-child(8),
.charge-detail-table td:nth-child(8),
.charge-detail-table th:nth-child(9),
.charge-detail-table td:nth-child(9) {
  width: 110px;
  text-align: right;
}

.charge-detail-table th:nth-child(10),
.charge-detail-table td:nth-child(10) {
  width: 180px;
  white-space: nowrap;
}

/* ---------- 横向滚动条 ---------- */
.charge-scroll-hint {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr);
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.charge-scroll-track {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--spd-border-subtle);
  cursor: pointer;
  touch-action: none;
}

.charge-scroll-thumb {
  position: absolute;
  inset-block: 0;
  left: 0;
  border-radius: inherit;
  background: var(--spd-text-tertiary);
  transition: background-color 0.16s ease;
}

.charge-scroll-track:hover .charge-scroll-thumb {
  background: var(--primary);
}

.charge-scroll-track.dragging .charge-scroll-thumb {
  background: var(--primary);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .charge-scroll-hint {
    grid-template-columns: 1fr;
  }

  .charge-more-toggle {
    margin-left: 0;
  }
}
</style>