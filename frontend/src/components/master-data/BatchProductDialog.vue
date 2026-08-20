<script setup lang="ts">
import { X } from '@lucide/vue'

defineProps<{
  open: boolean
  form: Record<string, unknown>
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'save'): void
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="$emit('close')">
    <section class="supplier-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>医院目录</p>
          <h3>批量修改</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid" @submit.prevent="$emit('save')">
        <label>
          <span>是否带量</span>
          <select v-model="form.volumeBased">
            <option value="">不修改</option>
            <option value="是">是</option>
            <option value="否">否</option>
          </select>
        </label>
        <label>
          <span>是否国产</span>
          <select v-model="form.domestic">
            <option value="">不修改</option>
            <option value="是">是</option>
            <option value="否">否</option>
          </select>
        </label>
        <label><span>单价</span><input v-model="form.purchasePrice" type="number" min="0" step="0.0001" /></label>
        <label><span>单位</span><input v-model.trim="form.unit" /></label>
        <label><span>注册证号</span><input v-model.trim="form.registrationNo" /></label>
        <label><span>合同编码</span><input v-model.trim="form.contractCode" /></label>
        <label><span>一级分类</span><input v-model.trim="form.firstCategory" /></label>
        <label><span>二级分类</span><input v-model.trim="form.secondCategory" /></label>
        <label><span>三级分类</span><input v-model.trim="form.thirdCategory" /></label>
        <label>
          <span>是否收费</span>
          <select v-model="form.chargeable">
            <option value="">不修改</option>
            <option value="是">是</option>
            <option value="否">否</option>
          </select>
        </label>
        <div class="dialog-actions">
          <button class="btn" type="button" @click="$emit('close')">取消</button>
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>
    </section>
  </div>
</template>
