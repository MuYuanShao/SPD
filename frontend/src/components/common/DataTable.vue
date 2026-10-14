<script setup lang="ts">
/**
 * DataTable — 通用业务表格组件
 *
 * 统一全院业务页面的表格风格：表头/斑马/边框/hover/分页/空状态/加载。
 *
 * 典型用法：
 * <DataTable
 *   :rows="rows"
 *   :columns="columns"
 *   :loading="loading"
 *   :error="error"
 *   :page="page"
 *   :size="size"
 *   :total="total"
 *   :selectable="true"
 *   :selected="selectedKeys"
 *   empty-text="暂无数据"
 *   @change-page="onPageChange"
 *   @change-size="onSizeChange"
 *   @select="onSelect"
 *   @select-all="onSelectAll"
 *   @row-click="onRowClick"
 * >
 *   <template #toolbar>...左侧工具 / 右侧操作...</template>
 *   <template #column-status="{ row }">{{ statusText(row.status) }}</template>
 *   <template #row-action="{ row }">...行内操作按钮...</template>
 * </DataTable>
 */
import { computed } from 'vue'
import PaginationControls from './PaginationControls.vue'

export interface DataTableColumn {
  /** 列唯一 key，对应 row[key] 读取值；也可以只用作 el-table-column 的标识 */
  key: string
  /** 表头文本 */
  label: string
  /** 列宽，ElTable column width */
  width?: number | string
  /** 最小宽度 */
  minWidth?: number | string
  /** 固定列：left / right */
  fixed?: 'left' | 'right'
  /** 对齐：left / center / right */
  align?: 'left' | 'center' | 'right'
  /** 是否显示 tooltip */
  showOverflowTooltip?: boolean
  /** 格式化函数：返回展示值；如需复杂渲染请使用插槽 column-<key> */
  formatter?: (row: Record<string, unknown>) => string
  /** 是否可排序（透传 sortable） */
  sortable?: boolean | 'custom'
}

type Row = Record<string, unknown>

const props = withDefaults(
  defineProps<{
    rows: Row[]
    columns: DataTableColumn[]
    loading?: boolean
    error?: string
    page?: number
    size?: number
    total?: number
    /** 是否显示复选框列（按 rowKey 取值） */
    selectable?: boolean
    /** 已选 key 数组 */
    selected?: (string | number)[]
    /** 行唯一 key 字段名 */
    rowKey?: string
    /** 行点击是否高亮 */
    highlightCurrent?: boolean
    /** 表格布局：fixed / auto */
    tableLayout?: 'fixed' | 'auto'
    /** 空状态文字 */
    emptyText?: string
    /** 表格高度（如需内部滚动） */
    height?: number | string
    /** 最大高度 */
    maxHeight?: number | string
    /** 是否显示分页（默认根据 total 自动判断，>0 显示） */
    showPagination?: boolean
    /** 紧凑尺寸 */
    tableSize?: 'default' | 'small'
  }>(),
  {
    loading: false,
    error: '',
    page: 1,
    size: 20,
    total: 0,
    selectable: false,
    selected: () => [],
    rowKey: 'id',
    highlightCurrent: false,
    tableLayout: 'auto',
    emptyText: '暂无数据',
    showPagination: true,
    tableSize: 'default'
  }
)

const emit = defineEmits<{
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
  (event: 'select', keys: (string | number)[]): void
  (event: 'select-all', keys: (string | number)[]): void
  (event: 'row-click', row: Row): void
}>()

const displayText = computed(() => {
  if (props.loading) return '正在加载…'
  if (props.error) return props.error
  return props.emptyText
})

function getValue(row: Row, column: DataTableColumn) {
  if (column.formatter) return column.formatter(row)
  return row[column.key]
}

function getRowKey(row: Row) {
  return (row as any)[props.rowKey] ?? ''
}

function handleSelect(selection: Row[]) {
  emit('select', selection.map((row) => getRowKey(row)))
}

function handleSelectAll(selection: Row[]) {
  emit('select-all', selection.map((row) => getRowKey(row)))
}
</script>

<template>
  <div class="spd-data-table">
    <div v-if="$slots.toolbar" class="spd-data-table__toolbar">
      <slot name="toolbar" />
    </div>

    <div class="spd-data-table__body">
      <el-table
        class="spd-data-table__el"
        :data="rows"
        :row-key="rowKey"
        :stripe="true"
        :border="true"
        :size="tableSize"
        :table-layout="tableLayout"
        :empty-text="displayText"
        :height="height"
        :max-height="maxHeight"
        :highlight-current-row="highlightCurrent"
        :aria-busy="loading"
        @selection-change="handleSelect"
        @select-all="handleSelectAll"
        @row-click="(row: any) => emit('row-click', row as Row)"
      >
        <el-table-column
          v-if="selectable"
          type="selection"
          width="48"
          fixed="left"
          align="center"
          :reserve-selection="true"
        />
        <el-table-column
          v-for="column in columns"
          :key="column.key"
          :prop="column.key"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :fixed="column.fixed"
          :align="column.align"
          :sortable="column.sortable"
          :show-overflow-tooltip="column.showOverflowTooltip"
        >
          <template #default="{ row }">
            <slot
              :name="`column-${column.key}`"
              :row="row"
              :value="getValue(row as Row, column)"
            >
              {{ getValue(row as Row, column) ?? '-' }}
            </slot>
          </template>
        </el-table-column>
        <el-table-column
          v-if="$slots['row-action']"
          label="操作"
          width="180"
          fixed="right"
          align="center"
          class-name="spd-data-table__action-cell"
        >
          <template #default="{ row }">
            <slot name="row-action" :row="row" />
          </template>
        </el-table-column>
        <template #empty>
          <div class="spd-data-table__empty">
            <span class="spd-data-table__empty-text">{{ displayText }}</span>
          </div>
        </template>
      </el-table>
    </div>

    <footer v-if="showPagination && total > 0" class="spd-data-table__pagination">
      <PaginationControls
        :page="page"
        :size="size"
        :total="total"
        :loading="loading"
        @change-page="(p) => emit('change-page', p)"
        @change-size="(s) => emit('change-size', s)"
      />
    </footer>
  </div>
</template>

<style scoped>
.spd-data-table {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: var(--spd-bg-container, #ffffff) !important;
  border: 1px solid var(--spd-border-subtle, #f0f0f0) !important;
  border-radius: var(--spd-radius-md, 8px);
  overflow: hidden;
  box-shadow: var(--spd-shadow-1);
}

.spd-data-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: var(--spd-bg-container, #ffffff);
  border-bottom: 1px solid var(--spd-border-subtle, #f0f0f0);
  flex-wrap: wrap;
}

.spd-data-table__body {
  flex: 1 1 auto;
  min-width: 0;
  background: var(--spd-bg-container, #ffffff);
}

.spd-data-table__el {
  width: 100%;
}

.spd-data-table__pagination {
  padding: 12px 16px;
  border-top: 1px solid var(--spd-border-subtle, #f0f0f0);
  background: var(--spd-bg-container, #ffffff);
}

.spd-data-table__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 12px;
  color: var(--spd-text-tertiary, rgba(0, 0, 0, 0.45));
  font-size: 14px;
}
</style>
