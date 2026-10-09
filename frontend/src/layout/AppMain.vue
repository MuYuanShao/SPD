<script setup lang="ts">
/**
 * AppMain — 整合后的主应用外壳
 *
 * 整合自 SPD 模板项目的设计语言：
 * - SpdTopNav 水平顶部导航
 * - 主内容区由 SpdTopNav 内嵌的 router-view 渲染
 *
 * AI 助手为可选附加功能，单独按需引入
 */
import { defineAsyncComponent } from 'vue'
import SpdTopNav from './SpdTopNav.vue'

// 可选：保留旧版的 AI 助手（异步加载）
const AiMedicalAssistant = defineAsyncComponent(
  () => import('../components/common/AiMedicalAssistant.vue')
)
</script>

<template>
  <div class="fli-shell">
    <SpdTopNav />
    <AiMedicalAssistant />
  </div>
</template>
