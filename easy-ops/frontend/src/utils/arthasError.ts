/**
 * Arthas 诊断错误的用户可读化。
 *
 * 后端异常要穿过 Agent → Server → 前端三层，每层都往前面套一句，
 * 直接把 e.message 显示出来是这样的：
 *
 *   采集失败: 命令执行失败: Agent 请求失败: Agent 返回错误:
 *   命令执行失败: Arthas 会话不存在或已结束: pid=320
 *
 * 用户看到只会觉得"系统崩了"，实际上其中大多数只是会话断了、重连即可。
 * 这里做两件事：把技术串翻译成人话，并标出它属于哪一类错误，
 * 让调用方能决定是"弹个提示"还是"引导重新连接"。
 */

export interface FriendlyError {
  /** 用户能看懂的一句话 */
  text: string
  /** 是否属于"会话断了"这一类可自愈错误 */
  sessionLost: boolean
}

/** 会话丢失的特征词，与后端 ArthasDiagnoseService.isSessionLost 保持对应 */
const SESSION_LOST_PATTERNS = [
  '会话不存在',
  '已结束',
  '目标进程已退出',
  'not attached',
  'Connection refused',
  '连接已断开',
  'No such process'
]

const TIMEOUT_PATTERNS = ['超时', 'timeout', 'Timeout', 'Read timed out']
const DENY_PATTERNS = ['不支持', '白名单', '禁止', '非法']
const PERM_PATTERNS = ['无权限', '403', '未授权']

export function toFriendlyError(e: any): FriendlyError {
  const raw = String((e && e.message) || e || '')

  if (matches(raw, SESSION_LOST_PATTERNS)) {
    return {
      text: '诊断连接已断开，已尝试自动重连，请再点一次采集；仍失败请重新进入诊断',
      sessionLost: true
    }
  }
  if (matches(raw, PERM_PATTERNS)) {
    return { text: '没有该项目的诊断权限', sessionLost: false }
  }
  if (matches(raw, DENY_PATTERNS)) {
    return { text: '该命令不在允许执行的白名单内', sessionLost: false }
  }
  if (matches(raw, TIMEOUT_PATTERNS)) {
    return { text: '命令执行超时，可尝试调大超时时间或换一个更轻量的命令', sessionLost: false }
  }
  return { text: lastSegment(raw) || '命令执行失败', sessionLost: false }
}

/**
 * 拼上操作前缀，保留"哪个动作失败了"的上下文，但错误信息用人话。
 * 例：friendlyMessage('采集失败', e) -> "采集失败：诊断连接已断开，已尝试自动重连…"
 */
export function friendlyMessage(action: string, e: any): string {
  return action + '：' + toFriendlyError(e).text
}

function matches(raw: string, patterns: string[]): boolean {
  return patterns.some((p) => raw.includes(p))
}

/**
 * 取技术串的最后一段。
 * 多层包装的错误里，越靠后越接近根因，前面几段都是"命令执行失败"这类无信息量的套话。
 */
function lastSegment(raw: string): string {
  const parts = raw
    .split(/[:：]/)
    .map((s) => s.trim())
    .filter(Boolean)
  return parts.length > 0 ? parts[parts.length - 1] : raw.trim()
}
