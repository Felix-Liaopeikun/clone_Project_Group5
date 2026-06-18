import request from './request'

/**
 * 获取所有用户列表（仅 admin）
 * @returns {Promise<UserVO[]>}
 */
export function listUsers() {
  return request.get('/admin/users')
}

/**
 * 更新用户角色（仅 admin）
 * @param {number} id  用户 ID
 * @param {string} role USER | ADMIN
 * @returns {Promise<UserVO>}
 */
export function updateUserRole(id, role) {
  return request.put(`/admin/users/${id}/role?role=${encodeURIComponent(role)}`)
}
