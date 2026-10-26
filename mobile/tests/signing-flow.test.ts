import assert from 'node:assert/strict'
import test from 'node:test'
import { SigningFlow, type SigningRecord, type SigningJournal, type SigningApi } from '../src/signingFlow.ts'
import { MobileApiError, type Operation, type DeliveryDetail, type OperationResult } from '../src/mobileTypes.ts'
import { createOperationId } from '../src/uuid.ts'

const scope = {baseUrl: 'http://172.16.2.153:1818/api', serverId: 'test-server', userId: 1}
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
const detail: DeliveryDetail = {task: {id: 10, documentNo: 'TEST-PS-10', status: 'picked', deliveryType: 'loose',
  deptId: 2, deptName: '测试科室', sourceWarehouseId: 3, sourceWarehouseName: '测试中心库', warehouseId: 4,
  warehouseName: '测试科室库', productCode: 'T1', productName: '测试耗材', quantity: 4, unit: '个'}, items: []}
class MemoryJournal implements SigningJournal {
  value: SigningRecord | null = null
  fail = false
  async load() { return this.value ? clone(this.value) : null }
  async save(value: SigningRecord) { if (this.fail) throw Error('disk full'); this.value = clone(value) }
  async clear() { this.value = null }
}
function fixture(journal = new MemoryJournal()) {
  const sent: Operation[] = []
  const success = (op: Operation): OperationResult => ({operationId: op.operationId, kind: op.kind, taskId: op.taskId,
    documentNo: 'TEST-PS-10', status: 'succeeded', userId: 1, deviceId: op.deviceId, completedAt: new Date().toISOString(), message: '成功'})
  const api: SigningApi = {
    async recover() { return {result:null,reviewHash:'renewed',expiresAt:new Date(Date.now()+300000).toISOString(),summary:clone(detail)} },
    async validate(op) { return {reviewHash: 'signed-review', expiresAt: new Date(Date.now() + 300000).toISOString(), summary: clone(detail)} },
    async submit(op) { sent.push(clone(op)); return success(op) },
    async result(id) { return {operationId: id, kind: 'SIGN_DELIVERY', taskId: null, documentNo: null, status: 'unknown', userId: 1, deviceId: null, completedAt: null, message: ''} }
  }
  return {journal, api, sent, success, flow: new SigningFlow(scope, 'device', journal, api)}
}
async function ready(f: ReturnType<typeof fixture>) { await f.flow.begin(detail); await f.flow.confirmLoose(true); await f.flow.review() }

test('WebView-compatible operation IDs are UUID v4 and unique', () => {
  const ids = Array.from({length: 100}, createOperationId)
  assert.equal(new Set(ids).size, 100)
  for (const id of ids) assert.match(id, /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/)
})
test('cannot submit before server review and edits invalidate the review', async () => {
  const f = fixture(); await f.flow.begin(detail)
  await assert.rejects(f.flow.submit()); assert.equal(f.sent.length, 0)
  await f.flow.confirmLoose(true); await f.flow.review(); await f.flow.confirmLoose(false)
  await assert.rejects(f.flow.submit()); assert.equal(f.sent.length, 0)
})
test('complete review submits once and records server receipt', async () => {
  const f = fixture(); await ready(f); await f.flow.submit()
  assert.equal(f.sent.length, 1); assert.equal(f.flow.current?.state, 'succeeded')
  assert.equal(f.journal.value?.result?.documentNo, 'TEST-PS-10')
})
test('double-click cannot send a second operation', async () => {
  const f = fixture(); await ready(f)
  const result = await Promise.allSettled([f.flow.submit(), f.flow.submit()])
  assert.equal(result.filter(r => r.status === 'fulfilled').length, 1)
  assert.equal(f.sent.length, 1)
})
test('durable journal failure prevents the business write', async () => {
  const f = fixture(); await ready(f); f.journal.fail = true
  await assert.rejects(f.flow.submit(), /disk full/); assert.equal(f.sent.length, 0)
})
test('timeout freezes payload; unknown result cannot be discarded or replaced', async () => {
  const f = fixture(); await ready(f)
  f.api.submit = async op => {f.sent.push(clone(op)); throw Error('timeout')}
  await f.flow.submit()
  assert.equal(f.flow.current?.state, 'unknown')
  await assert.rejects(f.flow.clear()); await assert.rejects(f.flow.begin(detail)); await assert.rejects(f.flow.confirmLoose(false))
  await f.flow.query(); assert.equal(f.flow.current?.state, 'unknown')
  await f.flow.retryOriginal(); assert.deepEqual(f.sent[0], f.sent[1])
})
test('query after lost response recovers the successful original receipt', async () => {
  const f = fixture(); await ready(f)
  f.api.submit = async op => {f.sent.push(clone(op)); throw Error('response lost')}
  await f.flow.submit(); f.api.result = async () => f.success(f.sent[0])
  await f.flow.query(); assert.equal(f.flow.current?.state, 'succeeded'); assert.equal(f.sent.length, 1)
})
test('revoked permission during an uncertain retry never permits a new operation', async () => {
  const f = fixture(); await ready(f); f.api.submit = async () => {throw Error('timeout')}
  await f.flow.submit(); f.api.submit = async () => {throw new MobileApiError(403, 'denied')}
  await f.flow.retryOriginal(); assert.equal(f.flow.current?.state, 'unknown'); await assert.rejects(f.flow.clear())
})
test('explicit initial rejection records conflict without success', async () => {
  const f = fixture(); await ready(f); f.api.submit = async () => {throw new MobileApiError(409, 'already signed')}
  await f.flow.submit(); assert.equal(f.flow.current?.state, 'conflict'); await f.flow.clear(); assert.equal(f.flow.current, null)
})
test('restart turns interrupted submission into query-only recovery', async () => {
  const f = fixture(); await ready(f); f.journal.value!.state = 'submitting'
  const restarted = new SigningFlow(scope, 'device', f.journal, f.api)
  await restarted.restore(); assert.equal(restarted.current?.state, 'unknown')
  assert.equal(restarted.current?.operation.operationId, f.flow.current?.operation.operationId)
  assert.equal(f.sent.length, 0)
})
test('another account cannot restore the saved operation', async () => {
  const f = fixture(); await ready(f)
  const other = new SigningFlow({...scope, userId: 2}, 'device', f.journal, f.api)
  await assert.rejects(other.restore(), /身份不匹配/); assert.equal(f.sent.length, 0)
})
test('duplicate physical code is counted once; wrong delivery code is rejected', async () => {
  const f = fixture()
  const item = {type: 'package' as const, id: 11, code: 'PK-11', alternateCode: '', status: 'delivered'}
  await f.flow.begin({...detail, task: {...detail.task, deliveryType: 'package'}, items: [item]})
  assert.equal(await f.flow.addItem(item), true); assert.equal(await f.flow.addItem(item), false)
  await assert.rejects(f.flow.addItem({...item, id: 12})); assert.equal(f.flow.current?.operation.packageIds.length, 1)
})
test('a receipt for another user is never shown as success', async () => {
  const f = fixture(); await ready(f)
  f.api.submit = async op => ({...f.success(op), userId: 2})
  await f.flow.submit(); assert.equal(f.flow.current?.state, 'unknown')
})
