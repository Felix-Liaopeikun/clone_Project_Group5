<template>
  <div class="page-container">
    <h2 class="page-title">系统概览</h2>

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="12" :md="8">
        <div class="stat-card">
          <div class="icon" style="background:#409EFF"><el-icon><Document /></el-icon></div>
          <div>
            <div class="value">{{ stats.totalDocs }}</div>
            <div class="label">文档总数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <div class="stat-card">
          <div class="icon" style="background:#67C23A"><el-icon><Folder /></el-icon></div>
          <div>
            <div class="value">{{ stats.categoryCount }}</div>
            <div class="label">分类数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <div class="stat-card">
          <div class="icon" style="background:#E6A23C"><el-icon><ChatLineRound /></el-icon></div>
          <div>
            <div class="value">{{ stats.totalQa }}</div>
            <div class="label">问答历史</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <div class="section-card">
          <div class="card-title">分类分布</div>
          <EChart v-if="pieOption" :option="pieOption" height="340px" />
          <div v-else class="empty-block">暂无分类数据，请先上传文档</div>
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="section-card">
          <div class="card-title">最近文档</div>
          <el-empty v-if="recent.length === 0" description="暂无文档" />
          <el-scrollbar v-else height="340px">
            <div
              v-for="doc in recent"
              :key="doc.id"
              class="recent-item"
              @click="$router.push(`/documents/${doc.id}`)"
            >
              <div class="recent-title">
                <el-icon><Document /></el-icon>
                <span>{{ doc.title }}</span>
              </div>
              <div class="recent-meta">
                <el-tag size="small" type="info" effect="plain">{{ doc.category || '未分类' }}</el-tag>
                <DocumentStatusTag :status="doc.status" />
                <span class="recent-time">{{ relativeTime(doc.createdAt) }}</span>
              </div>
            </div>
          </el-scrollbar>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Document, Folder, ChatLineRound } from '@element-plus/icons-vue'
import EChart from '@/components/EChart.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { listDocuments, listCategories } from '@/api/document'
import { listQaHistory } from '@/api/qa'
import { relativeTime } from '@/utils/format'

const stats = reactive({
  totalDocs: 0,
  categoryCount: 0,
  totalQa: 0
})
const recent = ref([])
const pieOption = ref(null)

const loadAll = async () => {
  // 文档总数 + 最近列表（取 size=10 即可）
  const docResp = await listDocuments({ page: 1, size: 10 })
  stats.totalDocs = docResp.total ?? (docResp.list?.length || 0)
  recent.value = docResp.list || []

  // 分类
  try {
    const cats = await listCategories()
    stats.categoryCount = Array.isArray(cats) ? cats.length : 0
    buildPie(cats)
  } catch (e) {
    // 失败不阻塞
  }

  // 问答历史数（只看 total，size=1 即可）
  try {
    const qa = await listQaHistory({ page: 1, size: 1 })
    stats.totalQa = qa.total ?? 0
  } catch (e) {
    stats.totalQa = 0
  }
}

// 简易方案：用 list 接口的前 50 条按 category 聚合；fallback 时再调 categories 接口
const buildPie = async (categories) => {
  try {
    const big = await listDocuments({ page: 1, size: 50 })
    const counter = {}
    for (const d of big.list || []) {
      const c = d.category || '未分类'
      counter[c] = (counter[c] || 0) + 1
    }
    const data = Object.entries(counter).map(([name, value]) => ({ name, value }))
    if (data.length === 0) {
      pieOption.value = null
      return
    }
    pieOption.value = {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: '分类',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}: {c}' },
          data
        }
      ]
    }
  } catch (e) {
    pieOption.value = null
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
.stat-row {
  margin-bottom: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.recent-item {
  padding: 10px 8px;
  border-bottom: 1px dashed #ebeef5;
  cursor: pointer;
}
.recent-item:hover {
  background: #f5f7fa;
}
.recent-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 6px;
}
.recent-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: #909399;
}
.recent-time {
  margin-left: auto;
}
</style>