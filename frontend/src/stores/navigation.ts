// 导航历史 Store——跟踪用户最近访问的功能页面
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface NavHistoryItem {
  path: string
  title: string
  timestamp: number
}

const MAX_HISTORY = 10
const STORAGE_KEY = 'spd.navHistory'

export const useNavigationStore = defineStore('navigation', () => {
  // ===== State =====
  const history = ref<NavHistoryItem[]>(loadHistory())

  // ===== Actions =====
  function push(path: string, title: string) {
    // 移除已有记录（防重复）
    history.value = history.value.filter((h) => h.path !== path)
    // 添加到头部
    history.value.unshift({ path, title, timestamp: Date.now() })
    // 限制数量
    if (history.value.length > MAX_HISTORY) {
      history.value = history.value.slice(0, MAX_HISTORY)
    }
    saveHistory()
  }

  function remove(path: string) {
    history.value = history.value.filter((h) => h.path !== path)
    saveHistory()
  }

  function clear() {
    history.value = []
    saveHistory()
  }

  // ===== Private =====
  function loadHistory(): NavHistoryItem[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      return raw ? JSON.parse(raw) : []
    } catch {
      return []
    }
  }

  function saveHistory() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(history.value))
    } catch { /* ignore */ }
  }

  return {
    history,
    push,
    remove,
    clear,
  }
})
