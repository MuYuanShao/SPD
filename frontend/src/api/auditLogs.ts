import { getData, getPage } from './http'

/**
 * Front-end client for /api/audit-logs.
 * The back-end AuditLogQueryService is the source of truth: it
 * returns PageResult<AuditLogRow> with summary, plus detail endpoints
 * for operation-type / biz-type facets and per-row before/after diffs.
 */

export interface AuditLogRow {
  auditId: number
  operatorId?: number | null
  operatorName?: string
  operationType: string
  bizType: string
  bizId?: number | null
  bizNo?: string | null
  ipAddress?: string
  operationTime?: string
  remark?: string
  beforeData?: Record<string, unknown> | null
  afterData?: Record<string, unknown> | null
}

export interface AuditLogSummary {
  total: number
  today: number
  distinctOperators: number
  distinctBizTypes: number
}

export interface AuditLogListResult {
  rows: AuditLogRow[]
  total: number
  page: number
  size: number
  summary?: AuditLogSummary
}

export interface AuditLogFacet {
  operationType: string
  total: number
}

export function fetchAuditLogs(params: Record<string, string | number | undefined>) {
  const filtered: Record<string, string> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    filtered[k] = String(v)
  }
  return getPage<AuditLogRow>('/audit-logs', { params: filtered })
}

export function fetchAuditLogDetail(auditId: number) {
  return getData<{ audit: AuditLogRow | null }>(`/audit-logs/${auditId}`)
}

export function fetchAuditLogOperationTypes() {
  return getData<{ operationTypes: AuditLogFacet[] }>('/audit-logs/operation-types')
}

export function fetchAuditLogBizTypes() {
  return getData<{ bizTypes: AuditLogFacet[] }>('/audit-logs/biz-types')
}
