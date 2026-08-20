<script setup lang="ts">
import { ref, watch } from 'vue'
import { FileText, ShieldCheck, UserRound, X } from '@lucide/vue'
import { fetchFieldOptions, type FieldOption } from '../../api/fieldOptions'

type SupplierForm = {
  supplierCode: string
  supplierName: string
  creditCode: string
  supplierType: string
  grade: string
  contactName: string
  contactPhone: string
  email: string
  address: string
}

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  form: SupplierForm
}>()

const emit = defineEmits<{
  close: []
  save: []
}>()

const supplierTypeOptions = ref<FieldOption[]>([])
const supplierGradeOptions = ref<FieldOption[]>([])

function activeOptions(options: FieldOption[], currentValue: string) {
  const active = options.filter((item) => item.status === 1)
  if (currentValue && !active.some((item) => item.optionValue === currentValue)) {
    const retained = options.find((item) => item.optionValue === currentValue)
    if (retained) {
      active.push(retained)
    }
  }
  return active
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    try {
      const [types, grades] = await Promise.all([
        fetchFieldOptions('supplier_type'),
        fetchFieldOptions('supplier_grade'),
      ])
      supplierTypeOptions.value = types
      supplierGradeOptions.value = grades
    } catch (err) {
      console.error('供应商下拉选项加载失败', err)
      supplierTypeOptions.value = []
      supplierGradeOptions.value = []
    }
  },
)

function clearCreditCodeValidity(event: Event) {
  const input = event.target as HTMLInputElement
  input.setCustomValidity('')
}

function showCreditCodeValidationMessage(event: Event) {
  const input = event.target as HTMLInputElement
  input.setCustomValidity(
    input.value.trim() ? '统一社会信用代码必须为18位标准代码（大写字母或数字）' : '请输入统一社会信用代码',
  )
}
</script>

<template>
  <div v-if="open" class="attachment-preview-mask" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section
      class="supplier-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="supplier-dialog-title"
      aria-describedby="supplier-dialog-description"
    >
      <header class="supplier-dialog-header">
        <div>
          <p class="supplier-dialog-kicker">
            <ShieldCheck :size="15" aria-hidden="true" />
            供应商档案
          </p>
          <h3 id="supplier-dialog-title" class="supplier-dialog-title">
            {{ mode === 'create' ? '新增供应商' : '修改供应商' }}
          </h3>
          <p id="supplier-dialog-description" class="supplier-dialog-subtitle">
            带 <span class="required-mark">*</span> 的字段为必填项，保存后将用于采购、收货与结算业务。
          </p>
        </div>
        <button class="btn-icon supplier-dialog-close" type="button" aria-label="关闭供应商弹窗" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>
      <form class="supplier-form-grid supplier-dialog-form" @submit.prevent="emit('save')">
        <section class="supplier-form-section" aria-labelledby="supplier-basic-heading">
          <div id="supplier-basic-heading" class="supplier-form-section-title">
            <FileText :size="17" aria-hidden="true" />
            基本资料
          </div>
          <label>
            <span>供应商编码</span>
            <input
              v-model.trim="form.supplierCode"
              name="supplierCode"
              autocomplete="off"
              readonly
              :placeholder="mode === 'create' ? '保存后系统自动生成' : undefined"
              aria-readonly="true"
            />
            <small class="supplier-form-hint">
              {{ mode === 'create' ? '系统按 SUP + 日期 + 流水号自动生成' : '编码为稳定业务标识，不可修改' }}
            </small>
          </label>
          <label>
            <span>供应商名称 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.supplierName"
              name="supplierName"
              autocomplete="organization"
              autofocus
              aria-required="true"
              required
            />
          </label>
          <label class="wide">
            <span>统一社会信用代码 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.creditCode"
              name="creditCode"
              autocomplete="off"
              minlength="18"
              maxlength="18"
              pattern="[0-9A-HJ-NPQRTUWXY]{18}"
              placeholder="请输入18位统一社会信用代码"
              title="请输入18位标准代码，仅使用大写字母或数字"
              aria-required="true"
              required
              @input="clearCreditCodeValidity"
              @invalid="showCreditCodeValidationMessage"
            />
            <small class="supplier-form-hint">18位标准代码，仅支持大写字母和数字</small>
          </label>
          <label>
            <span>供应商类型 <b class="required-mark" aria-hidden="true">*</b></span>
            <select v-model="form.supplierType" name="supplierType" aria-required="true" required>
              <option value="" disabled>请选择供应商类型</option>
              <option
                v-for="item in activeOptions(supplierTypeOptions, form.supplierType)"
                :key="item.optionId"
                :value="item.optionValue"
                :disabled="item.status !== 1"
              >
                {{ item.optionLabel }}{{ item.status === 1 ? '' : ' · 已停用' }}
              </option>
            </select>
          </label>
          <label>
            <span>供应商等级</span>
            <select v-model="form.grade" name="grade">
              <option
                v-for="item in activeOptions(supplierGradeOptions, form.grade)"
                :key="item.optionId"
                :value="item.optionValue"
                :disabled="item.status !== 1"
              >
                {{ item.optionLabel }}{{ item.status === 1 ? '' : ' · 已停用' }}
              </option>
            </select>
          </label>
        </section>

        <section class="supplier-form-section" aria-labelledby="supplier-contact-heading">
          <div id="supplier-contact-heading" class="supplier-form-section-title">
            <UserRound :size="17" aria-hidden="true" />
            联系信息
          </div>
          <label>
            <span>联系人 <b class="required-mark" aria-hidden="true">*</b></span>
            <input v-model.trim="form.contactName" name="contactName" autocomplete="name" aria-required="true" required />
          </label>
          <label>
            <span>联系电话 <b class="required-mark" aria-hidden="true">*</b></span>
            <input
              v-model.trim="form.contactPhone"
              name="contactPhone"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
              aria-required="true"
              required
            />
          </label>
          <label class="wide">
            <span>邮箱</span>
            <input v-model.trim="form.email" name="email" type="email" inputmode="email" autocomplete="email" />
          </label>
          <label class="wide">
            <span>联系地址</span>
            <input v-model.trim="form.address" name="address" autocomplete="street-address" />
          </label>
        </section>

        <div class="supplier-dialog-actions">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">
            {{ mode === 'create' ? '创建供应商' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
