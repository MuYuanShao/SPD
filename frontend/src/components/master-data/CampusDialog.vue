<script setup lang="ts">
import { Landmark, UserRound, X } from '@lucide/vue'
import type { CampusPayload } from '../../api/masterData'

defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: CampusPayload
}>()

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section
      class="supplier-dialog campus-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="campus-dialog-title"
      aria-describedby="campus-dialog-description"
    >
      <header class="campus-dialog-header">
        <div>
          <p class="campus-dialog-kicker">
            <Landmark :size="15" aria-hidden="true" />
            组织基础档案
          </p>
          <h3 id="campus-dialog-title" class="campus-dialog-title">
            {{ mode === 'create' ? '新增院区' : '编辑院区' }}
          </h3>
          <p id="campus-dialog-description" class="campus-dialog-subtitle">
            带 <span class="required-mark">*</span> 的字段为必填项，院区将用于科室和库房归属。
          </p>
        </div>
        <button class="btn-icon campus-dialog-close" type="button" aria-label="关闭院区弹窗" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid campus-dialog-form" @submit.prevent="emit('save')">
        <section class="campus-form-section" aria-labelledby="campus-basic-heading">
          <div id="campus-basic-heading" class="campus-form-section-title">
            <Landmark :size="17" aria-hidden="true" />
            院区资料
          </div>
          <label>
            <span>院区编码 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.campusCode"
              name="campusCode"
              autocomplete="off"
              :readonly="mode === 'edit'"
              :autofocus="mode === 'create'"
              aria-required="true"
              required
            />
            <small v-if="mode === 'edit'" class="campus-form-hint">编码为稳定组织标识，编辑时不可修改</small>
          </label>
          <label>
            <span>院区名称 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.campusName"
              name="campusName"
              autocomplete="organization"
              :autofocus="mode === 'edit'"
              aria-required="true"
              required
            />
          </label>
          <label class="wide">
            <span>院区地址</span>
            <input v-model.trim="form.address" name="address" autocomplete="street-address" />
          </label>
        </section>

        <section class="campus-form-section" aria-labelledby="campus-operation-heading">
          <div id="campus-operation-heading" class="campus-form-section-title">
            <UserRound :size="17" aria-hidden="true" />
            管理信息
          </div>
          <label>
            <span>负责人</span>
            <input v-model.trim="form.managerName" name="managerName" autocomplete="name" />
          </label>
          <label>
            <span>联系电话</span>
            <input
              v-model.trim="form.phone"
              name="phone"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
            />
          </label>
          <label>
            <span>显示排序</span>
            <input v-model.number="form.sortOrder" name="sortOrder" type="number" min="0" inputmode="numeric" />
            <small class="campus-form-hint">数值越小，在院区选项中越靠前</small>
          </label>
          <label>
            <span>启停状态</span>
            <select v-model.number="form.status" name="status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
            <small class="campus-form-hint">停用后不再出现在新增科室和库房的可选项中</small>
          </label>
        </section>

        <div class="campus-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">
            {{ mode === 'create' ? '创建院区' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
