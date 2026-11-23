<script setup lang="ts">
import type { SystemNotice } from '../../api/systemNotices'
export type NoticeItem = SystemNotice
defineProps<{ items: SystemNotice[] }>()
const emit = defineEmits<{ select: [item: SystemNotice] }>()
</script>
<template>
  <el-empty v-if="!items.length" description="暂无公告" :image-size="48" />
  <el-timeline v-else class="fli-notices">
    <el-timeline-item v-for="item in items" :key="item.id" :timestamp="item.date" placement="bottom">
      <button type="button" @click="emit('select', item)">{{ item.title }}</button>
    </el-timeline-item>
  </el-timeline>
</template>

<style scoped>
/* 时间轴节点色跟随全站主色，避免写死与主题不一致的色值 */
.fli-notices :deep(.el-timeline-item__node) {
  background-color: var(--ulc-primary, var(--primary, #1f6f6d));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ulc-primary, var(--primary, #1f6f6d)) 22%, transparent);
}
</style>
