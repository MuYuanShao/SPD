<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import {
  ChevronDown,
  Home,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  UserRound
} from '@lucide/vue'
import AiMedicalAssistant from './components/common/AiMedicalAssistant.vue'
import { menuGroups, type MenuItem } from './config/menu'
import { featureRouteTarget } from './config/featureCatalog'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const uiStore = useUiStore()
const isLoginPage = computed(() => route.name === 'login')
const expandedParentMenus = ref(new Set<string>())
const openNavGroups = ref(new Set<string>(menuGroups.map(g => g.code)))
const activeFeatureCode = computed(() => String(route.meta.featureCode || route.params.code || ''))

function authorizedMenu(item: MenuItem): MenuItem | null {
  const children = item.children
    ?.map(authorizedMenu)
    .filter((child): child is MenuItem => child !== null)
  const accessGranted =
    item.code === 'supplier-manufacturer-management'
      ? authStore.canAccessSupplierManufacturerManagement()
      : item.code === 'picking-records'
        ? authStore.canAccessMenu('picking-delivery')
      : authStore.canAccessMenu(item.code)
  if (!accessGranted && !children?.length) {
    return null
  }
  return { ...item, ...(children?.length ? { children } : { children: undefined }) }
}

const visibleMenuGroups = computed(() =>
  menuGroups.map(authorizedMenu).filter((group): group is MenuItem => group !== null)
)

function toggleNavGroup(code: string) {
  const next = new Set(openNavGroups.value)
  if (next.has(code)) { next.delete(code) } else { next.add(code) }
  openNavGroups.value = next
}
function isNavGroupOpen(code: string) {
  return openNavGroups.value.has(code)
}

function handleLogout() {
  authStore.logout()
  void router.push({ name: 'login' })
}

function toggleParentMenu(item: MenuItem) {
  const next = new Set(expandedParentMenus.value)
  if (next.has(item.code)) {
    next.delete(item.code)
  } else {
    next.add(item.code)
  }
  expandedParentMenus.value = next
}

function hasActiveChild(item: MenuItem) {
  return Boolean(item.children?.some((child) => child.code === activeFeatureCode.value))
}

function isParentMenuOpen(item: MenuItem) {
  return expandedParentMenus.value.has(item.code) || hasActiveChild(item)
}

/** Preserve table working area on compact desktop screens. */
let compactViewport: boolean | undefined

function handleResize() {
  const compact = window.innerWidth > 768 && window.innerWidth <= 1366
  // Apply the breakpoint only when crossing it, preserving manual toggles on resize.
  if (compact !== compactViewport) {
    compactViewport = compact
    uiStore.setSidebarCollapsed(compact)
  }
}

onMounted(() => {
  if (!isLoginPage.value) {
    authStore.init()
  }
  window.addEventListener('resize', handleResize)
  handleResize()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <RouterView v-if="isLoginPage" />

  <div v-else class="app-shell" :class="{ 'sidebar-collapsed': uiStore.sidebarCollapsed }">
    <aside class="sidebar">
      <div class="brand">
        <RouterLink class="brand-link" to="/" title="首页 / 工作台">
          <span class="brand-mark">SPD</span>
          <span class="brand-text">
            <strong>院内 SPD</strong>
            <small>供应链管理平台</small>
          </span>
        </RouterLink>
        <button
          class="sidebar-toggle"
          type="button"
          :aria-label="uiStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          :title="uiStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="uiStore.toggleSidebar()"
        >
          <PanelLeftOpen v-if="uiStore.sidebarCollapsed" :size="18" />
          <PanelLeftClose v-else :size="18" />
        </button>
      </div>

      <nav class="nav-list" aria-label="系统菜单">
        <RouterLink v-if="authStore.canAccessMenu('dashboard')" class="nav-item" to="/" title="首页 / 工作台">
          <Home :size="18" />
          <span>首页 / 工作台</span>
        </RouterLink>
        <div v-for="group in visibleMenuGroups" :key="group.code" class="nav-group">
            <button
              class="nav-group-toggle"
              type="button"
              :aria-expanded="isNavGroupOpen(group.code)"
              :title="group.title"
              @click="toggleNavGroup(group.code)"
            >
              <component :is="group.icon" v-if="group.icon" :size="18" />
              <span>{{ group.title }}</span>
              <ChevronDown class="nav-parent-arrow" :class="{ open: isNavGroupOpen(group.code) }" :size="15" />
            </button>

            <Transition name="nav-collapse">
              <div v-if="isNavGroupOpen(group.code)" class="nav-children">
                <template v-for="item in group.children" :key="item.code">
                  <button
                    v-if="item.children?.length"
                    class="nav-item child nav-parent-item"
                    type="button"
                    :aria-expanded="isParentMenuOpen(item)"
                    :title="item.title"
                    @click="toggleParentMenu(item)"
                  >
                    <component :is="item.icon" v-if="item.icon" :size="16" />
                    <span>{{ item.title }}</span>
                    <ChevronDown class="nav-parent-arrow" :class="{ open: isParentMenuOpen(item) }" :size="15" />
                  </button>

                  <RouterLink v-else class="nav-item child" :to="featureRouteTarget(item.code)" :title="item.title">
                    <component :is="item.icon" v-if="item.icon" :size="16" />
                    <span>{{ item.title }}</span>
                  </RouterLink>

                  <Transition name="nav-collapse">
                    <div v-if="isParentMenuOpen(item)" class="nav-grandchildren">
                      <RouterLink
                        v-for="child in item.children"
                        :key="child.code"
                        class="nav-item grandchild"
                        :to="featureRouteTarget(child.code)"
                        :title="child.title"
                      >
                        <component :is="child.icon" v-if="child.icon" :size="15" />
                        <span>{{ child.title }}</span>
                      </RouterLink>
                    </div>
                  </Transition>
                </template>
              </div>
            </Transition>
          </div>
      </nav>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <h1>院内 SPD 供应链管理平台</h1>
        </div>
        <details class="account-menu">
          <summary>
            <span class="account-avatar">{{ authStore.username.slice(0, 1) || '用' }}</span>
            <span>
              <strong>{{ authStore.username || '系统管理员' }}</strong>
              <small>当前账号</small>
            </span>
            <ChevronDown :size="16" />
          </summary>
          <div class="account-dropdown">
            <div class="account-info">
              <UserRound :size="18" />
              <div>
                <strong>{{ authStore.username || '系统管理员' }}</strong>
                <span>SPD 管理端用户</span>
              </div>
            </div>
            <button type="button" @click="handleLogout">
              <LogOut :size="16" />
              退出
            </button>
          </div>
        </details>
      </header>

      <RouterView />
      <AiMedicalAssistant />
    </main>
  </div>
</template>
