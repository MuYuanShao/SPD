import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const globalLoading = ref(false)
  const loadingText = ref('加载中...')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setSidebarCollapsed(collapsed: boolean) {
    sidebarCollapsed.value = collapsed
  }

  function showLoading(text?: string) {
    if (text) loadingText.value = text
    globalLoading.value = true
  }

  function hideLoading() {
    globalLoading.value = false
    loadingText.value = '加载中...'
  }

  return {
    sidebarCollapsed,
    globalLoading,
    loadingText,
    toggleSidebar,
    setSidebarCollapsed,
    showLoading,
    hideLoading,
  }
})
