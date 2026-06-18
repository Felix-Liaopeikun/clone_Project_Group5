<template>
  <div class="qa-page">
    <h2 class="page-title">智能问答</h2>

    <el-row :gutter="16">
      <!-- 左：提问 + 回答 -->
      <el-col :xs="24" :md="14">
        <div class="card">
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            placeholder="请输入你的问题，例如：这份文档的核心观点是什么？"
            :maxlength="500"
            show-word-limit
          />
          <div class="toolbar">
            <el-select v-model="topK" style="width:100px">
              <el-option :value="3" label="top 3" />
              <el-option :value="5" label="top 5" />
              <el-option :value="10" label="top 10" />
            </el-select>
            <el-select v-model="selectedModel" style="width:180px;margin-left:8px" placeholder="选择模型">
              <template #prefix><el-icon><Cpu /></el-icon></template>
              <el-option v-for="m in modelList" :key="m.name" :label="m.description || m.name" :value="m.name">
                <div class="model-opt"><span class="model-name">{{ m.name }}</span><el-tag v-if="m.active" type="success" size="small">当前</el-tag><span class="model-provider">{{ m.provider }}</span></div>
              </el-option>
            </el-select>
            <el-tooltip content="开启后回答会逐字流式显示">
              <el-switch v-model="streamMode" active-text="流式" inactive-text="普通" style="margin-left:8px" />
            </el-tooltip>
            <div class="flex-grow" />
            <el-button :icon="Delete" @click="question = ''; currentAnswer = null">清空</el-button>
            <el-button type="primary" :icon="Promotion" :loading="asking" :disabled="!question.trim()" @click="onAsk">
              {{ asking ? (streamMode ? '生成中…' : '处理中…') : '提问' }}
            </el-button>
          </div>
        </div>

        <div v-if="currentAnswer || streamText" class="card">
          <div class="card-header">
            <span class="card-title">回答</span>
            <el-tag v-if="streamMode && asking" type="info" size="small"><span class="typing-dot" />流式输出中</el-tag>
            <el-tag v-else-if="!asking && streamMode" type="success" size="small">生成完成</el-tag>
          </div>
          <div v-if="streamMode" class="qa-answer qa-stream" v-html="streamHtml" />
          <div v-else-if="currentAnswer?.answer" class="qa-answer">{{ currentAnswer.answer }}</div>
          <div v-else class="qa-answer qa-empty">暂无回答内容</div>

          <div class="card-title" style="margin-top:20px">引用来源</div>
          <el-empty v-if="!currentAnswer?.citations?.length" description="该回答未引用任何文档" />
          <div v-else>
            <CitationItem v-for="(c, i) in currentAnswer.citations" :key="i" :citation="c" />
          </div>
        </div>
      </el-col>

      <!-- 右：历史 + 评测统计 -->
      <el-col :xs="24" :md="10">
        <!-- 评测统计卡片 -->
        <div v-if="evalStats" class="card eval-stats-card">
          <div class="card-header"><span class="card-title">📊 质量评测</span></div>
          <div class="stats-grid">
            <div class="stat-item"><span class="stat-num">{{ evalStats.totalEvaluated }}</span><span class="stat-label">已评测</span></div>
            <div class="stat-item"><span class="stat-num">{{ evalStats.avgRating || 0 }}</span><span class="stat-label">平均分</span></div>
            <div class="stat-item"><span class="stat-num">{{ evalStats.usefulRate || 0 }}%</span><span class="stat-label">有用率</span></div>
          </div>
          <div class="rating-bars">
            <div v-for="i in 5" :key="i" class="bar-row">
              <span class="bar-star">{{ '★'.repeat(i) }}</span>
              <el-progress :percentage="barPercent(i)" :stroke-width="8" :color="barColor(i)" />
              <span class="bar-count">{{ evalStats.distribution?.[i] || 0 }}</span>
            </div>
          </div>
        </div>

        <!-- 历史记录卡片 -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">历史问答</span>
            <el-button :icon="RefreshRight" link @click="loadHistory">刷新</el-button>
          </div>
          <div v-if="historyNeedsLogin" class="login-prompt">
            <el-icon :size="32" color="#909399"><Warning /></el-icon>
            <p>登录后可查看问答历史</p>
            <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
          </div>
          <el-empty v-else-if="!historyLoading && historyList.length === 0" description="暂无历史问答" />
          <el-scrollbar v-else height="500px">
            <div v-for="item in historyList" :key="item.id" class="history-item">
              <div class="history-q" @click="toggleExpand(item.id)">
                <el-icon class="history-arrow"><component :is="expanded[item.id] ? 'ArrowDown' : 'ArrowRight'" /></el-icon>
                <span class="history-question">{{ item.question }}</span>
                <span class="history-time">{{ relativeTime(item.createdAt) }}</span>
              </div>
              <div v-if="expanded[item.id]" class="history-detail">
                <div class="qa-answer">{{ item.answer }}</div>
                <div v-if="item.citations?.length" style="margin-top:10px">
                  <div class="card-subtitle">引用</div>
                  <CitationItem v-for="(c, i) in item.citations" :key="i" :citation="c" />
                </div>

                <!-- 评分区域 -->
                <div class="eval-section">
                  <div class="eval-row">
                    <span class="eval-label">评分</span>
                    <el-rate
                      v-model="item._rating"
                      :max="5"
                      :disabled="item._rated"
                      @change="(v) => submitEval(item, { rating: v })"
                    />
                    <el-button
                      v-if="!item._rated"
                      :type="item._useful === true ? 'success' : 'default'"
                      size="small"
                      :icon="Select"
                      @click="submitEval(item, { useful: true })"
                    >有用</el-button>
                    <el-button
                      v-if="!item._rated"
                      :type="item._useful === false ? 'danger' : 'default'"
                      size="small"
                      @click="submitEval(item, { useful: false })"
                    >无用</el-button>
                    <el-tag v-if="item._rated" type="info" size="small">已评测</el-tag>
                  </div>
                  <div v-if="!item._rated" class="eval-feedback">
                    <el-input
                      v-model="item._feedback"
                      size="small"
                      placeholder="补充反馈（可选）"
                      :maxlength="200"
                      show-word-limit
                      @blur="submitEval(item, { feedback: item._feedback })"
                    />
                  </div>
                </div>

                <div class="history-actions">
                  <el-button link type="primary" size="small" @click="reuse(item)">再次提问</el-button>
                </div>
              </div>
            </div>
          </el-scrollbar>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="historyPage" v-model:page-size="historySize"
              :total="historyTotal" :page-sizes="[10, 20]"
              layout="total, sizes, prev, pager, next, jumper" background small
              @size-change="loadHistory" @current-change="loadHistory"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Delete, RefreshRight, Cpu, Warning, Select } from '@element-plus/icons-vue'
import CitationItem from '@/components/CitationItem.vue'
import { askQuestion, askQuestionStream, listQaHistory, evaluateAnswer, fetchEvaluationStats } from '@/api/qa'
import { listModels } from '@/api/model'
import { relativeTime } from '@/utils/format'

const question = ref('')
const topK = ref(5)
const asking = ref(false)
const streamMode = ref(true)
const currentAnswer = ref(null)
const streamText = ref('')
let cancelStream = null

const modelList = ref([])
const selectedModel = ref('')

const loadModels = async () => {
  try {
    const list = await listModels()
    modelList.value = list || []
    const active = list?.find(m => m.active)
    selectedModel.value = active?.name || list?.[0]?.name || ''
  } catch { /* silent */ }
}

const streamHtml = computed(() => {
  return streamText.value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
})

const onAsk = async () => {
  if (!question.value.trim()) return
  if (cancelStream) { cancelStream(); cancelStream = null }
  streamText.value = ''
  currentAnswer.value = null
  asking.value = true
  const payload = { question: question.value.trim(), topK: topK.value, model: selectedModel.value || undefined }
  if (streamMode.value) {
    cancelStream = askQuestionStream(payload, {
      onToken(t) { streamText.value += t },
      onDone() { asking.value = false; ElMessage.success('回答已生成并保存'); loadHistory() },
      onError(err) { asking.value = false; ElMessage.error('流式请求失败：' + err.message) },
    })
  } else {
    try { currentAnswer.value = await askQuestion(payload); ElMessage.success('已生成回答'); loadHistory() }
    catch { /* 拦截器已提示 */ }
    finally { asking.value = false }
  }
}

onUnmounted(() => { if (cancelStream) cancelStream() })

// 历史
const historyLoading = ref(false)
const historyList = ref([])
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)
const expanded = reactive({})
const historyNeedsLogin = ref(false)

const loadHistory = async () => {
  historyLoading.value = true
  historyNeedsLogin.value = false
  try {
    const data = await listQaHistory({ page: historyPage.value, size: historySize.value })
    const rawList = data?.list || []
    historyList.value = rawList.map(it => {
      if (typeof it.citations === 'string') { try { it.citations = JSON.parse(it.citations) } catch { it.citations = [] } }
      if (!Array.isArray(it.citations)) it.citations = []
      it._rating = it.rating ?? 0
      it._useful = it.useful ?? null
      it._feedback = it.feedback ?? ''
      it._rated = it.rating != null || it.useful != null
      return it
    })
    historyTotal.value = data.total ?? historyList.value.length
  } catch (e) {
    if (e?.response?.status === 401 || e?.status === 401) historyNeedsLogin.value = true
    else { historyList.value = []; historyTotal.value = 0 }
  } finally { historyLoading.value = false }
}

const toggleExpand = (id) => { expanded[id] = !expanded[id] }
const reuse = (item) => { question.value = item.question; window.scrollTo({ top: 0, behavior: 'smooth' }) }

// 评测
const evalStats = ref(null)

const loadStats = async () => {
  try { evalStats.value = await fetchEvaluationStats() } catch { evalStats.value = null }
}

const submitEval = async (item, payload) => {
  try {
    await evaluateAnswer(item.id, payload)
    if (payload.rating != null) item._rating = payload.rating
    if (payload.useful != null) item._useful = payload.useful
    if (payload.feedback != null) item._feedback = payload.feedback
    if (payload.rating != null || payload.useful != null) item._rated = true
    loadStats()
  } catch { /* silent */ }
}

const barPercent = (star) => {
  if (!evalStats.value?.totalEvaluated) return 0
  return Math.round((evalStats.value.distribution?.[star] || 0) / evalStats.value.totalEvaluated * 100)
}
const barColor = (star) => star >= 4 ? '#67C23A' : star === 3 ? '#E6A23C' : '#F56C6C'

onMounted(() => { loadModels(); loadHistory(); loadStats() })
</script>

<style scoped>
.qa-page { max-width: 1200px; margin: 0 auto; padding: 0 8px; }
.page-title { margin: 0 0 16px; font-size: 22px; color: #303133; font-weight: 700; }
.card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-subtitle { font-size: 13px; color: #909399; margin: 8px 0 4px; }
.flex-grow { flex: 1; }
.toolbar { display: flex; align-items: center; margin-top: 12px; flex-wrap: wrap; gap: 4px; }
.qa-answer { font-size: 15px; line-height: 1.8; color: #303133; white-space: pre-wrap; word-break: break-word; }
.qa-stream { min-height: 40px; }
.qa-empty { color: #c0c4cc; }
.typing-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #409EFF; animation: blink 1s infinite; margin-right: 4px; }
@keyframes blink { 0%,100% { opacity: 1 } 50% { opacity: 0.3 } }

/* 历史 */
.history-item { border-bottom: 1px solid #ebeef5; padding: 10px 4px; }
.history-q { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.history-arrow { flex-shrink: 0; }
.history-question { flex: 1; font-weight: 500; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-time { font-size: 12px; color: #909399; flex-shrink: 0; }
.history-detail { margin-top: 10px; padding-left: 22px; }
.history-actions { margin-top: 8px; display: flex; justify-content: flex-end; }
.login-prompt { display: flex; flex-direction: column; align-items: center; padding: 40px 0; gap: 12px; color: #909399; }
.login-prompt p { margin: 0; font-size: 14px; }

/* 评测统计卡片 */
.eval-stats-card { background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%); }
.stats-grid { display: flex; justify-content: space-around; margin-bottom: 14px; }
.stat-item { text-align: center; }
.stat-num { display: block; font-size: 24px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 12px; color: #909399; }
.rating-bars { display: flex; flex-direction: column; gap: 6px; }
.bar-row { display: flex; align-items: center; gap: 8px; }
.bar-star { width: 48px; font-size: 12px; color: #e6a23c; text-align: right; flex-shrink: 0; }
.bar-count { width: 24px; font-size: 12px; color: #909399; text-align: right; flex-shrink: 0; }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-row :deep(.el-progress-bar__outer) { border-radius: 4px; background: #ebeef5; }

/* 评测区域 */
.eval-section { margin-top: 12px; padding: 10px; background: #f8f9fb; border-radius: 6px; }
.eval-row { display: flex; align-items: center; gap: 8px; }
.eval-label { font-size: 13px; color: #606266; }
.eval-feedback { margin-top: 8px; }

/* 模型选择器 */
.model-opt { display: flex; align-items: center; gap: 6px; width: 100%; }
.model-name { font-weight: 500; color: #303133; }
.model-provider { font-size: 12px; color: #909399; margin-left: auto; }

.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
