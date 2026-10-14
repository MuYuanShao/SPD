<script setup lang="ts">
/**
 * 打印模板调整
 * ---------------------------------------------------------------------
 * 维护定数包标签打印模板的字段、顺序与纸张设置。
 * 视觉沿用主数据模块（MdPageHeader / MdDataTableCard / MdFormDialog）
 * 的 md-* 设计语言，与其他菜单保持一致。
 */
import { computed, onMounted, ref } from 'vue'
import { ArrowDown, ArrowUp, Plus, RotateCcw, Trash2 } from '@lucide/vue'
import {
  fetchPrintTemplates,
  parseTemplateFields,
  updatePrintTemplate,
  type PrintTemplateField,
  type PrintTemplateRow
} from '../../api/printTemplates'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import MdFormDialog from '../../components/master-data/MdFormDialog.vue'
import MdFormSection from '../../components/master-data/MdFormSection.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdStatus from '../../components/master-data/MdStatus.vue'

const rows = ref<PrintTemplateRow[]>([])
const loading = ref(false)
const error = ref('')

const showEditor = ref(false)
const editing = ref<PrintTemplateRow | null>(null)
const fields = ref<PrintTemplateField[]>([])
const templateName = ref('')
const paperWidthMm = ref(40)
const paperHeightMm = ref(60)
const status = ref(1)
const remark = ref('')
const saving = ref(false)
const formError = ref('')
const newFieldCode = ref('')
const newFieldLabel = ref('')

const paperPresets = [
  { label: '60 × 40 mm（常见标签纸）', width: 60, height: 40 },
  { label: '40 × 60 mm（默认）', width: 40, height: 60 },
  { label: '100 × 70 mm', width: 100, height: 70 },
  { label: '100 × 100 mm', width: 100, height: 100 },
  { label: 'A4 纵向（210 × 297 mm）', width: 210, height: 297 }
]

const enabledFieldCount = computed(() => fields.value.filter(field => field.enabled).length)

const templateTypeText = (row: PrintTemplateRow) =>
  row.templateType === 'quota_label' ? '定数包标签' : row.templateType

const statusText = (row: PrintTemplateRow) => (row.status === 1 ? '启用' : '停用')

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await fetchPrintTemplates()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '打印模板加载失败'
  } finally {
    loading.value = false
  }
}

function openEditor(row: PrintTemplateRow) {
  editing.value = row
  templateName.value = row.templateName
  paperWidthMm.value = Number(row.paperWidthMm) || 40
  paperHeightMm.value = Number(row.paperHeightMm) || 60
  status.value = Number(row.status ?? 1)
  remark.value = row.remark ?? ''
  fields.value = parseTemplateFields(row.fieldsJson)
  newFieldCode.value = ''
  newFieldLabel.value = ''
  formError.value = ''
  showEditor.value = true
}

function applyPaperPreset(event: Event) {
  const preset = paperPresets.find(item => item.label === (event.target as HTMLSelectElement).value)
  if (!preset) return
  paperWidthMm.value = preset.width
  paperHeightMm.value = preset.height
}

function toggleField(field: PrintTemplateField) {
  field.enabled = !field.enabled
}

function moveField(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= fields.value.length) return
  const list = [...fields.value]
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
  fields.value = list
}

function addField() {
  const code = newFieldCode.value.trim()
  const label = newFieldLabel.value.trim()
  if (!code) {
    formError.value = '请填写新增字段的编码'
    return
  }
  if (fields.value.some(field => field.code === code)) {
    formError.value = '该字段编码已存在'
    return
  }
  fields.value = [...fields.value, { code, label: label || code, enabled: true }]
  newFieldCode.value = ''
  newFieldLabel.value = ''
  formError.value = ''
}

function removeField(field: PrintTemplateField) {
  fields.value = fields.value.filter(item => item.code !== field.code)
}

async function saveTemplate() {
  if (!editing.value) return
  if (!templateName.value.trim()) {
    formError.value = '模板名称为必填项'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await updatePrintTemplate(editing.value.templateType, {
      templateName: templateName.value.trim(),
      fieldsJson: JSON.stringify(fields.value),
      paperWidthMm: paperWidthMm.value,
      paperHeightMm: paperHeightMm.value,
      status: status.value,
      remark: remark.value.trim()
    })
    showEditor.value = false
    await loadList()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '打印模板保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <section class="md-page md-print-template-page">
    <MdPageHeader
      crumb="系统管理"
      title="打印模板调整"
      description="调整定数包标签打印模板的字段、顺序与纸张设置，保存后打印时生效。"
      :loading="loading"
      @refresh="loadList"
    />

    <p v-if="error" class="md-error">{{ error }}</p>

    <MdDataTableCard :meta-count="`共 ${rows.length} 个模板`" :loading="loading">
      <MdEmptyState v-if="loading" text="正在加载打印模板..." :loading="true" />
      <MdEmptyState v-else-if="error" :text="error" />
      <div v-else class="md-print-template-table-wrap">
        <table class="md-print-template-table">
          <thead>
            <tr>
              <th>模板编码</th>
              <th>模板名称</th>
              <th>模板类型</th>
              <th>纸张（宽 × 高 mm）</th>
              <th>启用字段</th>
              <th>状态</th>
              <th class="md-print-template-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td class="md-cell-mono">{{ row.templateCode }}</td>
              <td>
                <div class="md-cell-name">
                  <strong>{{ row.templateName }}</strong>
                  <small v-if="row.remark">{{ row.remark }}</small>
                </div>
              </td>
              <td><span class="md-chip">{{ templateTypeText(row) }}</span></td>
              <td class="md-cell-amount">{{ row.paperWidthMm }} × {{ row.paperHeightMm }}</td>
              <td>
                <span class="md-chip primary">
                  {{ parseTemplateFields(row.fieldsJson).filter(f => f.enabled).length }} 项
                </span>
              </td>
              <td>
                <MdStatus :label="statusText(row)" :variant="row.status === 1 ? 'success' : 'neutral'" />
              </td>
              <td class="md-print-template-action-cell">
                <div class="md-row-actions">
                  <button class="md-btn-text" type="button" @click="openEditor(row)">
                    调整模板
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td :colspan="7">
                <MdEmptyState text="暂无打印模板" hint="打印模板由定数包标签初始化生成" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </MdDataTableCard>

    <MdFormDialog
      :open="showEditor"
      size="lg"
      :title="editing?.templateName || '打印模板调整'"
      :subtitle="editing ? `${editing.templateCode} · ${templateTypeText(editing)}` : ''"
      :loading="saving"
      save-text="保存模板"
      @close="showEditor = false"
      @save="saveTemplate"
    >
      <div class="md-print-template-dialog-body">
        <p v-if="formError" class="md-error">{{ formError }}</p>

        <MdFormSection title="基本信息">
          <div class="md-form-grid">
            <label>
              <span>模板名称 <em class="md-required">*</em></span>
              <input v-model.trim="templateName" class="md-input" required />
            </label>
            <label>
              <span>纸张预设</span>
              <select class="md-select" @change="applyPaperPreset">
                <option value="">自定义纸张</option>
                <option v-for="preset in paperPresets" :key="preset.label" :value="preset.label">
                  {{ preset.label }}
                </option>
              </select>
            </label>
            <label>
              <span>纸张宽度（mm）</span>
              <input v-model.number="paperWidthMm" class="md-input" type="number" min="1" step="0.5" required />
            </label>
            <label>
              <span>纸张高度（mm）</span>
              <input v-model.number="paperHeightMm" class="md-input" type="number" min="1" step="0.5" required />
            </label>
            <label>
              <span>状态</span>
              <select v-model.number="status" class="md-select">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>
            <label class="wide">
              <span>备注</span>
              <textarea v-model.trim="remark" class="md-textarea" rows="2" />
            </label>
          </div>
        </MdFormSection>

        <MdFormSection title="标签字段">
          <p class="md-form-hint">
            勾选需要打印的字段，用上移 / 下移调整顺序（已启用 {{ enabledFieldCount }} 项 / 共 {{ fields.length }} 项）
          </p>
          <div class="md-print-template-field-list">
            <div
              v-for="(field, index) in fields"
              :key="field.code"
              class="md-print-template-field-row"
              :class="{ 'is-disabled': !field.enabled }"
            >
              <label class="md-print-template-field-check">
                <input
                  type="checkbox"
                  :checked="field.enabled"
                  :aria-label="`启用字段 ${field.code}`"
                  @change="toggleField(field)"
                />
              </label>
              <span class="md-cell-mono md-print-template-field-code">{{ field.code }}</span>
              <input v-model.trim="field.label" class="md-input md-print-template-field-label" aria-label="字段名称" />
              <span class="md-print-template-field-value">
                {{ field.value !== undefined ? `固定值：${field.value}` : '动态字段' }}
              </span>
              <div class="md-print-template-field-actions">
                <button
                  class="md-btn-icon md-btn-sm"
                  type="button"
                  aria-label="上移"
                  :disabled="index === 0"
                  @click="moveField(index, -1)"
                >
                  <ArrowUp :size="14" />
                </button>
                <button
                  class="md-btn-icon md-btn-sm"
                  type="button"
                  aria-label="下移"
                  :disabled="index === fields.length - 1"
                  @click="moveField(index, 1)"
                >
                  <ArrowDown :size="14" />
                </button>
                <button
                  class="md-btn-icon md-btn-sm"
                  type="button"
                  aria-label="删除字段"
                  @click="removeField(field)"
                >
                  <Trash2 :size="14" />
                </button>
              </div>
            </div>
            <p v-if="!fields.length" class="md-print-template-field-empty">该模板暂无可用字段</p>
          </div>
          <form class="md-print-template-add-field" @submit.prevent="addField">
            <input v-model.trim="newFieldCode" class="md-input" placeholder="新字段编码，如 operator" />
            <input v-model.trim="newFieldLabel" class="md-input" placeholder="新字段名称，如 操作员" />
            <button class="md-btn md-btn-primary" type="submit">
              <Plus :size="15" />
              <span>新增字段</span>
            </button>
            <button class="md-btn" type="button" :disabled="!newFieldCode" @click="newFieldCode = ''; newFieldLabel = ''">
              <RotateCcw :size="14" />
              <span>清空</span>
            </button>
          </form>
        </MdFormSection>
      </div>
    </MdFormDialog>
  </section>
</template>

<style scoped>
/* =====================================================================
   打印模板调整 — 复用主数据 md-* 主题，仅补充本页需要的少量样式
   ===================================================================== */
.md-print-template-page {
  padding: 16px 24px 32px;
  max-width: 100%;
}

/* 页面外壳移除默认 flex/gap，改用主数据页面的 grid 布局 */
.md-print-template-page.md-page {
  display: grid;
  gap: 16px;
}

/* ===== 表格 ===== */
.md-print-template-table-wrap {
  background: var(--md-bg-card);
  border-top: 1px solid var(--md-border-soft);
}

.md-print-template-table {
  width: 100%;
  min-width: 860px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  color: var(--md-text);
}

.md-print-template-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  color: var(--md-text-heading);
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.2px;
  text-align: left;
  white-space: nowrap;
  padding: 10px 12px;
  border-bottom: 2px solid var(--md-border-strong);
}

.md-print-template-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--md-border-soft);
  color: var(--md-text);
  white-space: nowrap;
  vertical-align: middle;
  background: var(--md-bg-card);
}

.md-print-template-table tbody tr {
  transition: background 0.12s ease;
}

.md-print-template-table tbody tr:hover td {
  background: var(--md-primary-soft);
}

.md-print-template-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted);
}

.md-print-template-table tbody tr:nth-child(even):hover td {
  background: var(--md-primary-soft);
}

.md-print-template-table tbody tr:last-child td {
  border-bottom: 0;
}

/* 纸张尺寸按数值展示，等宽右对齐便于纵向比对 */
.md-print-template-table .md-cell-amount {
  text-align: left;
  color: var(--md-text-secondary);
  font-weight: 500;
}

.md-print-template-action-cell {
  width: 110px;
}

/* ===== 弹窗 ===== */
.md-print-template-dialog-body {
  display: grid;
  gap: 16px;
}

.md-print-template-dialog-body .md-form-section {
  margin-bottom: 0;
}

.md-print-template-dialog-body .md-form-hint {
  margin: 0;
}

/* 纸张预设 select 需要撑满网格列 */
.md-print-template-dialog-body .md-select {
  width: 100%;
}

/* ===== 字段列表 ===== */
.md-print-template-field-list {
  display: grid;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
  padding-right: 2px;
}

.md-print-template-field-row {
  display: grid;
  grid-template-columns: 28px 150px minmax(0, 1fr) 130px auto;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  background: var(--md-bg-card);
  border: 1px solid var(--md-border-soft);
  border-radius: var(--md-radius);
  transition: border-color 0.15s ease, background 0.15s ease;
}

.md-print-template-field-row:hover {
  border-color: var(--md-primary-border);
}

/* 未启用字段整体降透明，但仍可编辑名称与顺序 */
.md-print-template-field-row.is-disabled {
  background: var(--md-bg-muted);
}

.md-print-template-field-row.is-disabled .md-print-template-field-code,
.md-print-template-field-row.is-disabled .md-print-template-field-value {
  color: var(--md-text-muted);
  opacity: 0.7;
}

.md-print-template-field-check {
  display: grid;
  place-items: center;
  cursor: pointer;
}

.md-print-template-field-check input {
  width: 15px;
  height: 15px;
  accent-color: var(--md-primary);
  cursor: pointer;
}

.md-print-template-field-code {
  font-weight: 600;
  color: var(--md-primary-strong);
}

.md-print-template-field-value {
  color: var(--md-text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.md-print-template-field-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* md-btn-icon 默认 32px，此处压缩到与行内其它操作一致 */
.md-print-template-field-actions .md-btn-icon {
  width: 26px;
  height: 26px;
  min-height: 26px;
}

.md-print-template-field-empty {
  margin: 0;
  padding: 24px 12px;
  color: var(--md-text-muted);
  font-size: 13px;
  text-align: center;
}

/* ===== 新增字段 ===== */
.md-print-template-add-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
}

.md-print-template-add-field .md-input {
  flex: 1 1 160px;
  min-width: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .md-print-template-page {
    padding: 12px 16px 24px;
  }

  .md-print-template-field-row {
    grid-template-columns: 28px minmax(0, 1fr) auto;
    row-gap: 8px;
  }

  .md-print-template-field-code,
  .md-print-template-field-value {
    grid-column: 2 / -1;
  }

  .md-print-template-field-actions {
    grid-column: 3;
    grid-row: 1;
  }
}
</style>