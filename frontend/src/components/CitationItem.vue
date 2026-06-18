<template>
  <div class="citation-card" @click="handleClick">
    <div class="citation-header">
      <div class="citation-title">
        <el-icon><Document /></el-icon>
        <span class="title-text">{{ citation.title || `文档 #${citation.documentId}` }}</span>
        <el-tag v-if="citation.category" size="small" type="info" effect="plain">
          {{ citation.category }}
        </el-tag>
      </div>
      <el-tag v-if="citation.score != null" size="small" type="success">
        score {{ Number(citation.score).toFixed(2) }}
      </el-tag>
    </div>
    <div v-if="citation.snippet" class="citation-snippet">{{ citation.snippet }}</div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({
  citation: { type: Object, required: true }
})

const router = useRouter()

const handleClick = () => {
  if (props.citation?.documentId) {
    router.push(`/documents/${props.citation.documentId}`)
  }
}
</script>

<style scoped>
.citation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.citation-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
  font-weight: 600;
  font-size: 14px;
  min-width: 0;
}
.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}
</style>