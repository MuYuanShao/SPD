/**
 * useSpdConfirm — 函数式确认弹窗 API（简化版）
 *
 * 任何代码都可以 `await ask({...})` 弹出确认。
 * 用 <SpdConfirmHost /> 在 AppShell / Layout 末尾挂载一次。
 *
 * 限制：本实例仅支持**当前未在处理异步任务**的简单确认。
 * 复杂确认（如 loading、状态进度展示）请直接使用 <SpdConfirmDialog v-model:open>。
 */
import { defineComponent, h, reactive, ref } from 'vue'
import SpdConfirmDialog from '../components/common/ConfirmDialog.vue'

type Tone = 'danger' | 'warning' | 'success' | 'primary' | 'info'

export interface ConfirmOptions {
  title?: string
  description?: string | string[]
  details?: string | string[]
  tone?: Tone
  confirmText?: string
  cancelText?: string
}

export interface SpdConfirmHandle {
  /** 弹出确认，返回 true / false */
  ask: (options: ConfirmOptions) => Promise<boolean>
  /** 渲染到模板的 host 组件（挂载在 layout 末尾） */
  ConfirmHost: ReturnType<typeof defineComponent>
}

export function useSpdConfirm(): SpdConfirmHandle {
  const open = ref(false)
  const state = reactive<ConfirmOptions>({
    title: '请确认',
    description: '',
    details: undefined,
    tone: 'warning',
    confirmText: '确认',
    cancelText: '取消'
  })
  let resolveCurrent: ((v: boolean) => void) | null = null

  function ask(options: ConfirmOptions): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      Object.assign(state, {
        title: '请确认',
        description: '',
        details: undefined,
        tone: 'warning' as Tone,
        confirmText: '确认',
        cancelText: '取消',
        ...options
      })
      resolveCurrent = resolve
      open.value = true
    })
  }

  function onConfirm() {
    if (resolveCurrent) {
      resolveCurrent(true)
      resolveCurrent = null
    }
    open.value = false
  }

  function onCancel() {
    if (resolveCurrent) {
      resolveCurrent(false)
      resolveCurrent = null
    }
    open.value = false
  }

  const ConfirmHost = defineComponent({
    name: 'SpdConfirmHost',
    setup() {
      return () =>
        h(SpdConfirmDialog, {
          open: open.value,
          title: state.title,
          description: state.description,
          details: state.details,
          tone: state.tone,
          confirmText: state.confirmText,
          cancelText: state.cancelText,
          'onUpdate:open': (v: boolean) => {
            if (!v) onCancel()
          },
          onConfirm,
          onCancel
        })
    }
  })

  return { ask, ConfirmHost }
}
