<script setup lang="ts">
/**
 * SPD TopNav — 水平顶部导航栏
 *
 * 整合自 SPD 模板项目的设计语言：
 * - Logo + 水平主菜单（业务中心 / 数据中心 / 运营系统）+ 右侧操作区
 * - 主菜单项点击展开下拉分组（多级菜单可折叠）
 * - 自动根据当前路由激活对应模块
 */
import { computed, ref, watch, onMounted, onUnmounted, reactive } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { X, Minus, Trash2, MoreHorizontal } from '@lucide/vue'
import { findFeature, featureRouteTarget } from '../config/featureCatalog'
import {
  Box,
  Bell,
  RefreshCw,
  ChevronDown,
  ChevronRight,
  Home,
  ShoppingCart,
  Database,
  ShieldCheck,
  Landmark,
  BarChart3,
  Settings,
  LayoutGrid,
  FileText,
  Building2,
  Wallet,
  Search,
  Truck,
  Package,
  Tag,
  Link as LinkIcon,
  List as ListIcon,
  Trash,
  AlertTriangle,
  Stamp,
  TrendingUp,
  ScanLine,
  Key,
  Wrench,
  CheckCircle2,
  History,
  Clock,
  RotateCcw,
  PercentCircle,
  Box as BoxIcon,
  PackageOpen,
  Bell as BellIcon,
  Package2,
  PackageSearch,
  PackageCheck,
  ClipboardList,
  FileSearch,
  Receipt,
  Grid,
  HelpCircle,
  LogOut
} from '@lucide/vue'
import ThemeSwitcher from '../components/common/ThemeSwitcher.vue'
import { useAuthStore } from '../stores/auth'
import { menuGroups } from '../config/menu'

interface TabItem {
  key: string
  label: string
  fullPath: string
}

interface MenuLeaf {
  path: string
  name: string
}

interface MenuGroupUI {
  name: string
  key: string
  children: MenuUIChild[]
}

interface MenuUIChild {
  path?: string
  name: string
  icon?: any
  children?: MenuLeaf[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const refreshing = ref(false)
const activeModule = ref('dashboard')
const hoverSubmenu = ref(false)
const HOME_TAB: TabItem = { key: '/', label: '工作台', fullPath: '/' }
const tabs = ref<TabItem[]>([HOME_TAB])
const activeTab = ref('/')
const expandedGroups = reactive(new Set<string>())

const PATH_NAME: Record<string, string> = {
  '/': '工作台',
  '/login': '登录',
  '/forbidden': '无访问权限',
  '/dashboard/realtime': '实时工作台'
}

// 模块图标
const MODULE_ICONS: Record<string, any> = {
  dashboard: Home,
  'master-data-quota': Database,
  'quota-package-template': Package2,
  'supply-chain': ShoppingCart,
  'special-compliance': ShieldCheck,
  'settlement-finance': Landmark,
  'operations-decision': BarChart3,
  'system-management': Settings
}

// 子菜单图标
const MENU_ICONS: Record<string, any> = {
  'pending-product-catalog': ClipboardList,
  'hospital-product-catalog': PackageCheck,
  'product-catalog': PackageSearch,
  'supplier-manufacturer-management': Truck,
  'campus-management': Landmark,
  'department-management': Settings,
  'department-warehouse-catalog': ClipboardList,
  'warehouse-location-management': BoxIcon,
  'license-management': ShieldCheck,
  'quota-template-maintenance': Package2,
  'packing-task-confirmation': PackageCheck,
  'quota-label-unpack': PackageOpen,
  'quota-package-events': History,
  'packable-loose-snapshot': ShieldCheck,
  'print-template-settings': Settings,
  'quota-safety-stock': AlertTriangle,
  'purchase-management': ShoppingCart,
  'receiving-acceptance': ClipboardList,
  'inventory-management': BoxIcon,
  'inventory-events': History,
  'replenishment-task': FileText,
  'picking-delivery': Truck,
  'picking-records': History,
  'department-requisition': Settings,
  'department-consumption': PackageCheck,
  'stocktaking-management': ClipboardList,
  'udi-traceability': ScanLine,
  'high-value-consumables': PackageCheck,
  'cold-chain-monitoring': AlertTriangle,
  'recall-isolation': ShieldCheck,
  'pda-offline-record': ClipboardList,
  'settlement-reconciliation': Receipt,
  'batch-price-adjustment': ClipboardList,
  'red-flush-management': FileText,
  'reverse-consumption': PackageCheck,
  'invoice-management': Receipt,
  'operation-cockpit': BarChart3,
  'report-center': ClipboardList,
  'spd-his-reconciliation-report': ClipboardList,
  'supplier-delivery-ledger-report': Truck,
  'centralized-procurement-progress-report': BarChart3,
  'inventory-movement-summary-report': BoxIcon,
  'inventory-product-detail-report': ClipboardList,
  'user-management': Settings,
  'role-permission': ShieldCheck,
  'approval-flow-settings': ShieldCheck,
  'system-config': Settings,
  'field-option-management': Settings,
  'config-hit-explanation': ClipboardList,
  'audit-log': ClipboardList,
  'shortage-reminder': FileText
}

function iconFor(code: string): any {
  if (!code) return FileText
  // code 可能是完整路径（/features/some-code）或 feature code 本身
  const direct = MENU_ICONS[code]
  if (direct) return direct
  const match = code.match(/\/features\/([^/?#]+)/)
  if (match) return MENU_ICONS[match[1]] || FileText
  return FileText
}

// 把当前项目的 menuGroups 转换成 UI 模型
const uiMenuGroups = computed<MenuGroupUI[]>(() => {
  return menuGroups.map((group) => {
    const children: MenuUIChild[] = (group.children || []).map((child) => {
      if (child.children?.length) {
        return {
          name: child.title,
          icon: child.icon,
          children: child.children
            .map((sub) => ({
              path: featureRouteTarget(sub.code),
              name: sub.title
            }))
        }
      } else {
        return {
          path: featureRouteTarget(child.code),
          name: child.title,
          icon: child.icon
        }
      }
    })
    return {
      name: group.title,
      key: group.code,
      children
    }
  })
})

const visibleMenu = computed(() => {
  return uiMenuGroups.value
    .map((group) => ({
      ...group,
      children: group.children.filter((c) =>
        c.children ? c.children.length > 0 : !!c.path
      )
    }))
    .filter((group) => group.children.length > 0)
})

const activeModuleName = computed(() => {
  return visibleMenu.value.find((g) => g.key === activeModule.value)?.name || ''
})

const currentGroupChildren = computed(() => {
  const group = visibleMenu.value.find((g) => g.key === activeModule.value)
  return group?.children || []
})

function getGroupIcon(key: string) {
  return MODULE_ICONS[key] || Grid
}

function toggleSubmenu(key: string) {
  if (key === 'dashboard') {
    hoverSubmenu.value = false
    return
  }
  if (activeModule.value === key && hoverSubmenu.value) {
    hoverSubmenu.value = false
  } else {
    activeModule.value = key
    hoverSubmenu.value = true
  }
}

function handleDocumentClick(e: MouseEvent) {
  if (!hoverSubmenu.value) return
  const target = e.target as HTMLElement | null
  if (target && target.closest('.spd-topnav-menu')) return
  hoverSubmenu.value = false
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})

function toggleGroup(child: MenuUIChild) {
  if (expandedGroups.has(child.name)) {
    expandedGroups.delete(child.name)
  } else {
    expandedGroups.add(child.name)
  }
}

function isActive(path: string): boolean {
  return route.path === path
}

function handleSubmenuClick() {
  hoverSubmenu.value = false
}

function handleRefresh() {
  refreshing.value = true
  setTimeout(() => {
    refreshing.value = false
  }, 1000)
  window.location.reload()
}

function handleTabClick(key: string) {
  router.push(tabs.value.find(tab => tab.key === key)?.fullPath ?? key)
}

async function closeTab(key: string) {
  if (tabs.value.length <= 1) return
  const idx = tabs.value.findIndex((t) => t.key === key)
  const remaining = tabs.value.filter((t) => t.key !== key)
  if (key === activeTab.value) {
    const next = remaining[Math.min(idx, remaining.length - 1)]
    if (await router.push(next.fullPath)) return
  }
  tabs.value = tabs.value.filter((t) => t.key !== key)
}

async function handleTabAction(command: string) {
  const current = activeTab.value
  if (command === 'close-current') closeTab(current)
  else if (command === 'close-others')
    tabs.value = tabs.value.filter((t) => t.key === current)
  else if (command === 'close-all') {
    if (await router.push('/')) return
    tabs.value = [HOME_TAB]
  }
}

function handleUserCommand(command: string) {
  if (command === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

function titleForRoute(path: string): string {
  if (PATH_NAME[path]) return PATH_NAME[path]
  // 从 featureCatalog 反查中文标题，避免 Tab 显示英文 code
  const match = path.match(/^\/features\/([^/]+)/)
  if (match) {
    const code = match[1]
    const feature = findFeature(code)
    if (feature?.title) return feature.title
    return code
  }
  return path
}

watch(
  () => route.fullPath,
  () => {
    const p = typeof route.meta.tabPath === 'string' ? route.meta.tabPath : route.path
    activeTab.value = p
    const existing = tabs.value.find(tab => tab.key === p)
    if (existing) {
      existing.fullPath = route.fullPath
    } else {
      tabs.value.push({ key: p, label: titleForRoute(p), fullPath: route.fullPath })
    }

    // 自动激活模块
    if (p === '/') {
      activeModule.value = 'dashboard'
    } else {
      const matchedGroup = visibleMenu.value.find((g) =>
        g.children.some((c) =>
          c.path === p || (c.children && c.children.some((leaf) => leaf.path === p))
        )
      )
      if (matchedGroup) {
        activeModule.value = matchedGroup.key
        const parentGroup = matchedGroup.children.find(
          (c) => c.children && c.children.some((leaf) => leaf.path === p)
        )
        if (parentGroup) {
          expandedGroups.add(parentGroup.name)
        }
      }
    }
    hoverSubmenu.value = false
  },
  { immediate: true }
)
</script>

<template>
  <div class="spd-layout">
    <!-- 顶部水平导航栏 -->
    <header class="spd-topnav">
      <!-- Logo -->
      <RouterLink to="/" class="spd-topnav-logo">
        <div class="spd-logo-icon">
          <Box :size="20" />
        </div>
        <span class="spd-logo-name">SPD 院内供应链</span>
      </RouterLink>

      <!-- 主菜单 -->
      <nav class="spd-topnav-menu">
        <!-- 工作台（直接跳转） -->
        <div
          class="spd-menu-item"
          :class="{ active: activeModule === 'dashboard' }"
        >
          <RouterLink
            to="/"
            class="spd-menu-label"
            :class="{ active: activeModule === 'dashboard' }"
          >
            <Home :size="15" />
            工作台
          </RouterLink>
        </div>

        <!-- 业务模块 -->
        <div
          v-for="group in visibleMenu"
          :key="group.key"
          class="spd-menu-item"
          :class="{ active: activeModule === group.key }"
          @click.stop="toggleSubmenu(group.key)"
        >
          <span class="spd-menu-label">
            <component :is="getGroupIcon(group.key)" :size="15" />
            {{ group.name }}
            <ChevronDown :size="11" class="spd-menu-caret" />
          </span>

          <!-- 下拉子菜单 -->
          <Transition name="menu-dropdown">
            <div
              v-if="activeModule === group.key && hoverSubmenu"
              class="spd-menu-dropdown"
              @click.stop
            >
              <div class="spd-dropdown-inner">
                <template v-for="child in group.children" :key="child.name">
                  <!-- 有二级菜单的分组 -->
                  <div v-if="child.children" class="spd-dropdown-section">
                    <div
                      class="spd-dropdown-group-title"
                      @click="toggleGroup(child)"
                    >
                      <ChevronRight
                        :size="12"
                        :class="{ 'rotate-90': expandedGroups.has(child.name) }"
                      />
                      {{ child.name }}
                    </div>
                    <template v-if="expandedGroups.has(child.name)">
                      <RouterLink
                        v-for="sub in child.children"
                        :key="sub.path"
                        :to="sub.path || '/'"
                        class="spd-dropdown-link"
                        :class="{ active: isActive(sub.path || '/') }"
                        @click="handleSubmenuClick"
                      >
                        <span class="spd-dropdown-link-icon">
                          <component :is="iconFor(sub.path || '')" :size="14" />
                        </span>
                        <span class="spd-dropdown-link-text">
                          <span class="spd-dropdown-link-name">{{ sub.name }}</span>
                        </span>
                      </RouterLink>
                    </template>
                  </div>
                  <!-- 直接链接的叶子节点 -->
                  <div v-else class="spd-dropdown-section">
                    <RouterLink
                      v-if="child.path"
                      :to="child.path"
                      class="spd-dropdown-link"
                      :class="{ active: isActive(child.path) }"
                      @click="handleSubmenuClick"
                    >
                      <span class="spd-dropdown-link-icon">
                        <component :is="iconFor(child.name)" :size="14" />
                      </span>
                      <span class="spd-dropdown-link-text">
                        <span class="spd-dropdown-link-name">{{ child.name }}</span>
                      </span>
                    </RouterLink>
                  </div>
                </template>
              </div>
            </div>
          </Transition>
        </div>
      </nav>

      <!-- 右侧操作区 -->
      <div class="spd-topnav-actions">
        <button class="spd-icon-btn" title="刷新数据" @click="handleRefresh">
          <RefreshCw :size="16" :class="{ 'is-loading': refreshing }" />
        </button>

        <ThemeSwitcher />

        <button class="spd-icon-btn" title="消息通知">
          <Bell :size="16" />
        </button>

        <!-- 用户菜单 -->
        <el-dropdown
          trigger="click"
          @command="handleUserCommand"
          placement="bottom-end"
        >
          <div class="spd-user-btn">
            <div class="spd-user-avatar">
              {{ (auth.currentUser?.realName || auth.username || 'U').slice(0, 1) }}
            </div>
            <span class="spd-user-name">
              {{ auth.currentUser?.realName || auth.username }}
            </span>
            <ChevronDown :size="12" class="spd-user-caret" />
          </div>
          <template #dropdown>
            <div class="spd-user-dropdown">
              <div class="spd-user-profile">
                <div class="spd-user-profile-avatar">
                  {{ (auth.currentUser?.realName || auth.username || 'U').slice(0, 1) }}
                </div>
                <div>
                  <div class="spd-user-profile-name">
                    {{ auth.currentUser?.realName || auth.username }}
                  </div>
                  <div class="spd-user-profile-dept">当前账号</div>
                </div>
              </div>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <LogOut :size="14" /> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </div>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 页面主体 -->
    <div class="spd-body">
      <!-- 迷你侧边栏（仅在主内容区有可展开菜单时显示） -->
      <aside
        class="spd-mini-sidebar"
        v-if="currentGroupChildren.length > 0 && activeModule !== 'dashboard'"
      >
        <div class="spd-mini-sidebar-title">
          <component :is="getGroupIcon(activeModule)" :size="13" />
          {{ activeModuleName }}
        </div>
        <div class="spd-mini-nav">
          <template v-for="child in currentGroupChildren" :key="child.name">
            <!-- 二级分组：可展开/收起 -->
            <div v-if="child.children" class="spd-mini-nav-group">
              <div class="spd-mini-nav-group-title" @click="toggleGroup(child)">
                <ChevronRight
                  :size="11"
                  class="spd-mini-nav-group-caret"
                  :class="{ 'is-expanded': expandedGroups.has(child.name) }"
                />
                <span class="spd-mini-nav-group-text">{{ child.name }}</span>
                <span class="spd-mini-nav-group-count">{{ child.children.length }}</span>
              </div>
              <Transition name="spd-submenu">
                <div
                  v-show="expandedGroups.has(child.name)"
                  class="spd-mini-nav-sublist"
                >
                  <RouterLink
                    v-for="sub in child.children"
                    :key="sub.path"
                    :to="sub.path || '/'"
                    class="spd-mini-nav-sub"
                    :class="{ active: isActive(sub.path || '/') }"
                  >
                    <span class="spd-mini-nav-sub-dot"></span>
                    <span class="spd-mini-nav-sub-text">{{ sub.name }}</span>
                  </RouterLink>
                </div>
              </Transition>
            </div>

            <!-- 直接叶子节点（无分组） -->
            <RouterLink
              v-else-if="child.path"
              :to="child.path"
              class="spd-mini-nav-item"
              :class="{ active: isActive(child.path) }"
            >
              <component :is="iconFor(child.name)" :size="15" />
              <span>{{ child.name }}</span>
            </RouterLink>
          </template>
        </div>
      </aside>

      <!-- 主内容区 -->
      <div
        class="spd-main"
        :class="{ 'has-mini-sidebar': currentGroupChildren.length > 0 && activeModule !== 'dashboard' }"
      >
        <!-- Tab Bar -->
        <div class="spd-tabbar">
          <div class="spd-tabbar-tabs">
            <div
              v-for="(tab, index) in tabs"
              :key="tab.key"
              class="spd-tabbar-tab"
              :class="{ active: activeTab === tab.key }"
              @click="handleTabClick(tab.key)"
            >
              <Home v-if="index === 0" :size="13" />
              <span class="spd-tabbar-tab-label">{{ tab.label }}</span>
              <span
                v-if="tabs.length > 1"
                class="spd-tabbar-tab-close"
                @click.stop="closeTab(tab.key)"
              >
                <X :size="11" />
              </span>
            </div>
          </div>
          <el-dropdown @command="handleTabAction" trigger="click">
            <el-button text size="small">
              <MoreHorizontal :size="14" />
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="close-current">
                  <X :size="12" />关闭当前
                </el-dropdown-item>
                <el-dropdown-item command="close-others">
                  <Minus :size="12" />关闭其他
                </el-dropdown-item>
                <el-dropdown-item command="close-all">
                  <Trash2 :size="12" />关闭全部
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- Page Content -->
        <main class="spd-content">
          <div class="spd-content-inner">
            <RouterView v-slot="{ Component }">
              <keep-alive>
                <component :is="Component" />
              </keep-alive>
            </RouterView>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 局部补充：路由视图容器 */
.spd-content > * {
  width: 100%;
  min-width: 0;
}
</style>
