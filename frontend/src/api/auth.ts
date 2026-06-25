import request from '../utils/request'
import type { UserModel, Result } from '../types'

/** 用户登录 */
export function login(username: string, password: string) {
  return request.post<any, Result<{ token: string }>>('/auth/login', { username, password })
}

/** 获取用户列表 */
export function getUsers() {
  return request.get<any, Result<{ list: UserModel[]; total: number }>>('/auth/users')
}

/** 获取用户详情 */
export function getUserById(id: number) {
  return request.get<any, Result<UserModel>>(`/auth/users/${id}`)
}

/** 新增用户 */
export function createUser(user: UserModel) {
  return request.post<any, Result<UserModel>>('/auth/users', user)
}

/** 更新用户 */
export function updateUser(id: number, user: UserModel) {
  return request.put<any, Result<UserModel>>(`/auth/users/${id}`, user)
}

/** 删除用户 */
export function deleteUser(id: number) {
  return request.delete<any, Result>(`/auth/users/${id}`)
}
