<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle2, Save } from '@lucide/vue'
import { batchUpdateHospitalProducts } from '../../api/masterData'
import StatusMessage from '../../components/common/StatusMessage.vue'

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const error = ref('')

const productCodes = computed(() =>
  String(route.query.codes ?? '')
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean)
)

const form = reactive({
  volumeBased: '',
  domestic: '',
  purchasePrice: '',
  unit: '',
  registrationNo: '',
  contractCode: '',
  firstCategory: '',
  secondCategory: '',
  thirdCategory: '',
  chargeable: ''
})

function hasChangeValue() {
  return Object.values(form).some((value) => String(value).trim() !== '')
}

function normalizedPrice() {
  return form.purchasePrice === '' ? null : Number(form.purchasePrice)
}

async function submitBatchEdit() {
  error.value = ''
  if (!productCodes.value.length) {
    error.value = '请选择需要批量修改的商品'
    return
  }
  if (!hasChangeValue()) {
    error.value = '请至少填写一个批量修改字段'
    return
  }

  saving.value = true
  try {
    await batchUpdateHospitalProducts({
      productCodes: productCodes.value,
      volumeBased: form.volumeBased,
      domestic: form.domestic,
      purchasePrice: normalizedPrice(),
      unit: form.unit,
      registrationNo: form.registrationNo,
      contractCode: form.contractCode,
      firstCategory: form.firstCategory,
      secondCategory: form.secondCategory,
      thirdCategory: form.thirdCategory,
      chargeable: form.chargeable
    })
    router.push({ name: 'master-data-list', params: { code: 'hospital-product-catalog' } })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '批量修改失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="product-form-page">
    <button class="btn" type="button" @click="router.back()">
      <ArrowLeft :size="17" />
      返回医院目录
    </button>

    <header class="approval-detail-header">
      <div>
        <p>医院目录</p>
        <h2>批量修改</h2>
        <span>对已选择的 {{ productCodes.length }} 条耗材统一更新字段。</span>
      </div>
    </header>

    <form class="product-form-card" @submit.prevent="submitBatchEdit">
      <section>
        <div class="section-title">
          <CheckCircle2 :size="20" />
          <h3>批量修改字段</h3>
        </div>
        <div class="product-form-grid batch-edit-fields">
          <label>
            <span>是否带量</span>
            <select v-model="form.volumeBased">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
          <label>
            <span>是否国产</span>
            <select v-model="form.domestic">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
          <label>
            <span>单价</span>
            <input v-model="form.purchasePrice" type="number" min="0" step="0.0001" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>单位</span>
            <input v-model.trim="form.unit" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>注册证号</span>
            <input v-model.trim="form.registrationNo" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>合同编码</span>
            <input v-model.trim="form.contractCode" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>一级分类</span>
            <input v-model.trim="form.firstCategory" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>二级分类</span>
            <input v-model.trim="form.secondCategory" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>三级分类</span>
            <input v-model.trim="form.thirdCategory" type="text" placeholder="不填写则不修改" />
          </label>
          <label>
            <span>是否收费</span>
            <select v-model="form.chargeable">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
        </div>
      </section>

      <section>
        <div class="section-title">
          <CheckCircle2 :size="20" />
          <h3>已选择商品</h3>
        </div>
        <div class="selected-code-list">
          <span v-for="code in productCodes" :key="code">{{ code }}</span>
          <p v-if="!productCodes.length" class="muted">尚未选择商品，请返回医院目录勾选后再批量修改。</p>
        </div>
      </section>

      <StatusMessage :message="error" tone="error" />
      <div class="product-form-actions">
        <button class="btn" type="button" @click="router.back()">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="saving">
          <Save :size="17" />
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </form>
  </section>
</template>
