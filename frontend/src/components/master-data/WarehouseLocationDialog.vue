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
        <SpdButton label="" tone="primary" aria-label="关闭" @click="emit('close')">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
                <SpdButton label="" tone="primary" aria-label="搜索固定商品" @click="searchProducts">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
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
            <SpdButton label="新增" tone="primary" @click="emit('create')">
          <template #icon><Plus :size="16" /></template>
        </SpdButton>
            <SpdButton tone="primary" type="submit" :disabled="saving">
          <template #icon><Save :size="16" /></template>
          {{ saving ? '保存中' : '保存货位' }}
        </SpdButton>
          </div>
        </form>

        <div class="warehouse-location-table-panel">
          <div class="warehouse-location-tools">
            <strong>货位明细</strong>
            <span class="warehouse-location-count">{{ rows.length }} 个货位</span>
            <SpdButton label="刷新" tone="neutral" @click="emit('refresh')">
          <template #icon><RefreshCw :size="16" /></template>
        </SpdButton>
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
                  <th>创建时间</th>
                  <th>修改时间</th>
                  <th class="col-actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="10" class="approval-empty">正在加载...</td>
                </tr>
                <tr v-else-if="!rows.length">
                  <td colspan="10" class="approval-empty">暂无货位，请先新增</td>
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
                      <SpdStatusChip
                        :tone="row.status !== 1 ? 'warning' : 'success'"
                        :label="row.statusLabel"
                      />
                    </td>
                    <td class="md-audit-time">{{ row.createTime || '-' }}</td>
                    <td class="md-audit-time">{{ row.updateTime || '-' }}</td>
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
  background: var(--spd-p-bg);
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
}

.warehouse-location-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary-strong) 100%);
  color: var(--spd-p-bg);
}

.warehouse-location-title p {
  margin: 0 0 4px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.warehouse-location-title h3 {
  margin: 0;
  font-size: var(--spd-font-size-xl);
  font-weight: 600;
  color: var(--spd-p-bg);
}

.warehouse-location-sub {
  display: inline-block;
  margin-top: 6px;
  padding: 3px 10px;
  font-size: var(--spd-font-size-sm);
  background: color-mix(in srgb, var(--spd-p-bg) 18%, transparent);
  border-radius: 999px;
  color: var(--spd-p-bg);
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
  background: var(--spd-p-bg-subtle);
  border-right: 1px solid var(--spd-p-border);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--spd-p-text);
}

.section-editing-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-warning) 15%, transparent), color-mix(in srgb, var(--spd-p-danger) 15%, transparent));
  color: var(--spd-p-warning-text);
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
}

.warehouse-location-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-muted);
  font-weight: 500;
}

.warehouse-location-form input,
.warehouse-location-form select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  transition: border-color 0.18s, box-shadow 0.18s;
}

.warehouse-location-form input:focus,
.warehouse-location-form select:focus {
  outline: none;
  border-color: var(--spd-p-primary);
  box-shadow: var(--spd-p-shadow-1);
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
  font-size: var(--spd-font-size-base);
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
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary-strong) 100%);
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}

.wld-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary) 0%, var(--spd-p-primary-strong) 100%);
  box-shadow: var(--spd-p-shadow-2);
}

.wld-btn-secondary {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-primary) 10%, transparent), color-mix(in srgb, var(--spd-p-success-text) 10%, transparent));
  color: var(--spd-p-primary-hover);
  border-color: color-mix(in srgb, var(--spd-p-primary) 20%, transparent);
}

.wld-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-hover));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}

.wld-btn-info {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-success-text) 10%, transparent), color-mix(in srgb, var(--spd-p-info) 10%, transparent));
  color: var(--spd-p-primary-hover);
  border-color: color-mix(in srgb, var(--spd-p-success-text) 20%, transparent);
}

.wld-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-info));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}

.wld-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: color-mix(in srgb, var(--spd-p-bg) 18%, transparent);
  color: var(--spd-p-bg);
  border-color: color-mix(in srgb, var(--spd-p-bg) 30%, transparent);
}

.wld-btn-ghost:hover {
  background: color-mix(in srgb, var(--spd-p-bg) 30%, transparent);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-3);
}

.product-result-option {
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) minmax(150px, 1.2fr) minmax(110px, 1fr);
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-bottom: 1px solid var(--spd-p-bg-muted);
  background: var(--spd-p-bg);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.product-result-option:hover,
.product-result-option.active {
  background: var(--spd-p-primary-bg);
}

.product-result-option .product-code {
  color: var(--spd-p-primary);
  font-weight: 700;
}

.product-result-option .product-name {
  color: var(--spd-p-text);
}

.product-result-option .product-spec {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-results-empty {
  margin: 0;
  padding: 12px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg);
  overflow: hidden;
}

.warehouse-location-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  background: linear-gradient(135deg, var(--spd-p-bg-muted), var(--spd-p-bg-muted));
  border-bottom: 1px solid var(--spd-p-border);
}

.warehouse-location-tools strong {
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--spd-p-text);
}

.warehouse-location-count {
  padding: 2px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-info) 12%, transparent), color-mix(in srgb, var(--spd-p-primary-strong) 12%, transparent));
  color: var(--spd-p-primary);
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text);
}

.warehouse-location-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 12px;
  text-align: left;
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  letter-spacing: 0.3px;
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-text-secondary) 0%, var(--spd-p-text-secondary) 100%);
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
  border-bottom: 1px solid var(--spd-p-bg-muted);
  vertical-align: middle;
}

.warehouse-location-table tbody tr:nth-child(even) td {
  background: var(--spd-p-bg-muted);
}

.warehouse-location-table tbody tr:hover td {
  background: var(--spd-p-primary-bg);
}

.warehouse-location-table tbody td.col-index {
  text-align: center;
  font-variant-numeric: tabular-nums;
  color: var(--spd-p-text-secondary);
  font-weight: 600;
}

.warehouse-location-table tbody td.col-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--spd-p-text);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  border: 1px solid transparent;
}

.type-chip.type-整件货位 {
  background: color-mix(in srgb, var(--spd-p-primary) 12%, transparent);
  color: var(--spd-p-primary-hover);
  border-color: color-mix(in srgb, var(--spd-p-primary) 30%, transparent);
}

.type-chip.type-散货货位 {
  background: color-mix(in srgb, var(--spd-p-warning) 12%, transparent);
  color: var(--spd-p-warning-text);
  border-color: color-mix(in srgb, var(--spd-p-warning) 30%, transparent);
}

.type-chip.type-试剂货位 {
  background: color-mix(in srgb, var(--spd-p-primary-strong) 12%, transparent);
  color: var(--spd-p-primary-strong);
  border-color: color-mix(in srgb, var(--spd-p-primary-strong) 30%, transparent);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.warehouse-table-btn:hover {
  transform: translateY(-1px);
}

.warehouse-table-btn-edit {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-info) 12%, transparent), color-mix(in srgb, var(--spd-p-primary-strong) 12%, transparent));
  color: var(--spd-p-primary);
  border-color: color-mix(in srgb, var(--spd-p-info) 20%, transparent);
}

.warehouse-table-btn-edit:hover {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}

.warehouse-table-btn-danger {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-danger) 12%, transparent), color-mix(in srgb, var(--spd-p-danger) 12%, transparent));
  color: var(--spd-p-danger);
  border-color: color-mix(in srgb, var(--spd-p-danger) 20%, transparent);
}

.warehouse-table-btn-danger:hover {
  background: linear-gradient(135deg, var(--spd-p-danger), var(--spd-p-danger));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}
</style>
