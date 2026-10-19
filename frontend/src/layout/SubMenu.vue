<script setup lang="ts">
import { useLayoutNavigation } from './useLayoutNavigation'
import { featureRouteTarget } from '../config/featureCatalog'
const { auth, pages, activeCode } = useLayoutNavigation()
</script>
<template>
  <nav class="fli-submenu" aria-label="页面入口">
    <!-- 工作台入口组 -->
    <div class="fli-tab-group">
      <RouterLink
        v-if="auth.canAccessMenu('dashboard')"
        to="/"
        class="fli-tab fli-tab-home"
        :class="{ selected: $route.name === 'dashboard' }">
        首页
      </RouterLink>
      <RouterLink
        v-if="auth.canAccessMenu('dashboard')"
        to="/dashboard/realtime"
        class="fli-tab fli-tab-realtime"
        :class="{ selected: $route.name === 'dashboard-realtime' }">
        实时工作台
      </RouterLink>
    </div>
    <!-- 业务功能入口 -->
    <RouterLink
      v-for="page in pages"
      :key="page.code"
      :to="featureRouteTarget(page.code)"
      :class="{ selected: activeCode === page.code }">
      {{ page.title }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.fli-tab-group {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  margin-right: 6px;
  padding-right: 6px;
  border-right: 1px solid var(--fli-line, #e5e7eb);
}

.fli-tab {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-size: 12.5px;
  font-weight: 500;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  border-radius: 5px 5px 0 0;
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
  cursor: pointer;
}

/* 首页：蓝色系 */
.fli-tab-home {
  color: #64748b;
  background: transparent;
}
.fli-tab-home:hover {
  color: #2563eb;
  background: #eff6ff;
}
.fli-tab-home.selected {
  color: #2563eb;
  font-weight: 600;
  background: #eff6ff;
  border-bottom-color: #2563eb;
}

/* 实时工作台：橙色系 */
.fli-tab-realtime {
  color: #64748b;
  background: transparent;
}
.fli-tab-realtime:hover {
  color: #ea580c;
  background: #fff7ed;
}
.fli-tab-realtime.selected {
  color: #ea580c;
  font-weight: 600;
  background: #fff7ed;
  border-bottom-color: #ea580c;
}
</style>
