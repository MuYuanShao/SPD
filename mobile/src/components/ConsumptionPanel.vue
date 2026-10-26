<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ConsumptionFlow, ConsumptionRecord } from '../consumptionFlow'
import { errorMessage } from '../mobileApi'
import AppIcon from './AppIcon.vue'
const props=defineProps<{flow:ConsumptionFlow;initial:ConsumptionRecord}>()
const emit=defineEmits<{closed:[]}>()
const record=ref(props.initial)
const loading=ref(false)
const feedback=ref('')
const failed=ref(false)
const editable=computed(()=>['draft','ready'].includes(record.value.state))
const unresolved=computed(()=>['submitting','unknown','recovery_ready'].includes(record.value.state))
async function run(work:()=>Promise<void>){
  loading.value=true;feedback.value='';failed.value=false
  try{await work()}catch(error){feedback.value=errorMessage(error);failed.value=true}
  finally{record.value=props.flow.current??record.value;loading.value=false}
}
async function confirmed(event:Event){const value=(event.target as HTMLInputElement).checked;await run(()=>props.flow.confirmWhole(value))}
async function close(){await run(async()=>{await props.flow.clear();emit('closed')})}
</script>
<template>
  <section class="workspace">
    <div class="scan-preview"><AppIcon name="scan" :size="64"/><b>实际定数包整包消耗</b><small>PDA / 手工识别 · 摄像头尚未接入</small></div>
    <dl class="summary">
      <dt>包码</dt><dd>{{ record.detail.pack.labelNo }}</dd>
      <dt>商品</dt><dd>{{ record.detail.pack.productName }}</dd>
      <dt>科室</dt><dd>{{ record.detail.pack.deptName }}</dd>
      <dt>库房</dt><dd>{{ record.detail.pack.warehouseName }}</dd>
      <dt>本次</dt><dd>1 包 · {{ record.detail.pack.quantity }} {{ record.detail.pack.unit }}</dd>
    </dl>
    <template v-if="editable">
      <p class="notice scan-warning"><AppIcon name="info" :size="18"/>高值耗材不支持此操作，不支持拆包消耗</p>
      <label class="checkline"><input type="checkbox" :checked="record.operation.wholePackageConfirmed" :disabled="loading" @change="confirmed" />已核对包码和包内数量，确认整包消耗</label>
      <button v-if="record.state==='draft'" :disabled="loading||!record.operation.wholePackageConfirmed" @click="run(()=>flow.review())">校验整包消耗</button>
      <div v-else class="review-box">
        <p>服务器校验通过。确认后将扣减 {{ record.detail.pack.quantity }} {{ record.detail.pack.unit }} 并生成消耗单。</p>
        <button :disabled="loading" @click="run(()=>flow.submit())">确认整包消耗</button>
        <button class="secondary" :disabled="loading" @click="run(()=>flow.review())">重新校验</button>
      </div>
      <button class="secondary" :disabled="loading" @click="close">返回扫码</button>
    </template>
    <template v-else-if="unresolved">
      <p class="notice">消耗结果尚未确认。原操作已保留，请查询原结果，不要重新扫描创建另一操作。</p>
      <button :disabled="loading" @click="run(()=>flow.query())">查询原消耗结果</button>
      <button class="secondary" :disabled="loading||record.state!=='unknown'" @click="run(()=>flow.retryOriginal())">重试原整包消耗</button>
      <button class="secondary" :disabled="loading||record.state==='submitting'" @click="run(()=>flow.recover())">恢复原操作并重新核对</button>
      <p v-if="record.state==='recovery_ready'" class="hint">{{ record.message }}</p>
      <button v-if="record.state==='recovery_ready'" :disabled="loading" @click="run(()=>flow.submit())">确认恢复后的原消耗</button>
    </template>
    <template v-else-if="record.state==='succeeded'">
      <p class="success">定数包整包消耗成功</p>
      <p>消耗单号：{{ record.result?.documentNo }}</p>
      <p>消耗数量：1 包 · {{ record.result?.quantity ?? record.detail.pack.quantity }} {{ record.detail.pack.unit }}</p>
      <p v-if="record.result?.amount!=null">后台金额：{{ record.result.amount }} 元</p>
      <button :disabled="loading" @click="close">完成并继续扫码</button>
    </template>
    <template v-else>
      <p class="notice">{{ record.message }}</p>
      <button class="secondary" :disabled="loading" @click="close">返回扫码重新核对</button>
    </template>
    <details class="operation-metadata"><summary>查看操作编号</summary><p class="operation-id">{{ record.operation.operationId }}</p></details>
    <p v-if="feedback" role="status" aria-live="polite" class="message" :class="{error:failed}">{{ feedback }}</p>
  </section>
</template>
