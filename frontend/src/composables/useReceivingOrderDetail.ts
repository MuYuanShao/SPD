import { ref } from 'vue'
import {
  fetchReceivingOrderDetail,
  type ReceivingOrderDetail,
  type ReceivingOrderRow
} from '../api/receivingOrders'

export function useReceivingOrderDetail() {
  const detail = ref<ReceivingOrderDetail | null>(null)
  const detailLoading = ref(false)
  const detailPage = ref(1)
  const detailSize = ref(20)
  const detailQuery = ref('')

  async function fetchDetailPage(receivingNo: string, page = detailPage.value) {
    detailLoading.value = true
    try {
      detail.value = await fetchReceivingOrderDetail(receivingNo, {
        page,
        size: detailSize.value,
        keyword: detailQuery.value.trim()
      })
      detailPage.value = detail.value.page
    } finally {
      detailLoading.value = false
    }
  }

  async function openDetail(row: ReceivingOrderRow) {
    detailPage.value = 1
    detailQuery.value = ''
    await fetchDetailPage(row.receivingNo, 1)
  }

  async function searchDetailItems() {
    if (!detail.value || detailLoading.value) return
    detailPage.value = 1
    await fetchDetailPage(detail.value.order.receivingNo, 1)
  }

  async function changeDetailPage(page: number) {
    if (!detail.value) return
    const totalPages = Math.max(Math.ceil(detail.value.total / detailSize.value), 1)
    const nextPage = Math.min(Math.max(page, 1), totalPages)
    await fetchDetailPage(detail.value.order.receivingNo, nextPage)
  }

  async function changeDetailPageSize(size: number) {
    if (!detail.value || size === detailSize.value) return
    detailSize.value = size
    detailPage.value = 1
    await fetchDetailPage(detail.value.order.receivingNo, 1)
  }

  return {
    detail,
    detailLoading,
    detailPage,
    detailSize,
    detailQuery,
    fetchDetailPage,
    openDetail,
    searchDetailItems,
    changeDetailPage,
    changeDetailPageSize
  }
}
