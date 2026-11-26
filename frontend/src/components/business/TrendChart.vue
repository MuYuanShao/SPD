<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type ECharts } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, AriaComponent, CanvasRenderer])
export interface TrendSeries { name: string; values: number[]; color: string; type?: 'bar' | 'line' }
const props = withDefaults(defineProps<{ labels: string[]; series: TrendSeries[]; height?: number; title: string; unit?: string }>(), { height: 225, unit: '万元' })
const host = ref<HTMLElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

/* =====================================================================
 * 图表字号体系（D-01）
 * ---------------------------------------------------------------------
 * ECharts 的 fontSize 是 canvas 像素值，不接受 CSS var() 表达式，
 * 因此图表无法直接消费 --spd-font-size-*，必须：
 *   1. 通过 getComputedStyle 读取 --spd-chart-font-* 的实际像素值；
 *   2. 在字号档位（<html data-font-size>）变化时重新读取并 setOption 重绘。
 *
 * 令牌改了不用改 JS —— 只需改 tokens/typography.css 的 --spd-chart-font-*。
 * 标准文档：docs/ui-review/spd-typography-spec-final.md 第 5 章。
 * ===================================================================== */

/** 图表字号令牌 → 用途（标准文档 5.4） */
const CHART_FONT_TOKENS = {
  /** 坐标轴刻度 / X 轴、Y 轴名称 */
  sm: '--spd-chart-font-sm',
  /** 图例文字 */
  base: '--spd-chart-font-base',
  /** 数据标签 / Tooltip 标题 */
  lg: '--spd-chart-font-lg',
  /** Tooltip 正文 */
  xl: '--spd-chart-font-xl'
} as const

/** 兜底值（standard 档），令牌缺失时使用，保证图表不会因缺值而崩 */
const CHART_FONT_FALLBACK = { sm: 12, base: 13, lg: 14, xl: 15 }

/**
 * 读取图表字号令牌的真实像素值。
 *
 * 注意：不能直接用 getComputedStyle(root).getPropertyValue(token)，
 * 因为令牌以 calc(var(--spd-fs-base) - 2px) 形式声明时，浏览器返回的是
 * **未解析的 calc 表达式字符串**（如 "calc(18px - 2px)"），parseFloat 得到 NaN。
 * 正确做法是把令牌挂到一个临时探针元素的 font-size 上，
 * 由浏览器完成 calc 解析后再读取 computed fontSize。
 *
 * @param key 令牌用途键
 * @returns 像素数值（number）
 */
function resolveChartFont(key: keyof typeof CHART_FONT_TOKENS): number {
  if (typeof window === 'undefined') return CHART_FONT_FALLBACK[key]
  const token = CHART_FONT_TOKENS[key]
  const probe = document.createElement('span')
  probe.setAttribute('style', 'position:absolute;left:-9999px;top:-9999px;font-size:var(' + token + ')')
  document.body.appendChild(probe)
  const parsed = parseFloat(getComputedStyle(probe).fontSize)
  probe.remove()
  return Number.isFinite(parsed) && parsed > 0 ? parsed : CHART_FONT_FALLBACK[key]
}

function render() {
  const fontSm = resolveChartFont('sm')
  const fontBase = resolveChartFont('base')
  const fontLg = resolveChartFont('lg')
  const fontXl = resolveChartFont('xl')
  const option: EChartsCoreOption = {
    animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    aria: { enabled: true },
    tooltip: {
      trigger: 'axis',
      renderMode: 'richText',
      textStyle: {
        fontSize: fontLg,
        lineHeight: 18,
        rich: {
          title: { fontSize: fontLg, fontWeight: 'bold' as const, lineHeight: 22 },
          value: { fontSize: fontXl, lineHeight: 20 }
        }
      }
    },
    legend: {
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 9,
      itemHeight: 5,
      textStyle: { fontSize: fontBase, color: 'var(--spd-p-text-muted)' }
    },
    grid: { left: 38, right: 12, bottom: 25, top: 38 },
    xAxis: {
      type: 'category',
      data: props.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'var(--spd-p-border-subtle)' } },
      axisLabel: { color: 'var(--spd-p-text-muted)', fontSize: fontSm }
    },
    yAxis: {
      type: 'value',
      name: props.unit,
      nameTextStyle: { color: 'var(--spd-p-text-muted)', fontSize: fontSm },
      splitLine: { lineStyle: { color: 'var(--spd-p-bg-muted)', type: 'dashed' } },
      axisLabel: { color: 'var(--spd-p-text-muted)', fontSize: fontSm }
    },
    series: props.series.map(series => ({
      name: series.name, data: series.values, type: series.type || 'bar', smooth: true,
      barMaxWidth: 10, itemStyle: { color: series.color, borderRadius: [2, 2, 0, 0] },
      lineStyle: { width: 2 }, symbolSize: 4, areaStyle: series.type === 'line' ? { opacity: 0.06 } : undefined,
      label: { show: false, fontSize: fontLg }
    }))
  }
  chart?.setOption(option, true)
}

/* =====================================================================
 * 订阅字号档位变化
 * ---------------------------------------------------------------------
 * 档位由 <html data-font-size="standard|xlarge"> 驱动
 * （stores/theme.ts 的 applyFontSize）。默认档「中」会被 removeAttribute，
 * 因此监听 attributeFilter 即可覆盖全部三档。
 * ===================================================================== */
let fontSizeObserver: MutationObserver | undefined

function observeFontSizeTier() {
  if (typeof MutationObserver === 'undefined') return
  fontSizeObserver = new MutationObserver(() => {
    // 令牌随 data-font-size 变化 → 重新读取像素值并重绘
    render()
  })
  fontSizeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-font-size']
  })
}

onMounted(() => {
  if (!host.value) return
  chart = init(host.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(host.value)
  observeFontSizeTier()
})
watch(() => [props.labels, props.series, props.unit], render, { deep: true })
onBeforeUnmount(() => {
  observer?.disconnect()
  fontSizeObserver?.disconnect()
  chart?.dispose()
})
</script>
<template><div ref="host" class="fli-chart" :style="{ height: height + 'px' }" role="img" :aria-label="title" /></template>
