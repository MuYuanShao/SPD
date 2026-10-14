<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GitBranch, RefreshCw, Search, SlidersHorizontal } from '@lucide/vue'
import { fetchConfigHitExplanation, type ConfigHitExplanation } from '../../api/systemConfig'
import EmptyState from '../../components/common/EmptyState.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import SectionTitle from '../../components/common/SectionTitle.vue'
import { formatStatusText } from '../../utils/chineseDisplay'

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

const explanation = ref<ConfigHitExplanation | null>(null)
const loading = ref(false)
const error = ref('')

const summaryCards = computed(() => [
  { label: '最终来源层级', value: explanation.value?.sourceLevel || '-' },
  { label: '来源配置编号', value: explanation.value?.sourceConfigId ? `CFG-${explanation.value.sourceConfigId}` : '-' },
  { label: '覆盖上级配置', value: explanation.value?.overridesParent ? '是' : '否' },
  { label: '审批状态', value: formatStatusText(explanation.value?.approvalStatus) }
])

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
  <div class="md-page sys-page">
    <MdPageHeader
      crumb="系统管理"
      title="配置命中说明"
      description="展示业务页面最终生效配置及来源层级，说明上级配置如何被更细粒度配置覆盖。"
      :loading="loading"
      @refresh="loadExplanation"
    />

    <div class="md-content sys-content">
      <!-- 查询表单 -->
      <div class="md-form-section">
        <h4>查询上下文</h4>
        <form class="md-form-grid" @submit.prevent="loadExplanation">
          <label><span>业务页面</span><input v-model.trim="query.businessPage" class="md-input" /></label>
          <label><span>配置类型</span><select v-model="query.configType" class="md-select"><option value="workflow">业务流程配置</option><option value="permission">权限配置</option><option value="parameter">参数配置</option><option value="base">基础配置</option><option value="audit">日志审计</option><option value="data">数据管理</option><option value="integration">系统集成</option></select></label>
          <label><span>用户</span><input v-model.trim="query.userId" class="md-input" /></label>
          <label><span>模块</span><input v-model.trim="query.moduleId" class="md-input" /></label>
          <label><span>库房</span><input v-model.trim="query.warehouseId" class="md-input" /></label>
          <label><span>科室</span><input v-model.trim="query.departmentId" class="md-input" /></label>
          <label><span>院区</span><input v-model.trim="query.campusId" class="md-input" /></label>
          <label><span>医院</span><input v-model.trim="query.hospitalId" class="md-input" /></label>
          <div class="md-form-grid-actions">
            <button class="md-btn" type="button" @click="resetQuery">重置</button>
            <button class="md-btn md-btn-primary" type="submit"><Search :size="15" /> 查看命中</button>
          </div>
        </form>
      </div>

      <div v-if="loading" class="text-center muted" style="padding:40px">正在计算配置命中...</div>
      <div v-else-if="error" class="md-error">{{ error }}</div>

      <template v-else-if="explanation">
        <div class="sys-stat-grid">
          <article v-for="item in summaryCards" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>

        <div class="config-hit-grid">
          <article class="md-card-soft">
            <div class="md-panel-header"><h4 class="md-panel-title"><SlidersHorizontal :size="16" /> 最终命中配置</h4></div>
            <dl class="config-hit-dl">
              <dt>业务页面</dt><dd>{{ explanation.businessPage }}</dd>
              <dt>最终生效值</dt><dd class="json-value">{{ explanation.finalValue }}</dd>
              <dt>配置类型</dt><dd>{{ explanation.configType }}</dd>
              <dt>来源层级</dt><dd>{{ explanation.sourceLevel }}</dd>
              <dt>来源配置编号</dt><dd>{{ explanation.sourceConfigId ? 'CFG-' + explanation.sourceConfigId : '-' }}</dd>
              <dt>适用对象</dt><dd>{{ explanation.scopeType }} / {{ explanation.scopeId }}</dd>
              <dt>生效时间</dt><dd>{{ explanation.effectiveTime }}</dd>
              <dt>失效时间</dt><dd>{{ explanation.expireTime }}</dd>
              <dt>上级配置值</dt><dd class="json-value">{{ explanation.parentValue }}</dd>
              <dt>最近变更人</dt><dd>{{ explanation.lastChangedBy }}</dd>
            </dl>
          </article>
          <article class="md-card-soft">
            <div class="md-panel-header"><h4 class="md-panel-title"><GitBranch :size="16" /> 优先级规则</h4></div>
            <ol class="config-rule-list">
              <li v-for="rule in explanation.priorityRules" :key="rule">{{ rule }}</li>
            </ol>
          </article>
        </div>

        <div class="md-table-card">
          <div class="md-panel-header"><h4 class="md-panel-title"><GitBranch :size="16" /> 候选配置链路</h4></div>
          <div class="table-scroll">
            <table class="md-table">
              <thead>
                <tr>
                  <th>命中</th>
                  <th>优先级</th>
                  <th>来源层级</th>
                  <th>配置编号</th>
                  <th>配置名称</th>
                  <th>适用对象</th>
                  <th>配置值</th>
                  <th>生效时间</th>
                  <th>失效时间</th>
                  <th>风险</th>
                  <th>审批状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in explanation.candidates" :key="row.configId" :class="{ 'hit-row': row.hit }">
                  <td>
                    <span v-if="row.hit" class="md-chip success">最终命中</span>
                    <span v-else-if="row.overridden" class="md-chip neutral">被覆盖</span>
                    <span v-else>-</span>
                  </td>
                  <td>{{ row.priority }}</td>
                  <td>{{ row.sourceLevel }}</td>
                  <td>CFG-{{ row.configId }}</td>
                  <td>{{ row.configName }}</td>
                  <td>{{ row.scopeType }} / {{ row.scopeId }}</td>
                  <td class="config-value-cell">{{ row.configValue }}</td>
                  <td>{{ row.effectiveTime }}</td>
                  <td>{{ row.expireTime }}</td>
                  <td><span :class="['md-chip', row.riskLevel === 'high' ? 'danger' : '']">{{ row.riskLevel }}</span></td>
                  <td><span :class="['md-status', row.approvalStatus === '已批准' ? 'success' : 'neutral']">{{ formatStatusText(row.approvalStatus) }}</span></td>
                </tr>
                <tr v-if="!explanation.candidates.length"><td colspan="11" class="text-center muted">当前上下文未命中已生效配置</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.sys-page { display: flex; flex-direction: column; }
.sys-content { padding: 12px 0 24px; display: flex; flex-direction: column; gap: 12px; }
.sys-stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
.sys-stat-grid article { background: var(--md-bg-card); border: 1px solid var(--md-border); border-radius: var(--md-radius-md); padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; }
.sys-stat-grid article span { font-size: 12px; color: var(--md-text-secondary); font-weight: 500; }
.sys-stat-grid article strong { font-size: 22px; color: var(--md-text-heading); font-weight: 700; font-family: var(--md-font-mono); }
.md-form-grid-actions { grid-column: 1 / -1; display: flex; gap: 8px; justify-content: flex-end; }
.config-hit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 900px) { .config-hit-grid { grid-template-columns: 1fr; } }
.md-card-soft { background: var(--md-bg-card); border: 1px solid var(--md-border); border-radius: var(--md-radius-md); overflow: hidden; }
.config-hit-dl { padding: 14px 18px; display: grid; grid-template-columns: 100px 1fr; gap: 8px 14px; margin: 0; }
.config-hit-dl dt { color: var(--md-text-secondary); font-size: 12px; font-weight: 500; }
.config-hit-dl dd { margin: 0; color: var(--md-text); font-size: 13px; }
.json-value { font-family: var(--md-font-mono); font-size: 12px; color: var(--md-text-heading); word-break: break-all; }
.config-rule-list { padding: 14px 18px 14px 32px; margin: 0; color: var(--md-text); font-size: 13px; line-height: 1.8; }
.table-scroll { overflow-x: auto; }
.text-center { text-align: center; }
.muted { color: var(--md-text-muted); }
.config-value-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--md-font-mono); font-size: 12px; }
.hit-row td { background: rgba(13, 107, 99, 0.06) !important; }
</style>
