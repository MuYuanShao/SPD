<script setup lang="ts">
import { Landmark, ReceiptText, UserRound, Users, X } from '@lucide/vue'
import type { CampusOption, DepartmentPayload } from '../../api/masterData'

defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: DepartmentPayload
  campusOptions: CampusOption[]
}>()

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section
      class="supplier-dialog department-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="department-dialog-title"
      aria-describedby="department-dialog-description"
    >
      <header class="department-dialog-header">
        <div>
          <p class="department-dialog-kicker">
            <Users :size="15" aria-hidden="true" />
            科室组织档案
          </p>
          <h3 id="department-dialog-title" class="department-dialog-title">
            {{ mode === 'create' ? '新增科室' : '编辑科室' }}
          </h3>
          <p id="department-dialog-description" class="department-dialog-subtitle">
            带 <span class="required-mark">*</span> 的字段为必填项，院区和财务映射会影响后续业务归属。
          </p>
        </div>
        <button
          class="btn-icon department-dialog-close"
          type="button"
          aria-label="关闭科室弹窗"
          @click="emit('close')"
        >
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid department-dialog-form" @submit.prevent="emit('save')">
        <section class="department-form-section" aria-labelledby="department-basic-heading">
          <div id="department-basic-heading" class="department-form-section-title">
            <Landmark :size="17" aria-hidden="true" />
            组织归属
          </div>
          <label>
            <span>科室编码 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.deptCode"
              name="deptCode"
              autocomplete="off"
              :readonly="mode === 'edit'"
              :autofocus="mode === 'create'"
              aria-required="true"
              required
            />
            <small v-if="mode === 'edit'" class="department-form-hint">编码为稳定组织标识，编辑时不可修改</small>
          </label>
          <label>
            <span>科室名称 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.deptName"
              name="deptName"
              autocomplete="organization"
              :autofocus="mode === 'edit'"
              aria-required="true"
              required
            />
          </label>
          <label>
            <span>所属院区</span>
            <select v-model.trim="form.campusName" name="campusName">
              <option value="">请选择院区</option>
              <option v-for="campus in campusOptions" :key="campus.code" :value="campus.name">
                {{ campus.name }}
              </option>
            </select>
            <small class="department-form-hint">院区选项来源于启用中的院区档案</small>
          </label>
          <label>
            <span>科室状态</span>
            <select v-model.number="form.status" name="status">
              <option :value="1">正常</option>
              <option :value="0">停用</option>
            </select>
          </label>
        </section>

        <section class="department-form-section" aria-labelledby="department-finance-heading">
          <div id="department-finance-heading" class="department-form-section-title">
            <ReceiptText :size="17" aria-hidden="true" />
            财务科室映射
          </div>
          <label>
            <span>财务科室编码</span>
            <input v-model.trim="form.financeDeptCode" name="financeDeptCode" autocomplete="off" />
          </label>
          <label>
            <span>财务科室名称</span>
            <input v-model.trim="form.financeDeptName" name="financeDeptName" autocomplete="off" />
          </label>
        </section>

        <section class="department-form-section" aria-labelledby="department-manager-heading">
          <div id="department-manager-heading" class="department-form-section-title">
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
            <small class="department-form-hint">数值越小，在科室选项中越靠前</small>
          </label>
          <label>
            <span>科室地址</span>
            <input v-model.trim="form.address" name="address" autocomplete="street-address" />
          </label>
        </section>

        <div class="department-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">
            {{ mode === 'create' ? '创建科室' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
