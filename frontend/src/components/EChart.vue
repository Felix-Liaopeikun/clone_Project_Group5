<template>
  <div ref="chartEl" class="echart" :style="{ height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  PieChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer
])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const chartEl = ref(null)
let chartInstance = null

const render = () => {
  if (!chartEl.value) return
  if (!chartInstance) chartInstance = echarts.init(chartEl.value)
  chartInstance.setOption(props.option, true)
}

const handleResize = () => chartInstance?.resize()

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})

watch(() => props.option, render, { deep: true })
</script>

<style scoped>
.echart {
  width: 100%;
}
</style>