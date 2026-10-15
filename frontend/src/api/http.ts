import axios from 'axios'
import { clearSession, getToken } from './session'

/**
 * 统一 API 客户端：经 Vite 代理转发到 gateway（/api 前缀被 rewrite 移除）。
 * 请求拦截器统一注入 Authorization；响应拦截器解包 ApiResponse(code/message/data)。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(new Error(body.message || `请求失败（${body.code}）`))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      clearSession()
      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        window.location.assign(`/login?redirect=${encodeURIComponent(path)}`)
      }
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  },
)

export default http
