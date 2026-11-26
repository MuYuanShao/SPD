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
  border-right: 1px solid var(--fli-line, var(--spd-p-border));
}

.fli-tab {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  border-radius: 5px 5px 0 0;
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
  cursor: pointer;
}

/* 首页：蓝色系 */
.fli-tab-home {
  color: var(--spd-p-text-secondary);
  background: transparent;
}
.fli-tab-home:hover {
  color: var(--spd-p-info);
  background: var(--spd-p-info-bg);
}
.fli-tab-home.selected {
  color: var(--spd-p-info);
  font-weight: 600;
  background: var(--spd-p-info-bg);
  border-bottom-color: var(--spd-p-info);
}

/* 实时工作台：橙色系 */
.fli-tab-realtime {
  color: var(--spd-p-text-secondary);
  background: transparent;
}
.fli-tab-realtime:hover {
  color: var(--spd-p-warning-strong);
  background: var(--spd-p-warning-bg);
}
.fli-tab-realtime.selected {
  color: var(--spd-p-warning-strong);
  font-weight: 600;
  background: var(--spd-p-warning-bg);
  border-bottom-color: var(--spd-p-warning-strong);
}
</style>
