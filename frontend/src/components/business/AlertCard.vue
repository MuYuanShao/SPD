<script setup lang="ts">
defineProps<{ title: string; value: number; unit?: string; tone?: 'orange' | 'teal' | 'purple' | 'blue' }>()
</script>
<template>
  <div :class="['fli-alert', 'fli-alert-' + (tone || 'teal')]">
    <span>{{ title }}</span><strong>{{ value }}<small>{{ unit || '项' }}</small></strong>
  </div>
</template>
