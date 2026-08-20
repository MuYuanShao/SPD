<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Layers3, Search, X } from '@lucide/vue'
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
  productOptions: WarehouseProductOption[]
  productsLoading: boolean
}>()

const productKeyword = ref('')
const filteredProducts = computed(() => {
  const keyword = productKeyword.value.trim().toLowerCase()
  if (!keyword) return props.productOptions
  return props.productOptions.filter((product) =>
    [product.productCode, product.productName, product.specModel]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword))
  )
})

watch(() => props.open, (open) => {
  if (open) productKeyword.value = ''
})

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

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')">
    <section class="supplier-dialog warehouse-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>库房 / 货位管理</p>
          <h3>{{ mode === 'create' ? '新增库房' : '编辑库房' }}</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid" @submit.prevent="emit('save')">
        <label>
          <span>库房编码</span>
          <input v-model.trim="form.warehouseCode" :readonly="mode === 'edit'" required />
        </label>
        <label>
          <span>库房名称</span>
          <input v-model.trim="form.warehouseName" required />
        </label>
        <label>
          <span>库房类型</span>
          <input v-model.trim="form.warehouseType" placeholder="中心库、科室二级库、虚拟库" required />
        </label>
        <label>
          <span>所属院区</span>
          <select v-model.trim="form.campusName" required>
            <option value="">请选择院区</option>
            <option v-for="campus in campusOptions" :key="campus.code" :value="campus.name">
              {{ campus.name }}
            </option>
          </select>
        </label>
        <label>
          <span>关联科室</span>
          <input v-model.trim="form.deptName" placeholder="按科室名称关联" />
        </label>
        <label>
          <span>库房状态</span>
          <select v-model.number="form.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
        <label>
          <span>是否参与统计</span>
          <select v-model="form.participateStats">
            <option :value="true">参与</option>
            <option :value="false">不参与</option>
          </select>
        </label>
        <label>
          <span>统计分类</span>
          <input v-model.trim="form.statsCategories" placeholder="耗材、试剂、高值" />
        </label>
        <section class="warehouse-product-picker wide" aria-label="商品绑定">
          <div class="warehouse-product-toolbar">
            <div>
              <strong>商品绑定</strong>
              <span>选择该库房允许管理的商品，可按编码、名称或规格搜索</span>
            </div>
            <b>已选 {{ form.productCodes.length }} 项</b>
          </div>

          <div class="warehouse-product-controls">
            <label class="warehouse-product-search">
              <Search :size="16" />
              <input v-model.trim="productKeyword" type="search" placeholder="搜索商品编码、名称或规格" />
            </label>
            <button
              class="btn"
              type="button"
              :disabled="productsLoading || !filteredProducts.length"
              @click="toggleAllFiltered"
            >
              <Layers3 :size="16" />
              全选 / 取消当前结果
            </button>
          </div>

          <div class="warehouse-product-options">
            <p v-if="productsLoading" class="warehouse-product-empty">正在加载可用商品...</p>
            <label v-for="product in filteredProducts" v-else :key="product.productCode" class="warehouse-product-option">
              <input
                v-model="form.productCodes"
                type="checkbox"
                :value="product.productCode"
                :disabled="form.productCodes.length >= 500 && !form.productCodes.includes(product.productCode)"
              />
              <span class="product-code">{{ product.productCode }}</span>
              <span class="product-name">{{ product.productName }}</span>
              <span class="product-spec">{{ product.specModel || '-' }}</span>
            </label>
            <p v-if="!productsLoading && !filteredProducts.length" class="warehouse-product-empty">没有匹配的可用商品</p>
          </div>
          <p class="warehouse-product-hint">商品绑定可后续编辑，单个库房最多绑定 500 项。</p>
        </section>

        <div class="product-form-actions wide">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">保存</button>
        </div>
      </form>
    </section>

  </div>
</template>
<style scoped>
.warehouse-dialog {
  width: min(920px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow: auto;
}

.warehouse-product-picker {
  grid-column: 1 / -1;
  overflow: hidden;
  border: 1px solid #dbe8ee;
  border-radius: 8px;
  background: #fff;
}

.warehouse-product-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5eef2;
  background: #f5f9fb;
}

.warehouse-product-toolbar > div {
  display: grid;
  gap: 3px;
}

.warehouse-product-toolbar strong {
  color: #172b3a;
  font-size: 15px;
}

.warehouse-product-toolbar span {
  color: #6b7c8f;
  font-size: 13px;
  font-weight: 500;
}

.warehouse-product-toolbar b {
  color: #0f6f78;
  white-space: nowrap;
}

.warehouse-product-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #eaf1f4;
}

.warehouse-product-search {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
  min-width: 240px;
  height: 38px;
  padding: 0 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #fff;
  color: #607487;
}

.warehouse-product-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  font: inherit;
}

.warehouse-product-options {
  max-height: 280px;
  overflow: auto;
}

.warehouse-product-option {
  display: grid;
  grid-template-columns: 24px minmax(110px, 0.75fr) minmax(180px, 1.35fr) minmax(130px, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 6px 12px;
  border-bottom: 1px solid #eef3f5;
  color: #33485c;
  cursor: pointer;
}

.warehouse-product-option:hover {
  background: #f7fbfc;
}

.warehouse-product-option input {
  width: 16px;
  height: 16px;
}

.product-code {
  color: #0f6f78;
  font-weight: 700;
}

.product-name {
  color: #172b3a;
  font-weight: 650;
}

.product-spec,
.warehouse-product-empty,
.warehouse-product-hint {
  color: #6b7c8f;
}

.warehouse-product-empty {
  margin: 0;
  padding: 32px 12px;
  text-align: center;
}

.warehouse-product-hint {
  margin: 0;
  padding: 8px 12px;
  border-top: 1px solid #e5eef2;
  background: #fbfdfe;
  font-size: 13px;
}

@media (max-width: 760px) {
  .warehouse-product-controls {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .warehouse-product-search {
    flex-basis: 100%;
  }

  .warehouse-product-option {
    grid-template-columns: 24px 110px 1fr;
  }

  .product-spec {
    display: none;
  }
}
</style>
