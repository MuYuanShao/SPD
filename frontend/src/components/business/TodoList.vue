<script setup lang="ts">
export interface TodoItem { id: string; title: string; status: string; progress: number }
defineProps<{ items: TodoItem[] }>()
const emit = defineEmits<{ select: [item: TodoItem] }>()
</script>
<template>
  <div class="fli-todos">
    <el-empty v-if="!items.length" description="暂无待办事项" :image-size="48" />
    <button v-for="item in items" :key="item.id" type="button" class="fli-todo" @click="emit('select', item)">
      <span class="fli-todo-title">{{ item.title }}<el-tag size="small" effect="plain">{{ item.status }}</el-tag></span>
      <span class="fli-todo-id">{{ item.id }}</span>
      <el-progress :percentage="item.progress" :stroke-width="4" :show-text="false" :aria-label="'进度 ' + item.progress + '%'" />
    </button>
  </div>
</template>
