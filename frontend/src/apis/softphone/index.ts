/**
 * 软电话方案专用 API 总入口与 Mock 开关。
 *
 * 后端接口未完成时，customer/callHistory/transcription/tts 走 Mock 适配层，
 * 保证前端可独立开发与演示。真实接口就绪后，将 VITE_SOFTPHONE_LIVE_API 置为
 * 'true'（或在构建时配置），即可切换到真实请求，无需改页面逻辑。
 *
 * 切换规则：每个 API 内部判断 USE_MOCK，true 则返回 Mock Promise，false 走 Request。
 */

const env = (import.meta as any).env || {}

/** 是否启用真实后端接口（默认 false，即使用 Mock） */
export const SOFTPHONE_LIVE_API = String(env.VITE_SOFTPHONE_LIVE_API || '') === 'true'

/** 各模块独立开关（便于分阶段联调） */
export const SOFTPHONE_MOCK = {
  customer: !SOFTPHONE_LIVE_API || String(env.VITE_SOFTPHONE_LIVE_CUSTOMER || '') === 'true',
  callHistory:
    !SOFTPHONE_LIVE_API || String(env.VITE_SOFTPHONE_LIVE_CALL_HISTORY || '') === 'true',
  transcription:
    !SOFTPHONE_LIVE_API || String(env.VITE_SOFTPHONE_LIVE_TRANSCRIPT || '') === 'true',
  tts: !SOFTPHONE_LIVE_API || String(env.VITE_SOFTPHONE_LIVE_TTS || '') === 'true'
}

/** 统一返回结构（与后端 {code,msg,data} 对齐） */
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

export function ok<T>(data: T, msg = ''): ApiResult<T> {
  return { code: 200, msg, data }
}

export function fail<T = null>(msg: string, data: T = null as any): ApiResult<T> {
  return { code: 500, msg, data }
}

/** 模拟网络延迟 */
export function mockDelay<T>(data: T, ms = 300): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms))
}
