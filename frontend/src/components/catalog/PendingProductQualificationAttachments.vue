<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { Download, Eye, FileText, RefreshCw } from '@lucide/vue'
import { fetchPendingProductQualificationAttachments, fetchPendingProductQualificationBlob, type PendingProductAttachment } from '../../api/pendingProductApplications'

const props = defineProps<{ applicationNo: string }>()
const files = ref<PendingProductAttachment[]>([])
const loading = ref(false)
const error = ref('')
const previewOpen = ref(false)
const previewFile = ref<PendingProductAttachment | null>(null)
const previewUrl = ref('')
const previewType = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let listRequest = 0
let previewRequest = 0

function closePreview() {
  previewRequest++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewFile.value = null
  previewLoading.value = false
}

async function loadFiles() {
  const request = ++listRequest
  loading.value = true
  error.value = ''
  files.value = []
  try {
    const result = await fetchPendingProductQualificationAttachments(props.applicationNo)
    if (request === listRequest) files.value = result
  } catch (err) {
    if (request === listRequest) error.value = err instanceof Error ? err.message : '资质附件加载失败'
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function preview(file: PendingProductAttachment) {
  closePreview()
  const request = ++previewRequest
  previewFile.value = file
  previewOpen.value = true
  previewLoading.value = true
  previewError.value = ''
  try {
    const blob = await fetchPendingProductQualificationBlob(props.applicationNo, file.attachmentId)
    if (request !== previewRequest) return
    previewUrl.value = URL.createObjectURL(blob)
    previewType.value = (blob.type || file.contentType).split(';')[0].toLowerCase()
  } catch (err) {
    if (request === previewRequest) previewError.value = err instanceof Error ? err.message : '附件阅览失败'
  } finally {
    if (request === previewRequest) previewLoading.value = false
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
function statusLabel(status?: string) {
  return ({ expired: '已过期', invalid: '失效', not_effective: '尚未生效', valid: '有效' } as Record<string, string>)[status || ''] || ''
}

watch(() => props.applicationNo, () => { previewOpen.value = false; closePreview(); loadFiles() }, { immediate: true })
onUnmounted(() => { listRequest++; closePreview() })
</script>

<template>
  <section class="qualification-attachments" aria-label="资质证照附件">
    <header><strong><FileText :size="15" /> 资质证照附件（{{ files.length }}）</strong><button class="btn-text" type="button" :disabled="loading" @click="loadFiles"><RefreshCw :size="14" /> 刷新附件</button></header>
    <p class="attachment-hint">包含申请上传文件，以及当前关联商品、供应商、厂家和合同的证照附件。</p>
    <p v-if="loading">正在加载资质附件...</p>
    <p v-if="error" class="attachment-error" role="alert">{{ error }}</p>
    <p v-if="!loading && !error && !files.length">暂无资质附件</p>
    <ul v-if="files.length" class="qualification-files">
      <li v-for="file in files" :key="file.attachmentId">
        <div class="qualification-file-info">
          <strong :title="file.fileName">{{ file.fileName }}</strong>
          <small>{{ sourceLabel(file) }}<template v-if="file.licenseName"> · {{ file.licenseName }}</template><template v-if="file.ownerName"> · {{ file.ownerName }}</template> · {{ (file.fileSize / 1024).toFixed(1) }} KB</small>
          <small v-if="file.source === 'license'">{{ file.licenseNo || '未维护证照编号' }} · {{ statusLabel(file.licenseStatus) }}<template v-if="file.expireDate"> · 有效期至 {{ file.expireDate }}</template></small>
        </div>
        <div class="qualification-file-actions"><button class="btn-text" type="button" :aria-label="`阅览 ${file.fileName}`" @click="preview(file)"><Eye :size="14" /> 阅览</button><button class="btn-text" type="button" :aria-label="`下载 ${file.fileName}`" @click="download(file)"><Download :size="14" /> 下载</button></div>
      </li>
    </ul>
    <el-dialog v-model="previewOpen" :title="previewFile?.fileName || '资质附件阅览'" width="min(1000px, 94vw)" append-to-body destroy-on-close @close="closePreview">
      <p v-if="previewLoading">正在读取附件...</p>
      <p v-else-if="previewError" class="attachment-error" role="alert">{{ previewError }}</p>
      <img v-else-if="previewUrl && previewType.startsWith('image/')" class="qualification-image" :src="previewUrl" :alt="previewFile?.fileName" />
      <iframe v-else-if="previewUrl && previewType === 'application/pdf'" class="qualification-pdf" :src="previewUrl" :title="previewFile?.fileName" />
      <p v-else-if="previewUrl">该文件格式不支持在线阅览，请下载后查看。</p>
      <template #footer><button v-if="previewFile" type="button" class="btn" :disabled="previewLoading" @click="download(previewFile)"><Download :size="14" /> 下载附件</button><button type="button" class="btn" @click="previewOpen = false">关闭</button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.qualification-attachments { grid-column: 1 / -1; width: 100%; min-width: 0; border-top: 1px solid #e2e8f0; padding-top: 12px; }
.qualification-attachments > header, .qualification-attachments > header strong { display: flex; align-items: center; gap: 6px; }
.qualification-attachments > header { justify-content: space-between; }
.attachment-hint, .qualification-file-info small { color: #64748b; font-size: 12px; }
.attachment-error { color: #b91c1c; }
.qualification-files { list-style: none; padding: 0; margin: 8px 0 0; max-height: 330px; overflow: auto; }
.qualification-files li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #eef2f7; }
.qualification-file-info { min-width: 0; display: grid; gap: 4px; }
.qualification-file-info strong { overflow-wrap: anywhere; font-size: 13px; }
.qualification-file-actions { display: flex; gap: 10px; flex-shrink: 0; }
.qualification-image { display: block; max-width: 100%; max-height: 65vh; margin: auto; object-fit: contain; }
.qualification-pdf { display: block; border: 0; width: 100%; height: 65vh; }
@media (max-width: 600px) { .qualification-files li { align-items: flex-start; flex-direction: column; gap: 6px; } }
</style>
