import request from '../utils/request'

/** 获取 AI 配置 */
export function getAIConfig() {
  return request.get<any, Result<{ endpoint: string; model: string; apiKey: string; enabled: string }>>('/ai/config')
}

/** 保存 AI 配置 */
export function saveAIConfig(config: { endpoint?: string; model?: string; apiKey?: string; enabled?: string }) {
  return request.post<any, Result>('/ai/config', config)
}

/** AI 分析日志 */
export function analyzeLog(nodeId: string, logPath: string, lines = 200, question?: string) {
  return request.post<any, Result<{ logSnippet: string; analysis: string }>>('/ai/analyze-log', {
    nodeId, logPath, lines, question
  })
}

/** AI 通用对话 */
export function aiChat(prompt: string) {
  return request.post<any, Result<{ reply: string }>>('/ai/chat', { prompt })
}
