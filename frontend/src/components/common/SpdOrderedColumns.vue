<script setup lang="ts">
/**
 * SpdOrderedColumns — 按「存储顺序」渲染表格列
 * =====================================================================
 * 产品诉求（硬约束）：
 *   「列设置要加入一个调节列顺序的功能」
 *   → 调整顺序后，**渲染出来的表格列顺序必须真的跟着变**，且刷新后仍然保持。
 *
 * ---------------------------------------------------------------------
 * 【为什么需要这个组件】
 *   Element Plus 的 el-table 把列顺序定义为
 *   「隐藏的 <th> 元素在 DOM 里的先后位置」
 *   （见 element-plus/es/components/table/src/table-column：
 *    getColumnIndex = children.indexOf(instance.vnode.el)，
 *    children 来自 table.refs.hiddenColumns.children）。
 *   也就是说：**模板里 <el-table-column> 的书写顺序 = 表格的实际列顺序**，
 *   框架没有提供公开的「重排列」接口。
 *
 *   因此本组件把「列的书写顺序」从模板里搬到数据里：
 *     - 外部传入 columns（来自 useListColumns().columns，已按用户顺序排好）；
 *     - 组件用 v-for 按该顺序渲染 <el-table-column>；
 *     - 每列的表头 / 内容通过**同名具名插槽**回传给调用方。
 *
 * 【调用方写法】
 *   <el-table :data="rows">
 *     <SpdOrderedColumns
 *       :columns="cols"
 *       :visible-keys="cols.orderedColumnKeys.value"
 *       :column-meta="columnMeta"
 *     >
 *       <template #productName="{ row }">
 *         <span class="ulc-wrap-text">{{ row.name }}</span>
 *       </template>
 *     </SpdOrderedColumns>
 *   </el-table>
 *
 *   其中 columnMeta 是 { [key]: { label, width, minWidth, align, ... } }。
 *
 * 【关键实现细节】
 *   1. el-table-column 必须渲染在 el-table 的默认插槽里，
 *      本组件以 Fragment（v-for）形式渲染，Vue 会把其中的 <th>
 *      摊平进 table 的 hidden-columns 容器 → 顺序即 DOM 顺序 → 生效。
 *   2. el-table-column 通过 inject 找到所属的 table：
 *      columnOrTableParent 会沿 vnode.vParent 向上找到带 tableId 的组件，
 *      中间隔着本组件不影响（会继续往上找）。
 *   3. 只渲染「可见」列：显隐切换 = 该列的 <el-table-column> 被 v-if 移除，
 *      Element Plus 的 removeColumn 会自动同步 store。
 */
import { computed } from 'vue'
import type { ListColumnConfig } from '../../composables/useListColumns'

/** 每列传给 <el-table-column> 的附加属性 */
export interface SpdColumnMeta {
  label?: string
  width?: number
  minWidth?: number
  align?: string
  fixed?: 'left' | 'right' | false
  className?: string
  showOverflowTooltip?: boolean
  [key: string]: unknown
}

const props = withDefaults(
  defineProps<{
    /** 全部列配置（useListColumns().columns） */
    columns: ListColumnConfig[]
    /** 需要渲染的列 key（按顺序）—— 通常传 orderedColumnKeys（仅可见列） */
    visibleKeys?: string[]
    /** 每列传给 el-table-column 的属性表 */
    columnMeta?: Record<string, SpdColumnMeta>
  }>(),
  {
    visibleKeys: undefined,
    columnMeta: () => ({})
  }
)

/**
 * 实际渲染的列（按传入顺序）。
 * 传入 visibleKeys 时只渲染其中的列；未传则渲染全部列。
 */
const rendered = computed(() => {
  const all = props.columns ?? []
  if (!props.visibleKeys) return all
  const order = props.visibleKeys
  const byKey = new Map(all.map((column) => [column.key, column]))
  const result: ListColumnConfig[] = []
  for (const key of order) {
    const column = byKey.get(key)
    if (column) result.push(column)
  }
  // 兜底：visibleKeys 里没有但 columns 里有的（顺序未知的列）追加到末尾
  const known = new Set(result.map((column) => column.key))
  for (const column of all) {
    if (!known.has(column.key)) result.push(column)
  }
  return result
})

function metaOf(key: string): SpdColumnMeta {
  return props.columnMeta?.[key] ?? {}
}
</script>

<template>
  <el-table-column
    v-for="col in rendered"
    :key="col.key"
    v-bind="metaOf(col.key)"
    :column-key="col.key"
    :label="metaOf(col.key).label ?? col.label"
  >
    <template #default="scope">
      <slot :name="col.key" v-bind="scope" />
    </template>
  </el-table-column>
</template>
