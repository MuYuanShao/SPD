import assert from 'node:assert/strict'
import test from 'node:test'
import { ConsumptionFlow, type ConsumptionApi, type ConsumptionJournal, type ConsumptionRecord } from '../src/consumptionFlow.ts'
import type { ConsumeSnapshot, ConsumeOperation } from '../src/mobileTypes.ts'
const scope={baseUrl:'http://172.16.2.153:1818/api',serverId:'test',userId:1}
const detail:ConsumeSnapshot={pack:{id:8,labelNo:'PK8',traceCodeId:9,productId:3,productCode:'P3',productName:'测试物料',unit:'个',quantity:2,deptId:2,deptName:'科室',warehouseId:4,warehouseName:'科室库',status:'signed',version:1},batches:[{batchId:5,quantity:2,unitPrice:10}]}
const copy=<T>(v:T):T=>JSON.parse(JSON.stringify(v))
function fixture(){
 let saved:ConsumptionRecord|null=null;const sent:ConsumeOperation[]=[]
 const journal:ConsumptionJournal={async load(){return saved&&copy(saved)},async save(r){saved=copy(r)},async clear(){saved=null}}
 const api:ConsumptionApi={async recover(){return {result:null,reviewHash:'renewed-hash',expiresAt:new Date(Date.now()+300000).toISOString(),summary:copy(detail)}},async validate(){return {reviewHash:'hash',expiresAt:new Date(Date.now()+300000).toISOString(),summary:copy(detail)}},
 async submit(op){sent.push(copy(op));return {operationId:op.operationId,kind:op.kind,taskId:op.taskId,documentNo:'XH1',status:'succeeded',userId:1,deviceId:op.deviceId,completedAt:null,message:'成功',quantity:2,amount:20}},
 async result(id){return {operationId:id,kind:'CONSUME_PACKAGE',taskId:8,documentNo:'XH1',status:'succeeded',userId:1,deviceId:'device',completedAt:null,message:'成功',quantity:2,amount:20}}}
 return {journal,api,sent,flow:new ConsumptionFlow(scope,'device',journal,api)}
}
test('whole package requires explicit confirmation and never supplies quantity or amount',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await assert.rejects(f.flow.review());await f.flow.confirmWhole(true);await f.flow.review();await f.flow.submit()
 assert.equal(f.sent.length,1);assert.equal('quantity' in f.sent[0],false);assert.equal('amount' in f.sent[0],false)
 assert.equal(f.flow.current?.result?.quantity,2);assert.equal(f.flow.current?.result?.amount,20)
})
test('unsigned, untraced and empty packages cannot start',async()=>{
 for(const changes of [{status:'consumed'},{traceCodeId:0},{quantity:0}]){const f=fixture();await assert.rejects(f.flow.begin({...detail,pack:{...detail.pack,...changes}},'PK8'))}
})
test('confirmation changes invalidate review and block submission',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review();await f.flow.confirmWhole(false)
 await assert.rejects(f.flow.submit());assert.equal(f.sent.length,0)
})
test('timeout and restart preserve exact original operation and prohibit replacement',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review()
 f.api.submit=async op=>{f.sent.push(copy(op));throw Error('timeout')};await f.flow.submit()
 const restored=new ConsumptionFlow(scope,'device',f.journal,f.api);await restored.restore()
 await assert.rejects(restored.clear());await assert.rejects(restored.begin(detail,'PK8'));await assert.rejects(restored.confirmWhole(false))
 await restored.retryOriginal();assert.deepEqual(f.sent[0],f.sent[1]);await restored.query();assert.equal(restored.current?.state,'succeeded')
})
test('failed durable save prevents consumption HTTP write',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review()
 f.journal.save=async()=>{throw Error('storage unavailable')};await assert.rejects(f.flow.submit());assert.equal(f.sent.length,0)
})
test('another account cannot restore or submit a saved consumption',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');const other=new ConsumptionFlow({...scope,userId:2},'device',f.journal,f.api)
 await assert.rejects(other.restore());assert.equal(other.current,null);assert.equal(f.sent.length,0)
})
test('double confirmation cannot create two HTTP submissions',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review()
 const results=await Promise.allSettled([f.flow.submit(),f.flow.submit()]);assert.equal(f.sent.length,1);assert.equal(results.filter(r=>r.status==='rejected').length,1)
})
test('lost request and expired review recover under the same ID with explicit confirmation',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review()
 const original=f.flow.current!.operation;const success=f.api.submit
 f.api.submit=async()=>{throw Error('request never reached server')};await f.flow.submit()
 await f.flow.recover();assert.equal(f.flow.current?.state,'recovery_ready');assert.equal(f.sent.length,0)
 assert.deepEqual({...f.flow.current!.operation,reviewHash:original.reviewHash},original)
 await assert.rejects(f.flow.clear());await assert.rejects(f.flow.confirmWhole(false));await assert.rejects(f.flow.begin(detail,'PK8'))
 f.api.submit=success;await f.flow.submit();assert.equal(f.sent[0].operationId,original.operationId);assert.equal(f.sent[0].reviewHash,'renewed-hash')
 assert.equal(f.flow.current?.state,'succeeded')
})
test('recovery returns an existing success without another business submission',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review();f.api.submit=async()=>{throw Error('lost response')};await f.flow.submit()
 f.api.recover=async op=>({result:await f.api.result(op.operationId)})
 await f.flow.recover();assert.equal(f.flow.current?.state,'succeeded');assert.equal(f.sent.length,0)
})
test('authoritative rejection permits clearing while a failed recovery remains frozen',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review();f.api.submit=async()=>{throw Error('timeout')};await f.flow.submit()
 f.api.recover=async()=>{throw Error('offline')};await assert.rejects(f.flow.recover());assert.equal(f.flow.current?.state,'unknown');await assert.rejects(f.flow.clear())
 f.api.recover=async op=>({result:{...await f.api.result(op.operationId),status:'rejected',message:'原操作已终止，包状态已变化'}})
 await f.flow.recover();assert.equal(f.flow.current?.state,'conflict');await f.flow.clear();assert.equal(f.flow.current,null)
})
test('recovered confirmation survives restart as unknown and never becomes editable',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review();f.api.submit=async()=>{throw Error('timeout')};await f.flow.submit();await f.flow.recover()
 const restored=new ConsumptionFlow(scope,'device',f.journal,f.api);await restored.restore();assert.equal(restored.current?.state,'unknown');await assert.rejects(restored.confirmWhole(false));await restored.recover();assert.equal(restored.current?.state,'recovery_ready')
})
test('recovery refuses cross-task summaries and cross-user receipts',async()=>{
 const f=fixture();await f.flow.begin(detail,'PK8');await f.flow.confirmWhole(true);await f.flow.review();f.api.submit=async()=>{throw Error('timeout')};await f.flow.submit()
 f.api.recover=async()=>({result:null,summary:{...detail,pack:{...detail.pack,id:99}},reviewHash:'bad',expiresAt:new Date(Date.now()+300000).toISOString()})
 await assert.rejects(f.flow.recover());assert.equal(f.flow.current?.state,'unknown')
 f.api.recover=async op=>({result:{...await f.api.result(op.operationId),userId:99}});await assert.rejects(f.flow.recover());assert.equal(f.flow.current?.state,'unknown')
})
