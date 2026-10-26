<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { clearSession, context, createSigningApi, errorMessage, getDeviceId, getEnvironment, getServerUrl, login, saveServerConfig,
  createConsumptionApi, resolveConsumptionPack, createPickingApi, pickingDetail, pickingOrders, pickingOrderDetail, resolveOrderScan, taskDetail, tasks, testConnection, type AppEnvironment, type DeliveryTask, type MobileContext } from './mobileApi'
import { SigningFlow, type SigningRecord } from './signingFlow'
import { consumptionJournal, pickingJournal, signingJournal } from './signingJournal'
import SigningPanel from './components/SigningPanel.vue'
import PickingPanel from './components/PickingPanel.vue'
import { PickingFlow, type PickingRecord } from './pickingFlow'
import type { PickTask, PickOrder, PickOrderMatch } from './mobileTypes'
import { ConsumptionFlow, type ConsumptionRecord } from './consumptionFlow'
import ConsumptionPanel from './components/ConsumptionPanel.vue'
import AppIcon from './components/AppIcon.vue'

const server = ref('')
const isDemo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1'
const screen=ref<'home'|'work'|'pending'|'profile'>('home')
const serverSettings=ref(false)
const showPassword=ref(false)
const homeCounts=ref<{signing:number|null;picking:number|null}>({signing:null,picking:null})
const environment = ref<AppEnvironment>('test')
const username = ref('')
const password = ref('')
const message = ref('')
const isError = ref(false)
const ctx = ref<MobileContext | null>(null)
const loading = ref(false)
const warehouse = ref('')
const rows = ref<DeliveryTask[]>([])
const total = ref(0)
const page = ref(1)
type Mode='signing'|'picking'|'consumption'
const mode = ref<Mode>('signing')
const pickRows = ref<PickTask[]>([])
const pickOrders=ref<PickOrder[]>([])
const selectedOrder=ref<PickOrder|null>(null)
const orderLoaded=ref(false)
const orderScanCode=ref('')
const orderScanInput=ref<HTMLInputElement|null>(null)
const scanMatches=ref<PickOrderMatch[]>([])
const resolvedScanCode=ref('')
watch(orderScanCode,()=>{scanMatches.value=[];resolvedScanCode.value=''}, {flush:'sync'})
const query = ref('')
let pickFlow: PickingFlow | null = null
const currentPicking = ref<PickingRecord | null>(null)
let consumeFlow:ConsumptionFlow|null=null
const currentConsumption=ref<ConsumptionRecord|null>(null)
const consumptionCode=ref('')
const consumptionInput=ref<HTMLInputElement|null>(null)
const activeWarehouses=computed(()=>mode.value==='consumption'?(ctx.value?.consumptionWarehouses??ctx.value?.warehouses??[]):(ctx.value?.warehouses??[]))
const hasRecord = computed(() => !!currentSigning.value || !!currentPicking.value || !!currentConsumption.value)
let signingFlow: SigningFlow | null = null
const currentSigning = ref<SigningRecord | null>(null)
const signingSession = ref(0)
function currentFlow(): SigningFlow { if (!signingFlow) throw new Error('签收上下文尚未准备完成'); return signingFlow }
function currentPickFlow(): PickingFlow { if (!pickFlow) throw new Error('拣选上下文尚未准备完成'); return pickFlow }
function currentConsumeFlow():ConsumptionFlow{if(!consumeFlow)throw new Error('整包消耗上下文尚未准备完成');return consumeFlow}

onMounted(async () => {
  await clearSession()
  server.value = await getServerUrl()
  environment.value = await getEnvironment()
})
async function action(work: () => Promise<void>) {
  loading.value = true; message.value = ''; isError.value = false
  try { await work() } catch (e) { message.value = errorMessage(e); isError.value = true }
  finally { loading.value = false }
}
async function test() {
  await action(async () => {
    await testConnection(server.value, environment.value)
    message.value = isDemo ? '本地模拟服务可用' : 'SPD 服务连接正常，可以登录'
  })
}
async function signIn() {
  await action(async () => {
    try {
      await saveServerConfig(server.value, environment.value)
      await login(username.value.trim(), password.value)
      const user = await context()
      warehouse.value = ''; rows.value = []; total.value = 0
      selectedOrder.value=null;pickOrders.value=[];scanMatches.value=[];orderScanCode.value=''
      signingFlow = null; currentSigning.value = null; pickFlow = null; currentPicking.value = null; pickRows.value = []
      consumeFlow=null;currentConsumption.value=null;consumptionCode.value=''
      if (user.serverId) {
        const scope = { baseUrl: await getServerUrl(), serverId: user.serverId, userId: user.userId }
        const device = await getDeviceId()
        signingFlow = new SigningFlow(scope, device, signingJournal(scope), await createSigningApi())
        await signingFlow.restore()
        currentSigning.value = signingFlow.current
        pickFlow = new PickingFlow(scope, device, pickingJournal(scope), await createPickingApi())
        await pickFlow.restore(); currentPicking.value = pickFlow.current
        consumeFlow=new ConsumptionFlow(scope,device,consumptionJournal(scope),await createConsumptionApi())
        await consumeFlow.restore();currentConsumption.value=consumeFlow.current
        if(!showRemainingRecord())mode.value=user.capabilities.includes('SIGN_DELIVERY')?'signing':user.capabilities.some(c=>c.startsWith('PICK_'))?'picking':'consumption'
      }
      signingSession.value++; ctx.value = user
      screen.value=hasRecord.value?'work':'home'
      await loadHomeCounts()
    } catch (e) { await clearSession(); throw e }
    finally { password.value = '' }
  })
}
async function signOut() {
  await action(async () => {
    await clearSession(); ctx.value = null; rows.value = []; warehouse.value = ''
    signingFlow = null; currentSigning.value = null
    pickFlow = null; currentPicking.value = null; pickRows.value = []
    selectedOrder.value=null;pickOrders.value=[];scanMatches.value=[];orderScanCode.value=''
    consumeFlow=null;currentConsumption.value=null;consumptionCode.value=''
    screen.value='home';serverSettings.value=false;homeCounts.value={signing:null,picking:null}
  })
}
async function loadTasks(nextPage = 1) {
  if(mode.value==='picking'&&selectedOrder.value){await loadOrder();return}
  rows.value = []; pickRows.value = []; total.value = 0
  if(mode.value==='consumption'){consumptionCode.value='';return}
  const selected = ctx.value?.warehouses.find(w => String(w.warehouseId) === warehouse.value)
  if (!selected) return
  await action(async () => {
    if (mode.value === 'picking') {
      const result = await pickingOrders(selected.deptId, selected.warehouseId, nextPage, query.value)
      pickOrders.value = result.rows; total.value = result.total
    } else {
      const result = await tasks(selected.deptId, selected.warehouseId, nextPage)
      rows.value = result.rows; total.value = result.total
    }
    page.value = nextPage
  })
}
async function openTask(row: DeliveryTask) {
  await action(async () => {
    const selected = ctx.value?.warehouses.find(w => String(w.warehouseId) === warehouse.value)
    if (!selected) throw new Error('请先选择库房')
    await currentFlow().begin(await taskDetail(row.id, selected.deptId, selected.warehouseId))
    currentSigning.value = currentFlow().current
  })
}
function showRemainingRecord():boolean {
  if(currentSigning.value){selectedOrder.value=null;mode.value='signing';warehouse.value=String(currentSigning.value.operation.warehouseId);return true}
  if(currentPicking.value){mode.value='picking';selectTaskOrder(currentPicking.value.detail.task);warehouse.value=String(currentPicking.value.operation.warehouseId);return true}
  if(currentConsumption.value){selectedOrder.value=null;mode.value='consumption';warehouse.value=String(currentConsumption.value.operation.warehouseId);return true}
  return false
}
async function signingClosed() { currentSigning.value = null; if(!showRemainingRecord())await loadTasks(1) }
async function pickingClosed() {
  const task=currentPicking.value?.detail.task;currentPicking.value=null
  if(showRemainingRecord())return
  if(!selectedOrder.value&&task){
    await action(async()=>{const fresh=task.requisitionId?task:await pickingDetail(task.id,task.deptId,task.warehouseId);selectTaskOrder(fresh)})
  }
  if(selectedOrder.value)await loadOrder();else await loadTasks(1)
}
async function consumptionClosed(){currentConsumption.value=null;consumptionCode.value='';if(!showRemainingRecord()){await nextTick();consumptionInput.value?.focus()}}
async function switchMode(next: Mode) {
  if (hasRecord.value) return
  mode.value = next; query.value = '';consumptionCode.value=''
  selectedOrder.value=null;pickOrders.value=[];scanMatches.value=[];orderScanCode.value=''
  if(!activeWarehouses.value.some(w=>String(w.warehouseId)===warehouse.value))warehouse.value=''
  await loadTasks(1)
}
async function openPick(row: PickTask) {
  await action(async () => {
    const selected = ctx.value?.warehouses.find(w => String(w.warehouseId) === warehouse.value)
    if (!selected) throw new Error('请先选择目标库房')
    await currentPickFlow().begin(await pickingDetail(row.id, selected.deptId, selected.warehouseId))
    currentPicking.value = currentPickFlow().current
  })
}
async function openConsumption(){
  if(loading.value||!consumptionCode.value.trim())return
  const rawCode=consumptionCode.value
  await action(async()=>{
    const selected=activeWarehouses.value.find(w=>String(w.warehouseId)===warehouse.value)
    if(!selected)throw new Error('请先选择科室与消耗库房')
    const flow=currentConsumeFlow()
    const snapshot=await resolveConsumptionPack(selected.deptId,selected.warehouseId,rawCode)
    if(snapshot.pack.deptId!==selected.deptId||snapshot.pack.warehouseId!==selected.warehouseId)throw new Error('服务器返回的包不属于所选科室库房')
    await flow.begin(snapshot,rawCode)
    if(flow===consumeFlow){currentConsumption.value=flow.current;consumptionCode.value=''}
  })
}
const pendingRecords=computed(()=>[currentSigning.value,currentPicking.value,currentConsumption.value].filter(r=>r!=null))
const title=computed(()=>serverSettings.value?'服务器设置':!ctx.value?'':screen.value==='home'?'SPD 移动作业':screen.value==='pending'?'待处理记录':screen.value==='profile'?'我的':mode.value==='picking'?(selectedOrder.value?'申领单拣选':currentPicking.value?'拣选明细':'待拣申领单'):mode.value==='signing'?'配送签收':'扫码消耗')
async function loadHomeCounts(){
  const user=ctx.value,choice=user?.warehouses.find(w=>String(w.warehouseId)===warehouse.value)||user?.warehouses[0]
  if(!user||!choice)return
  const reads:Promise<void>[]=[]
  if(user.capabilities.includes('SIGN_DELIVERY'))reads.push(tasks(choice.deptId,choice.warehouseId).then(v=>{homeCounts.value.signing=v.total}))
  if(user.capabilities.some(c=>c.startsWith('PICK_')))reads.push(pickingOrders(choice.deptId,choice.warehouseId).then(v=>{homeCounts.value.picking=v.total}))
  await Promise.allSettled(reads)
}
function refreshRecords(){currentSigning.value=signingFlow?.current??null;currentPicking.value=pickFlow?.current??null;currentConsumption.value=consumeFlow?.current??null}
async function enterWork(next:Mode){
  refreshRecords();if(hasRecord.value){showRemainingRecord();screen.value='work';return}
  screen.value='work';await switchMode(next)
  if(!warehouse.value&&activeWarehouses.value.length===1){warehouse.value=String(activeWarehouses.value[0].warehouseId);await loadTasks()}
}
async function back(){
  if(loading.value)return
  if(serverSettings.value){serverSettings.value=false;return}
  if(screen.value==='work'&&mode.value==='picking'){
    if(currentPicking.value){
      const record=currentPickFlow().current
      if(record&&!['unknown','submitting','recovery_ready'].includes(record.state)){await action(()=>currentPickFlow().clear());if(!currentPickFlow().current)await pickingClosed();return}
    }else if(selectedOrder.value){selectedOrder.value=null;pickRows.value=[];scanMatches.value=[];orderScanCode.value='';await loadTasks(page.value);return}
  }
  if(hasRecord.value){
    refreshRecords();screen.value='home';message.value='';await loadHomeCounts();return
  }
  screen.value='home';message.value='';await loadHomeCounts()
}
function showPending(){refreshRecords();screen.value='pending'}
function resumeRecord(){refreshRecords();showRemainingRecord();screen.value='work'}
async function settingsFromProfile(){await signOut();serverSettings.value=true}
async function saveSettings(){await action(async()=>{await saveServerConfig(server.value,environment.value);serverSettings.value=false;message.value='服务器配置已保存'})}
function selectTaskOrder(task:PickTask){
  if(!task.requisitionId)return
  selectedOrder.value={id:task.requisitionId,documentNo:task.documentNo,status:task.status,deptId:task.deptId,deptName:task.deptName,
    sourceWarehouseId:task.sourceWarehouseId,sourceWarehouseName:task.sourceWarehouseName,warehouseId:task.warehouseId,warehouseName:task.warehouseName,pendingLineCount:0}
}
async function loadOrder(){
  const order=selectedOrder.value;if(!order)return
  orderLoaded.value=false;pickRows.value=[]
  await action(async()=>{const detail=await pickingOrderDetail(order.id,order.deptId,order.warehouseId);
    if(detail.order.id!==order.id||detail.order.deptId!==order.deptId||detail.order.warehouseId!==order.warehouseId||detail.items.some(t=>t.requisitionId!==order.id))throw new Error('申领单明细归属不一致，已停止操作')
    selectedOrder.value=detail.order;pickRows.value=detail.items;orderLoaded.value=true;scanMatches.value=[];orderScanCode.value=''
  });await nextTick();orderScanInput.value?.focus()
}
async function openOrder(order:PickOrder){selectedOrder.value=order;pickRows.value=[];await loadOrder()}
async function warehouseChanged(){selectedOrder.value=null;pickRows.value=[];pickOrders.value=[];await loadTasks(1)}
async function beginScannedItem(match:PickOrderMatch,rawCode:string){
  const order=selectedOrder.value;if(!order||match.task.requisitionId!==order.id)throw new Error('扫码明细不属于当前申领单')
  if(!rawCode||rawCode!==orderScanCode.value.trim())throw new Error('扫码内容已变化，请重新识别本单明细')
  const flow=currentPickFlow();await flow.begin(match.task)
  try{await flow.addUnit(match.unit,rawCode);scanMatches.value=[];resolvedScanCode.value='';orderScanCode.value=''}finally{currentPicking.value=flow.current}
}
async function scanOrder(){
  if(loading.value||!orderScanCode.value.trim()||!selectedOrder.value)return
  await action(async()=>{const order=selectedOrder.value!,rawCode=orderScanCode.value.trim();scanMatches.value=[];resolvedScanCode.value='';const resolved=await resolveOrderScan(order.id,order.deptId,order.warehouseId,rawCode);
    if(selectedOrder.value?.id!==order.id||orderScanCode.value.trim()!==rawCode)throw new Error('扫码内容已变化，请重新识别本单明细')
    if(!resolved.matches.length)throw new Error('未找到本单待拣明细')
    if(resolved.matches.length===1)await beginScannedItem(resolved.matches[0],rawCode);else{resolvedScanCode.value=rawCode;scanMatches.value=resolved.matches}
  })
}
async function chooseMatch(match:PickOrderMatch){await action(async()=>{
  if(!scanMatches.value.includes(match)||!resolvedScanCode.value)throw new Error('候选明细已失效，请重新扫码')
  await beginScannedItem(match,resolvedScanCode.value)
})}
</script>

<template src="./AppView.html"></template>
