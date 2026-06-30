<template>
  <div class="eval-page">
    <h2 class="page-title">质量评测</h2>

    <!-- 评测统计卡片 -->
    <el-row :gutter="16" v-if="evalStats">
      <el-col :xs="24" :md="8">
        <div class="stat-card">
          <div class="stat-num">{{ evalStats.totalEvaluated }}</div>
          <div class="stat-label">已评测</div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="stat-card">
          <div class="stat-num">{{ evalStats.avgRating || 0 }}</div>
          <div class="stat-label">平均分</div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="stat-card">
          <div class="stat-num">{{ evalStats.usefulRate || 0 }}%</div>
          <div class="stat-label">有用率</div>
        </div>
      </el-col>
    </el-row>

    <!-- 星级分布 -->
    <div v-if="evalStats?.distribution" class="rating-bars card">
      <div class="card-title">星级分布</div>
      <div v-for="i in 5" :key="i" class="bar-row">
        <span class="bar-star">{{ '★'.repeat(i) }}</span>
        <el-progress :percentage="barPercent(i)" :stroke-width="8" :color="barColor(i)" />
        <span class="bar-count">{{ evalStats.distribution?.[i] || 0 }}</span>
      </div>
    </div>

    <!-- 历史记录列表 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">问答记录</span>
        <el-button :icon="RefreshRight" link @click="loadAll">刷新</el-button>
      </div>

      <el-table :data="historyList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
        <el-table-column label="回答" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">{{ row.answer?.substring(0, 80) }}{{ row.answer?.length > 80 ? '…' : '' }}</template>
        </el-table-column>
        <el-table-column label="评分" width="120" align="center">
          <template #default="{ row }">
            <el-rate v-if="row._rated" :model-value="row.rating" :max="5" disabled show-score />
            <span v-else class="no-rate">未评测</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="row._aiEval" @click="onAiEvaluate(row)">
              {{ row._aiResult ? '重新评分' : 'AI评分' }}
            </el-button>
            <el-popover placement="left" :width="280" trigger="click">
              <template #reference>
                <el-button size="small">手动评分</el-button>
              </template>
              <div class="manual-eval">
                <div class="eval-row"><span>评分</span><el-rate v-model="row._manualRating" :max="5" /></div>
                <div class="eval-row" style="margin-top:8px">
                  <el-button :type="row._manualUseful === true ? 'success' : 'default'" size="small" @click="row._manualUseful = true">有用</el-button>
                  <el-button :type="row._manualUseful === false ? 'danger' : 'default'" size="small" @click="row._manualUseful = false">无用</el-button>
                </div>
                <el-input v-model="row._manualFeedback" size="small" placeholder="补充反馈（可选）" :maxlength="200" style="margin-top:8px" />
                <el-button type="primary" size="small" style="margin-top:8px;width:100%" @click="submitManual(row)">提交</el-button>
              </div>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page" v-model:page-size="size"
          :total="total" :page-sizes="[10, 20]"
          layout="total, sizes, prev, pager, next, jumper" background small
          @size-change="loadHistory" @current-change="loadHistory"
        />
      </div>
    </div>

    <!-- AI 评测结果弹窗 -->
    <el-dialog v-model="aiDialogVisible" title="AI 评测结果" width="500px">
      <div v-if="currentAiResult" class="ai-result">
        <div class="result-grid">
          <div class="result-item"><span class="dim-label">准确性</span><el-rate :model-value="currentAiResult.accuracy" :max="5" disabled /><span class="dim-score">{{ currentAiResult.accuracy }}</span></div>
          <div class="result-item"><span class="dim-label">完整性</span><el-rate :model-value="currentAiResult.completeness" :max="5" disabled /><span class="dim-score">{{ currentAiResult.completeness }}</span></div>
          <div class="result-item"><span class="dim-label">相关性</span><el-rate :model-value="currentAiResult.relevance" :max="5" disabled /><span class="dim-score">{{ currentAiResult.relevance }}</span></div>
          <div class="result-item"><span class="dim-label">清晰度</span><el-rate :model-value="currentAiResult.clarity" :max="5" disabled /><span class="dim-score">{{ currentAiResult.clarity }}</span></div>
        </div>
        <div class="overall-score">综合评分：<strong>{{ currentAiResult.overall }}</strong> / 5</div>
        <div v-if="currentAiResult.comment" class="ai-comment">📝 {{ currentAiResult.comment }}</div>
        <div v-if="currentAiResult.error" class="ai-error">⚠️ {{ currentAiResult.error }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import { listQaHistory, evaluateAnswer, autoEvaluateAnswer, fetchEvaluationStats } from '@/api/qa'
import { relativeTime } from '@/utils/format'

const loading = ref(false)
const historyList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const evalStats = ref(null)
const aiDialogVisible = ref(false)
const currentAiResult = ref(null)

const loadAll = () => { loadHistory(); loadStats() }

const loadHistory = async () => {
  loading.value = true
  try {
    const data = await listQaHistory({ page: page.value, size: size.value })
    const rawList = data?.list || []
    historyList.value = rawList.map(it => {
      if (typeof it.citations === 'string') { try { it.citations = JSON.parse(it.citations) } catch { it.citations = [] } }
      if (!Array.isArray(it.citations)) it.citations = []
      it._rated = it.rating != null
      it._aiEval = false
      it._aiResult = null
      it._manualRating = it.rating ?? 0
      it._manualUseful = it.useful ?? null
      it._manualFeedback = it.feedback ?? ''
      return it
    })
    total.value = data.total ?? historyList.value.length
  } catch { historyList.value = []; total.value = 0 }
  finally { loading.value = false }
}

const loadStats = async () => {
  try { evalStats.value = await fetchEvaluationStats() } catch { evalStats.value = null }
}

const onAiEvaluate = async (row) => {
  row._aiEval = true
  try {
    const result = await autoEvaluateAnswer(row.id)
    row._aiResult = result
    currentAiResult.value = result
    aiDialogVisible.value = true
    if (result.overall != null) {
      row.rating = Math.round(result.overall)
      row._rated = true
      row._manualRating = row.rating
    }
    if (result.comment) row._manualFeedback = '[AI评测] ' + result.comment
    ElMessage.success('AI评测完成')
    loadStats()
  } catch (e) {
    ElMessage.error('AI评测失败：' + (e?.message || '网络错误'))
  } finally { row._aiEval = false }
}

const submitManual = async (row) => {
  try {
    await evaluateAnswer(row.id, {
      rating: row._manualRating || undefined,
      useful: row._manualUseful,
      feedback: row._manualFeedback || undefined
    })
    if (row._manualRating > 0) { row.rating = row._manualRating; row._rated = true }
    row.useful = row._manualUseful
    row.feedback = row._manualFeedback
    ElMessage.success('评分已提交')
    loadStats()
  } catch { /* 拦截器已提示 */ }
}

const barPercent = (star) => {
  if (!evalStats.value?.totalEvaluated) return 0
  return Math.round((evalStats.value.distribution?.[star] || 0) / evalStats.value.totalEvaluated * 100)
}
const barColor = (star) => star >= 4 ? '#67C23A' : star === 3 ? '#E6A23C' : '#F56C6C'

onMounted(loadAll)
</script>

<style scoped>
.eval-page { max-width: 1200px; margin: 0 auto; padding: 0 8px; }
.page-title { margin: 0 0 16px; font-size: 22px; color: #303133; font-weight: 700; }
.card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 12px; }

.stat-card { background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%); border-radius: 8px; padding: 20px; text-align: center; margin-bottom: 16px; }
.stat-num { font-size: 28px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }

.rating-bars { display: flex; flex-direction: column; gap: 6px; }
.bar-row { display: flex; align-items: center; gap: 8px; }
.bar-star { width: 48px; font-size: 12px; color: #e6a23c; text-align: right; flex-shrink: 0; }
.bar-count { width: 24px; font-size: 12px; color: #909399; text-align: right; flex-shrink: 0; }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-row :deep(.el-progress-bar__outer) { border-radius: 4px; background: #ebeef5; }

.no-rate { color: #c0c4cc; font-size: 13px; }

.result-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.result-item { display: flex; align-items: center; gap: 8px; }
.dim-label { width: 48px; font-size: 13px; color: #606266; flex-shrink: 0; }
.dim-score { font-weight: 700; color: #409EFF; width: 24px; text-align: center; }
.overall-score { margin-top: 16px; padding: 10px; background: #ecf5ff; border-radius: 6px; text-align: center; font-size: 16px; }
.ai-comment { margin-top: 12px; padding: 10px; background: #f0f9eb; border-radius: 6px; color: #67c23a; font-size: 14px; }
.ai-error { margin-top: 12px; padding: 10px; background: #fef0f0; border-radius: 6px; color: #f56c6c; font-size: 14px; }

.manual-eval { display: flex; flex-direction: column; }
.eval-row { display: flex; align-items: center; gap: 8px; }

.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
