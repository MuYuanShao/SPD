<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, RefreshCw, Search, X } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useChargeDetailQuery } from '../../composables/useChargeDetailQuery'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
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

    <StatusMessage :message="message" tone="error" role="alert" />
    <StatusMessage :message="validationMessage" tone="warning" role="alert" />

    <form class="charge-panel" @submit.prevent="search">
      <div class="charge-section-title">
        <Search :size="20" />
        <h3>查询条件</h3>
      </div>
      <div class="charge-query-grid">
        <label><span>患者号/住院号</span><input v-model.trim="filters.patientNo" placeholder="患者号、住院号" /></label>
        <label><span>患者姓名</span><input v-model.trim="filters.patientName" placeholder="支持脱敏姓名" /></label>
        <label><span>商品编码</span><input v-model.trim="filters.productCode" placeholder="医院目录编码" /></label>
        <label><span>商品名称</span><input v-model.trim="filters.productName" placeholder="耗材名称" /></label>
        <label><span>开始日期</span><input v-model="filters.dateFrom" type="date" /></label>
        <label><span>结束日期</span><input v-model="filters.dateTo" type="date" /></label>
        <label><span>UID/唯一码</span><input v-model.trim="filters.uid" placeholder="唯一码" /></label>
        <label><span>UDI</span><input v-model.trim="filters.udi" placeholder="UDI-DI / UDI" /></label>
        <label><span>供应商</span><input v-model.trim="filters.supplierName" placeholder="供应商名称" /></label>
        <label><span>厂家</span><input v-model.trim="filters.manufacturerName" placeholder="生产厂家" /></label>
        <label><span>注册证</span><input v-model.trim="filters.registrationNo" placeholder="注册证号" /></label>
      </div>
      <div class="hospital-action-row">
        <button class="btn btn-primary" type="submit">
          <Search :size="18" />
          查询
        </button>
        <button class="btn" type="button" @click="reset">
          <X :size="18" />
          重置
        </button>
        <button class="btn" type="button" @click="loadData"><RefreshCw :size="17" />刷新</button>
        <RouterLink v-if="auth.canWrite('high-value-consumables')" class="btn" to="/features/high-value-consumables/operations">高值耗材计费操作</RouterLink>
      </div>
    </form>

    <section class="charge-panel" :aria-busy="loading">
      <div class="charge-section-title charge-detail-title">
        <h3>收费明细</h3>
        <div class="charge-scroll-actions" aria-label="收费明细横向滚动控制">
          <button class="btn-icon" type="button" aria-label="向左滚动" @click="scrollChargeTable('left')">
            <ChevronLeft :size="18" />
          </button>
          <button class="btn-icon" type="button" aria-label="向右滚动" @click="scrollChargeTable('right')">
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
      <div class="charge-scroll-hint">
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
  </section>
</template>

<style scoped>
.charge-detail-page { display: grid; gap: 16px; min-width: 0; }
.charge-panel {
  min-width: 0;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--fli-line, #e5eaf0);
  border-radius: 8px;
  background: white;
}
.charge-section-title { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.charge-section-title h3 { margin: 0; font-size: 15px; }
.charge-section-title > svg { color: var(--primary); }
.charge-query-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); gap: 12px 16px; }
.charge-query-grid label { display: grid; gap: 6px; min-width: 0; font-size: 13px; }
.charge-query-grid input {
  width: 100%; min-width: 0; box-sizing: border-box; height: 36px; padding: 0 10px;
  border: 1px solid var(--fli-line, #dbe5ec); border-radius: 6px;
  background: white; color: inherit; font: inherit;
}
.charge-query-grid input:focus-visible { outline: 2px solid var(--primary); outline-offset: 1px; }
.charge-panel .hospital-action-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.charge-scroll-track:focus-visible { outline: 2px solid var(--primary); outline-offset: 3px; }

.charge-detail-title {
  align-items: center;
  justify-content: space-between;
}

.charge-scroll-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.charge-scroll-actions .btn-icon {
  width: 36px;
  height: 36px;
  border: 1px solid #dbe5ec;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.charge-scroll-actions .btn-icon:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

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

.charge-scroll-hint {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr);
  align-items: center;
  gap: 12px;
  padding: 10px 2px 0;
  color: #64748b;
  font-size: 13px;
}

.charge-scroll-track {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8eef3;
  cursor: pointer;
  touch-action: none;
}

.charge-scroll-thumb {
  position: absolute;
  inset-block: 0;
  left: 0;
  border-radius: inherit;
  background: #8298aa;
  transition: background-color 0.16s ease;
}

.charge-scroll-track.dragging .charge-scroll-thumb {
  background: #62798e;
}

@media (max-width: 900px) {
  .charge-scroll-hint {
    grid-template-columns: 1fr;
  }
}
</style>
