import axios from 'axios'
import type { AxiosInstance, AxiosResponse } from 'axios'
import type { Result } from '../types'
import { message } from 'ant-design-vue'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const url = config.url || ''
    const isAuthApi = url.includes('/auth/login') || url.includes('/auth/captcha')
    const token = localStorage.getItem('token')
    if (token && !isAuthApi) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 租户上下文（平台管理员切换租户视角时生效；后端从 token 解析为主，此头仅辅助）
    const tenantId = localStorage.getItem('tenantId')
    if (tenantId && !isAuthApi && url !== '/tenants/switch') {
      config.headers['X-Tenant-Id'] = tenantId
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  async (response: AxiosResponse<Result>) => {
    // blob 响应（文件下载等）：response.data 是 Blob 对象，不包含 code 字段
    if (response.config.responseType === 'blob') {
      const blob = response.data
      if (blob instanceof Blob && blob.size > 0) {
        // 后端返回错误时（如 500），会将 JSON 错误信息作为 text/plain 写入响应体，
        // axios 因 responseType=blob 不会走 error handler，需要手动检测
        const type = blob.type || ''
        if (type.includes('json') || type.includes('text')) {
          try {
            const text = await blob.text()
            const parsed = JSON.parse(text)
            if (parsed.code && parsed.code !== 200) {
              message.error(parsed.message || '请求失败')
              return Promise.reject(new Error(parsed.message))
            }
          } catch {
            // 非 JSON 内容，视为正常文件数据，直接返回原始 blob
          }
        }
      }
      // 返回真正的 Blob（response.data），供调用方 createObjectURL 使用
      return response.data as any
    }
    const res = response.data
    const requestUrl = response.config?.url || ''
    const isLoginApi = requestUrl.includes('/auth/login')
    if (res.code !== 200) {
      const isCaptchaMsg = (res.message || '').includes('验证码')
      const skipGlobalToast = isLoginApi || isCaptchaMsg
      if (!skipGlobalToast) {
        message.error(res.message || '请求失败')
      }
      if (res.code === 401 && !window.location.pathname.startsWith('/login')) {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    return res as any
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    // 后端未启动或代理失败时，Vite 常返回 500/502
    if (error.response && (error.response.status === 500 || error.response.status === 502 || error.response.status === 503)) {
      const body = error.response.data
      const hasStructuredResponse = body && typeof body === 'object' && 'code' in (body || {})
      if (hasStructuredResponse) {
        // 后端返回了结构化错误响应，提取友好消息
        const bizMsg = body?.message || '系统内部异常，请联系管理员'
        message.error(bizMsg)
        error.message = bizMsg
        return Promise.reject(error)
      }
      // 后端未启动或代理失败（无结构化响应）
      message.error('后端服务未启动或不可用，请在 backend 目录执行 ./start.sh')
      return Promise.reject(error)
    }
    // 400 是业务异常，由调用方自行处理提示（如 displayMessage: false 则静默）
    if (error.response && error.response.status === 400) {
      const body = error.response.data
      const bizMsg = body?.message || error.message
      error.message = bizMsg
      return Promise.reject(error)
    }
    message.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
