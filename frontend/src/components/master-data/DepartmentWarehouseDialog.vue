<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshCw, Save, Search, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import type { DepartmentWarehouseRelation } from '../../api/masterData'

const props = defineProps<{
  open: boolean
  department: Record<string, unknown> | null
  rows: DepartmentWarehouseRelation[]
  selectedCodes: string[]
  loading: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  close: []
  refresh: []
  save: []
  toggle: [warehouseCode: string, checked: boolean]
}>()

const warehouseNameKeyword = ref('')
const filteredRows = computed(() => {
  const keyword = warehouseNameKeyword.value.trim().toLowerCase()
  if (!keyword) return props.rows
  return props.rows.filter((row) => row.name.toLowerCase().includes(keyword))
})

watch(() => props.open, (open) => {
  if (open) warehouseNameKeyword.value = ''
})
</script>

<template>
  <div v-if="open" class="attachment-preview-mask">
    <section class="warehouse-location-dialog department-warehouse-dialog" role="dialog" aria-modal="true">
      <header class="warehouse-location-header">
        <div>
          <p>科室管理</p>
          <h3>关联库房</h3>
          <span>{{ department?.name || '-' }} / {{ department?.code || '-' }}</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <div class="department-warehouse-body">
        <div class="warehouse-location-tools">
          <div class="department-warehouse-filter">
            <strong>可关联库房</strong>
            <label class="department-warehouse-search">
              <Search :size="16" aria-hidden="true" />
              <input
                v-model.trim="warehouseNameKeyword"
                type="search"
                placeholder="搜索库房名称"
                aria-label="搜索库房名称"
              />
            </label>
          </div>
          <div class="department-warehouse-actions">
            <button class="btn" type="button" @click="emit('refresh')">
              <RefreshCw :size="16" />
              刷新
            </button>
            <button class="btn btn-primary" type="button" :disabled="saving" @click="emit('save')">
              <Save :size="16" />
              {{ saving ? '保存中' : '保存关联' }}
            </button>
          </div>
        </div>

        <div class="warehouse-location-table-wrap">
          <table class="master-table department-warehouse-table">
            <thead>
              <tr>
                <th class="selection-cell">选择</th>
                <th>库房编码</th>
                <th>库房名称</th>
                <th>库房类型</th>
                <th>所属院区</th>
                <th>当前关联科室</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="approval-empty">正在加载...</td>
              </tr>
              <tr v-else-if="!filteredRows.length">
                <td colspan="7" class="approval-empty">暂无可关联库房</td>
              </tr>
              <tr v-for="row in filteredRows" v-else :key="row.code">
                <td class="selection-cell">
                  <input
                    type="checkbox"
                    :checked="selectedCodes.includes(row.code)"
                    @change="emit('toggle', row.code, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td>{{ row.code }}</td>
                <td>{{ row.name }}</td>
                <td>{{ row.type || '-' }}</td>
                <td>{{ row.campus || '-' }}</td>
                <td>{{ row.relatedDepartment || '-' }}</td>
                <td>
                  <span class="status-badge" :class="{ warning: row.status !== '启用' }">
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
