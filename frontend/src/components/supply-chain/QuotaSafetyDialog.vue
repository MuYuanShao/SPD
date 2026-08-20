<script setup lang="ts">
import { Save, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import PaginationControls from '../common/PaginationControls.vue'
import type { QuotaTemplateRow } from '../../api/quotaPackages'

defineProps<{
  mode: 'create' | 'edit'
  safetyForm: {
    deptName: string
    templateCode: string
    productCode: string
    minQty: number
    maxQty: number
  }
  catalogQuery: {
    templateCode: string
    templateName: string
    deptName: string
    productName: string
  }
  templates: QuotaTemplateRow[]
  filteredCount: number
  page: number
  size: number
  loading: boolean
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'submit'): void
  (event: 'select-template', row: QuotaTemplateRow): void
  (event: 'change-page', page: number): void
  (event: 'change-size', size: number): void
}>()
</script>

<template>
  <div class="attachment-preview-mask" @click.self="$emit('close')">
    <section class="supplier-dialog product-selector-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>定数安全量</p>
          <h3>{{ mode === 'create' ? '新增科室安全量' : '编辑科室安全量' }}</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>
      <div class="product-selector-body">
        <div class="supplier-form-grid">
          <label>
            <span>科室名称</span>
            <input v-model="safetyForm.deptName" placeholder="如：骨科" />
          </label>
          <label>
            <span>模板编码</span>
            <input v-model="safetyForm.templateCode" readonly placeholder="从下方目录选择" />
          </label>
          <label>
            <span>商品编码</span>
            <input v-model="safetyForm.productCode" readonly placeholder="从下方目录选择" />
          </label>
          <label>
            <span>安全下限</span>
            <input v-model.number="safetyForm.minQty" type="number" min="0" />
          </label>
          <label>
            <span>安全上限</span>
            <input v-model.number="safetyForm.maxQty" type="number" min="0" />
          </label>
          <div class="approval-action-row wide">
            <button class="btn btn-primary" type="button" @click="$emit('submit')">
              <Save :size="18" />
              {{ mode === 'create' ? '保存安全量' : '保存修改' }}
            </button>
            <button class="btn" type="button" @click="$emit('close')">取消</button>
          </div>
        </div>
        <div class="product-selector-search">
          <input v-model="catalogQuery.templateCode" placeholder="模板编码" />
          <input v-model="catalogQuery.templateName" placeholder="模板名称" />
          <input v-model="catalogQuery.deptName" placeholder="科室" />
          <input v-model="catalogQuery.productName" placeholder="商品名称" />
        </div>
        <div class="table-scroll">
          <table class="master-table compact-table">
            <thead>
              <tr>
                <th>模板编码</th>
                <th>模板名称</th>
                <th>科室</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格</th>
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
                @dblclick="$emit('select-template', row)"
              >
                <td>{{ row.templateCode }}</td>
                <td><strong>{{ row.templateName }}</strong></td>
                <td>{{ row.deptName }}</td>
                <td>{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel }}</td>
                <td>{{ row.quantity }} {{ row.unit }}</td>
                <td>
                  <span :class="['status-badge', row.status === '禁用' || row.status === 'disabled' ? 'disabled' : 'enabled']">
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td>
                  <button class="btn-text" type="button" @click="$emit('select-template', row)">选择</button>
                </td>
              </tr>
              <tr v-if="!filteredCount">
                <td colspan="9" class="approval-empty">没有找到匹配的定数包目录</td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="page"
          :size="size"
          :total="filteredCount"
          :loading="loading"
          :page-size-options="[5, 10, 20, 50]"
          @change-page="$emit('change-page', $event)"
          @change-size="$emit('change-size', $event)"
        />
      </div>
    </section>
  </div>
</template>
