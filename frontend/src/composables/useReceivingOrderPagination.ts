import { ref } from 'vue'

export function useReceivingOrderPagination(options: {
  reload: () => Promise<void>
}) {
  const currentPage = ref(1)
  const pageSize = ref(20)
  const totalItems = ref(0)

  async function changePage(page: number) {
    const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
    const nextPage = Math.min(Math.max(page, 1), totalPages)
    if (nextPage === currentPage.value) return
    currentPage.value = nextPage
    await options.reload()
  }

  async function changePageSize(size: number) {
    if (size === pageSize.value) return
    pageSize.value = size
    currentPage.value = 1
    await options.reload()
  }

  function searchOrders() {
    currentPage.value = 1
    void options.reload()
  }

  return {
    currentPage,
    pageSize,
    totalItems,
    changePage,
    changePageSize,
    searchOrders
  }
}
