const TOKEN_KEY = 'spd.token'

export interface SessionAdapter {
  getToken(): string | null
  setToken(token: string): void
  clearToken(): void
  handleUnauthorized(): void
}

function getBrowserStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

export const browserSessionAdapter: SessionAdapter = {
  getToken() {
    return getBrowserStorage()?.getItem(TOKEN_KEY) ?? null
  },
  setToken(token: string) {
    getBrowserStorage()?.setItem(TOKEN_KEY, token)
  },
  clearToken() {
    getBrowserStorage()?.removeItem(TOKEN_KEY)
  },
  handleUnauthorized() {
    this.clearToken()
    getBrowserStorage()?.removeItem('spd.user')
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  },
}

let sessionAdapter: SessionAdapter = browserSessionAdapter

export function getSessionAdapter() {
  return sessionAdapter
}

export function setSessionAdapter(adapter: SessionAdapter) {
  sessionAdapter = adapter
}

export function resetSessionAdapter() {
  sessionAdapter = browserSessionAdapter
}
