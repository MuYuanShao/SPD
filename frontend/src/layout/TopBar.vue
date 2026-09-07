<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ChevronDown } from '@lucide/vue'
import MenuBranch from './MenuBranch.vue'
import { useLayoutNavigation } from './useLayoutNavigation'
import { featureRouteTarget } from '../config/featureCatalog'
const { auth, groups, activeCode } = useLayoutNavigation()
const router = useRouter()
function logout() {
  auth.logout()
  void router.push({ name: 'login' })
}
</script>
<template>
  <el-header class="fli-topbar">
    <RouterLink to="/" class="fli-brand"><span class="fli-brand-symbol">SPD</span><span>医用耗材精益管理平台<small>HOSPITAL SUPPLY CHAIN</small></span></RouterLink>
    <el-menu class="fli-main-menu" mode="horizontal" router :default-active="activeCode === 'dashboard' ? '/' : featureRouteTarget(activeCode)" aria-label="主导航">
      <el-menu-item v-if="auth.canAccessMenu('dashboard')" index="/">首页</el-menu-item>
      <MenuBranch v-for="group in groups" :key="group.code" :item="group" />
    </el-menu>
    <el-dropdown @command="logout">
      <button class="fli-account" type="button" aria-label="用户菜单">
        <span class="fli-avatar">{{ (auth.currentUser?.realName || auth.username || '用').slice(0, 1) }}</span>
        <span>{{ auth.currentUser?.realName || auth.username }}<small>{{ auth.currentUser?.deptId ? '科室编号 ' + auth.currentUser.deptId : '当前账号' }}</small></span>
        <ChevronDown :size="14" />
      </button>
      <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
    </el-dropdown>
  </el-header>
</template>
