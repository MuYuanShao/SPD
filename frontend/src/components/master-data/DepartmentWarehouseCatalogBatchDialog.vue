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
    deptKeyword.value = props.form.deptName
    deptDropdownOpen.value = false
  }
})

function selectDept(deptName: string) {
  props.form.deptName = deptName
  deptDropdownOpen.value = false
  deptKeyword.value = deptName
  emit('changeDept', deptName)
}

function submitProductSearch() {
  emit('searchProducts', productKeyword.value.trim())
}

function toggleAllFiltered() {
  const filteredCodes = filteredProducts.value.filter(product => product.selectable !== 0).map((product) => product.productCode)
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
        <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" :disabled="saving" @click="emit('close')">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
              <SpdButton icon-only variant="ghost" tone="neutral" class="search-trigger" type="button" title="搜索科室" :disabled="saving" @click="deptDropdownOpen = true">
                <template #icon><Search :size="16" /></template>
              </SpdButton>
            </div>
            <ul v-if="deptDropdownOpen" class="batch-dept-results" role="listbox">
              <li v-for="dept in filteredDepartments" :key="dept.deptCode">
                <button type="button" role="option" @click="selectDept(dept.deptName)">
                  {{ dept.deptName }}（{{ dept.deptCode }}）
                </button>
              </li>
              <li v-if="!filteredDepartments.length" class="batch-dept-empty">未找到匹配科室</li>
            </ul>
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
              <input
                v-model.trim="productKeyword"
                type="search"
                placeholder="输入商品编码/名称/规格，回车或点击放大镜搜索医院目录"
                aria-label="搜索目录商品"
                @keydown.enter.prevent="submitProductSearch"
              />
              <SpdButton icon-only variant="ghost" tone="neutral" class="search-trigger" type="button" title="搜索商品" :disabled="saving || !form.warehouseName" @click="submitProductSearch">
                <template #icon><Search :size="16" /></template>
              </SpdButton>
            </label>
            <SpdButton label="全选/取消当前结果" tone="primary" :disabled="saving || !filteredProducts.length" @click="toggleAllFiltered">
          <template #icon><Layers3 :size="16" /></template>
        </SpdButton>
            <strong class="product-picker-count" aria-live="polite">已选 {{ form.productCodes.length }} 项</strong>
          </div>

          <div class="product-options">
            <label
              v-for="product in filteredProducts"
              :key="product.productCode"
              class="product-option"
              :class="{ 'is-disabled': product.selectable === 0 }"
            >
              <input
                v-model="form.productCodes"
                type="checkbox"
                :value="product.productCode"
                :disabled="saving || product.selectable === 0 || (form.productCodes.length >= 500 && !form.productCodes.includes(product.productCode))"
              />
              <span class="product-code">{{ product.productCode }}</span>
              <span class="product-name">
                {{ product.productName }}
                <em v-if="product.selectable === 0" class="product-flag is-muted">未绑定库房</em>
                <em v-else-if="product.maintained === 1" class="product-flag is-maintained">已维护</em>
              </span>
              <span class="product-spec">{{ product.specModel || '-' }}</span>
            </label>
            <p v-if="!filteredProducts.length" class="product-empty">
              该库房尚未绑定可维护的目录商品，请先在「库房 / 货位管理」中维护商品绑定
            </p>
          </div>
          <p class="product-hint">已存在目录会更新状态，已删除目录会恢复；单次最多维护 500 项。</p>
        </section>

        <div class="batch-catalog-actions">
          <SpdButton label="取消" :disabled="saving" @click="emit('close')"></SpdButton>
          <SpdButton tone="primary" type="submit" :disabled="saving || !form.productCodes.length">
          <template #icon><Save :size="16" /></template>
          {{ saving ? '正在保存...' : `保存 ${form.productCodes.length} 项目录` }}
        </SpdButton>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.batch-catalog-dialog {
  --bcd-primary: var(--spd-primary, var(--spd-p-primary));
  --bcd-primary-dark: var(--spd-primary-dark, var(--spd-p-primary-strong));
  --bcd-primary-soft: color-mix(in srgb, var(--bcd-primary) 8%, var(--spd-p-bg));
  --bcd-primary-border: color-mix(in srgb, var(--bcd-primary) 26%, var(--spd-p-bg));
  --bcd-text: var(--spd-p-text);
  --bcd-muted: var(--spd-p-info-text);
  width: min(960px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow: auto;
  background: var(--spd-p-bg);
  border: 1px solid var(--bcd-primary-border);
  border-radius: 10px;
  box-shadow: var(--spd-p-shadow-dialog);
}

/* ===== 头部：主题色基调 ===== */
.batch-catalog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  color: var(--spd-p-bg);
  background: linear-gradient(
    135deg,
    var(--bcd-primary) 0%,
    var(--bcd-primary-dark) 100%
  );
  border-bottom: 1px solid var(--bcd-primary-border);
}

.batch-catalog-header p,
.batch-catalog-header h3 {
  margin: 0;
}

.batch-catalog-header p {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--spd-p-bg) 18%, transparent);
  color: var(--spd-p-bg);
  /* 与 .catalog-dialog-kicker 统一：--spd-font-size-sm + 字重 800 */
  font-size: var(--spd-font-size-sm);
  font-weight: 800;
}

.batch-catalog-header h3 {
  color: var(--spd-p-bg);
  font-size: var(--spd-font-size-2xl);
  font-weight: 700;
  letter-spacing: 0.3px;
}

.batch-catalog-header span {
  display: block;
  margin-top: 6px;
  color: color-mix(in srgb, var(--spd-p-bg) 88%, transparent);
  font-size: var(--spd-font-size-sm);
}

/* 关闭按钮在深色头部上保持可见 */
.batch-catalog-header .btn-icon {
  background: color-mix(in srgb, var(--spd-p-bg) 16%, transparent);
  color: var(--spd-p-bg);
  border-color: color-mix(in srgb, var(--spd-p-bg) 24%, transparent);
}

.batch-catalog-header .btn-icon:hover:not(:disabled) {
  background: color-mix(in srgb, var(--spd-p-bg) 28%, transparent);
  color: var(--spd-p-bg);
}

.batch-catalog-form {
  padding: 20px 22px;
}

/* ===== 申领范围：主题色浅底卡片 ===== */
.batch-catalog-fields {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 0.7fr;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--bcd-primary-border);
  border-radius: 10px;
  background: var(--bcd-primary-soft);
}

.batch-catalog-scope-heading {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--bcd-primary-border);
  color: var(--bcd-primary);
}

.batch-catalog-scope-heading div {
  display: grid;
  gap: 3px;
}

.batch-catalog-scope-heading strong {
  color: var(--bcd-text);
  /* 与 .catalog-dialog-section-heading strong 统一为 --spd-font-size-sm */
  font-size: var(--spd-font-size-sm);
  font-weight: 700;
}

.batch-catalog-scope-heading span {
  color: var(--bcd-muted);
  font-size: var(--spd-font-size-sm);
}

.batch-catalog-fields label {
  display: grid;
  gap: 7px;
  color: var(--spd-p-text-secondary);
  /* 与 .catalog-dialog-form label 对齐：字重 500 */
  font-weight: 500;
}

.batch-catalog-fields select {
  min-height: 40px;
  border: 1px solid var(--spd-p-primary-border);
  border-radius: 7px;
  padding: 0 11px;
  background: var(--spd-p-bg);
  color: var(--bcd-text);
  font: inherit;
}

/* ===== 商品选择区 ===== */
.product-picker {
  margin-top: 18px;
  border: 1px solid var(--bcd-primary-border);
  border-radius: 10px;
  overflow: hidden;
}

.product-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bcd-primary-soft);
  border-bottom: 1px solid var(--bcd-primary-border);
}

.catalog-filter-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--bcd-primary);
  /* 与 .batch-catalog-scope-heading strong 统一：--spd-font-size-sm + 字重 700 */
  font-size: var(--spd-font-size-sm);
  font-weight: 700;
  white-space: nowrap;
}

/* 搜索框：放大镜图标紧贴输入框右侧 */
.product-search {
  display: flex;
  align-items: center;
  gap: 0;
  flex: 1;
  min-width: 220px;
  height: 38px;
  padding-left: 10px;
  border: 1px solid var(--bcd-primary-border);
  border-radius: 7px;
  background: var(--spd-p-bg);
  color: var(--bcd-muted);
  overflow: hidden;
}

.product-search:focus-within {
  border-color: var(--bcd-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--bcd-primary) 14%, transparent);
}

.product-search input {
  min-width: 0;
  flex: 1;
  height: 100%;
  border: 0;
  outline: 0;
  padding: 0 8px 0 0;
  background: transparent;
  color: var(--bcd-text);
  font: inherit;
}

/* 搜索按钮：主题色实心，紧贴输入框右侧。
   全局 .search-trigger 用 !important 白底，这里同样加 !important 并提高特异性。 */
/* 搜索触发按钮：与输入框贴合的「组合按钮」形态。
   宽度统一取调色板控件高度（--spd-p-control-height），
   左圆角去掉以与输入框贴合，右侧圆角保留。 */
.product-search .search-trigger {
  flex: 0 0 auto;
  width: var(--spd-p-control-height);
  height: 100%;
  border: 0 !important;
  border-left: 1px solid var(--bcd-primary-border) !important;
  border-top-right-radius: var(--spd-p-control-radius) !important;
  border-bottom-right-radius: var(--spd-p-control-radius) !important;
  border-radius: 0 var(--spd-p-control-radius) var(--spd-p-control-radius) 0 !important;
  background: var(--bcd-primary) !important;
  color: var(--spd-p-bg) !important;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.product-search .search-trigger:hover:not(:disabled) {
  background: var(--bcd-primary-dark) !important;
  color: var(--spd-p-bg) !important;
}

.product-search .search-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-dept-search {
  position: relative;
}

.batch-dept-box {
  display: flex;
  align-items: center;
  gap: 0;
  height: 40px;
  border: 1px solid var(--spd-p-primary-border);
  border-radius: 7px;
  background: var(--spd-p-bg);
  overflow: hidden;
}

.batch-dept-box:focus-within {
  border-color: var(--bcd-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--bcd-primary) 14%, transparent);
}

.batch-dept-box input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  padding: 0 8px 0 11px;
  background: transparent;
  color: var(--bcd-text);
  font: inherit;
}

.batch-dept-box .search-trigger {
  flex: 0 0 auto;
  width: var(--spd-p-control-height);
  height: 100%;
  border: 0 !important;
  border-left: 1px solid var(--bcd-primary-border) !important;
  border-top-right-radius: var(--spd-p-control-radius) !important;
  border-bottom-right-radius: var(--spd-p-control-radius) !important;
  border-radius: 0 var(--spd-p-control-radius) var(--spd-p-control-radius) 0 !important;
  background: var(--bcd-primary) !important;
  color: var(--spd-p-bg) !important;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.batch-dept-box .search-trigger:hover:not(:disabled) {
  background: var(--bcd-primary-dark) !important;
  color: var(--spd-p-bg) !important;
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
  border: 1px solid var(--bcd-primary-border);
  border-radius: 7px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-3);
  padding: 4px;
  list-style: none;
}

.batch-dept-results li button {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--spd-p-text);
  padding: 8px 10px;
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.batch-dept-results li button:hover {
  background: var(--bcd-primary-soft);
  color: var(--bcd-primary);
}

.batch-dept-empty {
  color: var(--bcd-muted);
  padding: 8px 10px;
}

.batch-dept-hint {
  color: var(--bcd-primary);
  font-size: var(--spd-font-size-sm);
}

/* ===== 商品列表 ===== */
.product-picker-toolbar strong {
  color: var(--bcd-primary);
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
  border-bottom: 1px solid var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
  cursor: pointer;
}

.product-option:hover {
  background: var(--bcd-primary-soft);
}

.product-option input {
  width: 16px;
  height: 16px;
}

.product-code {
  color: var(--bcd-primary);
  font-weight: 700;
}

.product-name {
  color: var(--bcd-text);
  font-weight: 700;
}

.product-option.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.product-flag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: var(--spd-font-size-base);
  font-style: normal;
  font-weight: 600;
  vertical-align: middle;
}

.product-flag.is-muted {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-info-text);
}

.product-flag.is-maintained {
  background: var(--bcd-primary-soft);
  color: var(--bcd-primary);
  border: 1px solid var(--bcd-primary-border);
}

.product-spec {
  color: var(--bcd-muted);
}

.product-empty,
.product-hint {
  margin: 0;
  color: var(--bcd-muted);
}

.product-empty {
  padding: 36px 12px;
  text-align: center;
}

.product-hint {
  padding: 9px 12px;
  border-top: 1px solid var(--bcd-primary-border);
  background: var(--bcd-primary-soft);
  font-size: var(--spd-font-size-sm);
}

/* ===== 底部按钮背景色由 department-warehouse-catalog.css 的全局规则接管 =====
   （弹窗挂在 .master-page 内，需与 master-data-list.override.css 的
   .master-page .btn 白底 !important 规则竞争，必须放在全局样式中） */
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
