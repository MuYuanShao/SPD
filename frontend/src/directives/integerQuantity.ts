import type { ObjectDirective } from 'vue'

const listeners = new WeakMap<HTMLInputElement, { input: () => void; before: (event: InputEvent) => void; paste: (event: ClipboardEvent) => void }>()

/** Prevents fractional entry for physical counts; monetary inputs do not use this directive. */
export const integerQuantity: ObjectDirective<HTMLInputElement> = {
  mounted(el, binding) {
    if (binding.value === false) return
    el.step = '1'
    el.inputMode = 'numeric'
    const input = () => el.setCustomValidity(el.value === '' || /^\d+$/.test(el.value) ? '' : '数量必须为整数，不支持小数')
    const before = (event: InputEvent) => {
      if (event.data && /[.,eE+-]/.test(event.data)) event.preventDefault()
    }
    const paste = (event: ClipboardEvent) => {
      const value = event.clipboardData?.getData('text').trim() || ''
      if (!/^\d+$/.test(value)) { event.preventDefault(); el.setCustomValidity('数量必须为整数，不支持小数') }
    }
    el.addEventListener('input', input)
    el.addEventListener('beforeinput', before)
    el.addEventListener('paste', paste)
    listeners.set(el, { input, before, paste })
    input()
  },
  updated(el, binding) { if (binding.value !== false) el.setCustomValidity(el.value === '' || /^\d+$/.test(el.value) ? '' : '数量必须为整数，不支持小数') },
  beforeUnmount(el) {
    const handlers = listeners.get(el)
    if (!handlers) return
    el.removeEventListener('input', handlers.input)
    el.removeEventListener('beforeinput', handlers.before)
    el.removeEventListener('paste', handlers.paste)
    listeners.delete(el)
  }
}
