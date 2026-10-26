import axios from 'axios'
import { MobileApiError, type DeliveryDetail, type PickTask, type PickUnit, type PickOrder, type OperationResult, type ConsumeSnapshot } from './mobileTypes'

const multiScenario = new URLSearchParams(location.search).get('scenario') === 'multi'
const storageKey = multiScenario ? 'spd-local-demo-multi-v3' : 'spd-local-demo-v3'
const serverId = multiScenario ? 'local-demo-spd-multi' : 'local-demo-spd'
type Command = {operationId:string;deviceId:string;kind:string;taskId:number;deptId:number;warehouseId:number;
  quantity?:string;rawCode?:string;packageIds?:number[];traceCodeIds?:number[];looseConfirmed?:boolean;
  wholePackageConfirmed?:boolean;reviewHash?:string;username?:string;password?:string}
type State = {deliveries:DeliveryDetail[];picks:PickTask[];packs:Record<string,ConsumeSnapshot>;
  receipts:Record<string,OperationResult>;reviews:Record<string,{payload:string;token:string;expires:number}>}
const copy = <T>(v:T):T => JSON.parse(JSON.stringify(v))
const units:Record<number,PickUnit> = {
  101:{type:'product',id:501,code:'P0',status:'available',quantity:1},
  102:{type:'package',id:701,code:'PK701',status:'available',quantity:2},
  103:{type:'trace',id:901,code:'U901',status:'in_stock',quantity:1},
  104:{type:'product',id:504,code:'SYR-5',status:'available',quantity:1},
  105:{type:'product',id:505,code:'SYR-10',status:'available',quantity:1},
  106:{type:'product',id:506,code:'INF-1',status:'available',quantity:1}
}
const warehouse = {deptId:2,deptName:'演示科室',warehouseId:4,warehouseName:'演示科室库'}
function pack(code:string,id:number,quantity=2):ConsumeSnapshot {
  return {pack:{id,labelNo:code,traceCodeId:id+10000,productId:502,productCode:'P1',productName:'演示定数包',unit:'个',quantity,
    ...warehouse,status:'signed',version:1},batches:[{batchId:5,quantity,unitPrice:10}]}
}
function initial():State {
  const task={id:10,documentNo:'演示-配送-10',status:'picked',deliveryType:'loose',...warehouse,
    sourceWarehouseId:3,sourceWarehouseName:'演示中心库',productCode:'SYR-5',productName:'一次性使用注射器',quantity:4,unit:'支',
    requisitionNo:'SL-DEMO-20261026-001',specModel:'5ml · 无菌独立包装',manufacturerName:'演示医疗器械厂',createdAt:'2026-10-26T08:30:00',signedAt:null}
  const batch={batchId:50001,systemBatchNo:'SYS-DEMO-20261026-001',productionBatchNo:'DEMO-20260901',productionDate:'2026-09-01',expireDate:'2029-08-31',quantity:4}
  const state:State = {deliveries:[{task,items:[],batches:[batch]},{task:{...task,id:20,documentNo:'演示-配送-20',productCode:'GAUZE-PACK',productName:'医用纱布定数包',specModel:'2片/包 · 10cm×10cm',unit:'片',deliveryType:'package'},
    batches:[{...batch,batchId:50002,systemBatchNo:'SYS-DEMO-20261026-002'}],items:[11,12].map(id=>({type:'package',id,code:'PK'+id,alternateCode:'',status:'delivered'}))}],
    picks:['PICK_LOOSE','PICK_PACKAGE','PICK_HIGH_VALUE'].map((kind,n)=>({id:101+n,requisitionId:10001+n,documentNo:'演示-申领-'+(101+n),status:'approved',kind:kind as PickTask['kind'],
      ...warehouse,sourceWarehouseId:3,sourceWarehouseName:'演示中心库',productId:501+n,productCode:'P'+n,
      productName:['演示散货','演示定数包','演示高值耗材'][n],specModel:['10片/袋','2个/包','标准型'][n],manufacturerName:'演示医疗器械厂',unit:'个',quantity:kind==='PICK_LOOSE'?4:units[101+n].quantity,
      pickedQuantity:0,remainingQuantity:kind==='PICK_LOOSE'?4:units[101+n].quantity,packageQuantity:kind==='PICK_PACKAGE'?2:null,packageUnit:kind==='PICK_PACKAGE'?'包':null})),
    packs:{PK8:pack('PK8',8)},receipts:{},reviews:{}}
  if(multiScenario){
    state.picks.unshift(...[104,105].map((id,n)=>({...state.picks[0],id,productId:504+n,productCode:units[id].code,productName:'一次性使用注射器',specModel:n===0?'5ml · 无菌独立包装':'10ml · 无菌独立包装',manufacturerName:'演示医疗器械厂',unit:'支',quantity:10,pickedQuantity:0,remainingQuantity:10})))
    state.picks.forEach(p=>{p.documentNo='SL-DEMO-20261023-001';p.requisitionId=5001})
    state.picks.push({...state.picks[0],id:106,requisitionId:5000,documentNo:'SL-DEMO-20261023-002',productId:506,productCode:'INF-1',productName:'一次性输液器',specModel:'普通型',unit:'套',quantity:3,pickedQuantity:0,remainingQuantity:3})
  }
  return state
}
function requireValue<T>(value:T|undefined):T {if(value===undefined)throw new MobileApiError(404,'未找到演示数据');return value}
function payload(command:Command) {const {reviewHash,...rest}=command;return JSON.stringify(rest)}

/** Development-only service: every Axios request is handled here, including unknown paths (never forwarded). */
export function enableDemo() {
  let state:State = JSON.parse(sessionStorage.getItem(storageKey) || 'null') || initial()
  const save=()=>sessionStorage.setItem(storageKey,JSON.stringify(state))
  function orderDetail(id:number) {
    const all=state.picks.filter(t=>t.requisitionId===id);const first=requireValue(all[0]);const pending=all.filter(t=>t.remainingQuantity>0)
    const status=pending.length?(all.some(t=>t.pickedQuantity>0)?'partial_picked':'approved'):'picked'
    const order:PickOrder={id,documentNo:first.documentNo,status,deptId:first.deptId,deptName:first.deptName,sourceWarehouseId:first.sourceWarehouseId,sourceWarehouseName:first.sourceWarehouseName,warehouseId:first.warehouseId,warehouseName:first.warehouseName,pendingLineCount:pending.length}
    return {order,items:pending.map(t=>({...copy(t),status}))}
  }
  function summary(op:Command) {
    if(op.deptId!==2||op.warehouseId!==4)throw new MobileApiError(403,'请选择演示科室库房')
    if(op.kind==='SIGN_DELIVERY') {
      const detail=requireValue(state.deliveries.find(d=>d.task.id===op.taskId))
      if(detail.task.status!=='picked')throw new MobileApiError(409,'演示配送单已签收')
      if(detail.task.deliveryType==='loose'&&!op.looseConfirmed)throw new MobileApiError(400,'请确认散货实收')
      if(detail.items.some(i=>!(i.type==='package'?op.packageIds:op.traceCodeIds)?.includes(i.id)))throw new MobileApiError(400,'请核对全部演示实物码')
      return copy(detail)
    }
    if(op.kind==='CONSUME_PACKAGE') {
      const detail=requireValue(state.packs[op.rawCode || ''])
      if(detail.pack.id!==op.taskId||detail.pack.status!=='signed')throw new MobileApiError(409,'该演示包已消耗或身份不符')
      if(!op.wholePackageConfirmed)throw new MobileApiError(400,'请确认整包消耗')
      return copy(detail)
    }
    const task=requireValue(state.picks.find(p=>p.id===op.taskId)),unit=units[task.id]
    const quantity=op.kind==='PICK_LOOSE'?Number(op.quantity):unit.quantity
    if(!Number.isInteger(quantity))throw new MobileApiError(400,'拣选数量必须为正整数')
    if(!(quantity>0)||quantity>task.remainingQuantity)throw new MobileApiError(409,'本次数量超过演示剩余量')
    if(op.kind==='PICK_LOOSE'&&op.rawCode!==unit.code)throw new MobileApiError(400,'演示商品码不正确')
    if(op.kind==='PICK_PACKAGE'&&!op.packageIds?.includes(unit.id))throw new MobileApiError(400,'请扫描 PK701')
    if(op.kind==='PICK_HIGH_VALUE'&&!op.traceCodeIds?.includes(unit.id))throw new MobileApiError(400,'请扫描 U901')
    return {task:copy(task),units:[copy(unit)],quantity,availableQuantity:op.kind==='PICK_LOOSE'?10:null}
  }
  function review(op:Command) {
    const result=summary(op),expires=Date.now()+300000,token='demo-review-'+op.operationId+'-'+expires
    state.reviews[op.operationId]={payload:payload(op),token,expires};save()
    return {summary:result,reviewHash:token,expiresAt:new Date(expires).toISOString()}
  }
  function result(op:Command,status:string,message:string):OperationResult {
    return {operationId:op.operationId,kind:op.kind,taskId:op.taskId,documentNo:null,status,userId:90001,
      deviceId:op.deviceId,completedAt:new Date().toISOString(),message}
  }
  axios.defaults.adapter=async config=>{
    const path=new URL((config.baseURL || '')+(config.url || ''),'http://localhost').pathname.replace(/^.*\/api/,'')
    const body:Command=typeof config.data==='string'?JSON.parse(config.data):config.data || {}
    const id=body.operationId
    let data:unknown
    if(path==='/health')data={service:'hospital-spd',status:'UP'}
    else if(path==='/auth/login') {
      if(body.username!=='demo'||body.password!=='demo123')throw new MobileApiError(401,'演示账号 demo，密码 demo123，请勿使用真实账号')
      data={token:'local-demo-only'}
    }else if(config.headers.Authorization!=='Bearer local-demo-only')throw new MobileApiError(401,'请先登录本地演示账号')
    else if(path==='/mobile/context')data={serverId,userId:90001,username:'本地演示账号',capabilities:['SIGN_DELIVERY','PICK_LOOSE','PICK_PACKAGE','PICK_HIGH_VALUE','CONSUME_PACKAGE'],warehouses:[warehouse],consumptionWarehouses:[warehouse]}
    else if(path==='/mobile/tasks') {const rows=state.deliveries.filter(d=>d.task.status==='picked').map(d=>d.task);data={rows,total:rows.length,page:1,size:20}}
    else if(path.startsWith('/mobile/tasks/SIGN_DELIVERY/'))data=copy(requireValue(state.deliveries.find(d=>d.task.id===Number(path.split('/').pop()))))
    else if(path==='/mobile/scans/resolve')data=copy(requireValue(state.deliveries.find(d=>d.task.id===body.taskId)?.items.find(i=>i.code===body.rawCode)))
    else if(path==='/mobile/picking/tasks') {const q=String(config.params?.query || '');const rows=state.picks.filter(p=>p.remainingQuantity>0&&(p.productName+p.documentNo+p.productCode).includes(q));data={rows:copy(rows),total:rows.length,page:1,size:20}}
    else if(path==='/mobile/picking/orders') {
      const query=String(config.params?.query||'');const orders=[...new Set(state.picks.map(t=>t.requisitionId!))].map(id=>orderDetail(id)).filter(d=>d.items.length&&d.items.some(t=>(t.documentNo+t.productCode+t.productName).includes(query))).map(d=>d.order).sort((a,b)=>b.id-a.id)
      const page=Number(config.params?.page||1),size=Number(config.params?.size||20);data={rows:copy(orders.slice((page-1)*size,page*size)),total:orders.length,page,size}
    }
    else if(/^\/mobile\/picking\/orders\/\d+$/.test(path))data=orderDetail(Number(path.split('/').pop()))
    else if(/^\/mobile\/picking\/orders\/\d+\/scans\/resolve$/.test(path)) {
      const detail=orderDetail(Number(path.split('/')[4]));const matches=detail.items.filter(t=>units[t.id]?.code===body.rawCode).map(task=>({task,unit:copy(units[task.id])}))
      if(!matches.length)throw new MobileApiError(400,'该码不属于本单待拣明细，或实物状态不可用，请核对原单');data={matches}
    }
    else if(path.startsWith('/mobile/picking/tasks/'))data=copy(requireValue(state.picks.find(p=>p.id===Number(path.split('/').pop()))))
    else if(path==='/mobile/picking/scans/resolve') {const unit=units[body.taskId];if(!unit||unit.code!==body.rawCode)throw new MobileApiError(400,'演示码不匹配，请参考页面上方示例');data=copy(unit)}
    else if(path==='/mobile/consumption/packages/resolve') {const detail=requireValue(state.packs[body.rawCode || '']);if(detail.pack.status!=='signed')throw new MobileApiError(409,'该演示包已消耗，请重置演示或选择其他包');data=copy(detail)}
    else if(['/mobile/operations/validate','/mobile/picking/operations/validate','/mobile/consumption/operations/validate'].includes(path))data=review(body)
    else if(path.endsWith('/operations/recover')) {
      if(state.receipts[id])data={result:state.receipts[id]}
      else try {data=review(body)}catch(error){if(!(error instanceof MobileApiError))throw error;state.receipts[id]=result(body,'rejected','原演示操作已终止：'+error.message);save();data={result:state.receipts[id]}}
    }else if(['/mobile/operations','/mobile/picking/operations','/mobile/consumption/operations'].includes(path)) {
      if(state.receipts[id])data=state.receipts[id]
      else {
        const reviewed=state.reviews[id]
        if(!reviewed||reviewed.token!==body.reviewHash||reviewed.expires<=Date.now()||reviewed.payload!==payload(body))throw new MobileApiError(409,'演示核对摘要已失效，请重新核对或恢复原操作')
        const checked=summary(body),receipt=result(body,'succeeded','演示作业成功（未写入SPD）')
        receipt.documentNo='演示-单据-'+(Object.keys(state.receipts).length+1)
        if(body.kind==='SIGN_DELIVERY') {
          const detail=requireValue(state.deliveries.find(d=>d.task.id===body.taskId));detail.task.status='signed';detail.task.signedAt=new Date().toISOString();receipt.documentNo=detail.task.documentNo;receipt.message='配送整单签收成功（本地演示）'
          for(const item of detail.items)if(item.type==='package')state.packs[item.code]=pack(item.code,item.id,detail.task.quantity/detail.items.length)
        }else if(body.kind==='CONSUME_PACKAGE') {
          const detail=state.packs[body.rawCode!];detail.pack.status='consumed';receipt.quantity=detail.pack.quantity;receipt.amount=detail.pack.quantity*10
        }else if('quantity' in checked&&'task' in checked) {
          const task=requireValue(state.picks.find(p=>p.id===body.taskId)),unit=units[task.id];task.pickedQuantity+=checked.quantity;task.remainingQuantity-=checked.quantity;receipt.remainingQuantity=task.remainingQuantity
          task.status=task.remainingQuantity===0?'picked':'partial_picked'
          const deliveryId=1000+state.deliveries.length
          state.deliveries.push({task:{...task,id:deliveryId,documentNo:'演示-配送-'+deliveryId,requisitionNo:task.documentNo,createdAt:new Date().toISOString(),signedAt:null,status:'picked',deliveryType:body.kind==='PICK_LOOSE'?'loose':body.kind==='PICK_PACKAGE'?'package':'unique_code',quantity:checked.quantity},
            batches:[{batchId:50000+deliveryId,systemBatchNo:'SYS-DEMO-'+deliveryId,productionBatchNo:'DEMO-20260901',productionDate:'2026-09-01',expireDate:'2029-08-31',quantity:checked.quantity}],
            items:body.kind==='PICK_LOOSE'?[]:[{type:body.kind==='PICK_PACKAGE'?'package':'trace',id:unit.id,code:unit.code,alternateCode:'',status:body.kind==='PICK_PACKAGE'?'delivered':'delivery_picked'}]})
        }
        state.receipts[id]=receipt;save();data=receipt
      }
    }else if(path.startsWith('/mobile/operations/'))data=state.receipts[path.split('/').pop()!] || {operationId:path.split('/').pop(),status:'unknown',userId:90001}
    else throw new MobileApiError(404,'该入口未提供本地演示，已阻止外部请求')
    return {data:{code:0,message:'本地演示',data:copy(data)},status:200,statusText:'OK',headers:{},config}
  }
  const panel=document.createElement('details')
  panel.className='demo-tools'
  panel.innerHTML='<summary>本地演示说明 · 示例码</summary><p>模拟数据，不写入真实 SPD。<br>登录：demo / demo123<br>拣选：P0、PK701、U901<br>注射器：SYR-5、SYR-10<br>签收包：PK11、PK12<br>整包消耗：PK8（1包2个）</p><p><a href="/?demo=1&scenario=multi">查看同一申领单多个商品</a></p>'
  const reset=document.createElement('button');reset.textContent='重置演示数据'
  reset.onclick=()=>{sessionStorage.removeItem(storageKey);for(const key of Object.keys(sessionStorage))if(key.startsWith('spd-dev-signing:')&&key.includes('"'+serverId+'"'))sessionStorage.removeItem(key);location.reload()}
  panel.append(reset);document.body.prepend(panel)
}
