<template>
  <div class="chat-shell" :class="{ 'sidebar-open': showSidebar }">
    <!-- 顶部工具栏 -->
    <header class="chat-toolbar">
      <div class="toolbar-left">
        <h2 class="chat-title">智能问答</h2>
      </div>
      <div class="toolbar-center">
        <el-select v-model="selectedModel" size="small" style="width:180px" placeholder="选择模型">
          <template #prefix><el-icon><Cpu /></el-icon></template>
          <el-option v-for="m in modelList" :key="m.name" :label="m.name" :value="m.name">
            <div class="model-opt"><span class="model-name">{{ m.name }}</span><span class="model-provider">{{ m.provider }}</span></div>
          </el-option>
        </el-select>
        <el-select v-model="topK" size="small" style="width:90px">
          <el-option :value="3" label="top 3" />
          <el-option :value="5" label="top 5" />
          <el-option :value="10" label="top 10" />
        </el-select>
        <el-switch v-model="streamMode" size="small" active-text="流式" inactive-text="普通" />
        <el-button link size="small" type="warning" @click="newConversation">新对话</el-button>
      </div>
      <div class="toolbar-right">
        <el-button link @click="showSidebar = !showSidebar">
          <el-icon :size="18"><component :is="showSidebar ? Fold : Expand" /></el-icon>
        </el-button>
      </div>
    </header>

    <div class="chat-body">
      <!-- 消息列表 -->
      <main class="chat-main" ref="chatMainRef">
        <div v-if="messages.length === 0" class="chat-welcome">
          <div class="welcome-icon">🤖</div>
          <h3>知识库智能问答</h3>
          <p>基于已上传的文档，向 AI 提问获取精准回答</p>
          <div class="welcome-hints">
            <span v-for="h in hints" :key="h" class="hint-chip" @click="quickAsk(h)">{{ h }}</span>
          </div>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
          <div class="msg-bubble" :class="msg.role">
            <div class="msg-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🤖</span>
            </div>
            <div class="msg-body">
              <div class="msg-content" v-html="renderContent(msg)" />
              <div v-if="msg.role === 'assistant' && msg.citations?.length" class="msg-citations">
                <div class="cite-header" @click="msg._showCites = !msg._showCites">
                  📎 引用来源 ({{ msg.citations.length }})
                  <el-icon class="cite-arrow" :class="{ open: msg._showCites }"><ArrowDown /></el-icon>
                </div>
                <div v-if="msg._showCites" class="cite-list">
                  <CitationItem v-for="(c, ci) in msg.citations" :key="ci" :citation="c" />
                </div>
              </div>
              <div v-if="msg.role === 'assistant' && idx === messages.length - 1 && asking && streamMode" class="msg-typing">
                <span class="typing-dot" />
              </div>
              <div class="msg-meta">
                <span v-if="msg.createdAt" class="msg-time">{{ formatTime(msg.createdAt) }}</span>
                <el-button v-if="msg.role === 'user'" link size="small" @click="reuseMsg(msg)">重新提问</el-button>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- 右侧面板 -->
      <aside class="chat-sidebar" v-show="showSidebar">
        <!-- 评测统计 -->
        <div v-if="evalStats" class="side-card eval-card">
          <div class="side-card-title">📊 质量评测</div>
          <div class="stats-grid">
            <div class="stat-item"><span class="stat-num">{{ evalStats.totalEvaluated }}</span><span class="stat-label">已评测</span></div>
            <div class="stat-item"><span class="stat-num">{{ evalStats.avgRating || 0 }}</span><span class="stat-label">平均分</span></div>
            <div class="stat-item"><span class="stat-num">{{ evalStats.usefulRate || 0 }}%</span><span class="stat-label">有用率</span></div>
          </div>
          <div class="rating-bars" v-if="evalStats?.distribution">
            <div v-for="i in 5" :key="i" class="bar-row">
              <span class="bar-star">{{ '★'.repeat(i) }}</span>
              <el-progress :percentage="barPercent(i)" :stroke-width="6" :color="barColor(i)" />
              <span class="bar-count">{{ evalStats.distribution?.[i] || 0 }}</span>
            </div>
          </div>
        </div>

        <!-- 历史记录 -->
        <div class="side-card">
          <div class="side-card-title">📋 历史问答</div>
          <div v-if="historyNeedsLogin" class="side-login-prompt">
            <p>登录后可查看历史</p>
            <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
          </div>
          <el-empty v-else-if="!historyLoading && historyList.length === 0" description="暂无记录" :image-size="60" />
          <el-scrollbar v-else class="history-scroll">
            <div v-for="item in historyList" :key="item.id" class="history-item" @click="loadConversation(item)">
              <div class="history-q-text">{{ item.question }}</div>
              <div class="history-q-answer">{{ item.answer?.substring(0, 60) }}{{ item.answer?.length > 60 ? '…' : '' }}</div>
              <div class="history-q-meta">
                <span>{{ relativeTime(item.createdAt) }}</span>
                <el-rate v-if="item.rating" :model-value="item.rating" :max="5" disabled size="small" show-score style="display:inline-flex;height:14px" />
              </div>
            </div>
          </el-scrollbar>
          <div v-if="historyTotal > historySize" class="pagination-wrap">
            <el-pagination
              v-model:current-page="historyPage" v-model:page-size="historySize"
              :total="historyTotal" :page-sizes="[10, 20]"
              layout="total, prev, next" background small
              @size-change="loadHistory" @current-change="loadHistory"
            />
          </div>
        </div>
      </aside>
    </div>

    <!-- 输入区域 -->
    <footer class="chat-input-bar">
      <div class="input-wrapper">
        <el-input
          v-model="question"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 5 }"
          placeholder="输入你的问题… (Enter 发送，Shift+Enter 换行)"
          :maxlength="2000"
          :disabled="asking"
          @keydown.enter.exact.prevent="onAsk"
          resize="none"
          class="chat-input"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="asking"
          :disabled="!question.trim() || asking"
          @click="onAsk"
          class="send-btn"
        >
          发送
        </el-button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Cpu, ArrowDown, Fold, Expand } from '@element-plus/icons-vue'
import CitationItem from '@/components/CitationItem.vue'
import { askQuestion, askQuestionStream, listQaHistory, evaluateAnswer, fetchEvaluationStats, deleteQaHistory } from '@/api/qa'
import { listModels } from '@/api/model'
import { relativeTime } from '@/utils/format'

/* ========== 对话状态 ========== */
const question = ref('')
const messages = ref([])
const asking = ref(false)
const streamMode = ref(true)
const conversationId = ref(generateUUID())
const showSidebar = ref(true)
const chatMainRef = ref(null)
let cancelStream = null

const hints = ['这份文档的核心观点是什么？', '总结文档的主要内容', '文档中提到了哪些关键概念？', '作者的主要论点有哪些？']

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

const topK = ref(5)
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

/* ========== 消息渲染 ========== */
function renderContent(msg) {
  let text = msg.content || ''
  text = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  return text
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

/* ========== 提问 ========== */
const onAsk = async () => {
  const q = question.value.trim()
  if (!q || asking.value) return
  question.value = ''

  // 添加用户消息
  const userMsg = { role: 'user', content: q, createdAt: new Date().toISOString() }
  messages.value.push(userMsg)

  // 添加空的 AI 消息
  const aiMsg = { role: 'assistant', content: '', citations: [], _showCites: false, createdAt: null }
  messages.value.push(aiMsg)

  asking.value = true
  scrollToBottom()

  const payload = { question: q, topK: topK.value, model: selectedModel.value || undefined, conversationId: conversationId.value }

  if (streamMode.value) {
    cancelStream = askQuestionStream(payload, {
      onToken(t) {
        aiMsg.content += t
        scrollToBottom()
      },
      onDone() {
        aiMsg.createdAt = new Date().toISOString()
        asking.value = false
        loadHistory()
        scrollToBottom()
      },
      onError(err) {
        aiMsg.content = '⚠️ 请求失败：' + (err?.message || '网络错误')
        aiMsg.createdAt = new Date().toISOString()
        asking.value = false
      },
    })
  } else {
    try {
      const result = await askQuestion(payload)
      aiMsg.content = result.answer || ''
      if (result.citations) {
        aiMsg.citations = Array.isArray(result.citations) ? result.citations : []
      }
      aiMsg.createdAt = result.createdAt || new Date().toISOString()
      loadHistory()
      scrollToBottom()
    } catch {
      aiMsg.content = '⚠️ 请求失败，请重试'
      aiMsg.createdAt = new Date().toISOString()
    } finally {
      asking.value = false
    }
  }
}

const quickAsk = (hint) => {
  question.value = hint
  onAsk()
}

const reuseMsg = (msg) => {
  question.value = msg.content
  chatMainRef.value?.scrollTo({ top: 99999, behavior: 'smooth' })
}

const newConversation = () => {
  if (asking.value && cancelStream) { cancelStream(); cancelStream = null; asking.value = false }
  conversationId.value = generateUUID()
  messages.value = []
  ElMessage.success('已开启新对话')
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatMainRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onUnmounted(() => { if (cancelStream) cancelStream() })

/* ========== 历史记录 ========== */
const historyLoading = ref(false)
const historyList = ref([])
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)
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
      return it
    })
    historyTotal.value = data.total ?? historyList.value.length
  } catch (e) {
    if (e?.response?.status === 401 || e?.status === 401) historyNeedsLogin.value = true
    else { historyList.value = []; historyTotal.value = 0 }
  } finally { historyLoading.value = false }
}

const loadConversation = (item) => {
  // 把历史记录加载到当前对话面板
  if (!item.question) return
  messages.value = [
    { role: 'user', content: item.question, createdAt: item.createdAt },
    { role: 'assistant', content: item.answer || '', citations: item.citations || [], _showCites: !!(item.citations?.length), createdAt: item.createdAt }
  ]
  conversationId.value = item.conversationId || generateUUID()
  scrollToBottom()
}

/* ========== 评测 ========== */
const evalStats = ref(null)
const loadStats = async () => {
  try { evalStats.value = await fetchEvaluationStats() } catch { evalStats.value = null }
}
const barPercent = (star) => {
  if (!evalStats.value?.totalEvaluated) return 0
  return Math.round((evalStats.value.distribution?.[star] || 0) / evalStats.value.totalEvaluated * 100)
}
const barColor = (star) => star >= 4 ? '#67C23A' : star === 3 ? '#E6A23C' : '#F56C6C'

onMounted(() => { loadModels(); loadHistory(); loadStats() })
</script>

<style scoped>
/* ========== 整体布局 ========== */
.chat-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  max-width: 1400px;
  margin: 0 auto;
  background: #f5f7fa;
}

/* ========== 顶部工具栏 ========== */
.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
  z-index: 10;
}
.chat-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  white-space: nowrap;
}
.toolbar-left { flex-shrink: 0; }
.toolbar-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
  flex-wrap: wrap;
}
.toolbar-right { flex-shrink: 0; }

/* ========== 主体区域 ========== */
.chat-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ========== 消息区 ========== */
.chat-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  scroll-behavior: smooth;
}

/* 欢迎界面 */
.chat-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
  padding: 60px 20px;
}
.welcome-icon { font-size: 56px; margin-bottom: 16px; }
.chat-welcome h3 { font-size: 22px; color: #303133; margin: 0 0 8px; }
.chat-welcome p { font-size: 14px; color: #909399; margin: 0 0 24px; }
.welcome-hints { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; max-width: 500px; }
.hint-chip {
  display: inline-block;
  padding: 6px 14px;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background .2s;
}
.hint-chip:hover { background: #d9ecff; }

/* ========== 消息行 ========== */
.msg-row { display: flex; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { justify-content: flex-start; }

.msg-bubble {
  display: flex;
  gap: 10px;
  max-width: 78%;
  min-width: 120px;
}
.msg-bubble.user { flex-direction: row-reverse; }

.msg-avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: #f0f2f5;
  margin-top: 4px;
}
.msg-bubble.user .msg-avatar { background: #409eff; }

.msg-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.msg-content {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}
.msg-bubble.assistant .msg-content {
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
  border-top-left-radius: 4px;
}
.msg-bubble.user .msg-content {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
}
.msg-time { font-size: 11px; color: #b0b8c1; }
.msg-bubble.user .msg-meta { justify-content: flex-end; }

/* 引用 */
.msg-citations { margin-top: 6px; }
.cite-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  user-select: none;
  padding: 4px 0;
}
.cite-arrow { transition: transform .2s; font-size: 12px; }
.cite-arrow.open { transform: rotate(180deg); }
.cite-list { margin-top: 6px; display: flex; flex-direction: column; gap: 6px; }

/* 流式光标 */
.msg-typing { display: flex; align-items: center; gap: 4px; padding: 0 4px; }
.typing-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #409eff;
  animation: blink 1s infinite;
}
@keyframes blink { 0%,100% { opacity: 1 } 50% { opacity: 0.2 } }

/* ========== 侧边栏 ========== */
.chat-sidebar {
  width: 300px;
  flex-shrink: 0;
  border-left: 1px solid #e4e7ed;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-card {
  padding: 14px;
  border-bottom: 1px solid #ebeef5;
}
.side-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}
.history-scroll { max-height: 320px; }

/* 评测卡片 */
.eval-card { background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%); }
.stats-grid { display: flex; justify-content: space-around; margin-bottom: 10px; }
.stat-item { text-align: center; }
.stat-num { display: block; font-size: 22px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 11px; color: #909399; }
.rating-bars { display: flex; flex-direction: column; gap: 4px; }
.bar-row { display: flex; align-items: center; gap: 6px; }
.bar-star { width: 40px; font-size: 11px; color: #e6a23c; text-align: right; flex-shrink: 0; }
.bar-count { width: 20px; font-size: 11px; color: #909399; text-align: right; flex-shrink: 0; }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-row :deep(.el-progress-bar__outer) { border-radius: 3px; background: #d9dce1; }

/* 历史项 */
.history-item {
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background .15s;
}
.history-item:hover { background: #f0f5ff; }
.history-q-text { font-size: 13px; font-weight: 500; color: #303133; margin-bottom: 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.history-q-answer { font-size: 12px; color: #909399; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-q-meta { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #b0b8c1; }
.side-login-prompt { display: flex; flex-direction: column; align-items: center; padding: 20px 0; gap: 8px; color: #909399; font-size: 13px; }
.pagination-wrap { display: flex; justify-content: center; padding: 8px 0; }

/* ========== 底部输入 ========== */
.chat-input-bar {
  flex-shrink: 0;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 900px;
  margin: 0 auto;
}
.chat-input {
  flex: 1;
}
.chat-input :deep(.el-textarea__inner) {
  border-radius: 10px;
  font-size: 15px;
  line-height: 1.5;
  padding: 10px 14px;
  resize: none;
}
.send-btn {
  flex-shrink: 0;
  border-radius: 10px;
  padding: 10px 20px;
  font-size: 15px;
}

/* 模型选择器 */
.model-opt { display: flex; align-items: center; gap: 6px; width: 100%; }
.model-name { font-weight: 500; color: #303133; font-size: 13px; }
.model-provider { font-size: 11px; color: #909399; margin-left: auto; }

/* 响应式 */
@media (max-width: 768px) {
  .chat-sidebar { display: none; }
  .chat-shell.sidebar-open .chat-sidebar { display: flex; position: fixed; right: 0; top: 60px; bottom: 0; z-index: 20; box-shadow: -2px 0 8px rgba(0,0,0,.1); }
  .msg-bubble { max-width: 90%; }
}
</style>
