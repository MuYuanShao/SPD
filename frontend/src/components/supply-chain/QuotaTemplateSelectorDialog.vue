<script setup lang="ts">
import { computed } from 'vue'
import { Check, CheckSquare, Square, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

const props = defineProps<{
  open: boolean
  templateQuery: {
    templateCode: string
    templateName: string
  }
  showAll: boolean
  templates: QuotaTemplateRow[]
}>()

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'update:showAll', value: boolean): void
  (event: 'select', row: QuotaTemplateRow): void
}>()

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

const includeDisabled = computed({
  get: () => props.showAll,
  set: (value) => emit('update:showAll', value)
})

function closeDialog() {
  isOpen.value = false
}
</script>

<template>
  <div v-if="isOpen" class="attachment-preview-mask" @click.self="closeDialog">
    <section class="qts-dialog" role="dialog" aria-modal="true">
      <header class="qts-dialog-header">
        <div class="qts-dialog-title">
          <p>打包任务确认</p>
          <h3>选择定数包模板</h3>
        </div>
        <button class="qts-btn qts-btn-ghost" type="button" aria-label="关闭" @click="closeDialog">
          <X :size="18" />
        </button>
      </header>

      <div class="qts-dialog-body">
        <div class="qts-toolbar">
          <div class="qts-search-group">
            <input v-model="templateQuery.templateCode" placeholder="模板编码" />
            <input v-model="templateQuery.templateName" placeholder="模板名称" />
          </div>
          <label class="qts-toggle">
            <span class="qts-toggle-box" :class="{ on: includeDisabled }">
              <Check v-if="includeDisabled" :size="11" />
            </span>
            <span>显示已停用</span>
          </label>
        </div>

        <div class="qts-table-wrap">
          <table class="qts-table">
            <colgroup>
              <col style="min-width: 130px" />
              <col style="min-width: 150px" />
              <col style="min-width: 180px" />
              <col style="min-width: 120px" />
              <col style="min-width: 150px" />
              <col style="min-width: 110px" />
              <col style="min-width: 80px" />
              <col style="min-width: 110px" />
            </colgroup>
            <thead>
              <tr>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>商品名称</th>
                <th>规格</th>
                <th>厂家</th>
                <th>包内数量</th>
                <th>状态</th>
                <th class="col-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in templates"
                :key="row.templateId"
                class="qts-row"
                @dblclick="emit('select', row)"
              >
                <td><span class="cell-code">{{ row.templateCode }}</span></td>
                <td><strong>{{ row.templateName }}</strong></td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td><span class="cell-qty">{{ row.quantity }} {{ row.unit }}</span></td>
                <td>
                  <span class="status-badge" :class="row.status === '禁用' || row.status === 'disabled' ? 'disabled' : 'enabled'">
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td class="col-action">
                  <button class="qts-btn qts-btn-primary qts-btn-sm" type="button" @click="emit('select', row)">
                    <Check :size="13" />
                    选择
                  </button>
                </td>
              </tr>
              <tr v-if="!templates.length">
                <td colspan="8" class="qts-empty">没有找到匹配的定数包模板</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.qts-dialog {
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

.qts-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
}

.qts-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.qts-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qts-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.qts-dialog-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.qts-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: #f5f8fa;
  border-bottom: 1px solid #e1e9ee;
  flex-wrap: wrap;
}

.qts-search-group {
  display: flex;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.qts-search-group input {
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

.qts-search-group input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.qts-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #566b77;
  font-weight: 500;
  white-space: nowrap;
  user-select: none;
}

.qts-toggle-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1.5px solid #cbd5e1;
  border-radius: 4px;
  background: #ffffff;
  color: transparent;
  transition: background 0.18s, border-color 0.18s, color 0.18s;
  flex-shrink: 0;
}

.qts-toggle-box.on {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-color: transparent;
  color: #ffffff;
}

.qts-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.qts-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
  color: #172b3a;
}

.qts-table thead th {
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

.qts-table thead th.col-action {
  text-align: center;
  width: 120px;
}

.qts-table tbody td {
  padding: 9px 14px;
  border-bottom: 1px solid #eef2f5;
  vertical-align: middle;
}

.qts-table tbody tr:nth-child(even) td {
  background: #f9fbfc;
}

.qts-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}

.qts-table tbody tr:hover td {
  background: #eef7f8;
}

.qts-table tbody td.col-action {
  text-align: center;
  white-space: nowrap;
}

.qts-table tbody td.col-action .qts-btn {
  white-space: nowrap;
  flex-shrink: 0;
}

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

.qts-empty {
  padding: 32px 14px;
  text-align: center;
  color: #6b7c8f;
}

.qts-row {
  font-size: 13px;
}

/* Buttons */
.qts-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 4px 12px;
  border: 1px solid transparent;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.qts-btn:hover { transform: translateY(-1px); }
.qts-btn:active { transform: translateY(0); }
.qts-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.qts-btn-sm {
  min-height: 26px;
  padding: 2px 10px;
  font-size: 12px;
}

.qts-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 3px 8px -3px rgba(99, 102, 241, 0.4);
}

.qts-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 5px 12px -3px rgba(99, 102, 241, 0.5);
}

.qts-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.qts-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
