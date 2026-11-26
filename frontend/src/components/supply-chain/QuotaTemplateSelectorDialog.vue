<script setup lang="ts">
import { computed } from 'vue'
import { Check, CheckSquare, Square, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

const props = defineProps<{
  open: boolean
  loading: boolean
  error: string
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
  (event: 'retry'): void
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
        <SpdButton label="" tone="primary" aria-label="关闭" @click="closeDialog">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
                  <SpdStatusChip
                    :tone="row.status === '禁用' || row.status === 'disabled' ? 'neutral' : 'success'"
                    :label="formatStatusText(row.status)"
                  />
                </td>
                <td class="col-action">
                  <SpdButton label="选择" @click="emit('select', row)">
          <template #icon><Check :size="13" /></template>
        </SpdButton>
                </td>
              </tr>
              <tr v-if="loading">
                <td colspan="8" class="qts-empty">正在加载打包模板…</td>
              </tr>
              <tr v-else-if="error">
                <td colspan="8" class="qts-empty" role="alert">
                  {{ error }}
                  <SpdButton label="重试" @click="emit('retry')"></SpdButton>
                </td>
              </tr>
              <tr v-else-if="!templates.length">
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
  background: var(--spd-p-bg);
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
}

.qts-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qts-dialog-title h3 {
  margin: 0;
  font-size: var(--spd-font-size-xl);
  font-weight: 600;
  color: var(--spd-p-bg);
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
  background: var(--spd-p-bg-subtle);
  border-bottom: 1px solid var(--spd-p-border);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  transition: border-color 0.18s, box-shadow 0.18s;
  min-width: 140px;
}

.qts-search-group input:focus {
  outline: none;
  border-color: var(--spd-p-primary);
  box-shadow: var(--spd-p-shadow-1);
}

.qts-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-muted);
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
  border: 1.5px solid var(--spd-p-border-strong);
  border-radius: 4px;
  background: var(--spd-p-bg);
  color: transparent;
  transition: background 0.18s, border-color 0.18s, color 0.18s;
  flex-shrink: 0;
}

.qts-toggle-box.on {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  border-color: transparent;
  color: var(--spd-p-bg);
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
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text);
}

.qts-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px;
  text-align: left;
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  letter-spacing: 0.3px;
  color: var(--spd-p-bg);
  background: linear-gradient(135deg, var(--spd-p-text-secondary), var(--spd-p-text-secondary));
  white-space: nowrap;
}

.qts-table thead th.col-action {
  text-align: center;
  width: 120px;
}

.qts-table tbody td {
  padding: 9px 14px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
  vertical-align: middle;
}

.qts-table tbody tr:nth-child(even) td {
  background: var(--spd-p-bg-muted);
}

.qts-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}

.qts-table tbody tr:hover td {
  background: var(--spd-p-primary-bg);
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
  color: var(--spd-p-primary);
  font-weight: 700;
  font-size: var(--spd-font-size-base);
}

.cell-qty {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--spd-p-info) 10%, transparent);
  color: var(--spd-p-primary);
  font-weight: 700;
  font-size: var(--spd-font-size-base);
}

.qts-empty {
  padding: 32px 14px;
  text-align: center;
  color: var(--spd-p-text-secondary);
}

.qts-row {
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-sm);
}

.qts-btn-primary {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
}

.qts-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  box-shadow: var(--spd-p-shadow-2);
}

.qts-btn-ghost {
  width: 32px;
  min-height: 32px;
  padding: 0;
  justify-content: center;
  background: color-mix(in srgb, var(--spd-p-bg) 18%, transparent);
  color: var(--spd-p-bg);
  border-color: color-mix(in srgb, var(--spd-p-bg) 30%, transparent);
}

.qts-btn-ghost:hover {
  background: color-mix(in srgb, var(--spd-p-bg) 30%, transparent);
}
</style>
