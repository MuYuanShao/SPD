import { getData, postData, putData } from './http'

export interface SystemConfigRow {
  configId: number
  configType: string
  configTypeName: string
  scopeType: string
  scopeId: string
  configKey: string
  configName: string
  configValue: string
  effectiveMode: string
  effectiveTime: string
  expireTime: string
  riskLevel: string
  status: string
  updateTime: string
}

export interface SystemConfigPayload {
  configType: string
  scopeType: string
  scopeId: string
  configKey: string
  configValue: string
  effectiveMode: string
  expireTime: string
  riskLevel: string
  status: number
}

export interface SystemConfigQuery {
  configType?: string
  keyword?: string
  status?: string
}

export interface ConfigHitCandidate {
  configId: number
  configType: string
  configTypeName: string
  sourceLevel: string
  priority: number
  scopeType: string
  scopeId: string
  configKey: string
  configName: string
  configValue: string
  effectiveTime: string
  expireTime: string
  riskLevel: string
  approvalStatus: string
  hit: boolean
  overridden: boolean
}

export interface ConfigHitExplanation {
  businessPage: string
  configType: string
  finalValue: string
  sourceLevel: string
  sourceConfigId: number | null
  scopeType: string
  scopeId: string
  effectiveTime: string
  expireTime: string
  overridesParent: boolean
  parentValue: string
  lastChangedBy: string
  approvalStatus: string
  candidates: ConfigHitCandidate[]
  priorityRules: string[]
}

export interface ConfigHitQuery {
  configType?: string
  businessPage?: string
  userId?: string
  moduleId?: string
  warehouseId?: string
  departmentId?: string
  campusId?: string
  hospitalId?: string
  tenantId?: string
}

/**
 * 查询系统配置列表
 * @param query - 查询参数（配置类型、关键词、状态等）
 * @returns 系统配置列表
 */
export async function fetchSystemConfigs(query: SystemConfigQuery = {}) {
  return getData<SystemConfigRow[]>('/system-config', { params: query })
}

/**
 * 新增系统配置
 * @param payload - 配置信息
 * @returns 新增配置 key
 */
export async function createSystemConfig(payload: SystemConfigPayload) {
  return postData<{ configKey: string }>('/system-config', payload)
}

/**
 * 更新系统配置
 * @param configId - 配置 ID
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateSystemConfig(configId: number, payload: SystemConfigPayload) {
  return putData<{ configId: number; updatedRows: number }>(
    `/system-config/${configId}`,
    payload
  )
}

/**
 * 更新系统配置状态（启用/禁用）
 * @param configId - 配置 ID
 * @param status - 目标状态
 * @returns 更新结果
 */
export async function updateSystemConfigStatus(configId: number, status: number) {
  return putData<{ configId: number; updatedRows: number }>(
    `/system-config/${configId}/status`,
    { status }
  )
}

/**
 * 查询配置命中说明（解释某业务场景下配置的生效逻辑）
 * @param query - 命中查询参数（业务页面、配置类型等）
 * @returns 配置命中说明
 */
export async function fetchConfigHitExplanation(query: ConfigHitQuery = {}) {
  return getData<ConfigHitExplanation>('/config-hit-explanation', { params: query })
}
