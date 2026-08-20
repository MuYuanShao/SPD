import { getData, postData, putData } from './http'

export interface ApprovalFlowStep {
  stepId?: number
  stepOrder: number
  stepName: string
  approverType: string
  roleId?: number
  roleName?: string
  userId?: number
  realName?: string
  deptId?: number
  deptName?: string
  minApprovals: number
  allowSelfApprove: boolean
  dataScope: number
  status: number
}

export interface ApprovalFlowRow {
  flowId: number
  featureCode: string
  featureName: string
  nodeCode: string
  nodeName: string
  scopeType: string
  scopeId: string
  dataScope: number
  status: number
  remark?: string
  deptId?: number
  deptName?: string
  createBy?: number
  createByName?: string
  updateTime?: string
  steps: ApprovalFlowStep[]
}

export interface ApprovalFlowNodeOption {
  featureCode: string
  featureName: string
  nodeCode: string
  nodeName: string
}

export interface ApprovalFlowRoleOption {
  roleId: number
  roleName: string
  roleCode: string
}

export interface ApprovalFlowUserOption {
  userId: number
  username: string
  realName: string
  deptId?: number
}

export interface ApprovalFlowDepartmentOption {
  deptId: number
  deptName: string
  deptCode: string
}

export interface ApprovalFlowOptions {
  nodes: ApprovalFlowNodeOption[]
  roles: ApprovalFlowRoleOption[]
  users: ApprovalFlowUserOption[]
  departments: ApprovalFlowDepartmentOption[]
}

export type ApprovalFlowPayload = Omit<ApprovalFlowRow, 'flowId' | 'deptName' | 'createBy' | 'createByName' | 'updateTime'>

export async function fetchApprovalFlows(params: Record<string, string>) {
  return getData<ApprovalFlowRow[]>('/approval-flows', { params })
}

export async function fetchApprovalFlowOptions() {
  return getData<ApprovalFlowOptions>('/approval-flows/options')
}

export async function createApprovalFlow(payload: ApprovalFlowPayload) {
  return postData<{ flowId: number }>('/approval-flows', payload)
}

export async function updateApprovalFlow(flowId: number, payload: ApprovalFlowPayload) {
  return putData<{ updated: number }>(`/approval-flows/${flowId}`, payload)
}

export async function updateApprovalFlowStatus(flowId: number, status: number) {
  return putData<{ updated: number }>(`/approval-flows/${flowId}/status`, { status })
}
