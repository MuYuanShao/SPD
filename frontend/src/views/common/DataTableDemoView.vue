<script setup lang="ts">
/**
 * DataTableDemo — DataTable 通用表格组件演示页
 *
 * 该页面用于：
 * 1. 展示 DataTable 在 toolbar / 列定义 / 操作列 / 选择列 / 分页上的用法
 * 2. 作为其他业务页表格替换的参考样板
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit3, Eye, Plus, Search, Trash2 } from '@lucide/vue'
import DataTable, { type DataTableColumn } from '../../components/common/DataTable.vue'

interface DemoRow {
  id: string
  code: string
  name: string
  category: string
  spec: string
  unit: string
  price: number
  stock: number
  status: 'enabled' | 'disabled' | 'pending'
  updatedAt: string
}

const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const allRows = ref<DemoRow[]>([])
const rows = ref<DemoRow[]>([])
const selected = ref<string[]>([])
const keyword = ref('')

const columns = computed<DataTableColumn[]>(() => [
  { key: 'code', label: '商品编码', width: 140, showOverflowTooltip: true },
  { key: 'name', label: '商品名称', minWidth: 180, showOverflowTooltip: true },
  { key: 'category', label: '分类', width: 110 },
  { key: 'spec', label: '规格型号', width: 130, showOverflowTooltip: true },
  { key: 'unit', label: '单位', width: 80, align: 'center' },
  {
    key: 'price',
    label: '单价',
    width: 110,
    align: 'right',
    formatter: (row) => `¥ ${(row as unknown as DemoRow).price.toFixed(2)}`
  },
  {
    key: 'stock',
    label: '库存',
    width: 100,
    align: 'right',
    formatter: (row) => `${(row as unknown as DemoRow).stock}`
  },
  {
    key: 'status',
    label: '状态',
    width: 100,
    align: 'center'
  },
  { key: 'updatedAt', label: '更新时间', width: 170 }
])

function statusTone(status: DemoRow['status']): 'success' | 'neutral' | 'warning' {
  if (status === 'enabled') return 'success'
  if (status === 'pending') return 'warning'
  return 'neutral'
}

function statusText(status: DemoRow['status']): string {
  if (status === 'enabled') return '已启用'
  if (status === 'pending') return '待审核'
  return '已停用'
}

function generateSeed() {
  const seed: DemoRow[] = []
  const categories = ['高值耗材', '低值耗材', '试剂', '消毒用品', '防护用品']
  const units = ['盒', '支', '包', '瓶', '袋']
  for (let i = 0; i < 168; i += 1) {
    const idx = i + 1
    const status: DemoRow['status'] = i % 7 === 0 ? 'pending' : i % 11 === 0 ? 'disabled' : 'enabled'
    seed.push({
      id: `R${String(idx).padStart(5, '0')}`,
      code: `SP-${String(idx).padStart(5, '0')}`,
      name: `演示商品 ${idx} - ${categories[i % categories.length]}`,
      category: categories[i % categories.length],
      spec: `规格-${(i % 12) + 1}`,
      unit: units[i % units.length],
      price: Math.round((10 + (i % 50) * 3.7) * 100) / 100,
      stock: (i * 17) % 600,
      status,
      updatedAt: new Date(Date.now() - i * 3600_000).toLocaleString('zh-CN')
    })
  }
  return seed
}

function refresh() {
  loading.value = true
  error.value = ''
  setTimeout(() => {
    const keywordLower = keyword.value.trim().toLowerCase()
    const filtered = keywordLower
      ? allRows.value.filter(
          (row) =>
            row.code.toLowerCase().includes(keywordLower) ||
            row.name.toLowerCase().includes(keywordLower)
        )
      : allRows.value
    total.value = filtered.length
    const start = (page.value - 1) * size.value
    rows.value = filtered.slice(start, start + size.value)
    loading.value = false
  }, 320)
}

function onPageChange(p: number) {
  page.value = p
  refresh()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  refresh()
}

function onSearch() {
  page.value = 1
  refresh()
}

function onResetSearch() {
  keyword.value = ''
  onSearch()
}

function onCreate() {
  // 演示：实际业务替换为对话框
  console.info('[DataTableDemo] 新增商品')
}

function onRowView(row: DemoRow) {
  console.info('[DataTableDemo] 查看', row.code)
}

function onRowEdit(row: DemoRow) {
  console.info('[DataTableDemo] 编辑', row.code)
}

function onRowDelete(row: DemoRow) {
  console.info('[DataTableDemo] 删除', row.code)
}

function onBatchDelete() {
  if (!selected.value.length) return
  console.info('[DataTableDemo] 批量删除', [...selected.value])
  selected.value = []
}

onMounted(() => {
  allRows.value = generateSeed()
  refresh()
})
</script>

<template>
  <section class="standard-page">
    <header class="standard-page-header">
      <div class="standard-page-heading">
        <SectionTitle title="DataTable 通用表格演示" :level="1">
          <template #icon><SlidersHorizontal :size="22" /></template>
        </SectionTitle>
        <p>统一表格风格：表头 / 斑马 / 边框 / hover / 分页 / 选择 / 操作</p>
      </div>
      <div class="standard-page-actions">
        <el-button type="primary" @click="onCreate">
          <Plus :size="14" />新增商品
        </el-button>
        <el-button :disabled="!selected.length" @click="onBatchDelete">
          <Trash2 :size="14" />批量删除（{{ selected.length }}）
        </el-button>
      </div>
    </header>

    <div class="master-search-bar">
      <div class="master-search-input">
        <Search :size="14" />
        <input
          v-model="keyword"
          placeholder="按编码 / 名称搜索"
          aria-label="搜索"
          @keyup.enter="onSearch"
        />
      </div>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onResetSearch">重置</el-button>
    </div>

    <DataTable
      :rows="rows"
      :columns="columns"
      :loading="loading"
      :error="error"
      :page="page"
      :size="size"
      :total="total"
      :selectable="true"
      :selected="selected"
      :row-key="'id'"
      empty-text="暂无符合条件的演示商品"
      @change-page="onPageChange"
      @change-size="onSizeChange"
      @select="(keys) => (selected = keys as string[])"
    >
      <template #toolbar>
        <div class="spd-data-table__hint">已选 {{ selected.length }} 条 · 共 {{ total }} 条</div>
      </template>

      <template #column-status="{ row }">
        <span :class="['status-pill', statusTone((row as DemoRow).status)]">
          {{ statusText((row as DemoRow).status) }}
        </span>
      </template>

      <template #row-action="{ row }">
        <el-button class="spd-row-action" link type="primary" @click="onRowView(row as DemoRow)">
          <Eye :size="13" />查看
        </el-button>
        <el-button class="spd-row-action" link type="primary" @click="onRowEdit(row as DemoRow)">
          <Edit3 :size="13" />编辑
        </el-button>
        <el-button class="spd-row-action" link type="danger" @click="onRowDelete(row as DemoRow)">
          <Trash2 :size="13" />删除
        </el-button>
      </template>
    </DataTable>
  </section>
</template>

<style scoped>
.master-search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.master-search-input {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  height: 36px;
  border: 1px solid var(--spd-border-default);
  border-radius: 6px;
  background: var(--spd-bg-surface);
  min-width: 280px;
  color: var(--spd-text-tertiary);
}
.master-search-input input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  color: var(--spd-text-primary);
  font-size: 14px;
}
.spd-data-table__hint {
  font-size: 13px;
  color: var(--spd-text-secondary);
  font-weight: 500;
}
.spd-row-action {
  padding: 0 6px !important;
  font-size: 13px !important;
}
</style>
