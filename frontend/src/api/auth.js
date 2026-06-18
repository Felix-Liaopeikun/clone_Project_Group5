import request from './request'

/**
 * 注册
 * @param {{ username: string, password: string }} data
 */
export function register(data) {
  return request.post('/auth/register', data)
}

/**
 * 登录
 * @param {{ username: string, password: string }} data
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 修改当前登录用户的密码
 * @param {{ oldPassword: string, newPassword: string }} data
 */
export function changePassword(data) {
  return request.put('/auth/password', data)
}
