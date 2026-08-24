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
        <div>
          <p>库房 / 货位管理</p>
          <h3>维护货位</h3>
          <span>{{ warehouse?.name || '-' }} · {{ warehouse?.code || '-' }}</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <div class="warehouse-location-body">
        <form class="warehouse-location-form" @submit.prevent="emit('save')">
          <div class="section-title compact">
            <h3>{{ editingId == null ? '新增货位' : '编辑货位' }}</h3>
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
                <button class="btn-icon" type="button" aria-label="搜索固定商品" @click="searchProducts">
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
            <button class="btn" type="button" @click="emit('create')">
              <Plus :size="16" />
              新增
            </button>
            <button class="btn btn-primary" type="submit" :disabled="saving">
              <Save :size="16" />
              {{ saving ? '保存中' : '保存货位' }}
            </button>
          </div>
        </form>

        <div class="warehouse-location-table-panel">
          <div class="warehouse-location-tools">
            <strong>货位明细</strong>
            <button class="btn" type="button" @click="emit('refresh')">
              <RefreshCw :size="16" />
              刷新
            </button>
          </div>
          <div class="warehouse-location-table-wrap">
            <table class="master-table warehouse-location-table">
              <thead>
                <tr>
                  <th>货位编码</th>
                  <th>货位类型</th>
                  <th>容量上限</th>
                  <th>固定商品</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="7" class="approval-empty">正在加载...</td>
                </tr>
                <tr v-else-if="!rows.length">
                  <td colspan="7" class="approval-empty">暂无货位，请先新增</td>
                </tr>
                <template v-else>
                  <tr v-for="row in rows" :key="row.locationId">
                    <td>{{ row.locationCode }}</td>
                    <td>{{ row.locationType }}</td>
                    <td>{{ row.capacityLimit ?? '-' }}</td>
                    <td>{{ row.productCode ? `${row.productCode} ${row.productName || ''}` : '-' }}</td>
                    <td>
                      <span class="status-badge" :class="{ warning: row.status !== 1 }">
                        {{ row.statusLabel }}
                      </span>
                    </td>
                    <td>{{ row.updateTime || '-' }}</td>
                    <td>
                      <button type="button" class="btn-text" @click="emit('edit', row)">编辑</button>
                      <button type="button" class="btn-text btn-text-danger" @click="emit('remove', row)">
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
</style>
