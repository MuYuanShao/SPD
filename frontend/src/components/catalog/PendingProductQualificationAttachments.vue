<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElIcon } from 'element-plus'
import { Teleport } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Document,
  Files,
  FolderOpened,
  Picture,
  Postcard,
  Refresh,
  Download,
  Close
} from '@element-plus/icons-vue'
import { fetchPendingProductQualificationAttachments, fetchPendingProductQualificationBlob, type PendingProductAttachment } from '../../api/pendingProductApplications'

const props = defineProps<{
  applicationNo: string
  /**
   * 两列大图排布（用于「资质阅览」弹窗，容器宽 1280px）。
   * 弹窗里每行两张、图片看得清；详情页容器较窄时走自适应多列。
   */
  twoColumn?: boolean
}>()
const files = ref<PendingProductAttachment[]>([])
const loading = ref(false)
const error = ref('')
/**
 * 内联预览：弹窗打开后直接把图片渲染出来，用户不需要再点一次「审阅 / 预览」。
 * 每张图片一条独立的 blob URL（按 attachmentId 缓存），点击列表项切换主图。
 */
const inlinePreviews = ref<Record<number, { url: string; type: string }>>({})
const inlineLoading = ref<Record<number, boolean>>({})
const inlineError = ref<Record<number, string>>({})
const activePreviewId = ref<number | null>(null)
/** 大图查看器：当前正在查看的附件（点击缩略图打开）。 */
const viewerAttachmentId = ref<number | null>(null)
const viewerOpen = ref(false)
let listRequest = 0

/** 当前大图查看器中的附件 */
const viewerAttachment = computed(
  () => inlineFiles.value.find((item) => item.attachmentId === viewerAttachmentId.value) ?? null
)

/** 当前大图查看器中的 blob URL */
const viewerUrl = computed(() => {
  const id = viewerAttachment.value?.attachmentId
  return id ? inlinePreviews.value[id]?.url : undefined
})

/** 打开大图查看器 */
function openViewer(file: PendingProductAttachment) {
  viewerAttachmentId.value = file.attachmentId
  viewerOpen.value = true
}

/** 关闭大图查看器 */
function closeViewer() {
  viewerOpen.value = false
}

/** 在大图查看器里切换到上一张 / 下一张（循环） */
function stepViewer(direction: 1 | -1) {
  const list = inlineFiles.value
  if (!list.length) return
  const current = list.findIndex((item) => item.attachmentId === viewerAttachmentId.value)
  const next = (current + direction + list.length) % list.length
  viewerAttachmentId.value = list[next].attachmentId
}

/** 释放所有内联预览的 blob URL，避免内存泄漏。 */
function releaseInlinePreviews() {
  Object.values(inlinePreviews.value).forEach((item) => {
    if (item?.url) URL.revokeObjectURL(item.url)
  })
  inlinePreviews.value = {}
  inlineLoading.value = {}
  inlineError.value = {}
  activePreviewId.value = null
}

/** 判断附件是否支持浏览器内直接渲染（图片）。 */
function isImageFile(file: PendingProductAttachment) {
  const type = (file.contentType || '').toLowerCase()
  const name = (file.fileName || '').toLowerCase()
  return type.startsWith('image/') || /\.(png|jpe?g|gif|bmp|webp)$/.test(name)
}

/** 判断附件是否支持浏览器内嵌（PDF）。 */
function isPdfFile(file: PendingProductAttachment) {
  const type = (file.contentType || '').toLowerCase()
  const name = (file.fileName || '').toLowerCase()
  return type.includes('pdf') || name.endsWith('.pdf')
}

/**
 * 直接读取图片 / PDF 的二进制并生成 blob URL，
 * 供弹窗内联渲染（不再需要用户点击「审阅」按钮）。
 */
async function loadInlinePreview(file: PendingProductAttachment) {
  if (!isImageFile(file) && !isPdfFile(file)) return
  if (inlinePreviews.value[file.attachmentId]) return
  inlineLoading.value = { ...inlineLoading.value, [file.attachmentId]: true }
  inlineError.value = { ...inlineError.value, [file.attachmentId]: '' }
  try {
    const blob = await fetchPendingProductQualificationBlob(props.applicationNo, file.attachmentId)
    const url = URL.createObjectURL(blob)
    inlinePreviews.value = {
      ...inlinePreviews.value,
      [file.attachmentId]: { url, type: (blob.type || file.contentType || '').split(';')[0].toLowerCase() }
    }
    if (!activePreviewId.value) activePreviewId.value = file.attachmentId  } catch (err) {
    inlineError.value = {
      ...inlineError.value,
      [file.attachmentId]: err instanceof Error ? err.message : '附件读取失败'
    }
  } finally {
    inlineLoading.value = { ...inlineLoading.value, [file.attachmentId]: false }
  }
}

/** 附件列表加载完成后自动把可内联预览的文件读出来。 */
async function loadInlinePreviews(items: PendingProductAttachment[]) {
  const inlineable = items.filter((item) => isImageFile(item) || isPdfFile(item))
  if (!inlineable.length) return
  await Promise.all(inlineable.map((item) => loadInlinePreview(item)))
}

/** 可内联预览（图片 / PDF）的附件列表 */
const inlineFiles = computed(() =>
  files.value.filter((item) => isImageFile(item) || isPdfFile(item))
)

async function loadFiles() {
  const request = ++listRequest
  loading.value = true
  error.value = ''
  files.value = []
  try {
    const result = await fetchPendingProductQualificationAttachments(props.applicationNo)
    if (request === listRequest) files.value = result
    // 附件到位后立即把图片 / PDF 读出来直接渲染，用户无需再点一次「审阅」
    if (request === listRequest) void loadInlinePreviews(result)
  } catch (err) {
    if (request === listRequest) error.value = err instanceof Error ? err.message : '资质附件加载失败'
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function download(file: PendingProductAttachment) {
  try {
    const blob = await fetchPendingProductQualificationBlob(props.applicationNo, file.attachmentId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) { error.value = err instanceof Error ? err.message : '附件下载失败' }
}

function sourceLabel(file: PendingProductAttachment) {
  if (file.source !== 'license') return '申请上传'
  return ({ product: '商品证照', supplier: '供应商证照', manufacturer: '厂家证照', contract: '关联合同' } as Record<string, string>)[file.licenseType || ''] || '关联证照'
}

/** 文件类型徽标：按内容类型给出「图片 / PDF / 文档」标签与图标。 */
function fileKind(file: PendingProductAttachment) {
  const type = (file.contentType || '').toLowerCase()
  const name = (file.fileName || '').toLowerCase()
  if (type.startsWith('image/') || /\.(png|jpe?g|gif|bmp|webp)$/.test(name)) return { label: '图片', icon: Picture, tone: 'image' }
  if (type.includes('pdf') || name.endsWith('.pdf')) return { label: 'PDF', icon: Postcard, tone: 'pdf' }
  if (/\.(docx?|xlsx?)$/.test(name)) return { label: '文档', icon: Document, tone: 'doc' }
  return { label: '附件', icon: Files, tone: 'file' }
}

function formatSize(size?: number) {
  const bytes = Number(size || 0)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

watch(() => props.applicationNo, () => {
  releaseInlinePreviews()
  loadFiles()
}, { immediate: true })
onUnmounted(() => { listRequest++; releaseInlinePreviews() })
</script>

<template>
  <section class="qualification-attachments" aria-label="资质证照附件">
    <!-- 需求：附件区只保留「图片预览」。
         原来的「附件清单」标题行 + 说明行 + 下方逐条附件列表与上方图片网格
         内容重复（同一批附件、同一套查看大图 / 下载能力），却把版面撑得很长。
         现在统一收敛为「图片预览」网格，点击图片进入大图查看器即可下载。 -->
    <p v-if="loading" class="qual-state">正在加载资质附件...</p>
    <p v-if="error" class="attachment-error" role="alert">{{ error }}</p>

    <!-- 空状态：与全站 el-empty 观感一致，用 Element Plus 图标承载语义 -->
    <div v-if="!loading && !error && !inlineFiles.length" class="qual-empty">
      <ElIcon><FolderOpened /></ElIcon>
      <p>暂无资质附件</p>
      <span>该审批单尚未上传或关联任何证照文件。</span>
    </div>

    <!-- ============ 资质审阅：多张图片自然排布（网格 + 可滚动） ============ -->
    <!-- 用户诉求：
         「资质审阅看多张图片时要自然排布，允许滚动」
         因此这里不再用「主图 + 缩略图条」的两段式布局，
         改为**自适应网格**：图片按容器宽度自动换行铺开，
         整体区域超过一定高度时内部滚动（纵向 + 横向都可滚）。
         点击任意一张打开大图查看器，可在多张之间连续翻看。 -->
    <div v-if="inlineFiles.length" class="qual-gallery" :class="{ 'is-pair': twoColumn }">
      <div class="qual-gallery-head">
        <span class="qual-gallery-title">
          <ElIcon><Picture /></ElIcon>
          <strong>图片预览</strong>
          <em class="qual-count">{{ inlineFiles.length }}</em>
        </span>
        <span class="qual-gallery-hint">点击图片可查看大图，支持上下翻看</span>
      </div>
      <div class="qual-gallery-grid">
        <button
          v-for="file in inlineFiles"
          :key="file.attachmentId"
          type="button"
          class="qual-gallery-item"
          :class="{ 'is-active': file.attachmentId === activePreviewId }"
          :title="file.fileName"
          @click="activePreviewId = file.attachmentId; openViewer(file)"
        >
          <span class="qual-gallery-thumb">
            <img
              v-if="inlinePreviews[file.attachmentId] && isImageFile(file)"
              :src="inlinePreviews[file.attachmentId]!.url"
              :alt="file.fileName"
              loading="lazy"
            />
            <span v-else-if="inlineLoading[file.attachmentId]" class="qual-gallery-fallback">
              <ElIcon><Refresh /></ElIcon>
            </span>
            <span v-else class="qual-gallery-fallback">
              <ElIcon><component :is="fileKind(file).icon" /></ElIcon>
            </span>
            <span class="qual-gallery-kind" :class="fileKind(file).tone">{{ fileKind(file).label }}</span>
          </span>
          <em class="qual-gallery-name">{{ file.fileName }}</em>
        </button>
      </div>
    </div>

    <!-- ============ 大图查看器（覆盖层，可左右翻看） ============ -->
    <Teleport to="body">
      <div v-if="viewerOpen && viewerAttachment" class="qual-viewer-mask" @click.self="closeViewer">
        <div class="qual-viewer" role="dialog" aria-modal="true" aria-label="附件大图查看">
          <header class="qual-viewer-head">
            <div>
              <strong :title="viewerAttachment.fileName">{{ viewerAttachment.fileName }}</strong>
              <span>
                {{ sourceLabel(viewerAttachment) }}
                · {{ formatSize(viewerAttachment.fileSize) }}
              </span>
            </div>
            <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭大图" @click="closeViewer">
              <template #icon><ElIcon><Close /></ElIcon></template>
            </SpdButton>
          </header>

          <div class="qual-viewer-body">
            <img
              v-if="viewerUrl && isImageFile(viewerAttachment)"
              class="qual-viewer-image"
              :src="viewerUrl"
              :alt="viewerAttachment.fileName"
            />
            <iframe
              v-else-if="viewerUrl && isPdfFile(viewerAttachment)"
              class="qual-viewer-pdf"
              :src="viewerUrl"
              :title="viewerAttachment.fileName"
            />
            <p v-else class="qual-state">该文件格式不支持在线预览，请下载后查看。</p>

            <template v-if="inlineFiles.length > 1">
              <button
                type="button"
                class="qual-viewer-nav is-prev"
                aria-label="上一张"
                @click="stepViewer(-1)"
              >
                <ElIcon><ArrowLeft /></ElIcon>
              </button>
              <button
                type="button"
                class="qual-viewer-nav is-next"
                aria-label="下一张"
                @click="stepViewer(1)"
              >
                <ElIcon><ArrowRight /></ElIcon>
              </button>
            </template>
          </div>

          <footer class="qual-viewer-foot">
            <span>
              {{ inlineFiles.findIndex((item) => item.attachmentId === viewerAttachmentId) + 1 }}
              / {{ inlineFiles.length }}
            </span>
            <SpdButton label="下载" @click="download(viewerAttachment)">
              <template #icon><ElIcon><Download /></ElIcon></template>
            </SpdButton>
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
/* =====================================================================
 * 资质证照附件区块
 * ---------------------------------------------------------------------
 * 目标：与「新增待审批目录」弹窗的分区标题 / 字段 / 按钮观感一致
 *      —— 主色竖条标题、紧凑字段、图标统一走 Element Plus。
 * 约束：只使用既有 --spd-p-* / --spd-* 令牌，不新增调色板。
 * ===================================================================== */

.qualification-attachments {
  grid-column: 1 / -1;
  width: 100%;
  min-width: 0;
  padding-top: 4px;
}

/* --- 区块标题：与 .approval-create-section h4 同构 --- */
/* 计数徽标 */
.qual-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--spd-primary-bg);
  color: var(--spd-p-primary-text);
  font-size: var(--spd-font-size-xs);
  font-style: normal;
  font-weight: 700;
  line-height: 1;
}

.qual-state {
  margin: 10px 0 0;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
}

.attachment-error {
  margin: 10px 0 0;
  color: var(--spd-p-danger-strong);
  font-size: var(--spd-font-size-base);
}

/* --- 空状态 --- */
.qual-empty {
  display: grid;
  justify-items: center;
  gap: 4px;
  margin-top: 12px;
  padding: 28px 16px;
  border: 1px dashed var(--spd-border-strong);
  border-radius: 8px;
  background: var(--spd-bg-page);
  text-align: center;
}

.qual-empty .el-icon {
  font-size: 26px;
  color: var(--spd-p-text-tertiary);
}

.qual-empty p {
  margin: 2px 0 0;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.qual-empty span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

/* --- 预览 --- */
.qualification-image {
  display: block;
  max-width: 100%;
  max-height: 65vh;
  margin: auto;
  object-fit: contain;
}

.qualification-pdf {
  display: block;
  border: 0;
  width: 100%;
  height: 65vh;
}

/* =====================================================================
 * 资质审阅 · 多图网格（资质阅览弹窗）
 * ---------------------------------------------------------------------
 * 需求：「资质审阅看多张图片时要自然排布，允许滚动」。
 * 布局：
 *   - 网格用 auto-fill + minmax 自适应列宽，图片按容器宽度自动换行，
 *     铺得满但不会被拉伸变形（缩略图统一 object-fit: contain）；
 *   - 图片多时整个网格区域内部滚动（纵向为主，横向也可滚），
 *     弹窗本身不会被撑高，底部「关闭」按钮始终可见；
 *   - 点击任意一张打开大图查看器，可连续翻看上一张 / 下一张。
 * ===================================================================== */
.qual-gallery {
  margin: 10px 0 0;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 8px);
  background: var(--spd-bg-page);
  overflow: hidden;
}

.qual-gallery-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--spd-border-subtle);
  background: var(--spd-bg-container);
}

.qual-gallery-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
}

.qual-gallery-title .el-icon {
  font-size: 15px;
  color: var(--spd-p-text-secondary);
}

.qual-gallery-hint {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
}

/* 网格：自适应列数 + 内部滚动 */
.qual-gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 10px;
  padding: 10px;
  /* 图片多时内部滚动，弹窗高度保持稳定 */
  max-height: min(46vh, 420px);
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
  overscroll-behavior: contain;
}

/* 「两两一行」：弹窗宽度已放宽到 1280px，固定两列，
   每行两张大图看得清，图片之间不再挤成一条窄缝。 */
.qual-gallery.is-pair .qual-gallery-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  max-height: min(58vh, 520px);
}

.qual-gallery.is-pair .qual-gallery-thumb {
  height: 200px;
}

.qual-gallery-grid::-webkit-scrollbar {
  width: 8px;
}

.qual-gallery-grid::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--spd-p-text-strong) 18%, transparent);
  border-radius: 4px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.qual-gallery-item {
  display: grid;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius, 6px);
  background: var(--spd-bg-container);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.qual-gallery-item:hover {
  border-color: var(--spd-p-primary, var(--primary));
}

.qual-gallery-item.is-active {
  border-color: var(--spd-p-primary, var(--primary));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--spd-p-primary, var(--primary)) 22%, transparent);
}

.qual-gallery-thumb {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  height: 96px;
  border-radius: 4px;
  background: var(--spd-bg-page);
  overflow: hidden;
}

.qual-gallery-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qual-gallery-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--spd-p-text-tertiary);
  font-size: 22px;
}

/* 缩略图角标：图片 / PDF */
.qual-gallery-kind {
  position: absolute;
  left: 4px;
  bottom: 4px;
  padding: 0 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--spd-p-bg) 82%, transparent);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
  font-style: normal;
  font-weight: 600;
  line-height: 18px;
}

.qual-gallery-kind.image {
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
}

.qual-gallery-kind.pdf {
  background: var(--spd-p-danger-bg);
  color: var(--spd-p-danger);
}

.qual-gallery-name {
  display: block;
  overflow: hidden;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
  font-style: normal;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qual-gallery-item.is-active .qual-gallery-name {
  color: var(--spd-text-heading);
  font-weight: 600;
}

/* =====================================================================
 * 大图查看器（Teleport 到 body 的覆盖层）
 * ===================================================================== */
.qual-viewer-mask {
  position: fixed;
  inset: 0;
  /* 必须高于 el-dialog 的 overlay（PopupManager 动态分配，起始约 2000），
     否则大图查看器会被「资质阅览」弹窗的遮罩盖住。 */
  z-index: 100050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  background: color-mix(in srgb, var(--spd-p-bg-page) 72%, transparent);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
}

.qual-viewer {
  display: flex;
  flex-direction: column;
  width: min(1100px, 100%);
  max-height: calc(100vh - 48px);
  background: var(--spd-bg-container);
  border: 1px solid var(--spd-border-default);
  border-radius: 12px;
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
}

.qual-viewer-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--spd-border-subtle);
  background: var(--spd-bg-page, var(--spd-p-bg-muted));
}

.qual-viewer-head > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.qual-viewer-head strong {
  overflow: hidden;
  color: var(--spd-text-heading);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qual-viewer-head span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
}

.qual-viewer-body {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: var(--spd-bg-container);
  overflow: auto;
}

.qual-viewer-image {
  display: block;
  max-width: 100%;
  max-height: calc(100vh - 220px);
  object-fit: contain;
}

.qual-viewer-pdf {
  display: block;
  width: 100%;
  height: calc(100vh - 220px);
  border: 0;
}

/* 左右翻看按钮 */
.qual-viewer-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid var(--spd-border-default);
  border-radius: 50%;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font-size: 18px;
  cursor: pointer;
  box-shadow: var(--spd-p-shadow-1);
  transition: border-color 0.16s ease, color 0.16s ease;
}

.qual-viewer-nav:hover {
  border-color: var(--spd-p-primary, var(--primary));
  color: var(--spd-p-primary, var(--primary));
}

.qual-viewer-nav.is-prev {
  left: 12px;
}

.qual-viewer-nav.is-next {
  right: 12px;
}

.qual-viewer-foot {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--spd-border-subtle);
  background: var(--spd-bg-page, var(--spd-p-bg-muted));
}

.qual-viewer-foot > span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 600px) {
  .qual-gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
    max-height: 40vh;
  }

  /* 窄屏回到自适应多列，避免两列把图片压得过小 */
  .qual-gallery.is-pair .qual-gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
    max-height: 40vh;
  }

  .qual-gallery.is-pair .qual-gallery-thumb {
    height: 96px;
  }
}
</style>
