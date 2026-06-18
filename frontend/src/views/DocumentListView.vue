<template>
  <div class="page-container">
    <h2 class="page-title">文档管理</h2>

    <!-- 上传 + 工具栏 -->
    <div class="section-card">
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="true"
        :http-request="customUpload"
        :show-file-list="false"
        :before-upload="beforeUpload"
        accept=".pdf,.txt,.md,.docx"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 pdf / txt / md / docx，单文件 ≤ 20MB
          </div>
        </template>
      </el-upload>

      <div class="toolbar" style="margin-top:16px">
        <el-input
          v-model="filters.keyword"
          placeholder="按标题搜索"
          clearable
          style="width:240px"
          @keyup.enter="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="filters.category"
          placeholder="全部分类"
          clearable
          style="width:180px"
          @change="onSearch"
        >
          <el-option
            v-for="c in categories"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        <div class="flex-grow" />
        <el-button :icon="RefreshRight" @click="loadList" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="section-card">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        border
        style="width:100%"
        empty-text="暂无文档，请先上传"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a class="link" @click="goDetail(row.id)">{{ row.title }}</a>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="文件类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType?.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.category || '未分类' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <DocumentStatusTag :status="row.status" />
            <el-tooltip v-if="row.status === 'failed' && row.errorMsg" :content="row.errorMsg" placement="top">
              <el-icon class="error-icon"><WarningFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="goDetail(row.id)">查看</el-button>
            <el-popconfirm
              title="确认删除该文档？相关分块也会一并删除"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="onDelete(row)"
            >
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  Refresh,
  RefreshRight,
  View,
  Delete,
  UploadFilled,
  WarningFilled
} from '@element-plus/icons-vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { listDocuments, listCategories, uploadDocument, deleteDocument, getDocument } from '@/api/document'
import { formatFileSize, formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const categories = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const filters = reactive({ keyword: '', category: '' })

// 轮询控制器：Map<docId, intervalId>
const pollingMap = new Map()

const loadList = async () => {
  loading.value = true
  try {
    const data = await listDocuments({
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword || undefined,
      category: filters.category || undefined
    })
    list.value = data.list || []
    pagination.total = data.total ?? list.value.length
    // 表格里有 pending/processing 的，启动轮询
    syncPolling()
  } catch (e) {
    list.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const cats = await listCategories()
    categories.value = Array.isArray(cats) ? cats : []
  } catch (e) {
    categories.value = []
  }
}

const onSearch = () => {
  pagination.page = 1
  loadList()
}

const resetSearch = () => {
  filters.keyword = ''
  filters.category = ''
  pagination.page = 1
  loadList()
}

const goDetail = (id) => router.push(`/documents/${id}`)

const onDelete = async (row) => {
  try {
    await deleteDocument(row.id)
    ElMessage.success(`已删除：${row.title}`)
    stopPolling(row.id)
    loadList()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 上传 ----------
const beforeUpload = (file) => {
  const allowed = ['pdf', 'txt', 'md', 'docx']
  const ext = file.name.split('.').pop().toLowerCase()
  if (!allowed.includes(ext)) {
    ElMessage.error(`不支持的文件类型: .${ext}`)
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件超过 20MB')
    return false
  }
  return true
}

const customUpload = async (options) => {
  try {
    const res = await uploadDocument(options.file)
    ElMessage.success(`上传成功！文档 ID = ${res.id}（${res.status}）`)
    // 立刻刷新列表 + 启动该 doc 的轮询
    await loadList()
    if (res.status === 'pending' || res.status === 'processing') {
      startPolling(res.id)
    }
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 轮询 ----------
const startPolling = (id) => {
  if (pollingMap.has(id)) return
  const timer = setInterval(async () => {
    try {
      const doc = await getDocument(id)
      if (doc.status !== 'pending' && doc.status !== 'processing') {
        ElMessage[doc.status === 'done' ? 'success' : 'error'](
          `文档 #${id} ${doc.status === 'done' ? '处理完成' : '处理失败'}`
        )
        stopPolling(id)
        loadList()
      }
    } catch (e) {
      // 失败继续轮询（网络抖动），但避免无限重试
    }
  }, 3000)
  pollingMap.set(id, timer)
}

const stopPolling = (id) => {
  const timer = pollingMap.get(id)
  if (timer) {
    clearInterval(timer)
    pollingMap.delete(id)
  }
}

const syncPolling = () => {
  // 停止已不在列表中、或状态已结束的轮询
  for (const id of [...pollingMap.keys()]) {
    const doc = list.value.find((d) => d.id === id)
    if (!doc || (doc.status !== 'pending' && doc.status !== 'processing')) {
      stopPolling(id)
    }
  }
  // 给当前 pending/processing 的行开启轮询
  for (const doc of list.value) {
    if ((doc.status === 'pending' || doc.status === 'processing') && !pollingMap.has(doc.id)) {
      startPolling(doc.id)
    }
  }
}

onMounted(() => {
  loadList()
  loadCategories()
})

onBeforeUnmount(() => {
  for (const id of pollingMap.keys()) stopPolling(id)
})
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
.upload-area :deep(.el-upload-dragger) {
  padding: 24px;
}
.link {
  color: #409eff;
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
.error-icon {
  color: #f56c6c;
  margin-left: 4px;
  vertical-align: middle;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>