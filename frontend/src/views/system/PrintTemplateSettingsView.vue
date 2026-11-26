<script setup lang="ts">
/**
 * 打印模板调整
 * ---------------------------------------------------------------------
 * 维护定数包标签打印模板的字段、顺序与纸张设置。
 * 视觉沿用主数据模块（MdDataTableCard / MdFormDialog）
 * 的 md-* 设计语言，与其他菜单保持一致。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Plus, RotateCcw, Trash2 } from '@lucide/vue'
import {
  createPrintTemplate,
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
import MdStatus from '../../components/master-data/MdStatus.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { defaultPrintConfig } from '../../utils/zebraBrowserPrint'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()

const rows = ref<PrintTemplateRow[]>([])
const loading = ref(false)
const error = ref('')

const showEditor = ref(false)
const editing = ref<PrintTemplateRow | null>(null)
const fields = ref<PrintTemplateField[]>([])
const templateCode = ref('')
const templateName = ref('')
const paperWidthMm = ref(40)
const paperHeightMm = ref(60)
const status = ref(1)
const remark = ref('')
const saving = ref(false)
const formError = ref('')
const saveFeedback = ref<HTMLElement>()
const newFieldCode = ref('')
const newFieldLabel = ref('')
const newFieldError = ref('')
const newFieldFeedback = ref('')
const fieldFeedback = ref<HTMLElement>()

watch([newFieldError, newFieldFeedback], () => {
  fieldFeedback.value?.scrollIntoView({ block: 'nearest' })
}, { flush: 'post' })

watch(formError, error => {
  if (error) saveFeedback.value?.scrollIntoView({ block: 'nearest' })
}, { flush: 'post' })

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

function openEditor(row: PrintTemplateRow | null) {
  editing.value = row
  templateCode.value = row?.templateCode ?? ''
  templateName.value = row?.templateName ?? ''
  paperWidthMm.value = Number(row?.paperWidthMm) || 40
  paperHeightMm.value = Number(row?.paperHeightMm) || 60
  status.value = Number(row?.status ?? 1)
  remark.value = row?.remark ?? ''
  fields.value = row ? parseTemplateFields(row.fieldsJson) : defaultPrintConfig().fields
  resetNewField()
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
  newFieldFeedback.value = ''
  if (!code) {
    newFieldError.value = '请填写新增字段的编码'
    return
  }
  if (fields.value.some(field => field.code === code)) {
    newFieldError.value = '该字段编码已存在'
    return
  }
  fields.value = [...fields.value, { code, label: label || code, enabled: true }]
  resetNewField()
  newFieldFeedback.value = `已添加「${label || code}」，保存模板后生效`
}

function resetNewField() {
  newFieldCode.value = ''
  newFieldLabel.value = ''
  newFieldError.value = ''
  newFieldFeedback.value = ''
}

function removeField(field: PrintTemplateField) {
  fields.value = fields.value.filter(item => item.code !== field.code)
  newFieldFeedback.value = ''
}

function setFixedFieldValue(field: PrintTemplateField, event: Event) {
  const value = (event.target as HTMLInputElement).value
  field.value = value || undefined
}

async function saveTemplate() {
  if (saving.value || !auth.canWrite('print-template-settings')) return
  if (!editing.value && !/^[\w-]{1,50}$/.test(templateCode.value.trim())) {
    formError.value = '模板编码须为 1 至 50 位字母、数字、下划线或短横线'
    return
  }
  if (!templateName.value.trim()) {
    formError.value = '模板名称为必填项'
    return
  }
  if (![paperWidthMm.value, paperHeightMm.value].every(value =>
    Number.isFinite(value) && value >= 0.01 && value <= 999999.99 && Math.round(value * 100) / 100 === value
  )) {
    formError.value = '纸张宽度和高度须为 0.01 至 999999.99 mm，最多两位小数'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = {
      templateName: templateName.value.trim(),
      fieldsJson: JSON.stringify(fields.value),
      paperWidthMm: paperWidthMm.value,
      paperHeightMm: paperHeightMm.value,
      status: status.value,
      remark: remark.value.trim()
    }
    if (editing.value) await updatePrintTemplate(editing.value.id, payload)
    else await createPrintTemplate(templateCode.value.trim(), payload)
    showEditor.value = false
    await loadList()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '打印模板保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadList)

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const PRINTTEMPLATE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'templateCode', label: '模板编码', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'templateName', label: '模板名称', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'templateType', label: '模板类型', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 190 },
  { key: 'paper', label: '纸张（宽 × 高 mm）', width: 160, visible: true, weight: 3, minWidth: 130, maxWidth: 230 },
  { key: 'fields', label: '启用字段', width: 220, visible: true, flexible: true, idealWidth: 280, weight: 6, minWidth: 140, maxWidth: 380 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  // 「操作」列必须出现在 defaults 里：isColumnVisible() 只查 defaults，
  // 缺少该键时 v-if="isPrintTemplateColVisible('actions')" 恒为 false，
  // 整列不会渲染，「调整模板」按钮将完全无法触达。
  { key: 'actions', label: '操作', width: 140, visible: true, weight: 3, minWidth: 120, maxWidth: 200, fill: true }
]

const printTemplateCols = useListColumns({
  storageKey: 'spd-print-template-cols-v1',
  defaults: PRINTTEMPLATE_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「启用字段」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const printTemplateTableRef = printTemplateCols.tableRef
const isPrintTemplateColVisible = (key: string) => printTemplateCols.isColumnVisible(key)
const printTemplateColBinding = (key: string) => printTemplateCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const printTemplateActionFix = useActionColumnFix()
const printTemplateActionFixed = computed(() => printTemplateActionFix.actionFixedBinding.value)

watch(
  [printTemplateTableRef],
  () => {
    printTemplateActionFix.observe(printTemplateTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const printTemplateColSettingsAnchorRef = ref<HTMLElement | null>(null)
const printTemplateColSettingsPopRef = ref<HTMLElement | null>(null)
const printTemplateColSettingsPop = useColSettingsPopover({
  open: printTemplateCols.settingsOpen,
  anchorRef: printTemplateColSettingsAnchorRef,
  popRef: printTemplateColSettingsPopRef
})

</script>

<template>
  <section class="md-page md-print-template-page spd-unified-list">
    <p v-if="error" class="md-error">{{ error }}</p>

    <MdDataTableCard :meta-count="`共 ${rows.length} 个模板`" :loading="loading">
      <template #toolbar>
        <button v-if="auth.canWrite('print-template-settings')" class="md-btn md-btn-primary" type="button" @click="openEditor(null)">
          <Plus :size="15" /> 新建模板
        </button>
          <div class="ulc-col-settings">
            <button
              ref="printTemplateColSettingsAnchorRef"
              type="button"
              class="md-btn md-btn-primary"
              @click="printTemplateCols.settingsOpen.value = !printTemplateCols.settingsOpen.value"
            >
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: printTemplateCols.settingsOpen.value }" />
            </button>
            <div
              v-if="printTemplateCols.settingsOpen.value"
              ref="printTemplateColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': printTemplateColSettingsPop.align.value === 'right', 'is-align-up': printTemplateColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': printTemplateColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': printTemplateColSettingsPop.maxHeight.value + 'px' }"
            >
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="printTemplateCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in printTemplateCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && printTemplateCols.visibleColumnCount.value <= 1"
                    @change="printTemplateCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="printTemplateCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
      </template>
      <MdEmptyState v-if="loading" text="正在加载打印模板..." :loading="true" />
      <MdEmptyState v-else-if="error" :text="error" />
      <div v-else class="ulc-table-wrap">
        <el-table
          ref="printTemplateTableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          border
          stripe
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="'暂无打印模板'"
          @header-dragend="(width: number, oldWidth: number, column: any) => printTemplateCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="templateCode"
            v-if="isPrintTemplateColVisible('templateCode')"
            label="模板编码"
            v-bind="printTemplateColBinding('templateCode')"
          >
            <template #header>
              <span class="ulc-th-text">模板编码</span>
            </template>
            <template #default="{ row }">
              <span class="md-cell-mono ulc-wrap-text">
                {{ row.templateCode }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="templateName"
            v-if="isPrintTemplateColVisible('templateName')"
            label="模板名称"
            v-bind="printTemplateColBinding('templateName')"
          >
            <template #header>
              <span class="ulc-th-text">模板名称</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
              <span class="ulc-strong ulc-wrap-text">{{ row.templateName }}</span>
              <span class="ulc-sub ulc-wrap-text is-sub" v-if="row.remark">{{ row.remark }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="templateType"
            v-if="isPrintTemplateColVisible('templateType')"
            label="模板类型"
            v-bind="printTemplateColBinding('templateType')"
          >
            <template #header>
              <span class="ulc-th-text">模板类型</span>
            </template>
            <template #default="{ row }">
              <span class="md-chip ulc-wrap-text">{{ templateTypeText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="paper"
            v-if="isPrintTemplateColVisible('paper')"
            label="纸张（宽 × 高 mm）"
            v-bind="printTemplateColBinding('paper')"
          >
            <template #header>
              <span class="ulc-th-text">纸张（宽 × 高 mm）</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-joined">
                <span class="ulc-strong">{{ row.paperWidthMm }}</span>
                <span class="ulc-sub">× {{ row.paperHeightMm }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="fields"
            v-if="isPrintTemplateColVisible('fields')"
            label="启用字段"
            v-bind="printTemplateColBinding('fields')"
          >
            <template #header>
              <span class="ulc-th-text">启用字段</span>
            </template>
            <template #default="{ row }">
              <span class="md-chip primary">
              {{ parseTemplateFields(row.fieldsJson).filter(f => f.enabled).length }} 项
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="status"
            v-if="isPrintTemplateColVisible('status')"
            label="状态"
            v-bind="printTemplateColBinding('status')"
          >
            <template #header>
              <span class="ulc-th-text">状态</span>
            </template>
            <template #default="{ row }">
              <MdStatus :label="statusText(row)" :variant="row.status === 1 ? 'success' : 'neutral'" />
            </template>
          </el-table-column>
          <el-table-column
            column-key="createTime"
            v-if="isPrintTemplateColVisible('createTime')"
            label="创建时间"
            v-bind="printTemplateColBinding('createTime')"
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
            v-if="isPrintTemplateColVisible('updateTime')"
            label="修改时间"
            v-bind="printTemplateColBinding('updateTime')"
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

            v-if="isPrintTemplateColVisible('actions')"

            label="操作"

            v-bind="printTemplateColBinding('actions')"

            :fixed="printTemplateActionFixed"
            align="center"
          >
            <template #header>
              <span class="ulc-th-text">操作</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-actions">
              <button v-if="auth.canWrite('print-template-settings')" class="ulc-action" type="button" @click="openEditor(row)">调整模板</button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </MdDataTableCard>

    <Teleport to="body">
    <MdFormDialog
      :open="showEditor"
      size="lg"
      :title="editing ? '调整模板' : '新建模板'"
      :subtitle="editing ? `${editing.templateCode} · ${templateTypeText(editing)}` : '定数包标签'"
      :loading="saving"
      save-text="保存模板"
      @close="!saving && (showEditor = false)"
      @save="saveTemplate"
    >
      <div class="md-print-template-dialog-body">
        <div v-if="formError" ref="saveFeedback">
          <StatusMessage :message="formError" tone="error" role="alert" />
        </div>

        <MdFormSection title="基本信息">
          <div class="md-form-grid">
            <label v-if="!editing">
              <span>模板编码 <em class="md-required">*</em></span>
              <input v-model.trim="templateCode" class="md-input" maxlength="50" required />
            </label>
            <label>
              <span>模板名称 <em class="md-required">*</em></span>
              <input v-model.trim="templateName" class="md-input" maxlength="100" required />
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
              <input v-model.number="paperWidthMm" class="md-input" type="number" min="0.01" max="999999.99" step="0.01" required />
            </label>
            <label>
              <span>纸张高度（mm）</span>
              <input v-model.number="paperHeightMm" class="md-input" type="number" min="0.01" max="999999.99" step="0.01" required />
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
              <textarea v-model.trim="remark" class="md-textarea" rows="2" maxlength="500" />
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
              <input class="md-input md-print-template-field-value" :value="field.value"
                :aria-label="`固定值 ${field.code}`" placeholder="留空取动态值" @input="setFixedFieldValue(field, $event)" />
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
          <div class="md-print-template-add-field">
            <input v-model.trim="newFieldCode" class="md-input" aria-label="新字段编码"
              :aria-invalid="Boolean(newFieldError)" :aria-describedby="newFieldError ? 'print-template-field-error' : undefined"
              placeholder="新字段编码，如 operator" @input="newFieldError = ''; newFieldFeedback = ''"
              @keydown.enter.prevent="addField" />
            <input v-model.trim="newFieldLabel" class="md-input" aria-label="新字段名称" placeholder="新字段名称，如 操作员"
              @keydown.enter.prevent="addField" />
            <button class="md-btn md-btn-primary" type="button" @click="addField">
              <Plus :size="15" />
              <span>新增字段</span>
            </button>
            <button class="md-btn" type="button" :disabled="!newFieldCode && !newFieldLabel" @click="resetNewField">
              <RotateCcw :size="14" />
              <span>清空</span>
            </button>
            <div v-if="newFieldError || newFieldFeedback" ref="fieldFeedback" class="md-print-template-field-feedback">
              <StatusMessage id="print-template-field-error" :message="newFieldError" tone="error" role="alert" />
              <StatusMessage :message="newFieldFeedback" tone="success" role="status" />
            </div>
          </div>
        </MdFormSection>
      </div>
    </MdFormDialog>
    </Teleport>
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
  /* T-21 打印模板字段表数据：--spd-font-size-base（14/16/18px） */
  font-size: var(--spd-font-size-base);
  font-weight: 400;
  line-height: var(--spd-lh-base);
  color: var(--md-text);
}

.md-print-template-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  color: var(--md-text-heading);
  font-weight: 700;
  /* T-22 打印模板字段表表头：--spd-font-size-table-head（14/16/18px） */
  font-size: var(--spd-font-size-table-head);
  line-height: var(--spd-lh-tight);
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
  /* PR-1 打印模板配置界面字段值：--spd-font-size-base */
  font-size: var(--spd-font-size-base);
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
  /* E-07 打印模板空态：--spd-font-size-base */
  font-size: var(--spd-font-size-base);
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

.md-print-template-field-feedback {
  flex: 0 0 100%;
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
