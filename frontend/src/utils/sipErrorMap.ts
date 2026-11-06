/**
 * SIP 通话失败 → 中文提示映射（方案二 2.5）。
 *
 * 纯函数，不依赖运行时，方便单元测试。
 * 输入：JsSIP failed/ended 事件的 cause、SIP 状态码、发起方。
 * 输出：用户可理解的中文消息 + 提示级别。
 */
import { ErrorLevel } from '@/consts/softphone'

export interface SipFailureInput {
  /** JsSIP cause 字符串，如 'Rejected'、'Unavailable'、'User Denied Media Access' */
  cause?: string
  /** SIP 响应状态码，如 404、486、487、503 */
  statusCode?: number
  /** 失败来源 local=本地主动、remote=远端、system=系统 */
  originator?: 'local' | 'remote' | 'system'
  /** SIP reason phrase，作为兜底 */
  reasonPhrase?: string
}

export interface SipFailureResult {
  /** 是否属于用户主动挂断（不应展示为系统错误） */
  intentional: boolean
  /** 中文提示 */
  message: string
  /** 提示级别 */
  level: ErrorLevel
}

/** JsSIP cause → 中文描述 */
const CAUSE_MESSAGES: Record<string, string> = {
  // 注册相关
  'Registration Error': 'SIP 注册失败，请检查账号或网络',
  // 通用
  'Rejected': '对方拒接或呼叫被拒绝',
  'Busy': '用户忙',
  'Not Found': '号码不存在或号码错误',
  'Unavailable': '网关或对端暂时不可用',
  'Unavailable Target': '号码无法接通',
  'Request Timeout': '请求超时，请稍后重试',
  'Timeout': '请求超时，请稍后重试',
  'Temporarily Unavailable': '对端暂时不可达',
  'Address Incomplete': '号码不完整',
  'Incompatible SDP': '媒体协商失败',
  'Authentication Error': 'SIP 鉴权失败，请检查账号密码',
  'Connection Error': 'SIP 连接异常',
  'Internal Error': '服务内部错误',
  'Canceled': '呼叫已取消',
  'Canceled By Originator': '呼叫已取消',
  'No Answer': '无人接听',
  'SIP Failure Code': 'SIP 呼叫失败',
  'User Denied Media Access': '麦克风权限被拒绝',
  'Media Error': '媒体获取失败，请检查麦克风设备',
  'WebRTC Error': 'WebRTC 媒体失败',
  'WebRTC Not Supported': '当前浏览器不支持 WebRTC',
  'RTP Timeout': '媒体超时',
  'No ACK': '通话确认超时',
  'No PRACK': '通话振铃确认超时'
}

/** SIP 状态码 → 中文描述（覆盖常见场景） */
const STATUS_CODE_MESSAGES: Record<number, string> = {
  401: 'SIP 鉴权失败(401)，请检查账号密码',
  403: 'SIP 拒绝访问(403)，账号可能未授权或被禁用',
  404: '号码不存在或号码错误(404)',
  405: '方法不允许(405)',
  407: 'SIP 代理鉴权失败(407)',
  408: '请求超时，请稍后重试(408)',
  410: '号码已失效(410)',
  480: '对端暂时不可达(480)',
  482: '呼叫环路(482)',
  484: '号码格式不正确(484)',
  486: '用户忙(486)',
  487: '呼叫已取消(487)',
  488: '媒体协商失败(488)',
  491: '请求冲突(491)',
  500: '服务内部错误(500)',
  502: '网关不可用(502)',
  503: '服务不可用(503)，网关繁忙或离线',
  504: '网关超时(504)'
}

/** 用户主动操作导致的状态码/cause（不应报红） */
const INTENTIONAL_STATUS_CODES = new Set([487]) // Request Terminated
const INTENTIONAL_CAUSES = new Set(['Canceled', 'Canceled By Originator'])

/**
 * 把 JsSIP failed/ended 信息转换为中文提示。
 *
 * @param input 失败信息
 * @param isEnded whether the session ended normally（ended 事件而非 failed）。
 *                ended 通常表示正常结束，不需要错误提示。
 */
export function describeSipFailure(input: SipFailureInput, isEnded = false): SipFailureResult {
  const cause = input?.cause
  const statusCode = Number(input?.statusCode)
  const reasonPhrase = input?.reasonPhrase || ''

  // 用户主动挂断：cause 为 Canceled 或状态码 487
  const intentional =
    !!(cause && INTENTIONAL_CAUSES.has(cause)) || INTENTIONAL_STATUS_CODES.has(statusCode)

  if (isEnded) {
    return { intentional: true, message: '通话已结束', level: 'info' }
  }

  if (intentional) {
    return { intentional: true, message: '通话已结束', level: 'info' }
  }

  // 优先按状态码匹配
  if (statusCode && STATUS_CODE_MESSAGES[statusCode]) {
    return {
      intentional: false,
      message: STATUS_CODE_MESSAGES[statusCode],
      level: 'error'
    }
  }

  // 其次按 JsSIP cause 匹配
  if (cause && CAUSE_MESSAGES[cause]) {
    const isMedia = cause.includes('Media') || cause.includes('WebRTC')
    return {
      intentional: false,
      message: CAUSE_MESSAGES[cause],
      level: isMedia ? 'warning' : 'error'
    }
  }

  // 兜底：使用 reason phrase
  if (reasonPhrase) {
    return {
      intentional: false,
      message: `呼叫失败：${reasonPhrase}`,
      level: 'error'
    }
  }

  return {
    intentional: false,
    message: '呼叫失败，请稍后重试',
    level: 'error'
  }
}

/** 注册失败 → 中文提示 */
export function describeRegistrationFailure(cause?: string, statusCode?: number): string {
  const fromStatus = statusCode && STATUS_CODE_MESSAGES[statusCode]
  if (fromStatus) return fromStatus
  if (cause === 'Authentication Error') return CAUSE_MESSAGES['Authentication Error']
  if (cause === 'Registration Error') return CAUSE_MESSAGES['Registration Error']
  if (cause === 'Connection Error') return CAUSE_MESSAGES['Connection Error']
  return cause ? `注册失败：${cause}` : 'SIP 注册失败，请检查账号或网络'
}
