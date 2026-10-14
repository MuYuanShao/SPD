<script setup lang="ts">
import { X } from '@lucide/vue'

const props = defineProps<{
  open: boolean
  title: string
  subtitle?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  /** 提交按钮 loading */
  loading?: boolean
  /** 提交按钮文字，默认 "保存" */
  saveText?: string
  /** 取消按钮文字，默认 "取消" */
  cancelText?: string
  /** 隐藏底部 */
  hideFooter?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: []
}>()

const sizeClass = {
  sm: '',
  md: '',
  lg: 'lg',
  xl: 'xl'
}
</script>

<template>
  <div v-if="open" class="md-dialog-mask" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section :class="['md-dialog', sizeClass[size || 'md']]" role="dialog" aria-modal="true">
      <header class="md-dialog-header">
        <div>
          <h3>{{ title }}</h3>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <button class="md-btn-icon" type="button" aria-label="关闭" @click="emit('close')">
          <X :size="18" />
        </button>
      </header>
      <div class="md-dialog-body">
        <slot />
      </div>
      <footer v-if="!hideFooter" class="md-dialog-footer">
        <slot name="footer">
          <button class="md-btn" type="button" :disabled="loading" @click="emit('close')">
            {{ cancelText || '取消' }}
          </button>
          <button class="md-btn md-btn-primary" type="button" :disabled="loading" @click="emit('save')">
            {{ saveText || '保存' }}
          </button>
        </slot>
      </footer>
    </section>
  </div>
</template>
