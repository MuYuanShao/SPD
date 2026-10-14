<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <section class="md-form-section">
    <h4 v-if="title">{{ title }}</h4>
    <slot />
  </section>
</template>
