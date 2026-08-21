<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Building2, Layers3, Save, Search, X } from '@lucide/vue'
import type {
  DepartmentWarehouseCatalogBatchPayload,
  DepartmentWarehouseRelation
} from '../../api/masterData'
import type { ClosureOptions } from '../../api/operationalClosure'

const props = defineProps<{
  open: boolean
  form: DepartmentWarehouseCatalogBatchPayload
  options: ClosureOptions
  warehouses: DepartmentWarehouseRelation[]
  saving: boolean
}>()

const emit = defineEmits<{
  close: []
  save: []
  changeDept: [deptName: string]
  changeWarehouse: [warehouseName: string]
  searchProducts: [keyword: string]
}>()

const deptKeyword = ref('')
const deptDropdownOpen = ref(false)

const filteredDepartments = computed(() => {
  const keyword = deptKeyword.value.trim()
  if (!keyword) return props.options.departments
  return props.options.departments.filter(
    (dept) => dept.deptName.includes(keyword) || dept.deptCode.includes(keyword)
  )
})

const productKeyword = ref('')
const filteredProducts = computed(() => props.options.products)

watch(() => props.open, (open) => {
  if (open) {
    productKeyword.value = ''
    deptKeyword.value = ''
    deptDropdownOpen.value = false
  }
})

function selectDept(deptName: string) {
  props.form.deptName = deptName
  deptDropdownOpen.value = false
  deptKeyword.value = ''
  emit('changeDept', deptName)
}

function submitProductSearch() {
  emit('searchProducts', productKeyword.value.trim())
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
</script>

<template>
  <div
    v-if="open"
    class="attachment-preview-mask"
    @click.self="!saving && emit('close')"
    @keydown.esc="!saving && emit('close')"
  >
    <section class="batch-catalog-dialog" role="dialog" aria-modal="true" aria-labelledby="batch-catalog-title" aria-describedby="batch-catalog-description">
      <header class="batch-catalog-header">
        <div>
          <p class="batch-catalog-kicker"><Layers3 :size="15" /> 科室库房目录</p>
          <h3 id="batch-catalog-title">批量目录维护</h3>
          <span id="batch-catalog-description">为一个科室库房一次维护多种可申领商品</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" :disabled="saving" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <form class="batch-catalog-form" @submit.prevent="emit('save')">
        <div class="batch-catalog-fields">
          <div class="batch-catalog-scope-heading">
            <Building2 :size="19" aria-hidden="true" />
            <div>
              <strong>申领范围</strong>
              <span>选择目标科室与其已关联库房，随后勾选该库房已绑定的商品</span>
            </div>
          </div>

          <label class="batch-dept-search">
            <span>科室 *</span>
            <div class="batch-dept-box">
              <input
                v-model.trim="deptKeyword"
                type="search"
                placeholder="输入科室名称/编码，回车或点击放大镜搜索"
                aria-label="搜索科室"
                :disabled="saving"
                @keydown.enter.prevent="deptDropdownOpen = true"
                @focus="deptDropdownOpen = true"
              />
              <button class="btn-icon search-trigger" type="button" title="搜索科室" :disabled="saving" @click="deptDropdownOpen = true">
                <Search :size="16" />
              </button>
            </div>
            <ul v-if="deptDropdownOpen" class="batch-dept-results" role="listbox">
              <li v-for="dept in filteredDepartments" :key="dept.deptCode">
                <button type="button" role="option" @click="selectDept(dept.deptName)">
                  {{ dept.deptName }}（{{ dept.deptCode }}）
                </button>
              </li>
              <li v-if="!filteredDepartments.length" class="batch-dept-empty">未找到匹配科室</li>
            </ul>
            <small v-if="form.deptName" class="batch-dept-hint">已选科室：{{ form.deptName }}</small>
          </label>

          <label>
            <span>关联库房 *</span>
            <select
              v-model="form.warehouseName"
              required
              :disabled="saving || !form.deptName"
              @change="emit('changeWarehouse', form.warehouseName)"
            >
              <option value="">请选择关联库房</option>
              <option v-for="warehouse in warehouses" :key="warehouse.code" :value="warehouse.name">
                {{ warehouse.name }}
              </option>
            </select>
          </label>

          <label>
            <span>目录状态</span>
            <select v-model.number="form.status" :disabled="saving">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
        </div>

        <section class="product-picker" aria-label="商品选择">
          <div class="product-picker-toolbar">
            <div class="catalog-filter-title">
              <Layers3 :size="17" aria-hidden="true" />
              <span>选择目录商品</span>
            </div>
            <label class="product-search">
              <Search :size="16" />
              <input
                v-model.trim="productKeyword"
                type="search"
                placeholder="输入商品编码/名称/规格，回车或点击放大镜搜索医院目录"
                aria-label="搜索目录商品"
                @keydown.enter.prevent="submitProductSearch"
              />
              <button class="btn-icon search-trigger" type="button" title="搜索商品" :disabled="saving || !form.warehouseName" @click="submitProductSearch">
                <Search :size="16" />
              </button>
            </label>
            <button class="btn" type="button" :disabled="saving || !filteredProducts.length" @click="toggleAllFiltered">
              <Layers3 :size="16" />
              全选/取消当前结果
            </button>
            <strong class="product-picker-count" aria-live="polite">已选 {{ form.productCodes.length }} 项</strong>
          </div>

          <div class="product-options">
            <label v-for="product in filteredProducts" :key="product.productCode" class="product-option">
              <input
                v-model="form.productCodes"
                type="checkbox"
                :value="product.productCode"
                :disabled="saving || (form.productCodes.length >= 500 && !form.productCodes.includes(product.productCode))"
              />
              <span class="product-code">{{ product.productCode }}</span>
              <span class="product-name">{{ product.productName }}</span>
              <span class="product-spec">{{ product.specModel || '-' }}</span>
            </label>
            <p v-if="!filteredProducts.length" class="product-empty">没有匹配的可用商品</p>
          </div>
          <p class="product-hint">已存在目录会更新状态，已删除目录会恢复；单次最多维护 500 项。</p>
        </section>

        <div class="batch-catalog-actions">
          <button class="btn" type="button" :disabled="saving" @click="emit('close')">取消</button>
          <button class="btn btn-primary" type="submit" :disabled="saving || !form.productCodes.length">
            <Save :size="16" />
            {{ saving ? '正在保存...' : `保存 ${form.productCodes.length} 项目录` }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.batch-catalog-dialog {
  width: min(900px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow: auto;
  background: #fff;
  border: 1px solid #dbe8ee;
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
}

.batch-catalog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-bottom: 1px solid #edf3f6;
}

.batch-catalog-header p,
.batch-catalog-header h3 {
  margin: 0;
}

.batch-catalog-header p {
  margin-bottom: 4px;
  color: #64748b;
  font-weight: 700;
}

.batch-catalog-header h3 {
  color: #102033;
  font-size: 21px;
}

.batch-catalog-header span {
  display: block;
  margin-top: 5px;
  color: #6b7c8f;
}

.batch-catalog-form {
  padding: 20px 22px;
}

.batch-catalog-fields {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 0.7fr;
  gap: 14px;
}

.batch-catalog-fields label {
  display: grid;
  gap: 7px;
  color: #4f6477;
  font-weight: 700;
}

.batch-catalog-fields select {
  min-height: 40px;
  border: 1px solid #d7e5ec;
  border-radius: 7px;
  padding: 0 11px;
  background: #fff;
  color: #102033;
  font: inherit;
}

.product-picker {
  margin-top: 18px;
  border: 1px solid #dbe8ee;
  border-radius: 8px;
  overflow: hidden;
}

.product-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #f5f9fb;
  border-bottom: 1px solid #e5eef2;
}

.product-search {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
  min-width: 220px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #fff;
  color: #607487;
}

.product-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  font: inherit;
}

.product-search .search-trigger {
  flex: 0 0 auto;
  height: 28px;
}

.batch-dept-search {
  position: relative;
}

.batch-dept-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.batch-dept-box input {
  flex: 1;
  min-width: 0;
  min-height: 40px;
  border: 1px solid #d7e5ec;
  border-radius: 7px;
  padding: 0 11px;
  background: #fff;
  color: #102033;
  font: inherit;
}

.batch-dept-results {
  position: absolute;
  z-index: 30;
  top: 100%;
  left: 0;
  right: 0;
  margin: 4px 0 0;
  max-height: 220px;
  overflow: auto;
  border: 1px solid #dbe8ee;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
  padding: 4px;
  list-style: none;
}

.batch-dept-results li button {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #25384a;
  padding: 8px 10px;
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.batch-dept-results li button:hover {
  background: #eef8f6;
}

.batch-dept-empty {
  color: #6b7c8f;
  padding: 8px 10px;
}

.batch-dept-hint {
  color: #0f6f78;
  font-size: 12px;
}

.product-picker-toolbar strong {
  color: #0f6f78;
  white-space: nowrap;
}

.product-options {
  max-height: 330px;
  overflow: auto;
}

.product-option {
  display: grid;
  grid-template-columns: 24px minmax(110px, 0.8fr) minmax(170px, 1.4fr) minmax(130px, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 6px 12px;
  border-bottom: 1px solid #eef3f5;
  color: #33485c;
  cursor: pointer;
}

.product-option:hover {
  background: #f7fbfc;
}

.product-option input {
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

.product-spec {
  color: #6b7c8f;
}

.product-empty,
.product-hint {
  margin: 0;
  color: #6b7c8f;
}

.product-empty {
  padding: 36px 12px;
  text-align: center;
}

.product-hint {
  padding: 9px 12px;
  border-top: 1px solid #e5eef2;
  background: #fbfdfe;
  font-size: 13px;
}

.batch-catalog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 16px;
}

@media (max-width: 760px) {
  .batch-catalog-fields {
    grid-template-columns: 1fr;
  }

  .product-picker-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .product-search {
    flex-basis: 100%;
  }

  .product-option {
    grid-template-columns: 24px 110px 1fr;
  }

  .product-spec {
    display: none;
  }
}
</style>
