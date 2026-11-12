<script setup lang="ts">
/**
 * PersonalCenterDialog — 个人中心（修改密码）
 *
 * 挂在顶部导航栏的用户下拉菜单中，供当前登录用户自助修改初始密码。
 * 走 /auth/password 接口：需校验原密码，新密码与确认密码必须一致。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { KeyRound, ShieldCheck, UserRound } from '@lucide/vue'
import ModalMask from './ModalMask.vue'
import { changeOwnPassword } from '../../api/foundation'
import { useAuthStore } from '../../stores/auth'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const auth = useAuthStore()
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const accountName = computed(() => auth.currentUser?.realName || auth.username || '-')
const accountUsername = computed(() => auth.currentUser?.username || '-')
const accountDept = computed(() => auth.currentUser?.deptId ? `科室编号 ${auth.currentUser.deptId}` : '未分配科室')

/** 新密码强度提示：长度、大小写字母、数字、符号。 */
const passwordChecks = computed(() => [
  { label: '至少 8 位', passed: newPassword.value.length >= 8 },
  { label: '包含字母', passed: /[A-Za-z]/.test(newPassword.value) },
  { label: '包含数字', passed: /\d/.test(newPassword.value) },
])

const canSubmit = computed(() =>
  Boolean(oldPassword.value.trim())
  && newPassword.value.length >= 8
  && newPassword.value === confirmPassword.value
  && newPassword.value !== oldPassword.value
  && !submitting.value,
)

function resetForm() {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  errorMessage.value = ''
}

function close() {
  resetForm()
  emit('close')
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await changeOwnPassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    })
    ElMessage.success('密码修改成功，请牢记新密码')
    emit('saved')
    close()
  } catch (error: any) {
    errorMessage.value = error?.message || '密码修改失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

watch(() => props.open, (open) => {
  if (!open) resetForm()
})
</script>

<template>
  <ModalMask v-if="open" @click.self="close" @keydown.esc="close">
    <div class="pc-mask" @click.self="close">
      <div class="pc-dialog" role="dialog" aria-modal="true" aria-label="个人中心">
      <header class="pc-header">
        <div class="pc-header-title">
          <i class="pc-header-icon"><UserRound :size="16" /></i>
          <div>
            <h3>个人中心</h3>
            <p>维护当前登录账号信息与登录密码</p>
          </div>
        </div>
        <button class="pc-close" type="button" aria-label="关闭" @click="close">×</button>
      </header>

      <div class="pc-body">
        <section class="pc-profile">
          <div class="pc-avatar">{{ accountName.slice(0, 1) }}</div>
          <div class="pc-profile-fields">
            <div class="pc-profile-row">
              <span>真实姓名</span>
              <strong>{{ accountName }}</strong>
            </div>
            <div class="pc-profile-row">
              <span>登录账号</span>
              <strong>{{ accountUsername }}</strong>
            </div>
            <div class="pc-profile-row">
              <span>所属科室</span>
              <strong>{{ accountDept }}</strong>
            </div>
          </div>
        </section>

        <section class="pc-section">
          <h4><KeyRound :size="14" /> 修改登录密码</h4>
          <div class="pc-form">
            <label>
              <span>原密码</span>
              <input v-model="oldPassword" class="pc-input" type="password" autocomplete="current-password" placeholder="请输入当前使用的密码" />
            </label>
            <label>
              <span>新密码</span>
              <input v-model="newPassword" class="pc-input" type="password" autocomplete="new-password" placeholder="请输入新的登录密码" />
            </label>
            <label>
              <span>确认新密码</span>
              <input v-model="confirmPassword" class="pc-input" type="password" autocomplete="new-password" placeholder="请再次输入新密码" />
            </label>
          </div>

          <ul class="pc-checks">
            <li v-for="item in passwordChecks" :key="item.label" :class="{ 'is-passed': item.passed }">
              <ShieldCheck :size="12" /> {{ item.label }}
            </li>
          </ul>

          <p v-if="confirmPassword && newPassword !== confirmPassword" class="pc-error">两次输入的新密码不一致</p>
          <p v-if="errorMessage" class="pc-error">{{ errorMessage }}</p>
        </section>
      </div>

      <footer class="pc-footer">
        <button class="pc-btn" type="button" @click="close">取消</button>
        <button class="pc-btn is-primary" type="button" :disabled="!canSubmit" @click="submit">
          {{ submitting ? '提交中…' : '确认修改' }}
        </button>
      </footer>
      </div>
    </div>
  </ModalMask>
</template>

<style scoped>
.pc-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.pc-dialog {
  width: 100%;
  max-width: 460px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.22);
  animation: pc-in 0.2s ease;
}

@keyframes pc-in {
  from { opacity: 0; transform: translateY(-8px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.pc-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.pc-header-title { display: flex; align-items: center; gap: 10px; }
.pc-header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.pc-header h3 { margin: 0; font-size: 15px; font-weight: 700; color: #102033; }
.pc-header p  { margin: 2px 0 0; font-size: 12px; color: #64748b; }

.pc-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.pc-close:hover { background: #f1f5f9; color: #334155; }

.pc-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pc-profile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.pc-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border: 1px solid #93c5fd;
  color: #1d4ed8;
  font-size: 17px;
  font-weight: 700;
  flex-shrink: 0;
}

.pc-profile-fields { display: grid; gap: 4px; min-width: 0; flex: 1; }
.pc-profile-row { display: flex; align-items: baseline; gap: 8px; font-size: 13px; }
.pc-profile-row span { color: #64748b; min-width: 62px; }
.pc-profile-row strong { color: #102033; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pc-section { display: grid; gap: 10px; }
.pc-section h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.pc-form { display: grid; gap: 10px; }
.pc-form label { display: grid; gap: 5px; font-size: 12px; font-weight: 600; color: #4b5b66; }

.pc-input {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
}
.pc-input:focus { outline: none; border-color: #10b981; }

.pc-checks { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
.pc-checks li {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 11.5px;
}
.pc-checks li.is-passed { color: #047857; border-color: #6ee7b7; background: #ecfdf5; }

.pc-error { margin: 0; color: #b91c1c; font-size: 12px; font-weight: 500; }

.pc-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
}

.pc-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  height: 32px;
  padding: 0 16px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.pc-btn:hover:not(:disabled) { background: #f8fafc; border-color: #94a3b8; }
.pc-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.pc-btn.is-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857;
  color: #fff;
}
.pc-btn.is-primary:hover:not(:disabled) { background: linear-gradient(180deg, #059669 0%, #047857 100%); }
</style>
