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
            <SpdButton tone="neutral" :disabled="loading" @click="handleCancel">
              {{ cancelText }}
            </SpdButton>
            <SpdButton
              :tone="tone === 'primary' ? 'primary' : tone"
              :label="loading ? '处理中...' : confirmText"
              :loading="loading"
              :disabled="loading"
              @click="handleConfirm">
              <template v-if="tone === 'danger'" #icon>
                <AlertOctagon :size="16" />
              </template>
              <template v-else-if="tone === 'success'" #icon>
                <CheckCircle2 :size="16" />
              </template>
            </SpdButton>
          </footer>
          <footer v-else class="spd-confirm-foot">
            <SpdButton
              :tone="tone === 'primary' ? 'primary' : tone"
              :label="loading ? '处理中...' : confirmText"
              :loading="loading"
              :disabled="loading"
              @click="handleConfirm">
              <template v-if="tone === 'danger'" #icon>
                <AlertOctagon :size="16" />
              </template>
              <template v-else-if="tone === 'success'" #icon>
                <CheckCircle2 :size="16" />
              </template>
            </SpdButton>
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
  background: color-mix(in srgb, var(--spd-p-text-strong) 45%, transparent);
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-strong);
}

/* ===== Header ===== */
.spd-confirm-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--spd-p-text-strong);
  background: linear-gradient(135deg, var(--spd-p-bg-subtle) 0%, var(--spd-p-bg-subtle) 50%, var(--spd-p-danger-bg) 100%);
}

.spd-confirm-title {
  flex: 1;
  margin: 0;
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  color: var(--spd-p-text-strong);
  line-height: var(--spd-lh-tight);
}

.spd-confirm-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--spd-p-header-gradient);
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}

.spd-confirm-icon.is-danger {
  background: var(--spd-p-danger);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-icon.is-warning {
  background: var(--spd-p-warning);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-icon.is-success {
  background: var(--spd-p-success);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-icon.is-info {
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary) 100%);
  box-shadow: var(--spd-p-shadow-2);
}

.spd-confirm-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  background: transparent;
  color: var(--spd-p-text-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}
/* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
.spd-confirm-close:hover {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-strong);
}

/* ===== Body ===== */
.spd-confirm-body {
  padding: 18px 22px;
  display: grid;
  gap: 10px;
}

.spd-confirm-desc {
  margin: 0;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-loose);
}

.spd-confirm-details {
  margin: 0;
  padding: 10px 14px;
  list-style: none;
  background: linear-gradient(135deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-bg) 100%);
  border-left: 3px solid var(--spd-p-warning);
  border-radius: 8px;
  display: grid;
  gap: 4px;
}

.spd-confirm-details li {
  color: var(--spd-p-danger-text);
  font-size: var(--spd-font-size-sm);
  line-height: 1.5;
}

.spd-confirm-details li::before {
  content: '•';
  margin-right: 6px;
  color: var(--spd-p-warning-strong);
  font-weight: 700;
}

.is-danger .spd-confirm-details {
  background: linear-gradient(135deg, var(--spd-p-danger-bg) 0%, var(--spd-p-danger-bg) 100%);
  border-left-color: var(--spd-p-danger);
}
.is-danger .spd-confirm-details li { color: var(--spd-p-danger-strong); }
.is-danger .spd-confirm-details li::before { color: var(--spd-p-danger); }

.is-success .spd-confirm-details {
  background: linear-gradient(135deg, var(--spd-p-primary-bg) 0%, var(--spd-p-success-bg) 100%);
  border-left-color: var(--spd-p-success);
}
.is-success .spd-confirm-details li { color: var(--spd-p-primary-strong); }
.is-success .spd-confirm-details li::before { color: var(--spd-p-success); }

.is-info .spd-confirm-details {
  background: linear-gradient(135deg, var(--spd-p-info-bg) 0%, var(--spd-p-border) 100%);
  border-left-color: var(--spd-p-info);
}
.is-info .spd-confirm-details li { color: var(--spd-p-info-strong); }
.is-info .spd-confirm-details li::before { color: var(--spd-p-info); }

/* ===== Footer / Buttons ===== */
.spd-confirm-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--spd-p-text-strong);
  background: var(--spd-p-row-stripe);
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
  font-size: var(--spd-font-size-base);
  line-height: 1;
  color: var(--spd-p-bg);
  cursor: pointer;
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary) 100%);
  box-shadow: var(--spd-p-shadow-2);
  transition: background 0.18s, box-shadow 0.18s, transform 0.18s, opacity 0.18s;
}

.spd-confirm-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--spd-p-shadow-3);
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
  background: var(--spd-p-header-gradient);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-btn-primary:hover {
  box-shadow: var(--spd-p-shadow-3);
}

.spd-confirm-btn-success {
  background: var(--spd-p-success);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-btn-success:hover {
  box-shadow: var(--spd-p-shadow-3);
}

.spd-confirm-btn-danger {
  background: var(--spd-p-danger);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-btn-danger:hover {
  box-shadow: var(--spd-p-shadow-3);
}

.spd-confirm-btn-warning {
  background: var(--spd-p-warning);
  box-shadow: var(--spd-p-shadow-2);
}
.spd-confirm-btn-warning:hover {
  box-shadow: var(--spd-p-shadow-3);
}

.spd-confirm-btn-info {
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary) 100%);
  box-shadow: var(--spd-p-shadow-2);
}

.spd-confirm-btn-cancel {
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  border: 1px solid var(--spd-p-border-strong);
  box-shadow: none;
}
/* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
.spd-confirm-btn-cancel:hover {
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-border-strong);
  color: var(--spd-p-text-strong);
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
