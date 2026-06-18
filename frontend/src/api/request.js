import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const TOKEN_KEY = 'auth_token'

// axios 实例：统一前缀 /api，超时 30s
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------- 请求拦截器 ----------
request.interceptors.request.use(
  (config) => {
    // 自动附加 JWT Token
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    // 文件上传时不主动改 Content-Type，让浏览器自动带 boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (err) => Promise.reject(err)
)

// ---------- 响应拦截器 ----------
// 后端统一返回 { code, message, data }，此处拆包为业务侧只看到 data
request.interceptors.response.use(
  (response) => {
    const payload = response.data

    // 二进制流（文件下载/预览）直接放行
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }

    // 非 Result 包装（理论上不会发生）也直接放行
    if (payload == null || typeof payload !== 'object' || !('code' in payload)) {
      return response
    }

    if (payload.code === 0) {
      // 业务成功，把 data 提出来
      return payload.data
    }

    // 业务错误：弹 toast 并 reject（这样 await 处可以继续 catch）
    ElMessage.error(payload.message || `请求失败 (code=${payload.code})`)
    return Promise.reject(new Error(payload.message || `Business error ${payload.code}`))
  },
  (error) => {
    // HTTP 层错误
    const status = error.response?.status
    const apiMsg = error.response?.data?.message
    let msg = apiMsg || error.message || '网络请求失败'
    if (status === 401) {
      msg = '未登录或登录已过期'
      // 清除本地登录态并跳转登录页
      localStorage.removeItem('auth_token')
      localStorage.removeItem('auth_user')
      router.push('/login')
    } else if (status === 403) {
      msg = '无权访问'
    } else if (status === 404) {
      msg = '资源不存在'
    } else if (status === 413) {
      msg = '文件过大'
    } else if (status >= 500) {
      msg = `服务器错误 (${status})`
    }

    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request