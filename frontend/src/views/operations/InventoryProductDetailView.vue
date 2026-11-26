<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChevronDown, Columns3, RefreshCw, Search, X } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import { fetchInventoryProductDetails, type InventoryProductDetail } from '../../api/reportCenter'

const keyword = ref('')
const rows = ref<InventoryProductDetail[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const error = ref('')
let appliedKeyword = ''
let sequence = 0
let request: AbortController | undefined

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode: 'hybrid'：按权重比例铺满内容区，表格右侧不留空白；
     - reservedWidth: 0：不额外扣除固定宽度；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。

   本表是只读报表、没有「操作」列，
   因此把最后一列「库位」标记为 fill: true，
   由 Element Plus 原生 fit 吃掉表格右侧剩余空白 —— 表格 100% 铺满内容区。
   ===================================================================== */
const INVENTORY_DETAIL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'index', label: '序号', width: 64, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'name', label: '产品名称', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 9, minWidth: 150, maxWidth: 380 },
  { key: 'registrationNo', label: '注册证', width: 180, visible: true, flexible: true, idealWidth: 210, weight: 7, minWidth: 130, maxWidth: 300 },
  { key: 'manufacturerName', label: '厂家', width: 160, visible: true, flexible: true, idealWidth: 190, weight: 7, minWidth: 120, maxWidth: 280 },
  { key: 'distributorName', label: '配送商', width: 160, visible: true, flexible: true, idealWidth: 190, weight: 7, minWidth: 120, maxWidth: 280 },
  { key: 'expiry', label: '有效期至', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 190 },
  { key: 'specification', label: '规格型号', width: 140, visible: true, flexible: true, idealWidth: 170, weight: 6, minWidth: 110, maxWidth: 260 },
  { key: 'quantity', label: '数量', width: 100, visible: true, weight: 3, minWidth: 88, maxWidth: 150 },
  { key: 'location', label: '库位', width: 180, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 300, fill: true }
]

const inventoryDetailCols = useListColumns({
  storageKey: 'spd-inventory-product-detail-cols-v1',
  defaults: INVENTORY_DETAIL_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const inventoryDetailTableRef = inventoryDetailCols.tableRef
const isInventoryDetailColVisible = (key: string) => inventoryDetailCols.isColumnVisible(key)
const inventoryDetailColBinding = (key: string) => inventoryDetailCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const inventoryDetailActionFix = useActionColumnFix()
const inventoryDetailActionFixed = computed(() => inventoryDetailActionFix.actionFixedBinding.value)

watch(
  [inventoryDetailTableRef],
  () => {
    inventoryDetailActionFix.observe(inventoryDetailTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const inventoryDetailColSettingsAnchorRef = ref<HTMLElement | null>(null)
const inventoryDetailColSettingsPopRef = ref<HTMLElement | null>(null)
const inventoryDetailColSettingsPop = useColSettingsPopover({
  open: inventoryDetailCols.settingsOpen,
  anchorRef: inventoryDetailColSettingsAnchorRef,
  popRef: inventoryDetailColSettingsPopRef
})

async function load() {
  const current = ++sequence
  request?.abort()
  const controller = request = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await fetchInventoryProductDetails({ page: page.value, size: size.value, keyword: appliedKeyword }, controller.signal)
    if (current !== sequence) return
    const last = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > last) { page.value = last; await load(); return }
    rows.value = result.rows
    total.value = result.total
  } catch (reason) {
    if (current === sequence && !controller.signal.aborted) {
      total.value = 0
      error.value = '进销存商品明细加载失败，请刷新重试'
      ElMessage.error(error.value)
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}

function search() {
  appliedKeyword = keyword.value.trim()
  page.value = 1
  void load()
}
function reset() {
  keyword.value = ''
  search()
}
function changePage(value: number) {
  if (value === page.value) return
  page.value = value
  void load()
}
function changeSize(value: number) {
  size.value = value
  page.value = 1
  void load()
}
function refresh() { void load() }
onMounted(load)
onBeforeUnmount(() => { ++sequence; request?.abort() })
</script>

<template>
  <main class="report-page inventory-product-page spd-unified-list">
    <MdPageHeader
      crumb="运营与决策 / 报表中心 / 进销存明细"
      title="进销存商品明细"
      :loading="loading"
      :show-refresh="false"
      @refresh="refresh"
    />

    <section class="report-toolbar">
      <form class="report-filters" @submit.prevent="search">
        <label class="search-keyword">
          <span>关键词</span>
          <input v-model="keyword" maxlength="100" placeholder="搜索产品、注册证、厂家、配送商、规格或库位" />
        </label>
      </form>
      <div class="report-actions">
        <SpdButton label="查询" tone="primary" :disabled="loading" @click="search">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
        <SpdButton label="重置" tone="neutral" :disabled="loading" @click="reset">
          <template #icon><X :size="16" /></template>
        </SpdButton>
        <SpdButton label="刷新" tone="neutral" :disabled="loading" @click="refresh">
          <template #icon><RefreshCw :size="16" /></template>
        </SpdButton>
      </div>
    </section>

    <StatusMessage :message="error" tone="error" role="alert" />

    <MdDataTableCard :loading="loading">
      <template #toolbar>
        <!-- 查询 / 重置 / 刷新 保留在「查询条件」面板内，
             表格工具条只承载列设置，避免同一动作出现两处。 -->
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="inventoryDetailColSettingsAnchorRef"
           
           
            @click="inventoryDetailCols.settingsOpen.value = !inventoryDetailCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <div
            v-if="inventoryDetailCols.settingsOpen.value"
            ref="inventoryDetailColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': inventoryDetailColSettingsPop.align.value === 'right', 'is-align-up': inventoryDetailColSettingsPop.vertical.value === 'up' }"
            :style="inventoryDetailColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="inventoryDetailCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in inventoryDetailCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && inventoryDetailCols.visibleColumnCount.value <= 1"
                  @change="inventoryDetailCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="inventoryDetailCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
      </template>
      <template #meta-extra>
        <span class="md-chip primary">共 {{ total.toLocaleString('zh-CN') }} 条</span>
      </template>

      <div class="ulc-table-wrap">
        <el-table
          ref="inventoryDetailTableRef"
          v-loading="loading"
          :data="rows"
          stripe
          border
          row-key="id"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在加载进销存商品明细…' : error ? '加载失败，请刷新重试' : '暂无匹配商品'"
          @header-dragend="(width: number, oldWidth: number, column: any) => inventoryDetailCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="index"
            v-if="isInventoryDetailColVisible('index')"
            type="index"
            align="center"
            v-bind="inventoryDetailColBinding('index')"
            :index="(i: number) => (page - 1) * size + i + 1"
          >
            <template #header>
              <span class="ulc-th-text">序号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="name"
            v-if="isInventoryDetailColVisible('name')"
            prop="name"
            v-bind="inventoryDetailColBinding('name')"
          >
            <template #header>
              <span class="ulc-th-text">产品名称</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-strong ulc-wrap-text">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="registrationNo"
            v-if="isInventoryDetailColVisible('registrationNo')"
            prop="registrationNo"
            v-bind="inventoryDetailColBinding('registrationNo')"
          >
            <template #header>
              <span class="ulc-th-text">注册证</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-mono ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="manufacturerName"
            v-if="isInventoryDetailColVisible('manufacturerName')"
            prop="manufacturerName"
            v-bind="inventoryDetailColBinding('manufacturerName')"
          >
            <template #header>
              <span class="ulc-th-text">厂家</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.manufacturerName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="distributorName"
            v-if="isInventoryDetailColVisible('distributorName')"
            prop="distributorName"
            v-bind="inventoryDetailColBinding('distributorName')"
          >
            <template #header>
              <span class="ulc-th-text">配送商</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.distributorName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="expiry"
            v-if="isInventoryDetailColVisible('expiry')"
            prop="expiry"
            v-bind="inventoryDetailColBinding('expiry')"
          >
            <template #header>
              <span class="ulc-th-text">有效期至</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ row.expiry || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="specification"
            v-if="isInventoryDetailColVisible('specification')"
            prop="specification"
            v-bind="inventoryDetailColBinding('specification')"
          >
            <template #header>
              <span class="ulc-th-text">规格型号</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.specification || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="quantity"
            v-if="isInventoryDetailColVisible('quantity')"
            prop="quantity"
            align="right"
            v-bind="inventoryDetailColBinding('quantity')"
          >
            <template #header>
              <span class="ulc-th-text">数量</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ Number(row.quantity || 0).toLocaleString('zh-CN') }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="location"
            v-if="isInventoryDetailColVisible('location')"
            prop="location"
            v-bind="inventoryDetailColBinding('location')"
          >
            <template #header>
              <span class="ulc-th-text">库位</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.location || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #pagination>
        <MdPagination
          :page="page"
          :total-pages="Math.max(1, Math.ceil(total / size))"
          :total="total"
          :size="size"
          :loading="loading"
          @update:page="changePage"
          @update:size="changeSize"
        />
      </template>
    </MdDataTableCard>
  </main>
</template>

<style scoped>
/* =====================================================================
   报表中心 / 进销存商品明细 InventoryProductDetailView
   ---------------------------------------------------------------------
   与其他报表一致：MdPageHeader + 工具区 + 表格 + 分页器。
   区别：只有一个搜索输入 + 三按钮，工具区只占一行。
   ===================================================================== */
.report-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}
.report-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}
.report-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 14px;
  padding: 14px 16px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-line, var(--spd-p-border-subtle));
  border-radius: 10px;
  box-shadow: var(--spd-p-shadow-1);
}
.report-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1fr);
  gap: 8px;
  align-items: end;
}
.report-filters label {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 700;
}
.report-filters input {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--spd-line, var(--spd-p-border));
  border-radius: 7px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 0 10px;
  font: inherit;
  font-weight: 500;
}
.report-filters input:focus {
  outline: none;
  border-color: var(--spd-primary, var(--spd-p-primary));
  box-shadow: var(--spd-p-shadow-1);
}
.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

/* 表格的外观（表头 / 斑马纹 / 拖拽热区 / 换行）统一由
   unified-list.css 的 .spd-unified-list .ulc-table 提供，
   本页不再重复定义表格样式。 */

@media (max-width: 1100px) {
  .report-toolbar { grid-template-columns: 1fr; }
  .report-actions { justify-content: flex-start; }
}
@media (max-width: 720px) {
  .report-actions { display: grid; grid-template-columns: repeat(2, 1fr); }
  .report-actions .btn { width: 100%; }
}
</style>
