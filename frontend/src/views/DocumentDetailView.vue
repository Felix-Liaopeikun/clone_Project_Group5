<template>
  <div class="page-container">
    <el-page-header @back="goBack" :content="doc?.title || '文档详情'" class="page-header" />

    <div v-loading="loading">
      <el-empty v-if="!loading && !doc" description="文档不存在或已删除" />

      <template v-else>
        <!-- 基本信息卡 -->
        <div class="section-card">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="文档 ID">{{ doc.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <DocumentStatusTag :status="doc.status" />
            </el-descriptions-item>
            <el-descriptions-item label="文件类型">
              <el-tag size="small" effect="plain">{{ doc.fileType?.toUpperCase() }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="原始文件名">{{ doc.originalName }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatFileSize(doc.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="分类">
              <el-tag size="small" type="info" effect="plain">{{ doc.category || '未分类' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Tags" :span="3">
              <template v-if="tagList.length">
                <el-tag
                  v-for="t in tagList"
                  :key="t"
                  size="small"
                  style="margin-right:6px"
                >{{ t }}</el-tag>
              </template>
              <span v-else style="color:#909399">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(doc.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间" :span="2">{{ formatDateTime(doc.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="doc.errorMsg" label="错误信息" :span="3">
              <span style="color:#f56c6c">{{ doc.errorMsg }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 摘要 -->
        <div class="section-card">
          <div class="card-title">AI 摘要</div>
          <div v-if="doc.status === 'pending' || doc.status === 'processing'" class="empty-block">
            <el-icon class="rotating"><Loading /></el-icon>
            正在生成摘要…（每 3 秒自动刷新）
          </div>
          <div v-else-if="doc.summary" class="summary-text">{{ doc.summary }}</div>
          <div v-else class="empty-block">暂无摘要</div>
        </div>

        <!-- 分块列表 -->
        <div class="section-card">
          <div class="card-title-row">
            <div class="card-title">文档分块 (Chunks)</div>
            <span class="muted">展示前 {{ shownChunks.length }} 条</span>
          </div>
          <el-empty v-if="chunks.length === 0" description="暂无分块数据" />
          <template v-else>
            <div
              v-for="chunk in shownChunks"
              :key="chunk.id"
              class="chunk-item"
              @click="toggleExpand(chunk.id)"
            >
              <div class="chunk-header">
                <span class="chunk-index">#{{ chunk.chunkIndex }}</span>
                <span class="chunk-meta">
                  {{ chunk.charCount }} 字 · {{ formatDateTime(chunk.createdAt) }}
                </span>
                <el-icon class="chunk-arrow">
                  <component :is="expanded[chunk.id] ? 'ArrowDown' : 'ArrowRight'" />
                </el-icon>
              </div>
              <div
                v-if="expanded[chunk.id]"
                class="chunk-content"
              >{{ chunk.content }}</div>
            </div>

            <div v-if="chunks.length > 5" class="expand-toggle">
              <el-button link type="primary" @click="allExpanded = !allExpanded">
                {{ allExpanded ? '收起全部' : `展开更多（剩余 ${chunks.length - 5} 条）` }}
              </el-button>
            </div>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { getDocument } from '@/api/document'
import { formatFileSize, formatDateTime } from '@/utils/format'

const props = defineProps({
  id: { type: [String, Number], required: true }
})

const router = useRouter()
const loading = ref(false)
const doc = ref(null)
const chunks = ref([])
const expanded = reactive({})
const allExpanded = ref(false)
let pollTimer = null

const tagList = computed(() => {
  if (!doc.value?.tags) return []
  return String(doc.value.tags)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
})

const shownChunks = computed(() => {
  if (allExpanded.value) return chunks.value
  return chunks.value.slice(0, 5)
})

const toggleExpand = (id) => {
  expanded[id] = !expanded[id]
}

const goBack = () => router.push('/documents')

const fetchDoc = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const data = await getDocument(props.id)
    doc.value = data
    // 后端 VO 中可能没把 chunks 放进顶层；尝试兼容两种形态
    chunks.value = data.chunks || data.chunkList || []
    // 自动展开当前前 5 条的展开状态在 view 模板里基于 expanded[id]
  } catch (e) {
    doc.value = null
  } finally {
    if (!silent) loading.value = false
  }
}

// pending/processing 状态时轮询
const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!doc.value) return
    if (doc.value.status !== 'pending' && doc.value.status !== 'processing') return
    await fetchDoc(true)
    if (doc.value && doc.value.status !== 'pending' && doc.value.status !== 'processing') {
      stopPolling()
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(allExpanded, (v) => {
  if (v) {
    for (const c of chunks.value) expanded[c.id] = true
  } else {
    for (const c of chunks.value) delete expanded[c.id]
  }
})

watch(() => props.id, () => {
  allExpanded.value = false
  for (const k of Object.keys(expanded)) delete expanded[k]
  fetchDoc().then(() => {
    if (doc.value && (doc.value.status === 'pending' || doc.value.status === 'processing')) {
      startPolling()
    }
  })
})

onMounted(() => {
  fetchDoc().then(() => {
    if (doc.value && (doc.value.status === 'pending' || doc.value.status === 'processing')) {
      startPolling()
    }
  })
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.card-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.summary-text {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #303133;
}
.chunk-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fafbfc;
  transition: background 0.2s;
}
.chunk-item:hover {
  background: #f0f5ff;
}
.chunk-header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  gap: 12px;
}
.chunk-index {
  font-weight: 600;
  color: #409eff;
}
.chunk-meta {
  color: #909399;
  font-size: 12px;
  flex: 1;
}
.chunk-content {
  padding: 0 12px 12px;
  white-space: pre-wrap;
  line-height: 1.6;
  color: #303133;
  border-top: 1px dashed #ebeef5;
  margin-top: 4px;
  padding-top: 10px;
}
.expand-toggle {
  text-align: center;
  margin-top: 8px;
}
.rotating {
  animation: spin 1s linear infinite;
  margin-right: 6px;
  vertical-align: middle;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>