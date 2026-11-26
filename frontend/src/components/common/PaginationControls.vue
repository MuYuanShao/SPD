<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    size: number
    total: number
    loading?: boolean
    maxPageSize?: number
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
  const size = Math.min(nextSize, props.maxPageSize ?? nextSize)
  sizeInput.value = String(size)
  if (size !== props.size) {
    emit('change-size', size)
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
      <form class="hospital-pagination-size" novalidate @submit.prevent="submitSize">
        每页
        <input v-model="sizeInput" type="number" aria-label="每页条数" min="1" :max="maxPageSize" :disabled="loading" @blur="submitSize" />
        条
      </form>
      <SpdButton label="上一页" :disabled="page <= 1 || loading" @click="changePage(page - 1)"></SpdButton>
      <SpdButton tone="primary" size="small" v-for="visiblePage in visiblePages"
        :key="visiblePage"
       
        :class="{ 'btn-primary': visiblePage === page }"
       
        :disabled="loading"
        @click="changePage(visiblePage)">
          {{ visiblePage }}
        </SpdButton>
      <SpdButton label="下一页" :disabled="page >= totalPages || loading" @click="changePage(page + 1)"></SpdButton>
      <form class="hospital-page-jump" @submit.prevent="submitJump">
        <span>跳至</span>
        <input v-model="jumpInput" type="number" min="1" :max="totalPages" :disabled="loading" />
        <span>页</span>
        <SpdButton label="跳转" tone="primary" size="small" type="submit" :disabled="loading"></SpdButton>
      </form>
    </div>
  </footer>
</template>
