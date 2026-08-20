<script setup lang="ts">
import { X } from '@lucide/vue'
import type { PartnerOption } from '../../api/masterData'

defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: Record<string, unknown>
  manufacturerOptions: PartnerOption[]
  supplierOptions: PartnerOption[]
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'save'): void
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="$emit('close')">
    <section class="supplier-dialog product-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>医院目录</p>
          <h3>{{ mode === 'create' ? '新增商品' : '修改商品' }}</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid" @submit.prevent="$emit('save')">
        <label>
          <span>商品编码</span>
          <input v-model.trim="form.productCode" :readonly="mode === 'edit'" required />
        </label>
        <label>
          <span>商品名称</span>
          <input v-model.trim="form.productName" required />
        </label>
        <label>
          <span>规格型号</span>
          <input v-model.trim="form.specModel" required />
        </label>
        <label><span>品牌</span><input v-model.trim="form.brand" /></label>
        <label>
          <span>生产厂家</span>
          <select v-model="form.manufacturerName">
            <option value="">请选择生产厂家</option>
            <option
              v-for="item in manufacturerOptions"
              :key="item.code"
              :value="item.name"
              :disabled="item.status !== 1"
            >
              {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
            </option>
          </select>
        </label>
        <label>
          <span>供应商</span>
          <select v-model="form.supplierName">
            <option value="">请选择供应商</option>
            <option
              v-for="item in supplierOptions"
              :key="item.code"
              :value="item.name"
              :disabled="item.status !== 1"
            >
              {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
            </option>
          </select>
        </label>
        <label>
          <span>单位</span>
          <input v-model.trim="form.unit" required />
        </label>
        <label><span>单价</span><input v-model="form.purchasePrice" type="number" min="0" step="0.0001" /></label>
        <label><span>零售价</span><input v-model="form.retailPrice" type="number" min="0" step="0.0001" /></label>
        <label><span>最小采购量</span><input v-model="form.minPurchaseQty" type="number" min="0" step="0.0001" /></label>
        <label><span>采购单位</span><input v-model.trim="form.purchaseUnit" /></label>
        <label><span>换算系数</span><input v-model="form.conversionRate" type="number" min="0" step="0.000001" /></label>
        <label><span>合同编码</span><input v-model.trim="form.contractCode" /></label>
        <label><span>一级分类</span><input v-model.trim="form.firstCategory" /></label>
        <label><span>二级分类</span><input v-model.trim="form.secondCategory" /></label>
        <label><span>三级分类</span><input v-model.trim="form.thirdCategory" /></label>
        <label><span>招采子编码</span><input v-model.trim="form.tenderSubCode" /></label>
        <label><span>UDI编码</span><input v-model.trim="form.udiCode" /></label>
        <label><span>注册证号</span><input v-model.trim="form.registrationNo" /></label>
        <label><span>注册证有效期</span><input v-model="form.registrationExpireDate" type="date" /></label>
        <label><span>生产许可证号</span><input v-model.trim="form.productionLicenseNo" /></label>
        <label><span>经营许可证号</span><input v-model.trim="form.businessLicenseNo" /></label>
        <label><span>存储条件</span><input v-model.trim="form.storageCondition" /></label>
        <div class="dialog-toggle-row">
          <label><input v-model="form.volumeBased" type="checkbox" /> 是否带量</label>
          <label><input v-model="form.centralizedProcurement" type="checkbox" /> 是否集采</label>
          <label><input v-model="form.domestic" type="checkbox" /> 是否国产</label>
          <label><input v-model="form.chargeable" type="checkbox" /> 是否收费</label>
          <label><input v-model="form.highValue" type="checkbox" /> 是否高值</label>
          <label><input v-model="form.coldChain" type="checkbox" /> 是否冷链</label>
          <label><input v-model="form.quotaManaged" type="checkbox" /> 是否定数管理</label>
        </div>
        <div class="dialog-actions">
          <button class="btn" type="button" @click="$emit('close')">取消</button>
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>
    </section>
  </div>
</template>
