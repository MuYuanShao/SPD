<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, Save, Search, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import PaginationControls from '../common/PaginationControls.vue'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

const props = defineProps<{
  mode: 'create' | 'edit'
  safetyForm: {
    deptCode: string
    deptName: string
    warehouseName: string
    templateCode: string
    templateId?: number
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
  warehouses: Array<{ name: string; dept: string; deptCode: string }>
  filteredCount: number
  page: number
  size: number
  loading: boolean
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'submit'): void
  (event: 'select-template', row: QuotaTemplateRow): void
  (event: 'select-warehouse', warehouse: { name: string; dept: string; deptCode: string }): void
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
}>()

function isSelectedTemplate(row: QuotaTemplateRow) {
  const sameTemplate = props.safetyForm.templateId != null
    ? row.templateId === props.safetyForm.templateId
    : row.templateCode === props.safetyForm.templateCode
  return sameTemplate && row.productCode === props.safetyForm.productCode
}

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

function selectWarehouse(warehouse: { name: string; dept: string; deptCode: string }) {
  warehouseDropdownOpen.value = false
  warehouseKeyword.value = warehouse.name
  emit('select-warehouse', warehouse)
}
</script>

<template>
  <div class="attachment-preview-mask" @click.self="$emit('close')">
    <section class="qs-dialog" role="dialog" aria-modal="true">
      <header class="qs-dialog-header">
        <div class="qs-dialog-title">
          <p>定数安全量</p>
          <h3>{{ mode === 'create' ? '新增科室安全量' : '编辑科室安全量' }}</h3>
          <span v-if="mode !== 'create'" class="qs-dialog-tag">编辑模式</span>
        </div>
        <button class="qs-btn qs-btn-ghost" type="button" aria-label="关闭" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>

      <div class="qs-dialog-body">
        <!-- 左侧表单 -->
        <div class="qs-form-section">
          <div class="qs-form-header">
            <strong>安全量设置</strong>
          </div>

          <div class="qs-form-fields">
            <label class="qs-field qs-field-wide">
              <span>定数包（先选择）</span>
              <input
                :value="safetyForm.templateCode ? `${safetyForm.templateCode} / ${safetyForm.productCode}` : ''"
                readonly
                placeholder="在右侧定数包列表中选择一条"
                class="qs-input-readonly"
              />
            </label>

            <label class="qs-field qs-field-wide qs-warehouse-field">
              <span>关联库房</span>
              <div class="qs-warehouse-search">
                <input
                  v-model.trim="warehouseKeyword"
                  type="search"
                  placeholder="输入库房名称"
                  aria-label="搜索库房"
                  @keydown.enter.prevent="warehouseDropdownOpen = true"
                  @focus="warehouseDropdownOpen = true"
                />
                <button class="qs-btn qs-btn-info qs-btn-icon" type="button" title="搜索库房" @click="warehouseDropdownOpen = true">
                  <Search :size="15" />
                </button>
              </div>
              <ul v-if="warehouseDropdownOpen" class="qs-warehouse-results">
                <li v-for="warehouse in filteredWarehouses" :key="warehouse.name">
                  <button type="button" @click="selectWarehouse(warehouse)">
                    {{ warehouse.name }}
                    <small>{{ warehouse.dept && warehouse.dept !== '-' ? warehouse.dept : '未关联科室' }}</small>
                  </button>
                </li>
                <li v-if="!filteredWarehouses.length" class="qs-warehouse-empty">没有匹配的库房</li>
              </ul>
              <div v-if="safetyForm.warehouseName" class="qs-warehouse-hint">
                <Check :size="12" />
                已选：{{ safetyForm.warehouseName }}，科室：{{ safetyForm.deptName || '未关联科室' }}
              </div>
            </label>

            <label class="qs-field">
              <span>安全下限</span>
              <input v-model.number="safetyForm.minQty" type="number" min="0" />
            </label>

            <label class="qs-field">
              <span>安全上限</span>
              <input v-model.number="safetyForm.maxQty" type="number" min="0" />
            </label>
          </div>

          <div class="qs-form-actions">
            <button class="qs-btn qs-btn-secondary" type="button" @click="$emit('close')">取消</button>
            <button class="qs-btn qs-btn-primary" type="button" @click="$emit('submit')">
              <Save :size="15" />
              {{ mode === 'create' ? '保存安全量' : '保存修改' }}
            </button>
          </div>
        </div>

        <!-- 右侧目录表 -->
        <div class="qs-catalog-section">
          <div class="qs-form-header">
            <strong>定数包目录</strong>
          </div>

          <div class="qs-catalog-toolbar">
            <input v-model="catalogQuery.templateCode" placeholder="模板编码" @keydown.enter="$emit('change-page', 1)" />
            <input v-model="catalogQuery.templateName" placeholder="模板名称" @keydown.enter="$emit('change-page', 1)" />
            <input v-model="catalogQuery.productName" placeholder="商品名称" @keydown.enter="$emit('change-page', 1)" />
            <button class="qs-btn qs-btn-info qs-btn-sm" type="button" @click="$emit('change-page', 1)">
              <Search :size="14" />
              搜索
            </button>
          </div>

          <div class="qs-catalog-table-wrap">
            <table class="qs-catalog-table">
              <colgroup>
                <col style="width: 170px" />
                <col style="width: 170px" />
                <col style="width: 190px" />
                <col style="width: 220px" />
                <col style="width: 120px" />
                <col style="width: 100px" />
                <col style="width: 90px" />
                <col style="width: 120px" />
              </colgroup>
              <thead>
                <tr>
                  <th>模板编码</th>
                  <th>模板名称</th>
                  <th>商品编码</th>
                  <th>商品名称</th>
                  <th>规格</th>
                  <th>包内数量</th>
                  <th>状态</th>
                  <th class="col-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in templates"
                  :key="`${row.templateId}:${row.productCode}`"
                  class="qs-catalog-row"
                  :class="{ selected: isSelectedTemplate(row) }"
                  @dblclick="$emit('select-template', row)"
                >
                  <td :title="row.templateCode"><span class="cell-code">{{ row.templateCode }}</span></td>
                  <td :title="row.templateName"><strong>{{ row.templateName }}</strong></td>
                  <td :title="row.productCode">{{ row.productCode }}</td>
                  <td :title="row.productName">{{ row.productName }}</td>
                  <td :title="row.specModel">{{ row.specModel || '-' }}</td>
                  <td><span class="cell-qty">{{ row.quantity }} {{ row.unit }}</span></td>
                  <td>
                    <span class="status-badge" :class="row.status === '禁用' || row.status === 'disabled' ? 'disabled' : 'enabled'">
                      {{ formatStatusText(row.status) }}
                    </span>
                  </td>
                  <td class="col-action">
                    <button class="qs-btn qs-btn-primary qs-btn-sm" type="button" @click="$emit('select-template', row)">
                      <Check :size="12" />
                      选择
                    </button>
                  </td>
                </tr>
                <tr v-if="!filteredCount">
                  <td colspan="8" class="qs-empty">没有找到匹配的定数包目录</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="qs-catalog-pagination">
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
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================
   Dialog container
   ============================================================ */
.qs-dialog {
  width: min(1280px, calc(100vw - 32px)) !important;
  max-height: 96vh !important;
  min-height: 760px !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15, 42, 58, 0.35);
  overflow: hidden;
}

.qs-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  flex-shrink: 0;
}

.qs-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.qs-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qs-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.qs-dialog-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.3);
  color: #dcfce7;
  font-size: 12px;
  font-weight: 600;
}

/* ============================================================
   Body: left form + right catalog
   ============================================================ */
.qs-dialog-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 900px) {
  .qs-dialog-body {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }
}

/* ============================================================
   Left: form section
   ============================================================ */
.qs-form-section {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e1e9ee;
  background: #f9fbfc;
  overflow: hidden;
}

.qs-form-header {
  padding: 12px 18px;
  border-bottom: 1px solid #e1e9ee;
  background: linear-gradient(135deg, #f8fafc, #eef2f7);
  flex-shrink: 0;
}

.qs-form-header strong {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.qs-form-fields {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  padding: 16px 18px;
  flex: 1;
  overflow-y: auto;
  align-content: start;
}

.qs-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #566b77;
  font-weight: 500;
}

.qs-field-wide {
  grid-column: 1 / -1;
}

.qs-field input {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.qs-field input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.qs-input-readonly {
  background: #f0f3f5;
  color: #6b7c8f;
  cursor: not-allowed;
}

/* Warehouse search */
.qs-warehouse-field {
  position: relative;
}

.qs-warehouse-search {
  display: flex;
  gap: 6px;
  align-items: center;
}

.qs-warehouse-search input {
  flex: 1;
  min-width: 0;
}

.qs-warehouse-results {
  position: absolute;
  z-index: 30;
  top: calc(100% - 2px);
  left: 0;
  right: 0;
  max-height: 200px;
  overflow: auto;
  border: 1px solid #cfdfe7;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
  padding: 4px;
  list-style: none;
  margin: 0;
}

.qs-warehouse-results li button {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #25384a;
  padding: 8px 10px;
  text-align: left;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.qs-warehouse-results li button:hover {
  background: #eef7f8;
}

.qs-warehouse-results li button small {
  color: #94a3b8;
  font-size: 12px;
}

.qs-warehouse-empty {
  color: #6b7c8f;
  padding: 8px 10px;
  font-size: 12.5px;
}

.qs-warehouse-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #0d9488;
  font-size: 12px;
  font-weight: 600;
  padding-top: 2px;
}

/* Form actions */
.qs-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid #e1e9ee;
  background: #f5f8fa;
  flex-shrink: 0;
}

/* ============================================================
   Right: catalog table
   ============================================================ */
.qs-catalog-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.qs-catalog-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #ffffff;
  border-bottom: 1px solid #e1e9ee;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.qs-catalog-toolbar input {
  min-height: 34px;
  padding: 4px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 7px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
  min-width: 110px;
}

.qs-catalog-toolbar input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.qs-catalog-table-wrap {
  flex: 1;
  min-height: 480px;
  overflow: auto;
}

.qs-catalog-table {
  width: 100%;
  min-width: 1180px;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
  color: #172b3a;
}

.qs-catalog-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 9px 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: #ffffff;
  background: linear-gradient(135deg, #475569, #334155);
  white-space: nowrap;
}

.qs-catalog-table thead th.col-action {
  text-align: center;
  width: 120px;
}

.qs-catalog-table tbody td {
  padding: 8px 12px;
  border-bottom: 1px solid #eef2f5;
  vertical-align: middle;
  white-space: normal;
  overflow-wrap: anywhere;
  overflow: hidden;
}

.qs-catalog-table tbody tr:nth-child(even) td {
  background: #f9fbfc;
}

.qs-catalog-row {
  cursor: pointer;
  transition: background 0.15s;
}

.qs-catalog-row:hover td {
  background: #eef7f8;
}

.qs-catalog-row.selected td {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.08));
}

.qs-catalog-row.selected td:first-child {
  border-left: 3px solid #6366f1;
}

.qs-catalog-table tbody td.col-action {
  text-align: center;
  white-space: nowrap;
}

.qs-catalog-table tbody td.col-action .qs-btn {
  white-space: nowrap;
  flex-shrink: 0;
}

.qs-empty {
  padding: 32px 14px;
  text-align: center;
  color: #6b7c8f;
}

.qs-catalog-pagination {
  padding: 10px 14px;
  background: #f5f8fa;
  border-top: 1px solid #e1e9ee;
  flex-shrink: 0;
}

/* ============================================================
   Shared cells
   ============================================================ */
.cell-code {
  color: #0f6f78;
  font-weight: 700;
  font-size: 12.5px;
}

.cell-qty {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  font-weight: 700;
  font-size: 12px;
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.enabled {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.status-badge.disabled {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

/* ============================================================
   Buttons
   ============================================================ */
.qs-btn {
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

.qs-btn:hover { transform: translateY(-1px); }
.qs-btn:active { transform: translateY(0); }
.qs-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.qs-btn-icon {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
}

.qs-btn-sm {
  min-height: 28px; padding: 3px 12px; font-size: 12px;
}

.qs-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.4);
}

.qs-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.5);
}

.qs-btn-secondary {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.1), rgba(13, 148, 136, 0.1));
  color: #0d9488;
  border-color: rgba(20, 184, 166, 0.2);
}

.qs-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(20, 184, 166, 0.5);
}

.qs-btn-info {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(14, 165, 233, 0.1));
  color: #0891b2;
  border-color: rgba(6, 182, 212, 0.2);
}

.qs-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, #06b6d4, #0ea5e9);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(6, 182, 212, 0.5);
}

.qs-btn-ghost {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.qs-btn-ghost:hover { background: rgba(255, 255, 255, 0.3); }
</style>
