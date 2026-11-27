<script setup lang="ts">
/**
 * HospitalProductDetailDialog — 医院目录「详情」居中弹窗
 * =====================================================================
 * 产品诉求：
 *   1. 详情不再用右侧窄抽屉（470px），改为**居中大弹窗**，宽度加大；
 *   2. 内容区可滚动查阅（头部固定、底部操作固定）；
 *   3. 整体样式美化：分区卡片 + 字段栅格 + 状态标签 + 属性标签。
 *
 * 数据来源：医院目录列表行（page.rows），因此不额外请求接口。
 * 完整档案（含附件、UDI、许可证）仍通过「查看完整详情」进入独立详情页。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Boxes, FileText, Package, ShieldCheck, Tag, X } from '@lucide/vue'
import SpdButton from '../common/SpdButton.vue'
import SpdStatusChip, { type SpdStatusChipTone } from '../common/SpdStatusChip.vue'
import { formatDateTime } from '../../utils/dateTime'

const props = defineProps<{
  open: boolean
  row: Record<string, unknown> | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const router = useRouter()
const maskRef = ref<HTMLElement | null>(null)

function text(value: unknown, fallback = '-') {
  if (value === null || value === undefined) return fallback
  const raw = String(value).trim()
  return raw === '' ? fallback : raw
}

function price(value: unknown) {
  if (value === null || value === undefined || String(value).trim() === '') return '-'
  const amount = Number(value)
  return Number.isFinite(amount) ? `¥ ${amount.toFixed(2)}` : text(value)
}

const statusText = computed(() => text(props.row?.status))
const statusTone = computed<SpdStatusChipTone>(() =>
  statusText.value.includes('停') || statusText.value.includes('禁用')
    ? 'danger'
    : statusText.value.includes('启用') || statusText.value.includes('正常')
      ? 'success'
      : 'neutral'
)

/** 属性标签：名称 + 是否命中（后端以「是 / 否」返回） */
const attrTags = computed(() => {
  const row = props.row ?? {}
  const source: Array<{ key: string; text: string }> = [
    { key: 'centralized', text: '集采' },
    { key: 'domestic', text: '国产' },
    { key: 'chargeable', text: '收费' },
    { key: 'keyMonitored', text: '重点监控' },
    { key: 'volumeBased', text: '带量' },
    { key: 'quotaManaged', text: '定数' }
  ]
  return source.map((item) => ({
    ...item,
    hit: String(row[item.key] ?? '') === '是'
  }))
})

const baseFields = computed(() => {
  const row = props.row ?? {}
  return [
    { label: '商品编码', value: text(row.code), mono: true },
    { label: '商品名称', value: text(row.name), strong: true },
    { label: '规格型号', value: text(row.spec) },
    { label: '品牌', value: text(row.brand) },
    { label: '计量单位', value: text(row.unit) },
    { label: '目录单价', value: price(row.price), strong: true }
  ]
})

const partnerFields = computed(() => {
  const row = props.row ?? {}
  return [
    { label: '生产厂家', value: text(row.manufacturer) },
    { label: '供应商', value: text(row.supplier) },
    { label: '创建时间', value: formatDateTime(row.createTime) },
    { label: '最近修改', value: formatDateTime(row.updateTime) }
  ]
})

const classifyFields = computed(() => {
  const row = props.row ?? {}
  return [
    { label: '一级分类', value: text(row.firstCategory) },
    { label: '二级分类', value: text(row.secondCategory) },
    { label: '三级分类', value: text(row.thirdCategory), full: true },
    { label: '注册证号', value: text(row.registrationNo), mono: true },
    { label: '招采子编码', value: text(row.tenderSubCode), mono: true },
    { label: '合同编码', value: text(row.contractCode), mono: true, full: true }
  ]
})

function openFullDetail() {
  const code = text(props.row?.code, '')
  if (!code) return
  emit('close')
  void router.push({ name: 'hospital-product-detail', params: { productCode: code } })
}

/**
 * ESC 关闭：弹窗被 Teleport 到 body，键盘事件不会冒泡到列表页，
 * 因此这里在 document 上监听（仅在弹窗打开时生效）。
 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) {
    event.stopPropagation()
    emit('close')
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
      // 每次打开都从顶部开始查阅
      void nextTick(() => {
        const body = maskRef.value?.querySelector('.hpd-body')
        if (body) body.scrollTop = 0
      })
    } else {
      document.removeEventListener('keydown', onKeydown)
    }
  }
)

onMounted(() => {
  if (props.open) document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open && row"
      ref="maskRef"
      class="hpd-mask"
      role="presentation"
      tabindex="-1"
      @click.self="emit('close')"
    >
      <section class="hpd-dialog" role="dialog" aria-modal="true" aria-label="商品详情">
        <header class="hpd-head">
          <div class="hpd-head-icon" aria-hidden="true"><Package :size="20" /></div>
          <div class="hpd-head-text">
            <p class="hpd-kicker">医院目录 · 商品详情</p>
            <h3>{{ text(row.name) }}</h3>
            <p class="hpd-sub">
              <span class="mono">{{ text(row.code) }}</span>
              <span class="hpd-dot">·</span>
              <span>{{ text(row.spec) }}</span>
            </p>
          </div>
          <div class="hpd-head-side">
            <SpdStatusChip :tone="statusTone" :label="statusText" />
            <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭商品详情" @click="emit('close')">
              <template #icon><X :size="18" /></template>
            </SpdButton>
          </div>
        </header>

        <div class="hpd-body">
          <section class="hpd-section">
            <header class="hpd-section-head">
              <Boxes :size="17" aria-hidden="true" />
              <div>
                <strong>基本信息</strong>
                <span>商品身份、计量单位与价格</span>
              </div>
            </header>
            <dl class="hpd-grid">
              <div v-for="field in baseFields" :key="field.label" class="hpd-field" :class="{ 'is-strong': field.strong }">
                <dt>{{ field.label }}</dt>
                <dd :class="{ mono: field.mono }">{{ field.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="hpd-section">
            <header class="hpd-section-head">
              <FileText :size="17" aria-hidden="true" />
              <div>
                <strong>厂商与时效</strong>
                <span>生产厂家、供应商及维护时间</span>
              </div>
            </header>
            <dl class="hpd-grid">
              <div v-for="field in partnerFields" :key="field.label" class="hpd-field">
                <dt>{{ field.label }}</dt>
                <dd>{{ field.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="hpd-section">
            <header class="hpd-section-head">
              <ShieldCheck :size="17" aria-hidden="true" />
              <div>
                <strong>分类与证照</strong>
                <span>目录分类、注册证与合同信息</span>
              </div>
            </header>
            <dl class="hpd-grid">
              <div v-for="field in classifyFields" :key="field.label" class="hpd-field" :class="{ 'is-full': field.full }">
                <dt>{{ field.label }}</dt>
                <dd :class="{ mono: field.mono }">{{ field.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="hpd-section">
            <header class="hpd-section-head">
              <Tag :size="17" aria-hidden="true" />
              <div>
                <strong>属性标签</strong>
                <span>采购、收费与监控类管理属性</span>
              </div>
            </header>
            <div class="hpd-tags">
              <SpdStatusChip
                v-for="tag in attrTags"
                :key="tag.key"
                :tone="tag.hit ? 'primary' : 'neutral'"
                :label="`${tag.text} · ${tag.hit ? '是' : '否'}`"
                size="sm"
              />
            </div>
          </section>
        </div>

        <footer class="hpd-foot">
          <span class="hpd-foot-hint">附件、UDI 与许可证等完整档案请进入商品详情页查阅</span>
          <div class="hpd-foot-actions">
            <SpdButton label="关闭" tone="neutral" @click="emit('close')"></SpdButton>
            <SpdButton label="查看完整详情" tone="primary" @click="openFullDetail">
              <template #icon><FileText :size="16" /></template>
            </SpdButton>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.hpd-mask {
  position: fixed;
  inset: 0;
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 72px 24px 24px;
  background: var(--spd-p-overlay);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  isolation: isolate;
  animation: hpd-mask-in 0.18s ease-out;
}

@keyframes hpd-mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.hpd-dialog {
  display: flex;
  flex-direction: column;
  width: min(1180px, calc(100vw - 48px));
  max-height: calc(100vh - 96px);
  overflow: hidden;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border-subtle);
  border-radius: 12px;
  box-shadow: var(--spd-p-shadow-3);
  animation: hpd-dialog-in 0.2s ease;
}

@keyframes hpd-dialog-in {
  from { transform: translateY(-12px) scale(0.985); }
  to { transform: translateY(0) scale(1); }
}

/* ===== 头部 ===== */
.hpd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: linear-gradient(120deg,
    color-mix(in srgb, var(--spd-p-primary) 12%, var(--spd-p-bg)) 0%,
    var(--spd-p-bg) 100%);
  border-bottom: 1px solid var(--spd-p-border-subtle);
  flex-shrink: 0;
}

.hpd-head-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: var(--spd-p-bg);
  background: var(--spd-p-primary);
}

.hpd-head-text {
  min-width: 0;
  flex: 1;
}

.hpd-kicker {
  margin: 0;
  color: var(--spd-p-primary-text);
  font-size: var(--spd-font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hpd-head h3 {
  margin: 2px 0 0;
  color: var(--spd-p-text-strong);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.hpd-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 4px 0 0;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.hpd-dot {
  color: var(--spd-p-text-muted);
}

.hpd-head-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* ===== 主体（可滚动） ===== */
.hpd-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 18px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--spd-p-bg-subtle);
}

.hpd-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border-subtle);
  border-radius: 10px;
}

.hpd-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--spd-p-primary-text);
}

.hpd-section-head strong {
  display: block;
  color: var(--spd-p-text-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
}

.hpd-section-head span {
  display: block;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-xs);
}

.hpd-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 0;
}

.hpd-field {
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--spd-p-bg-subtle);
  border: 1px solid transparent;
}

.hpd-field.is-full {
  grid-column: 1 / -1;
}

.hpd-field.is-strong {
  background: color-mix(in srgb, var(--spd-p-primary) 7%, var(--spd-p-bg));
  border-color: color-mix(in srgb, var(--spd-p-primary) 22%, transparent);
}

.hpd-field dt {
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
}

.hpd-field dd {
  margin: 3px 0 0;
  color: var(--spd-p-text-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.hpd-field dd.mono {
  font-family: var(--spd-font-family-mono);
}

.hpd-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ===== 底部 ===== */
.hpd-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px;
  background: var(--spd-p-bg);
  border-top: 1px solid var(--spd-p-border-subtle);
  flex-shrink: 0;
}

.hpd-foot-hint {
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-xs);
}

.hpd-foot-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 900px) {
  .hpd-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hpd-foot {
    flex-direction: column;
    align-items: stretch;
  }
  .hpd-foot-actions {
    justify-content: flex-end;
  }
}
</style>
