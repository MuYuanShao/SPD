<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ClipboardCheck, FileCheck2, RefreshCw, Search } from '@lucide/vue'
import PaginationControls from '../common/PaginationControls.vue'
import {
  bindHighValuePatient,
  fetchClosureList,
  receiveHighValueBillingCallback
} from '../../api/operationalClosure'
import { formatApiErrorText, formatStatusText } from '../../utils/chineseDisplay'

const loading = ref(false)
const actionError = ref('')
const actionOk = ref('')
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const action = reactive<{ busy: 'bind' | 'charge' | '' }>({ busy: '' })

const form = reactive({
  patientNo: 'MZ-0001',
  patientNameMasked: '',
  uniqueCodes: '',
  externalChargeNo: ''
})

function notifyError(text: string) { actionError.value = text }
function notifyOk(text: string) { actionOk.value = text; actionError.value = '' }
function clearMessages() { actionError.value = ''; actionOk.value = '' }

async function loadData() {
  loading.value = true
  try {
    const result = await fetchClosureList('high-value', {
      page: String(currentPage.value),
      size: String(pageSize.value)
    })
    rows.value = result.rows ?? []
    total.value = result.total ?? 0
  } catch (error) {
    notifyError(formatApiErrorText(error, '高值计费记录加载失败'))
  } finally {
    loading.value = false
  }
}

async function bindPatient() {
  if (!form.patientNo.trim() || !form.uniqueCodes.trim()) {
    notifyError('请填写患者号和高值唯一码')
    return
  }
  action.busy = 'bind'
  try {
    const result = await bindHighValuePatient({
      patientNo: form.patientNo.trim(),
      patientNameMasked: form.patientNameMasked.trim(),
      uniqueCodes: form.uniqueCodes.trim()
    })
    const code = (result as Record<string, unknown>)?.uniqueCode || form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    notifyOk(`唯一码 ${code} 已绑定患者 ${form.patientNo}`)
    await loadData()
  } catch (error) {
    notifyError(formatApiErrorText(error, '高值患者绑定失败'))
  } finally {
    action.busy = ''
  }
}

async function submitCharge() {
  if (!form.uniqueCodes.trim() || !form.externalChargeNo.trim()) {
    notifyError('请填写高值唯一码和外部计费流水号')
    return
  }
  action.busy = 'charge'
  try {
    const uniqueCode = form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    const result = await receiveHighValueBillingCallback({
      patientNo: form.patientNo.trim(),
      patientNameMasked: form.patientNameMasked.trim(),
      uniqueCode,
      quantity: 1,
      externalChargeNo: form.externalChargeNo.trim()
    })
    const chargeNo = (result as Record<string, unknown>)?.chargeNo || (result as Record<string, unknown>)?.recordNo || ''
    notifyOk(`高值耗材已按唯一码计费扣减：${chargeNo}`)
    form.externalChargeNo = ''
    await loadData()
  } catch (error) {
    notifyError(formatApiErrorText(error, '高值计费回传失败'))
  } finally {
    action.busy = ''
  }
}

function changePage(value: number) { currentPage.value = value; loadData() }
function changeSize(value: number) {
  if (value === pageSize.value) return
  pageSize.value = value
  currentPage.value = 1
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="hv-ops-panel" aria-label="高值耗材计费操作">
    <p v-if="actionError" class="inline-message is-error" role="alert">{{ actionError }}</p>
    <p v-if="actionOk" class="inline-message is-ok" role="status">{{ actionOk }}</p>

    <section class="hv-ops-card">
      <div class="section-title">
        <ClipboardCheck :size="20" />
        <div>
          <h3>高值患者绑定</h3>
          <p class="hv-ops-section-description">扫描唯一码并指定患者号，把高值耗材与具体患者绑定后再回传计费</p>
        </div>
      </div>
      <div class="hv-ops-form">
        <label><span>患者号</span><input v-model.trim="form.patientNo" placeholder="门诊 / 住院号" /></label>
        <label class="wide-field"><span>高值唯一码</span>
          <input v-model.trim="form.uniqueCodes" placeholder="扫描或输入唯一码；多码用逗号分隔" />
        </label>
        <label><span>患者姓名</span><input v-model.trim="form.patientNameMasked" placeholder="脱敏姓名" /></label>
        <div class="hv-ops-actions">
          <button class="btn" type="button" :disabled="action.busy === 'bind' || loading" @click="bindPatient">
            <ClipboardCheck :size="18" />
            绑定患者
          </button>
        </div>
      </div>
    </section>

    <section class="hv-ops-card">
      <div class="section-title">
        <FileCheck2 :size="20" />
        <div>
          <h3>HIS / 麻醉 / 手麻计费回传</h3>
          <p class="hv-ops-section-description">收到 HIS、AIMS、手麻系统计费流水后，按唯一码扣减高值库存并写入计费事件</p>
        </div>
      </div>
      <div class="hv-ops-form">
        <label class="wide-field"><span>外部计费流水</span>
          <input v-model.trim="form.externalChargeNo" placeholder="HIS / AIMS / 手麻 计费流水号" />
        </label>
        <div class="hv-ops-actions">
          <button class="btn btn-primary" type="button" :disabled="action.busy === 'charge' || loading" @click="submitCharge">
            <FileCheck2 :size="18" />
            {{ action.busy === 'charge' ? '回传中…' : '计费回传并扣减' }}
          </button>
          <button class="btn" type="button" :disabled="loading" @click="loadData">
            <RefreshCw :size="16" />
            刷新列表
          </button>
        </div>
      </div>
    </section>

    <section class="hv-ops-card">
      <div class="section-title">
        <Search :size="20" />
        <h3>高值计费记录</h3>
      </div>
      <div class="table-scroll hv-ops-table-scroll">
        <table class="master-table purchase-table">
          <thead>
            <tr>
              <th>计费单号</th>
              <th>患者</th>
              <th>唯一码</th>
              <th>商品</th>
              <th>数量 / 金额</th>
              <th>状态</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="approval-empty">正在加载高值计费记录…</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="7" class="approval-empty">暂无高值计费记录</td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.bizNo || row.chargeNo || row.id)">
              <td>{{ row.bizNo || row.chargeNo || row.recordNo || '-' }}</td>
              <td>
                <strong>{{ row.patientNo || row.deptName || '-' }}</strong>
                <span v-if="row.patientNameMasked" class="muted-cell">{{ row.patientNameMasked }}</span>
              </td>
              <td>
                <strong>{{ row.uniqueCode || row.uid || '-' }}</strong>
                <span v-if="row.udiCode" class="muted-cell">UDI: {{ row.udiCode }}</span>
              </td>
              <td>
                <strong>{{ row.productName || '-' }}</strong>
                <span class="muted-cell">{{ row.productCode || '' }}</span>
              </td>
              <td>
                <strong>{{ row.quantity ?? 1 }}</strong>
                <span class="muted-cell">{{ row.amount ? `¥ ${Number(row.amount).toFixed(2)}` : '' }}</span>
              </td>
              <td>
                <span class="status-badge" :class="row.status === 'pending_confirm' ? 'pending' : 'enabled'">
                  {{ formatStatusText(row.status) }}
                </span>
              </td>
              <td>{{ row.createTime || row.settlementTime || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="currentPage"
        :size="pageSize"
        :total="total"
        :loading="loading"
        @change-page="changePage"
        @change-size="changeSize"
      />
    </section>
  </div>
</template>

<style scoped>
.hv-ops-panel {
  display: grid;
  gap: 14px;
}

.hv-ops-card {
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-md, 10px);
  background: var(--md-bg-card, #fff);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
  padding: 18px 20px 20px;
  display: grid;
  gap: 14px;
}

.hv-ops-card .section-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hv-ops-card .section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--spd-text-heading, #14213a);
}

.hv-ops-section-description {
  margin: 2px 0 0;
  color: var(--md-text-secondary, #607285);
  font-size: 12px;
  font-weight: 400;
}

.hv-ops-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px 14px;
  align-items: end;
}

.hv-ops-form label {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--md-text-secondary, #607285);
  font-size: 12px;
  font-weight: 600;
}

.hv-ops-form input {
  min-height: 32px;
  height: 32px;
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  padding: 0 11px;
  background: var(--md-bg-card, #fff);
  color: var(--spd-text-primary, #111c2e);
  font-size: 13px;
}

.hv-ops-form .wide-field {
  grid-column: span 2;
}

.hv-ops-actions {
  grid-column: span 4;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed var(--md-border-soft, var(--spd-border-subtle));
  padding-top: 12px;
}

.hv-ops-table-scroll {
  max-height: 60vh;
  overflow: auto;
}

.inline-message {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, #fff);
  color: var(--spd-text-primary, #111c2e);
  font-size: 13px;
}

.inline-message.is-error {
  border-color: #f1c2bf;
  background: #fff5f4;
  color: #b42318;
}

.inline-message.is-ok {
  border-color: #b7e4c7;
  background: #ecfdf3;
  color: #067647;
}

.muted-cell {
  display: block;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
  font-weight: 400;
}

.approval-empty {
  text-align: center;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  padding: 18px 12px;
}

@media (max-width: 1080px) {
  .hv-ops-form { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
  .hv-ops-form .wide-field { grid-column: span 2; }
  .hv-ops-actions { grid-column: span 2; }
}

@media (max-width: 720px) {
  .hv-ops-form { grid-template-columns: 1fr; }
  .hv-ops-form .wide-field { grid-column: span 1; }
  .hv-ops-actions { grid-column: span 1; }
}
</style>
