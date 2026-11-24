<script setup lang="ts">
import { Edit3, Save, X } from '@lucide/vue'

defineProps<{
  open: boolean
  saving?: boolean
  error?: string
  form: Record<string, unknown>
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'save'): void
}>()
</script>

<template>
  <div
    v-if="open"
    class="attachment-preview-mask"
    @click.self="!saving && $emit('close')"
    @keydown.esc="!saving && $emit('close')"
  >
    <section
      class="batch-product-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="batch-product-title"
      aria-describedby="batch-product-description"
    >
      <header class="batch-product-header">
        <div>
          <p class="batch-product-kicker"><Edit3 :size="15" /> 医院商品目录</p>
          <h3 id="batch-product-title">批量修改</h3>
          <span id="batch-product-description">一次修改多条商品属性，留空的字段表示不修改</span>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" :disabled="saving" @click="$emit('close')">
          <X :size="18" />
        </button>
      </header>

      <form class="batch-product-form" @submit.prevent="$emit('save')">
        <p v-if="error" class="standard-status-message is-error" role="alert">{{ error }}</p>

        <section class="batch-product-section" aria-labelledby="batch-product-fields-heading">
          <div class="batch-product-section-heading">
            <Edit3 :size="19" aria-hidden="true" />
            <div>
              <strong id="batch-product-fields-heading">待修改字段</strong>
              <span>只填写需要变更的字段，其余保持原值</span>
            </div>
          </div>

          <label>
            <span>是否带量</span>
            <select :disabled="saving" v-model="form.volumeBased">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
          <label>
            <span>是否国产</span>
            <select :disabled="saving" v-model="form.domestic">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
          <label>
            <span>单价</span>
            <input :disabled="saving" v-model="form.purchasePrice" type="number" min="0" step="0.0001" />
          </label>
          <label>
            <span>单位</span>
            <input :disabled="saving" v-model.trim="form.unit" />
          </label>
          <label>
            <span>注册证号</span>
            <input :disabled="saving" v-model.trim="form.registrationNo" />
          </label>
          <label>
            <span>合同编码</span>
            <input :disabled="saving" v-model.trim="form.contractCode" />
          </label>
          <label>
            <span>一级分类</span>
            <input :disabled="saving" v-model.trim="form.firstCategory" />
          </label>
          <label>
            <span>二级分类</span>
            <input :disabled="saving" v-model.trim="form.secondCategory" />
          </label>
          <label>
            <span>三级分类</span>
            <input :disabled="saving" v-model.trim="form.thirdCategory" />
          </label>
          <label>
            <span>是否收费</span>
            <select :disabled="saving" v-model="form.chargeable">
              <option value="">不修改</option>
              <option value="是">是</option>
              <option value="否">否</option>
            </select>
          </label>
        </section>

        <div class="batch-product-actions">
          <button class="btn btn-secondary" type="button" :disabled="saving" @click="$emit('close')">取消</button>
          <button class="btn btn-primary" type="submit" :disabled="saving">
            <Save :size="16" />
            {{ saving ? '正在保存...' : '保存' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================
   批量修改弹窗 —— 与「科室库房目录」弹窗保持同一套视觉标准
   ------------------------------------------------------------
   1. 头部：浅色渐变 + 主题色 kicker + 标题 + 说明
   2. 字段区：浅底卡片，标签 / 输入框字号全部走 --spd-font-size-* 令牌
   3. 按钮：与标准列表页一致的主色实心按钮
   ============================================================ */
.batch-product-dialog {
  width: min(1040px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  overflow: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
  border: 1px solid #dbe8ee;
  border-radius: 10px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
}

/* ===== 头部 ===== */
.batch-product-header {
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid #e1eaee;
  background: linear-gradient(180deg, #f8fcfc 0%, #fff 100%);
}

.batch-product-header > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.batch-product-header h3 {
  margin: 0;
  color: #102033;
  font-size: var(--spd-font-size-2xl);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.batch-product-header span {
  color: #687e8a;
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-tight);
}

/* kicker：主题色小胶囊标签 */
.batch-product-kicker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 7px;
  color: #0f7580;
  font-size: var(--spd-font-size-sm);
  font-weight: 800;
  letter-spacing: 0.06em;
}

/* 关闭按钮 */
.batch-product-header .btn-icon {
  flex: none;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe8ee;
  border-radius: 6px;
  background: #fff;
  color: #5b6b7a;
  cursor: pointer;
  transition: all 160ms ease;
}

.batch-product-header .btn-icon:hover:not(:disabled) {
  border-color: #16838d;
  color: #16838d;
  background: #effaf8;
}

/* ===== 表单 ===== */
.batch-product-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 12px;
  padding: 14px 18px 16px;
  overflow-y: auto;
}

/* ===== 字段区：浅底卡片 ===== */
.batch-product-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
  padding: 12px;
  border: 1px solid #deeaee;
  border-radius: 8px;
  background: #fbfdfd;
}

.batch-product-section-heading {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed #e1eaee;
}

.batch-product-section-heading svg {
  flex: none;
  color: #16838d;
}

.batch-product-section-heading strong {
  color: #173440;
  font-size: var(--spd-font-size-sm);
  font-weight: 700;
}

.batch-product-section-heading span {
  color: #687e8a;
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
}

/* 标签：字号走令牌，随大/中/小三档同步缩放 */
.batch-product-section label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.batch-product-section label > span {
  color: #5b6b7a;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.batch-product-section input,
.batch-product-section select {
  min-width: 0;
  max-width: 100%;
  height: 38px;
  padding: 0 10px;
  border: 1px solid #d7e5ec;
  border-radius: 7px;
  background: #fff;
  color: #102033;
  font-family: inherit;
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
}

.batch-product-section input:focus,
.batch-product-section select:focus {
  outline: none;
  border-color: var(--spd-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--spd-primary) 14%, transparent);
}

.batch-product-section input:disabled,
.batch-product-section select:disabled {
  background: #f4f7f8;
  color: #90a4ae;
  cursor: not-allowed;
}

/* ===== 底部按钮 ===== */
.batch-product-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 4px;
}

@media (max-width: 1100px) {
  .batch-product-section {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .batch-product-section {
    grid-template-columns: 1fr;
  }
}
</style>
