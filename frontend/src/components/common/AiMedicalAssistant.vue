<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3,
  Boxes,
  ChevronRight,
  Minus,
  ScanLine,
  Search,
  Send,
  Sparkles,
  Stethoscope,
  X
} from '@lucide/vue'
import {
  assistantQuickActions,
  assistantReplies,
  assistantReplyForPrompt,
  type AssistantActionKey,
  type AssistantReply
} from '../../config/aiMedicalAssistant'

interface ConversationMessage {
  id: number
  role: 'user' | 'assistant'
  text?: string
  time?: string
  reply?: AssistantReply
}

const router = useRouter()
const open = ref(false)
const prompt = ref('')
const messageList = ref<HTMLElement | null>(null)
let nextMessageId = 4

const actionIcons = {
  inventory: Search,
  consumption: BarChart3,
  replenishment: Boxes,
  udi: ScanLine
}
const quickActions = assistantQuickActions.map(action => ({ ...action, icon: actionIcons[action.key] }))

const messages = ref<ConversationMessage[]>([
  {
    id: 1,
    role: 'user',
    text: '骨科有哪些库存不足的重点监控耗材？',
    time: '16:16'
  },
  {
    id: 2,
    role: 'assistant',
    reply: assistantReplies.inventory
  }
])

function currentTime() {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date())
}

function scrollToLatest() {
  void nextTick(() => {
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  })
}

function openAssistant() {
  open.value = true
  scrollToLatest()
}

function closeAssistant() {
  open.value = false
}

function submitPrompt(value = prompt.value) {
  const normalized = value.trim()
  if (!normalized) return
  messages.value.push({ id: nextMessageId++, role: 'user', text: normalized, time: currentTime() })
  messages.value.push({ id: nextMessageId++, role: 'assistant', reply: assistantReplyForPrompt(normalized) })
  prompt.value = ''
  scrollToLatest()
}

function runQuickAction(key: AssistantActionKey, label: string) {
  messages.value.push({ id: nextMessageId++, role: 'user', text: label, time: currentTime() })
  messages.value.push({ id: nextMessageId++, role: 'assistant', reply: assistantReplies[key] })
  scrollToLatest()
}

function navigateTo(route?: string) {
  if (!route) return
  closeAssistant()
  void router.push(route)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) closeAssistant()
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <button
      v-if="!open"
      class="ai-medical-fab"
      type="button"
      aria-label="打开AI医护助手"
      @click="openAssistant"
    >
      <span class="ai-medical-fab-icon">
        <Stethoscope :size="25" stroke-width="2.4" />
        <Sparkles :size="14" stroke-width="2.6" />
      </span>
      <span>AI医护助手</span>
    </button>

    <Transition name="ai-medical-drawer">
      <aside v-if="open" class="ai-medical-panel" role="dialog" aria-modal="false" aria-label="AI医护助手">
        <header class="ai-medical-header">
          <div>
            <strong>AI医护助手</strong>
            <span>院内SPD智能协作</span>
          </div>
          <div class="ai-medical-window-actions">
            <button type="button" aria-label="最小化AI医护助手" @click="closeAssistant">
              <Minus :size="19" />
            </button>
            <button type="button" aria-label="关闭AI医护助手" @click="closeAssistant">
              <X :size="19" />
            </button>
          </div>
        </header>

        <div ref="messageList" class="ai-medical-content" aria-live="polite">
          <section class="ai-medical-welcome">
            <span class="ai-medical-avatar"><Sparkles :size="20" fill="currentColor" /></span>
            <strong>您好，我是您的AI医护助手</strong>
          </section>

          <div class="ai-medical-quick-grid" aria-label="快捷问题">
            <button
              v-for="action in quickActions"
              :key="action.key"
              type="button"
              @click="runQuickAction(action.key, action.label)"
            >
              <component :is="action.icon" :size="22" stroke-width="2.2" />
              <span>{{ action.label }}</span>
            </button>
          </div>

          <article v-for="message in messages" :key="message.id" class="ai-medical-message" :class="message.role">
            <template v-if="message.role === 'user'">
              <div class="ai-medical-user-bubble">
                <span class="ai-medical-user-icon"><Stethoscope :size="18" /></span>
                <p>{{ message.text }}</p>
              </div>
              <time>{{ message.time }}</time>
            </template>

            <template v-else-if="message.reply">
              <div class="ai-medical-answer">
                <span class="ai-medical-avatar compact"><Sparkles :size="16" fill="currentColor" /></span>
                <div>
                  <p>{{ message.reply.title }}</p>
                  <ul>
                    <li v-for="line in message.reply.lines" :key="line">{{ line }}</li>
                  </ul>
                  <button
                    v-if="message.reply.actionLabel"
                    class="ai-medical-link"
                    type="button"
                    @click="navigateTo(message.reply.actionRoute)"
                  >
                    {{ message.reply.actionLabel }}
                    <ChevronRight :size="16" />
                  </button>
                </div>
              </div>
            </template>
          </article>
        </div>

        <footer class="ai-medical-composer">
          <div class="ai-medical-input-wrap">
            <textarea
              v-model="prompt"
              rows="2"
              placeholder="输入您的问题，支持耗材、库存、订单、UDI查询…"
              aria-label="向AI医护助手提问"
              @keydown.enter.exact.prevent="submitPrompt()"
            ></textarea>
            <button type="button" aria-label="发送问题" :disabled="!prompt.trim()" @click="submitPrompt()">
              <Send :size="19" fill="currentColor" />
            </button>
          </div>
          <small>内容仅供院内SPD业务协作使用</small>
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ai-medical-fab {
  position: fixed;
  right: 34px;
  bottom: 32px;
  z-index: 65;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: 999px;
  background: linear-gradient(105deg, #6f47e8 0%, #467df5 100%);
  box-shadow: 0 10px 30px rgba(64, 82, 209, 0.34), inset 0 0 0 1px rgba(255, 255, 255, 0.24);
  padding: 0 24px 0 18px;
  color: #ffffff;
  font: inherit;
  font-size: 17px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.ai-medical-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 34px rgba(64, 82, 209, 0.42), inset 0 0 0 1px rgba(255, 255, 255, 0.24);
}

.ai-medical-fab:focus-visible,
.ai-medical-panel button:focus-visible,
.ai-medical-panel textarea:focus-visible {
  outline: 3px solid rgba(78, 106, 244, 0.32);
  outline-offset: 2px;
}

.ai-medical-fab-icon {
  position: relative;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
}

.ai-medical-fab-icon svg:last-child {
  position: absolute;
  top: -4px;
  right: -6px;
}

.ai-medical-panel {
  position: fixed;
  top: 18px;
  right: 18px;
  bottom: 18px;
  z-index: 70;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(420px, calc(100vw - 28px));
  border: 1px solid #e3e7f2;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfbff 100%);
  box-shadow: 0 24px 70px rgba(18, 31, 75, 0.24);
  color: #17203a;
  overflow: hidden;
}

.ai-medical-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 86px;
  border-bottom: 1px solid #eceef6;
  padding: 18px 20px;
}

.ai-medical-header > div:first-child {
  display: grid;
  gap: 5px;
}

.ai-medical-header strong {
  color: #161f38;
  font-size: 21px;
  line-height: 1.1;
}

.ai-medical-header span {
  color: #7a8298;
  font-size: 13px;
}

.ai-medical-window-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-medical-window-actions button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #27314b;
  cursor: pointer;
}

.ai-medical-window-actions button:hover {
  background: #f1f3f9;
}

.ai-medical-content {
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 24px;
  scrollbar-color: #cbd3e7 transparent;
}

.ai-medical-welcome {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 18px;
}

.ai-medical-welcome strong {
  font-size: 15px;
}

.ai-medical-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f0edff, #f6f3ff);
  color: #7359ee;
}

.ai-medical-avatar.compact {
  width: 30px;
  height: 30px;
  border-radius: 10px;
}

.ai-medical-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

.ai-medical-quick-grid button {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  min-height: 58px;
  border: 1px solid #e2e6f2;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff, #fafbff);
  box-shadow: 0 3px 12px rgba(51, 65, 128, 0.04);
  padding: 10px 11px;
  color: #5c52dd;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.ai-medical-quick-grid button:hover {
  border-color: #ada6f4;
  background: #f7f5ff;
}

.ai-medical-quick-grid button span {
  min-width: 0;
  color: #5147cf;
  white-space: nowrap;
}

.ai-medical-message {
  margin-top: 16px;
}

.ai-medical-message.user {
  display: grid;
  justify-items: end;
}

.ai-medical-user-bubble {
  display: flex;
  align-items: center;
  gap: 9px;
  max-width: 100%;
  border-radius: 8px 8px 2px 8px;
  background: #f1efff;
  padding: 10px 12px;
  color: #31385a;
}

.ai-medical-user-bubble p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.ai-medical-user-icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 27px;
  height: 27px;
  border-radius: 999px;
  background: #ffffff;
  color: #5f65e8;
}

.ai-medical-message time {
  margin-top: 6px;
  color: #a1a7b6;
  font-size: 11px;
}

.ai-medical-answer {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
}

.ai-medical-answer p {
  margin: 1px 0 10px;
  color: #404760;
  font-size: 13px;
  line-height: 1.65;
}

.ai-medical-answer ul {
  margin: 0;
  border: 1px solid #edf0f6;
  border-radius: 8px;
  background: #ffffff;
  padding: 0;
  list-style: none;
  overflow: hidden;
}

.ai-medical-answer li {
  padding: 11px 12px;
  color: #454d66;
  font-size: 12px;
  line-height: 1.55;
}

.ai-medical-answer li + li {
  border-top: 1px solid #eef0f6;
}

.ai-medical-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 36px;
  margin-top: 12px;
  border: 1px solid #879df8;
  border-radius: 6px;
  background: #ffffff;
  padding: 0 18px;
  color: #4b6ff2;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.ai-medical-link:hover {
  background: #f6f8ff;
}

.ai-medical-composer {
  border-top: 1px solid #eceef5;
  background: #ffffff;
  padding: 14px 16px 12px;
}

.ai-medical-input-wrap {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  align-items: end;
  gap: 8px;
  border: 1px solid #dde2ed;
  border-radius: 8px;
  background: #ffffff;
  padding: 9px 9px 9px 12px;
  box-shadow: 0 2px 10px rgba(30, 45, 90, 0.04);
}

.ai-medical-input-wrap:focus-within {
  border-color: #8ea2fa;
}

.ai-medical-input-wrap textarea {
  min-height: 42px;
  max-height: 110px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 3px 0;
  color: #27314b;
  font: inherit;
  font-size: 12px;
  line-height: 1.5;
}

.ai-medical-input-wrap textarea::placeholder {
  color: #9ca4b6;
}

.ai-medical-input-wrap button {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 7px;
  background: #2579ef;
  color: #ffffff;
  cursor: pointer;
}

.ai-medical-input-wrap button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-medical-composer small {
  display: block;
  margin-top: 9px;
  color: #a0a7b6;
  font-size: 10px;
  text-align: center;
}

.ai-medical-drawer-enter-active,
.ai-medical-drawer-leave-active {
  transition: transform 0.24s ease, opacity 0.2s ease;
}

.ai-medical-drawer-enter-from,
.ai-medical-drawer-leave-to {
  transform: translateX(28px);
  opacity: 0;
}

@media (max-width: 620px) {
  .ai-medical-fab {
    right: 18px;
    bottom: 18px;
    min-height: 50px;
    padding: 0 18px 0 14px;
    font-size: 15px;
  }

  .ai-medical-panel {
    inset: 8px;
    width: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-medical-fab,
  .ai-medical-drawer-enter-active,
  .ai-medical-drawer-leave-active {
    transition: none;
  }
}
</style>
