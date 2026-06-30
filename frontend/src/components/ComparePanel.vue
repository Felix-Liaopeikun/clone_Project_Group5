<template>
  <div class="compare-panel">
    <div class="compare-header">
      <span class="compare-title">模型对比结果</span>
      <span class="compare-id">#{{ compareResult.id }}</span>
    </div>
    <div class="compare-columns" :class="{ 'two-cols': results.length === 2, 'three-cols': results.length >= 3 }">
      <div
        v-for="(r, i) in results"
        :key="r.tag"
        class="compare-column"
        :class="{ winner: compareResult.winner === r.tag }"
      >
        <div class="col-header">
          <el-tag :type="tagType(i)" size="large" effect="dark">{{ r.model }}</el-tag>
          <span class="latency">{{ r.latencyMs }}ms</span>
        </div>
        <div class="col-answer">
          <div class="answer-text">{{ r.answer }}</div>
        </div>
        <div v-if="r.citations && r.citations.length > 0" class="col-citations">
          <el-divider content-position="left">引用来源</el-divider>
          <CitationItem
            v-for="(c, ci) in r.citations"
            :key="ci"
            :citation="c"
          />
        </div>
        <div class="col-vote" v-if="!compareResult.winner">
          <el-button
            type="primary"
            size="small"
            @click="$emit('vote', r.tag)"
            :loading="voting === r.tag"
          >
            {{ r.tag }} 模型更优
          </el-button>
        </div>
      </div>
    </div>
    <div class="compare-footer" v-if="!compareResult.winner">
      <el-button size="small" @click="$emit('vote', 'TIE')" :loading="voting === 'TIE'">
        平手（两者相当）
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import CitationItem from './CitationItem.vue'

const props = defineProps({
  compareResult: {
    type: Object,
    required: true
  },
  voting: {
    type: String,
    default: null
  }
})

defineEmits(['vote'])

const results = computed(() => props.compareResult.results || [])

const tagColors = ['', 'primary', 'success', 'warning', 'danger']
function tagType(i) {
  return tagColors[i] || 'info'
}
</script>

<style scoped>
.compare-panel {
  width: 100%;
  margin-top: 8px;
}
.compare-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.compare-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary, #303133);
}
.compare-id {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}
.compare-columns {
  display: grid;
  gap: 16px;
}
.compare-columns.two-cols {
  grid-template-columns: 1fr 1fr;
}
.compare-columns.three-cols {
  grid-template-columns: 1fr 1fr 1fr;
}
.compare-column {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  padding: 16px;
  background: var(--bg-card, #fff);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.compare-column.winner {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.2);
}
.col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.latency {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}
.col-answer {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
}
.answer-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary, #303133);
}
.col-vote {
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid var(--border-color-light, #ebeef5);
}
.compare-footer {
  text-align: center;
  margin-top: 12px;
}
</style>
