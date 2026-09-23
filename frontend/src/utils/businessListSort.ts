import { pinyin } from 'pinyin-pro'

export type ListRecord = Record<string, unknown>
const nameFields = ['productName', 'product', 'name', 'itemName', 'materialName', 'templateName', 'licenseName', 'deptName', 'warehouseName', 'supplierName', 'manufacturerName', 'realName', 'roleName', 'username', 'title']
const keyCache = new Map<string, string>()

/** Preserve the original text; normalization is only used for the phonetic comparison key. */
export function pinyinInitials(text: string): string {
  let key = keyCache.get(text)
  if (key === undefined) {
    key = pinyin(text, { pattern: 'first', toneType: 'none', type: 'array', nonZh: 'consecutive' }).join('').toLowerCase()
    if (keyCache.size > 20000) keyCache.clear()
    keyCache.set(text, key)
  }
  return key
}

function canonical(value: unknown): string {
  if (value === undefined) return 'undefined'
  if (value === null) return 'null'
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`
  if (typeof value === 'object') return `{${Object.keys(value as ListRecord).sort().map(key => `${JSON.stringify(key)}:${canonical((value as ListRecord)[key])}`).join(',')}}`
  return JSON.stringify(value)
}

function compareText(a: string, b: string) { return a < b ? -1 : a > b ? 1 : 0 }
function text(value: unknown) { return typeof value === 'string' ? value : canonical(value) }

export function sortBusinessRows<T>(rows: readonly T[]): T[] {
  if (!rows.length || !rows.every(row => row !== null && typeof row === 'object' && !Array.isArray(row))) return [...rows]
  const records = rows as readonly ListRecord[]
  const keys = [...new Set(records.flatMap(row => Object.keys(row)))].sort()
  const name = nameFields.find(key => keys.includes(key))
  const preferred = ['specModel', 'spec', 'unit', 'brand', 'manufacturerName', 'manufacturer', 'supplierName', 'supplier']
  const otherFields = [...preferred.filter(key => keys.includes(key) && key !== name), ...keys.filter(key => key !== name && !preferred.includes(key))]
  const fields = name ? [name, ...otherFields] : otherFields
  const prepared = rows.map((row, index) => ({ row, index,
    primary: name ? pinyinInitials(text((row as ListRecord)[name])) : '',
    values: fields.map(field => pinyinInitials(text((row as ListRecord)[field]))),
    exact: canonical(row)
  }))
  prepared.sort((a, b) => {
    let order = compareText(a.primary, b.primary)
    if (order) return order
    for (let index = 0; index < fields.length; index++) {
      order = compareText(a.values[index], b.values[index])
      if (order) return order
    }
    return compareText(a.exact, b.exact) || a.index - b.index
  })
  return prepared.map(item => item.row)
}

/** Sort business collections, never workflow steps, timeline nodes or print field order. */
export function sortBusinessData<T>(data: T): T {
  if (Array.isArray(data)) return sortBusinessRows(data) as T
  if (data && typeof data === 'object') {
    const copy = { ...data } as ListRecord
    for (const key of ['rows', 'products', 'candidates', 'departments', 'warehouses', 'manufacturers', 'suppliers']) {
      if (Array.isArray(copy[key])) copy[key] = sortBusinessRows(copy[key] as unknown[])
    }
    return copy as T
  }
  return data
}

export interface SortablePage extends ListRecord { rows: unknown[]; total: number; page?: number; size?: number }
export function isSortablePage(value: unknown): value is SortablePage {
  return Boolean(value && typeof value === 'object' && Array.isArray((value as SortablePage).rows)
    && Number.isFinite((value as SortablePage).total))
}

/** Read the filtered result through bounded API pages, then sort before taking the UI page. */
export async function collectSortedRows(first: SortablePage, read: (page: number, size: number) => Promise<SortablePage>): Promise<unknown[]> {
  if (first.total <= first.rows.length && Number(first.page ?? 1) === 1) return sortBusinessRows(first.rows)
  const start = await read(1, 200)
  const size = Number(start.size ?? start.rows.length)
  if (size <= 0 && start.total > 0) throw new Error('列表分页数据不完整，请刷新重试')
  const rows = [...start.rows]
  const pages = Math.ceil(start.total / Math.max(size, 1))
  for (let page = 2; page <= pages; page++) {
    const next = await read(page, size)
    if (next.total !== start.total || (!next.rows.length && rows.length < start.total)) {
      throw new Error('列表数据已变化，请刷新重试')
    }
    rows.push(...next.rows)
  }
  if (rows.length !== start.total) throw new Error('列表分页数据不完整，请刷新重试')
  return sortBusinessRows(rows)
}
