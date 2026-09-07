import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { menuGroups, flattenMenu, type MenuItem } from '../config/menu'
import { useAuthStore } from '../stores/auth'

/** Adapt the existing catalog without introducing a second permission model. */
export function useLayoutNavigation() {
  const auth = useAuthStore()
  const route = useRoute()
  function authorized(item: MenuItem): MenuItem | null {
    const children = item.children?.map(authorized).filter((child): child is MenuItem => child !== null)
    const allowed = item.code === 'supplier-manufacturer-management'
      ? auth.canAccessSupplierManufacturerManagement()
      : auth.canAccessMenu(item.code === 'picking-records' ? 'picking-delivery' : item.code)
    if (!allowed && !children?.length) return null
    return { ...item, children: children?.length ? children : undefined }
  }
  const groups = computed(() => menuGroups.map(authorized).filter((item): item is MenuItem => item !== null))
  const activeCode = computed(() => String(route.meta.featureCode || route.params.code || route.path.match(/^\/features\/([^/]+)/)?.[1] || 'dashboard'))
  const entries = computed(() => flattenMenu(groups.value))
  const active = computed(() => entries.value.find(item => item.code === activeCode.value))
  const group = computed(() => active.value?.parents[0] || active.value)
  const pages = computed(() => group.value ? flattenMenu([group.value]).filter(item => !item.children?.length) : [])
  const title = computed(() => String(route.meta.title || active.value?.title || (route.name === 'forbidden' ? '无访问权限' : '首页')))
  return { auth, groups, activeCode, active, group, pages, title }
}
