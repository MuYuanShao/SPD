<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Settings, X } from '@lucide/vue'
import {
  createFieldOption,
  fetchFieldOptionFields,
  fetchFieldOptions,
  removeFieldOption,
  updateFieldOption,
  type FieldOption,
  type FieldOptionField
} from '../../api/fieldOptions'
import PageHeader from '../../components/common/PageHeader.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'

const fields = ref<FieldOptionField[]>([])
const options = ref<FieldOption[]>([])
const activeFieldKey = ref('')
const loading = ref(false)
const error = ref('')
const message = ref('')

const dialogOpen = ref(false)
const dialogMode = ref<'new-field' | 'new-option' | 'edit-option'>('new-option')
const editingOptionId = ref<number | null>(null)

const dialogForm = reactive({
  fieldKey: '',
  fieldLabel: '',
  optionValue: '',
  optionLabel: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})

const activeField = computed(
  () => fields.value.find((item) => item.fieldKey === activeFieldKey.value) ?? null
)

async function loadFields(keepSelection = true) {
  loading.value = true
  error.value = ''
  try {
    fields.value = await fetchFieldOptionFields()
    if (!keepSelection || !fields.value.some((item) => item.fieldKey === activeFieldKey.value)) {
      activeFieldKey.value = fields.value[0]?.fieldKey ?? ''
    }
    await loadOptions()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '字段列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  if (!activeFieldKey.value) {
    options.value = []
    return
  }
  try {
    options.value = await fetchFieldOptions(activeFieldKey.value)
  } catch (err) {
    options.value = []
    error.value = err instanceof Error ? err.message : '选项加载失败'
  }
}

function openNewFieldDialog() {
  dialogMode.value = 'new-field'
  editingOptionId.value = null
  Object.assign(dialogForm, {
    fieldKey: '',
    fieldLabel: '',
    optionValue: '',
    optionLabel: '',
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  dialogOpen.value = true
}

function openNewOptionDialog() {
  if (!activeField.value) {
    error.value = '请先选择左侧字段'
    return
  }
  dialogMode.value = 'new-option'
  editingOptionId.value = null
  Object.assign(dialogForm, {
    fieldKey: activeField.value.fieldKey,
    fieldLabel: activeField.value.fieldLabel,
    optionValue: '',
    optionLabel: '',
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  dialogOpen.value = true
}

function openEditOptionDialog(option: FieldOption) {
  dialogMode.value = 'edit-option'
  editingOptionId.value = option.optionId
  Object.assign(dialogForm, {
    fieldKey: option.fieldKey,
    fieldLabel: option.fieldLabel,
    optionValue: option.optionValue,
    optionLabel: option.optionLabel,
    sortOrder: option.sortOrder,
    status: option.status,
    remark: option.remark ?? ''
  })
  dialogOpen.value = true
}

async function submitDialog() {
  error.value = ''
  if (!dialogForm.fieldKey.trim() || !dialogForm.fieldLabel.trim()) {
    error.value = '字段键与字段名称为必填项'
    return
  }
  if (!dialogForm.optionValue.trim() || !dialogForm.optionLabel.trim()) {
    error.value = '选项值与显示名为必填项'
    return
  }
  try {
    if (dialogMode.value === 'edit-option' && editingOptionId.value !== null) {
      await updateFieldOption(editingOptionId.value, {
        fieldLabel: dialogForm.fieldLabel,
        optionLabel: dialogForm.optionLabel,
        sortOrder: dialogForm.sortOrder,
        status: dialogForm.status,
        remark: dialogForm.remark
      })
      message.value = '选项已更新'
    } else {
      await createFieldOption({
        fieldKey: dialogForm.fieldKey,
        fieldLabel: dialogForm.fieldLabel,
        optionValue: dialogForm.optionValue,
        optionLabel: dialogForm.optionLabel,
        sortOrder: dialogForm.sortOrder,
        status: dialogForm.status,
        remark: dialogForm.remark
      })
      message.value = dialogMode.value === 'new-field' ? '字段已新增' : '选项已新增'
    }
    dialogOpen.value = false
    await loadFields(false)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  }
}

async function handleRemoveOption(option: FieldOption) {
  if (!window.confirm(`确认删除选项“${option.optionLabel}”吗？`)) return
  error.value = ''
  try {
    await removeFieldOption(option.optionId)
    message.value = '选项已删除'
    await loadFields(false)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
  }
}

onMounted(() => loadFields(true))
</script>

<template>
  <div class="md-page sys-page">
    <MdPageHeader
      crumb="系统管理"
      title="字段管理"
      description="维护所有表格中下拉选字段的选项，支持新增、修改与停用。"
      :loading="loading"
      @refresh="loadFields(false)"
    >
      <template #actions>
        <button class="md-btn md-btn-primary" type="button" @click="openNewFieldDialog"><Plus :size="15" /> 新增字段</button>
      </template>
    </MdPageHeader>

    <div class="md-content sys-content">
      <div v-if="error" class="md-error">{{ error }}</div>
      <div v-if="message" class="md-message">{{ message }}</div>

      <div class="field-option-layout">
        <aside class="field-option-fields">
          <div class="md-panel-header"><h4 class="md-panel-title">下拉字段</h4></div>
          <div class="field-option-fields-list">
            <button
              v-for="field in fields"
              :key="field.fieldKey"
              type="button"
              :class="['field-option-field-item', { active: field.fieldKey === activeFieldKey }]"
              @click="activeFieldKey = field.fieldKey; loadOptions()"
            >
              <span class="field-option-field-name">{{ field.fieldLabel }}</span>
              <span class="field-option-field-meta">{{ field.fieldKey }} · {{ field.optionCount }} 项</span>
            </button>
            <p v-if="!fields.length" class="text-center muted">暂无下拉字段</p>
          </div>
        </aside>

        <section class="md-table-card">
          <div class="md-panel-header">
            <h4 class="md-panel-title">{{ activeField?.fieldLabel ?? '未选择字段' }}</h4>
            <button v-if="activeField" class="md-btn md-btn-primary" type="button" @click="openNewOptionDialog"><Plus :size="14" /> 新增选项</button>
          </div>
          <div v-if="activeField" class="md-panel-body"><small class="field-option-sub">字段键 {{ activeField.fieldKey }}，表格中该下拉字段的可用选项如下。</small></div>
          <div v-if="options.length" class="table-scroll">
            <table class="md-table">
              <thead>
                <tr>
                  <th>选项值</th>
                  <th>显示名</th>
                  <th>排序</th>
                  <th>状态</th>
                  <th>备注</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading"><td colspan="6" class="text-center muted">正在加载字段...</td></tr>
                <tr v-for="option in options" v-else :key="option.optionId">
                  <td>{{ option.optionValue === '' ? '（空值）' : option.optionValue }}</td>
                  <td>{{ option.optionLabel }}</td>
                  <td>{{ option.sortOrder }}</td>
                  <td><span :class="['md-status', option.status === 1 ? 'success' : 'neutral']">{{ option.status === 1 ? '启用' : '停用' }}</span></td>
                  <td>{{ option.remark || '-' }}</td>
                  <td>
                    <button class="md-btn-text" type="button" @click="openEditOptionDialog(option)">编辑</button>
                    <button class="md-btn-text md-btn-text-danger" type="button" @click="handleRemoveOption(option)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="text-center muted" style="padding:48px 16px">{{ activeField ? '该字段暂无选项，点击"新增选项"添加。' : '请选择左侧字段查看选项' }}</p>
        </section>
      </div>
    </div>

    <div v-if="dialogOpen" class="md-dialog-mask" @click.self="dialogOpen = false">
      <div class="md-dialog">
        <div class="md-dialog-header">
          <h3>{{ dialogMode === 'edit-option' ? '编辑选项' : dialogMode === 'new-field' ? '新增字段' : '新增选项' }}</h3>
          <button class="md-btn-icon" type="button" @click="dialogOpen = false"><X :size="16" /></button>
        </div>
        <div class="md-dialog-body">
          <div class="md-form-grid">
            <label v-if="dialogMode === 'new-field'"><span>字段键</span><input v-model.trim="dialogForm.fieldKey" class="md-input" placeholder="如 supplier_type" /></label>
            <label><span>字段名称</span><input v-model.trim="dialogForm.fieldLabel" class="md-input" placeholder="如 供应商类型" /></label>
            <label><span>选项值</span><input v-model="dialogForm.optionValue" :readonly="dialogMode === 'edit-option'" class="md-input" placeholder="保存到数据库的值" /></label>
            <label><span>显示名</span><input v-model.trim="dialogForm.optionLabel" class="md-input" placeholder="界面展示的文字" /></label>
            <label><span>排序</span><input v-model.number="dialogForm.sortOrder" type="number" min="0" step="1" class="md-input" /></label>
            <label><span>状态</span><select v-model.number="dialogForm.status" class="md-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label class="wide"><span>备注</span><input v-model.trim="dialogForm.remark" class="md-input" placeholder="选项说明（可选）" /></label>
          </div>
        </div>
        <div class="md-dialog-footer">
          <button class="md-btn" type="button" @click="dialogOpen = false">取消</button>
          <button class="md-btn md-btn-primary" type="button" @click="submitDialog"><Settings :size="15" /> 保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sys-page { display: flex; flex-direction: column; }
.sys-content { padding: 12px 0 24px; display: flex; flex-direction: column; gap: 12px; }
.field-option-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
.field-option-fields {
  background: var(--md-bg-card);
  border: 1px solid var(--md-border);
  border-radius: var(--md-radius-md);
  display: flex;
  flex-direction: column;
}
.field-option-fields-list {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 600px;
  overflow-y: auto;
}
.field-option-field-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  border: 1px solid transparent;
  border-radius: var(--md-radius);
  background: transparent;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  color: var(--md-text);
  transition: all .15s;
}
.field-option-field-item:hover { background: var(--md-primary-soft); color: var(--md-primary-strong); }
.field-option-field-item.active { background: var(--md-primary-soft); color: var(--md-primary-strong); border-color: rgba(13, 107, 99, .24); font-weight: 600; }
.field-option-field-name { font-size: 14px; }
.field-option-field-meta { color: var(--md-text-muted); font-size: 12px; }
.field-option-sub { color: var(--md-text-muted); font-size: 12px; }
.table-scroll { overflow-x: auto; }
.text-center { text-align: center; }
.muted { color: var(--md-text-muted); padding: 24px 12px; }
</style>

<style scoped>
.field-option-page {
  max-width: 1280px;
}

.field-option-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.field-option-fields {
  display: grid;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  padding: 12px;
}

.field-option-fields h3 {
  margin: 0 0 4px;
  padding: 0 6px;
  color: #102033;
  font-size: 15px;
}

.field-option-field-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
}

.field-option-field-item:hover {
  background: #f3f7f8;
}

.field-option-field-item.active {
  border-color: #b7ded7;
  background: #eaf7f3;
}

.field-option-field-name {
  color: #102033;
  font-size: 14px;
  font-weight: 700;
}

.field-option-field-meta {
  color: #607486;
  font-size: 12px;
}

.field-option-panel {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: 0 2px 8px rgba(27, 39, 51, 0.06);
}

.field-option-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--line);
  background: #f7fafb;
  padding: 12px 16px;
}

.field-option-panel-heading h3 {
  margin: 0;
  color: #102033;
  font-size: 16px;
}

.field-option-panel-heading p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.field-option-table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.field-option-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.field-option-table th,
.field-option-table td {
  border-bottom: 1px solid #e8eef1;
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.field-option-table th {
  background: #fbfcfd;
  color: #607486;
  font-size: 12px;
  font-weight: 800;
}

.field-option-table td {
  color: #25384a;
}

.field-option-status {
  display: inline-flex;
  align-items: center;
  border: 1px solid #b7ded7;
  border-radius: 999px;
  background: #eaf7f3;
  color: #087f71;
  padding: 1px 9px;
  font-size: 12px;
  font-weight: 800;
}

.field-option-status.inactive {
  border-color: #e2e8ed;
  background: #f1f4f6;
  color: #7b8a99;
}

.field-option-actions {
  display: flex;
  gap: 6px;
}

@media (max-width: 760px) {
  .field-option-layout {
    grid-template-columns: 1fr;
  }
}
</style>
