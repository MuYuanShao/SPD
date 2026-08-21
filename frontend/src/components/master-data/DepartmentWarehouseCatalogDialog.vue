<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Boxes, Building2, Save, Search, X } from '@lucide/vue'
import type {
  DepartmentWarehouseCatalogPayload,
  DepartmentWarehouseRelation
} from '../../api/masterData'
import type { ClosureOptions } from '../../api/operationalClosure'

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: DepartmentWarehouseCatalogPayload
  options: ClosureOptions
  warehouses: DepartmentWarehouseRelation[]
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
const productKeyword = ref('')
const productDropdownOpen = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) {
      deptKeyword.value = ''
      productKeyword.value = ''
      deptDropdownOpen.value = false
      productDropdownOpen.value = false
    }
  }
)

const filteredDepartments = computed(() => {
  const keyword = deptKeyword.value.trim()
  if (!keyword) return props.options.departments
  return props.options.departments.filter(
    (dept) => dept.deptName.includes(keyword) || dept.deptCode.includes(keyword)
  )
})

const filteredProducts = computed(() => props.options.products)

function selectDept(deptName: string) {
  props.form.deptName = deptName
  deptDropdownOpen.value = false
  deptKeyword.value = ''
  emit('changeDept', deptName)
}

function submitProductSearch() {
  productDropdownOpen.value = true
  emit('searchProducts', productKeyword.value.trim())
}

function selectProduct(productCode: string) {
  props.form.productCode = productCode
  productDropdownOpen.value = false
}
</script>

<template>
  <div
    v-if="open"
    class="attachment-preview-mask"
    @click.self="emit('close')"
    @keydown.esc="emit('close')"
  >
    <section
      class="catalog-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="catalog-dialog-title"
      aria-describedby="catalog-dialog-description"
    >
      <header class="master-data-dialog-header catalog-dialog-header">
        <div>
          <p class="catalog-dialog-kicker"><Boxes :size="15" /> 科室库房目录</p>
          <h3 id="catalog-dialog-title">{{ mode === 'create' ? '新增可申领商品' : '编辑目录关系' }}</h3>
          <span id="catalog-dialog-description">设置科室在指定关联库房下可以申领的商品</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <form class="catalog-dialog-form" @submit.prevent="emit('save')">
        <section class="catalog-dialog-section" aria-labelledby="catalog-scope-heading">
          <div class="catalog-dialog-section-heading">
            <Building2 :size="19" aria-hidden="true" />
            <div>
              <strong id="catalog-scope-heading">申领范围</strong>
              <span>输入科室名称回车或点击放大镜搜索，再选择该科室已经关联的库房</span>
            </div>
          </div>
          <label class="catalog-search-field">
            <span>科室 *</span>
            <div class="catalog-search-box">
              <input
                v-model.trim="deptKeyword"
                type="search"
                placeholder="输入科室名称/编码搜索"
                aria-label="搜索科室"
                @keydown.enter.prevent="deptDropdownOpen = true"
                @focus="deptDropdownOpen = true"
              />
              <button class="btn-icon search-trigger" type="button" title="搜索科室" @click="deptDropdownOpen = true">
                <Search :size="16" />
              </button>
            </div>
            <ul v-if="deptDropdownOpen" class="catalog-search-results" role="listbox">
              <li v-for="dept in filteredDepartments" :key="dept.deptCode">
                <button type="button" role="option" @click="selectDept(dept.deptName)">
                  {{ dept.deptName }}（{{ dept.deptCode }}）
                </button>
              </li>
              <li v-if="!filteredDepartments.length" class="catalog-search-empty">未找到匹配科室</li>
            </ul>
            <small v-if="form.deptName" class="catalog-field-hint">已选科室：{{ form.deptName }}</small>
          </label>
          <label>
            <span>关联库房 *</span>
            <select
              v-model="form.warehouseName"
              required
              :disabled="!form.deptName"
              @change="emit('changeWarehouse', form.warehouseName)"
            >
              <option value="">请选择关联库房</option>
              <option v-for="warehouse in warehouses" :key="warehouse.code" :value="warehouse.name">
                {{ warehouse.name }}
              </option>
            </select>
            <small class="catalog-field-hint">库房列表直接来自科室管理中的科室库房关联关系</small>
          </label>
        </section>

        <section class="catalog-dialog-section" aria-labelledby="catalog-product-heading">
          <div class="catalog-dialog-section-heading">
            <Boxes :size="19" aria-hidden="true" />
            <div>
              <strong id="catalog-product-heading">目录商品</strong>
              <span>搜索范围为医院目录，已维护的目录不会展示</span>
            </div>
          </div>
          <label class="catalog-search-field">
            <span>商品 *</span>
            <div class="catalog-search-box">
              <input
                v-model.trim="productKeyword"
                type="search"
                placeholder="输入商品编码/名称/规格回车或点击放大镜搜索"
                aria-label="搜索商品"
                @keydown.enter.prevent="submitProductSearch"
                @focus="productDropdownOpen = true"
              />
              <button class="btn-icon search-trigger" type="button" title="搜索商品" @click="submitProductSearch">
                <Search :size="16" />
              </button>
            </div>
            <ul v-if="productDropdownOpen" class="catalog-search-results" role="listbox">
              <li v-for="product in filteredProducts" :key="product.productCode">
                <button type="button" role="option" @click="selectProduct(product.productCode)">
                  {{ product.productCode }} - {{ product.productName }}（{{ product.specModel || '-' }}）
                </button>
              </li>
              <li v-if="!filteredProducts.length" class="catalog-search-empty">没有匹配的可用商品</li>
            </ul>
            <small v-if="form.productCode" class="catalog-field-hint">已选商品：{{ form.productCode }}</small>
          </label>
          <label>
            <span>目录状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
        </section>

        <div class="master-data-dialog-actions catalog-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button class="btn btn-primary" type="submit">
            <Save :size="16" />
            {{ mode === 'create' ? '创建目录' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.catalog-search-field {
  position: relative;
}

.catalog-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.catalog-search-box input {
  flex: 1;
  min-width: 0;
}

.catalog-search-box .search-trigger {
  flex: 0 0 auto;
  height: 38px;
}

.catalog-search-results {
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
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
  padding: 4px;
  list-style: none;
}

.catalog-search-results li button {
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

.catalog-search-results li button:hover {
  background: #eef8f6;
}

.catalog-search-empty {
  color: #6b7c8f;
  padding: 8px 10px;
}
</style>
