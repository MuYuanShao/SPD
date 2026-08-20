<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'

defineProps<{
  title: string
  subtitle: string
  stats: Array<{
    label: string
    value: string | number
  }>
}>()

defineEmits<{
  (event: 'refresh'): void
}>()
</script>

<template>
  <div class="breadcrumb-line">
    <span>供应链业务</span>
    <strong>{{ title }}</strong>
  </div>

  <div class="detail-heading">
    <div>
      <p>定数包、打包、解包闭环</p>
      <h2>{{ title }}</h2>
      <small>{{ subtitle }}</small>
    </div>
    <button class="btn" type="button" @click="$emit('refresh')">
      <RefreshCw :size="17" />
      刷新
    </button>
  </div>

  <div class="foundation-stat-grid quota-stat-grid">
    <article v-for="item in stats" :key="item.label">
      <span>{{ item.label }}</span>
      <strong>{{ item.value }}</strong>
    </article>
  </div>
</template>
