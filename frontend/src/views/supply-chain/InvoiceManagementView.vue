<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '../../components/common/PageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import { createInvoice, fetchInvoiceOptions, fetchInvoices, verifyInvoice, type InvoiceRow } from '../../api/invoices'
import { formatStatusText } from '../../utils/chineseDisplay'

const loading = ref(false)
const saving = ref(false)
const message = ref('')
const rows = ref<InvoiceRow[]>([])
const suppliers = ref<Array<{ supplierId: number; supplierName: string }>>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const query = reactive({ keyword: '', status: '' })
const form = reactive({ supplierId: 0, invoiceCode: '', invoiceNumber: '', invoiceDate: new Date().toISOString().slice(0, 10), amount: 0, taxAmount: 0, remark: '' })

async function load() {
  loading.value = true
  message.value = ''
  try {
    const [result, options] = await Promise.all([
      fetchInvoices({ page: String(page.value), size: String(size.value), keyword: query.keyword, status: query.status }),
      fetchInvoiceOptions()
    ])
    rows.value = result.rows
    total.value = result.total
    suppliers.value = options
    if (!form.supplierId && options[0]) form.supplierId = options[0].supplierId
  } catch (error) { message.value = error instanceof Error ? error.message : '发票数据加载失败' }
  finally { loading.value = false }
}

async function submit() {
  if (!form.supplierId || !form.invoiceCode.trim() || !form.invoiceNumber.trim() || !form.invoiceDate) { message.value = '请完整填写供应商、发票代码、号码和日期'; return }
  saving.value = true
  try {
    const result = await createInvoice({ ...form, invoiceCode: form.invoiceCode.trim(), invoiceNumber: form.invoiceNumber.trim() })
    message.value = `发票 ${result.invoiceNo} 已登记`
    form.invoiceCode = ''; form.invoiceNumber = ''; form.amount = 0; form.taxAmount = 0; form.remark = ''
    page.value = 1
    await load()
  } catch (error) { message.value = error instanceof Error ? error.message : '发票登记失败' }
  finally { saving.value = false }
}

async function verify(row: InvoiceRow) {
  try { await verifyInvoice(row.invoiceNo); message.value = `发票 ${row.invoiceNo} 已核验`; await load() }
  catch (error) { message.value = error instanceof Error ? error.message : '核验失败' }
}

function changePage(value: number) { page.value = value; load() }
function changeSize(value: number) { size.value = value; page.value = 1; load() }
onMounted(load)
</script>

<template>
  <section class="invoice-page">
    <PageHeader eyebrow="结算与财务" title="发票管理" description="供应商发票登记、核验及结算关联均实时写入 MySQL。">
      <template #actions><button class="btn" type="button" :disabled="loading" @click="load">刷新</button></template>
    </PageHeader>
    <p v-if="message" class="inline-message">{{ message }}</p>

    <section class="invoice-panel">
      <h3>登记供应商发票</h3>
      <form class="invoice-form" @submit.prevent="submit">
        <label><span>供应商</span><select v-model.number="form.supplierId"><option v-for="item in suppliers" :key="item.supplierId" :value="item.supplierId">{{ item.supplierName }}</option></select></label>
        <label><span>发票代码</span><input v-model="form.invoiceCode" /></label>
        <label><span>发票号码</span><input v-model="form.invoiceNumber" /></label>
        <label><span>开票日期</span><input v-model="form.invoiceDate" type="date" /></label>
        <label><span>含税金额</span><input v-model.number="form.amount" type="number" min="0" step="0.01" /></label>
        <label><span>税额</span><input v-model.number="form.taxAmount" type="number" min="0" step="0.01" /></label>
        <label class="wide"><span>备注</span><input v-model="form.remark" /></label>
        <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中...' : '登记发票' }}</button>
      </form>
    </section>

    <section class="invoice-panel">
      <div class="invoice-toolbar">
        <input v-model="query.keyword" placeholder="发票编号 / 代码 / 号码 / 供应商" @keyup.enter="page = 1; load()" />
        <select v-model="query.status"><option value="">全部状态</option><option value="pending_verification">待核验</option><option value="verified">已核验</option></select>
        <button class="btn" type="button" @click="page = 1; load()">查询</button>
      </div>
      <div class="table-scroll">
        <table>
          <thead><tr><th>SPD发票编号</th><th>供应商</th><th>发票代码</th><th>发票号码</th><th>开票日期</th><th>含税金额</th><th>税额</th><th>结算单</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.invoiceId"><td>{{ row.invoiceNo }}</td><td>{{ row.supplierName }}</td><td>{{ row.invoiceCode }}</td><td>{{ row.invoiceNumber }}</td><td>{{ row.invoiceDate }}</td><td>{{ row.amount }}</td><td>{{ row.taxAmount }}</td><td>{{ row.settlementNo || '-' }}</td><td>{{ formatStatusText(row.status) }}</td><td><button v-if="row.status === 'pending_verification'" class="btn btn-sm" type="button" @click="verify(row)">核验</button><span v-else>已核验</span></td></tr>
            <tr v-if="!loading && !rows.length"><td colspan="10" class="empty-cell">暂无发票数据</td></tr>
          </tbody>
        </table>
      </div>
      <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
    </section>
  </section>
</template>

<style scoped>
.invoice-page { display:grid; gap:16px; }
.invoice-panel { border:1px solid var(--line); border-radius:8px; background:var(--surface); padding:20px; }
.invoice-panel h3 { margin:0 0 16px; }
.invoice-form { display:grid; grid-template-columns:repeat(4,minmax(150px,1fr)); gap:14px; align-items:end; }
.invoice-form label { display:grid; gap:6px; color:var(--text-muted); }
.invoice-form input,.invoice-form select,.invoice-toolbar input,.invoice-toolbar select { min-height:38px; border:1px solid var(--line); border-radius:6px; padding:0 10px; background:#fff; }
.invoice-form .wide { grid-column:span 2; }
.invoice-toolbar { display:flex; gap:10px; margin-bottom:14px; }
.invoice-toolbar input { min-width:320px; }
.table-scroll { overflow:auto; }
table { width:100%; min-width:1200px; border-collapse:collapse; }
th,td { padding:11px 10px; border-bottom:1px solid var(--line); text-align:left; white-space:nowrap; }
th { color:var(--text-muted); font-weight:600; }
.empty-cell { padding:36px; text-align:center; color:var(--text-muted); }
@media (max-width:1100px){.invoice-form{grid-template-columns:repeat(2,minmax(150px,1fr));}}
</style>
