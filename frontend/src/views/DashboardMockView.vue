<script setup lang="ts">
/**
 * DashboardView — 现代化工作台首页
 *
 * - 顶部 hero header：欢迎语 + 副标题 + 关键数据指标
 * - 中部业务模块区：标题区独立白底卡片 + 业务模块卡片网格（主色渐变 + 子菜单）
 * - 整体 24px 边距，圆角阴影分明，引入新字体（display 字体）
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Database,
  FileText,
  Search,
  LayoutDashboard,
  Sparkles,
  Layers,
  Activity
} from '@lucide/vue'
import { useLayoutNavigation } from '../layout/useLayoutNavigation'
import { useAuthStore } from '../stores/auth'
import { featureRouteTarget } from '../config/featureCatalog'

const router = useRouter()
const auth = useAuthStore()
const { groups } = useLayoutNavigation()

const quickSearch = ref('')

// 图标映射
const ICON_MAP: Record<string, any> = {
  Database,
  FileText,
  Search,
  LayoutDashboard
}

// 从 menuGroups 提取业务大模块与子功能
interface QuickChild {
  path: string
  label: string
  icon: any
}

interface QuickGroupUI {
  key: string
  label: string
  desc: string
  icon: any
  children: QuickChild[]
}

const groupDescs: Record<string, string> = {
  'master-data-quota': '主数据 · 定数包',
  'supply-chain': '采购 · 库存 · 配送',
  'special-compliance': 'UDI · 冷链 · 召回',
  'settlement-finance': '对账 · 红冲 · 发票',
  'operations-decision': '运营驾驶舱 · 报表',
  'system-management': '用户 · 权限 · 配置'
}

const quickGroups = computed<QuickGroupUI[]>(() => {
  return groups.value.map((group) => {
    const children: QuickChild[] = []
    for (const child of group.children || []) {
      if (child.children?.length) {
        for (const sub of child.children) {
          children.push({
            path: featureRouteTarget(sub.code),
            label: sub.title,
            icon: sub.icon || ICON_MAP[sub.code] || FileText
          })
        }
      } else {
        children.push({
          path: featureRouteTarget(child.code),
          label: child.title,
          icon: child.icon || ICON_MAP[child.code] || FileText
        })
      }
    }
    return {
      key: group.code,
      label: group.title,
      desc: groupDescs[group.code] || '',
      icon: group.icon || ICON_MAP[group.code] || Database,
      children
    }
  })
})

// 搜索过滤
const filteredQuickGroups = computed<QuickGroupUI[]>(() => {
  const kw = quickSearch.value.trim().toLowerCase()
  if (!kw) return quickGroups.value
  return quickGroups.value
    .map((g) => {
      const groupHit = g.label.toLowerCase().includes(kw) || g.desc.toLowerCase().includes(kw)
      const childHits = g.children.filter(
        (c) => c.label.toLowerCase().includes(kw) || c.path.toLowerCase().includes(kw)
      )
      if (groupHit) return g
      if (childHits.length > 0) return { ...g, children: childHits }
      return null
    })
    .filter((g): g is QuickGroupUI => g !== null)
})

// 汇总指标
const totalModules = computed(() => quickGroups.value.length)
const totalFeatures = computed(() => quickGroups.value.reduce((sum, g) => sum + g.children.length, 0))

function goTo(path: string) {
  router.push(path)
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

const today = computed(() => {
  const d = new Date()
  const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${d.getFullYear()} 年 ${String(d.getMonth() + 1).padStart(2, '0')} 月 ${String(
    d.getDate()
  ).padStart(2, '0')} 日 · ${weekDays[d.getDay()]}`
})
</script>

<template>
  <div class="spd-dashboard">
    <!-- Hero Header：欢迎区 -->
    <header class="spd-dashboard-hero">
      <div class="spd-hero-content">
        <span class="spd-hero-eyebrow">
          <Sparkles :size="13" />
          SPD 医用耗材精益管理平台
        </span>
        <h1 class="spd-hero-title">
          {{ greeting() }}，{{ auth.currentUser?.realName || auth.username || '同事' }}
        </h1>
        <p class="spd-hero-subtitle">
          {{ today }} · 当前账号已开通
          {{ totalModules }} 个业务模块、{{ totalFeatures }} 项功能
        </p>
      </div>
      <div class="spd-hero-cta">
        <div class="spd-hero-stat">
          <span class="spd-hero-stat-label">业务模块</span>
          <span class="spd-hero-stat-value">{{ totalModules }}</span>
        </div>
        <div class="spd-hero-stat">
          <span class="spd-hero-stat-label">功能项</span>
          <span class="spd-hero-stat-value">{{ totalFeatures }}</span>
        </div>
      </div>
    </header>

    <!-- 业务模块区 -->
    <section class="spd-modules-section">
      <div class="spd-modules-header">
        <div class="spd-modules-title">
          <span class="spd-modules-title-icon">
            <LayoutDashboard :size="16" />
          </span>
          <div class="spd-modules-title-text">
            <h2 class="spd-modules-name">业务模块快捷入口</h2>
            <span class="spd-modules-hint">点击直达各功能模块</span>
          </div>
        </div>
        <el-input
          v-model="quickSearch"
          placeholder="搜索模块或功能"
          clearable
          size="default"
          class="spd-modules-search"
        >
          <template #prefix>
            <Search :size="14" />
          </template>
        </el-input>
      </div>

      <div class="spd-modules-grid">
        <div
          v-for="group in filteredQuickGroups"
          :key="group.key"
          class="spd-module-card"
        >
          <div class="spd-module-header">
            <div class="spd-module-icon">
              <component :is="group.icon" :size="20" />
            </div>
            <div class="spd-module-title">
              <div class="spd-module-name">{{ group.label }}</div>
              <div class="spd-module-desc">{{ group.desc }}</div>
            </div>
            <div class="spd-module-count">{{ group.children.length }}</div>
          </div>
          <div class="spd-module-body">
            <div
              v-for="child in group.children"
              :key="child.path"
              class="spd-module-child"
              @click="goTo(child.path)"
              :title="child.label"
            >
              <div class="spd-child-icon">
                <component :is="child.icon" :size="13" />
              </div>
              <div class="spd-child-label">{{ child.label }}</div>
            </div>
          </div>
        </div>
        <div v-if="filteredQuickGroups.length === 0" class="spd-modules-empty">
          <el-empty description="未找到匹配的模块" :image-size="80" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.is-loading {
  animation: spd-rotating 1s linear infinite;
}

@keyframes spd-rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
