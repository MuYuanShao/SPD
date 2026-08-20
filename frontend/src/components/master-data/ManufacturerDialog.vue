<script setup lang="ts">
import { Factory, FileCheck2, UserRound, X } from '@lucide/vue'

type ManufacturerForm = {
  manufacturerCode: string
  manufacturerName: string
  creditCode: string
  licenseNo: string
  contactName: string
  contactPhone: string
  address: string
}

defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: ManufacturerForm
}>()

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section
      class="supplier-dialog manufacturer-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="manufacturer-dialog-title"
      aria-describedby="manufacturer-dialog-description"
    >
      <header class="manufacturer-dialog-header">
        <div>
          <p class="manufacturer-dialog-kicker">
            <Factory :size="15" aria-hidden="true" />
            生产厂家档案
          </p>
          <h3 id="manufacturer-dialog-title" class="manufacturer-dialog-title">
            {{ mode === 'create' ? '新增厂家' : '修改厂家' }}
          </h3>
          <p id="manufacturer-dialog-description" class="manufacturer-dialog-subtitle">
            带 <span class="required-mark">*</span> 的字段为必填项，许可证与联系资料可按实际档案补充。
          </p>
        </div>
        <button
          class="btn-icon manufacturer-dialog-close"
          type="button"
          aria-label="关闭厂家弹窗"
          @click="emit('close')"
        >
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid manufacturer-dialog-form" @submit.prevent="emit('save')">
        <section class="manufacturer-form-section" aria-labelledby="manufacturer-basic-heading">
          <div id="manufacturer-basic-heading" class="manufacturer-form-section-title">
            <Factory :size="17" aria-hidden="true" />
            基本资料
          </div>
          <label>
            <span>厂家编码</span>
            <input
              v-model.trim="form.manufacturerCode"
              name="manufacturerCode"
              autocomplete="off"
              readonly
              :placeholder="mode === 'create' ? '保存后系统自动生成' : undefined"
              aria-readonly="true"
            />
            <small class="manufacturer-form-hint">
              {{ mode === 'create' ? '系统按 MFR + 日期 + 流水号自动生成' : '编码为稳定业务标识，不可修改' }}
            </small>
          </label>
          <label>
            <span>厂家名称 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.manufacturerName"
              name="manufacturerName"
              autocomplete="organization"
              autofocus
              aria-required="true"
              required
            />
          </label>
          <label class="wide">
            <span>统一社会信用代码</span>
            <input v-model.trim="form.creditCode" name="creditCode" autocomplete="off" />
          </label>
        </section>

        <section class="manufacturer-form-section" aria-labelledby="manufacturer-license-heading">
          <div id="manufacturer-license-heading" class="manufacturer-form-section-title">
            <FileCheck2 :size="17" aria-hidden="true" />
            生产资质
          </div>
          <label class="wide">
            <span>生产许可证号</span>
            <input
              v-model.trim="form.licenseNo"
              name="licenseNo"
              autocomplete="off"
              placeholder="填写医疗器械生产许可证号"
            />
            <small class="manufacturer-form-hint">如该厂家无需生产许可，可暂不填写</small>
          </label>
        </section>

        <section class="manufacturer-form-section" aria-labelledby="manufacturer-contact-heading">
          <div id="manufacturer-contact-heading" class="manufacturer-form-section-title">
            <UserRound :size="17" aria-hidden="true" />
            联系信息
          </div>
          <label>
            <span>联系人</span>
            <input v-model.trim="form.contactName" name="contactName" autocomplete="name" />
          </label>
          <label>
            <span>联系电话</span>
            <input
              v-model.trim="form.contactPhone"
              name="contactPhone"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
            />
          </label>
          <label class="wide">
            <span>联系地址</span>
            <input v-model.trim="form.address" name="address" autocomplete="street-address" />
          </label>
        </section>

        <div class="manufacturer-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">
            {{ mode === 'create' ? '创建厂家' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
