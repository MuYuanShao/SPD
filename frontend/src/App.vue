<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import AppMain from './layout/AppMain.vue'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
const route = useRoute()
</script>
<template>
  <el-config-provider :locale="zhCn">
    <RouterView v-if="route.name === 'login'" />
    <AppMain v-else />
  </el-config-provider>
</template>
