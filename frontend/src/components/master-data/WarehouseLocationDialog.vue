<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, RefreshCw, Save, Search, Trash2, X } from '@lucide/vue'
import type {
  WarehouseLocation,
  WarehouseLocationPayload,
  WarehouseProductOption
} from '../../api/masterData'

const props = defineProps<{
  open: boolean
  warehouse: Record<string, unknown> | null
  rows: WarehouseLocation[]
  form: WarehouseLocationPayload
  editingId: number | null
  loading: boolean
  saving: boolean
  productOptions: WarehouseProductOption[]
}>()

const LOCATION_TYPES = ['整件货位', '散货货位', '试剂货位'] as const

const productResultsOpen = ref(false)

const locationTypeOptions = computed(() => {
  const current = String(props.form.locationType ?? '').trim()
  if (current && !LOCATION_TYPES.includes(current as (typeof LOCATION_TYPES)[number])) {
    return [...LOCATION_TYPES, current]
  }
  return LOCATION_TYPES
})

const filteredProducts = computed(() => {
  const keyword = String(props.form.productCode ?? '').trim().toLowerCase()
  if (!keyword) return props.productOptions.slice(0, 50)
  return props.productOptions
    .filter((product) =>
      [product.productCode, product.productName, product.specModel]
        .some((value) => String(value ?? '').toLowerCase().includes(keyword))
    )
    .slice(0, 50)
})

watch(() => props.open, (open) => {
  if (open) {
    productResultsOpen.value = false
  }
})

function searchProducts() {
  productResultsOpen.value = true
}

function pickProduct(product: WarehouseProductOption) {
  props.form.productCode = product.productCode
  productResultsOpen.value = false
}

const emit = defineEmits<{
  close: []
  refresh: []
  create: []
  edit: [row: WarehouseLocation]
  save: []
  remove: [row: WarehouseLocation]
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask">
    <section class="warehouse-location-dialog" role="dialog" aria-modal="true">
      <header class="warehouse-location-header">
        <div class="warehouse-location-title">
          <p>库房 / 货位管理</p>
          <h3>维护货位</h3>
          <span class="warehouse-location-sub">
            {{ warehouse?.name || '-' }} · {{ warehouse?.code || '-' }}
          </span>
        </div>
        <button class="wld-btn wld-btn-ghost" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <div class="warehouse-location-body">
        <form class="warehouse-location-form" @submit.prevent="emit('save')">
          <div class="section-title compact">
            <h3>{{ editingId == null ? '新增货位' : '编辑货位' }}</h3>
            <span v-if="editingId != null" class="section-editing-tag">正在编辑</span>
          </div>
          <label>
            <span>货位编码</span>
            <input v-model.trim="form.locationCode" placeholder="如：A-01-01-01" required />
          </label>
          <label>
            <span>货位类型</span>
            <select v-model.trim="form.locationType" required>
              <option value="">请选择货位类型</option>
              <option v-for="type in locationTypeOptions" :key="type" :value="type">{{ type }}</option>
            </select>
          </label>
          <label>
            <span>容量上限</span>
            <input v-model="form.capacityLimit" type="number" min="0" step="0.0001" placeholder="可选" />
          </label>
          <label class="location-product-field">
            <span>固定商品编码</span>
            <div class="product-search">
              <div class="product-search-input">
                <input
                  v-model.trim="form.productCode"
                  placeholder="输入编码，回车或点击放大镜搜索医院目录"
                  @keyup.enter="searchProducts"
                  @input="searchProducts"
                  @focus="searchProducts"
                />
                <button class="wld-btn wld-btn-info" type="button" aria-label="搜索固定商品" @click="searchProducts">
                  <Search :size="16" />
                </button>
              </div>
              <div v-if="productResultsOpen" class="product-results">
                <button
                  v-for="product in filteredProducts"
                  :key="product.productCode"
                  type="button"
                  class="product-result-option"
                  :class="{ active: form.productCode === product.productCode }"
                  @click="pickProduct(product)"
                >
                  <span class="product-code">{{ product.productCode }}</span>
                  <span class="product-name">{{ product.productName }}</span>
                  <span class="product-spec">{{ product.specModel || '-' }}</span>
                </button>
                <p v-if="!filteredProducts.length" class="product-results-empty">医院目录中未找到匹配商品</p>
              </div>
            </div>
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <div class="warehouse-location-actions">
            <button class="wld-btn wld-btn-secondary" type="button" @click="emit('create')">
              <Plus :size="16" />
              新增
            </button>
            <button class="wld-btn wld-btn-primary" type="submit" :disabled="saving">
              <Save :size="16" />
              {{ saving ? '保存中' : '保存货位' }}
            </button>
          </div>
        </form>

        <div class="warehouse-location-table-panel">
          <div class="warehouse-location-tools">
            <strong>货位明细</strong>
            <span class="warehouse-location-count">{{ rows.length }} 个货位</span>
            <button class="wld-btn wld-btn-info" type="button" @click="emit('refresh')">
              <RefreshCw :size="16" />
              刷新
            </button>
          </div>
          <div class="warehouse-location-table-wrap">
            <table class="master-table warehouse-location-table">
              <thead>
                <tr>
                  <th class="col-index">序号</th>
                  <th>货位编码</th>
                  <th>货位类型</th>
                  <th class="col-num">容量上限</th>
                  <th>固定商品编码</th>
                  <th>商品名称</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th class="col-actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="9" class="approval-empty">正在加载...</td>
                </tr>
                <tr v-else-if="!rows.length">
                  <td colspan="9" class="approval-empty">暂无货位，请先新增</td>
                </tr>
                <template v-else>
                  <tr v-for="(row, index) in rows" :key="row.locationId">
                    <td class="col-index">{{ index + 1 }}</td>
                    <td>{{ row.locationCode }}</td>
                    <td>
                      <span class="type-chip" :class="`type-${row.locationType}`">
                        {{ row.locationType || '-' }}
                      </span>
                    </td>
                    <td class="col-num">{{ row.capacityLimit ?? '-' }}</td>
                    <td>{{ row.productCode || '-' }}</td>
                    <td>{{ row.productName || '-' }}</td>
                    <td>
                      <span class="status-badge" :class="{ warning: row.status !== 1 }">
                        {{ row.statusLabel }}
                      </span>
                    </td>
                    <td>{{ row.updateTime || '-' }}</td>
                    <td class="col-actions warehouse-action-cell">
                      <button
                        type="button"
                        class="warehouse-table-btn warehouse-table-btn-edit"
                        @click="emit('edit', row)"
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        class="warehouse-table-btn warehouse-table-btn-danger"
                        @click="emit('remove', row)"
                      >
                        <Trash2 :size="14" />
                        删除
                      </button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================
   Dialog 容器
   ============================================================ */
.warehouse-location-dialog {
  width: min(1280px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15, 42, 58, 0.35);
  overflow: hidden;
}

.warehouse-location-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #ffffff;
}

.warehouse-location-title p {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.warehouse-location-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.warehouse-location-sub {
  display: inline-block;
  margin-top: 6px;
  padding: 3px 10px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  color: #ffffff;
}

.warehouse-location-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 1100px) {
  .warehouse-location-body {
    grid-template-columns: 1fr;
  }
}

.warehouse-location-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px;
  background: #f5f8fa;
  border-right: 1px solid #e1e9ee;
  overflow-y: auto;
}

.warehouse-location-form .section-title.compact {
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.warehouse-location-form .section-title.compact h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #172b3a;
}

.section-editing-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(239, 68, 68, 0.15));
  color: #b45309;
  font-size: 11px;
  font-weight: 600;
}

.warehouse-location-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #566b77;
  font-weight: 500;
}

.warehouse-location-form input,
.warehouse-location-form select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.warehouse-location-form input:focus,
.warehouse-location-form select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.warehouse-location-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.warehouse-location-actions .wld-btn {
  flex: 1;
  justify-content: center;
}

/* ============================================================
   Buttons
   ============================================================ */
.wld-btn {
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

.wld-btn:hover {
  transform: translateY(-1px);
}

.wld-btn:active {
  transform: translateY(0);
}

.wld-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.wld-btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.4);
}

.wld-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.55);
}

.wld-btn-secondary {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.1), rgba(13, 148, 136, 0.1));
  color: #0d9488;
  border-color: rgba(20, 184, 166, 0.2);
}

.wld-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(20, 184, 166, 0.5);
}

.wld-btn-info {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(14, 165, 233, 0.1));
  color: #0891b2;
  border-color: rgba(6, 182, 212, 0.2);
}

.wld-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, #06b6d4, #0ea5e9);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(6, 182, 212, 0.5);
}

.wld-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.wld-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.3);
  box-shadow: none;
}

/* ============================================================
   Product search dropdown
   ============================================================ */
.location-product-field {
  position: relative;
}

.product-search {
  position: relative;
  display: grid;
  gap: 4px;
}

.product-search-input {
  display: flex;
  align-items: center;
  gap: 6px;
}

.product-search-input input {
  flex: 1;
  min-width: 0;
}

.product-results {
  position: absolute;
  top: calc(100% - 4px);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 240px;
  overflow: auto;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 42, 58, 0.12);
}

.product-result-option {
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) minmax(150px, 1.2fr) minmax(110px, 1fr);
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-bottom: 1px solid #eef3f5;
  background: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.product-result-option:hover,
.product-result-option.active {
  background: #eef7f8;
}

.product-result-option .product-code {
  color: #0f6f78;
  font-weight: 700;
}

.product-result-option .product-name {
  color: #172b3a;
}

.product-result-option .product-spec {
  color: #6b7c8f;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-results-empty {
  margin: 0;
  padding: 12px;
  color: #6b7c8f;
  font-size: 13px;
  text-align: center;
}

/* ============================================================
   Table panel
   ============================================================ */
.warehouse-location-table-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: #ffffff;
  overflow: hidden;
}

.warehouse-location-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  background: linear-gradient(135deg, #f8fafc, #eef2f7);
  border-bottom: 1px solid #e1e9ee;
}

.warehouse-location-tools strong {
  font-size: 14px;
  font-weight: 600;
  color: #172b3a;
}

.warehouse-location-count {
  padding: 2px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(139, 92, 246, 0.12));
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  margin-right: auto;
}

.warehouse-location-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 20px 20px;
}

.warehouse-location-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: auto;
  font-size: 13px;
  color: #172b3a;
}

.warehouse-location-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: #ffffff;
  background: linear-gradient(135deg, #475569 0%, #334155 100%);
  border-bottom: 0;
  white-space: nowrap;
}

.warehouse-location-table thead th.col-index {
  width: 56px;
  text-align: center;
}

.warehouse-location-table thead th.col-num {
  text-align: right;
  width: 100px;
}

.warehouse-location-table thead th.col-actions {
  width: 168px;
  text-align: center;
}

.warehouse-location-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f5;
  vertical-align: middle;
}

.warehouse-location-table tbody tr:nth-child(even) td {
  background: #f9fbfc;
}

.warehouse-location-table tbody tr:hover td {
  background: #eef7f8;
}

.warehouse-location-table tbody td.col-index {
  text-align: center;
  font-variant-numeric: tabular-nums;
  color: #6b7c8f;
  font-weight: 600;
}

.warehouse-location-table tbody td.col-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #172b3a;
}

.warehouse-location-table tbody td.col-actions {
  text-align: center;
}

/* ============================================================
   Type chip / status badge
   ============================================================ */
.type-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}

.type-chip.type-整件货位 {
  background: rgba(20, 184, 166, 0.12);
  color: #0d9488;
  border-color: rgba(20, 184, 166, 0.3);
}

.type-chip.type-散货货位 {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.3);
}

.type-chip.type-试剂货位 {
  background: rgba(139, 92, 246, 0.12);
  color: #7c3aed;
  border-color: rgba(139, 92, 246, 0.3);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.status-badge.warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.3);
}

/* ============================================================
   Row action buttons (in table)
   ============================================================ */
.warehouse-action-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.warehouse-table-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 3px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.warehouse-table-btn:hover {
  transform: translateY(-1px);
}

.warehouse-table-btn-edit {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(139, 92, 246, 0.12));
  color: #4f46e5;
  border-color: rgba(99, 102, 241, 0.2);
}

.warehouse-table-btn-edit:hover {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.5);
}

.warehouse-table-btn-danger {
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.12), rgba(225, 29, 72, 0.12));
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.2);
}

.warehouse-table-btn-danger:hover {
  background: linear-gradient(135deg, #dc2626, #e11d48);
  color: #ffffff;
  box-shadow: 0 4px 10px -4px rgba(220, 38, 38, 0.5);
}
</style>
