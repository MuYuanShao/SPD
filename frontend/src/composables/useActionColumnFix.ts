/**
 * 操作列固定规则（统一列表页共用）
 * =============================================================================
 * 目标：所有基础数据管理列表页的「操作」列采用**同一套**固定策略。
 *
 * 规则：
 * ------------------------------------------------------------------
 * 1. 当表格的**可见列数不超过阈值**时，操作列**不固定**，
 *    跟其它列一样随表格一起滚动。
 * 2. 当可见列数**超过阈值**时，操作列才固定在右侧，
 *    保证用户横向滚动时始终能看到操作按钮。
 *
 * 为什么要有阈值：
 * ------------------------------------------------------------------
 * 列不多时，固定右侧会白白占掉一列宽度，还会让表头与内容出现
 * 两层「固定列」副本（Element Plus 会额外渲染 .el-table__fixed-right），
 * 视觉上像是多了一列。只有列多到横向滚动不可避免时，固定才有意义。
 *
 * 阈值取 8：基础数据管理各页面的列数在 6~12 之间，
 * 8 能把「列少不固定、列多固定」这条线划在同一处，
 * 各页面表现完全一致。
 */
import { computed, ref } from 'vue'

/** 默认阈值：可见列数超过该值才固定操作列 */
export const ACTION_COLUMN_FIX_THRESHOLD = 8

export interface ActionColumnFixOptions {
  /** 阈值，默认 ACTION_COLUMN_FIX_THRESHOLD */
  threshold?: number
}

export function useActionColumnFix(options: ActionColumnFixOptions = {}) {
  const threshold = options.threshold ?? ACTION_COLUMN_FIX_THRESHOLD
  /** 参与判定的可见列数（含选择列与操作列） */
  const visibleColumnCount = ref(0)

  /**
   * 是否固定操作列。
   * 可见列数超过阈值才固定；列数不足时保持普通列。
   */
  const actionFixed = computed(() => visibleColumnCount.value > threshold)

  /**
   * 绑定到 el-table-column 的 fixed 属性。
   * 传 undefined 表示不固定（Element Plus 接受 undefined / false）。
   */
  const actionFixedBinding = computed(() => (actionFixed.value ? 'right' : undefined))

  /**
   * 记录当前表格的可见列数。
   * @param count 可见列数（含选择列与操作列本身）
   */
  function setVisibleColumnCount(count: number) {
    visibleColumnCount.value = Number.isFinite(count) ? count : 0
  }

  /**
   * 从表格 DOM 反推可见列数。
   * 用于列设置里勾选/取消列之后重新判定。
   */
  function syncFromTable(tableEl: { $el?: HTMLElement } | null | undefined) {
    const root = tableEl?.$el
    if (!root) return
    const header = root.querySelector('.el-table__header')
    if (!header) return
    const count = header.querySelectorAll('th:not(.el-table__placeholder)').length
    setVisibleColumnCount(count)
  }

  return {
    threshold,
    visibleColumnCount,
    actionFixed,
    actionFixedBinding,
    setVisibleColumnCount,
    syncFromTable
  }
}
