<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GitBranch, RefreshCw, Search, SlidersHorizontal } from '@lucide/vue'
import { fetchConfigHitExplanation, type ConfigHitExplanation } from '../../api/systemConfig'
import EmptyState from '../../components/common/EmptyState.vue'
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
  <section class="system-config-page">
    <header class="master-page-title">
      <div>
        <p>系统管理</p>
        <h2>配置命中说明</h2>
        <span>展示业务页面最终生效配置及来源层级，说明上级配置如何被更细粒度配置覆盖。</span>
      </div>
      <button class="btn" type="button" @click="loadExplanation">
        <RefreshCw :size="17" />
        刷新
      </button>
    </header>

    <section class="hospital-catalog-panel">
      <form class="hospital-query-grid config-hit-query-grid" @submit.prevent="loadExplanation">
        <label>
          <span>业务页面</span>
          <input v-model.trim="query.businessPage" type="text" />
        </label>
        <label>
          <span>配置类型</span>
          <select v-model="query.configType">
            <option value="workflow">业务流程配置</option>
            <option value="permission">权限配置</option>
            <option value="parameter">参数配置</option>
            <option value="base">基础配置</option>
            <option value="audit">日志审计</option>
            <option value="data">数据管理</option>
            <option value="integration">系统集成</option>
          </select>
        </label>
        <label>
          <span>用户</span>
          <input v-model.trim="query.userId" type="text" />
        </label>
        <label>
          <span>模块</span>
          <input v-model.trim="query.moduleId" type="text" />
        </label>
        <label>
          <span>库房</span>
          <input v-model.trim="query.warehouseId" type="text" />
        </label>
        <label>
          <span>科室</span>
          <input v-model.trim="query.departmentId" type="text" />
        </label>
        <label>
          <span>院区</span>
          <input v-model.trim="query.campusId" type="text" />
        </label>
        <label>
          <span>医院</span>
          <input v-model.trim="query.hospitalId" type="text" />
        </label>
        <div class="hospital-query-actions">
          <button class="btn btn-primary" type="submit">
            <Search :size="17" />
            查看命中
          </button>
          <button class="btn" type="button" @click="resetQuery">重置</button>
        </div>
      </form>
    </section>

    <p v-if="loading" class="approval-empty">正在计算配置命中...</p>
    <EmptyState v-else-if="error" :message="error" />

    <template v-else-if="explanation">
      <section class="config-summary-grid">
        <article v-for="item in summaryCards" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="config-hit-grid">
        <article class="config-hit-card">
          <SectionTitle :level="3">
            <template #icon>
              <SlidersHorizontal :size="20" />
            </template>
            <h3>最终命中配置</h3>
          </SectionTitle>
          <dl>
            <dt>业务页面</dt>
            <dd>{{ explanation.businessPage }}</dd>
            <dt>最终生效值</dt>
            <dd class="json-value">{{ explanation.finalValue }}</dd>
            <dt>配置类型</dt>
            <dd>{{ explanation.configType }}</dd>
            <dt>来源层级</dt>
            <dd>{{ explanation.sourceLevel }}</dd>
            <dt>来源配置编号</dt>
            <dd>{{ explanation.sourceConfigId ? `CFG-${explanation.sourceConfigId}` : '-' }}</dd>
            <dt>适用对象</dt>
            <dd>{{ explanation.scopeType }} / {{ explanation.scopeId }}</dd>
            <dt>生效时间</dt>
            <dd>{{ explanation.effectiveTime }}</dd>
            <dt>失效时间</dt>
            <dd>{{ explanation.expireTime }}</dd>
            <dt>上级配置值</dt>
            <dd class="json-value">{{ explanation.parentValue }}</dd>
            <dt>最近变更人</dt>
            <dd>{{ explanation.lastChangedBy }}</dd>
          </dl>
        </article>

        <article class="config-hit-card">
          <SectionTitle :level="3">
            <template #icon>
              <GitBranch :size="20" />
            </template>
            <h3>优先级规则</h3>
          </SectionTitle>
          <ol class="config-rule-list">
            <li v-for="rule in explanation.priorityRules" :key="rule">{{ rule }}</li>
          </ol>
        </article>
      </section>

      <section class="master-table-card">
          <SectionTitle :level="3">
            <template #icon>
              <GitBranch :size="20" />
            </template>
            <h3>候选配置链路</h3>
          </SectionTitle>
        <table class="master-table config-table">
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
              <td>{{ row.hit ? '最终命中' : row.overridden ? '被覆盖' : '-' }}</td>
              <td>{{ row.priority }}</td>
              <td>{{ row.sourceLevel }}</td>
              <td>CFG-{{ row.configId }}</td>
              <td>{{ row.configName }}</td>
              <td>{{ row.scopeType }} / {{ row.scopeId }}</td>
              <td class="config-value-cell">{{ row.configValue }}</td>
              <td>{{ row.effectiveTime }}</td>
              <td>{{ row.expireTime }}</td>
              <td>{{ row.riskLevel }}</td>
              <td>{{ formatStatusText(row.approvalStatus) }}</td>
            </tr>
            <tr v-if="!explanation.candidates.length">
              <td class="approval-empty" colspan="11">当前上下文未命中已生效配置</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </section>
</template>
