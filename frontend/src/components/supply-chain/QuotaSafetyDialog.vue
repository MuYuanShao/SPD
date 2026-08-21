<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Save, Search, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import PaginationControls from '../common/PaginationControls.vue'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

const props = defineProps<{
  mode: 'create' | 'edit'
  safetyForm: {
    deptName: string
    warehouseName: string
    templateCode: string
    productCode: string
    minQty: number
    maxQty: number
  }
  catalogQuery: {
    templateCode: string
    templateName: string
    deptName: string
    productName: string
  }
  templates: QuotaTemplateRow[]
  warehouses: Array<{ name: string; dept: string }>
  filteredCount: number
  page: number
  size: number
  loading: boolean
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'submit'): void
  (event: 'select-template', row: QuotaTemplateRow): void
  (event: 'select-warehouse', warehouse: { name: string; dept: string }): void
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
}>()

const warehouseKeyword = ref('')
const warehouseDropdownOpen = ref(false)

watch(
  () => props.mode,
  () => {
    warehouseKeyword.value = props.mode === 'edit' ? props.safetyForm.warehouseName : ''
    warehouseDropdownOpen.value = false
  }
)

const filteredWarehouses = computed(() => {
  const keyword = warehouseKeyword.value.trim()
  if (!keyword) return props.warehouses
  return props.warehouses.filter(
    (warehouse) => warehouse.name.includes(keyword) || (warehouse.dept || '').includes(keyword)
  )
})

function selectWarehouse(warehouse: { name: string; dept: string }) {
  warehouseDropdownOpen.value = false
  warehouseKeyword.value = warehouse.name
  emit('select-warehouse', warehouse)
}
</script>

<template>
  <div class="attachment-preview-mask" @click.self="$emit('close')">
    <section class="supplier-dialog product-selector-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>定数安全量</p>
          <h3>{{ mode === 'create' ? '新增科室安全量' : '编辑科室安全量' }}</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>
      <div class="product-selector-body">
        <div class="supplier-form-grid">
          <label class="wide">
            <span>定数包（先选择）</span>
            <input :value="safetyForm.templateCode ? `${safetyForm.templateCode} / ${safetyForm.productCode}` : ''" readonly placeholder="在下方定数包列表中选择一条" />
          </label>
          <label class="wide catalog-search-field">
            <span>关联库房</span>
            <div class="catalog-search-box">
              <input
                v-model.trim="warehouseKeyword"
                type="search"
                placeholder="输入库房名称回车或点击放大镜搜索"
                aria-label="搜索库房"
                @keydown.enter.prevent="warehouseDropdownOpen = true"
                @focus="warehouseDropdownOpen = true"
              />
              <button class="btn-icon search-trigger" type="button" title="搜索库房" @click="warehouseDropdownOpen = true">
                <Search :size="16" />
              </button>
            </div>
            <ul v-if="warehouseDropdownOpen" class="catalog-search-results" role="listbox">
              <li v-for="warehouse in filteredWarehouses" :key="warehouse.name">
                <button type="button" role="option" @click="selectWarehouse(warehouse)">
                  {{ warehouse.name }}（{{ warehouse.dept && warehouse.dept !== '-' ? warehouse.dept : '未关联科室' }}）
                </button>
              </li>
              <li v-if="!filteredWarehouses.length" class="catalog-search-empty">没有匹配的库房</li>
            </ul>
            <small v-if="safetyForm.warehouseName" class="catalog-field-hint">
              已选库房：{{ safetyForm.warehouseName }}，科室自动带出：{{ safetyForm.deptName || '未关联科室' }}
            </small>
          </label>
          <label>
            <span>安全下限</span>
            <input v-model.number="safetyForm.minQty" type="number" min="0" />
          </label>
          <label>
            <span>安全上限</span>
            <input v-model.number="safetyForm.maxQty" type="number" min="0" />
          </label>
          <div class="approval-action-row wide">
            <button class="btn btn-primary" type="button" @click="$emit('submit')">
              <Save :size="18" />
              {{ mode === 'create' ? '保存安全量' : '保存修改' }}
            </button>
            <button class="btn" type="button" @click="$emit('close')">取消</button>
          </div>
        </div>
        <div class="product-selector-search">
          <input v-model="catalogQuery.templateCode" placeholder="模板编码" @keydown.enter="$emit('change-page', 1)" />
          <input v-model="catalogQuery.templateName" placeholder="模板名称" @keydown.enter="$emit('change-page', 1)" />
          <input v-model="catalogQuery.productName" placeholder="商品名称" @keydown.enter="$emit('change-page', 1)" />
          <button class="btn btn-sm" type="button" @click="$emit('change-page', 1)">
            <Search :size="14" />
            搜索
          </button>
        </div>
        <div class="table-scroll">
          <table class="master-table compact-table">
            <thead>
              <tr>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格</th>
                <th>包内数量</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in templates"
                :key="row.templateId"
                class="clickable-row"
                :class="{ 'selected-row': row.templateCode === safetyForm.templateCode }"
                @dblclick="$emit('select-template', row)"
              >
                <td>{{ row.templateCode }}</td>
                <td><strong>{{ row.templateName }}</strong></td>
                <td>{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel }}</td>
                <td>{{ row.quantity }} {{ row.unit }}</td>
                <td>
                  <span :class="['status-badge', row.status === '禁用' || row.status === 'disabled' ? 'disabled' : 'enabled']">
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td>
                  <button class="btn-text" type="button" @click="$emit('select-template', row)">选择</button>
                </td>
              </tr>
              <tr v-if="!filteredCount">
                <td colspan="8" class="approval-empty">没有找到匹配的定数包目录</td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="page"
          :size="size"
          :total="filteredCount"
          :loading="loading"
          :page-size-options="[5, 10, 20, 50]"
          @change-page="$emit('change-page', $event)"
          @change-size="$emit('change-size', $event)"
        />
      </div>
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

.catalog-search-results {
  position: absolute;
  z-index: 30;
  top: 100%;
  left: 0;
  right: 0;
  margin: 4px 0 0;
  max-height: 200px;
  overflow: auto;
  border: 1px solid #dbe8ee;
  border-radius: 7px;
  background: #fff;
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

.selected-row {
  background: #eef8f6;
}

.catalog-field-hint {
  color: #0f6f78;
  font-size: 12px;
}
</style>
