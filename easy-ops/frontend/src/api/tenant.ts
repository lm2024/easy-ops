import request from '../utils/request'
import type { Result, TenantModel, TenantMemberModel, UserModel } from '../types'

/** 租户列表（含统计；SUPER_ADMIN 全量 / TENANT_ADMIN 本人租户） */
export function getTenants() {
  return request.get<any, Result<{ list: TenantModel[]; total: number }>>('/tenants')
}

/** 租户详情 */
export function getTenant(id: number) {
  return request.get<any, Result<TenantModel>>(`/tenants/${id}`)
}

/** 创建租户（仅平台管理员） */
export function createTenant(tenant: Partial<TenantModel>) {
  return request.post<any, Result<TenantModel>>('/tenants', tenant)
}

/** 编辑租户（改名/启停，仅平台管理员） */
export function updateTenant(id: number, tenant: Partial<TenantModel>) {
  return request.put<any, Result>(`/tenants/${id}`, tenant)
}

/** 删除租户（仅平台管理员；有资源或 default 租户禁删） */
export function deleteTenant(id: number) {
  return request.delete<any, Result>(`/tenants/${id}`)
}

/** 租户成员列表 */
export function getTenantMembers(tenantId: number) {
  return request.get<any, Result<TenantMemberModel[]>>(`/tenants/${tenantId}/members`)
}

/** 添加租户成员（body: { userId, role }） */
export function addTenantMember(tenantId: number, userId: number, role: string) {
  return request.post<any, Result>(`/tenants/${tenantId}/members`, { userId, role })
}

/** 修改成员角色/状态 */
export function updateTenantMember(tenantId: number, userId: number, member: Partial<TenantMemberModel>) {
  return request.put<any, Result>(`/tenants/${tenantId}/members/${userId}`, member)
}

/** 移除租户成员 */
export function removeTenantMember(tenantId: number, userId: number) {
  return request.delete<any, Result>(`/tenants/${tenantId}/members/${userId}`)
}

/** 平台管理员切换当前生效租户（body: { tenantId }） */
export function switchTenant(tenantId: number) {
  return request.post<any, Result<{ tenantId: number; tenantName: string; tenantRole: string }>>('/tenants/switch', { tenantId })
}

/** 可用用户候选（添加成员用）：复用用户列表 */
export function listUsersForMember(page = 1, pageSize = 100) {
  return request.get<any, Result<{ list: UserModel[]; total: number }>>('/auth/users', {
    params: { page, pageSize }
  })
}
