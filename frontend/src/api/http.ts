import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { collectSortedRows, isSortablePage, sortBusinessData, type SortablePage } from '../utils/businessListSort'
import { getSessionAdapter } from './session'

declare module 'axios' {
  interface AxiosRequestConfig {
    freeTextFields?: readonly string[]
    /**
     * 上传类请求（multipart/form-data）专用：
     * 文件名允许包含空格（真实文件名常见「营业执照 (1).pdf」），
     * 由后端负责文件名与文件签名一致性校验，前端不做空白拦截。
     */
    multipart?: boolean
    /** 上传类请求的超时时间（毫秒）。大附件默认 10s 会中断，单独放宽。 */
    uploadTimeout?: number
  }
}

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
    // 文件名可能合法包含空格（如「营业执照 (1).pdf」），这里不拦截，
    // 由后端 PendingProductAttachmentService 负责签名/扩展名一致性校验。
  } else if (value instanceof FormData || value instanceof URLSearchParams) {
    value.forEach(entry => validateWriteFields(entry))
  } else if (Array.isArray(value)) {
    value.forEach(validateWriteFields)
  } else if (isPlainRecord(value)) {
    Object.values(value).forEach(validateWriteFields)
  }
}

/** 去掉字符串里的全部空白（含换行），用于「先清洗再提交」的字段。 */
function stripWhitespace(value: string): string {
  return value.replace(/[\s\u0085]/gu, '')
}

/**
 * 判断异常是否来自写入请求的前端字段校验。
 * 这类异常在请求拦截器里抛出，请求根本没有发出，
 * 因此不能按「网络错误」归类，否则用户会看到误导性的 `[Network Error]`。
 */
function isRequestValidationError(error: unknown): boolean {
  const anyError = error as { config?: unknown; message?: string; request?: unknown }
  if (!anyError || typeof anyError !== 'object') return false
  // axios 会把请求拦截器里抛出的异常挂到 config 上，并附带 request 对象。
  if (!anyError.config || !anyError.request) return false
  return typeof anyError.message === 'string' && anyError.message.length > 0
}

/**
 * 判断是否是可以逐字段清洗的「普通对象」载荷。
 * 不能用 `Object.getPrototypeOf(value) === Object.prototype`：
 * Vue 的 reactive() 返回 Proxy，代理对象的原型判断在部分场景下不可靠，
 * 会导致 freeTextFields 清洗分支被跳过。
 * 这里改为排除掉所有特殊类型后按普通对象处理。
 */
function isPlainRecord(value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== 'object') return false
  if (Array.isArray(value)) return false
  if (value instanceof File || value instanceof Blob || value instanceof FormData) return false
  if (value instanceof URLSearchParams || value instanceof Date) return false
  if (typeof (value as { then?: unknown }).then === 'function') return false
  const proto = Object.getPrototypeOf(value)
  return proto === Object.prototype || proto === null || !Object.getPrototypeOf(proto)
}

http.interceptors.request.use(
  (config) => {
    if (writeMethods.has(config.method ?? '')) {
      const data = config.data
      const freeText = config.freeTextFields
      if (freeText?.length && isPlainRecord(data)) {
        // freeTextFields：这些字段允许用户输入空格/换行。
        // 后端 WriteFieldWhitespace 同样拒绝空白，因此这里不是「放行」，
        // 而是「就地清洗」——去掉空白后再提交，保证后端能落库。
        for (const [field, value] of Object.entries(data)) {
          if (freeText.includes(field) && typeof value === 'string') {
            data[field] = stripWhitespace(value)
          } else {
            validateWriteFields(value)
          }
        }
      } else {
        validateWriteFields(data)
      }
      validateWriteFields(config.params)
    }
    if (config.multipart) {
      // 上传大附件时 10s 默认超时容易中断，这里单独放宽。
      config.timeout = config.uploadTimeout ?? 60000
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
    } else if (isRequestValidationError(error)) {
      // 前端写入请求的字段校验失败（请求未发出），不是网络问题，不要按 Network Error 归类。
      console.error('[Request Validation Error]', error.message)
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

export async function getData<T>(url: string, config?: AxiosRequestConfig, groupBy?: string,
  excludedSortFields: readonly string[] = []) {
  const data = unwrapData(await http.get<ApiResponse<T>>(url, config))
  if (!isSortablePage(data)) return sortBusinessData(data)
  const params = { ...(config?.params ?? {}) }
  const page = Math.max(1, Number(data.page ?? params.page ?? 1))
  const size = Math.max(1, Number(data.size ?? params.size ?? data.rows.length ?? 20))
  delete params.page
  delete params.size
  const key = JSON.stringify([getSessionAdapter().getToken(), url, groupBy, excludedSortFields, Object.entries(params).sort()])
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
    }, groupBy, excludedSortFields)
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

/** Reads the server's paged result in its original order. */
export async function getServerPage<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return unwrapData(await http.get<ApiResponse<T>>(url, config))
}
