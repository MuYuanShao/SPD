<script setup lang="ts">
export interface NoticeItem { id: string; title: string; date: string; content: string }
defineProps<{ items: NoticeItem[] }>()
const emit = defineEmits<{ select: [item: NoticeItem] }>()
</script>
<template>
  <el-empty v-if="!items.length" description="暂无公告" :image-size="48" />
  <el-timeline v-else class="fli-notices">
    <el-timeline-item v-for="item in items" :key="item.id" :timestamp="item.date" placement="bottom" color="#009688">
      <button type="button" @click="emit('select', item)">{{ item.title }}</button>
    </el-timeline-item>
  </el-timeline>
</template>
