import { http, type ApiResponse, type PageResult } from './http'

export interface UserRow {
  userId: number
  username: string
  realName: string
  phone?: string
  email?: string
  gender: number
  deptId?: number
  deptName?: string
  status: number
  roleNames?: string
  roleIds?: string
  lastLoginTime?: string
  updateTime?: string
}

export interface RoleRow {
  roleId: number
  roleName: string
  roleCode: string
  description?: string
  dataScope: number
  status: number
  sortOrder: number
  userCount: number
  permissionCount: number
  permissionIds?: string
  customDeptIds?: string
  updateTime?: string
}

export interface PermissionRow {
  permissionId: number
  parentId: number
  permissionName: string
  permissionCode: string
  permissionType: number
  path?: string
  sortOrder: number
}

export interface DepartmentOption {
  deptId: number
  deptName: string
  deptCode: string
}

export interface UserPayload {
  username?: string
  password?: string
  realName: string
  phone?: string
  email?: string
  gender: number
  deptId?: number
  status: number
  roleIds: number[]
}

export interface RolePayload {
  roleName: string
  roleCode?: string
  description?: string
  dataScope: number
  status: number
  sortOrder: number
}

/**
 * 查询用户列表
 * @param params - 查询参数
 * @returns 用户列表
 */
export async function fetchUsers(params: Record<string, string>) {
  const { data } = await http.get<ApiResponse<PageResult<UserRow>>>('/users', { params })
  return data.data
}

/**
 * 新增用户
 * @param payload - 用户信息
 * @returns 新增用户ID
 */
export async function createUser(payload: UserPayload) {
  const { data } = await http.post<ApiResponse<{ userId: number }>>('/users', payload)
  return data.data
}

/**
 * 更新用户信息
 * @param userId - 用户ID
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateUser(userId: number, payload: UserPayload) {
  const { data } = await http.put<ApiResponse<{ updated: number }>>(`/users/${userId}`, payload)
  return data.data
}

/**
 * 批量删除用户
 * @param userIds - 待删除的用户ID列表
 * @returns 删除结果
 */
export async function deleteUsers(userIds: number[]) {
  const { data } = await http.put<ApiResponse<{ deleted: number }>>('/users/delete', { userIds })
  return data.data
}

/**
 * 重置用户密码
 * @param userIds - 待重置密码的用户ID列表
 * @returns 重置结果及默认密码
 */
export async function resetUserPasswords(userIds: number[]) {
  const { data } = await http.put<ApiResponse<{ reset: number; temporaryPassword: string }>>(
    '/users/reset-password',
    { userIds }
  )
  return data.data
}

/**
 * 为用户分配角色
 * @param userId - 用户ID
 * @param roleIds - 角色ID列表
 * @returns 分配结果
 */
export async function assignUserRoles(userId: number, roleIds: number[]) {
  const { data } = await http.put<ApiResponse<{ assigned: boolean }>>('/users/roles', { userId, roleIds })
  return data.data
}

/**
 * 查询角色列表
 * @param params - 查询参数
 * @returns 角色列表
 */
export async function fetchRoles(params: Record<string, string>) {
  const { data } = await http.get<ApiResponse<PageResult<RoleRow>>>('/users/roles', { params })
  return data.data
}

/**
 * 新增角色
 * @param payload - 角色信息
 * @returns 新增角色ID
 */
export async function createRole(payload: RolePayload) {
  const { data } = await http.post<ApiResponse<{ roleId: number }>>('/users/roles', payload)
  return data.data
}

/**
 * 更新角色信息
 * @param roleId - 角色ID
 * @param payload - 更新信息
 * @returns 更新结果
 */
export async function updateRole(roleId: number, payload: RolePayload) {
  const { data } = await http.put<ApiResponse<{ updated: number }>>(`/users/roles/${roleId}`, payload)
  return data.data
}

/**
 * 批量删除角色
 * @param roleIds - 待删除的角色ID列表
 * @returns 删除结果
 */
export async function deleteRoles(roleIds: number[]) {
  const { data } = await http.put<ApiResponse<{ deleted: number }>>('/users/roles/delete', { roleIds })
  return data.data
}

/**
 * 为角色分配权限
 * @param roleId - 角色ID
 * @param permissionIds - 权限ID列表
 * @returns 分配结果
 */
export async function assignRolePermissions(roleId: number, permissionIds: number[]) {
  const { data } = await http.put<ApiResponse<{ assigned: boolean }>>('/users/roles/permissions', {
    roleId,
    permissionIds
  })
  return data.data
}

/**
 * 更新角色数据权限范围
 * @param roleId - 角色ID
 * @param dataScope - 数据权限范围
 * @param deptIds - 自定义部门ID
 * @returns 更新结果
 */
export async function updateDataPermission(roleId: number, dataScope: number, deptIds: number[] = []) {
  const { data } = await http.put<ApiResponse<{ updated: number }>>('/users/roles/data-permission', {
    roleId,
    dataScope,
    deptIds
  })
  return data.data
}

/**
 * 获取所有权限列表
 * @returns 权限树数据
 */
export async function fetchPermissions() {
  const { data } = await http.get<ApiResponse<PermissionRow[]>>('/users/permissions')
  return data.data
}

/**
 * 获取部门下拉选项列表
 * @returns 部门选项
 */
export async function fetchDepartmentOptions() {
  const { data } = await http.get<ApiResponse<DepartmentOption[]>>('/users/departments/options')
  return data.data
}
