<script setup lang="ts">
import { computed } from 'vue'
import { X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

const props = defineProps<{
  open: boolean
  templateQuery: {
    templateCode: string
    templateName: string
  }
  showAll: boolean
  templates: QuotaTemplateRow[]
}>()

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'update:showAll', value: boolean): void
  (event: 'select', row: QuotaTemplateRow): void
}>()

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

const includeDisabled = computed({
  get: () => props.showAll,
  set: (value) => emit('update:showAll', value)
})

function closeDialog() {
  isOpen.value = false
}
</script>

<template>
  <div v-if="isOpen" class="attachment-preview-mask" @click.self="closeDialog">
    <section class="supplier-dialog product-selector-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>打包任务确认</p>
          <h3>选择定数包模板</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="closeDialog">
          <X :size="18" />
        </button>
      </header>
      <div class="product-selector-body">
        <div class="product-selector-search">
          <input v-model="templateQuery.templateCode" placeholder="模板编码" />
          <input v-model="templateQuery.templateName" placeholder="模板名称" />
          <label class="show-all-toggle">
            <input v-model="includeDisabled" type="checkbox" />
            <span>显示已停用</span>
          </label>
        </div>
        <div class="table-scroll">
          <table class="master-table compact-table">
            <thead>
              <tr>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>商品名称</th>
                <th>规格</th>
                <th>厂家</th>
                <th>包内数量</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in templates"
                :key="row.templateId"
                class="clickable-row"
                @dblclick="emit('select', row)"
              >
                <td>{{ row.templateCode }}</td>
                <td><strong>{{ row.templateName }}</strong></td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.quantity }} {{ row.unit }}</td>
                <td>
                  <span :class="['status-badge', row.status === '禁用' || row.status === 'disabled' ? 'disabled' : 'enabled']">
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td>
                  <button class="btn-text" type="button" @click="emit('select', row)">选择</button>
                </td>
              </tr>
              <tr v-if="!templates.length">
                <td colspan="9" class="approval-empty">没有找到匹配的定数包模板</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
