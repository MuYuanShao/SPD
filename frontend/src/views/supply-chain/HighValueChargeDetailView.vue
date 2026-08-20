<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, RefreshCw, Search, X } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import { fetchClosureList } from '../../api/operationalClosure'

const loading = ref(false)
const message = ref('')
const rows = ref<Record<string, unknown>[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
const tableScrollRef = ref<HTMLElement | null>(null)
const scrollTrackRef = ref<HTMLElement | null>(null)
const tableScrollState = ref({ left: 0, max: 0 })
const scrollDragging = ref(false)
let scrollDragStartX = 0
let scrollDragStartLeft = 0

const filters = reactive({
  patientNo: '',
  patientName: '',
  productCode: '',
  productName: '',
  dateFrom: '',
  dateTo: '',
  uid: '',
  udi: '',
  supplierName: '',
  manufacturerName: '',
  registrationNo: ''
})

function requestParams() {
  const params: Record<string, string> = {
    page: String(currentPage.value),
    size: String(pageSize.value)
  }
  Object.entries(filters).forEach(([key, value]) => {
    const text = String(value || '').trim()
    if (text) {
      params[key] = text
    }
  })
  return params
}

async function loadData() {
  loading.value = true
  message.value = ''
  try {
    const page = await fetchClosureList('high-value', requestParams())
    rows.value = page.rows
    totalItems.value = page.total
    await nextTick()
    updateTableScrollState()
  } catch (error) {
    message.value = error instanceof Error ? error.message : '收费耗材明细加载失败'
  } finally {
    loading.value = false
  }
}

async function search() {
  currentPage.value = 1
  await loadData()
}

async function reset() {
  Object.keys(filters).forEach((key) => {
    filters[key as keyof typeof filters] = ''
  })
  currentPage.value = 1
  await loadData()
}

async function changePage(page: number) {
  const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadData()
}

async function changePageSize(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

function updateTableScrollState() {
  const el = tableScrollRef.value
  if (!el) {
    tableScrollState.value = { left: 0, max: 0 }
    return
  }
  tableScrollState.value = {
    left: el.scrollLeft,
    max: Math.max(0, el.scrollWidth - el.clientWidth)
  }
}

function setChargeTableScrollLeft(left: number) {
  const el = tableScrollRef.value
  if (!el) return
  const max = Math.max(0, el.scrollWidth - el.clientWidth)
  const nextLeft = Math.min(max, Math.max(0, left))
  el.scrollLeft = nextLeft
  tableScrollState.value = { left: nextLeft, max }
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
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / Math.max(1, rect.width)))
  setChargeTableScrollLeft((el.scrollWidth - el.clientWidth) * ratio)
}

function startChargeTrackDrag(event: PointerEvent) {
  const el = tableScrollRef.value
  if (!el) return
  scrollDragging.value = true
  scrollDragStartX = event.clientX
  scrollDragStartLeft = el.scrollLeft
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  setChargeTableScrollFromTrack(event)
}

function moveChargeTrackDrag(event: PointerEvent) {
  if (!scrollDragging.value) return
  const track = scrollTrackRef.value
  const el = tableScrollRef.value
  if (!track || !el) return
  const max = Math.max(1, el.scrollWidth - el.clientWidth)
  const delta = ((event.clientX - scrollDragStartX) / Math.max(1, track.clientWidth)) * max
  setChargeTableScrollLeft(scrollDragStartLeft + delta)
}

function endChargeTrackDrag(event: PointerEvent) {
  scrollDragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
}

onMounted(loadData)
onMounted(() => window.addEventListener('resize', updateTableScrollState))
onBeforeUnmount(() => window.removeEventListener('resize', updateTableScrollState))
</script>

<template>
  <section class="purchase-page closure-page">
    <div class="breadcrumb-line">
      <span>专项与合规</span>
      <strong>收费耗材明细查询</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>高值耗材收费回传明细</p>
        <h2>收费耗材明细查询</h2>
        <small>按患者、商品、日期、住院号/患者号、UDI/唯一码、供应商、厂家、注册证查询收费耗材明细。</small>
      </div>
      <button class="btn" type="button" @click="loadData">
      <RouterLink class="btn btn-primary" to="/features/high-value-consumables/operations">
        ??????
      </RouterLink>
        <RefreshCw :size="17" />
        刷新
      </button>
    </div>

    <p v-if="message" class="inline-message">{{ message }}</p>

    <section class="hospital-catalog-panel">
      <div class="section-title">
        <Search :size="20" />
        <h3>查询条件</h3>
      </div>
      <div class="hospital-query-grid closure-form-grid">
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
        <button class="btn btn-primary" type="button" @click="search">
          <Search :size="18" />
          查询
        </button>
        <button class="btn" type="button" @click="reset">
          <X :size="18" />
          重置
        </button>
      </div>
    </section>

    <section class="hospital-catalog-panel">
      <div class="section-title charge-detail-title">
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
      <div ref="tableScrollRef" class="table-scroll charge-detail-scroll" @scroll="updateTableScrollState">
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
            <tr v-if="loading">
              <td colspan="10" class="approval-empty">正在加载收费耗材明细...</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="10" class="approval-empty">暂无收费耗材明细</td>
            </tr>
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
              <td>{{ row.chargeQuantity || '-' }}</td>
              <td>{{ row.unitPrice || '-' }}</td>
              <td>{{ row.chargeAmount || '-' }}</td>
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
              width: tableScrollState.max > 0 ? '34%' : '100%',
              transform: `translateX(${tableScrollState.max > 0 ? (tableScrollState.left / tableScrollState.max) * 194 : 0}%)`
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
  transition: transform 0.16s ease, width 0.16s ease;
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
