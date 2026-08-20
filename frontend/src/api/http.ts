import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { getSessionAdapter } from './session'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  rows: T[]
  total: number
  page: number
  size: number
  summary?: Record<string, number>
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use(
  (config) => {
    const token = getSessionAdapter().getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

http.interceptors.response.use(
  (response) => {
    const { code, message } = response.data as ApiResponse<unknown>
    if (code !== 0) {
      console.error(`[API Error] code=${code}, message=${message}`)
      return Promise.reject(new Error(message || 'Request failed'))
    }
    return response
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        getSessionAdapter().handleUnauthorized()
      } else if (status >= 500) {
        console.error(`[Server Error] ${status}: ${error.response.statusText}`)
      }
    } else if (error.code === 'ECONNABORTED') {
      console.error('[Network Error] Request timeout')
    } else {
      console.error('[Network Error]', error.message)
    }
    return Promise.reject(error)
  },
)

function isApiResponse<T>(value: unknown): value is ApiResponse<T> {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'code' in value &&
      'message' in value &&
      'data' in value
  )
}

export function unwrapData<T>(response: AxiosResponse<ApiResponse<T>> | ApiResponse<T>): T {
  const envelope = isApiResponse<T>(response) ? response : response.data
  if (envelope.code !== 0) {
    throw new Error(envelope.message || 'Request failed')
  }
  return envelope.data
}

export function normalizePageResult<T>(data: PageResult<T> | T[]): PageResult<T> {
  if (Array.isArray(data)) {
    return {
      rows: data,
      total: data.length,
      page: 1,
      size: data.length,
    }
  }

  return {
    ...data,
    rows: data.rows ?? [],
    total: data.total ?? data.rows?.length ?? 0,
    page: data.page ?? 1,
    size: data.size ?? data.rows?.length ?? 0,
  }
}

export async function getData<T>(url: string, config?: AxiosRequestConfig) {
  return unwrapData(await http.get<ApiResponse<T>>(url, config))
}

export async function postData<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return unwrapData(await http.post<ApiResponse<T>>(url, data, config))
}

export async function putData<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return unwrapData(await http.put<ApiResponse<T>>(url, data, config))
}

export async function deleteData<T>(url: string, config?: AxiosRequestConfig) {
  return unwrapData(await http.delete<ApiResponse<T>>(url, config))
}

export async function getPage<T>(url: string, config?: AxiosRequestConfig) {
  return normalizePageResult(await getData<PageResult<T> | T[]>(url, config))
}
