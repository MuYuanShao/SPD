import { defineStore } from 'pinia'
import { ref } from 'vue'
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
  const currentUser = ref<UserInfo | null>(loadUser())

  const isAuthenticated = ref(token.value !== null && currentUser.value !== null)
  const username = ref(currentUser.value?.username || '')

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

    token.value = data.token
    session.setToken(data.token)

    await fetchCurrentUser()
  }

  async function fetchCurrentUser() {
    try {
      const user = await getData<UserInfo>('/auth/me')
      if (user) {
        currentUser.value = user
        username.value = user.username
        isAuthenticated.value = true
        saveUser(user)
      }
    } catch (e) {
      console.error('Failed to fetch user info', e)
      logout()
    }
  }

  function logout() {
    token.value = null
    currentUser.value = null
    username.value = ''
    isAuthenticated.value = false
    session.clearToken()
    localStorage.removeItem(USER_KEY)
  }

  async function init() {
    const savedToken = session.getToken()
    if (savedToken) {
      token.value = savedToken
      isAuthenticated.value = true
      await fetchCurrentUser()
    }
  }

  function loadUser(): UserInfo | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
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
    username,
    login,
    logout,
    fetchCurrentUser,
    init,
    hasPermission,
    canAccessMenu,
    canWrite,
    hasRole,
  }
})
