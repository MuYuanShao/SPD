<!-- =====================================================================
     SystemPageShell — 系统管理统一页面布局
     ---------------------------------------------------------------------
     造型与证照管理 / 主数据模块一致：
       md-page (深绿渐变顶栏)
       md-tabs (标签切换)
       md-toolbar (搜索 + 操作按钮)
       slot: content (表格 / 内容区)
       slot: footer (可选分页)
     ===================================================================== -->
<script setup lang="ts">
import { Search } from '@lucide/vue'
import { ElInput } from 'element-plus'

interface Props {
  title: string
  subtitle?: string
  crumb?: string
  /** 默认 search placeholder */
  searchPlaceholder?: string
  /** true = 显示 Tab bar */
  tabs?: Array<{ key: string; label: string }>
  modelValue?: string       // active tab key
  /** true = 渲染工具栏（搜索 + 主按钮） */
  toolbar?: boolean
  searchLabel?: string
  primaryBtnLabel?: string
  primaryBtnIcon?: any
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  crumb: '系统管理',
  searchPlaceholder: '搜索关键词',
  tabs: () => [],
  toolbar: true,
  searchLabel: '关键词',
  loading: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'search', v: string): void
  (e: 'primary'): void
}>()

const localSearch = defineModel<string>('search', { default: '' })

function onSearch() { emit('search', localSearch.value) }
function onTab(key: string) { emit('update:modelValue', key) }
</script>

<template>
  <div class="md-page sys-page">
    <!-- 顶栏 -->
    <header class="md-header">
      <div class="md-header-inner">
        <div class="md-header-text">
          <p class="md-header-crumbs">
            <strong>{{ crumb }}</strong>
          </p>
          <h1 class="md-header-title">
            <span>{{ title }}</span>
            <small v-if="subtitle">{{ subtitle }}</small>
          </h1>
        </div>
        <div class="md-header-actions">
          <slot name="header-actions" />
        </div>
      </div>
    </header>

    <!-- 标签栏（可选） -->
    <div v-if="tabs.length" class="md-tabs sys-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="['md-tab', { active: modelValue === tab.key }]"
        @click="onTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 工具栏 -->
    <div v-if="toolbar" class="md-toolbar sys-toolbar">
      <div class="sys-search-wrap">
        <span class="sys-search-label">{{ searchLabel }}</span>
        <div class="sys-search-input-wrap">
          <input
            v-model.trim="localSearch"
            class="sys-search-input"
            :placeholder="searchPlaceholder"
            :disabled="loading"
            @keyup.enter="onSearch"
          />
          <button class="sys-search-btn" type="button" :disabled="loading" @click="onSearch">
            <Search :size="15" />
          </button>
        </div>
      </div>
      <div class="sys-toolbar-right">
        <slot name="toolbar-actions" />
        <button
          v-if="primaryBtnLabel"
          type="button"
          class="md-btn md-btn-primary"
          @click="emit('primary')"
        >
          <component v-if="primaryBtnIcon" :is="primaryBtnIcon" :size="15" />
          {{ primaryBtnLabel }}
        </button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="md-content sys-content">
      <slot />
    </div>

    <!-- 分页区（可选） -->
    <slot name="footer" />
  </div>
</template>

<style scoped>
/* ===================================================================
   SystemPageShell — 系统管理专有补充样式
   =================================================================== */

/* 顶栏内间距略收窄（与证照管理一致） */
.sys-page .md-header { padding: 16px 24px 16px; }

/* Tab bar */
.sys-tabs {
  margin: 0 24px;
  overflow-x: auto;
}

/* 工具栏 */
.sys-toolbar {
  margin: 0 24px;
  min-height: 52px;
}

/* 搜索行 */
.sys-search-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.sys-search-label {
  color: var(--md-text-secondary);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
.sys-search-input-wrap {
  display: flex;
  align-items: center;
  gap: 0;
  flex: 1;
  max-width: 420px;
  border: 1px solid var(--md-border);
  border-radius: var(--md-radius);
  background: var(--md-bg-card);
  overflow: hidden;
  transition: border-color .18s, box-shadow .18s;
}
.sys-search-input-wrap:focus-within {
  border-color: var(--md-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--md-primary) 16%, transparent);
}
.sys-search-input {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--md-text);
  font-size: 13px;
  padding: 0 12px;
  min-height: 34px;
  box-sizing: border-box;
}
.sys-search-input::placeholder { color: var(--md-text-muted); }
.sys-search-input:disabled { opacity: .5; cursor: not-allowed; }
.sys-search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-left: 1px solid var(--md-border);
  background: var(--md-bg-muted);
  color: var(--md-text-secondary);
  cursor: pointer;
  transition: color .18s, background .18s;
  flex-shrink: 0;
}
.sys-search-btn:hover:not(:disabled) {
  background: var(--md-primary-soft);
  color: var(--md-primary);
}
.sys-search-btn:disabled { opacity: .45; cursor: not-allowed; }

/* 工具栏右侧 */
.sys-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: auto;
}

/* 内容区 */
.sys-content {
  padding: 0 24px;
}
</style>
