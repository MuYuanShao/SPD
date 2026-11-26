<script setup lang="ts">
/**
 * SpdColumnSettings — 全站统一的「列展示设置」面板
 * =====================================================================
 * 产品诉求（硬约束）：
 *   「列设置要加入一个调节列顺序的功能」
 *
 * ---------------------------------------------------------------------
 * 【为什么抽成组件】
 *   列设置面板的标记此前在 26 个列表页各写了一份（复选框 + 标签 + 宽度 +
 *   恢复默认 + 确定），每次改交互都要同步改 26 处，极易漂移。
 *   本组件把这些标记收敛成一处：
 *     - 勾选显隐（保留原行为：至少保留一列可见）
 *     - 显示列宽（px）
 *     - **▲ / ▼ 调整列顺序**（键盘可达、无需拖拽依赖）
 *     - 恢复默认（同时恢复顺序 / 宽度 / 显隐）
 *     - 确定（关闭面板）
 *
 * 【顺序如何真正作用到表格上】
 *   列顺序保存在 useListColumns 的 columns 数组顺序里（持久化格式不变，
 *   仍是 JSON 数组，向后兼容）。表格侧通过 <SpdOrderedColumns>
 *   按该顺序渲染 <el-table-column>，因此渲染顺序 = 存储顺序。
 *
 * 【样式来源】
 *   面板外观沿用 styles/list/unified-list.css 的
 *   .ulc-col-settings-pop / .ulc-col-settings-list / .ulc-col-settings-item，
 *   本组件不新增视觉规范；新增的顺序按钮样式写在
 *   styles/components/spd-column-settings.css。
 */
import { computed } from 'vue'
import { ChevronDown, ChevronUp } from '@lucide/vue'
import SpdButton from './SpdButton.vue'
import type { ListColumnConfig } from '../../composables/useListColumns'

const props = withDefaults(
  defineProps<{
    /** 列配置数组（来自 useListColumns().columns） */
    columns: ListColumnConfig[]
    /** 当前可见列数量（用于「至少保留一列」的禁用判断） */
    visibleColumnCount: number
  }>(),
  {
    columns: () => [],
    visibleColumnCount: 0
  }
)

const emit = defineEmits<{
  /** 切换列显隐 */
  (e: 'toggle', key: string, visible: boolean): void
  /** 上移 / 下移一列 */
  (e: 'move', key: string, direction: -1 | 1): void
  /** 恢复默认（顺序 + 宽度 + 显隐） */
  (e: 'reset'): void
  /** 关闭面板 */
  (e: 'confirm'): void
}>()

/** 列总数：用于「首行不能上移 / 末行不能下移」的禁用判断 */
const total = computed(() => props.columns.length)

function canMoveUp(index: number) {
  return index > 0
}

function canMoveDown(index: number) {
  return index < total.value - 1
}

function handleToggle(event: Event, key: string) {
  const input = event.target as HTMLInputElement
  emit('toggle', key, input.checked)
}
</script>

<template>
  <div class="ulc-col-settings-pop spd-column-settings">
    <header>
      <strong>列展示设置</strong>
      <SpdButton label="恢复默认" variant="text" tone="neutral" @click="emit('reset')" />
    </header>

    <div class="ulc-col-settings-list">
      <div
        v-for="(col, index) in columns"
        :key="col.key"
        class="ulc-col-settings-item spd-column-settings__row"
      >
        <label class="spd-column-settings__label">
          <input
            type="checkbox"
            :checked="col.visible"
            :disabled="col.visible && visibleColumnCount <= 1"
            :aria-label="`显示列 ${col.label}`"
            @change="handleToggle($event, col.key)"
          />
          <span :title="col.label">{{ col.label }}</span>
        </label>
        <small>{{ col.width }}px</small>
        <span class="spd-column-settings__order">
          <button
            type="button"
            class="spd-column-settings__move"
            :disabled="!canMoveUp(index)"
            :aria-label="`把「${col.label}」列上移`"
            @click="emit('move', col.key, -1)"
          >
            <ChevronUp :size="14" aria-hidden="true" />
          </button>
          <button
            type="button"
            class="spd-column-settings__move"
            :disabled="!canMoveDown(index)"
            :aria-label="`把「${col.label}」列下移`"
            @click="emit('move', col.key, 1)"
          >
            <ChevronDown :size="14" aria-hidden="true" />
          </button>
        </span>
      </div>
    </div>

    <footer>
      <SpdButton label="确定" tone="primary" @click="emit('confirm')" />
    </footer>
  </div>
</template>
