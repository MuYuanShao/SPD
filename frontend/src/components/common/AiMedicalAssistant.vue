<script setup lang="ts">
import { chatWithAi } from '../../api/aiAssistant'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3,
  Boxes,
  ChevronRight,
  GripVertical,
  Image,
  Minus,
  Pause,
  ScanLine,
  Search,
  Send,
  Sparkles,
  Square,
  Stethoscope,
  X
} from '@lucide/vue'
import {
  assistantQuickActions,
  assistantReplyForPrompt,
  type AssistantActionKey,
  type AssistantReply
} from '../../config/aiMedicalAssistant'

interface ConversationMessage {
  id: number
  role: 'user' | 'assistant'
  text?: string
  time?: string
  image?: string
  failed?: boolean
  reply?: AssistantReply
}

interface DragState {
  pointerId: number
  startClientX: number
  startClientY: number
  startLeft: number
  startTop: number
  rectWidth: number
  rectHeight: number
  target: HTMLElement
  kind: 'fab' | 'panel'
  // Whether the pointer has moved past the drag threshold.
  started: boolean
  // Whether the synthetic click event should be suppressed (true after a real drag).
  suppressClick: boolean
  // Optional callback fired once the user crosses the drag threshold.
  onActivate?: () => void
  // Optional callback fired after a drag finishes (whether dragged or not).
  onAfter?: (state: { dragged: boolean }) => void
}

const PANEL_WIDTH = 420
const PANEL_GAP = 18
// Top app-navbar is sticky with z-index: 100 and 56px tall. The assistant panel
// must stay clear of it so its header isn't covered, and the drag clamp must
// never let the panel slide above this offset. If the layout changes the
// topnav height, update this constant to match.
const TOPNAV_HEIGHT = 56
const TOPNAV_OFFSET = TOPNAV_HEIGHT + PANEL_GAP
const STORAGE_KEY = 'spd-ai-assistant-position'

const router = useRouter()
const open = ref(false)
const prompt = ref('')
const messageList = ref<HTMLElement | null>(null)
let nextMessageId = 4

const fabPosition = ref<{ left: number; top: number } | null>(null)
const panelPosition = ref<{ left: number; top: number; width: number; height: number } | null>(null)
const activeDrag = ref<DragState | null>(null)

const fabStyle = computed(() => {
  const pos = fabPosition.value
  if (!pos) return undefined
  return {
    left: `${pos.left}px`,
    top: `${pos.top}px`,
    right: 'auto',
    bottom: 'auto'
  } as Record<string, string>
})

const panelStyle = computed(() => {
  const pos = panelPosition.value
  if (!pos) return undefined
  return {
    left: `${pos.left}px`,
    top: `${pos.top}px`,
    width: `${pos.width}px`,
    height: `${pos.height}px`,
    right: 'auto',
    bottom: 'auto'
  } as Record<string, string>
})

function loadStoredPosition(): void {
  if (typeof window === 'undefined') return
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw) as {
      fab?: { left: number; top: number }
      panel?: { left: number; top: number; width: number; height: number }
    }
    if (parsed.fab && Number.isFinite(parsed.fab.left) && Number.isFinite(parsed.fab.top)) {
      fabPosition.value = parsed.fab
    }
    if (
      parsed.panel &&
      Number.isFinite(parsed.panel.left) &&
      Number.isFinite(parsed.panel.top) &&
      Number.isFinite(parsed.panel.width) &&
      Number.isFinite(parsed.panel.height)
    ) {
      panelPosition.value = parsed.panel
    }
  } catch {
    // ignore corrupted storage
  }
}

function persistPosition(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ fab: fabPosition.value, panel: panelPosition.value })
    )
  } catch {
    // ignore quota errors
  }
}

function getViewportBounds() {
  if (typeof window === 'undefined') return { width: 1024, height: 768 }
  return { width: window.innerWidth, height: window.innerHeight }
}

function getElementRect(target: HTMLElement) {
  const rect = target.getBoundingClientRect()
  return { left: rect.left, top: rect.top, width: rect.width, height: rect.height }
}

function ensureFabPosition(): { left: number; top: number } {
  if (fabPosition.value) {
    const { width, height } = getElementRect(fabRef.value!)
    return clampPosition(fabPosition.value, width, height, 'fab')
  }
  if (fabRef.value) {
    const rect = getElementRect(fabRef.value)
    fabPosition.value = clampPosition({ left: rect.left, top: rect.top }, rect.width, rect.height, 'fab')
  } else {
    const { width, height } = getViewportBounds()
    fabPosition.value = clampPosition({ left: width - 220, top: height - 88 }, 200, 56, 'fab')
  }
  persistPosition()
  return fabPosition.value!
}

function ensurePanelPosition(): { left: number; top: number; width: number; height: number } {
  const { width: vw, height: vh } = getViewportBounds()
  const availableHeight = Math.max(360, vh - TOPNAV_OFFSET - PANEL_GAP)
  const width = Math.min(PANEL_WIDTH, vw - PANEL_GAP * 2)
  const height = availableHeight
  if (panelPosition.value) {
    panelPosition.value = clampPosition(panelPosition.value, width, height, 'panel')
  } else {
    panelPosition.value = clampPosition(
      { left: vw - width - PANEL_GAP, top: TOPNAV_OFFSET, width, height },
      width,
      height,
      'panel'
    )
  }
  persistPosition()
  return panelPosition.value!
}

function clampPosition<T extends { left: number; top: number; width?: number; height?: number }>(
  pos: T,
  width: number,
  height: number,
  kind: 'fab' | 'panel'
): T {
  const { width: vw, height: vh } = getViewportBounds()
  const w = kind === 'panel' ? width : Math.min(width, vw - PANEL_GAP * 2)
  const h = kind === 'panel' ? height : Math.min(height, vh - TOPNAV_OFFSET - PANEL_GAP)
  const maxLeft = Math.max(PANEL_GAP, vw - w - PANEL_GAP)
  const maxTop = Math.max(TOPNAV_OFFSET, vh - h - PANEL_GAP)
  const minTop = kind === 'panel' ? TOPNAV_OFFSET : PANEL_GAP
  const left = Math.min(Math.max(pos.left, PANEL_GAP), maxLeft)
  const top = Math.min(Math.max(pos.top, minTop), maxTop)
  return { ...pos, left, top, width: w, height: h } as T
}

const fabRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const DRAG_THRESHOLD_PX = 4

function beginDrag(
  event: PointerEvent,
  kind: 'fab' | 'panel',
  options: { onAfter?: DragState['onAfter'] } = {}
) {
  if (event.button !== undefined && event.button !== 0) return
  const target = kind === 'fab' ? fabRef.value : panelRef.value
  if (!target) return

  const rect = getElementRect(target)
  const state: DragState = {
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    startLeft: rect.left,
    startTop: rect.top,
    rectWidth: rect.width,
    rectHeight: rect.height,
    target,
    kind,
    started: false,
    suppressClick: false,
    onAfter: options.onAfter
  }
  activeDrag.value = state

  // Try to capture the pointer on the target so we keep getting pointermove
  // even if the cursor briefly leaves the element (e.g. over a child button).
  try {
    target.setPointerCapture(event.pointerId)
  } catch {
    // setPointerCapture is not available for every element; fall back to window listeners below.
  }

  // Note: we intentionally do NOT call event.preventDefault() on pointerdown.
  // That would prevent the button from being focusable / cancel touch scrolling
  // in odd ways. Instead, we suppress the synthetic click later on pointerup
  // (only when an actual drag happened) by calling upEvent.preventDefault().

  const handleMove = (moveEvent: PointerEvent) => {
    if (moveEvent.pointerId !== state.pointerId) return
    const dx = moveEvent.clientX - state.startClientX
    const dy = moveEvent.clientY - state.startClientY
    if (!state.started) {
      if (Math.abs(dx) <= DRAG_THRESHOLD_PX && Math.abs(dy) <= DRAG_THRESHOLD_PX) {
        return
      }
      state.started = true
      state.suppressClick = true
    }
    if (state.kind === 'fab') {
      fabPosition.value = clampPosition(
        { left: state.startLeft + dx, top: state.startTop + dy },
        state.rectWidth,
        state.rectHeight,
        'fab'
      )
    } else {
      panelPosition.value = clampPosition(
        {
          left: state.startLeft + dx,
          top: state.startTop + dy,
          width: state.rectWidth,
          height: state.rectHeight
        },
        state.rectWidth,
        state.rectHeight,
        'panel'
      )
    }
  }

  const handleUp = (upEvent: PointerEvent) => {
    if (upEvent.pointerId !== state.pointerId) return
    window.removeEventListener('pointermove', handleMove)
    window.removeEventListener('pointerup', handleUp)
    window.removeEventListener('pointercancel', handleUp)
    try {
      state.target.releasePointerCapture(state.pointerId)
    } catch {
      // ignore
    }
    if (activeDrag.value?.pointerId === state.pointerId) {
      activeDrag.value = null
    }
    if (state.suppressClick) {
      upEvent.preventDefault()
      upEvent.stopPropagation()
      persistPosition()
    }
    state.onAfter?.({ dragged: state.started })
  }

  window.addEventListener('pointermove', handleMove)
  window.addEventListener('pointerup', handleUp)
  window.addEventListener('pointercancel', handleUp)
}

function handleWindowResize() {
  if (fabPosition.value && fabRef.value) {
    const rect = getElementRect(fabRef.value)
    fabPosition.value = clampPosition(fabPosition.value, rect.width, rect.height, 'fab')
  }
  if (panelPosition.value && panelRef.value) {
    const rect = getElementRect(panelRef.value)
    panelPosition.value = clampPosition(panelPosition.value, rect.width, rect.height, 'panel')
  }
}

function onFabPointerDown(event: PointerEvent) {
  // If the user actually drags, beginDrag will suppress the synthetic click and
  // call onAfter({ dragged: true }). On a non-drag click, open the panel here.
  beginDrag(event, 'fab', {
    onAfter: ({ dragged }) => {
      if (!dragged) openAssistant()
    }
  })
}

const actionIcons = {
  inventory: Search,
  consumption: BarChart3,
  replenishment: Boxes,
  udi: ScanLine
}
const quickActions = assistantQuickActions.map(action => ({ ...action, icon: actionIcons[action.key] }))

const messages = ref<ConversationMessage[]>([])
const busy = ref(false)
const isGenerating = ref(false)
const selectedImage = ref('')
const imageError = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const promptRef = ref<HTMLTextAreaElement | null>(null)
let abortController: AbortController | null = null

// Mirrors the 豆包 / ChatGPT composer: the textarea auto-grows with the
// user's typing up to MAX_TEXTAREA_HEIGHT and shrinks back on delete.
const MAX_TEXTAREA_HEIGHT = 220

function autoResizeTextarea() {
  const el = promptRef.value
  if (!el) return
  // Reset to 0 so shrink works; then measure scrollHeight and clamp.
  el.style.height = '0px'
  const next = Math.min(el.scrollHeight, MAX_TEXTAREA_HEIGHT)
  el.style.height = `${next}px`
  el.style.overflowY = el.scrollHeight > MAX_TEXTAREA_HEIGHT ? 'auto' : 'hidden'
}

watch(prompt, () => autoResizeTextarea())

async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  imageError.value = ''
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
    imageError.value = '请选择不超过2MB的PNG、JPEG或WebP图片'
    return
  }
  try {
    selectedImage.value = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  } catch {
    imageError.value = '图片读取失败，请重新选择'
  }
}

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
  // Make sure the panel has a usable starting position before mount.
  void nextTick(() => {
    if (panelRef.value) {
      ensurePanelPosition()
      scrollToLatest()
    }
  })
}

function closeAssistant() {
  open.value = false
}

async function submitPrompt(value = prompt.value) {
  const normalized = value.trim() || (selectedImage.value ? '请识别这张图片中的耗材信息。' : '')
  if (!normalized || busy.value) return
  const image = selectedImage.value || undefined
  messages.value.push({ id: nextMessageId++, role: 'user', text: normalized, image, time: currentTime() })
  prompt.value = ''
  selectedImage.value = ''
  // Reset textarea to its initial rows after sending — autoResizeTextarea
  // will be triggered by the `watch(prompt, ...)` above.
  busy.value = true
  isGenerating.value = true
  scrollToLatest()
  abortController = new AbortController()
  try {
    const history = messages.value.filter(message => message.text && !message.failed).slice(-20)
    // Keep recent image context within the backend's three-image limit.
    let imageCount = 0
    const payload = history.slice().reverse().map(message => ({
      role: message.role,
      text: message.text!,
      image: message.image && ++imageCount <= 3 ? message.image : undefined
    })).reverse()
    const response = await chatWithAi(payload, { signal: abortController.signal })
    const guidance = assistantReplyForPrompt(normalized)
    messages.value.push({ id: nextMessageId++, role: 'assistant', text: response.text,
      reply: guidance.actionRoute ? { ...guidance, title: '', lines: [] } : undefined })
  } catch (error) {
    // Ignore AbortError — it means the user cancelled intentionally.
    if (error instanceof Error && (error.name === 'CanceledError' || error.name === 'AbortError')) return
    messages.value.push({ id: nextMessageId++, role: 'assistant', failed: true,
      text: error instanceof Error ? error.message : 'AI服务暂时不可用，请重试' })
    prompt.value = normalized
    selectedImage.value = image || ''
  } finally {
    busy.value = false
    isGenerating.value = false
    abortController = null
    scrollToLatest()
  }
}

function stopGeneration() {
  if (abortController) {
    abortController.abort()
  }
}

function runQuickAction(_key: AssistantActionKey, label: string) {
  void submitPrompt(label)
}

function navigateTo(route?: string) {
  if (!route) return
  closeAssistant()
  void router.push(route)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) closeAssistant()
}

watch(open, isOpen => {
  if (isOpen) {
    void nextTick(() => {
      if (panelRef.value) ensurePanelPosition()
      scrollToLatest()
    })
  }
})

onMounted(() => {
  loadStoredPosition()
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleWindowResize)
  // Make sure FAB and panel stay inside the viewport after layout settles.
  if (fabRef.value) ensureFabPosition()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleWindowResize)
  // Cancel any in-flight chat request so a pending server-side stream
  // does not survive panel unmount.
  abortController?.abort()
  // If a drag is still in progress when the component unmounts, drop the
  // reference. The pointermove/pointerup closures own their own window
  // listeners and will clean themselves up the next time the pointer fires.
  activeDrag.value = null
})
</script>

<template>
  <Teleport to="body">
    <button
      v-if="!open"
      ref="fabRef"
      class="ai-medical-fab is-draggable"
      type="button"
      :style="fabStyle"
      aria-label="打开AI医护助手（可拖拽移动）"
      @pointerdown="onFabPointerDown"
    >
      <span class="ai-medical-fab-icon">
        <Stethoscope :size="25" stroke-width="2.4" />
        <Sparkles :size="14" stroke-width="2.6" />
      </span>
      <span>AI医护助手</span>
    </button>

    <Transition name="ai-medical-drawer">
      <aside
        v-if="open"
        ref="panelRef"
        class="ai-medical-panel is-draggable"
        :style="panelStyle"
        role="dialog"
        aria-modal="false"
        aria-label="AI医护助手"
      >
        <header class="ai-medical-header">
          <button
            type="button"
            class="ai-medical-drag-handle"
            aria-label="拖拽移动AI医护助手窗口"
            @pointerdown="beginDrag($event, 'panel')"
          >
            <GripVertical :size="18" />
            <span class="ai-medical-drag-title">
              <strong>AI医护助手</strong>
              <span>院内SPD智能协作</span>
            </span>
          </button>
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
              :disabled="busy"
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
                <img v-if="message.image" :src="message.image" class="ai-image-preview" alt="上传的耗材图片" />
              </div>
              <time>{{ message.time }}</time>
            </template>

            <template v-else>
              <div class="ai-medical-answer">
                <span class="ai-medical-avatar compact"><Sparkles :size="16" fill="currentColor" /></span>
                <div>
                  <p class="ai-response-text">{{ message.text }}</p>
                  <p v-if="message.reply?.title">{{ message.reply.title }}</p>
                  <ul v-if="message.reply?.lines.length">
                    <li v-for="line in message.reply.lines" :key="line">{{ line }}</li>
                  </ul>
                  <button
                    v-if="message.reply?.actionLabel"
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
          <p v-if="busy" role="status">正在识别和思考，请稍候…</p>
        </div>

        <footer class="ai-medical-composer">
          <div v-if="selectedImage" class="ai-image-pending">
            <img :src="selectedImage" class="ai-image-preview" alt="待发送图片" />
            <button type="button" class="ai-image-remove" @click="selectedImage = ''">移除</button>
          </div>
          <p v-if="imageError" role="alert" class="ai-image-error">{{ imageError }}</p>
          <div class="ai-medical-input-wrap" :class="{ 'is-generating': isGenerating }">
            <button type="button" class="ai-upload-btn" aria-label="上传耗材图片" :disabled="busy" @click="uploadInputRef?.click()">
              <Image :size="18" />
            </button>
            <input ref="uploadInputRef" type="file" class="ai-hidden-upload" accept="image/png,image/jpeg,image/webp" :disabled="busy" @change="selectImage" />
            <textarea
              ref="promptRef"
              v-model="prompt"
              rows="3"
              maxlength="8000"
              placeholder="输入SPD业务问题，或上传耗材图片进行识别…"
              aria-label="向AI医护助手提问"
              @keydown.enter.exact.prevent="submitPrompt()"
              @input="autoResizeTextarea"
            ></textarea>
            <!-- 发送 / 停止按钮：豆包风格，按需切换。生成中显示停止按钮，用户可随时中断。 -->
            <button
              v-if="!isGenerating"
              type="button"
              class="ai-send-btn"
              aria-label="发送问题"
              :disabled="busy || (!prompt.trim() && !selectedImage)"
              @click="submitPrompt()"
            >
              <Send :size="18" fill="currentColor" />
            </button>
            <button
              v-else
              type="button"
              class="ai-stop-btn"
              aria-label="停止生成"
              @click="stopGeneration"
            >
              <Square :size="14" fill="currentColor" />
            </button>
          </div>
          <small>AI可识别图片及解答流程；实时业务数据请通过业务页面查询</small>
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ai-response-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.ai-image-preview { max-width: 120px; max-height: 100px; object-fit: contain; border-radius: 8px; }

.ai-image-pending {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 4px;
  padding: 6px 8px 6px 6px;
  border: 1px dashed #c5cdec;
  border-radius: 10px;
  background: linear-gradient(135deg, #f7f5ff 0%, #f0f4ff 100%);
}

.ai-image-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  border: 0;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(60, 75, 130, 0.12), inset 0 0 0 1px #e1e6f3;
  padding: 0 12px;
  color: #c0313a;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.18s ease, box-shadow 0.18s ease, color 0.18s ease;
}

.ai-image-remove:hover {
  background: linear-gradient(135deg, #fff1f1 0%, #ffe2e2 100%);
  color: #a7212a;
  box-shadow: 0 4px 10px rgba(192, 49, 58, 0.22), inset 0 0 0 1px #f3a8a8;
}

.ai-medical-panel button:disabled { opacity: 0.45; cursor: not-allowed; }

.ai-medical-fab.is-draggable {
  cursor: grab;
  touch-action: none;
}

.ai-medical-fab.is-draggable:active {
  cursor: grabbing;
}

.ai-medical-fab {
  position: fixed;
  right: 34px;
  bottom: 32px;
  /* Above the sticky top navbar (z-index 100) so the FAB stays clickable
     even when the page content scrolls beneath it. */
  z-index: 200;
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
  /* Default sits below the sticky top navbar (56px) with an 18px gap. The
     ensurePanelPosition() / clampPosition() helpers keep the panel pinned
     inside that safe area when the user drags it. */
  top: 74px;
  right: 18px;
  bottom: 18px;
  /* Above the sticky top navbar (z-index 100) so the panel can never be
     covered by it, but below modal masks (z-index 99999). */
  z-index: 200;
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

.ai-medical-panel.is-draggable {
  touch-action: none;
}

.ai-medical-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 86px;
  border-bottom: 1px solid #eceef6;
  /* Subtle gradient anchors the header visually and matches the FAB / CTA. */
  background: linear-gradient(135deg, #f5f3ff 0%, #eef2ff 60%, #ffffff 100%);
  padding: 18px 20px;
  cursor: default;
  -webkit-user-select: none;
  user-select: none;
}

.ai-medical-drag-handle {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 44px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  padding: 6px 10px;
  color: #27314b;
  font: inherit;
  text-align: left;
  cursor: grab;
  transition: background-color 0.18s ease, box-shadow 0.18s ease;
}

.ai-medical-drag-handle:hover {
  background: #ffffff;
  box-shadow: 0 4px 14px rgba(82, 96, 199, 0.14), inset 0 0 0 1px rgba(140, 124, 240, 0.22);
}

.ai-medical-drag-handle:active {
  cursor: grabbing;
  background: #ffffff;
  box-shadow: 0 6px 18px rgba(82, 96, 199, 0.22), inset 0 0 0 1px rgba(140, 124, 240, 0.32);
}

.ai-medical-drag-handle .ai-medical-drag-title {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.ai-medical-drag-handle .ai-medical-drag-title strong {
  color: #161f38;
  font-size: 21px;
  line-height: 1.1;
}

.ai-medical-drag-handle .ai-medical-drag-title span {
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
  border: 1px solid #e1e6f3;
  border-radius: 10px;
  background: #ffffff;
  color: #4b546e;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(48, 60, 110, 0.06);
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease,
    box-shadow 0.18s ease, transform 0.18s ease;
}

.ai-medical-window-actions button:hover {
  background: linear-gradient(135deg, #f0edff 0%, #eaf0ff 100%);
  border-color: #b9acf3;
  color: #4b40c8;
  box-shadow: 0 4px 12px rgba(82, 96, 199, 0.18);
  transform: translateY(-1px);
}

/* Close button on hover gets a destructive tint for affordance. */
.ai-medical-window-actions button[aria-label="关闭AI医护助手"]:hover {
  background: linear-gradient(135deg, #fff1f1 0%, #ffe2e2 100%);
  border-color: #f3a8a8;
  color: #c0313a;
  box-shadow: 0 4px 12px rgba(192, 49, 58, 0.22);
}

.ai-medical-window-actions button:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(48, 60, 110, 0.1);
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
  padding: 12px 14px;
  border: 1px solid #ece6ff;
  border-radius: 12px;
  background: linear-gradient(135deg, #f6f3ff 0%, #f0f4ff 100%);
  box-shadow: 0 3px 12px rgba(82, 96, 199, 0.08);
}

.ai-medical-welcome strong {
  font-size: 15px;
  color: #1c2540;
}

.ai-medical-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6f47e8 0%, #467df5 100%);
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(64, 82, 199, 0.28), inset 0 0 0 1px rgba(255, 255, 255, 0.22);
}

.ai-medical-avatar.compact {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  box-shadow: 0 3px 8px rgba(64, 82, 199, 0.24), inset 0 0 0 1px rgba(255, 255, 255, 0.22);
}

.ai-medical-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.ai-medical-quick-grid button {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 64px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: linear-gradient(135deg, #6f47e8 0%, #467df5 100%);
  box-shadow: 0 6px 16px rgba(64, 82, 209, 0.18), inset 0 0 0 1px rgba(255, 255, 255, 0.18);
  padding: 12px 14px;
  color: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.ai-medical-quick-grid button :deep(svg) {
  flex: 0 0 auto;
  color: #ffffff;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.18));
}

.ai-medical-quick-grid button:hover {
  transform: translateY(-2px);
  background: linear-gradient(135deg, #7d56f3 0%, #5289fb 100%);
  box-shadow: 0 12px 24px rgba(64, 82, 209, 0.32), inset 0 0 0 1px rgba(255, 255, 255, 0.28);
}

.ai-medical-quick-grid button:active {
  transform: translateY(0);
  box-shadow: 0 4px 10px rgba(64, 82, 209, 0.28), inset 0 0 0 1px rgba(255, 255, 255, 0.2);
}

.ai-medical-quick-grid button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.ai-medical-quick-grid button span {
  min-width: 0;
  color: #ffffff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  border: 1px solid #e1d8ff;
  border-radius: 10px 10px 2px 10px;
  background: linear-gradient(135deg, #f1efff 0%, #ebe7ff 100%);
  box-shadow: 0 3px 10px rgba(82, 96, 199, 0.1);
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
  border: 0;
  border-radius: 999px;
  background: linear-gradient(105deg, #6f47e8 0%, #467df5 100%);
  box-shadow: 0 6px 16px rgba(64, 82, 209, 0.22), inset 0 0 0 1px rgba(255, 255, 255, 0.18);
  padding: 0 18px;
  color: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.ai-medical-link:hover {
  transform: translateY(-1px);
  background: linear-gradient(105deg, #7d56f3 0%, #5289fb 100%);
  box-shadow: 0 10px 22px rgba(64, 82, 209, 0.32), inset 0 0 0 1px rgba(255, 255, 255, 0.28);
}

.ai-medical-link:active {
  transform: translateY(0);
  box-shadow: 0 4px 10px rgba(64, 82, 209, 0.24), inset 0 0 0 1px rgba(255, 255, 255, 0.2);
}

.ai-medical-composer {
  border-top: 1px solid #eceef5;
  background: #ffffff;
  padding: 14px 16px 12px;
}

.ai-medical-input-wrap {
  display: grid;
  /* Upload | textarea | send/stop. The middle column stretches, the action
     buttons stay at the right edge and align to the bottom. */
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  align-items: end;
  column-gap: 8px;
  border: 1px solid #dde2ed;
  border-radius: 14px;
  background: #ffffff;
  padding: 8px 8px 8px 6px;
  box-shadow: 0 2px 12px rgba(30, 45, 90, 0.05);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.ai-medical-input-wrap:focus-within {
  border-color: #8ea2fa;
  box-shadow: 0 4px 16px rgba(64, 82, 209, 0.14);
}

.ai-medical-input-wrap.is-generating {
  background: #fafbff;
  border-color: #c8bcf3;
  box-shadow: 0 4px 16px rgba(64, 82, 209, 0.16), inset 0 0 0 1px rgba(200, 188, 243, 0.55);
}

.ai-upload-btn {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid #e1e6f3;
  border-radius: 10px;
  background: #f5f6fc;
  color: #5560a0;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.ai-upload-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #f0edff 0%, #eaf0ff 100%);
  border-color: #b9acf3;
  color: #4b40c8;
  box-shadow: inset 0 0 0 1px #c8bcf3;
}

.ai-upload-btn:active:not(:disabled) {
  background: #e7e2ff;
}

.ai-upload-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-hidden-upload {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.ai-medical-input-wrap textarea {
  /* Auto-resize driven by autoResizeTextarea() — see MAX_TEXTAREA_HEIGHT. */
  min-height: 84px;
  max-height: 220px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 6px 4px 4px;
  color: #27314b;
  font: inherit;
  font-size: 14px;
  line-height: 1.55;
  overflow-y: hidden;
}

.ai-medical-input-wrap textarea::placeholder {
  color: #9ca4b6;
}

.ai-send-btn {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #6f47e8 0%, #467df5 100%);
  box-shadow: 0 4px 12px rgba(64, 82, 209, 0.32), inset 0 0 0 1px rgba(255, 255, 255, 0.18);
  color: #ffffff;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease, opacity 0.18s ease;
}

.ai-send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  background: linear-gradient(135deg, #7d56f3 0%, #5289fb 100%);
  box-shadow: 0 8px 18px rgba(64, 82, 209, 0.38), inset 0 0 0 1px rgba(255, 255, 255, 0.28);
}

.ai-send-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 3px 8px rgba(64, 82, 209, 0.32), inset 0 0 0 1px rgba(255, 255, 255, 0.2);
}

.ai-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  background: linear-gradient(135deg, #b3b6c8 0%, #9aa1bb 100%);
  box-shadow: none;
}

/* Stop button mirrors the 豆包 pattern: 圆形实心背景，hover 时从主色切到
   红色，提示这是破坏性操作。生成中始终保持可点击。 */
.ai-stop-btn {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #4b40c8 0%, #3b6df0 100%);
  box-shadow: 0 4px 14px rgba(64, 82, 209, 0.34), inset 0 0 0 2px rgba(255, 255, 255, 0.22);
  color: #ffffff;
  cursor: pointer;
  animation: ai-stop-pulse 1.4s ease-in-out infinite;
  transition: transform 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.ai-stop-btn:hover {
  transform: scale(1.06);
  background: linear-gradient(135deg, #c4384b 0%, #e0623b 100%);
  box-shadow: 0 8px 18px rgba(196, 56, 75, 0.36), inset 0 0 0 2px rgba(255, 255, 255, 0.28);
  animation: none;
}

.ai-stop-btn:active {
  transform: scale(0.96);
}

@keyframes ai-stop-pulse {
  0%, 100% {
    box-shadow: 0 4px 14px rgba(64, 82, 209, 0.34),
      inset 0 0 0 2px rgba(255, 255, 255, 0.22),
      0 0 0 0 rgba(111, 71, 232, 0.35);
  }
  50% {
    box-shadow: 0 4px 14px rgba(64, 82, 209, 0.34),
      inset 0 0 0 2px rgba(255, 255, 255, 0.22),
      0 0 0 8px rgba(111, 71, 232, 0);
  }
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
    top: 8px;
    right: 8px;
    bottom: 8px;
    left: 8px;
    width: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-medical-fab,
  .ai-medical-drawer-enter-active,
  .ai-medical-drawer-leave-active,
  .ai-stop-btn {
    transition: none;
    animation: none;
  }
}
</style>
