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
function render() {
  const option: EChartsCoreOption = {
    animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    aria: { enabled: true },
    tooltip: { trigger: 'axis', renderMode: 'richText' },
    legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 9, itemHeight: 5, textStyle: { fontSize: 11, color: '#73838c' } },
    grid: { left: 38, right: 12, bottom: 25, top: 38 },
    xAxis: { type: 'category', data: props.labels, axisTick: { show: false }, axisLine: { lineStyle: { color: '#e6ecef' } }, axisLabel: { color: '#87969e', fontSize: 10 } },
    yAxis: { type: 'value', name: props.unit, nameTextStyle: { color: '#87969e', fontSize: 10 }, splitLine: { lineStyle: { color: '#edf1f3', type: 'dashed' } }, axisLabel: { color: '#87969e', fontSize: 10 } },
    series: props.series.map(series => ({
      name: series.name, data: series.values, type: series.type || 'bar', smooth: true,
      barMaxWidth: 10, itemStyle: { color: series.color, borderRadius: [2, 2, 0, 0] },
      lineStyle: { width: 2 }, symbolSize: 4, areaStyle: series.type === 'line' ? { opacity: 0.06 } : undefined
    }))
  }
  chart?.setOption(option, true)
}
onMounted(() => {
  if (!host.value) return
  chart = init(host.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(host.value)
})
watch(() => [props.labels, props.series, props.unit], render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="host" class="fli-chart" :style="{ height: height + 'px' }" role="img" :aria-label="title" /></template>
