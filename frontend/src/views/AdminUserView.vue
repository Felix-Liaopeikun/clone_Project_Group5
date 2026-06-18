<template>
  <div class="page-container">
    <h2 class="page-title">用户管理</h2>

    <el-card shadow="never">
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="160">
          <template #default="{ row }">
            <div class="username-cell">
              <el-icon><User /></el-icon>
              <span>{{ row.username }}</span>
              <el-tag v-if="row.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="当前角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="180" />
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              style="width: 120px"
              @change="onRoleChange(row, $event)"
              :disabled="row.id === currentUserId"
            >
              <el-option value="USER" label="普通用户" />
              <el-option value="ADMIN" label="管理员" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && users.length === 0" description="暂无用户数据" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { listUsers, updateUserRole } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const currentUserId = auth.userId
const users = ref([])
const loading = ref(false)

const loadUsers = async () => {
  loading.value = true
  try {
    users.value = await listUsers()
  } catch (e) {
    // 403/401 错误已在 request.js 拦截器处理
    users.value = []
  } finally {
    loading.value = false
  }
}

const onRoleChange = async (row, newRole) => {
  if (row.role === newRole) return
  const action = newRole === 'ADMIN' ? '设为管理员' : '降为普通用户'
  try {
    await ElMessageBox.confirm(
      `确定将用户「${row.username}」${action}吗？`,
      '确认操作',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await updateUserRole(row.id, newRole)
    row.role = newRole
    ElMessage.success(`已${action}`)
  } catch (e) {
    // 错误已在拦截器处理
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
.username-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
