// 文件大小 / 日期格式化工具

/**
 * 字节数 -> 可读字符串
 * @param {number} bytes
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (bytes == null || isNaN(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/**
 * 格式化日期字符串，兼容 "yyyy-MM-dd HH:mm:ss" 与 ISO 8601
 * @param {string|Date|number} input
 * @param {string} fmt 默认 'yyyy-MM-dd HH:mm:ss'
 */
export function formatDateTime(input, fmt = 'yyyy-MM-dd HH:mm:ss') {
  if (!input) return '-'
  let d
  if (input instanceof Date) d = input
  else if (typeof input === 'number') d = new Date(input)
  else {
    // 将 "2026-06-16 17:00:00" 视为本地时间
    const s = String(input).includes('T') ? input : String(input).replace(' ', 'T')
    d = new Date(s)
  }
  if (isNaN(d.getTime())) return String(input)

  const pad = (n) => String(n).padStart(2, '0')
  return fmt
    .replace('yyyy', d.getFullYear())
    .replace('MM', pad(d.getMonth() + 1))
    .replace('dd', pad(d.getDate()))
    .replace('HH', pad(d.getHours()))
    .replace('mm', pad(d.getMinutes()))
    .replace('ss', pad(d.getSeconds()))
}

/**
 * 相对时间（刚刚 / N 分钟前 / N 小时前 / N 天前）
 */
export function relativeTime(input) {
  if (!input) return '-'
  const d = new Date(String(input).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(input)
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  if (diff < 30 * 86400_000) return `${Math.floor(diff / 86400_000)} 天前`
  return formatDateTime(input)
}