<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    size: number
    total: number
    loading?: boolean
    pageSizeOptions?: number[]
  }>(),
  {
    loading: false,
    pageSizeOptions: () => [10, 20, 50, 100, 200]
  }
)

const emit = defineEmits<{
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
}>()

const jumpInput = ref(String(props.page))
const sizeInput = ref(String(props.size))
const totalPages = computed(() => Math.max(Math.ceil(props.total / Math.max(props.size, 1)), 1))
const startItem = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.size + 1))
const endItem = computed(() => Math.min(props.page * props.size, props.total))
const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, props.page - 2)
  const end = Math.min(totalPages.value, start + 4)
  for (let page = Math.max(1, end - 4); page <= end; page += 1) {
    pages.push(page)
  }
  return pages
})

watch(
  () => props.page,
  (page) => {
    jumpInput.value = String(page)
  }
)

watch(
  () => props.size,
  (size) => {
    sizeInput.value = String(size)
  }
)

function changePage(page: number) {
  const nextPage = Math.min(Math.max(page, 1), totalPages.value)
  if (nextPage !== props.page) {
    emit('change-page', nextPage)
  }
}

function submitSize() {
  const nextSize = Math.floor(Number(sizeInput.value))
  if (!Number.isFinite(nextSize) || nextSize <= 0) {
    sizeInput.value = String(props.size)
    return
  }
  if (nextSize !== props.size) {
    emit('change-size', nextSize)
  }
}

function submitJump() {
  const nextPage = Number(jumpInput.value)
  if (!Number.isFinite(nextPage)) {
    jumpInput.value = String(props.page)
    return
  }
  changePage(nextPage)
}
</script>

<template>
  <footer v-if="total > 0" class="hospital-pagination">
    <span class="hospital-pagination-summary">
      显示 {{ startItem }}-{{ endItem }} 条，共 {{ total }} 条
    </span>
    <div class="hospital-pagination-controls">
      <form class="hospital-pagination-size" @submit.prevent="submitSize">
        每页
        <input v-model="sizeInput" type="number" min="1" :disabled="loading" @blur="submitSize" />
        条
      </form>
      <button class="btn btn-sm" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">
        上一页
      </button>
      <button
        v-for="visiblePage in visiblePages"
        :key="visiblePage"
        class="btn btn-sm"
        :class="{ 'btn-primary': visiblePage === page }"
        type="button"
        :disabled="loading"
        @click="changePage(visiblePage)"
      >
        {{ visiblePage }}
      </button>
      <button class="btn btn-sm" type="button" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">
        下一页
      </button>
      <form class="hospital-page-jump" @submit.prevent="submitJump">
        <span>跳至</span>
        <input v-model="jumpInput" type="number" min="1" :max="totalPages" :disabled="loading" />
        <span>页</span>
        <button class="btn btn-sm" type="submit" :disabled="loading">跳转</button>
      </form>
    </div>
  </footer>
</template>
