<script setup lang="ts">
import { X } from '@lucide/vue'
import type { PartnerOption } from '../../api/masterData'

const props = defineProps<{
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

/**
 * 选择厂家时自动回填该厂家的生产许可证号。
 */
function handleManufacturerChange() {
  const name = String(props.form.manufacturerName ?? '')
  const option = props.manufacturerOptions.find((item) => item.name === name)
  if (option) {
    props.form.productionLicenseNo = option.licenseNo ?? ''
  }
}

/**
 * 选择供应商时自动回填该供应商的经营许可证号。
 */
function handleSupplierChange() {
  const name = String(props.form.supplierName ?? '')
  const option = props.supplierOptions.find((item) => item.name === name)
  if (option) {
    props.form.businessLicenseNo = option.businessLicenseNo ?? ''
  }
}
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
      <form class="product-dialog-form" @submit.prevent="$emit('save')">
        <div class="product-dialog-scroll">
          <section class="product-dialog-section">
            <h4>基础信息</h4>
            <div class="supplier-form-grid compact">
              <label>
                <span>商品编码</span>
                <input v-model.trim="form.productCode" :readonly="mode === 'edit'" placeholder="留空时使用招采子编码或系统自动编码" />
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
                <select v-model="form.manufacturerName" @change="handleManufacturerChange">
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
                <select v-model="form.supplierName" @change="handleSupplierChange">
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
              <label><span>储存条件</span><input v-model.trim="form.storageCondition" /></label>
            </div>
          </section>

          <section class="product-dialog-section">
            <h4>价格采购与分类</h4>
            <div class="supplier-form-grid compact">
              <label><span>单价</span><input v-model="form.purchasePrice" type="number" min="0" step="0.0001" /></label>
              <label><span>零售价</span><input v-model="form.retailPrice" type="number" min="0" step="0.0001" /></label>
              <label><span>最小采购量</span><input v-model="form.minPurchaseQty" type="number" min="0" step="0.0001" /></label>
              <label><span>采购单位</span><input v-model.trim="form.purchaseUnit" /></label>
              <label><span>中包装数量</span><input v-model="form.conversionRate" type="number" min="0" step="0.000001" /></label>
              <label><span>采购包装数量</span><input v-model="form.purchasePackageQty" type="number" min="0" step="0.0001" /></label>
              <label><span>合同编码</span><input v-model.trim="form.contractCode" /></label>
              <label><span>一级分类</span><input v-model.trim="form.firstCategory" /></label>
              <label><span>二级分类</span><input v-model.trim="form.secondCategory" /></label>
              <label><span>三级分类</span><input v-model.trim="form.thirdCategory" /></label>
              <label><span>招采子编码</span><input v-model.trim="form.tenderSubCode" /></label>
            </div>
          </section>

          <section class="product-dialog-section">
            <h4>资质信息</h4>
            <div class="supplier-form-grid compact">
              <label><span>UDI编码</span><input v-model.trim="form.udiCode" /></label>
              <label><span>注册证号</span><input v-model.trim="form.registrationNo" /></label>
              <label><span>注册证有效期</span><input v-model="form.registrationExpireDate" type="date" /></label>
              <label><span>生产许可证号</span><input v-model.trim="form.productionLicenseNo" /></label>
              <label><span>经营许可证号</span><input v-model.trim="form.businessLicenseNo" /></label>
            </div>
          </section>

          <section class="product-dialog-section">
            <h4>业务属性</h4>
            <div class="dialog-toggle-row">
              <label><input v-model="form.volumeBased" type="checkbox" /> 是否带量</label>
              <label><input v-model="form.centralizedProcurement" type="checkbox" /> 是否集采</label>
              <label><input v-model="form.domestic" type="checkbox" /> 是否国产</label>
              <label><input v-model="form.chargeable" type="checkbox" /> 是否收费</label>
              <label><input v-model="form.highValue" type="checkbox" /> 是否高值</label>
              <label><input v-model="form.coldChain" type="checkbox" /> 是否冷链</label>
              <label><input v-model="form.quotaManaged" type="checkbox" /> 是否定数管理</label>
              <label><input v-model="form.keyMonitored" type="checkbox" /> 重点监控</label>
            </div>
          </section>
        </div>

        <div class="product-dialog-actions">
          <button class="btn" type="button" @click="$emit('close')">取消</button>
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.product-dialog {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.product-dialog-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.product-dialog-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px 18px 10px;
  scrollbar-gutter: stable;
  overscroll-behavior: contain;
}

.product-dialog-section h4 {
  margin: 0;
  padding: 10px 0 8px;
  border-bottom: 1px solid var(--line);
  color: #102033;
  font-size: 14px;
  font-weight: 700;
}

.product-dialog-section .supplier-form-grid {
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px 12px;
  padding: 10px 0 4px;
}

.product-dialog-section .dialog-toggle-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 4px;
}

.product-dialog-section .dialog-toggle-row label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--spd-border-default, #d9dde2);
  border-radius: var(--spd-radius, 6px);
  background: var(--spd-bg-container, #ffffff);
  color: var(--spd-text-primary, #1f2a37);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  box-sizing: border-box;
  white-space: nowrap;
}

.product-dialog-section .dialog-toggle-row label:hover {
  border-color: var(--primary, #1890ff);
  color: var(--primary, #1890ff);
  background: var(--spd-primary-bg, #e6f7ff);
}

.product-dialog-section .dialog-toggle-row input[type="checkbox"] {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--primary, #1890ff);
}

.product-dialog-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex: none;
  border-top: 1px solid var(--line);
  background: #fbfcfd;
  padding: 10px 18px;
  position: sticky;
  bottom: 0;
  z-index: 2;
  box-shadow: 0 -2px 6px rgba(0, 0, 0, 0.04);
}

.product-dialog-actions .btn {
  min-height: 32px !important;
  height: 32px !important;
  padding: 0 16px !important;
  font-size: 13px !important;
  border-radius: var(--spd-radius, 6px) !important;
  border: 1px solid var(--spd-border-default, #d9dde2) !important;
  background: var(--spd-bg-container, #ffffff) !important;
  color: var(--spd-text-primary, #1f2a37) !important;
  font-weight: 500 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: 6px;
  cursor: pointer;
  box-shadow: none !important;
  box-sizing: border-box;
}

.product-dialog-actions .btn:hover {
  border-color: var(--primary, #1890ff) !important;
  color: var(--primary, #1890ff) !important;
  background: var(--spd-primary-bg, #e6f7ff) !important;
}

.product-dialog-actions .btn.btn-primary {
  background: var(--primary, #1890ff) !important;
  border-color: var(--primary, #1890ff) !important;
  color: #ffffff !important;
  box-shadow: 0 2px 4px rgba(22, 119, 255, 0.18) !important;
}

.product-dialog-actions .btn.btn-primary:hover {
  background: var(--primary-strong, #096dd9) !important;
  border-color: var(--primary-strong, #096dd9) !important;
  color: #ffffff !important;
}

/* 表单输入 32px 统一 */
.product-dialog-section .supplier-form-grid label {
  display: flex !important;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.product-dialog-section .supplier-form-grid label > span {
  color: var(--spd-text-secondary, #5b6b7a) !important;
  font-size: 12px !important;
  font-weight: 500 !important;
}

.product-dialog-section .supplier-form-grid input,
.product-dialog-section .supplier-form-grid select {
  min-height: 32px !important;
  height: 32px !important;
  border: 1px solid var(--spd-border-default, #d9dde2) !important;
  border-radius: var(--spd-radius, 6px) !important;
  padding: 0 10px !important;
  background: var(--spd-bg-container, #ffffff) !important;
  color: var(--spd-text-primary, #1f2a37) !important;
  font-size: 13px !important;
  transition: all 0.18s ease;
  box-sizing: border-box;
  width: 100%;
}

.product-dialog-section .supplier-form-grid input:focus,
.product-dialog-section .supplier-form-grid select:focus {
  border-color: var(--primary, #1890ff) !important;
  outline: 0 !important;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15) !important;
}

.product-dialog-section .supplier-form-grid input[readonly] {
  background: var(--spd-bg-page, #f0f2f5) !important;
  color: var(--spd-text-tertiary, #8693a3) !important;
  cursor: not-allowed;
}

.product-dialog-section .supplier-form-grid select:disabled {
  background: var(--spd-bg-page, #f0f2f5) !important;
  color: var(--spd-text-tertiary, #8693a3) !important;
  cursor: not-allowed;
}
</style>
