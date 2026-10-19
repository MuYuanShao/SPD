<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Teleport } from 'vue'
import {
  AlertOctagon,
  Check,
  FileCog,
  ListTree,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings,
  Tags,
  X
} from '@lucide/vue'
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
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const fields = ref<FieldOptionField[]>([])
const options = ref<FieldOption[]>([])
const activeFieldKey = ref('')
const filterText = ref('')
const loading = ref(false)
const error = ref('')
const message = ref('')

const dialogOpen = ref(false)
const dialogMode = ref<'new-field' | 'new-option' | 'edit-option'>('new-option')
const editingOptionId = ref<number | null>(null)

const kpiStats = computed(() => [
  { icon: 'list',  theme: 'blue',   label: '字段数量',   value: fields.value.length, hint: '下拉字段总数' },
  { icon: 'check', theme: 'green',  label: '启用选项',   value: options.value.filter(o => o.status === 1).length, hint: '当前字段可用项' },
  { icon: 'stop',  theme: 'red',    label: '停用选项',   value: options.value.filter(o => o.status === 0).length, hint: '已禁用项' },
  { icon: 'item',  theme: 'orange', label: '当前字段选项',value: options.value.length, hint: activeField.value?.fieldLabel ?? '未选择' }
])

const filteredFields = computed(() => {
  const q = filterText.value.trim().toLowerCase()
  if (!q) return fields.value
  return fields.value.filter(f =>
    f.fieldLabel.toLowerCase().includes(q) || f.fieldKey.toLowerCase().includes(q)
  )
})

function filterFields() { /* computed is reactive; this is just for keyup.enter */ }
function resetFilter() {
  filterText.value = ''
}

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
      notify.success('选项已更新')
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
    notify.success('选项已删除')
    await loadFields(false)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
  }
}

onMounted(() => loadFields(true))
</script>

<template>
  <div class="sys-page">

    <MdPageHeader
      title="字段管理"
      description="维护所有表格中下拉选字段的选项，支持新增、修改与停用。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadFields(false)"
    />

    <!-- 顶部 4 张主题色 KPI 卡 -->
    <section class="sys-kpi-card" aria-label="字段统计">
      <div v-for="item in kpiStats" :key="item.label" class="sys-kpi-item" :class="`is-${item.theme}`">
        <i class="sys-kpi-icon" :class="`is-${item.icon}`">
          <ListTree v-if="item.icon === 'list'" :size="18" />
          <Check v-else-if="item.icon === 'check'" :size="18" />
          <X v-else-if="item.icon === 'stop'" :size="18" />
          <Tags v-else-if="item.icon === 'item'" :size="18" />
          <FileCog v-else :size="18" />
        </i>
        <div>
          <span class="sys-kpi-label">{{ item.label }}</span>
          <strong class="sys-kpi-value">{{ item.value }}</strong>
          <span class="sys-kpi-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

    <!-- 工具栏（顶部条不放按钮，全部动作放这里） -->
    <div class="sys-toolbar-panel">
      <div class="sys-toolbar-search">
        <i class="sys-toolbar-search-icon"><Search :size="15" /></i>
        <input
          v-model="filterText"
          placeholder="搜索字段名称 / 字段键"
          @keyup.enter="filterFields"
        />
      </div>
      <div class="sys-toolbar-actions">
        <button class="btn" type="button" @click="resetFilter"><RefreshCw :size="15" /> 重置</button>
        <i class="sys-toolbar-sep"></i>
        <button class="btn" type="button" @click="openNewFieldDialog"><Plus :size="15" /> 新增字段</button>
        <button
          v-if="activeField"
          class="btn btn-primary"
          type="button"
          @click="openNewOptionDialog"
        >
          <Plus :size="15" /> 新增选项
        </button>
      </div>
    </div>

    <!-- 消息 -->
    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->
    <div v-if="error" class="sys-error" role="alert">{{ error }}</div>

    <!-- 左：字段列表；右：选项表 -->
    <div class="sys-field-layout">

      <aside class="sys-field-side">
        <div class="sys-field-side-header">
          <i class="sys-field-side-icon"><ListTree :size="14" /></i>
          <h4>下拉字段</h4>
          <span class="sys-field-side-count">{{ fields.length }}</span>
        </div>
        <div class="sys-field-side-list">
          <button
            v-for="field in filteredFields"
            :key="field.fieldKey"
            type="button"
            :class="['sys-field-side-item', { active: field.fieldKey === activeFieldKey }]"
            @click="activeFieldKey = field.fieldKey; loadOptions()"
          >
            <span class="sys-field-side-name">{{ field.fieldLabel }}</span>
            <span class="sys-field-side-meta">{{ field.fieldKey }} · {{ field.optionCount }} 项</span>
          </button>
          <p v-if="!filteredFields.length" class="text-center muted">暂无匹配的字段</p>
        </div>
      </aside>

      <section class="sys-table-card">
        <div class="sys-table-card-header">
          <div>
            <h4>{{ activeField?.fieldLabel ?? '未选择字段' }}</h4>
            <p v-if="activeField">字段键 <code>{{ activeField.fieldKey }}</code>，该下拉字段的可用选项如下。</p>
          </div>
        </div>
        <div v-if="options.length" class="sys-table-scroll">
          <table class="sys-table">
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
                <td><span class="sys-tag is-code">{{ option.optionValue === '' ? '（空值）' : option.optionValue }}</span></td>
                <td>{{ option.optionLabel }}</td>
                <td>{{ option.sortOrder }}</td>
                <td>
                  <span :class="['sys-badge', option.status === 1 ? 'is-active' : 'is-stopped']">
                    {{ option.status === 1 ? '启用' : '停用' }}
                  </span>
                </td>
                <td>{{ option.remark || '-' }}</td>
                <td>
                  <button class="btn-text" type="button" @click="openEditOptionDialog(option)">编辑</button>
                  <button class="btn-text btn-text-danger" type="button" @click="handleRemoveOption(option)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="text-center muted" style="padding:48px 16px">
          {{ activeField ? '该字段暂无选项，点击"新增选项"添加。' : '请选择左侧字段查看选项' }}
        </p>
      </section>

    </div>

    <!-- 弹窗 -->
    <Teleport to="body">
    <div v-if="dialogOpen" class="sys-dialog-mask" @click.self="dialogOpen = false">
      <div class="sys-dialog">
        <div class="sys-dialog-header">
          <h3>{{ dialogMode === 'edit-option' ? '编辑选项' : dialogMode === 'new-field' ? '新增字段' : '新增选项' }}</h3>
          <button class="btn-icon" type="button" @click="dialogOpen = false"><X :size="16" /></button>
        </div>
        <div class="sys-dialog-body">
          <div class="sys-form-grid">
            <label v-if="dialogMode === 'new-field'">
              <span>字段键</span>
              <input v-model.trim="dialogForm.fieldKey" class="sys-input" placeholder="如 supplier_type" />
            </label>
            <label>
              <span>字段名称</span>
              <input v-model.trim="dialogForm.fieldLabel" class="sys-input" placeholder="如 供应商类型" />
            </label>
            <label>
              <span>选项值</span>
              <input v-model="dialogForm.optionValue" :readonly="dialogMode === 'edit-option'" class="sys-input" placeholder="保存到数据库的值" />
            </label>
            <label>
              <span>显示名</span>
              <input v-model.trim="dialogForm.optionLabel" class="sys-input" placeholder="界面展示的文字" />
            </label>
            <label>
              <span>排序</span>
              <input v-model.number="dialogForm.sortOrder" type="number" min="0" step="1" class="sys-input" />
            </label>
            <label>
              <span>状态</span>
              <select v-model.number="dialogForm.status" class="sys-select">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>
            <label class="wide">
              <span>备注</span>
              <input v-model.trim="dialogForm.remark" class="sys-input" placeholder="选项说明（可选）" />
            </label>
          </div>
        </div>
        <div class="sys-dialog-footer">
          <button class="btn" type="button" @click="dialogOpen = false">取消</button>
          <button class="btn btn-primary" type="button" @click="submitDialog"><Save :size="15" /> 保存</button>
        </div>
      </div>
    </div>
    </Teleport>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 字段管理
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡。
   2. 顶部条不放按钮：所有动作（重置 / 新增字段 / 新增选项）全部进工具栏。
   3. 工具栏：左侧搜索框 + 右侧动作按钮组。
   4. 左：字段侧栏（绿点激活态）；右：选项表（绿渐变表头）。
   5. 弹窗：统一表单控件。
   ===================================================================== */
.sys-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

/* ---------- 顶部 4 项 KPI ---------- */
.sys-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sys-kpi-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

.sys-kpi-item.is-blue {
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}
.sys-kpi-item.is-green {
  background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}
.sys-kpi-item.is-red {
  background: linear-gradient(180deg, #fee2e2 0%, #fecaca 100%);
  border-color: #fca5a5;
}
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}

.sys-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: #1d4ed8; }
.sys-kpi-item.is-green  .sys-kpi-icon { color: #047857; }
.sys-kpi-item.is-red    .sys-kpi-icon { color: #b91c1c; }
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label {
  font-size: 12px;
  font-weight: 500;
  opacity: 0.85;
}

.sys-kpi-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.sys-kpi-hint {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.3;
  opacity: 0.75;
}

.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-red    .sys-kpi-label,
.sys-kpi-item.is-red    .sys-kpi-value,
.sys-kpi-item.is-red    .sys-kpi-hint { color: #7f1d1d; }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }

/* ---------- 工具栏 ---------- */
.sys-toolbar-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  flex-wrap: wrap;
}

.sys-toolbar-search {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  overflow: hidden;
  flex: 0 0 280px;
  min-width: 0;
}

.sys-toolbar-search-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #64748b;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.sys-toolbar-search input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  background: transparent;
  color: #102033;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
}

.sys-toolbar-search input::placeholder { color: #94a3b8; }
.sys-toolbar-search input:focus { outline: none; }

.sys-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sys-toolbar-sep {
  width: 1px;
  height: 22px;
  background: #e2e8f0;
  margin: 0 4px;
}

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #1e293b;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, #059669 0%, #047857 100%);
  border-color: #065f46;
  color: #fff;
}

.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-icon:hover {
  background: #f1f5f9;
  color: #334155;
}

.btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #10b981;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.btn-text:hover { background: #d1fae5; }

.btn-text-danger {
  color: #b91c1c;
}

.btn-text-danger:hover {
  background: #fee2e2;
  color: #991b1b;
}

/* ---------- 消息条 ---------- */
.sys-msg {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

.sys-error {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fee2e2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  color: #991b1b;
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- 左右双栏 ---------- */
.sys-field-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.sys-field-side {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sys-field-side-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
}

.sys-field-side-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #d1fae5;
  color: #047857;
}

.sys-field-side-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
  flex: 1;
}

.sys-field-side-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--spd-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 0 6px;
}

.sys-field-side-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  max-height: 600px;
  overflow-y: auto;
}

.sys-field-side-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  color: #334155;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.sys-field-side-item:hover {
  background: #f0fdf9;
  color: #047857;
}

.sys-field-side-item.active {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-color: #6ee7b7;
  color: #065f46;
  font-weight: 600;
}

.sys-field-side-name {
  font-size: 13px;
  font-weight: 600;
}

.sys-field-side-meta {
  font-size: 11.5px;
  color: #94a3b8;
  font-family: var(--spd-font-mono), monospace;
}

/* ---------- 表格 ---------- */
.sys-table-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
}

.sys-table-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
  gap: 12px;
}

.sys-table-card-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-table-card-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--spd-text-tertiary);
}

.sys-table-card-header code {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 1px 6px;
  font-family: var(--spd-font-mono), monospace;
  font-size: 11.5px;
  color: #475569;
}

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sys-table thead tr {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-bottom: 1px solid #6ee7b7;
}

.sys-table th {
  color: #065f46;
  font-weight: 700;
  font-size: 12px;
  text-align: left;
  padding: 10px 12px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.sys-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.sys-table tbody tr:nth-child(even) td { background: #fafdfb; }
.sys-table tbody tr:hover td { background: #ecfdf5 !important; }
.sys-table tbody tr:last-child td { border-bottom: none; }

.text-center { text-align: center; }
.muted { color: #94a3b8; padding: 32px 16px; font-size: 13px; }

/* 选项值 monospace tag */
.sys-tag.is-code {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  color: #334155;
  background: #f1f5f9;
  font-family: var(--spd-font-mono), monospace;
  font-size: 11.5px;
}

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
}

.sys-badge.is-active {
  color: #065f46;
  background: #d1fae5;
  border: 1px solid #6ee7b7;
}

.sys-badge.is-stopped {
  color: #991b1b;
  background: #fee2e2;
  border: 1px solid #fca5a5;
}

/* ---------- 弹窗 ---------- */
.sys-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(2px);
}

.sys-dialog {
  background: #fff;
  border-radius: var(--spd-radius-lg, 14px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  width: 100%;
  max-width: 520px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.2s ease;
}

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(-8px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.sys-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.sys-dialog-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-dialog-body {
  padding: 18px 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.sys-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
}

/* ---------- 表单 ---------- */
.sys-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
  color: #4b5b66;
}

.sys-form-grid label.wide { grid-column: 1 / -1; }

.sys-input,
.sys-select {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus {
  outline: none;
  border-color: #cbd5e1;
}

.sys-input:read-only {
  background: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .sys-field-layout { grid-template-columns: 1fr; }
  .sys-form-grid { grid-template-columns: 1fr; }
  .sys-toolbar-panel { gap: 10px; }
  .sys-toolbar-search { flex: 1; min-width: 0; }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
