<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
    align?: 'left' | 'center'
  }>(),
  {
    align: 'center'
  }
)
</script>

<template>
  <p :class="['standard-empty-state', `is-${align}`]">
    <slot>{{ message }}</slot>
  </p>
</template>
