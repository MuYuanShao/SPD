<script setup lang="ts">
/**
 * DashboardView — 现代化工作台首页
 *
 * 仅保留业务模块快捷入口（按大模块分组，渐变色头 + 子功能网格）。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Database,
  FileText,
  Search,
  LayoutDashboard
} from '@lucide/vue'
import { useLayoutNavigation } from '../layout/useLayoutNavigation'
import { featureRouteTarget } from '../config/featureCatalog'

const router = useRouter()
const { groups } = useLayoutNavigation()

const quickSearch = ref('')

// 颜色盘（业务模块快捷入口）
const COLOR_PALETTE = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
  'linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 100%)'
]

// 图标映射
const ICON_MAP: Record<string, any> = {
  Database,
  FileText,
  Search,
  LayoutDashboard
}

function colorClass(index: number): string {
  return COLOR_PALETTE[index % COLOR_PALETTE.length]
}

// 从 menuGroups 提取业务大模块与子功能
interface QuickGroupUI {
  key: string
  label: string
  desc: string
  icon: any
  color: string
  children: { path: string; label: string; icon: any; color: string }[]
}

const quickGroups = computed<QuickGroupUI[]>(() => {
  const groupDescs: Record<string, string> = {
    'master-data-quota': '主数据·定数包',
    'supply-chain': '采购·库存·配送',
    'special-compliance': 'UDI·冷链·召回',
    'settlement-finance': '对账·红冲·发票',
    'operations-decision': '运营驾驶舱·报表',
    'system-management': '用户·权限·配置'
  }

  return groups.value.map((group, gi) => {
    const children: { path: string; label: string; icon: any; color: string }[] = []
    let childIndex = 0
    for (const child of group.children || []) {
      if (child.children?.length) {
        for (const sub of child.children) {
          children.push({
            path: featureRouteTarget(sub.code),
            label: sub.title,
            icon: sub.icon || ICON_MAP[sub.code] || FileText,
            color: colorClass(childIndex)
          })
          childIndex++
        }
      } else {
        children.push({
          path: featureRouteTarget(child.code),
          label: child.title,
          icon: child.icon || ICON_MAP[child.code] || FileText,
          color: colorClass(childIndex)
        })
        childIndex++
      }
    }
    return {
      key: group.code,
      label: group.title,
      desc: groupDescs[group.code] || '',
      icon: group.icon || ICON_MAP[group.code] || Database,
      color: colorClass(gi),
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

function goTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="spd-dashboard">
    <!-- 第一行：业务模块快捷入口 -->
    <section class="spd-modules-section">
      <div class="spd-modules-header">
        <div class="spd-modules-title">
          <LayoutDashboard :size="20" />
          <h2 class="spd-modules-name">业务模块快捷入口</h2>
          <span class="spd-modules-hint">点击直达各功能模块</span>
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
          <div class="spd-module-header" :style="{ background: group.color }">
            <div class="spd-module-icon">
              <component :is="group.icon" :size="22" />
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
              <div class="spd-child-icon" :style="{ background: child.color }">
                <component :is="child.icon" :size="14" />
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
