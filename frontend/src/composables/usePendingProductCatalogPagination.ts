import { computed, ref, type Ref } from 'vue'

export function usePendingProductCatalogPagination(options: {
  totalItems: Ref<number>
  reload: () => void | Promise<void>
}) {
  const currentPage = ref(1)
  const pageSize = ref(25)

  const totalPages = computed(() => Math.max(Math.ceil(options.totalItems.value / pageSize.value), 1))

  const visiblePages = computed(() => {
    const total = totalPages.value
    const current = currentPage.value
    const maxVisible = 5
    if (total <= maxVisible) {
      return Array.from({ length: total }, (_, i) => i + 1)
    }
    const half = Math.floor(maxVisible / 2)
    let start = current - half
    let end = current + half
    if (start < 1) {
      start = 1
      end = maxVisible
    }
    if (end > total) {
      end = total
      start = total - maxVisible + 1
    }
    return Array.from({ length: end - start + 1 }, (_, i) => start + i)
  })

  function changePage(page: number) {
    const nextPage = Math.min(Math.max(page, 1), totalPages.value)
    if (nextPage !== currentPage.value) {
      currentPage.value = nextPage
      void options.reload()
    }
  }

  function changePageSize(size: number) {
    if (size === pageSize.value) return
    pageSize.value = size
    currentPage.value = 1
    void options.reload()
  }

  function resetPage() {
    currentPage.value = 1
  }

  return {
    currentPage,
    pageSize,
    totalPages,
    visiblePages,
    changePage,
    changePageSize,
    resetPage
  }
}
