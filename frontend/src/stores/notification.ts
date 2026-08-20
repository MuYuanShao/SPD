// 全局通知 Store——管理消息通知队列
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface Notification {
  id: string
  type: 'success' | 'warning' | 'error' | 'info'
  title: string
  message: string
  timestamp: Date
  read: boolean
}

export const useNotificationStore = defineStore('notification', () => {
  // ===== State =====
  const notifications = ref<Notification[]>([])

  // ===== Getters =====
  const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)
  const recentNotifications = computed(() => notifications.value.slice(0, 10))

  // ===== Actions =====
  function addNotification(n: Omit<Notification, 'id' | 'timestamp' | 'read'>) {
    notifications.value.unshift({
      ...n,
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      timestamp: new Date(),
      read: false,
    })
  }

  function markAsRead(id: string) {
    const n = notifications.value.find((n) => n.id === id)
    if (n) n.read = true
  }

  function markAllAsRead() {
    notifications.value.forEach((n) => { n.read = true })
  }

  function clearNotifications() {
    notifications.value = []
  }

  return {
    notifications,
    unreadCount,
    recentNotifications,
    addNotification,
    markAsRead,
    markAllAsRead,
    clearNotifications,
  }
})
