import { ElMessage, ElMessageBox, type MessageOptions, type MessageType } from 'element-plus'

/**
 * 全局业务消息通道（基于 Element Plus ElMessage）
 * ---------------------------------------------------------------------
 * 用法：消除各业务页面在模板顶部用 `<p v-if="message" class="inline-message">`
 * 渲染"成功 / 失败 / 校验"消息的旧模式，统一改为顶部 Message 弹层，
 * 不占用页面布局、不与表单字段争抢高度。
 *
 * 推荐做法：把页面里的 `message.value = 'xxx'` 替换为 `notify.success('xxx')`，
 * 并把模板上的 `<p v-if="message">{{ message }}</p>` 删除。
 */
export interface UseNotify {
  success: (text: string, options?: MessageOptions) => void
  error: (text: string, options?: MessageOptions) => void
  warning: (text: string, options?: MessageOptions) => void
  info: (text: string, options?: MessageOptions) => void
  show: (type: MessageType, text: string, options?: MessageOptions) => void
  confirm: (text: string, title?: string, options?: Parameters<typeof ElMessageBox.confirm>[2]) => Promise<boolean>
}

function show(type: MessageType, text: string, options: MessageOptions = {}) {
  if (!text) return
  // appendTo 把消息挂到 body 末尾，脱离业务页面的 stacking context（如 .modal-mask 的 isolation: isolate），
  // zIndex 必须大于 .modal-mask 的 99999，否则消息仍会被弹窗的遮罩层压住。
  // 使用 customClass 配合最高优先级 z-index，确保 ElMessage 始终浮在所有业务 mask 之上。
  ElMessage({
    type,
    message: text,
    grouping: true,
    appendTo: 'body',
    zIndex: 999999,
    customClass: 'spd-top-notify',
    ...options
  })
}

export function useNotify(): UseNotify {
  return {
    success: (text, options) => show('success', text, options),
    error:   (text, options) => show('error', text, options),
    warning: (text, options) => show('warning', text, options),
    info:    (text, options) => show('info', text, options),
    show,
    async confirm(text, title = '操作确认', options) {
      try {
        await ElMessageBox.confirm(text, title, { type: 'warning', ...options })
        return true
      } catch {
        return false
      }
    }
  }
}
