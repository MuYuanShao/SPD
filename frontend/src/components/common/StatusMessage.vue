<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
    tone?: 'info' | 'success' | 'warning' | 'error'
  }>(),
  {
    tone: 'info'
  }
)
</script>

<template>
  <p v-if="message" :class="['standard-status-message', `is-${tone}`]">
    {{ message }}
  </p>
</template>
