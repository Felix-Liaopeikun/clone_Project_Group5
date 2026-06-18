<template>
  <el-container class="main-layout">
    <el-header class="layout-header">
      <div class="nav-title">
        <el-icon><Document /></el-icon>
        <span>文档摘要与知识库</span>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="activeMenu"
        :ellipsis="false"
        router
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#ffd04b"
      >
        <el-menu-item index="/">
          <el-icon><House /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><Folder /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
        <el-menu-item index="/qa">
          <el-icon><ChatLineRound /></el-icon>
          <span>智能问答</span>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/users">
          <el-icon><Setting /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
      <div class="header-right">
        <el-tag :type="health.ok ? 'success' : 'danger'" size="small">
          {{ health.ok ? '后端在线' : '后端离线' }}
        </el-tag>

        <!-- 已登录 -->
        <template v-if="auth.isLoggedIn">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-avatar">
              <el-icon><Avatar /></el-icon>
              <span class="username">{{ auth.username }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <router-link to="/change-password" custom #default="{ navigate }">
                  <el-dropdown-item @click="navigate">
                    <el-icon><Lock /></el-icon>
                    修改密码
                  </el-dropdown-item>
                </router-link>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <!-- 未登录 -->
        <template v-else>
          <el-button size="small" @click="$router.push('/login')">登录</el-button>
          <el-button size="small" type="primary" @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>
    <el-main class="layout-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, House, Folder, ChatLineRound, Avatar, ArrowDown, SwitchButton, Setting, Lock } from '@element-plus/icons-vue'
import { checkHealth } from '@/api/health'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => {
  // 详情页仍高亮"文档管理"
  if (route.path.startsWith('/documents')) return '/documents'
  if (route.path.startsWith('/qa')) return '/qa'
  if (route.path.startsWith('/admin')) return '/admin/users'
  return route.path
})

const health = reactive({ ok: false, info: null })
let healthTimer = null

const refreshHealth = async () => {
  try {
    const data = await checkHealth()
    health.info = data
    health.ok = data?.status === 'up'
  } catch (e) {
    health.ok = false
  }
}

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

onMounted(() => {
  refreshHealth()
  healthTimer = setInterval(refreshHealth, 30000)
})

onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}
.layout-header {
  display: flex;
  align-items: center;
  background-color: #001529;
  padding: 0 20px;
  height: 60px;
}
.layout-header :deep(.el-menu) {
  border-bottom: none;
  flex: 1;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-avatar {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #bfcbd9;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
  font-size: 14px;
}
.user-avatar:hover {
  background: rgba(255, 255, 255, 0.1);
}
.user-avatar .arrow {
  font-size: 12px;
}
.layout-main {
  background: var(--app-bg);
  padding: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>