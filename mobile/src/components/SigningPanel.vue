<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { SigningFlow, SigningRecord } from '../signingFlow'
import { errorMessage, resolveSigningScan } from '../mobileApi'
import AppIcon from './AppIcon.vue'
const props = defineProps<{ flow: SigningFlow; initial: SigningRecord }>()
const emit = defineEmits<{ closed: [] }>()
const record = ref(props.initial)
const loading = ref(false)
const code = ref('')
const feedback = ref('')
const failed = ref(false)
const scanInput = ref<HTMLInputElement | null>(null)
const task = computed(() => record.value.detail.task)
const statuses:Record<string,string>={picked:'待签收',signed:'已签收'}
const deliveryTypes:Record<string,string>={loose:'散货',package:'定数包',quota_package:'定数包',unique_code:'唯一码耗材',high_value:'高值耗材'}
const statusName = computed(() => record.value.state==='succeeded'?'已签收':statuses[task.value.status] ?? task.value.status)
const signedAt = computed(() => record.value.state==='succeeded' ? record.value.result?.completedAt : task.value.signedAt)
const deliveryName = computed(() => deliveryTypes[task.value.deliveryType] ?? task.value.deliveryType)
const canEdit = computed(() => ['draft', 'ready'].includes(record.value.state))
const unresolved = computed(() => ['unknown', 'submitting', 'recovery_ready'].includes(record.value.state))
const checkedCount = computed(() => record.value.operation.packageIds.length + record.value.operation.traceCodeIds.length)
const complete = computed(() => record.value.detail.task.deliveryType === 'loose'
  ? record.value.operation.looseConfirmed : checkedCount.value === record.value.detail.items.length && checkedCount.value > 0)
function checked(type: string, id: number) {
  return (type === 'package' ? record.value.operation.packageIds : record.value.operation.traceCodeIds).includes(id)
}
async function run(work: () => Promise<void>) {
  loading.value = true; feedback.value = ''; failed.value = false
  try { await work() } catch (error) { feedback.value = errorMessage(error); failed.value = true }
  finally { record.value = props.flow.current ?? record.value; loading.value = false }
}
async function scan() {
  if (loading.value || !canEdit.value || !code.value.trim()) return
  await run(async () => {
    const op = record.value.operation
    const item = await resolveSigningScan(op.taskId, op.deptId, op.warehouseId, code.value)
    const added = await props.flow.addItem(item)
    feedback.value = added ? '实物码核对成功' : '此实物已核对，未重复累计'
    code.value = ''
  })
  await nextTick(); scanInput.value?.focus()
}
async function loose(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  await run(() => props.flow.confirmLoose(checked))
}
async function clear() {
  await run(async () => { await props.flow.clear(); emit('closed') })
}
</script>
<template>
  <section class="workspace signing">
    <div class="list-heading"><h2>配送整单签收</h2><span>{{ record.detail.task.documentNo }}</span></div>
    <h3 class="detail-section-title">单据信息</h3>
    <dl class="summary">
      <dt>配送单</dt><dd><b>{{ record.detail.task.documentNo }}</b></dd>
      <dt>关联申领单</dt><dd>{{task.requisitionNo || '原单未提供'}}</dd>
      <dt>单据状态</dt><dd>{{statusName}}</dd>
      <dt>配送类型</dt><dd>{{deliveryName}}</dd>
      <dt>接收科室</dt><dd>{{task.deptName}}</dd>
      <dt>来源</dt><dd>{{ record.detail.task.sourceWarehouseName }}</dd>
      <dt>目标</dt><dd>{{ record.detail.task.warehouseName }}</dd>
      <dt>创建时间</dt><dd>{{task.createdAt?.replace('T',' ') || '原单未提供'}}</dd>
      <dt v-if="signedAt">签收时间</dt><dd v-if="signedAt">{{signedAt.replace('T',' ')}}</dd>
    </dl>
    <h3 class="detail-section-title">耗材明细</h3>
    <dl class="summary">
      <dt>耗材名称</dt><dd><b>{{task.productName}}</b></dd>
      <dt>耗材编码</dt><dd>{{task.productCode}}</dd>
      <dt>规格型号</dt><dd>{{task.specModel || '目录未提供，请核对原单'}}</dd>
      <dt>生产厂家</dt><dd>{{task.manufacturerName || '目录未提供，请核对原单'}}</dd>
      <dt>配送数量</dt><dd class="quantity-strong">{{task.quantity}} {{task.unit}}</dd>
    </dl>
    <template v-if="canEdit">
      <h3 class="detail-section-title">实物核对</h3>
      <label v-if="record.detail.task.deliveryType === 'loose'" class="checkline">
        <input type="checkbox" :checked="record.operation.looseConfirmed" :disabled="loading" @change="loose" />
        已核对品种、数量和单位，实收与配送单一致
      </label>
      <template v-else>
        <p>实物核对：{{ checkedCount }} / {{ record.detail.items.length }}</p>
        <form v-if="!complete" @submit.prevent="scan">
          <label>PDA 扫码 / 手工输入<input ref="scanInput" v-model="code" :disabled="loading" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="扫描包码或唯一码，以 Enter 结束" /></label>
          <button type="submit" class="secondary" :disabled="loading || !code.trim()">核对实物码</button>
        </form>
        <p v-if="!complete" class="hint">PDA 扫码以 Enter 结束；摄像头扫码尚未接入。</p>
        <ul class="check-items"><li v-for="item in record.detail.items" :key="item.type + item.id"><span class="check-marker" :class="{'is-checked':checked(item.type,item.id)}"><AppIcon v-if="checked(item.type,item.id)" name="check" :size="16"/><span class="sr-only">{{checked(item.type,item.id)?'已核对':'未核对'}}</span></span> {{ item.code }}</li></ul>
      </template>
      <p class="hint">少货、错货或错码时请退出核对，并由电脑端处理原单。</p>
      <div v-if="record.state === 'ready'" class="review-box">
        <p>已通过服务器校验，请再次核对上述商品、数量与目标库房。</p>
        <p class="hint">确认后将执行真实入库。校验摘要有时效，过期后需重新校验。</p>
        <button class="secondary" :disabled="loading" @click="run(() => flow.review())">重新校验</button>
      </div>
      <div class="actions action-dock"><button class="secondary" :disabled="loading" @click="clear">返回待签收列表</button><button v-if="record.state==='draft'" :disabled="loading||!complete" @click="run(()=>flow.review())">校验本次签收</button><button v-else :disabled="loading" @click="run(()=>flow.submit())">确认整单签收</button></div>
    </template>
    <template v-else-if="unresolved">
      <p class="notice">提交结果尚未确认。原操作已保留，请先查询，勿另建签收记录。</p>
      <button :disabled="loading" @click="run(() => flow.query())">查询原操作结果</button>
      <button class="secondary" :disabled="loading || record.state !== 'unknown'" @click="run(() => flow.retryOriginal())">重试原操作（不创建新单）</button>
      <button class="secondary" :disabled="loading || record.state === 'submitting'" @click="run(() => flow.recover())">恢复原操作并重新核对</button>
      <div v-if="record.state === 'recovery_ready'" class="review-box">
        <p>{{ record.message }}</p>
        <p v-if="record.detail.items.length">实物核对：{{ checkedCount }} / {{ record.detail.items.length }}</p>
        <ul class="check-items"><li v-for="item in record.detail.items" :key="item.type + item.id">{{ item.code }}</li></ul>
      </div>
      <button v-if="record.state === 'recovery_ready'" :disabled="loading" @click="run(() => flow.submit())">确认恢复后的原签收</button>
    </template>
    <template v-else-if="record.state === 'succeeded'">
      <p class="success">{{ record.result?.message || '配送整单签收成功' }}</p>
      <p>业务单号：{{ record.result?.documentNo }}</p>
      <p class="hint">服务器时间：{{ record.result?.completedAt }}</p>
      <button :disabled="loading" @click="clear">完成并刷新任务</button>
    </template>
    <template v-else>
      <p class="notice">{{ record.message }}</p>
      <button class="secondary" :disabled="loading" @click="clear">返回列表并重新核对</button>
    </template>
    <p v-if="record.message && unresolved" class="hint">{{ record.message }}</p>
    <details class="operation-metadata"><summary>查看操作编号</summary><p class="operation-id">{{ record.operation.operationId }}</p></details>
    <p v-if="feedback" role="status" aria-live="polite" class="message" :class="{error: failed}">{{ feedback }}</p>
  </section>
</template>
