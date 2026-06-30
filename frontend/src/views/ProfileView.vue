<template>
  <div class="page-container profile-page">
    <h2 class="page-title">🎀 个人中心</h2>

    <!-- 用户信息卡片 -->
    <div class="section-card user-info-card">
      <div class="user-avatar-large">
        <el-avatar :size="72" :style="{ background: 'var(--primary-color)' }">
          <span style="font-size:28px">{{ auth.username?.charAt(0)?.toUpperCase() }}</span>
        </el-avatar>
      </div>
      <div class="user-meta">
        <div class="username-display">{{ auth.username }}</div>
        <el-tag :type="auth.isAdmin ? 'danger' : 'info'" size="small" effect="plain">
          {{ auth.isAdmin ? '管理员' : '普通用户' }}
        </el-tag>
      </div>
    </div>

    <!-- 画风切换 -->
    <div class="section-card theme-card">
      <div class="card-header">
        <span class="card-title">🎨 界面画风</span>
        <span class="card-hint">选择你喜欢的视觉风格</span>
      </div>
      <div class="theme-options">
        <div
          class="theme-option"
          :class="{ active: theme.current === 'cute' }"
          @click="theme.setTheme('cute')"
        >
          <div class="theme-preview cute-preview">
            <span class="preview-emoji">🌸</span>
          </div>
          <div class="theme-label">
            <span class="theme-name">可爱风</span>
            <span class="theme-desc">暖粉色调 · 圆润柔和</span>
          </div>
          <el-icon v-if="theme.current === 'cute'" class="check-icon" color="var(--primary-color)">
            <CircleCheckFilled />
          </el-icon>
        </div>

        <div
          class="theme-option"
          :class="{ active: theme.current === 'simple' }"
          @click="theme.setTheme('simple')"
        >
          <div class="theme-preview simple-preview">
            <span class="preview-emoji">◻️</span>
          </div>
          <div class="theme-label">
            <span class="theme-name">简约风</span>
            <span class="theme-desc">冷灰色调 · 干净利落</span>
          </div>
          <el-icon v-if="theme.current === 'simple'" class="check-icon" color="var(--primary-color)">
            <CircleCheckFilled />
          </el-icon>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="section-card actions-card">
      <div class="card-header">
        <span class="card-title">⚙️ 快捷操作</span>
      </div>
      <div class="action-list">
        <div class="action-item" @click="$router.push('/change-password')">
          <el-icon><Lock /></el-icon>
          <span>修改密码</span>
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="action-item danger" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { CircleCheckFilled, Lock, SwitchButton, ArrowRight } from '@element-plus/icons-vue'

const auth = useAuthStore()
const theme = useThemeStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.profile-page {
  max-width: 520px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

/* 用户信息 */
.user-info-card {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.username-display {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 通用卡片头部 */
.card-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-hint {
  font-size: 13px;
  color: var(--text-muted);
}

/* 画风选择 */
.theme-options {
  display: flex;
  gap: 12px;
}

.theme-option {
  flex: 1;
  border: 2px solid var(--card-border);
  border-radius: var(--border-radius);
  padding: 16px;
  cursor: pointer;
  position: relative;
  transition: border-color var(--transition-speed), box-shadow var(--transition-speed);
  background: var(--card-bg);
}

.theme-option:hover {
  border-color: var(--primary-color);
}

.theme-option.active {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--primary-light);
}

.theme-preview {
  width: 100%;
  height: 60px;
  border-radius: calc(var(--border-radius) - 4px);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.cute-preview {
  background: linear-gradient(135deg, #ffe0e8, #ffc9d8);
}

.simple-preview {
  background: linear-gradient(135deg, #e8eaed, #d2d6dc);
}

.preview-emoji {
  font-size: 28px;
}

.theme-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.theme-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.theme-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.check-icon {
  position: absolute;
  top: 10px;
  right: 10px;
  font-size: 20px;
}

/* 操作列表 */
.action-list {
  display: flex;
  flex-direction: column;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px;
  border-radius: var(--border-radius);
  cursor: pointer;
  color: var(--text-primary);
  font-size: 14px;
  transition: background var(--transition-speed);
}

.action-item:hover {
  background: var(--primary-light);
}

.action-item.danger {
  color: #f56c6c;
}

.action-item.danger:hover {
  background: #fef0f0;
}

.action-arrow {
  margin-left: auto;
  font-size: 14px;
  color: var(--text-muted);
}
</style>
