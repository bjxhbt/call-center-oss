import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { getToken, setToken } from '@/utils/local'
import router from '@/router'
import { message } from 'ant-design-vue'
import { getErrorMessage, isBusinessCode, type FieldError } from '@/consts/errorCode'

type Result<T> = {
  code: number
  msg: string
  data: T
}

const baseURL = '/'

/**
 * 解析后端错误响应为中文提示（任务 10.2）。
 * 优先级：字段级错误(data 为 FieldError[]) → msg → 业务错误码映射 → 兜底。
 */
function resolveErrorMessage(data: any): string {
  // 字段级错误：后端校验失败时 data 为 [{field, message}, ...]
  if (Array.isArray(data?.data) && data.data.length > 0 && data.data[0]?.message) {
    const fields = data.data as FieldError[]
    return fields.map((f) => f.message).filter(Boolean).join('；')
  }
  if (data?.msg) return data.msg
  if (data?.code != null && isBusinessCode(data.code)) return getErrorMessage(data.code, '请求失败')
  return '请求失败'
}

export class Request {
  instance: AxiosInstance
  baseConfig: AxiosRequestConfig = { baseURL, timeout: 10000 }

  constructor(config: AxiosRequestConfig) {
    this.instance = axios.create(Object.assign(this.baseConfig, config))

    this.instance.interceptors.request.use(
      (config) => {
        const token = getToken()
        if (token) {
          config.headers!.Authorization = `Bearer ${token}`
        }
        return config
      },
      (err: any) => Promise.reject(err)
    )

    this.instance.interceptors.response.use(
      (res: AxiosResponse) => {
        if (res.data?.code === 401) {
          setToken('')
          router.push('/login')
          return Promise.reject(res.data)
        }

        if (res.data?.code !== undefined && res.data.code !== 200) {
          message.error(resolveErrorMessage(res.data))
          return res.data
        }

        return res.data
      },
      (err: any) => {
        const status = err.response?.status
        if (!status) {
          message.error('网络连接异常，请检查后端服务或网络')
          return Promise.reject(err)
        }

        let errorMessage = ''
        switch (status) {
          case 400:
            errorMessage = '请求错误(400)'
            break
          case 401:
            errorMessage = '未授权，请重新登录(401)'
            setToken('')
            router.push({ path: '/login' })
            break
          case 403:
            errorMessage = '拒绝访问(403)'
            break
          case 404:
            errorMessage = '请求出错(404)'
            break
          case 500:
            errorMessage = '服务器错误(500)'
            break
          default:
            errorMessage = `连接出错(${status})!`
        }

        message.error(errorMessage)
        return Promise.reject(err.response ?? err)
      }
    )
  }

  public request(config: AxiosRequestConfig): Promise<AxiosResponse> {
    return this.instance.request(config)
  }

  public get<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.get(url, config)
  }

  public post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.post(url, data, config)
  }

  public delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.delete(url, config)
  }

  public download(url: string, config: AxiosRequestConfig = {}): Promise<Blob> {
    return this.instance.get(url, { ...config, responseType: 'blob' }) as unknown as Promise<Blob>
  }
}

export default new Request({})
