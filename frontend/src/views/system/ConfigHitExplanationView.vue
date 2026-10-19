<script setup lang="ts">
/**
 * 配置命中说明（系统管理 / 配置命中说明）
 * ---------------------------------------------------------------------
 * 造型与系统管理其他页面保持一致：
 *   - MdPageHeader（无 actions 按钮）
 *   - 4 张主题色 KPI 卡
 *   - 查询条件卡：Filter 图标 + section-title
 *   - 命中结果：左右双面板（最终配置 / 优先级规则）+ 候选链路表
 *   - 命中行：浅绿底；被覆盖行：灰底
 */
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Award,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  Clock,
  Database,
  FileSearch,
  Filter,
  GitBranch,
  Globe,
  Hash,
  History,
  Info,
  Layers,
  Lightbulb,
  RefreshCw,
  Save,
  Search,
  Settings,
  Shield,
  SlidersHorizontal,
  Tag,
  X
} from '@lucide/vue'
import { fetchConfigHitExplanation, type ConfigHitExplanation } from '../../api/systemConfig'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import { formatStatusText } from '../../utils/chineseDisplay'

// ── 查询上下文字段 ──────────────────────────────────────────────────────────
interface ConfigType {
  code: string
  label: string
}

const configTypes: ConfigType[] = [
  { code: 'workflow',  label: '业务流程配置' },
  { code: 'permission', label: '权限配置'   },
  { code: 'parameter', label: '参数配置'   },
  { code: 'base',      label: '基础配置'   },
  { code: 'audit',     label: '日志审计'   },
  { code: 'data',      label: '数据管理'   },
  { code: 'integration', label: '系统集成'  }
]

const query = ref({
  businessPage: '科室申领',
  configType: 'workflow',
  userId: 'admin',
  moduleId: 'purchase_order',
  warehouseId: 'WH-CENTER',
  departmentId: 'DEPT-ORTH',
  campusId: '主院区',
  hospitalId: 'default',
  tenantId: ''
})

// ── 数据 ─────────────────────────────────────────────────────────────────────
const explanation = ref<ConfigHitExplanation | null>(null)
const loading = ref(false)
const error = ref('')

// ── KPI 摘要（4 张）───────────────────────────────────────────────────────────
const kpiStats = computed(() => {
  const exp = explanation.value
  const total = exp?.candidates?.length ?? 0
  const hit = exp?.candidates?.filter(c => c.hit).length ?? 0
  const overridden = exp?.candidates?.filter(c => c.overridden).length ?? 0
  const riskHigh = exp?.candidates?.filter(c => c.riskLevel === 'high').length ?? 0
  return [
    { icon: 'list',     theme: 'blue',   label: '候选配置数',   value: total,      hint: '当前上下文链路总数' },
    { icon: 'check',   theme: 'green',  label: '命中配置',     value: hit,        hint: '最终生效配置'       },
    { icon: 'shield',  theme: 'orange', label: '被覆盖配置',   value: overridden,  hint: '被更细粒度覆盖'    },
    { icon: 'risk',    theme: 'rose',   label: '高风险配置',   value: riskHigh,   hint: '需重点关注'        }
  ]
})

// ── 辅助函数 ───────────────────────────────────────────────────────────────────
function labelFor(code: string) {
  return configTypes.find(t => t.code === code)?.label ?? code
}

function riskChipType(level?: string): 'high' | 'medium' | 'low' | 'default' {
  if (level === 'high')   return 'high'
  if (level === 'medium') return 'medium'
  if (level === 'low')    return 'low'
  return 'default'
}

// ── 动作 ─────────────────────────────────────────────────────────────────────
async function loadExplanation() {
  loading.value = true
  error.value = ''
  try {
    explanation.value = await fetchConfigHitExplanation(query.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '配置命中说明加载失败'
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.value = {
    businessPage: '科室申领',
    configType: 'workflow',
    userId: 'admin',
    moduleId: 'purchase_order',
    warehouseId: 'WH-CENTER',
    departmentId: 'DEPT-ORTH',
    campusId: '主院区',
    hospitalId: 'default',
    tenantId: ''
  }
  loadExplanation()
}

onMounted(loadExplanation)
</script>

<template>
  <div class="sys-page">

    <MdPageHeader
      title="配置命中说明"
      description="展示业务页面最终生效配置及来源层级，说明上级配置如何被更细粒度配置覆盖。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadExplanation"
    />

    <!-- 顶部 4 张主题色 KPI 卡 -->
    <section class="sys-kpi-card" aria-label="配置命中统计">
      <div v-for="item in kpiStats" :key="item.label" class="sys-kpi-item" :class="`is-${item.theme}`">
        <i class="sys-kpi-icon" :class="`is-${item.icon}`">
          <Layers v-if="item.icon === 'list'" :size="18" />
          <CheckCircle2 v-else-if="item.icon === 'check'" :size="18" />
          <Shield v-else-if="item.icon === 'shield'" :size="18" />
          <AlertCircle v-else-if="item.icon === 'risk'" :size="18" />
          <Activity v-else :size="18" />
        </i>
        <div>
          <span class="sys-kpi-label">{{ item.label }}</span>
          <strong class="sys-kpi-value">{{ item.value }}</strong>
          <span class="sys-kpi-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

    <!-- 查询条件卡 -->
    <div class="sys-form-section">
      <div class="sys-form-section-header">
        <i class="sys-form-section-icon is-green"><Filter :size="15" /></i>
        <div>
          <h4>查询上下文</h4>
          <p>输入业务页面与配置类型，系统将展示命中配置及覆盖链路</p>
        </div>
      </div>
      <div class="sys-form-grid">
        <label><span>业务页面</span><input v-model.trim="query.businessPage" class="sys-input" /></label>
        <label><span>配置类型</span>
          <select v-model="query.configType" class="sys-select">
            <option v-for="t in configTypes" :key="t.code" :value="t.code">{{ t.label }}</option>
          </select>
        </label>
        <label><span>用户</span><input v-model.trim="query.userId" class="sys-input" /></label>
        <label><span>模块</span><input v-model.trim="query.moduleId" class="sys-input" /></label>
        <label><span>库房</span><input v-model.trim="query.warehouseId" class="sys-input" /></label>
        <label><span>科室</span><input v-model.trim="query.departmentId" class="sys-input" /></label>
        <label><span>院区</span><input v-model.trim="query.campusId" class="sys-input" /></label>
        <label><span>医院</span><input v-model.trim="query.hospitalId" class="sys-input" /></label>
        <div class="sys-form-actions">
          <button class="btn" type="button" @click="resetQuery"><RefreshCw :size="15" /> 重置</button>
          <button class="btn btn-primary" type="button" @click="loadExplanation"><Search :size="15" /> 查看命中</button>
        </div>
      </div>
    </div>

    <!-- 加载 / 错误 -->
    <div v-if="loading" class="sys-loading-card" role="status">
      <i class="sys-loading-icon"><Search :size="20" /></i>
      <p>正在计算配置命中...</p>
    </div>
    <div v-else-if="error" class="sys-error" role="alert">{{ error }}</div>

    <!-- 命中结果 -->
    <template v-else-if="explanation">

      <!-- 命中结果 KPI（第 2 行，4 张） -->
      <section class="sys-kpi-card" aria-label="命中结果详情">
        <div class="sys-kpi-item is-green">
          <i class="sys-kpi-icon is-check"><CheckCircle2 :size="18" /></i>
          <div>
            <span class="sys-kpi-label">最终来源层级</span>
            <strong class="sys-kpi-value">{{ explanation.sourceLevel || '-' }}</strong>
            <span class="sys-kpi-hint">命中的最细粒度层级</span>
          </div>
        </div>
        <div class="sys-kpi-item is-blue">
          <i class="sys-kpi-icon is-tag"><Tag :size="18" /></i>
          <div>
            <span class="sys-kpi-label">来源配置编号</span>
            <strong class="sys-kpi-value" style="font-size:15px">
              {{ explanation.sourceConfigId ? `CFG-${explanation.sourceConfigId}` : '-' }}
            </strong>
            <span class="sys-kpi-hint">最终生效配置编号</span>
          </div>
        </div>
        <div class="sys-kpi-item is-orange">
          <i class="sys-kpi-icon is-branch"><GitBranch :size="18" /></i>
          <div>
            <span class="sys-kpi-label">覆盖上级配置</span>
            <strong class="sys-kpi-value">{{ explanation.overridesParent ? '是' : '否' }}</strong>
            <span class="sys-kpi-hint">是否覆盖了上级通用配置</span>
          </div>
        </div>
        <div class="sys-kpi-item is-purple">
          <i class="sys-kpi-icon is-award"><Award :size="18" /></i>
          <div>
            <span class="sys-kpi-label">审批状态</span>
            <strong class="sys-kpi-value" style="font-size:14px">
              {{ formatStatusText(explanation.approvalStatus) }}
            </strong>
            <span class="sys-kpi-hint">配置变更审批状态</span>
          </div>
        </div>
      </section>

      <!-- 左右双面板：最终命中配置 + 优先级规则 -->
      <div class="sys-two-col">

        <!-- 左：最终命中配置详情 -->
        <article class="sys-detail-card">
          <div class="sys-detail-card-header">
            <i class="sys-detail-card-icon"><SlidersHorizontal :size="15" /></i>
            <div>
              <h4>最终命中配置</h4>
              <p>业务页面 <code>{{ explanation.businessPage || '-' }}</code> 的最终生效值</p>
            </div>
          </div>
          <div class="sys-detail-body">
            <dl class="sys-detail-dl">
              <div class="sys-detail-row">
                <dt>业务页面</dt>
                <dd>{{ explanation.businessPage || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>配置类型</dt>
                <dd>
                  <span class="sys-tag is-type">{{ labelFor(explanation.configType) }}</span>
                </dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>最终生效值</dt>
                <dd class="sys-json-inline">{{ explanation.finalValue }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>来源层级</dt>
                <dd>{{ explanation.sourceLevel || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>来源配置编号</dt>
                <dd>
                  <span class="sys-tag is-code">{{ explanation.sourceConfigId ? `CFG-${explanation.sourceConfigId}` : '-' }}</span>
                </dd>
              </div>
              <div class="sys-detail-row">
                <dt>适用对象</dt>
                <dd>{{ explanation.scopeType }} / {{ explanation.scopeId }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>生效时间</dt>
                <dd>{{ explanation.effectiveTime || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>失效时间</dt>
                <dd>{{ explanation.expireTime || '-' }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>上级配置值</dt>
                <dd class="sys-json-inline is-parent">{{ explanation.parentValue || '-' }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>最近变更人</dt>
                <dd>{{ explanation.lastChangedBy || '-' }}</dd>
              </div>
            </dl>
          </div>
        </article>

        <!-- 右：优先级规则 -->
        <article class="sys-detail-card">
          <div class="sys-detail-card-header">
            <i class="sys-detail-card-icon is-orange"><Lightbulb :size="15" /></i>
            <div>
              <h4>优先级规则</h4>
              <p>系统如何按层级由粗到细匹配并覆盖配置</p>
            </div>
          </div>
          <div class="sys-detail-body">
            <ol class="sys-rule-list">
              <li v-for="(rule, i) in explanation.priorityRules" :key="i" class="sys-rule-item">
                <i class="sys-rule-num">{{ i + 1 }}</i>
                <span>{{ rule }}</span>
              </li>
            </ol>
          </div>
        </article>

      </div>

      <!-- 候选配置链路表 -->
      <div class="sys-table-card">
        <div class="sys-table-card-header">
          <div>
            <h4>候选配置链路</h4>
            <p>从最粗粒度到最细粒度的全部候选配置，按优先级排序</p>
          </div>
          <span class="sys-table-card-meta">共 <strong>{{ explanation.candidates.length }}</strong> 条</span>
        </div>
        <div class="sys-table-scroll">
          <table class="sys-table">
            <thead>
              <tr>
                <th style="width:90px">命中</th>
                <th style="width:70px">优先级</th>
                <th>来源层级</th>
                <th>配置编号</th>
                <th>配置名称</th>
                <th>适用对象</th>
                <th>配置值</th>
                <th>生效时间</th>
                <th>失效时间</th>
                <th style="width:70px">风险</th>
                <th style="width:90px">审批状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in explanation.candidates"
                :key="row.configId"
                :class="{
                  'is-hit': row.hit,
                  'is-overridden': row.overridden
                }"
              >
                <td>
                  <span v-if="row.hit" class="sys-badge is-hit">
                    <CheckCircle2 :size="11" /> 最终命中
                  </span>
                  <span v-else-if="row.overridden" class="sys-badge is-overridden">
                    被覆盖
                  </span>
                  <span v-else class="sys-badge is-none">—</span>
                </td>
                <td>
                  <span class="sys-tag is-priority">#{{ row.priority }}</span>
                </td>
                <td>
                  <span class="sys-tag is-level">{{ row.sourceLevel }}</span>
                </td>
                <td>
                  <span class="sys-tag is-code">CFG-{{ row.configId }}</span>
                </td>
                <td>{{ row.configName || '-' }}</td>
                <td>
                  <span class="sys-tag is-scope">{{ row.scopeType }}</span>
                  <span class="sys-tag is-code">{{ row.scopeId }}</span>
                </td>
                <td class="config-value-cell" :title="row.configValue">{{ row.configValue }}</td>
                <td>{{ row.effectiveTime || '-' }}</td>
                <td>{{ row.expireTime || '-' }}</td>
                <td>
                  <span :class="['sys-chip', `is-risk-${riskChipType(row.riskLevel)}`]">
                    {{ row.riskLevel === 'high' ? '高' : row.riskLevel === 'medium' ? '中' : row.riskLevel === 'low' ? '低' : '—' }}
                  </span>
                </td>
                <td>
                  <span :class="['sys-badge', row.approvalStatus === '已批准' ? 'is-approved' : 'is-pending']">
                    {{ formatStatusText(row.approvalStatus) }}
                  </span>
                </td>
              </tr>
              <tr v-if="!explanation.candidates.length">
                <td colspan="11" class="text-center muted">当前上下文未命中已生效配置</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </template>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 配置命中说明
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡（候选数 / 命中数 / 被覆盖 / 高风险）。
   2. 查询条件卡：绿底 Filter 图标 + section-title。
   3. 命中结果：第 2 行 4 张详情卡 + 左右双面板（最终配置 / 优先级规则）。
   4. 候选链路表：命中行浅绿底，被覆盖行灰底，状态 chip 彩色。
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

.sys-kpi-item.is-blue    { background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%); border-color: #93c5fd; }
.sys-kpi-item.is-green   { background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%); border-color: #6ee7b7; }
.sys-kpi-item.is-orange  { background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%); border-color: #fcd34d; }
.sys-kpi-item.is-purple  { background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%); border-color: #c4b5fd; }
.sys-kpi-item.is-rose    { background: linear-gradient(180deg, #ffe4e6 0%, #fecdd3 100%); border-color: #fda4af; }
.sys-kpi-item.is-cyan    { background: linear-gradient(180deg, #cffafe 0%, #a5f3fc 100%); border-color: #67e8f9; }
.sys-kpi-item.is-teal    { background: linear-gradient(180deg, #ccfbf1 0%, #99f6e4 100%); border-color: #5eead4; }

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
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }
.sys-kpi-item.is-purple .sys-kpi-icon { color: #6d28d9; }
.sys-kpi-item.is-rose   .sys-kpi-icon { color: #be123c; }
.sys-kpi-item.is-cyan   .sys-kpi-icon { color: #0e7490; }
.sys-kpi-item.is-teal   .sys-kpi-icon { color: #0f766e; }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label { font-size: 12px; font-weight: 500; opacity: 0.85; }
.sys-kpi-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.sys-kpi-hint { font-size: 11px; font-weight: 400; line-height: 1.3; opacity: 0.75; }

.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: #4c1d95; }
.sys-kpi-item.is-rose   .sys-kpi-label,
.sys-kpi-item.is-rose   .sys-kpi-value,
.sys-kpi-item.is-rose   .sys-kpi-hint { color: #881337; }
.sys-kpi-item.is-cyan   .sys-kpi-label,
.sys-kpi-item.is-cyan   .sys-kpi-value,
.sys-kpi-item.is-cyan   .sys-kpi-hint { color: #155e75; }
.sys-kpi-item.is-teal   .sys-kpi-label,
.sys-kpi-item.is-teal   .sys-kpi-value,
.sys-kpi-item.is-teal   .sys-kpi-hint { color: #134e4a; }

/* ---------- 查询条件卡 ---------- */
.sys-form-section {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  padding: 18px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.sys-form-section-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.sys-form-section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex-shrink: 0;
}

.sys-form-section-icon.is-green { background: #d1fae5; color: #047857; }
.sys-form-section-icon.is-orange { background: #fef3c7; color: #b45309; }

.sys-form-section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-form-section-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--spd-text-tertiary);
}

.sys-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
  color: #4b5b66;
}

.sys-form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 4px;
}

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
.sys-select:focus { outline: none; }

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

.btn:hover:not(:disabled) { background: #f8fafc; border-color: #94a3b8; color: #1e293b; }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }

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

/* ---------- 加载 / 消息 / 错误 ---------- */
.sys-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  color: #64748b;
}

.sys-loading-icon {
  display: inline-flex;
  color: #10b981;
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.sys-loading-card p { margin: 0; font-size: 13px; }

.sys-error {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: #fee2e2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  color: #991b1b;
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- 左右双栏面板 ---------- */
.sys-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

@media (max-width: 1000px) {
  .sys-two-col { grid-template-columns: 1fr; }
}

/* ---------- 详情面板卡片 ---------- */
.sys-detail-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
}

.sys-detail-card-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
}

.sys-detail-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #d1fae5;
  color: #047857;
  flex-shrink: 0;
}

.sys-detail-card-icon.is-orange { background: #fef3c7; color: #b45309; }

.sys-detail-card-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-detail-card-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--spd-text-tertiary);
}

.sys-detail-card-header code {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 1px 6px;
  font-family: var(--spd-font-mono), monospace;
  font-size: 11.5px;
  color: #475569;
}

.sys-detail-body {
  padding: 14px 16px;
}

/* ---------- 详情 dl 网格 ---------- */
.sys-detail-dl {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

.sys-detail-row {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 8px 14px;
  padding: 7px 0;
  border-bottom: 1px solid #f1f5f9;
  align-items: start;
}

.sys-detail-row:last-child { border-bottom: none; }
.sys-detail-row.is-wide { grid-column: 1 / -1; }

.sys-detail-row dt {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  padding-top: 2px;
  flex-shrink: 0;
}

.sys-detail-row dd {
  margin: 0;
  color: #102033;
  font-size: 13px;
  font-weight: 500;
  word-break: break-all;
}

.sys-json-inline {
  font-family: var(--spd-font-mono), monospace;
  font-size: 12px;
  color: #334155;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 6px 10px;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 100px;
  overflow: auto;
}

.sys-json-inline.is-parent {
  background: #fef3c7;
  border-color: #fcd34d;
  color: #78350f;
}

/* ---------- 规则列表 ---------- */
.sys-rule-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sys-rule-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fafdfb;
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}

.sys-rule-item:hover { background: #f0fdf9; }

.sys-rule-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
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
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
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

.sys-table-card-meta {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  padding-top: 2px;
}

.sys-table-card-meta strong { color: #047857; font-weight: 700; }

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

.sys-table tbody tr:hover td { background: #f8fafc; }
.sys-table tbody tr:last-child td { border-bottom: none; }

/* 命中行 / 被覆盖行 */
.sys-table tbody tr.is-hit td {
  background: rgba(16, 185, 129, 0.07) !important;
  border-bottom-color: rgba(16, 185, 129, 0.15);
}

.sys-table tbody tr.is-overridden td {
  background: #f8fafc !important;
  color: #94a3b8;
}

.text-center { text-align: center; }
.muted { color: #94a3b8; padding: 32px 16px; font-size: 13px; }

.config-value-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--spd-font-mono), monospace;
  font-size: 12px;
  color: #475569;
}

/* ---------- 通用 tag / chip / badge ---------- */
.sys-tag {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  border-radius: 6px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  gap: 4px;
}

.sys-tag.is-code {
  color: #334155;
  background: #f1f5f9;
  border-color: #e2e8f0;
  font-family: var(--spd-font-mono), monospace;
  font-size: 11.5px;
}

.sys-tag.is-type {
  color: #047857;
  background: #d1fae5;
  border-color: #6ee7b7;
  font-size: 12px;
}

.sys-tag.is-priority {
  color: #1d4ed8;
  background: #dbeafe;
  border-color: #93c5fd;
  font-family: var(--spd-font-mono), monospace;
}

.sys-tag.is-level {
  color: #6d28d9;
  background: #ede9fe;
  border-color: #c4b5fd;
}

.sys-tag.is-scope {
  color: #b45309;
  background: #fef3c7;
  border-color: #fcd34d;
}

/* 风险 chip */
.sys-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
}

.sys-chip.is-risk-high   { color: #991b1b; background: #fee2e2; border-color: #fca5a5; }
.sys-chip.is-risk-medium { color: #b45309; background: #fef3c7; border-color: #fcd34d; }
.sys-chip.is-risk-low    { color: #065f46; background: #d1fae5; border-color: #6ee7b7; }
.sys-chip.is-risk-default { color: #64748b; background: #f1f5f9; border-color: #e2e8f0; }

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
}

.sys-badge.is-hit {
  color: #065f46;
  background: #d1fae5;
  border: 1px solid #6ee7b7;
}

.sys-badge.is-overridden {
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.sys-badge.is-none {
  color: #94a3b8;
  background: transparent;
  border: 1px solid #e2e8f0;
}

.sys-badge.is-approved {
  color: #065f46;
  background: #d1fae5;
  border: 1px solid #6ee7b7;
}

.sys-badge.is-pending {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fcd34d;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-form-grid { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
