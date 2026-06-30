import { ref, watchEffect } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'app-theme'
const THEMES = ['cute', 'simple']

/**
 * 主题 Store：管理画风切换（可爱风 / 简约风），持久化到 localStorage。
 *
 * 使用方式：
 *   const theme = useThemeStore()
 *   theme.current   // 'cute' | 'simple'
 *   theme.toggle()  // 切换
 */
export const useThemeStore = defineStore('theme', () => {
  const stored = localStorage.getItem(STORAGE_KEY)
  const current = ref(THEMES.includes(stored) ? stored : 'cute')

  /** 切换画风 */
  function toggle() {
    current.value = current.value === 'cute' ? 'simple' : 'cute'
  }

  /** 设置为指定画风 */
  function setTheme(theme) {
    if (THEMES.includes(theme)) {
      current.value = theme
    }
  }

  // 持久化 + 同步到 <html> data-theme 属性
  watchEffect(() => {
    localStorage.setItem(STORAGE_KEY, current.value)
    document.documentElement.setAttribute('data-theme', current.value)
  })

  return { current, toggle, setTheme }
})
