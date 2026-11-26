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
 *
 * ---------------------------------------------------------------------
 * 【通用错误抛出接口】
 * 用户诉求：「遇到请求失败的问题，不要使用这个展示在界面上，
 *   正确的做法是使用 element-plus 提供的 message 将他正确抛出，
 *   这个可以封装起来，做成通用接口运用」。
 *
 * 因此本模块除 `useNotify()` 组合式函数外，另导出**模块级**函数，
 * 供非组件上下文（composable / api 层 / 事件回调）直接调用：
 *
 *   import { notifyError, notifySuccess, notifyWarning, notifyInfo }
 *     from '../composables/useNotify'
 *
 *   try { await fetchSomething() }
 *   catch (e) { notifyError('加载失败，请稍后重试') }
 *
 * 这样全站任何请求失败都可以统一走 ElMessage，
 * 不再需要在模板里维护各自的 `*-load-error` / `error-panel` 内联错误面板。
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

/** 通用成功提示（模块级） */
export function notifySuccess(text: string, options?: MessageOptions): void {
  show('success', text, options)
}

/**
 * 通用失败提示（模块级）。
 * 请求失败 / 校验不通过 / 业务拒绝等场景统一走这里，
 * 不再渲染页面内联错误面板。
 */
export function notifyError(text: string, options?: MessageOptions): void {
  show('error', text, options)
}

/** 通用警告提示（模块级） */
export function notifyWarning(text: string, options?: MessageOptions): void {
  show('warning', text, options)
}

/** 通用中性提示（模块级） */
export function notifyInfo(text: string, options?: MessageOptions): void {
  show('info', text, options)
}

/**
 * 从任意异常对象中提取可读的中文错误文案。
 * 后端 ApiResponse 的 message 优先，其次 Error.message，最后回退到兜底文案。
 */
export function resolveErrorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  const anyError = error as any
  const fromResponse = anyError?.response?.data?.message || anyError?.response?.data?.msg
  if (typeof fromResponse === 'string' && fromResponse.trim()) return fromResponse
  if (typeof anyError?.message === 'string' && anyError.message.trim()) return anyError.message
  return fallback
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
