import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { isAxiosError } from 'axios'
import { getData, postData } from '../api/http'
import { getSessionAdapter } from '../api/session'

const USER_KEY = 'spd.user'

export interface UserInfo {
  userId: number
  username: string
  realName?: string
  phone?: string
  email?: string
  deptId?: number
  status: number
  roles: string[]
  permissionCodes: string[]
  menuCodes: string[]
  dataScope: number
}

export const useAuthStore = defineStore('auth', () => {
  const session = getSessionAdapter()
  const token = ref<string | null>(session.getToken())
  const currentUser = ref<UserInfo | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value && currentUser.value))
  const username = computed(() => currentUser.value?.username || '')
  const initializationError = ref('')
  let initialization: Promise<void> | undefined
  let sessionVersion = 0

  async function login(loginUsername: string, password: string) {
    if (!loginUsername.trim() || !password.trim()) {
      throw new Error('请输入账号和密码')
    }

    const data = await postData<{
      token: string
      tokenType: string
      userId: number
      username: string
    }>('/auth/login', {
      username: loginUsername.trim(),
      password,
    })

    if (!data) {
      throw new Error('登录失败')
    }

    ++sessionVersion
    currentUser.value = null
    initializationError.value = ''
    token.value = data.token
    session.setToken(data.token)

    await fetchCurrentUser()
  }

  async function fetchCurrentUser() {
    const version = sessionVersion
    try {
      const user = await getData<UserInfo>('/auth/me')
      if (version !== sessionVersion) throw new Error('登录状态已变更，请重新登录')
      if (!user) throw new Error('未获取到用户信息，请重试')
      currentUser.value = user
      initializationError.value = ''
      saveUser(user)
    } catch (error) {
      if (version !== sessionVersion) {
        if (isAxiosError(error) && error.response?.status === 401) throw new Error('登录已失效，请重新登录')
        throw error
      }
      currentUser.value = null
      const unauthorized = isAxiosError(error) && error.response?.status === 401
      if (unauthorized) logout()
      initializationError.value = unauthorized ? '登录已失效，请重新登录' : '用户信息加载失败，请重试登录'
      throw new Error(initializationError.value)
    }
  }

  function logout() {
    ++sessionVersion
    initialization = undefined
    token.value = null
    currentUser.value = null
    initializationError.value = ''
    session.clearToken()
    try { localStorage.removeItem(USER_KEY) } catch { /* Storage may be unavailable. */ }
  }

  function init(): Promise<void> {
    if (isAuthenticated.value) return Promise.resolve()
    if (initialization) return initialization
    const savedToken = session.getToken()
    if (!savedToken) return Promise.resolve()
    token.value = savedToken
    const pending = fetchCurrentUser().catch(() => {
      // The route guard opens login; retain the actionable error for that page.
    }).finally(() => {
      if (initialization === pending) initialization = undefined
    })
    initialization = pending
    return pending
  }

  function saveUser(user: UserInfo) {
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {
      // ignore persistence failures
    }
  }

  function hasPermission(permissionCode: string) {
    return isAdministrator() || Boolean(currentUser.value?.permissionCodes?.includes(permissionCode))
  }

  function canAccessMenu(menuCode: string) {
    return (
      isAdministrator() ||
      Boolean(currentUser.value?.menuCodes?.includes('*')) ||
      Boolean(currentUser.value?.menuCodes?.includes(menuCode))
    )
  }

  /**
   * 供应商厂家管理合并页的菜单可见性：拥有合并码或原供应商/厂家任一权限即可访问。
   */
  function canAccessSupplierManufacturerManagement() {
    if (isAdministrator()) return true
    const codes = currentUser.value?.menuCodes ?? []
    if (codes.includes('*')) return true
    return ['supplier-manufacturer-management', 'supplier-management', 'manufacturer-management'].some(
      (code) => codes.includes(code)
    )
  }

  function canWrite(featureCode: string) {
    return hasPermission(`${featureCode}:write`)
  }

  function hasRole(role: string) {
    return Boolean(currentUser.value?.roles?.includes(role))
  }

  function isAdministrator() {
    return (
      Boolean(currentUser.value?.roles?.includes('ROLE_ADMIN')) ||
      Boolean(currentUser.value?.permissionCodes?.includes('*'))
    )
  }

  return {
    token,
    currentUser,
    isAuthenticated,
    initializationError,
    username,
    login,
    logout,
    fetchCurrentUser,
    init,
    hasPermission,
    canAccessMenu,
    canAccessSupplierManufacturerManagement,
    canWrite,
    hasRole,
  }
})
