<script setup lang="ts">
import { Boxes, Building2, Save, X } from '@lucide/vue'
import type {
  DepartmentWarehouseCatalogPayload,
  DepartmentWarehouseRelation
} from '../../api/masterData'
import type { ClosureOptions } from '../../api/operationalClosure'

defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: DepartmentWarehouseCatalogPayload
  options: ClosureOptions
  warehouses: DepartmentWarehouseRelation[]
}>()

const emit = defineEmits<{
  close: []
  save: []
  changeDept: [deptName: string]
  changeWarehouse: [warehouseName: string]
}>()
</script>

<template>
  <div
    v-if="open"
    class="attachment-preview-mask"
    @click.self="emit('close')"
    @keydown.esc="emit('close')"
  >
    <section
      class="catalog-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="catalog-dialog-title"
      aria-describedby="catalog-dialog-description"
    >
      <header class="master-data-dialog-header catalog-dialog-header">
        <div>
          <p class="catalog-dialog-kicker"><Boxes :size="15" /> 科室库房目录</p>
          <h3 id="catalog-dialog-title">{{ mode === 'create' ? '新增可申领商品' : '编辑目录关系' }}</h3>
          <span id="catalog-dialog-description">设置科室在指定关联库房下可以申领的商品</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>

      <form class="catalog-dialog-form" @submit.prevent="emit('save')">
        <section class="catalog-dialog-section" aria-labelledby="catalog-scope-heading">
          <div class="catalog-dialog-section-heading">
            <Building2 :size="19" aria-hidden="true" />
            <div>
              <strong id="catalog-scope-heading">申领范围</strong>
              <span>先选择科室，再选择该科室已经关联的库房</span>
            </div>
          </div>
          <label>
            <span>科室 *</span>
            <select
              v-model="form.deptName"
              required
              autofocus
              @change="emit('changeDept', form.deptName)"
            >
              <option value="">请选择科室</option>
              <option v-for="dept in options.departments" :key="dept.deptCode" :value="dept.deptName">
                {{ dept.deptName }}
              </option>
            </select>
          </label>
          <label>
            <span>关联库房 *</span>
            <select
              v-model="form.warehouseName"
              required
              :disabled="!form.deptName"
              @change="emit('changeWarehouse', form.warehouseName)"
            >
              <option value="">请选择关联库房</option>
              <option v-for="warehouse in warehouses" :key="warehouse.code" :value="warehouse.name">
                {{ warehouse.name }}
              </option>
            </select>
            <small class="catalog-field-hint">商品范围按当前科室已关联库房的绑定目录过滤</small>
          </label>
        </section>

        <section class="catalog-dialog-section" aria-labelledby="catalog-product-heading">
          <div class="catalog-dialog-section-heading">
            <Boxes :size="19" aria-hidden="true" />
            <div>
              <strong id="catalog-product-heading">目录商品</strong>
              <span>目录启用后，科室申领只能读取已配置商品</span>
            </div>
          </div>
          <label>
            <span>商品 *</span>
            <select v-model="form.productCode" required>
              <option value="">请选择商品</option>
              <option v-for="product in options.products" :key="product.productCode" :value="product.productCode">
                {{ product.productCode }} - {{ product.productName }}
              </option>
            </select>
          </label>
          <label>
            <span>目录状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
        </section>

        <div class="master-data-dialog-actions catalog-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button class="btn btn-primary" type="submit">
            <Save :size="16" />
            {{ mode === 'create' ? '创建目录' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
