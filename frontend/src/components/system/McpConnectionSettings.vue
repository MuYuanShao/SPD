<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createSystemConfig, fetchSystemConfigs, updateSystemConfig } from '../../api/systemConfig'
import SectionTitle from '../common/SectionTitle.vue'
import StatusMessage from '../common/StatusMessage.vue'

const emit = defineEmits<{ saved: [] }>()
const configKey = 'integration.mcp-ui.connection'
const protocol = ref('http')
const host = ref('')
const port = ref(8080)
const phase2Enabled = ref(false)
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
  message.value = ''
  try {
    const payload = { configType: 'integration', scopeType: 'global', scopeId: 'default', configKey,
      configValue: JSON.stringify({ protocol: protocol.value, host: host.value.trim(), port: Number(port.value), phase2Enabled: phase2Enabled.value }),
      effectiveMode: 'realtime', expireTime: '', riskLevel: 'low', status: 1 }
    if (configId.value === null) await createSystemConfig(payload)
    else await updateSystemConfig(configId.value, payload)
    await load()
    message.value = 'MCP-UI对接地址已保存，下一次上传任务自动使用新地址'
    emit('saved')
  } catch (e) { error.value = e instanceof Error ? e.message : '连接配置保存失败' }
  finally { saving.value = false }
}
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
      <div class="hospital-query-actions"><button type="submit" class="btn btn-primary" :disabled="loading || saving || loadFailed">{{ saving ? '保存中…' : '保存对接地址' }}</button><button type="button" class="btn" :disabled="loading || saving" @click="load">重新加载</button></div>
    </form>
    <p class="connection-description">接口地址：{{ preview }}</p>
    <StatusMessage :message="error" tone="error" />
    <StatusMessage :message="message" tone="success" />
  </section>
</template>

<style scoped>
.connection-description { margin: 12px 0; color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.phase2-toggle { display: flex; flex-direction: row; align-items: center; gap: 8px; }
.phase2-toggle input { width: auto; }
</style>
