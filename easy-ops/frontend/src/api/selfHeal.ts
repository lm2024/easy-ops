import request from '../utils/request'
import type { Result, SelfHealPolicyModel, SelfHealEventModel } from '../types'

/** 策略列表 */
export function listPolicies() {
  return request.get<any, Result<SelfHealPolicyModel[]>>('/self-heal/policies')
}

/** 单项目策略 */
export function getPolicy(projectId: number) {
  return request.get<any, Result<SelfHealPolicyModel>>(`/self-heal/policies/${projectId}`)
}

/** 创建/更新策略 */
export function savePolicy(policy: SelfHealPolicyModel) {
  return request.post<any, Result<SelfHealPolicyModel>>('/self-heal/policies', policy)
}

/** 解除熔断 */
export function resetCircuitBreaker(projectId: number) {
  return request.post<any, Result<SelfHealPolicyModel>>(`/self-heal/policies/${projectId}/circuit-break`)
}

/** 自愈事件历史 */
export function listEvents(projectId?: number, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: SelfHealEventModel[]; total: number }>>('/self-heal/events', {
    params: { projectId, page, pageSize }
  })
}
