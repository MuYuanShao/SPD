<script setup lang="ts">
import type { Component } from 'vue'
defineProps<{ title: string; value: string | number; unit?: string; tone: 'orange' | 'teal' | 'purple' | 'blue'; icon: Component }>()
</script>
<template>
  <el-card shadow="never" :class="['fli-statistic', 'fli-tone-' + tone]">
    <span class="fli-statistic-icon"><component :is="icon" :size="30" :stroke-width="1.6" /></span>
    <div><div class="fli-statistic-value">{{ value }}<small>{{ unit }}</small></div><div class="fli-statistic-title">{{ title }}</div></div>
  </el-card>
</template>
