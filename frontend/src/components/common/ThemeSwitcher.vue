<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Check, Palette } from '@lucide/vue'
import { THEMES, useThemeStore, type ThemeId } from '../../stores/theme'

const themeStore = useThemeStore()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const active = computed(() => THEMES.find((t) => t.id === themeStore.current) ?? THEMES[0])

function pick(id: ThemeId) {
  if (themeStore.current !== id) {
    themeStore.setTheme(id)
  }
  open.value = false
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
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const idx = THEMES.findIndex((t) => t.id === themeStore.current)
    const dir = event.key === 'ArrowDown' ? 1 : -1
    const next = (idx + dir + THEMES.length) % THEMES.length
    pick(THEMES[next].id)
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
      :aria-label="`当前主题：${active.name}，点击切换主题`"
      :title="active.name"
      @click.stop="toggle"
    >
      <span class="spd-theme-swatch" :style="{ background: active.swatch }">
        <Palette :size="14" class="spd-theme-swatch-icon" />
      </span>
      <span class="spd-theme-trigger-label">{{ active.name }}</span>
    </button>

    <Transition name="spd-theme-pop">
      <div
        v-if="open"
        ref="panelRef"
        class="spd-theme-panel"
        role="dialog"
        aria-label="选择主题"
        tabindex="-1"
        @click.stop
      >
        <div class="spd-theme-panel-header">
          <span class="spd-theme-panel-title">选择主题</span>
          <span class="spd-theme-panel-sub">实时预览 · 自动保存</span>
        </div>
        <div class="spd-theme-grid">
          <button
            v-for="t in THEMES"
            :key="t.id"
            type="button"
            role="option"
            :aria-selected="t.id === themeStore.current"
            :class="['spd-theme-card', { 'is-active': t.id === themeStore.current }]"
            @click="pick(t.id)"
          >
            <span class="spd-theme-card-preview" :style="{ background: t.swatch }">
              <Check v-if="t.id === themeStore.current" :size="22" class="spd-theme-card-check" />
            </span>
            <span class="spd-theme-card-meta">
              <span class="spd-theme-card-name">{{ t.name }}</span>
              <span class="spd-theme-card-hint">{{ t.hint }}</span>
            </span>
          </button>
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
  border: 1px solid var(--fli-line, #e5e7eb);
  border-radius: 999px;
  background: var(--fli-surface, #ffffff);
  color: var(--fli-text, #1f2937);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  transition: all 0.18s ease;
  user-select: none;
}

.spd-theme-trigger:hover {
  border-color: var(--fli-brand, #0f766e);
  box-shadow: 0 2px 8px rgba(15, 118, 110, 0.12);
}

.spd-theme-trigger.is-open {
  border-color: var(--fli-brand, #0f766e);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
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
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.5), 0 1px 2px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.spd-theme-swatch-icon {
  color: rgba(255, 255, 255, 0.95);
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.25));
}

/* 弹出面板 */
.spd-theme-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 2200;
  width: 320px;
  padding: 14px;
  border: 1px solid var(--fli-line, #e5e7eb);
  border-radius: 12px;
  background: var(--fli-surface, #ffffff);
  box-shadow:
    0 12px 32px rgba(0, 0, 0, 0.14),
    0 2px 6px rgba(0, 0, 0, 0.06);
  outline: none;
}

.spd-theme-panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 2px;
}

.spd-theme-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fli-text, #1f2937);
}

.spd-theme-panel-sub {
  font-size: 11px;
  color: var(--fli-muted, #6b7280);
}

/* 主题卡片网格 */
.spd-theme-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.spd-theme-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 8px 8px;
  border: 1px solid var(--fli-line, #e5e7eb);
  border-radius: 10px;
  background: var(--fli-surface, #ffffff);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
  font: inherit;
  color: inherit;
}

.spd-theme-card:hover {
  border-color: var(--fli-brand, #0f766e);
  background: color-mix(in srgb, var(--fli-brand, #0f766e) 4%, transparent);
  transform: translateX(2px);
}

.spd-theme-card.is-active {
  border-color: var(--fli-brand, #0f766e);
  background: color-mix(in srgb, var(--fli-brand, #0f766e) 6%, transparent);
  box-shadow: 0 0 0 1px var(--fli-brand, #0f766e) inset;
}

.spd-theme-card-preview {
  position: relative;
  width: 56px;
  height: 40px;
  border-radius: 8px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease;
}

.spd-theme-card:hover .spd-theme-card-preview {
  transform: scale(1.04);
}

.spd-theme-card-check {
  color: #ffffff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.3));
}

.spd-theme-card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.spd-theme-card-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fli-text, #1f2937);
}

.spd-theme-card-hint {
  font-size: 11px;
  color: var(--fli-muted, #6b7280);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
