import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { collectSortedRows, isSortablePage, sortBusinessData, type SortablePage } from '../utils/businessListSort'
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

const writeMethods = new Set(['post', 'put', 'patch', 'delete'])
const fieldWhitespace = /[\s\u0085]/u

function validateWriteFields(value: unknown): void {
  if (typeof value === 'string') {
    if (fieldWhitespace.test(value)) throw new Error('不支持空格及换行符存在！')
  } else if (value instanceof File) {
    validateWriteFields(value.name)
  } else if (value instanceof FormData || value instanceof URLSearchParams) {
    value.forEach(entry => validateWriteFields(entry))
  } else if (Array.isArray(value)) {
    value.forEach(validateWriteFields)
  } else if (value && Object.getPrototypeOf(value) === Object.prototype) {
    Object.values(value).forEach(validateWriteFields)
  }
}

http.interceptors.request.use(
  (config) => {
    if (writeMethods.has(config.method ?? '')) {
      validateWriteFields(config.data)
      validateWriteFields(config.params)
    }
    const token = getSessionAdapter().getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

const sortedPageCache = new Map<string, { time: number; rows: unknown[] }>()

http.interceptors.response.use(
  (response) => {
    if (response.config.method !== 'get') sortedPageCache.clear()
    if (response.config.responseType === 'blob') {
      return response
    }
    const { code, message } = response.data as ApiResponse<unknown>
    if (code !== 0) {
      console.error(`[API Error] code=${code}, message=${message}`)
      return Promise.reject(new Error(message || 'Request failed'))
    }
    return response
  },
  (error) => {
    if (axios.isCancel(error)) return Promise.reject(error)
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

export async function getData<T>(url: string, config?: AxiosRequestConfig, groupBy?: string) {
  const data = unwrapData(await http.get<ApiResponse<T>>(url, config))
  if (!isSortablePage(data)) return sortBusinessData(data)
  const params = { ...(config?.params ?? {}) }
  const page = Math.max(1, Number(data.page ?? params.page ?? 1))
  const size = Math.max(1, Number(data.size ?? params.size ?? data.rows.length ?? 20))
  delete params.page
  delete params.size
  const key = JSON.stringify([getSessionAdapter().getToken(), url, groupBy, Object.entries(params).sort()])
  const cached = sortedPageCache.get(key)
  let rows: unknown[]
  if (page > 1 && cached && Date.now() - cached.time < 15000 && cached.rows.length === data.total) {
    rows = cached.rows
  } else {
    rows = await collectSortedRows(data, async (nextPage, nextSize) => {
      const next = unwrapData(await http.get<ApiResponse<SortablePage>>(url, {
        ...config, params: { ...params, page: nextPage, size: nextSize }
      }))
      if (!isSortablePage(next)) throw new Error('列表接口未返回完整分页信息')
      return next
    }, groupBy)
    if (sortedPageCache.size >= 12) sortedPageCache.delete(sortedPageCache.keys().next().value!)
    sortedPageCache.set(key, { time: Date.now(), rows })
  }
  const pageRows = rows.slice((page - 1) * size, page * size)
  return { ...data, total: rows.length, rows: groupBy ? pageRows.flat() : pageRows } as T
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

export async function getPage<T>(url: string, config?: AxiosRequestConfig, groupBy?: keyof T & string) {
  return normalizePageResult(await getData<PageResult<T> | T[]>(url, config, groupBy))
}
