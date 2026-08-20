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
  <main class="approval-detail-page field-option-page">
    <PageHeader eyebrow="系统管理" title="字段管理" description="维护所有表格中下拉选字段的选项，支持新增、修改与停用。">
      <template #actions>
        <button class="btn btn-primary" type="button" @click="openNewFieldDialog">
          <Plus :size="17" aria-hidden="true" />
          新增字段
        </button>
      </template>
    </PageHeader>

    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="message" class="success-text">{{ message }}</p>
    <p v-if="loading" class="approval-empty">正在加载字段...</p>

    <div v-else class="field-option-layout">
      <aside class="field-option-fields" aria-label="下拉字段列表">
        <h3>下拉字段</h3>
        <button
          v-for="field in fields"
          :key="field.fieldKey"
          type="button"
          class="field-option-field-item"
          :class="{ active: field.fieldKey === activeFieldKey }"
          @click="activeFieldKey = field.fieldKey; loadOptions()"
        >
          <span class="field-option-field-name">{{ field.fieldLabel }}</span>
          <span class="field-option-field-meta">{{ field.fieldKey }} · {{ field.optionCount }} 项</span>
        </button>
        <p v-if="!fields.length" class="approval-empty">暂无下拉字段</p>
      </aside>

      <section class="field-option-panel" aria-labelledby="field-option-panel-title">
        <header class="field-option-panel-heading">
          <div>
            <h3 id="field-option-panel-title">{{ activeField?.fieldLabel ?? '未选择字段' }}</h3>
            <p v-if="activeField">字段键 {{ activeField.fieldKey }}，表格中该下拉字段的可用选项如下。</p>
          </div>
          <button v-if="activeField" class="btn btn-primary" type="button" @click="openNewOptionDialog">
            <Plus :size="16" aria-hidden="true" />
            新增选项
          </button>
        </header>

        <div v-if="options.length" class="field-option-table-wrap">
          <table class="field-option-table">
            <thead>
              <tr>
                <th scope="col">选项值</th>
                <th scope="col">显示名</th>
                <th scope="col">排序</th>
                <th scope="col">状态</th>
                <th scope="col">备注</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="option in options" :key="option.optionId">
                <td>{{ option.optionValue === '' ? '（空值）' : option.optionValue }}</td>
                <td>{{ option.optionLabel }}</td>
                <td>{{ option.sortOrder }}</td>
                <td>
                  <span class="field-option-status" :class="{ inactive: option.status !== 1 }">
                    {{ option.status === 1 ? '启用' : '停用' }}
                  </span>
                </td>
                <td>{{ option.remark || '-' }}</td>
                <td>
                  <div class="field-option-actions">
                    <button class="btn-text" type="button" @click="openEditOptionDialog(option)">编辑</button>
                    <button class="btn-text btn-text-danger" type="button" @click="handleRemoveOption(option)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="product-detail-empty-state">该字段暂无选项，点击“新增选项”添加。</p>
      </section>
    </div>

    <div v-if="dialogOpen" class="attachment-preview-mask" @click.self="dialogOpen = false">
      <section class="supplier-dialog product-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>字段管理</p>
            <h3>{{ dialogMode === 'edit-option' ? '编辑选项' : dialogMode === 'new-field' ? '新增字段' : '新增选项' }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="dialogOpen = false">
            <X :size="18" />
          </button>
        </header>
        <form class="supplier-form-grid compact" @submit.prevent="submitDialog">
          <label v-if="dialogMode === 'new-field'">
            <span>字段键 <b class="required-mark" aria-hidden="true">*</b></span>
            <input v-model.trim="dialogForm.fieldKey" placeholder="如 supplier_type" required />
          </label>
          <label>
            <span>字段名称 <b class="required-mark" aria-hidden="true">*</b></span>
            <input v-model.trim="dialogForm.fieldLabel" placeholder="如 供应商类型" required />
          </label>
          <label>
            <span>选项值 <b class="required-mark" aria-hidden="true">*</b></span>
            <input v-model="dialogForm.optionValue" :readonly="dialogMode === 'edit-option'" placeholder="保存到数据库的值" required />
          </label>
          <label>
            <span>显示名 <b class="required-mark" aria-hidden="true">*</b></span>
            <input v-model.trim="dialogForm.optionLabel" placeholder="界面展示的文字" required />
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="dialogForm.sortOrder" type="number" min="0" step="1" />
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="dialogForm.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <label class="wide">
            <span>备注</span>
            <input v-model.trim="dialogForm.remark" placeholder="选项说明（可选）" />
          </label>
          <div class="dialog-actions wide">
            <button class="btn" type="button" @click="dialogOpen = false">取消</button>
            <button class="btn btn-primary" type="submit">
              <Settings :size="16" aria-hidden="true" />
              保存
            </button>
          </div>
        </form>
      </section>
    </div>
  </main>
</template>

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
