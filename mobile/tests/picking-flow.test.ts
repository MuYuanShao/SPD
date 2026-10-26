import assert from 'node:assert/strict'
import test from 'node:test'
import { PickingFlow, type PickingApi, type PickingJournal, type PickingRecord } from '../src/pickingFlow.ts'
import type { PickTask, PickOperation, PickKind } from '../src/mobileTypes.ts'

const scope = {baseUrl: 'http://172.16.2.153:1818/api', serverId: 'test-picking', userId: 1}
const task: PickTask = {id: 8, documentNo: 'SL-8', status: 'approved', kind: 'PICK_LOOSE', deptId: 2, deptName: '科室',
  sourceWarehouseId: 3, sourceWarehouseName: '中心库', warehouseId: 4, warehouseName: '科室库', productId: 5,
  productCode: 'P5', productName: '测试物料', unit: '个', quantity: 4, pickedQuantity: 0, remainingQuantity: 4,
  packageQuantity: 2, packageUnit: '包'}
const copy = <T>(v: T): T => JSON.parse(JSON.stringify(v))
function fixture(kind: PickKind = 'PICK_LOOSE') {
  const line = {...task, kind}; let saved: PickingRecord | null = null; const sent: PickOperation[] = []
  const journal: PickingJournal = {async load() {return saved && copy(saved)}, async save(r) {saved = copy(r)}, async clear() {saved = null}}
  const api: PickingApi = {
    async recover() { return {result:null,reviewHash:'renewed',expiresAt:new Date(Date.now()+300000).toISOString(),summary:{task:line,units:[],quantity:2,availableQuantity:6}} },
    async validate(op) {return {reviewHash: 'hash', expiresAt: new Date(Date.now()+300000).toISOString(), summary: {
      task: line, units: [], quantity: Number(op.quantity || '2'), availableQuantity: 6}}},
    async submit(op) {sent.push(copy(op)); return {operationId: op.operationId, kind, taskId: op.taskId, documentNo: 'PS-1',
      status: 'succeeded', userId: 1, deviceId: op.deviceId, completedAt: new Date().toISOString(), message: '成功', remainingQuantity: 2}},
    async result(id) {return {operationId: id, kind, taskId: null, documentNo: null, status: 'unknown', userId: 1, deviceId: null, completedAt: null, message: ''}}
  }
  return {line, journal, api, sent, flow: new PickingFlow(scope, 'device', journal, api)}
}
test('whole loose quantity and source are preserved', async () => {
  const f=fixture();await f.flow.begin(f.line)
  await f.flow.addUnit({type: 'product',id: 5,code: 'P5',status: 'available',quantity: 1}, 'P5')
  await f.flow.setQuantity('2');await f.flow.review();await f.flow.submit()
  assert.equal(f.sent[0].quantity,'2');assert.equal(f.sent[0].sourceWarehouseId,3)
  assert.equal(f.flow.current?.result?.remainingQuantity,2)
})
test('fractional and invalid loose quantities cannot be reviewed or submitted', async () => {
  const f=fixture();await f.flow.begin(f.line)
  for(const quantity of ['1.5','0','-1','1e2','100000000000000'])await assert.rejects(f.flow.setQuantity(quantity))
  assert.equal(f.sent.length,0);assert.equal(f.flow.current?.operation.quantity,'')
})
test('wrong product identity is rejected and duplicate product scans never increment quantity', async () => {
  const f=fixture();await f.flow.begin(f.line);await f.flow.setQuantity('1')
  await assert.rejects(f.flow.addUnit({type:'product',id:6,code:'P6',status:'available',quantity:1},'P6'))
  for(let i=0;i<2;i++)await f.flow.addUnit({type:'product',id:5,code:'P5',status:'available',quantity:1},'P5')
  assert.equal(f.flow.current?.operation.quantity,'1');assert.equal(f.flow.current?.detail.units.length,1)
})
test('actual packages use identities, not a client-declared quantity', async () => {
  const f=fixture('PICK_PACKAGE');await f.flow.begin(f.line)
  const unit={type:'package' as const,id:11,code:'PK11',status:'available',quantity:2}
  assert.equal(await f.flow.addUnit(unit,'PK11'),true);assert.equal(await f.flow.addUnit(unit,'PK11'),false)
  await assert.rejects(f.flow.setQuantity('99'))
  assert.deepEqual(f.flow.current?.operation.packageIds,[11]);assert.equal(f.flow.current?.operation.quantity,null)
})
test('high-value picking accepts trace identities and rejects package identities', async () => {
  const f=fixture('PICK_HIGH_VALUE');await f.flow.begin(f.line)
  await assert.rejects(f.flow.addUnit({type:'package',id:3,code:'PK3',status:'available',quantity:1},'PK3'))
  await f.flow.addUnit({type:'trace',id:9,code:'U9',status:'in_stock',quantity:1},'U9')
  assert.deepEqual(f.flow.current?.operation.traceCodeIds,[9]);assert.equal(f.flow.current?.operation.quantity,null)
})
test('uncertain picking retries preserve exact operation ID and all selected identities', async () => {
  const f=fixture('PICK_PACKAGE');await f.flow.begin(f.line)
  await f.flow.addUnit({type:'package',id:11,code:'PK11',status:'available',quantity:2},'PK11');await f.flow.review()
  f.api.submit=async op=>{f.sent.push(copy(op));throw Error('timeout')}
  await f.flow.submit();await assert.rejects(f.flow.clear());await assert.rejects(f.flow.reopen())
  await f.flow.retryOriginal();assert.deepEqual(f.sent[0],f.sent[1])
})
test('modifying reviewed quantity forces a new server review before submission', async () => {
  const f=fixture();await f.flow.begin(f.line);await f.flow.setQuantity('2');await f.flow.review()
  await f.flow.reopen();await f.flow.setQuantity('3');await assert.rejects(f.flow.submit());assert.equal(f.sent.length,0)
})
