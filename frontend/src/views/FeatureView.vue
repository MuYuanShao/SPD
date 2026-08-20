<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight, Database, KeyRound, LayoutList, Pencil, Plus, Save, ShieldCheck, Trash2, UserCheck, X } from '@lucide/vue'
import { flatMenus } from '../config/menu'
import { fetchFeatureMetadata, type FeatureMetadata } from '../api/features'

const route = useRoute()
const feature = ref<FeatureMetadata | null>(null)
const loading = ref(false)
const actionModal = ref<string | null>(null)

const currentMenu = computed(() => flatMenus.find((item) => item.code === route.params.code))
const childMenus = computed(() => currentMenu.value?.children ?? [])
const featureCode = computed(() => String(route.params.code ?? ''))
const actionButtons = computed(() => {
  if (featureCode.value === 'user-management') {
    return [
      { label: '用户列表查询', icon: LayoutList, tone: 'default' },
      { label: '新增', icon: Plus, tone: 'primary' },
      { label: '编辑', icon: Pencil, tone: 'default' },
      { label: '删除', icon: Trash2, tone: 'danger' },
      { label: '重置密码', icon: KeyRound, tone: 'default' },
      { label: '分配角色', icon: UserCheck, tone: 'default' }
    ]
  }

  if (featureCode.value === 'role-permission') {
    return [
      { label: '角色列表查询', icon: LayoutList, tone: 'default' },
      { label: '新增', icon: Plus, tone: 'primary' },
      { label: '编辑', icon: Pencil, tone: 'default' },
      { label: '删除', icon: Trash2, tone: 'danger' },
      { label: '分配权限', icon: ShieldCheck, tone: 'default' },
      { label: '数据权限', icon: Database, tone: 'default' }
    ]
  }

  return []
})

async function loadFeature() {
  if (!featureCode.value) return
  loading.value = true

  try {
    feature.value = await fetchFeatureMetadata(featureCode.value)
  } finally {
    loading.value = false
  }
}

function openAction(action: string) {
  if (action === '新增' || action === '编辑') {
    actionModal.value = action
  }
}

onMounted(loadFeature)
watch(featureCode, loadFeature)
</script>

<template>
  <section v-if="currentMenu" class="module-detail">
    <div class="breadcrumb-line">
      <template v-for="parent in currentMenu.parents" :key="parent.code">
        <span>{{ parent.title }}</span>
        <ChevronRight :size="14" />
      </template>
      <strong>{{ currentMenu.title }}</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>功能菜单</p>
        <h2>{{ feature?.title || currentMenu.title }}</h2>
      </div>
      <span class="status-pill">
        <LayoutList :size="16" />
        {{ childMenus.length ? `${childMenus.length} 个子功能` : loading ? '加载中' : '已联动后端' }}
      </span>
    </div>

    <div v-if="childMenus.length" class="feature-list">
      <RouterLink v-for="item in childMenus" :key="item.code" class="feature-row" :to="`/features/${item.code}`">
        <component :is="item.icon" v-if="item.icon" :size="18" />
        <span>{{ item.title }}</span>
      </RouterLink>
    </div>

    <div v-if="actionButtons.length" class="system-action-row">
      <button
        v-for="action in actionButtons"
        :key="action.label"
        type="button"
        :class="['btn', action.tone]"
        @click="openAction(action.label)"
      >
        <component :is="action.icon" :size="17" />
        {{ action.label }}
      </button>
    </div>

    <div v-if="actionModal" class="attachment-preview-mask" @click.self="actionModal = null">
      <section class="supplier-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>{{ feature?.title || currentMenu.title }}</p>
            <h3>{{ actionModal }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="actionModal = null">
            <X :size="18" />
          </button>
        </header>
        <form class="supplier-form-grid" @submit.prevent="actionModal = null">
          <label>
            <span>编码</span>
            <input placeholder="请输入编码" />
          </label>
          <label>
            <span>名称</span>
            <input placeholder="请输入名称" />
          </label>
          <label class="wide">
            <span>说明</span>
            <textarea placeholder="请输入说明"></textarea>
          </label>
          <div class="dialog-actions">
            <button class="btn" type="button" @click="actionModal = null">取消</button>
            <button class="btn btn-primary" type="submit">
              <Save :size="17" />
              保存
            </button>
          </div>
        </form>
      </section>
    </div>

    <div class="architecture-note">
      <LayoutList :size="20" />
      <p>{{ feature?.description || '当前功能已接入后端元数据接口，业务数据接口将按模块继续补齐。' }}</p>
    </div>

    <div v-if="feature?.capabilities.length" class="feature-list feature-capabilities">
      <article v-for="capability in feature.capabilities" :key="capability" class="feature-row">
        <LayoutList :size="18" />
        <span>{{ capability }}</span>
      </article>
    </div>
  </section>

  <section v-else class="empty-state">
    <h2>菜单不存在</h2>
    <p>请从左侧功能菜单重新进入。</p>
  </section>
</template>
