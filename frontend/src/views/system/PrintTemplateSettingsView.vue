<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowDown, ArrowUp, Pencil, Plus, Save, Trash2, X } from '@lucide/vue'
import {
  fetchPrintTemplates,
  parseTemplateFields,
  updatePrintTemplate,
  type PrintTemplateField,
  type PrintTemplateRow
} from '../../api/printTemplates'

const rows = ref<PrintTemplateRow[]>([])
const loading = ref(false)
const message = ref('')
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
const newFieldCode = ref('')
const newFieldLabel = ref('')

const paperPresets = [
  { label: '60 × 40 mm（常见标签纸）', width: 60, height: 40 },
  { label: '40 × 60 mm（默认）', width: 40, height: 60 },
  { label: '100 × 70 mm', width: 100, height: 70 },
  { label: '100 × 100 mm', width: 100, height: 100 },
  { label: 'A4 纵向（210 × 297 mm）', width: 210, height: 297 }
]

const enabledFieldCount = computed(() => fields.value.filter((field) => field.enabled).length)

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
  message.value = ''
  showEditor.value = true
}

function applyPaperPreset(event: Event) {
  const select = event.target as HTMLSelectElement
  const preset = paperPresets.find((item) => item.label === select.value)
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
    message.value = '请填写新增字段的编码'
    return
  }
  if (fields.value.some((field) => field.code === code)) {
    message.value = '该字段编码已存在'
    return
  }
  fields.value = [...fields.value, { code, label: label || code, enabled: true }]
  newFieldCode.value = ''
  newFieldLabel.value = ''
  message.value = ''
}

function removeField(field: PrintTemplateField) {
  fields.value = fields.value.filter((item) => item.code !== field.code)
}

async function saveTemplate() {
  if (!editing.value) return
  if (!templateName.value.trim()) {
    message.value = '模板名称为必填项'
    return
  }
  saving.value = true
  message.value = ''
  try {
    await updatePrintTemplate(editing.value.templateType, {
      templateName: templateName.value.trim(),
      fieldsJson: JSON.stringify(fields.value),
      paperWidthMm: paperWidthMm.value,
      paperHeightMm: paperHeightMm.value,
      status: status.value,
      remark: remark.value.trim()
    })
    message.value = '打印模板已保存'
    showEditor.value = false
    await loadList()
  } catch (err) {
    message.value = err instanceof Error ? err.message : '打印模板保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="print-template-page">
    <header class="license-header">
      <div>
        <p>定数包</p>
        <h2>打印模板调整</h2>
        <span>调整定数包标签打印模板的字段、顺序与纸张设置，保存后打印时生效。</span>
      </div>
    </header>

    <p v-if="message" class="license-message">{{ message }}</p>
    <p v-if="error" class="license-error">{{ error }}</p>

    <div class="license-table-wrap">
      <table class="license-table">
        <thead>
          <tr>
            <th>模板编码</th>
            <th>模板名称</th>
            <th>模板类型</th>
            <th>纸张（宽 × 高 mm）</th>
            <th>启用字段</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="approval-empty">正在加载打印模板...</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="7" class="approval-empty">暂无打印模板</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>{{ row.templateCode }}</td>
            <td>{{ row.templateName }}</td>
            <td>{{ row.templateType === 'quota_label' ? '定数包标签' : row.templateType }}</td>
            <td>{{ row.paperWidthMm }} × {{ row.paperHeightMm }}</td>
            <td>{{ parseTemplateFields(row.fieldsJson).filter((f) => f.enabled).length }} 项</td>
            <td>
              <span :class="['license-status', row.status === 1 ? 'ok' : 'warn']">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button class="btn-text" type="button" @click="openEditor(row)">
                <Pencil :size="14" /> 调整模板
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 模板编辑器 -->
    <div v-if="showEditor" class="attachment-preview-mask" @click.self="showEditor = false">
      <section class="supplier-dialog product-dialog print-template-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>打印模板调整</p>
            <h3>{{ editing?.templateName }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="showEditor = false"><X :size="18" /></button>
        </header>
        <div class="print-template-body">
          <form class="print-template-form" @submit.prevent="saveTemplate">
            <label><span>模板名称</span><input v-model.trim="templateName" required /></label>
            <label>
              <span>纸张预设</span>
              <select @change="applyPaperPreset">
                <option value="">自定义纸张</option>
                <option v-for="preset in paperPresets" :key="preset.label" :value="preset.label">{{ preset.label }}</option>
              </select>
            </label>
            <label><span>纸张宽度（mm）</span><input v-model.number="paperWidthMm" type="number" min="1" step="0.5" required /></label>
            <label><span>纸张高度（mm）</span><input v-model.number="paperHeightMm" type="number" min="1" step="0.5" required /></label>
            <label>
              <span>状态</span>
              <select v-model.number="status">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>
            <label class="wide"><span>备注</span><textarea v-model.trim="remark" rows="2" /></label>
          </form>

          <div class="print-template-fields">
            <div class="print-template-fields-head">
              <div>
                <strong>标签字段</strong>
                <span>勾选需要打印的字段，用上移 / 下移调整顺序（已启用 {{ enabledFieldCount }} 项）</span>
              </div>
            </div>
            <ul class="print-template-field-list">
              <li v-for="(field, index) in fields" :key="field.code">
                <label class="field-enabled">
                  <input type="checkbox" :checked="field.enabled" @change="toggleField(field)" />
                </label>
                <span class="field-code">{{ field.code }}</span>
                <input v-model.trim="field.label" class="field-label" aria-label="字段名称" />
                <span v-if="field.value !== undefined" class="field-value">固定值：{{ field.value }}</span>
                <span v-else class="field-value">动态字段</span>
                <div class="field-actions">
                  <button class="btn-icon" type="button" aria-label="上移" :disabled="index === 0" @click="moveField(index, -1)">
                    <ArrowUp :size="14" />
                  </button>
                  <button class="btn-icon" type="button" aria-label="下移" :disabled="index === fields.length - 1" @click="moveField(index, 1)">
                    <ArrowDown :size="14" />
                  </button>
                  <button class="btn-icon" type="button" aria-label="删除字段" @click="removeField(field)">
                    <Trash2 :size="14" />
                  </button>
                </div>
              </li>
            </ul>
            <form class="print-template-add-field" @submit.prevent="addField">
              <input v-model.trim="newFieldCode" placeholder="新字段编码，如 operator" />
              <input v-model.trim="newFieldLabel" placeholder="新字段名称，如 操作员" />
              <button class="btn" type="submit"><Plus :size="15" /> 新增字段</button>
            </form>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn" type="button" :disabled="saving" @click="showEditor = false">取消</button>
          <button class="btn btn-primary" type="button" :disabled="saving" @click="saveTemplate">
            <Save :size="15" /> {{ saving ? '保存中...' : '保存模板' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.print-template-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px;
}
.print-template-dialog {
  width: min(920px, 94vw);
}
.print-template-body {
  display: grid;
  gap: 16px;
  padding: 16px;
}
.print-template-form {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.print-template-form label {
  display: grid;
  gap: 4px;
  color: #33485c;
  font-size: 13px;
}
.print-template-form label.wide {
  grid-column: 1 / -1;
}
.print-template-form input,
.print-template-form select,
.print-template-form textarea,
.print-template-add-field input {
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font: inherit;
  font-size: 13px;
}
.print-template-fields {
  border: 1px solid #dbe8ee;
  border-radius: 8px;
  overflow: hidden;
}
.print-template-fields-head {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e5eef2;
  background: #f5f9fb;
}
.print-template-fields-head div {
  display: grid;
  gap: 3px;
}
.print-template-fields-head strong {
  color: #172b3a;
  font-size: 14px;
}
.print-template-fields-head span {
  color: #6b7c8f;
  font-size: 12px;
}
.print-template-field-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 300px;
  overflow: auto;
}
.print-template-field-list li {
  display: grid;
  grid-template-columns: 28px 150px 1fr 110px auto;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid #eef3f5;
}
.field-code {
  color: #0f6f78;
  font-weight: 700;
  font-size: 13px;
}
.field-label {
  padding: 6px 8px;
  border: 1px solid #cfdfe7;
  border-radius: 5px;
  font: inherit;
  font-size: 13px;
}
.field-value {
  color: #6b7c8f;
  font-size: 12px;
}
.field-actions {
  display: flex;
  gap: 4px;
}
.print-template-add-field {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  background: #fbfdfe;
}
.print-template-add-field input {
  flex: 1;
  min-width: 0;
}
</style>
