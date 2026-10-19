<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, CheckSquare, Layers3, Search, Square, X } from '@lucide/vue'
import type {
  CampusOption,
  WarehousePayload,
  WarehouseProductOption
} from '../../api/masterData'

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: WarehousePayload
  campusOptions: CampusOption[]
  departmentOptions: Array<{ deptCode: string; deptName: string }>
  productOptions: WarehouseProductOption[]
  productsLoading: boolean
}>()

const WAREHOUSE_TYPES = ['一级库', '二级库', '三级库'] as const

const productKeyword = ref('')
const deptResultsOpen = ref(false)

const warehouseTypeOptions = computed(() => {
  const current = props.form.warehouseType?.trim()
  if (current && !WAREHOUSE_TYPES.includes(current as (typeof WAREHOUSE_TYPES)[number])) {
    return [...WAREHOUSE_TYPES, current]
  }
  return WAREHOUSE_TYPES
})

const filteredDepartments = computed(() => {
  const keyword = String(props.form.deptName ?? '').trim().toLowerCase()
  if (!keyword) return props.departmentOptions
  return props.departmentOptions.filter((dept) =>
    String(dept.deptName ?? '').toLowerCase().includes(keyword) ||
    String(dept.deptCode ?? '').toLowerCase().includes(keyword)
  )
})

const filteredProducts = computed(() => {
  const keyword = productKeyword.value.trim().toLowerCase()
  if (!keyword) return props.productOptions
  return props.productOptions.filter((product) =>
    [product.productCode, product.productName, product.specModel]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword))
  )
})

watch(() => props.open, (open) => {
  if (open) {
    productKeyword.value = ''
    deptResultsOpen.value = false
  }
})

function searchDepartments() {
  deptResultsOpen.value = true
}

function handleDepartmentInput() {
  props.form.deptCode = ''
  searchDepartments()
}

function pickDepartment(dept: { deptCode: string; deptName: string }) {
  props.form.deptCode = dept.deptCode
  props.form.deptName = dept.deptName
  deptResultsOpen.value = false
}

function toggleAllFiltered() {
  const filteredCodes = filteredProducts.value.map((product) => product.productCode)
  const selected = new Set(props.form.productCodes)
  const allSelected = filteredCodes.length > 0 && filteredCodes.every((code) => selected.has(code))
  filteredCodes.forEach((code) => {
    if (allSelected) selected.delete(code)
    else if (selected.size < 500) selected.add(code)
  })
  props.form.productCodes = [...selected]
}

const isAllFilteredSelected = computed(() => {
  const codes = filteredProducts.value.map((p) => p.productCode)
  if (!codes.length) return false
  const selected = new Set(props.form.productCodes)
  return codes.every((code) => selected.has(code))
})

function isProductSelected(code: string): boolean {
  return props.form.productCodes.includes(code)
}

function toggleProduct(code: string) {
  if (isProductSelected(code)) {
    props.form.productCodes = props.form.productCodes.filter((c) => c !== code)
  } else if (props.form.productCodes.length < 500) {
    props.form.productCodes = [...props.form.productCodes, code]
  }
}

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')">
    <section class="warehouse-dialog" role="dialog" aria-modal="true">
      <header class="warehouse-dialog-header">
        <div class="warehouse-dialog-title">
          <p>库房 / 货位管理</p>
          <h3>{{ mode === 'create' ? '新增库房' : '编辑库房' }}</h3>
          <span v-if="mode === 'edit'" class="warehouse-dialog-editing-tag">正在编辑</span>
        </div>
        <button class="wdb-btn wdb-btn-ghost" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <form class="warehouse-dialog-form" @submit.prevent="emit('save')">
        <div class="warehouse-dialog-form-grid">
          <label class="field-cell">
            <span>库房编码</span>
            <input v-model.trim="form.warehouseCode" :readonly="mode === 'edit'" required />
          </label>
          <label class="field-cell">
            <span>库房名称</span>
            <input v-model.trim="form.warehouseName" required />
          </label>
          <label class="field-cell">
            <span>库房类型</span>
            <select v-model.trim="form.warehouseType" required>
              <option value="">请选择库房类型</option>
              <option v-for="type in warehouseTypeOptions" :key="type" :value="type">{{ type }}</option>
            </select>
          </label>
          <label class="field-cell">
            <span>所属院区</span>
            <select v-model.trim="form.campusName" required>
              <option value="">请选择院区</option>
              <option v-for="campus in campusOptions" :key="campus.code" :value="campus.name">
                {{ campus.name }}
              </option>
            </select>
          </label>
          <label class="field-cell warehouse-dept-field">
            <span>关联科室</span>
            <div class="dept-search">
              <div class="dept-search-input">
                <input
                  v-model.trim="form.deptName"
                  placeholder="输入科室名称，回车或点击放大镜搜索"
                  @keyup.enter="searchDepartments"
                  @input="handleDepartmentInput"
                  @focus="searchDepartments"
                />
                <button class="wdb-btn wdb-btn-info wdb-btn-icon" type="button" aria-label="搜索科室" @click="searchDepartments">
                  <Search :size="16" />
                </button>
              </div>
              <div v-if="deptResultsOpen" class="dept-results">
                <button
                  v-for="dept in filteredDepartments"
                  :key="dept.deptCode"
                  type="button"
                  class="dept-result-option"
                  :class="{ active: form.deptName === dept.deptName }"
                  @click="pickDepartment(dept)"
                >
                  <span>{{ dept.deptName }}</span>
                  <small>{{ dept.deptCode }}</small>
                </button>
                <p v-if="!filteredDepartments.length" class="dept-results-empty">未找到匹配科室</p>
              </div>
            </div>
          </label>
          <label class="field-cell">
            <span>库房状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <label class="field-cell">
            <span>是否参与统计</span>
            <select v-model="form.participateStats">
              <option :value="true">参与</option>
              <option :value="false">不参与</option>
            </select>
          </label>
          <label class="field-cell">
            <span>允许收货</span>
            <select v-model="form.receivingEnabled">
              <option :value="true">允许</option>
              <option :value="false">不允许</option>
            </select>
          </label>
          <label class="field-cell">
            <span>统计分类</span>
            <input v-model.trim="form.statsCategories" placeholder="耗材、试剂、高值" />
          </label>
        </div>

        <section class="warehouse-product-picker" aria-label="商品绑定">
          <div class="warehouse-product-toolbar">
            <div class="warehouse-product-toolbar-text">
              <strong>商品绑定</strong>
              <span>选择该库房允许管理的商品，可按编码、名称或规格搜索</span>
            </div>
            <div class="warehouse-product-toolbar-right">
              <span class="warehouse-product-counter">
                已选 <b>{{ form.productCodes.length }}</b> / 500 项
              </span>
            </div>
          </div>

          <div class="warehouse-product-controls">
            <label class="warehouse-product-search">
              <Search :size="16" />
              <input v-model.trim="productKeyword" type="search" placeholder="搜索商品编码、名称或规格" />
            </label>
            <button
              class="wdb-btn wdb-btn-secondary"
              type="button"
              :disabled="productsLoading || !filteredProducts.length"
              @click="toggleAllFiltered"
            >
              <component :is="isAllFilteredSelected ? CheckSquare : Square" :size="16" />
              {{ isAllFilteredSelected ? '取消当前结果' : '全选当前结果' }}
            </button>
          </div>

          <div class="warehouse-product-options-wrap">
            <table v-if="filteredProducts.length" class="warehouse-product-table">
              <thead>
                <tr>
                  <th class="col-check">选择</th>
                  <th>商品编码</th>
                  <th>商品名称</th>
                  <th>规格型号</th>
                  <th class="col-unit">单位</th>
                  <th class="col-qty">可选数量</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="product in filteredProducts"
                  :key="product.productCode"
                  class="warehouse-product-row"
                  :class="{ selected: isProductSelected(product.productCode) }"
                  @click="toggleProduct(product.productCode)"
                >
                  <td class="col-check">
                    <span class="check-box" :class="{ on: isProductSelected(product.productCode) }">
                      <Check v-if="isProductSelected(product.productCode)" :size="12" />
                    </span>
                  </td>
                  <td><span class="product-code">{{ product.productCode }}</span></td>
                  <td><span class="product-name">{{ product.productName }}</span></td>
                  <td><span class="product-spec">{{ product.specModel || '-' }}</span></td>
                  <td class="col-unit"><span class="product-unit">{{ product.unit || '-' }}</span></td>
                  <td class="col-qty">
                    <span
                      v-if="product.selectable != null"
                      class="product-qty-badge"
                      :class="{ zero: product.selectable === 0 }"
                    >
                      {{ product.selectable }}
                    </span>
                    <span v-else class="product-qty-none">-</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else-if="productsLoading" class="warehouse-product-empty">正在加载可用商品...</p>
            <p v-else class="warehouse-product-empty">没有匹配的可用商品</p>
          </div>
          <p class="warehouse-product-hint">商品绑定可后续编辑，单个库房最多绑定 500 项。</p>
        </section>

        <div class="warehouse-dialog-actions">
          <button class="wdb-btn wdb-btn-secondary" type="button" @click="emit('close')">
            取消
          </button>
          <button class="wdb-btn wdb-btn-primary" type="submit">
            <Check :size="16" />
            保存
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================
   Dialog container
   ============================================================ */
.warehouse-dialog {
  width: min(1280px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15, 42, 58, 0.35);
  overflow: hidden;
}

.warehouse-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #ffffff;
}

.warehouse-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.warehouse-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.warehouse-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.warehouse-dialog-editing-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
}

.warehouse-dialog-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.warehouse-dialog-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 20px 24px 12px;
  background: #f5f8fa;
  border-bottom: 1px solid #e1e9ee;
  overflow-y: auto;
}

@media (max-width: 1100px) {
  .warehouse-dialog-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .warehouse-dialog-form-grid {
    grid-template-columns: 1fr;
  }
}

.field-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #566b77;
  font-weight: 500;
}

.field-cell input,
.field-cell select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.field-cell input:focus,
.field-cell select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.field-cell input[readonly] {
  background: #f0f3f5;
  color: #6b7c8f;
  cursor: not-allowed;
}

/* ============================================================
   Buttons
   ============================================================ */
.wdb-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 5px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.wdb-btn:hover {
  transform: translateY(-1px);
}

.wdb-btn:active {
  transform: translateY(0);
}

.wdb-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.wdb-btn-icon {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
}

.wdb-btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.4);
}

.wdb-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.55);
}

.wdb-btn-secondary {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.1), rgba(13, 148, 136, 0.1));
  color: #0d9488;
  border-color: rgba(20, 184, 166, 0.2);
}

.wdb-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(20, 184, 166, 0.5);
}

.wdb-btn-info {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(14, 165, 233, 0.1));
  color: #0891b2;
  border-color: rgba(6, 182, 212, 0.2);
}

.wdb-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, #06b6d4, #0ea5e9);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(6, 182, 212, 0.5);
}

.wdb-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.wdb-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.3);
  box-shadow: none;
}

/* ============================================================
   Department search
   ============================================================ */
.warehouse-dept-field {
  position: relative;
}

.dept-search {
  position: relative;
  display: grid;
  gap: 4px;
}

.dept-search-input {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dept-search-input input {
  flex: 1;
  min-width: 0;
}

.dept-results {
  position: absolute;
  top: calc(100% - 4px);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 220px;
  overflow: auto;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 42, 58, 0.12);
}

.dept-result-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-bottom: 1px solid #eef3f5;
  background: #fff;
  color: #172b3a;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.dept-result-option:hover,
.dept-result-option.active {
  background: #eef7f8;
}

.dept-result-option small {
  color: #6b7c8f;
}

.dept-results-empty {
  margin: 0;
  padding: 12px;
  color: #6b7c8f;
  font-size: 13px;
  text-align: center;
}

/* ============================================================
   Product picker
   ============================================================ */
.warehouse-product-picker {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 16px 24px 0;
  border: 1px solid #dbe8ee;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(15, 42, 58, 0.04);
}

.warehouse-product-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: linear-gradient(135deg, #f8fafc, #eef2f7);
  border-bottom: 1px solid #e1e9ee;
}

.warehouse-product-toolbar-text {
  display: grid;
  gap: 3px;
}

.warehouse-product-toolbar-text strong {
  color: #172b3a;
  font-size: 14px;
  font-weight: 600;
}

.warehouse-product-toolbar-text span {
  color: #6b7c8f;
  font-size: 12.5px;
  font-weight: 500;
}

.warehouse-product-counter {
  padding: 4px 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(139, 92, 246, 0.12));
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.warehouse-product-counter b {
  font-weight: 700;
  color: #4f46e5;
}

.warehouse-product-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #eaf1f4;
  background: #ffffff;
}

.warehouse-product-search {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
  min-width: 200px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #cfdfe7;
  border-radius: 8px;
  background: #fff;
  color: #607487;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.warehouse-product-search:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.warehouse-product-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  font: inherit;
  color: #172b3a;
}

.warehouse-product-options-wrap {
  flex: 1;
  min-height: 280px;
  max-height: 100%;
  overflow: auto;
}

.warehouse-product-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: auto;
  font-size: 13px;
  color: #172b3a;
}

.warehouse-product-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: #ffffff;
  background: linear-gradient(135deg, #475569 0%, #334155 100%);
  white-space: nowrap;
}

.warehouse-product-table thead th.col-check {
  width: 72px;
  text-align: center;
}

.warehouse-product-table thead th.col-unit {
  width: 80px;
  text-align: center;
}

.warehouse-product-table thead th.col-qty {
  width: 96px;
  text-align: center;
}

.warehouse-product-table tbody td {
  padding: 8px 14px;
  border-bottom: 1px solid #eef2f5;
  vertical-align: middle;
}

.warehouse-product-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}

.warehouse-product-table tbody tr:nth-child(even) td {
  background: #f9fbfc;
}

.warehouse-product-table tbody tr:hover td {
  background: #eef7f8;
}

.warehouse-product-table tbody tr.selected td {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.08));
}

.warehouse-product-table tbody td.col-check {
  text-align: center;
}

.warehouse-product-table tbody td.col-unit {
  text-align: center;
}

.check-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1.5px solid #cbd5e1;
  border-radius: 4px;
  background: #ffffff;
  color: transparent;
  transition: background 0.18s, border-color 0.18s, color 0.18s;
}

.check-box.on {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-color: transparent;
  color: #ffffff;
}

.product-code {
  color: #0f6f78;
  font-weight: 700;
}

.product-name {
  color: #172b3a;
  font-weight: 600;
}

.product-spec {
  color: #6b7c8f;
  font-size: 12.5px;
}

.product-unit {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(20, 184, 166, 0.12);
  color: #0d9488;
  font-size: 12px;
  font-weight: 600;
}

.warehouse-product-table thead th.col-qty {
  width: 96px;
  text-align: center;
}

.warehouse-product-table tbody td.col-qty {
  text-align: center;
}

.product-qty-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.product-qty-badge.zero {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.product-qty-none {
  color: #9ca3af;
  font-size: 12px;
}

.warehouse-product-empty {
  margin: 0;
  padding: 32px 16px;
  color: #6b7c8f;
  text-align: center;
}

.warehouse-product-hint {
  margin: 0;
  padding: 8px 14px;
  border-top: 1px solid #e5eef2;
  background: #fbfdfe;
  color: #6b7c8f;
  font-size: 12.5px;
}

/* ============================================================
   Bottom actions
   ============================================================ */
.warehouse-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px 20px;
  background: #f5f8fa;
  border-top: 1px solid #e1e9ee;
}

@media (max-width: 720px) {
  .warehouse-product-controls {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .warehouse-product-search {
    flex-basis: 100%;
  }
}
</style>
