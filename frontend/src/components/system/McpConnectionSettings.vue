<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createSystemConfig, fetchSystemConfigs, updateSystemConfig } from '../../api/systemConfig'
import SectionTitle from '../common/SectionTitle.vue'
import StatusMessage from '../common/StatusMessage.vue'
import { useNotify } from '../../composables/useNotify'
import { fetchPhase3Status, fetchPhase3Feedback, backfillPhase3, retryPhase3, republishPhase3, type Phase3Message, type Phase3Feedback } from '../../api/mcpPhase3'

const notify = useNotify()


const emit = defineEmits<{ saved: [] }>()
const configKey = 'integration.mcp-ui.connection'
const protocol = ref('http')
const host = ref('')
const port = ref(8080)
const phase2Enabled = ref(false)
const phase3Enabled = ref(false)
const phase3Rows = ref<Phase3Message[]>([])
const phase3Feedback = ref<Phase3Feedback[]>([])
const phase3Busy = ref(false)
const configId = ref<number | null>(null)
const loading = ref(true)
const saving = ref(false)
const loadFailed = ref(false)
const message = ref('')
const error = ref('')
const preview = computed(() => `${protocol.value}://${host.value.includes(':') && !host.value.startsWith('[') ? `[${host.value}]` : host.value || 'IP地址'}:${port.value}/api`)

async function load() {
  loading.value = true
  loadFailed.value = false
  error.value = ''
  try {
    const rows = await fetchSystemConfigs({ configType: 'integration', keyword: configKey })
    const row = rows.find(r => r.configKey === configKey && r.scopeType === 'global' && r.scopeId === 'default')
    if (row) {
      const value = JSON.parse(row.configValue)
      protocol.value = value.protocol
      host.value = value.host
      port.value = value.port
      phase2Enabled.value = value.phase2Enabled === true
      phase3Enabled.value = value.phase3Enabled === true
      configId.value = row.configId
    }
  } catch (e) {
    loadFailed.value = true
    error.value = e instanceof Error ? e.message : '连接配置加载失败'
  } finally { loading.value = false }
}

async function save() {
  saving.value = true
  error.value = ''
  notify.success('')
  try {
    const payload = { configType: 'integration', scopeType: 'global', scopeId: 'default', configKey,
      configValue: JSON.stringify({ protocol: protocol.value, host: host.value.trim(), port: Number(port.value), phase2Enabled: phase2Enabled.value, phase3Enabled: phase3Enabled.value }),
      effectiveMode: 'realtime', expireTime: '', riskLevel: 'low', status: 1 }
    if (configId.value === null) await createSystemConfig(payload)
    else await updateSystemConfig(configId.value, payload)
    await load()
    notify.success('MCP-UI对接地址已保存，下一次上传任务自动使用新地址')
    emit('saved')
  } catch (e) { error.value = e instanceof Error ? e.message : '连接配置保存失败' }
  finally { saving.value = false }
}
async function refreshPhase3() {
  phase3Busy.value = true
  try { const [messages, feedback] = await Promise.all([fetchPhase3Status(), fetchPhase3Feedback()]); phase3Rows.value = messages.rows; phase3Feedback.value = feedback.rows }
  catch (e) { notify.error(e instanceof Error ? e.message : '第三期同步状态读取失败') }
  finally { phase3Busy.value = false }
}
async function runBackfill() {
  phase3Busy.value = true
  try { const result = await backfillPhase3(); notify.success(`本批回填计划 ${result.plans} 条、结算 ${result.settlements} 条；仍有历史数据时可继续回填`); await refreshPhase3() }
  catch (e) { notify.error(e instanceof Error ? e.message : '历史回填失败') }
  finally { phase3Busy.value = false }
}
async function retryMessage(id: string) {
  phase3Busy.value = true
  try { await retryPhase3(id); await refreshPhase3() }
  catch (e) { notify.error(e instanceof Error ? e.message : '消息重试失败') }
  finally { phase3Busy.value = false }
}
async function republishMessage(row: Phase3Message) {
  if (!await notify.confirm('将最新完整快照以新版本发送至当前配置的医院实例，请先核对目标地址和币种税口径。')) return
  phase3Busy.value = true
  try { await republishPhase3(row.documentType, row.sourceId); notify.success('最新快照已重新入队'); await refreshPhase3() }
  catch (e) { notify.error(e instanceof Error ? e.message : '重新发布失败') }
  finally { phase3Busy.value = false }
}
const phase3State = (state: string) => ({ pending: '等待发送', sending: '发送中', processed: '已接收', ignored: '旧版本已忽略', failed: '失败待重试', superseded: '已由新版本替代' }[state] || state)
onMounted(load)
</script>

<template>
  <section class="hospital-catalog-panel">
    <SectionTitle title="MCP-UI对接地址" />
    <p class="connection-description">设置MCP-UI服务器IP地址或域名及后端端口。保存后自动生成接口地址，无需输入接口路径。</p>
    <form class="hospital-query-grid" @submit.prevent="save">
      <label><span>连接协议</span><select v-model="protocol" :disabled="loading || saving"><option value="http">HTTP</option><option value="https">HTTPS</option></select></label>
      <label><span>MCP-UI IP地址／域名</span><input v-model.trim="host" required maxlength="253" placeholder="例如 192.168.1.20" :disabled="loading || saving" /></label>
      <label><span>端口</span><input v-model.number="port" type="number" min="1" max="65535" step="1" required :disabled="loading || saving" /></label>
      <label class="phase2-toggle"><input v-model="phase2Enabled" type="checkbox" :disabled="loading || saving" /><span>启用第二期收货、入库和批次库存同步</span></label>
      <label class="phase2-toggle"><input v-model="phase3Enabled" type="checkbox" :disabled="loading || saving" /><span>启用第三期医院采购计划与结算同步</span></label>
      <div class="hospital-query-actions"><button type="submit" class="btn btn-primary" :disabled="loading || saving || loadFailed">{{ saving ? '保存中…' : '保存对接地址' }}</button><button type="button" class="btn" :disabled="loading || saving" @click="load">重新加载</button></div>
    </form>
    <p class="connection-description">接口地址：{{ preview }}</p>
    <details class="phase3-status"><summary>医院计划与结算同步状态</summary>
      <p class="connection-description">第三期默认关闭。启用前需在 MCP-UI 服务器启用第三期，并明确配置币种、税口径和接收账户。每批最多回填100张计划及100张结算。</p>
      <div class="hospital-query-actions"><button type="button" class="btn" :disabled="phase3Busy" @click="refreshPhase3">读取同步与反馈状态</button><button type="button" class="btn" :disabled="phase3Busy || !phase3Enabled" @click="runBackfill">回填历史计划与结算</button></div>
      <div class="phase3-table"><table class="master-table"><thead><tr><th>类型</th><th>来源编号</th><th>版本</th><th>状态</th><th>处理说明</th><th>操作</th></tr></thead><tbody><tr v-for="row in phase3Rows" :key="row.messageId"><td>{{ row.documentType === 'purchase_plan' ? '采购计划' : '结算单' }}</td><td>{{ row.sourceId }}</td><td>{{ row.revision }}</td><td>{{ phase3State(row.state) }}</td><td>{{ row.message || '等待后台处理' }}</td><td><button type="button" class="btn" :disabled="row.state !== 'failed' || phase3Busy" @click="retryMessage(row.messageId)">重试</button><button type="button" class="btn" :disabled="phase3Busy" @click="republishMessage(row)">重新发布最新快照</button></td></tr><tr v-if="!phase3Rows.length"><td colspan="6">暂无已读取的同步消息。</td></tr></tbody></table></div>
      <h4>供应商协同反馈</h4><div class="phase3-table"><table class="master-table"><thead><tr><th>来源结算</th><th>版本</th><th>供应商</th><th>反馈</th><th>接收时间</th></tr></thead><tbody><tr v-for="row in phase3Feedback" :key="row.messageId"><td>{{ row.settlementId }}</td><td>{{ row.revision }}</td><td>{{ row.supplierCode }}</td><td>{{ ({ received: '已接收', disputed: '存在差异', withdrawn: '撤回反馈' } as Record<string, string>)[row.feedbackType] || row.feedbackType }}</td><td>{{ row.receivedAt }}</td></tr><tr v-if="!phase3Feedback.length"><td colspan="5">暂无已读取的协同反馈。</td></tr></tbody></table></div>
    </details>
    <StatusMessage :message="error" tone="error" />
    <StatusMessage :message="message" tone="success" />
  </section>
</template>

<style scoped>
.connection-description { margin: 12px 0; color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.phase2-toggle { display: flex; flex-direction: row; align-items: center; gap: 8px; }
.phase2-toggle input { width: auto; }
.phase3-status { margin-top: 16px; }
.phase3-status summary { cursor: pointer; font-weight: 600; }
.phase3-table { overflow-x: auto; margin: 12px 0; }
</style>
