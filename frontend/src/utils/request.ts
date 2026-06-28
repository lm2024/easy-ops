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
    // 添加Token (用户认证)
    const token = localStorage.getItem('token')
    if (token) {
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
    if (res.code !== 200) {
      message.error(res.message || '请求失败')
      if (res.code === 401) {
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
