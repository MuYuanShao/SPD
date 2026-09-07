<script setup lang="ts">
import type { MenuItem } from '../config/menu'
import { featureRouteTarget } from '../config/featureCatalog'
defineProps<{ item: MenuItem }>()
</script>
<template>
  <el-sub-menu v-if="item.children?.length" :index="item.code">
    <template #title>{{ item.title }}</template>
    <MenuBranch v-for="child in item.children" :key="child.code" :item="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="featureRouteTarget(item.code)">{{ item.title }}</el-menu-item>
</template>
