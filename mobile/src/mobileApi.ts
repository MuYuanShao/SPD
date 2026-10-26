import axios, { type AxiosInstance } from 'axios'
import { Preferences } from '@capacitor/preferences'
import { DEFAULT_TEST_SERVER, normalizeServerUrl, type AppEnvironment } from './serverConfig'
import { createOperationId } from './uuid'
import { MobileApiError, type Operation, type DeliveryTask, type DeliveryDetail, type CheckItem, type Review, type OperationResult } from './mobileTypes'
import type { PickTask, PickUnit, PickOperation, PickSnapshot } from './mobileTypes'
import type { ConsumeSnapshot, ConsumeOperation } from './mobileTypes'
import type { PickOrder, PickOrderDetail, PickOrderMatch } from './mobileTypes'
import type { Recovery } from './onlineOperationFlow'
export type { Operation, DeliveryTask, DeliveryDetail, CheckItem, Review, OperationResult } from './mobileTypes'

export type MobileWarehouse = {deptId:number;deptName:string;warehouseId:number;warehouseName:string}
export type MobileContext = { serverId: string; userId: number; username: string; capabilities: string[]; warehouses: MobileWarehouse[]; consumptionWarehouses?: MobileWarehouse[] }

export type { AppEnvironment } from './serverConfig'
const keys = { baseUrl:'spd.server.url', environment:'spd.server.environment', token:'spd.auth.token', device:'spd.device.id' }
let sessionToken: string | null = null
let sessionServer: string | null = null
export async function getServerUrl() { return (await Preferences.get({ key: keys.baseUrl })).value ?? DEFAULT_TEST_SERVER }
export async function getEnvironment():Promise<AppEnvironment> { return (await Preferences.get({key:keys.environment})).value === 'production' ? 'production' : 'test' }
export async function clearSession() {
  sessionToken = null; sessionServer = null
  await Preferences.remove({key: keys.token})
}
export async function saveServerConfig(value: string, environment: AppEnvironment) {
  const baseUrl = normalizeServerUrl(value, environment)
  if (baseUrl !== await getServerUrl() || environment !== await getEnvironment()) await clearSession()
  await Preferences.set({key: keys.baseUrl, value: baseUrl})
  await Preferences.set({key: keys.environment, value: environment})
}
export async function testConnection(value: string, environment: AppEnvironment) {
  // Testing an unsaved address never transmits the current login token.
  const baseURL = normalizeServerUrl(value, environment)
  const {data} = await axios.get(`${baseURL}/health`, {timeout: 10000})
  if (data.code !== 0 || data.data?.status !== 'UP' || data.data?.service !== 'hospital-spd') throw Error('服务器未返回 SPD 正常健康状态')
  return true
}
export async function getDeviceId() { const saved = await Preferences.get({key:keys.device}); if (saved.value) return saved.value; const id = createOperationId(); await Preferences.set({key:keys.device,value:id}); return id }
export async function apiClient():Promise<AxiosInstance> {
  const baseURL = normalizeServerUrl(await getServerUrl(), await getEnvironment())
  return axios.create({baseURL, timeout: 15000, headers: sessionToken && sessionServer === baseURL ? {Authorization:`Bearer ${sessionToken}`} : {}})
}
export async function login(username:string,password:string) {
  await clearSession()
  const baseURL = normalizeServerUrl(await getServerUrl(), await getEnvironment())
  const {data} = await axios.post(`${baseURL}/auth/login`, {username, password}, {timeout: 15000})
  if (data.code !== 0 || !data.data?.token) throw Error(data.message || '登录失败')
  sessionToken = data.data.token; sessionServer = baseURL
}
async function unwrap<T>(request: Promise<{data: {code: number; message: string; data: T}}>): Promise<T> {
  try {
    const { data } = await request
    if (data.code !== 0) throw new MobileApiError(data.code, data.message)
    return data.data
  } catch (error) {
    if (axios.isAxiosError(error) && Number.isInteger(error.response?.data?.code)) {
      const code = error.response!.data.code as number
      if (code >= 400 && code < 500) throw new MobileApiError(code, String(error.response!.data.message))
    }
    throw error
  }
}
export async function context():Promise<MobileContext> { return unwrap((await apiClient()).get('/mobile/context')) }
export async function tasks(deptId: number, warehouseId: number, page = 1): Promise<{rows: DeliveryTask[]; total: number}> {
  return unwrap((await apiClient()).get('/mobile/tasks', {params: {kind: 'SIGN_DELIVERY', deptId, warehouseId, page, size: 20}}))
}
export async function taskDetail(id: number, deptId: number, warehouseId: number): Promise<DeliveryDetail> {
  return unwrap((await apiClient()).get(`/mobile/tasks/SIGN_DELIVERY/${id}`, {params: {deptId, warehouseId}}))
}
export async function resolveSigningScan(taskId: number, deptId: number, warehouseId: number, rawCode: string): Promise<CheckItem> {
  return unwrap((await apiClient()).post('/mobile/scans/resolve', {taskId, deptId, warehouseId, rawCode}))
}
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.data?.message) return String(error.response.data.message)
    if (error.code === 'ECONNABORTED') return '连接超时，请检查手机网络和服务器地址'
    if (!error.response) return '无法连接服务器，请确认手机可访问该网络、端口和证书'
  }
  return error instanceof Error ? error.message : '操作失败，请重试'
}
export async function validate(op: Operation): Promise<Review> { return unwrap((await apiClient()).post('/mobile/operations/validate', op)) }
export async function submit(op: Operation): Promise<OperationResult> { return unwrap((await apiClient()).post('/mobile/operations', op)) }
export async function operationResult(id: string): Promise<OperationResult> { return unwrap((await apiClient()).get(`/mobile/operations/${id}`)) }
export async function createSigningApi() {
  // Freeze origin and token for this identity so a late response cannot switch to another server/account.
  const client = await apiClient()
  return {
    validate: (operation: Operation) => unwrap<Review>(client.post('/mobile/operations/validate', operation)),
    recover: (operation: Operation) => unwrap<Recovery<DeliveryDetail>>(client.post('/mobile/operations/recover', operation)),
    submit: (operation: Operation) => unwrap<OperationResult>(client.post('/mobile/operations', operation)),
    result: (id: string) => unwrap<OperationResult>(client.get(`/mobile/operations/${id}`))
  }
}
export async function pickingTasks(deptId: number, warehouseId: number, page = 1, query = ''): Promise<{rows: PickTask[]; total: number}> {
  return unwrap((await apiClient()).get('/mobile/picking/tasks', {params: {deptId, warehouseId, page, size: 20, query}}))
}
export async function pickingOrders(deptId:number,warehouseId:number,page=1,query=''):Promise<{rows:PickOrder[];total:number}> {
  return unwrap((await apiClient()).get('/mobile/picking/orders',{params:{deptId,warehouseId,page,size:20,query}}))
}
export async function pickingOrderDetail(id:number,deptId:number,warehouseId:number):Promise<PickOrderDetail> {
  return unwrap((await apiClient()).get(`/mobile/picking/orders/${id}`,{params:{deptId,warehouseId}}))
}
export async function resolveOrderScan(id:number,deptId:number,warehouseId:number,rawCode:string):Promise<{matches:PickOrderMatch[]}> {
  return unwrap((await apiClient()).post(`/mobile/picking/orders/${id}/scans/resolve`,{deptId,warehouseId,rawCode}))
}
export async function pickingDetail(id: number, deptId: number, warehouseId: number): Promise<PickTask> {
  return unwrap((await apiClient()).get(`/mobile/picking/tasks/${id}`, {params: {deptId, warehouseId}}))
}
export async function resolvePickingScan(taskId: number, deptId: number, warehouseId: number, rawCode: string): Promise<PickUnit> {
  return unwrap((await apiClient()).post('/mobile/picking/scans/resolve', {taskId, deptId, warehouseId, rawCode}))
}
export async function createPickingApi() {
  const client = await apiClient()
  return {
    validate: (op: PickOperation) => unwrap<{reviewHash: string; expiresAt: string; summary: PickSnapshot}>(client.post('/mobile/picking/operations/validate', op)),
    recover: (op: PickOperation) => unwrap<Recovery<PickSnapshot>>(client.post('/mobile/picking/operations/recover', op)),
    submit: (op: PickOperation) => unwrap<OperationResult>(client.post('/mobile/picking/operations', op)),
    result: (id: string) => unwrap<OperationResult>(client.get(`/mobile/operations/${id}`))
  }
}
export async function resolveConsumptionPack(deptId: number,warehouseId: number,rawCode: string): Promise<ConsumeSnapshot> {
  return unwrap((await apiClient()).post('/mobile/consumption/packages/resolve',{deptId,warehouseId,rawCode}))
}
export async function createConsumptionApi() {
  const client=await apiClient()
  return {
    validate:(op:ConsumeOperation)=>unwrap<{reviewHash:string;expiresAt:string;summary:ConsumeSnapshot}>(client.post('/mobile/consumption/operations/validate',op)),
    recover:(op:ConsumeOperation)=>unwrap<Recovery<ConsumeSnapshot>>(client.post('/mobile/consumption/operations/recover',op)),
    submit:(op:ConsumeOperation)=>unwrap<OperationResult>(client.post('/mobile/consumption/operations',op)),
    result:(id:string)=>unwrap<OperationResult>(client.get(`/mobile/operations/${id}`))
  }
}
