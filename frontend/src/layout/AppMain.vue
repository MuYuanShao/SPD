<script setup lang="ts">
import { onMounted } from 'vue'
import TopBar from './TopBar.vue'
import SubMenu from './SubMenu.vue'
import AiMedicalAssistant from '../components/common/AiMedicalAssistant.vue'
import { useLayoutNavigation } from './useLayoutNavigation'
const { group, title, auth } = useLayoutNavigation()
onMounted(() => { void auth.init() })
</script>
<template>
  <el-container direction="vertical" class="fli-shell">
    <TopBar />
    <SubMenu />
    <el-main class="fli-main">
      <div class="fli-content" :class="{ 'fli-content-home': $route.name === 'dashboard' }">
        <el-breadcrumb separator="/" class="fli-breadcrumb">
          <el-breadcrumb-item :to="auth.canAccessMenu('dashboard') ? '/' : undefined">工作空间</el-breadcrumb-item>
          <el-breadcrumb-item v-if="group">{{ group.title }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <RouterView />
        <footer class="fli-footer">© {{ new Date().getFullYear() }} 院内 SPD · 医用耗材精益管理平台</footer>
      </div>
      <AiMedicalAssistant />
    </el-main>
  </el-container>
</template>
