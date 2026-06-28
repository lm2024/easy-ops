import request from '../utils/request'
import type { AlarmModel, AlarmConfigModel, Result } from '../types'

/** 获取告警列表 */
export function getAlarms(projectId?: string, type = '', page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: AlarmModel[]; total: number }>>(`/alarms`, {
    params: { projectId, type, page, pageSize }
  })
}

/** 获取告警配置 */
export function getAlarmConfig() {
  return request.get<any, Result<AlarmConfigModel>>('/alarms/config')
}

/** 保存告警配置 */
export function saveAlarmConfig(config: AlarmConfigModel) {
  return request.put<any, Result<AlarmConfigModel>>('/alarms/config', config)
}

/** 发送测试告警 */
export function sendTestAlarm(configId: number) {
  return request.post<any, Result>('/alarms/send', { configId })
}

/** 清空所有告警 */
export function clearAlarms() {
  return request.delete<any, Result>('/alarms')
}
