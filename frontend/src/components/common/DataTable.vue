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
  /**
   * 是否显示溢出提示（Element Plus 的 show-overflow-tooltip）。
   * 不传时按「该列是否承载长文本」自动推断：
   *   - 默认 true —— 文字过长时省略号 + 悬浮提示完整文字；
   *   - 传 false 可关闭（适合本身就极短、且列宽固定的列）。
   */
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

/**
 * 单元格展示文本（纯文本列）。
 * 用于 Element Plus 的 show-overflow-tooltip 与原生 title 兜底：
 * 「文字过长导致列宽无法容纳时……使用省略号，悬浮上去出现一个tip
 *   来显示完整文字就好」。
 */
function getDisplayText(row: Row, column: DataTableColumn) {
  const value = getValue(row, column)
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

/**
 * 该列是否启用溢出提示。
 * 默认开启（长文本省略 + 悬浮显示完整文字），
 * 列定义可显式传 false 关闭。
 */
function overflowTooltipEnabled(column: DataTableColumn) {
  return column.showOverflowTooltip !== false
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
          :show-overflow-tooltip="overflowTooltipEnabled(column)"
        >
          <template #default="{ row }">
            <slot
              :name="`column-${column.key}`"
              :row="row"
              :value="getValue(row as Row, column)"
            >
              <!-- 纯文本列：省略号 + 悬浮提示完整文字。
                   需求：「文字过长导致列宽无法容纳时……使用省略号，
                   悬浮上去出现一个tip来显示完整文字就好」。

                   三重保障，任何一层可用即可看到完整文字：
                     1) el-tooltip —— Vue 原生方案，气泡跟随鼠标、可关闭；
                     2) title     —— 浏览器原生 tooltip，零依赖、可访问；
                     3) data-tip  —— 纯 CSS 气泡（见 spd-text-overflow.css）。

                   省略号由 .spd-ellipsis-cell 自身负责（overflow:hidden +
                   text-overflow:ellipsis），不依赖 .cell 的裁切，
                   因此与 show-overflow-tooltip 的内置检测互不干扰。 -->
              <el-tooltip
                :content="getDisplayText(row as Row, column)"
                placement="top"
                :show-after="200"
                :disabled="!overflowTooltipEnabled(column)"
              >
                <span
                  class="spd-ellipsis-cell"
                  :title="getDisplayText(row as Row, column)"
                  :data-tip="getDisplayText(row as Row, column)"
                >
                  {{ getDisplayText(row as Row, column) }}
                </span>
              </el-tooltip>
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
  background: var(--spd-bg-container, var(--spd-p-bg)) !important;
  border: 1px solid var(--spd-border-subtle, var(--spd-p-bg-muted)) !important;
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
  background: var(--spd-bg-container, var(--spd-p-bg));
  border-bottom: 1px solid var(--spd-border-subtle, var(--spd-p-bg-muted));
  flex-wrap: wrap;
}

.spd-data-table__body {
  flex: 1 1 auto;
  min-width: 0;
  background: var(--spd-bg-container, var(--spd-p-bg));
}

.spd-data-table__el {
  width: 100%;
}

/* 单元格文本：超长省略号 + 悬浮提示完整文字。
   （.spd-ellipsis-cell 的全局定义见 styles/components/spd-text-overflow.css，
     这里只补充表格布局需要的约束：让 .cell 自身也可省略，
     避免长文本把列撑宽或横向溢出侵占相邻列。）

   ⚠️ 这里用 width:100% + min-width:0 而不是 max-width:0：
     .cell 是 flex 容器，max-width:0 会把内容盒压成 0，
     导致状态条 / 按钮被挤成一条线。

   ⚠️ overflow 必须保持 hidden（Element Plus 的默认行为）：
     内置的 show-overflow-tooltip 正是靠比较
     `.cell.scrollWidth > .cell.clientWidth` 来判断「是否溢出」，
     一旦改成 visible，内置提示就永远不出现。
     真正产生「…」的是 .spd-ellipsis-cell（自带 overflow:hidden），
     .cell 的 hidden 只是兜底，防止裸文本横向侵占相邻列。

   ⚠️ position 保持 static（不要设 relative）：
     data-tip 的纯 CSS 气泡是省略号元素的 ::after（绝对定位），
     它的包含块是最近的已定位祖先。Element Plus 已给
     `td.el-table__cell` 设了 `position: relative` 且 td 的 overflow 是 visible，
     气泡因此能溢出 .cell 的裁切、完整显示出来。 */
.spd-data-table__el :deep(td.el-table__cell .cell) {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spd-data-table__el :deep(td.el-table__cell .cell > *) {
  min-width: 0;
  max-width: 100%;
}

.spd-data-table__pagination {
  padding: 12px 16px;
  border-top: 1px solid var(--spd-border-subtle, var(--spd-p-bg-muted));
  background: var(--spd-bg-container, var(--spd-p-bg));
}

.spd-data-table__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 12px;
  color: var(--spd-text-tertiary, color-mix(in srgb, var(--spd-p-text-strong) 45%, transparent));
  font-size: var(--spd-font-size-base);
}
</style>
