import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { register, login } from '@/api/auth'

const TOKEN_KEY = 'auth_token'
const USER_KEY = 'auth_user'

export const useAuthStore = defineStore('auth', () => {
  // ---------- state ----------
  const token = ref(localStorage.getItem(TOKEN_KEY) || null)
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  // ---------- getters ----------
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const username = computed(() => user.value?.username || null)
  const userId = computed(() => user.value?.id || null)

  // ---------- actions ----------
  /**
   * 注册（注册即登录，返回完整 AuthVO）
   * @param {{ username, password }} credentials
   * @returns {Promise<{ token, userId, username, role }>}
   */
  async function doRegister(credentials) {
    const result = await register(credentials)
    setSession(result)
    return result
  }

  /**
   * 登录
   * @param {{ username, password }} credentials
   * @returns {Promise<{ token, id, username, role }>}
   */
  async function doLogin(credentials) {
    const result = await login(credentials)
    // result = { token, id, username, role }
    setSession(result)
    return result
  }

  /**
   * 写入登录态
   * @param {{ token, userId, username, role }} result 后端 AuthVO
   */
  function setSession(result) {
    token.value = result.token
    user.value = {
      id: result.userId,
      username: result.username,
      role: result.role
    }
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  /**
   * 登出
   */
  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    username,
    userId,
    doRegister,
    doLogin,
    logout
  }
})
