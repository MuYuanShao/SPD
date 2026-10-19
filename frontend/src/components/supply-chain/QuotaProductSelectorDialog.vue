<script setup lang="ts">
import { computed } from 'vue'
import { Check, Search, X } from '@lucide/vue'
import PaginationControls from '../common/PaginationControls.vue'

const props = defineProps<{
  open: boolean
  loading: boolean
  page: number
  size: number
  total: number
  productQuery: {
    productCode: string
    productName: string
  }
  products: Record<string, unknown>[]
}>()

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'search'): void
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
  (event: 'select', row: Record<string, unknown>): void
}>()

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

function closeDialog() {
  isOpen.value = false
}

function priceText(value: unknown) {
  return value ? `¥ ${Number(value).toFixed(2)}` : '-'
}
</script>

<template>
  <div v-if="isOpen" class="attachment-preview-mask" @click.self="closeDialog">
    <section class="qps-dialog" role="dialog" aria-modal="true">
      <header class="qps-dialog-header">
        <div class="qps-dialog-title">
          <p>定数包模板维护</p>
          <h3>选择商品</h3>
        </div>
        <button class="qps-btn qps-btn-ghost" type="button" aria-label="关闭" @click="closeDialog">
          <X :size="18" />
        </button>
      </header>

      <div class="qps-dialog-body">
        <div class="qps-toolbar">
          <div class="qps-search-group">
            <input v-model="productQuery.productCode" placeholder="商品编码" @keyup.enter="emit('search')" />
            <input v-model="productQuery.productName" placeholder="商品名称" @keyup.enter="emit('search')" />
          </div>
          <button class="qps-btn qps-btn-primary" type="button" @click="emit('search')">
            <Search :size="15" />
            查询
          </button>
        </div>

        <div class="qps-table-wrap">
          <table class="qps-table">
            <thead>
              <tr>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>厂家</th>
                <th>单价</th>
                <th>单位</th>
                <th class="col-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="qps-empty">正在加载商品列表...</td>
              </tr>
              <tr
                v-for="row in products"
                v-else
                :key="String(row.code)"
                class="qps-row"
                @dblclick="emit('select', row)"
              >
                <td><span class="cell-code">{{ row.code }}</span></td>
                <td><strong>{{ row.name }}</strong></td>
                <td>{{ row.spec || '-' }}</td>
                <td>{{ row.manufacturer || '-' }}</td>
                <td>{{ priceText(row.price) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="col-action">
                  <button class="qps-btn qps-btn-primary qps-btn-sm" type="button" @click="emit('select', row)">
                    <Check :size="13" />
                    选择
                  </button>
                </td>
              </tr>
              <tr v-if="!loading && !products.length">
                <td colspan="7" class="qps-empty">没有找到已启用定数管理的商品</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="qps-pagination">
          <PaginationControls
            :page="page"
            :size="size"
            :total="total"
            :loading="loading"
            @change-page="emit('change-page', $event)"
            @change-size="emit('change-size', $event)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.qps-dialog {
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

.qps-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
}

.qps-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.qps-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qps-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.qps-dialog-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.qps-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: #f5f8fa;
  border-bottom: 1px solid #e1e9ee;
  flex-wrap: wrap;
}

.qps-search-group {
  display: flex;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.qps-search-group input {
  min-height: 36px;
  padding: 5px 12px;
  border: 1px solid #cfdfe7;
  border-radius: 8px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
  min-width: 140px;
}

.qps-search-group input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.qps-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.qps-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
  font-size: 13px;
  color: #172b3a;
}

.qps-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: #ffffff;
  background: linear-gradient(135deg, #475569, #334155);
  white-space: nowrap;
}

.qps-table thead th.col-action {
  text-align: center;
  width: 90px;
}

.qps-table tbody td {
  padding: 9px 14px;
  border-bottom: 1px solid #eef2f5;
  vertical-align: middle;
}

.qps-table tbody tr:nth-child(even) td {
  background: #f9fbfc;
}

.qps-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}

.qps-table tbody tr:hover td {
  background: #eef7f8;
}

.qps-table tbody td.col-action {
  text-align: center;
}

.cell-code {
  color: #0f6f78;
  font-weight: 700;
  font-size: 12.5px;
}

.qps-empty {
  padding: 32px 14px;
  text-align: center;
  color: #6b7c8f;
}

.qps-pagination {
  padding: 12px 18px;
  background: #f5f8fa;
  border-top: 1px solid #e1e9ee;
}

/* Buttons */
.qps-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 5px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.qps-btn:hover { transform: translateY(-1px); }
.qps-btn:active { transform: translateY(0); }
.qps-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.qps-btn-sm {
  min-height: 28px;
  padding: 3px 12px;
  font-size: 12px;
}

.qps-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.4);
}

.qps-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.5);
}

.qps-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.qps-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
