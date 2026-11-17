import { postData, putData } from './http'

export interface SystemNotice {
  id: string
  title: string
  date: string
  content: string
}

export function publishSystemNotice(title: string, content: string) {
  return postData<void>('/system-notices', { title, content }, { freeTextFields: ['title', 'content'] })
}

export function claimSystemNotice(loginId: string) {
  return postData<SystemNotice | null>('/system-notices/login-delivery', undefined, { params: { loginId } })
}

export function acknowledgeSystemNotice(id: string) {
  return putData<void>(`/system-notices/${encodeURIComponent(id)}/read`, {})
}
