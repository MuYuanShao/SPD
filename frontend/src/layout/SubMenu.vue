<script setup lang="ts">
import { useLayoutNavigation } from './useLayoutNavigation'
import { featureRouteTarget } from '../config/featureCatalog'
const { auth, pages, activeCode } = useLayoutNavigation()
</script>
<template>
  <nav class="fli-submenu" aria-label="页面入口">
    <RouterLink v-if="auth.canAccessMenu('dashboard')" to="/" :class="{ selected: $route.name === 'dashboard' }">首页</RouterLink>
    <RouterLink v-if="auth.canAccessMenu('dashboard')" to="/dashboard/realtime" :class="{ selected: $route.name === 'dashboard-realtime' }">实时工作台</RouterLink>
    <RouterLink v-for="page in pages" :key="page.code" :to="featureRouteTarget(page.code)" :class="{ selected: activeCode === page.code }">{{ page.title }}</RouterLink>
  </nav>
</template>
