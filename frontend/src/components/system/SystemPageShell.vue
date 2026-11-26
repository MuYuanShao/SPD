<!-- =====================================================================
     SystemPageShell — 系统管理统一页面布局
     ---------------------------------------------------------------------
     造型与证照管理 / 主数据模块一致：
       md-page (页面布局)
       md-tabs (标签切换)
       md-toolbar (搜索 + 操作按钮)
       slot: content (表格 / 内容区)
       slot: footer (可选分页)
     ===================================================================== -->
<script setup lang="ts">
import { Search } from '@lucide/vue'
import { ElInput } from 'element-plus'
import MdPageHeader from '../master-data/MdPageHeader.vue'

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
    <MdPageHeader :title="title" :show-refresh="false">
      <template v-if="$slots['header-actions']" #actions><slot name="header-actions" /></template>
    </MdPageHeader>

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
          <SpdButton icon-only variant="ghost" tone="neutral" class="sys-search-btn" type="button" :disabled="loading" @click="onSearch">
            <template #icon><Search :size="15" /></template>
          </SpdButton>
        </div>
      </div>
      <div class="sys-toolbar-right">
        <slot name="toolbar-actions" />
        <SpdButton tone="primary" v-if="primaryBtnLabel"
         
         
          @click="emit('primary')">
          <template #icon><component v-if="primaryBtnIcon" :is="primaryBtnIcon" :size="15" /></template>
          {{ primaryBtnLabel }}
        </SpdButton>
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
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-base);
  padding: 0 12px;
  min-height: 34px;
  box-sizing: border-box;
}
.sys-search-input::placeholder { color: var(--spd-p-text-secondary); }
.sys-search-input:disabled { opacity: .5; cursor: not-allowed; }
/* 搜索按钮内嵌在输入框右侧：保留左侧分隔线，其余几何交给 SpdButton */
.sys-search-btn {
  border-left: 1px solid var(--md-border);
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

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
