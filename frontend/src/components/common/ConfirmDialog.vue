<script setup lang="ts">
/**
 * SpdConfirmDialog — 通用确认弹窗
 *
 * 用于所有"危险/重要操作"前需要二次确认的场景（停用、删除、驳回等）。
 * 比浏览器原生 `confirm()` 视觉更精致，主题色与业务统一。
 *
 * 用法：
 *   <SpdConfirmDialog
 *     v-model:open="confirmOpen"
 *     title="停用商品"
 *     description="停用后该商品不可用于新订单，是否继续？"
 *     confirm-text="确认停用"
 *     tone="danger"
 *     :loading="loading"
 *     @confirm="handleConfirm"
 *   />
 *
 * Props:
 *   - open:        是否显示（v-model:open）
 *   - title:       弹窗标题（默认"请确认"）
 *   - description: 描述/原因（支持字符串或数组分段）
 *   - details:     可选高亮信息（数组或对象），如"共 3 个商品将被停用"
 *   - icon:        自定义图标（默认根据 tone 自动选）
 *   - tone:        'danger' | 'warning' | 'primary' | 'success' | 'info'
 *                  决定确认按钮颜色、图标、强调色
 *   - confirmText: 确认按钮文字（默认"确认"）
 *   - cancelText:  取消按钮文字（默认"取消"）
 *   - loading:     提交中状态（按钮置 loading 并 disable）
 *   - hideCancel:  隐藏取消按钮（仅用于提示型弹窗）
 *   - width:       弹窗宽度（默认 460px）
 *
 * Emits:
 *   - confirm: 用户点击确认
 *   - cancel:  用户点击取消/关闭
 *   - update:open
 */
import { AlertTriangle, AlertOctagon, CheckCircle2, Info, ShieldAlert, X } from '@lucide/vue'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string | string[]
    details?: string | string[]
    tone?: 'danger' | 'warning' | 'primary' | 'success' | 'info'
    confirmText?: string
    cancelText?: string
    loading?: boolean
    hideCancel?: boolean
    width?: string
  }>(),
  {
    title: '请确认操作',
    description: '',
    details: undefined,
    tone: 'warning',
    confirmText: '确认',
    cancelText: '取消',
    loading: false,
    hideCancel: false,
    width: '460px'
  }
)

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'update:open', value: boolean): void
}>()

const toneIcon = computed(() => {
  switch (props.tone) {
    case 'danger':
      return AlertOctagon
    case 'warning':
      return AlertTriangle
    case 'success':
      return CheckCircle2
    case 'primary':
      return ShieldAlert
    case 'info':
    default:
      return Info
  }
})

const descriptionLines = computed<string[]>(() => {
  if (Array.isArray(props.description)) return props.description
  if (typeof props.description === 'string' && props.description) return [props.description]
  return []
})

const detailLines = computed<string[]>(() => {
  if (!props.details) return []
  if (Array.isArray(props.details)) return props.details
  if (typeof props.details === 'string') return [props.details]
  return []
})

function handleConfirm() {
  if (props.loading) return
  emit('confirm')
}

function handleCancel() {
  if (props.loading) return
  emit('cancel')
  emit('update:open', false)
}

function closeOnMask(event: MouseEvent) {
  if (props.loading) return
  if (event.target === event.currentTarget) {
    emit('update:open', false)
    emit('cancel')
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="spd-confirm-fade">
      <div
        v-if="open"
        class="spd-confirm-mask"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`spd-confirm-title-${tone}`"
        @click="closeOnMask"
      >
        <section class="spd-confirm-panel" :class="`is-${tone}`" :style="{ maxWidth: width }">
          <header class="spd-confirm-head">
            <span class="spd-confirm-icon" :class="`is-${tone}`">
              <component :is="toneIcon" :size="22" />
            </span>
            <h3 :id="`spd-confirm-title-${tone}`" class="spd-confirm-title">{{ title }}</h3>
            <button
              v-if="!hideCancel && !loading"
              type="button"
              class="spd-confirm-close"
              aria-label="关闭"
              @click="handleCancel"
            >
              <X :size="18" />
            </button>
          </header>

          <div v-if="descriptionLines.length" class="spd-confirm-body">
            <p v-for="(line, idx) in descriptionLines" :key="idx" class="spd-confirm-desc">
              {{ line }}
            </p>
            <ul v-if="detailLines.length" class="spd-confirm-details">
              <li v-for="(line, idx) in detailLines" :key="`d-${idx}`">{{ line }}</li>
            </ul>
            <slot name="extra" />
          </div>

          <footer v-if="!hideCancel" class="spd-confirm-foot">
            <button
              type="button"
              class="spd-confirm-btn spd-confirm-btn-cancel"
              :disabled="loading"
              @click="handleCancel"
            >
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="spd-confirm-btn"
              :class="[`spd-confirm-btn-${tone}`, { 'is-loading': loading }]"
              :disabled="loading"
              @click="handleConfirm"
            >
              <span v-if="loading" class="spd-confirm-spinner" aria-hidden="true" />
              <span v-else-if="tone === 'danger'">
                <AlertOctagon :size="16" />
              </span>
              <span v-else-if="tone === 'success'">
                <CheckCircle2 :size="16" />
              </span>
              <span>{{ loading ? '处理中...' : confirmText }}</span>
            </button>
          </footer>
          <footer v-else class="spd-confirm-foot">
            <button
              type="button"
              class="spd-confirm-btn spd-confirm-btn-primary"
              :disabled="loading"
              @click="handleConfirm"
            >
              {{ confirmText }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ===== 遮罩 ===== */
.spd-confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(6, 21, 43, 0.45);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  isolation: isolate;
}

/* ===== 弹窗主体 ===== */
.spd-confirm-panel {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  width: 100%;
  max-height: calc(100vh - 48px);
  border-radius: 16px;
  background: #ffffff;
  box-shadow:
    0 24px 64px -16px rgba(15, 23, 42, 0.32),
    0 8px 20px -6px rgba(99, 102, 241, 0.18),
    0 2px 6px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  font-size: 14px;
  color: #0f172a;
}

/* ===== Header ===== */
.spd-confirm-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid #f1f5f9;
  background: linear-gradient(135deg, #fafbff 0%, #f5f3ff 50%, #fdf2f8 100%);
}

.spd-confirm-title {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
}

.spd-confirm-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 50%, #8b5cf6 100%);
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.5);
}

.spd-confirm-icon.is-danger {
  background: linear-gradient(135deg, #dc2626 0%, #e11d48 100%);
  box-shadow: 0 4px 12px -4px rgba(220, 38, 38, 0.5);
}
.spd-confirm-icon.is-warning {
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  box-shadow: 0 4px 12px -4px rgba(249, 115, 22, 0.5);
}
.spd-confirm-icon.is-success {
  background: linear-gradient(135deg, #059669 0%, #0d9488 100%);
  box-shadow: 0 4px 12px -4px rgba(5, 150, 105, 0.5);
}
.spd-confirm-icon.is-info {
  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 100%);
  box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.5);
}

.spd-confirm-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  background: transparent;
  color: #64748b;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}
.spd-confirm-close:hover {
  background: #f1f5f9;
  color: #0f172a;
}

/* ===== Body ===== */
.spd-confirm-body {
  padding: 18px 22px;
  display: grid;
  gap: 10px;
}

.spd-confirm-desc {
  margin: 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.6;
}

.spd-confirm-details {
  margin: 0;
  padding: 10px 14px;
  list-style: none;
  background: linear-gradient(135deg, #fff7ed 0%, #fef3c7 100%);
  border-left: 3px solid #f97316;
  border-radius: 8px;
  display: grid;
  gap: 4px;
}

.spd-confirm-details li {
  color: #9a3412;
  font-size: 13px;
  line-height: 1.5;
}

.spd-confirm-details li::before {
  content: '•';
  margin-right: 6px;
  color: #ea580c;
  font-weight: 700;
}

.is-danger .spd-confirm-details {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  border-left-color: #dc2626;
}
.is-danger .spd-confirm-details li { color: #991b1b; }
.is-danger .spd-confirm-details li::before { color: #dc2626; }

.is-success .spd-confirm-details {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
  border-left-color: #059669;
}
.is-success .spd-confirm-details li { color: #065f46; }
.is-success .spd-confirm-details li::before { color: #059669; }

.is-info .spd-confirm-details {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-left-color: #2563eb;
}
.is-info .spd-confirm-details li { color: #1e40af; }
.is-info .spd-confirm-details li::before { color: #2563eb; }

/* ===== Footer / Buttons ===== */
.spd-confirm-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 22px 18px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfc;
}

.spd-confirm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: 10px;
  font: inherit;
  font-weight: 600;
  font-size: 13px;
  line-height: 1;
  color: #ffffff;
  cursor: pointer;
  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 100%);
  box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.45);
  transition: background 0.18s, box-shadow 0.18s, transform 0.18s, opacity 0.18s;
}

.spd-confirm-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px -4px rgba(99, 102, 241, 0.55);
}
.spd-confirm-btn:active {
  transform: translateY(0);
}
.spd-confirm-btn:disabled,
.spd-confirm-btn.is-loading {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.spd-confirm-btn-primary {
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  box-shadow: 0 4px 12px -4px rgba(37, 99, 235, 0.45);
}
.spd-confirm-btn-primary:hover {
  box-shadow: 0 8px 20px -4px rgba(37, 99, 235, 0.55);
}

.spd-confirm-btn-success {
  background: linear-gradient(135deg, #059669 0%, #0d9488 100%);
  box-shadow: 0 4px 12px -4px rgba(5, 150, 105, 0.45);
}
.spd-confirm-btn-success:hover {
  box-shadow: 0 8px 20px -4px rgba(5, 150, 105, 0.55);
}

.spd-confirm-btn-danger {
  background: linear-gradient(135deg, #dc2626 0%, #e11d48 100%);
  box-shadow: 0 4px 12px -4px rgba(220, 38, 38, 0.45);
}
.spd-confirm-btn-danger:hover {
  box-shadow: 0 8px 20px -4px rgba(220, 38, 38, 0.55);
}

.spd-confirm-btn-warning {
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  box-shadow: 0 4px 12px -4px rgba(249, 115, 22, 0.45);
}
.spd-confirm-btn-warning:hover {
  box-shadow: 0 8px 20px -4px rgba(249, 115, 22, 0.55);
}

.spd-confirm-btn-info {
  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 100%);
  box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.45);
}

.spd-confirm-btn-cancel {
  background: #ffffff;
  color: #334155;
  border: 1px solid #cbd5e1;
  box-shadow: none;
}
.spd-confirm-btn-cancel:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #0f172a;
  transform: none;
  box-shadow: none;
}

/* ===== Loading spinner ===== */
.spd-confirm-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spd-confirm-spin 0.6s linear infinite;
}
@keyframes spd-confirm-spin {
  to { transform: rotate(360deg); }
}

/* ===== Transition ===== */
.spd-confirm-fade-enter-active,
.spd-confirm-fade-leave-active {
  transition: opacity 0.18s ease;
}
.spd-confirm-fade-enter-active .spd-confirm-panel,
.spd-confirm-fade-leave-active .spd-confirm-panel {
  transition: transform 0.22s ease, opacity 0.22s ease;
}
.spd-confirm-fade-enter-from,
.spd-confirm-fade-leave-to {
  opacity: 0;
}
.spd-confirm-fade-enter-from .spd-confirm-panel,
.spd-confirm-fade-leave-to .spd-confirm-panel {
  transform: scale(0.96) translateY(8px);
  opacity: 0;
}
</style>
