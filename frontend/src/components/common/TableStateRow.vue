<script setup lang="ts">
import EmptyState from './EmptyState.vue'

withDefaults(defineProps<{
  colspan: number
  message: string
  state?: 'loading' | 'error' | 'empty'
}>(), {
  state: 'empty',
})
</script>

<template>
  <tr class="table-state-row" :data-state="state">
    <td :colspan="colspan">
      <EmptyState :message="message" />
    </td>
  </tr>
</template>

<style scoped>
.table-state-row td {
  height: 148px;
  padding: 24px;
  text-align: center;
}
</style>
