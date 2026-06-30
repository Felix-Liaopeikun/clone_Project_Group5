<template>
  <div class="compare-stats" v-if="stats && stats.totalComparisons > 0">
    <h4>模型对比统计</h4>
    <div class="stats-summary">
      <span class="stat-item">总对比次数: <b>{{ stats.totalComparisons }}</b></span>
    </div>
    <EChart
      v-if="chartOption"
      :option="chartOption"
      :height="200"
    />
    <el-table :data="modelRows" size="small" v-if="modelRows.length">
      <el-table-column prop="model" label="模型" />
      <el-table-column prop="wins" label="胜出次数" width="100" />
      <el-table-column prop="rate" label="胜率" width="80">
        <template #default="{ row }">
          <el-progress :percentage="row.rate" :stroke-width="8" :show-text="true" />
        </template>
      </el-table-column>
    </el-table>
  </div>
  <div v-else class="compare-stats-empty">
    <span>暂无对比数据</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EChart from './EChart.vue'

const props = defineProps({
  stats: { type: Object, default: null }
})

const modelRows = computed(() => {
  if (!props.stats || !props.stats.modelWins) return []
  const wins = props.stats.modelWins
  const rates = props.stats.modelWinRate || {}
  return Object.keys(wins).map(model => ({
    model,
    wins: wins[model],
    rate: rates[model] || 0
  }))
})

const chartOption = computed(() => {
  if (modelRows.value.length === 0) return null
  return {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: modelRows.value.map(r => r.model)
    },
    yAxis: { type: 'value', name: '胜出次数' },
    series: [{
      type: 'bar',
      data: modelRows.value.map(r => r.wins),
      itemStyle: {
        color: '#409eff',
        borderRadius: [4, 4, 0, 0]
      }
    }]
  }
})
</script>

<style scoped>
.compare-stats {
  padding: 16px;
}
.compare-stats h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-primary, #303133);
}
.stats-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-regular, #606266);
}
.compare-stats-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-secondary, #909399);
  font-size: 13px;
}
</style>
