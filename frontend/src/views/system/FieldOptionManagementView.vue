<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
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
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
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

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const FIELDOPTION_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'optionValue', label: '选项值', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'optionLabel', label: '显示名', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'sortOrder', label: '排序', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'remark', label: '备注', width: 220, visible: true, flexible: true, idealWidth: 280, weight: 6, minWidth: 130, maxWidth: 380 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  // 「操作」列必须出现在 defaults 里：isColumnVisible() 只查 defaults，
  // 缺少该键时 v-if="isFieldOptionColVisible('actions')" 恒为 false，
  // 整列不会渲染，「编辑」按钮将完全无法触达。
  { key: 'actions', label: '操作', width: 140, visible: true, weight: 3, minWidth: 120, maxWidth: 200, fill: true }
]

const fieldOptionCols = useListColumns({
  storageKey: 'spd-field-option-cols-v1',
  defaults: FIELDOPTION_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「备注」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const fieldOptionTableRef = fieldOptionCols.tableRef
const isFieldOptionColVisible = (key: string) => fieldOptionCols.isColumnVisible(key)
const fieldOptionColBinding = (key: string) => fieldOptionCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const fieldOptionActionFix = useActionColumnFix()
const fieldOptionActionFixed = computed(() => fieldOptionActionFix.actionFixedBinding.value)

watch(
  [fieldOptionTableRef],
  () => {
    fieldOptionActionFix.observe(fieldOptionTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const fieldOptionColSettingsAnchorRef = ref<HTMLElement | null>(null)
const fieldOptionColSettingsPopRef = ref<HTMLElement | null>(null)
const fieldOptionColSettingsPop = useColSettingsPopover({
  open: fieldOptionCols.settingsOpen,
  anchorRef: fieldOptionColSettingsAnchorRef,
  popRef: fieldOptionColSettingsPopRef
})

</script>

<template>
  <div class="sys-page spd-unified-list">

    <MdPageHeader
      title="字段管理"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadFields(false)"
    />

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
        <div v-if="options.length">
                  <MdDataTableCard class="master-table-card">
          <template #toolbar>
            <div class="ulc-col-settings">
              <button
                ref="fieldOptionColSettingsAnchorRef"
                type="button"
                class="md-btn md-btn-primary"
                @click="fieldOptionCols.settingsOpen.value = !fieldOptionCols.settingsOpen.value"
              >
                <Columns3 :size="16" />
                <span>列设置</span>
                <ChevronDown :size="14" :class="{ rotated: fieldOptionCols.settingsOpen.value }" />
              </button>
              <div
                v-if="fieldOptionCols.settingsOpen.value"
                ref="fieldOptionColSettingsPopRef"
                class="ulc-col-settings-pop"
                :class="{ 'is-align-right': fieldOptionColSettingsPop.align.value === 'right', 'is-align-up': fieldOptionColSettingsPop.vertical.value === 'up' }"
                :style="{ '--ulc-pop-offset-y': fieldOptionColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': fieldOptionColSettingsPop.maxHeight.value + 'px' }"
              >
                <header>
                  <strong>列展示设置</strong>
                  <button type="button" class="ulc-link" @click="fieldOptionCols.resetColumns()">恢复默认</button>
                </header>
                <div class="ulc-col-settings-list">
                  <label v-for="col in fieldOptionCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                    <input
                      type="checkbox"
                      :checked="col.visible"
                      :disabled="col.visible && fieldOptionCols.visibleColumnCount.value <= 1"
                      @change="fieldOptionCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                    />
                    <span>{{ col.label }}</span>
                    <small>{{ col.width }}px</small>
                  </label>
                </div>
                <footer>
                  <button type="button" class="ulc-action" @click="fieldOptionCols.settingsOpen.value = false">确定</button>
                </footer>
              </div>
            </div>
          </template>
          <template #meta-extra>
            <span class="md-chip primary">共 {{ options.length }} 条</span>
          </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="fieldOptionTableRef"
            v-loading="loading"
            :data="options"
            row-key="optionId"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'该字段暂无选项'"
            @header-dragend="(width: number, oldWidth: number, column: any) => fieldOptionCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="optionValue"
              v-if="isFieldOptionColVisible('optionValue')"
              label="选项值"
              v-bind="fieldOptionColBinding('optionValue')"
            >
              <template #header>
                <span class="ulc-th-text">选项值</span>
              </template>
              <template #default="{ row }">
                <span class="sys-tag is-code ulc-wrap-text">{{ row.optionValue === '' ? '（空值）' : row.optionValue }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="optionLabel"
              v-if="isFieldOptionColVisible('optionLabel')"
              label="显示名"
              v-bind="fieldOptionColBinding('optionLabel')"
            >
              <template #header>
                <span class="ulc-th-text">显示名</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.optionLabel }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sortOrder"
              v-if="isFieldOptionColVisible('sortOrder')"
              label="排序"
              v-bind="fieldOptionColBinding('sortOrder')"
            >
              <template #header>
                <span class="ulc-th-text">排序</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.sortOrder }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isFieldOptionColVisible('status')"
              label="状态"
              v-bind="fieldOptionColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span :class="['sys-badge', row.status === 1 ? 'is-active' : 'is-stopped']">
                {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="remark"
              v-if="isFieldOptionColVisible('remark')"
              label="备注"
              v-bind="fieldOptionColBinding('remark')"
            >
              <template #header>
                <span class="ulc-th-text">备注</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isFieldOptionColVisible('createTime')"
              label="创建时间"
              v-bind="fieldOptionColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="md-audit-time">
                  {{ row.createTime || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isFieldOptionColVisible('updateTime')"
              label="修改时间"
              v-bind="fieldOptionColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="md-audit-time">
                  {{ row.updateTime || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isFieldOptionColVisible('actions')"

              label="操作"

              v-bind="fieldOptionColBinding('actions')"
              
              :fixed="fieldOptionActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-actions">
                <button class="ulc-action" type="button" @click="openEditOptionDialog(row)">编辑</button>
                <button class="ulc-action is-danger" type="button" @click="handleRemoveOption(row)">删除</button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        </MdDataTableCard>
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
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  opacity: 0.85;
}

.sys-kpi-value {
  font-size: var(--spd-font-size-2xl);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}

.sys-kpi-hint {
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  line-height: var(--spd-lh-tight);
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
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-xs);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.sys-field-side-meta {
  font-size: var(--spd-font-size-xs);
  color: #94a3b8;
  font-family: var(--spd-font-family-mono);
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
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-table-card-header p {
  margin: 3px 0 0;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-text-tertiary);
}

.sys-table-card-header code {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 1px 6px;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs);
  color: #475569;
}

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--spd-font-size-base);
}

.sys-table thead tr {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-bottom: 1px solid #6ee7b7;
}

.sys-table th {
  color: #065f46;
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
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
.muted { color: #94a3b8; padding: 32px 16px; font-size: var(--spd-font-size-base); }

/* 选项值 monospace tag */
.sys-tag.is-code {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1px 8px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  white-space: nowrap;
  color: #334155;
  background: #f1f5f9;
  font-family: var(--spd-font-family-mono);
}

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-lg);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
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
  font-size: var(--spd-font-size-base);
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
