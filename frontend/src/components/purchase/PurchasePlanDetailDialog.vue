<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchPurchasePlanDetail, updatePurchasePlanSupplier, type PurchasePlanDetail } from '../../api/purchaseOrders'
import { useAuthStore } from '../../stores/auth'
import { useNotify } from '../../composables/useNotify'

const props = defineProps<{ planNo: string }>()
const emit = defineEmits<{ close: []; updated: [] }>()
const auth = useAuthStore()
const notify = useNotify()
const detail = ref<PurchasePlanDetail | null>(null)
const error = ref('')
const loading = ref(false)
const saving = ref(false)
let requestId = 0
watch(() => props.planNo, async (no) => {
  const current = ++requestId
  detail.value = null
  error.value = ''
  if (!no) return
  loading.value = true
  try {
    const result = await fetchPurchasePlanDetail(no)
    if (current === requestId) detail.value = result
  } catch (e) {
    if (current === requestId) error.value = e instanceof Error ? e.message : '采购计划加载失败'
  } finally {
    if (current === requestId) loading.value = false
  }
})
async function save(itemId: number, supplierId: number) {
  const no = props.planNo
  const current = requestId
  saving.value = true
  try {
    const result = await updatePurchasePlanSupplier(no, itemId, supplierId)
    if (props.planNo === no && current === requestId) detail.value = result
    notify.success('明细配送商已保存')
    emit('updated')
  } catch (e) {
    notify.error(e instanceof Error ? e.message : '配送商保存失败')
    if (props.planNo === no && current === requestId) {
      try {
        const result = await fetchPurchasePlanDetail(no)
        if (current === requestId) detail.value = result
      } catch { error.value = '配送商保存失败，请关闭后重新加载计划' }
    }
  } finally { saving.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="planNo" class="plan-detail-mask" @click.self="emit('close')">
      <section class="plan-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="purchase-plan-detail-title">
        <header><h3 id="purchase-plan-detail-title">采购计划 {{ planNo }}</h3><button class="btn" aria-label="关闭采购计划明细" @click="emit('close')">关闭</button></header>
        <p>计划支持混合耗材。最终审批通过后，按明细配送商自动生成采购订单。</p>
        <p v-if="loading">正在加载...</p><p v-if="error" role="alert">{{ error }}</p>
        <template v-if="detail">
          <p v-if="!detail.canEditSupplier">审批已开始或计划已完成，配送商不可修改。</p>
          <div class="plan-detail-scroll"><table class="master-table">
            <thead><tr><th>商品编码</th><th>商品名称</th><th>规格</th><th>数量</th><th>单位</th><th>计划估价单价</th><th>计划估价金额</th><th>配送商</th></tr></thead>
            <tbody><tr v-for="item in detail.items" :key="item.planItemId">
              <td>{{ item.productCode }}</td><td>{{ item.productName }}</td><td>{{ item.specModel }}</td><td>{{ item.quantity }}</td><td>{{ item.unit }}</td>
              <td>{{ item.unitPrice == null ? '历史未记录' : Number(item.unitPrice).toFixed(4) }}</td><td>{{ item.amount == null ? '历史未记录' : Number(item.amount).toFixed(4) }}</td>
              <td><template v-if="detail.canEditSupplier && auth.hasPermission('purchase-demand:convert-plan')"><select v-model.number="item.supplierId" :aria-label="`${item.productCode}配送商`" :disabled="saving"><option v-for="supplier in detail.suppliers" :key="supplier.supplierId" :value="supplier.supplierId">{{ supplier.supplierName }}</option></select><button class="btn" :disabled="saving" @click="save(item.planItemId,item.supplierId)">保存</button></template><span v-else>{{ item.supplierName }}</span></td>
            </tr></tbody>
          </table></div>
          <p v-if="detail.orderNos.length">已生成订单：{{ detail.orderNos.join('、') }}</p>
          <p v-else>尚未生成订单，需完成计划最终审批。</p>
        </template>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.plan-detail-mask { position: fixed; inset: 0; z-index: 2100; display: grid; place-items: center; background: #0006; padding: 24px; }
.plan-detail-dialog { background: white; border-radius: 8px; padding: 20px; width: min(1180px, 96vw); max-height: 90vh; overflow: auto; }
header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
p { margin: 12px 0; color: #475569; font-size: 13px; }
.plan-detail-scroll { overflow-x: auto; }
table { min-width: 1000px; width: 100%; }
td { white-space: nowrap; }
select { max-width: 220px; padding: 6px; border: 1px solid #cbd5e1; border-radius: 4px; }
</style>
