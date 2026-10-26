<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { PickingFlow, PickingRecord } from '../pickingFlow'
import { errorMessage, resolvePickingScan } from '../mobileApi'
const props = defineProps<{flow: PickingFlow; initial: PickingRecord}>()
const emit = defineEmits<{closed: []}>()
const record = ref(props.initial)
const loading = ref(false)
const code = ref('')
const quantity = ref(props.initial.operation.quantity ?? '')
const feedback = ref('')
const failed = ref(false)
const input = ref<HTMLInputElement | null>(null)
const quantityInput=ref<HTMLInputElement|null>(null)
const loose = computed(() => record.value.operation.kind === 'PICK_LOOSE')
const unresolved = computed(() => ['unknown', 'submitting', 'recovery_ready'].includes(record.value.state))
const kindName = computed(() => loose.value ? '散货' : record.value.operation.kind === 'PICK_PACKAGE' ? '定数包' : '高值唯一码')
const canReview = computed(() => loose.value
  ? !!record.value.operation.rawCode && /^\d{1,14}$/.test(quantity.value.trim()) && Number(quantity.value) > 0
  : record.value.detail.units.length > 0)
onMounted(async()=>{await nextTick();if(record.value.state==='draft'){if(loose.value&&record.value.operation.rawCode)quantityInput.value?.focus();else input.value?.focus()}})
async function run(work: () => Promise<void>) {
  loading.value = true; feedback.value = ''; failed.value = false
  try { await work() } catch (error) { feedback.value = errorMessage(error); failed.value = true }
  finally { record.value = props.flow.current ?? record.value; loading.value = false }
}
async function scan() {
  if (loading.value || record.value.state !== 'draft' || !code.value.trim()) return
  await run(async () => {
    const op = record.value.operation
    const unit = await resolvePickingScan(op.taskId, op.deptId, op.warehouseId, code.value)
    const added = await props.flow.addUnit(unit, code.value)
    feedback.value = added ? '物料核对成功' : '重复实物码，未重复累计'
    code.value = ''
  })
  await nextTick(); input.value?.focus()
}
async function review() {
  await run(async () => {
    if (loose.value) await props.flow.setQuantity(quantity.value)
    await props.flow.review()
  })
}
async function close() { await run(async () => {await props.flow.clear(); emit('closed')}) }
</script>
<template>
  <section class="workspace">
    <div class="list-heading"><h2>{{ kindName }}拣选</h2><span>{{ record.detail.task.documentNo }}</span></div>
    <dl class="summary">
      <dt>申领单</dt><dd><b>{{ record.detail.task.documentNo }}</b></dd>
      <dt>明细编号</dt><dd>#{{ record.detail.task.id }} · {{ kindName }}</dd>
      <dt>来源</dt><dd>{{ record.detail.task.sourceWarehouseName }} → {{ record.detail.task.warehouseName }}</dd>
      <dt>当前明细</dt><dd>{{ kindName }}</dd>
    </dl>
    <dl class="summary">
      <dt>物资名称</dt><dd><b>{{ record.detail.task.productName }}</b></dd>
      <dt>商品编码</dt><dd>{{ record.detail.task.productCode }}</dd>
      <dt>规格型号</dt><dd>{{ record.detail.task.specModel || '目录未提供，请核对原单' }}</dd>
      <template v-if="record.detail.task.manufacturerName"><dt>生产厂家</dt><dd>{{record.detail.task.manufacturerName}}</dd></template>
      <dt>申请总量</dt><dd>{{ record.detail.task.quantity }} {{ record.detail.task.unit }}</dd>
      <dt>已拣 / 待拣</dt><dd>{{ record.detail.task.pickedQuantity }} / {{ record.detail.task.remainingQuantity }} {{ record.detail.task.unit }}</dd>
      <dt>已扫数量</dt><dd class="quantity-strong">{{ loose ? (quantity || '0') : record.detail.units.reduce((sum,unit)=>sum+unit.quantity,0) }} {{ record.detail.task.unit }}</dd>
    </dl>
    <template v-if="record.state === 'draft'">
      <p v-if="record.operation.kind === 'PICK_PACKAGE'" class="hint">每个实际包 {{ record.detail.task.packageQuantity }} {{ record.detail.task.unit }}，数量由后端按实际包码合计。</p>
      <form @submit.prevent="scan">
        <label>PDA 扫码 / 手工输入<input ref="input" v-model="code" :disabled="loading" autocomplete="off" autocapitalize="none" spellcheck="false" :placeholder="loose ? '扫描物料编码或商品 UDI' : '扫描实际包码或唯一码，以 Enter 结束'" /></label>
        <button type="submit" class="secondary" :disabled="loading || !code.trim()">核对拣选物料</button>
      </form>
      <label v-if="loose">本次数量（{{ record.detail.task.unit }}）<input ref="quantityInput" v-model="quantity" :disabled="loading" inputmode="numeric" autocomplete="off" placeholder="请输入正整数，按基本单位拣选" /></label>
      <ul class="check-items"><li v-for="unit in record.detail.units" :key="unit.type + unit.id">{{ unit.code }}<span v-if="!loose"> · {{ unit.quantity }} {{ record.detail.task.unit }}</span></li></ul>
      <p class="hint">{{ loose ? '物料码核对后，按基本单位填写本次数量。重复商品扫码不自动加量。' : `已核对 ${record.detail.units.length} 个实物码，可在剩余申请量内分次拣选。` }}</p>
      <div class="actions action-dock"><button class="secondary" :disabled="loading" @click="close">返回本单明细</button><button :disabled="loading || !canReview" @click="review">校验本次拣选</button></div>
    </template>
    <template v-else-if="record.state === 'ready'">
      <div class="review-box">
        <h2>本次拣选摘要</h2>
        <p>{{ record.detail.quantity }} {{ record.detail.task.unit }}<span v-if="!loose"> · {{ record.detail.units.length }} 个实物码</span></p>
        <p v-if="loose && record.detail.availableQuantity != null" class="hint">校验时可用散货：{{ record.detail.availableQuantity }} {{ record.detail.task.unit }}</p>
        <p class="hint">确认后生成配送单，请核对本次物料码和数量。</p>
        <button :disabled="loading" @click="run(() => flow.submit())">确认本次拣选</button>
        <button class="secondary" :disabled="loading" @click="run(() => flow.reopen())">返回修改</button>
      </div>
    </template>
    <template v-else-if="unresolved">
      <p class="notice">提交结果尚未确认，原编号和内容已保留，不能另建操作。</p>
      <button :disabled="loading" @click="run(() => flow.query())">查询原拣选结果</button>
      <button class="secondary" :disabled="loading || record.state !== 'unknown'" @click="run(() => flow.retryOriginal())">重试原拣选操作</button>
      <button class="secondary" :disabled="loading || record.state === 'submitting'" @click="run(() => flow.recover())">恢复原操作并重新核对</button>
      <div v-if="record.state === 'recovery_ready'" class="review-box">
        <p>{{ record.message }}</p>
        <p>本次拣选：{{ record.detail.quantity }} {{ record.detail.task.unit }}</p>
        <ul class="check-items"><li v-for="unit in record.detail.units" :key="unit.type + unit.id">{{ unit.code }}</li></ul>
      </div>
      <button v-if="record.state === 'recovery_ready'" :disabled="loading" @click="run(() => flow.submit())">确认恢复后的原拣选</button>
    </template>
    <template v-else-if="record.state === 'succeeded'">
      <p class="success">本次拣选成功</p>
      <p>配送单号：{{ record.result?.documentNo }}</p>
      <p>剩余待拣：{{ record.result?.remainingQuantity }} {{ record.detail.task.unit }}</p>
      <button :disabled="loading" @click="close">完成并继续本单拣选</button>
    </template>
    <template v-else>
      <p class="notice">{{ record.message }}</p>
      <button class="secondary" :disabled="loading" @click="close">返回列表重新核对</button>
    </template>
    <details class="operation-metadata"><summary>查看操作编号</summary><p class="operation-id">{{ record.operation.operationId }}</p></details>
    <p v-if="feedback" role="status" aria-live="polite" class="message" :class="{error: failed}">{{ feedback }}</p>
  </section>
</template>
