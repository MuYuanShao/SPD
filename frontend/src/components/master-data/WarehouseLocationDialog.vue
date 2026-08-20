<script setup lang="ts">
import { Plus, RefreshCw, Save, Trash2, X } from '@lucide/vue'
import type { WarehouseLocation, WarehouseLocationPayload } from '../../api/masterData'

defineProps<{
  open: boolean
  warehouse: Record<string, unknown> | null
  rows: WarehouseLocation[]
  form: WarehouseLocationPayload
  editingId: number | null
  loading: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  close: []
  refresh: []
  create: []
  edit: [row: WarehouseLocation]
  save: []
  remove: [row: WarehouseLocation]
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask">
    <section class="warehouse-location-dialog" role="dialog" aria-modal="true">
      <header class="warehouse-location-header">
        <div>
          <p>库房 / 货位管理</p>
          <h3>维护货位</h3>
          <span>{{ warehouse?.name || '-' }} · {{ warehouse?.code || '-' }}</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <div class="warehouse-location-body">
        <form class="warehouse-location-form" @submit.prevent="emit('save')">
          <div class="section-title compact">
            <h3>{{ editingId == null ? '新增货位' : '编辑货位' }}</h3>
          </div>
          <label>
            <span>货位编码</span>
            <input v-model.trim="form.locationCode" placeholder="如：A-01-01-01" required />
          </label>
          <label>
            <span>货位类型</span>
            <input v-model.trim="form.locationType" placeholder="整件位、散货位、冷链位" required />
          </label>
          <label>
            <span>容量上限</span>
            <input v-model="form.capacityLimit" type="number" min="0" step="0.0001" placeholder="可选" />
          </label>
          <label>
            <span>固定商品编码</span>
            <input v-model.trim="form.productCode" placeholder="可选，填写商品编码" />
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <div class="warehouse-location-actions">
            <button class="btn" type="button" @click="emit('create')">
              <Plus :size="16" />
              新增
            </button>
            <button class="btn btn-primary" type="submit" :disabled="saving">
              <Save :size="16" />
              {{ saving ? '保存中' : '保存货位' }}
            </button>
          </div>
        </form>

        <div class="warehouse-location-table-panel">
          <div class="warehouse-location-tools">
            <strong>货位明细</strong>
            <button class="btn" type="button" @click="emit('refresh')">
              <RefreshCw :size="16" />
              刷新
            </button>
          </div>
          <div class="warehouse-location-table-wrap">
            <table class="master-table warehouse-location-table">
              <thead>
                <tr>
                  <th>货位编码</th>
                  <th>货位类型</th>
                  <th>容量上限</th>
                  <th>固定商品</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="7" class="approval-empty">正在加载...</td>
                </tr>
                <tr v-else-if="!rows.length">
                  <td colspan="7" class="approval-empty">暂无货位，请先新增</td>
                </tr>
                <template v-else>
                  <tr v-for="row in rows" :key="row.locationId">
                    <td>{{ row.locationCode }}</td>
                    <td>{{ row.locationType }}</td>
                    <td>{{ row.capacityLimit ?? '-' }}</td>
                    <td>{{ row.productCode ? `${row.productCode} ${row.productName || ''}` : '-' }}</td>
                    <td>
                      <span class="status-badge" :class="{ warning: row.status !== 1 }">
                        {{ row.statusLabel }}
                      </span>
                    </td>
                    <td>{{ row.updateTime || '-' }}</td>
                    <td>
                      <button type="button" class="btn-text" @click="emit('edit', row)">编辑</button>
                      <button type="button" class="btn-text btn-text-danger" @click="emit('remove', row)">
                        <Trash2 :size="14" />
                        删除
                      </button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
