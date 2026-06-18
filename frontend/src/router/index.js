import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 路由表（按 CONTRACT.md 章节 8）
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', guest: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', guest: true }
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/ChangePasswordView.vue'),
    meta: { title: '修改密码' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '系统概览' }
      },
      {
        path: 'documents',
        name: 'documents',
        component: () => import('@/views/DocumentListView.vue'),
        meta: { title: '文档管理' }
      },
      {
        path: 'documents/:id',
        name: 'document-detail',
        component: () => import('@/views/DocumentDetailView.vue'),
        meta: { title: '文档详情' },
        props: true
      },
      {
        path: 'qa',
        name: 'qa',
        component: () => import('@/views/QaView.vue'),
        meta: { title: '智能问答' }
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/AdminUserView.vue'),
        meta: { title: '用户管理', admin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ---------- 导航守卫 ----------
router.beforeEach((to, from) => {
  const auth = useAuthStore()

  // guest 路由（login/register）：已登录则跳首页
  if (to.meta?.guest && auth.isLoggedIn) {
    return '/'
  }

  // 修改密码页：必须已登录
  if (to.name === 'change-password' && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // admin 路由：未登录跳转登录页；非管理员跳转首页
  if (to.meta?.admin) {
    if (!auth.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (!auth.isAdmin) {
      return '/'
    }
  }

  return true
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - 文档摘要与知识库`
  } else {
    document.title = '文档摘要与知识库'
  }
})

export default router