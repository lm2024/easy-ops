import axios from 'axios'
import type { AxiosInstance, AxiosResponse } from 'axios'
import type { Result } from '../types'
import { message } from 'ant-design-vue'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
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
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
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
      const isProxyDown = !error.response.data || typeof error.response.data !== 'object' || !('code' in (error.response.data || {}))
      if (isProxyDown) {
        message.error('后端服务未启动或不可用，请在 backend 目录执行 ./start.sh')
        return Promise.reject(error)
      }
    }
    // 400 是业务异常，由调用方自行处理提示（如 displayMessage: false 则静默）
    if (error.response && error.response.status === 400) {
      const body = error.response.data
      const bizMsg = body?.message || error.message
      // 用更友好的业务消息替换 axios 的原始错误消息
      error.message = bizMsg
      return Promise.reject(error)
    }
    message.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
