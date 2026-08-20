<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title?: string
  description?: string
}>()
</script>

<template>
  <header class="standard-page-header">
    <div class="standard-page-heading">
      <p v-if="eyebrow">{{ eyebrow }}</p>
      <h2 v-if="title">{{ title }}</h2>
      <span v-if="description">{{ description }}</span>
      <slot />
    </div>
    <div v-if="$slots.actions" class="standard-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
