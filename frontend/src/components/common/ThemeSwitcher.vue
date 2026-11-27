<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Check, Palette } from '@lucide/vue'
import {
  SCHEMES,
  FONT_SIZES,
  DEFAULT_FONT_SIZE,
  useThemeStore,
  type SchemeId,
  type FontSizeId
} from '../../stores/theme'

const themeStore = useThemeStore()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const activeScheme = computed(
  () => SCHEMES.find((s) => s.id === themeStore.scheme) ?? SCHEMES[0]
)
const activeFontSize = computed(
  () => FONT_SIZES.find((f) => f.id === themeStore.fontSize) ?? FONT_SIZES[0]
)

/** 暂未开放的配色方案（灰显展示，不可点击） */
const pendingSchemes = computed(() => SCHEMES.filter((s) => s.disabled))

function pickScheme(id: SchemeId) {
  if (themeStore.scheme !== id) {
    themeStore.setScheme(id)
  }
}

function pickFontSize(id: FontSizeId) {
  if (themeStore.fontSize !== id) {
    themeStore.setFontSize(id)
  }
}

function resetFontSize() {
  pickFontSize(DEFAULT_FONT_SIZE)
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    nextTick(() => panelRef.value?.focus())
  }
}

function handleDocumentClick(event: MouseEvent) {
  if (!open.value) return
  const target = event.target as Node | null
  if (rootRef.value && target && !rootRef.value.contains(target)) {
    open.value = false
  }
}

function handleKey(event: KeyboardEvent) {
  if (!open.value) return
  if (event.key === 'Escape') {
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div ref="rootRef" class="spd-theme-switcher">
    <button
      type="button"
      class="spd-theme-trigger"
      :class="{ 'is-open': open }"
      :aria-expanded="open"
      aria-haspopup="dialog"
      :aria-label="`当前配色方案：${activeScheme.name}，点击切换配色方案与界面字号`"
      :title="activeScheme.name"
      @click.stop="toggle"
    >
      <span class="spd-theme-swatch" :style="{ background: activeScheme.swatch }">
        <Palette :size="14" class="spd-theme-swatch-icon" />
      </span>
      <span class="spd-theme-trigger-label">{{ activeScheme.name }}</span>
    </button>

    <Transition name="spd-theme-pop">
      <div
        v-if="open"
        ref="panelRef"
        class="spd-theme-panel"
        role="dialog"
        aria-label="选择配色方案与界面字号"
        tabindex="-1"
        @click.stop
      >
        <!-- 配色方案：目前仅「标准白」开放，其余方案暂未开放（灰显不可点） -->
        <div class="spd-theme-panel-header">
          <span class="spd-theme-panel-title">配色方案</span>
          <span class="spd-theme-panel-sub">全站生效 · 自动保存</span>
        </div>

        <div class="spd-scheme-grid" role="radiogroup" aria-label="配色方案">
          <button
            v-for="s in SCHEMES"
            :key="s.id"
            type="button"
            role="radio"
            :aria-checked="s.id === themeStore.scheme"
            :aria-disabled="s.disabled ? 'true' : undefined"
            :disabled="s.disabled"
            :class="[
              'spd-scheme-card',
              { 'is-active': s.id === themeStore.scheme, 'is-disabled': s.disabled }
            ]"
            @click="pickScheme(s.id)"
          >
            <span class="spd-scheme-card-preview" :style="{ background: s.swatch }">
              <Check v-if="s.id === themeStore.scheme" :size="18" class="spd-scheme-card-check" />
            </span>
            <span class="spd-scheme-card-meta">
              <span class="spd-scheme-card-name">{{ s.name }}</span>
              <span class="spd-scheme-card-hint">{{ s.hint }}</span>
            </span>
            <span v-if="s.disabled" class="spd-scheme-card-badge">暂未开放</span>
          </button>
        </div>

        <div class="spd-theme-panel-sub spd-palette-current">
          当前配色方案：<strong>{{ activeScheme.name }}</strong>
          <span v-if="pendingSchemes.length" class="spd-scheme-pending-note">
            （其余方案暂未开放）
          </span>
        </div>

        <!-- 字号档位：小 / 中 / 大 -->
        <div class="spd-theme-panel-header spd-theme-panel-header--second">
          <span class="spd-theme-panel-title">界面字号</span>
          <button
            type="button"
            class="spd-theme-reset"
            :disabled="themeStore.fontSize === DEFAULT_FONT_SIZE"
            @click="resetFontSize"
          >
            恢复默认
          </button>
        </div>

        <div class="spd-fontsize-grid" role="radiogroup" aria-label="界面字号">
          <button
            v-for="f in FONT_SIZES"
            :key="f.id"
            type="button"
            role="radio"
            :aria-checked="f.id === themeStore.fontSize"
            :class="['spd-fontsize-card', { 'is-active': f.id === themeStore.fontSize }]"
            @click="pickFontSize(f.id)"
          >
            <span class="spd-fontsize-sample" :style="{ fontSize: f.base }">Aa</span>
            <span class="spd-fontsize-meta">
              <span class="spd-fontsize-name">
                {{ f.name }}
                <small>{{ f.base }}</small>
              </span>
              <span class="spd-fontsize-hint">{{ f.hint }}</span>
            </span>
            <Check v-if="f.id === themeStore.fontSize" :size="16" class="spd-fontsize-check" />
          </button>
        </div>

        <div class="spd-theme-panel-sub spd-palette-current">
          当前字号：<strong>{{ activeFontSize.name }}</strong>
          <code>{{ activeFontSize.base }}</code>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.spd-theme-switcher {
  position: relative;
  display: inline-flex;
}

/* 触发按钮：色块 + 文字 */
.spd-theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px 0 6px;
  border: 1px solid var(--fli-line, var(--spd-p-border));
  border-radius: 999px;
  background: var(--fli-surface, var(--spd-p-bg));
  color: var(--fli-text, var(--spd-p-text-secondary));
  cursor: pointer;
  font-size: var(--spd-font-size-base);
  line-height: 1;
  transition: all 0.18s ease;
  user-select: none;
}

.spd-theme-trigger:hover {
  border-color: var(--fli-brand, var(--spd-p-primary));
  box-shadow: var(--spd-p-shadow-1);
}

.spd-theme-trigger.is-open {
  border-color: var(--fli-brand, var(--spd-p-primary));
  box-shadow: var(--spd-p-shadow-1);
}

.spd-theme-trigger-label {
  font-weight: 500;
  white-space: nowrap;
}

/* 色块圆点 */
.spd-theme-swatch {
  position: relative;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--spd-p-shadow-1);
  flex-shrink: 0;
}

.spd-theme-swatch-icon {
  color: color-mix(in srgb, var(--spd-p-bg) 95%, transparent);
  filter: drop-shadow(0 1px 1px color-mix(in srgb, var(--spd-p-text-strong) 25%, transparent));
}

/* 弹出面板 */
.spd-theme-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 2200;
  width: 320px;
  padding: 14px;
  border: 1px solid var(--fli-line, var(--spd-p-border));
  border-radius: 12px;
  background: var(--fli-surface, var(--spd-p-bg));
  box-shadow: var(--spd-p-shadow-3);
  outline: none;
}

.spd-theme-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 2px;
}

.spd-theme-panel-header--second {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--fli-line, var(--spd-p-border));
}

.spd-theme-panel-title {
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--fli-text, var(--spd-p-text-secondary));
}

.spd-theme-panel-sub {
  font-size: var(--spd-font-size-xs);
  color: var(--fli-muted, var(--spd-p-text-muted));
}

/* 恢复默认按钮 */
.spd-theme-reset {
  border: none;
  background: transparent;
  padding: 0;
  font-size: var(--spd-font-size-sm);
  color: var(--fli-brand, var(--spd-p-primary));
  cursor: pointer;
}

.spd-theme-reset:disabled {
  color: var(--fli-muted, var(--spd-p-text-muted));
  cursor: not-allowed;
  opacity: 0.6;
}

/* 当前状态摘要行 */
.spd-palette-current {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.spd-palette-current strong {
  color: var(--fli-text, var(--spd-p-text-secondary));
}

.spd-palette-current code {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs);
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
}

/* 配色方案卡片网格 */
.spd-scheme-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.spd-scheme-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 8px 8px;
  border: 1px solid var(--fli-line, var(--spd-p-border));
  border-radius: 10px;
  background: var(--fli-surface, var(--spd-p-bg));
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
  font: inherit;
  color: inherit;
}

.spd-scheme-card:hover {
  border-color: var(--fli-brand, var(--spd-p-primary));
  background: color-mix(in srgb, var(--fli-brand, var(--spd-p-primary)) 4%, transparent);
  transform: translateX(2px);
}

.spd-scheme-card.is-active {
  border-color: var(--fli-brand, var(--spd-p-primary));
  background: color-mix(in srgb, var(--fli-brand, var(--spd-p-primary)) 6%, transparent);
  box-shadow: var(--spd-p-shadow-1);
}

/* 暂未开放的方案：灰显、不可点击（disabled 属性已阻止点击） */
.spd-scheme-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
  filter: grayscale(0.7);
  border-style: dashed;
}

.spd-scheme-card.is-disabled:hover {
  border-color: var(--fli-line, var(--spd-p-border));
  background: var(--fli-surface, var(--spd-p-bg));
  transform: none;
}

.spd-scheme-card.is-disabled .spd-scheme-card-preview {
  box-shadow: none;
}

/* 「暂未开放」角标 */
.spd-scheme-card-badge {
  flex-shrink: 0;
  align-self: flex-start;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--fli-line, var(--spd-p-border));
  background: var(--spd-p-bg-muted, var(--fli-surface, var(--spd-p-bg)));
  color: var(--fli-muted, var(--spd-p-text-muted));
  font-size: var(--spd-font-size-xs);
  line-height: 1.6;
  white-space: nowrap;
}

.spd-scheme-pending-note {
  color: var(--fli-muted, var(--spd-p-text-muted));
}

.spd-scheme-card-preview {
  position: relative;
  width: 56px;
  height: 40px;
  border-radius: 8px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--spd-p-shadow-1);
  transition: transform 0.2s ease;
}

.spd-scheme-card:hover .spd-scheme-card-preview {
  transform: scale(1.04);
}

.spd-scheme-card-check {
  color: var(--spd-p-bg);
  filter: drop-shadow(0 1px 2px color-mix(in srgb, var(--spd-p-text-strong) 30%, transparent));
}

.spd-scheme-card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.spd-scheme-card-name {
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--fli-text, var(--spd-p-text-secondary));
}

.spd-scheme-card-hint {
  font-size: var(--spd-font-size-xs);
  color: var(--fli-muted, var(--spd-p-text-muted));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 字号档位卡片 */
.spd-fontsize-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.spd-fontsize-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--fli-line, var(--spd-p-border));
  border-radius: 10px;
  background: var(--fli-surface, var(--spd-p-bg));
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
  font: inherit;
  color: inherit;
  position: relative;
}

.spd-fontsize-card:hover {
  border-color: var(--fli-brand, var(--spd-p-primary));
  background: color-mix(in srgb, var(--fli-brand, var(--spd-p-primary)) 4%, transparent);
}

.spd-fontsize-card.is-active {
  border-color: var(--fli-brand, var(--spd-p-primary));
  background: color-mix(in srgb, var(--fli-brand, var(--spd-p-primary)) 6%, transparent);
  box-shadow: var(--spd-p-shadow-1);
}

/* 字号样张：用真实基准字号预览，方便用户直接判断 */
.spd-fontsize-sample {
  width: 44px;
  flex-shrink: 0;
  text-align: center;
  font-weight: 700;
  line-height: var(--spd-lh-tight);
  color: var(--fli-text, var(--spd-p-text-secondary));
}

.spd-fontsize-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.spd-fontsize-name {
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--fli-text, var(--spd-p-text-secondary));
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}

.spd-fontsize-name small {
  font-size: var(--spd-font-size-xs);
  font-weight: 500;
  color: var(--fli-muted, var(--spd-p-text-muted));
}

.spd-fontsize-hint {
  font-size: var(--spd-font-size-xs);
  color: var(--fli-muted, var(--spd-p-text-muted));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.spd-fontsize-check {
  color: var(--fli-brand, var(--spd-p-primary));
  flex-shrink: 0;
}

/* 弹出动画 */
.spd-theme-pop-enter-active,
.spd-theme-pop-leave-active {
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.spd-theme-pop-enter-from,
.spd-theme-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}
</style>
