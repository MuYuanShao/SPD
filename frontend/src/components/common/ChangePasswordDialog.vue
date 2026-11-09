<script setup lang="ts">
import { reactive, ref } from 'vue'
import { putData } from '../../api/http'
import StatusMessage from './StatusMessage.vue'

const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ changed: [] }>()

const form = reactive({ currentPassword: '', newPassword: '', confirmation: '' })
const saving = ref(false)
const error = ref('')

function resetForm() {
  Object.assign(form, { currentPassword: '', newPassword: '', confirmation: '' })
  error.value = ''
}

async function save() {
  if (saving.value) return
  error.value = ''
  if (!form.currentPassword.trim()) {
    error.value = '请输入当前密码'
  } else if (!form.newPassword.trim() || [...form.newPassword].length < 8) {
    error.value = '新密码至少需要 8 个字符'
  } else if (new TextEncoder().encode(form.newPassword).length > 72) {
    error.value = '新密码不能超过 72 个 UTF-8 字节'
  } else if (form.newPassword === form.currentPassword) {
    error.value = '新密码不能与当前密码相同'
  } else if (form.newPassword !== form.confirmation) {
    error.value = '两次输入的新密码不一致'
  }
  if (error.value) return

  saving.value = true
  try {
    await putData<null>('/auth/password', {
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    resetForm()
    open.value = false
    emit('changed')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '密码修改失败，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="open"
    title="修改密码"
    width="min(440px, 92vw)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @open="resetForm"
    @closed="resetForm"
  >
    <form class="password-form" @submit.prevent="save">
      <StatusMessage :message="error" tone="error" role="alert" />
      <label>
        <span>当前密码</span>
        <el-input v-model="form.currentPassword" type="password" autocomplete="current-password" show-password :disabled="saving" />
      </label>
      <label>
        <span>新密码</span>
        <el-input v-model="form.newPassword" type="password" autocomplete="new-password" show-password :disabled="saving" aria-describedby="password-length-hint" />
      </label>
      <small id="password-length-hint">至少 8 个字符，最多 72 个 UTF-8 字节。</small>
      <label>
        <span>确认新密码</span>
        <el-input v-model="form.confirmation" type="password" autocomplete="new-password" show-password :disabled="saving" />
      </label>
      <p class="password-hint">保存后，请使用新密码重新登录。</p>
      <div class="password-actions">
        <el-button :disabled="saving" @click="open = false">取消</el-button>
        <el-button native-type="submit" type="primary" :loading="saving">保存修改</el-button>
      </div>
    </form>
  </el-dialog>
</template>

<style scoped>
.password-form {
  display: grid;
  gap: 12px;
}

.password-form label {
  display: grid;
  gap: 5px;
}

.password-form small,
.password-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.password-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}
</style>
