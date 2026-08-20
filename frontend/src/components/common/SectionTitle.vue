<script setup lang="ts">
defineProps<{
  title?: string
  level?: 2 | 3 | 4
}>()
</script>

<template>
  <div class="section-title">
    <slot name="icon" />
    <component :is="`h${level ?? 2}`" v-if="title">{{ title }}</component>
    <slot />
  </div>
</template>
