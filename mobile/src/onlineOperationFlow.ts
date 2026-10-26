import { MobileApiError, type SigningScope, type OperationResult } from './mobileTypes.ts'

export type OnlineState = 'draft' | 'ready' | 'recovery_ready' | 'submitting' | 'unknown' | 'conflict' | 'succeeded'
export interface OnlineCommand { operationId: string; deviceId: string; kind: string; taskId: number; deptId: number; warehouseId: number; reviewHash?: string }
export type OnlineRecord<D, O extends OnlineCommand> = { version: 1; scope: SigningScope; detail: D; operation: O;
  state: OnlineState; expiresAt?: string; result?: OperationResult; message: string }
export interface OnlineJournal<D, O extends OnlineCommand> { load(): Promise<OnlineRecord<D, O> | null>; save(record: OnlineRecord<D, O>): Promise<void>; clear(): Promise<void> }
export type Recovery<D> = {result?: OperationResult | null; reviewHash?: string | null; expiresAt?: string | null; summary?: D | null}
export interface OnlineApi<D, O extends OnlineCommand> {
  recover(op: O): Promise<Recovery<D>>;
  validate(op: O): Promise<{reviewHash: string; expiresAt: string; summary: D}>;
  submit(op: O): Promise<OperationResult>; result(id: string): Promise<OperationResult>
}
export const copyRecord = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

/** Shared crash/retry protection for stock-affecting operations; adapters supply the task-specific payload. */
export class OnlineOperationFlow<D, O extends OnlineCommand> {
  protected record: OnlineRecord<D, O> | null = null
  protected scope: SigningScope
  protected deviceId: string
  private journal: OnlineJournal<D, O>
  private api: OnlineApi<D, O>
  private supports: (kind: string) => boolean
  private identity: (detail: D) => {id: number; deptId: number; warehouseId: number}
  private busy = false
  constructor(scope: SigningScope, deviceId: string, journal: OnlineJournal<D, O>, api: OnlineApi<D, O>,
    supports: (kind: string) => boolean, identity: (detail: D) => {id: number; deptId: number; warehouseId: number}) {
    if (!scope.serverId || !scope.baseUrl || !scope.userId) throw new Error('服务器或账号上下文不完整')
    this.scope = copyRecord(scope); this.deviceId = deviceId; this.journal = journal; this.api = api
    this.supports = supports; this.identity = identity
  }
  get current(): OnlineRecord<D, O> | null { return this.record ? copyRecord(this.record) : null }
  protected async exclusive<T>(work: () => Promise<T>): Promise<T> {
    if (this.busy) throw new Error('操作正在处理，请稍候')
    this.busy = true
    try { return await work() } finally { this.busy = false }
  }
  protected async persist(next: OnlineRecord<D, O>) { await this.journal.save(copyRecord(next)); this.record = copyRecord(next) }
  protected requireRecord(): OnlineRecord<D, O> {
    if (!this.record) throw new Error('没有当前作业记录')
    return copyRecord(this.record)
  }
  async restore() {
    return this.exclusive(async () => {
      const saved = await this.journal.load()
      if (!saved) return
      if (saved.version !== 1 || saved.scope.baseUrl !== this.scope.baseUrl || saved.scope.serverId !== this.scope.serverId
        || saved.scope.userId !== this.scope.userId || !this.supports(saved.operation.kind)) throw new Error('待处理记录身份不匹配，已阻止读取和提交')
      if (!['draft', 'ready', 'recovery_ready', 'submitting', 'unknown', 'conflict', 'succeeded'].includes(saved.state)) throw new Error('待处理记录状态无效')
      if (['submitting', 'recovery_ready'].includes(saved.state)) { saved.state = 'unknown'; saved.message = '上次提交结果尚未确认，请查询或恢复原操作' }
      if (saved.state === 'ready') { saved.state = 'draft'; delete saved.operation.reviewHash; delete saved.expiresAt }
      await this.persist(saved)
    })
  }
  protected async edit(change: (record: OnlineRecord<D, O>) => void) {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (!['draft', 'ready'].includes(record.state)) throw new Error('当前操作不可修改，请先确认服务器结果')
      change(record); record.state = 'draft'; record.message = ''
      delete record.operation.reviewHash; delete record.expiresAt
      await this.persist(record)
    })
  }
  async review() {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (!['draft', 'ready'].includes(record.state)) throw new Error('请先确认原操作结果')
      const review = await this.api.validate(copyRecord(record.operation)); const task = this.identity(review.summary)
      if (task.id !== record.operation.taskId || task.deptId !== record.operation.deptId || task.warehouseId !== record.operation.warehouseId
        || !review.reviewHash || !(Date.parse(review.expiresAt) > Date.now())) throw new Error('服务器核对摘要无效，请刷新')
      record.detail = review.summary; record.operation.reviewHash = review.reviewHash
      record.expiresAt = review.expiresAt; record.state = 'ready'; record.message = '请核对摘要后确认提交'
      await this.persist(record)
    })
  }
  async submit() {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (!['ready','recovery_ready'].includes(record.state) || !record.operation.reviewHash || !(Date.parse(record.expiresAt ?? '') > Date.now())) throw new Error('请重新预校验或恢复原操作后确认提交')
      await this.send(record)
    })
  }
  async recover() {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (!['unknown','recovery_ready'].includes(record.state)) throw new Error('当前操作无需恢复')
      const recovery = await this.api.recover(copyRecord(record.operation))
      if (recovery.result) {
        this.checkResult(record, recovery.result)
        if (!['succeeded','rejected'].includes(recovery.result.status)) throw new Error('服务器未返回原操作的最终结果')
        this.acceptTerminal(record, recovery.result)
      } else {
        if (!recovery.summary || !recovery.reviewHash || !recovery.expiresAt || !(Date.parse(recovery.expiresAt)>Date.now())) throw new Error('恢复摘要无效，请继续查询原操作')
        const task = this.identity(recovery.summary)
        if(task.id!==record.operation.taskId || task.deptId!==record.operation.deptId || task.warehouseId!==record.operation.warehouseId) throw new Error('恢复摘要身份不匹配')
        // Only the review token may change. Original business identity and payload remain frozen.
        record.detail=copyRecord(recovery.summary);record.operation.reviewHash=recovery.reviewHash;record.expiresAt=recovery.expiresAt
        record.state='recovery_ready';record.message='原编号已恢复，请核对最新摘要后确认原操作'
      }
      await this.persist(record)
    })
  }
  async retryOriginal() {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (record.state !== 'unknown') throw new Error('当前没有需要重试的原操作')
      await this.send(record)
    })
  }
  private async send(record: OnlineRecord<D, O>) {
    const wasUncertain = ['unknown','recovery_ready'].includes(record.state)
    record.state = 'submitting'; record.message = '正在提交'
    await this.persist(record)
    let result: OperationResult
    try {
      result = await this.api.submit(copyRecord(record.operation)); this.checkResult(record, result)
      if (!['succeeded','rejected'].includes(result.status)) throw new Error('服务器未确认操作结果')
    } catch (error) {
      const definite = !wasUncertain && error instanceof MobileApiError && [400, 401, 403, 404, 409, 422].includes(error.code)
      record.state = definite ? 'conflict' : 'unknown'
      record.message = definite ? (error as Error).message : '结果尚未确认。请查询原操作，勿重新创建作业记录'
      await this.persist(record); return
    }
    this.acceptTerminal(record, result)
    await this.persist(record)
  }
  async query() {
    return this.exclusive(async () => {
      const record = this.requireRecord()
      if (!['unknown', 'submitting', 'recovery_ready'].includes(record.state)) throw new Error('当前操作无需查询')
      record.state = 'unknown'; const result = await this.api.result(record.operation.operationId)
      if (result.operationId !== record.operation.operationId || result.userId !== record.scope.userId) throw new Error('服务器回执身份不匹配')
      if (['succeeded','rejected'].includes(result.status)) { this.checkResult(record, result); this.acceptTerminal(record,result) }
      else record.message = '尚未查到成功回执，不能认定失败。可继续查询或重试原操作'
      await this.persist(record)
    })
  }
  private acceptTerminal(record: OnlineRecord<D, O>, result: OperationResult) {
    record.state=result.status==='succeeded'?'succeeded':'conflict';record.result=result;record.message=result.message
  }
  private checkResult(record: OnlineRecord<D, O>, result: OperationResult) {
    if (result.operationId !== record.operation.operationId || result.userId !== record.scope.userId
      || result.taskId !== record.operation.taskId || result.kind !== record.operation.kind) throw new Error('服务器回执与当前操作不一致')
  }
  async clear() {
    return this.exclusive(async () => {
      if (this.record && ['unknown', 'submitting', 'recovery_ready'].includes(this.record.state)) throw new Error('结果不明的操作不能丢弃，请先查询或恢复原操作')
      await this.journal.clear(); this.record = null
    })
  }
}
