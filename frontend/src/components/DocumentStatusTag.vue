<template>
  <el-tag :type="tagType" :effect="effect" round>
    <el-icon v-if="status === 'processing'" class="rotating"><Loading /></el-icon>
    <span>{{ label }}</span>
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'

const props = defineProps({
  status: { type: String, required: true },
  effect: { type: String, default: 'light' }
})

// pending/processing/done/failed 颜色映射
const tagType = computed(() => {
  switch (props.status) {
    case 'pending': return 'info'
    case 'processing': return 'warning'
    case 'done': return 'success'
    case 'failed': return 'danger'
    default: return ''
  }
})

const label = computed(() => {
  switch (props.status) {
    case 'pending': return '待处理'
    case 'processing': return '处理中'
    case 'done': return '已完成'
    case 'failed': return '失败'
    default: return props.status || '未知'
  }
})
</script>

<style scoped>
.rotating {
  animation: spin 1s linear infinite;
  margin-right: 4px;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>