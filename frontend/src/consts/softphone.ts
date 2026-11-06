/**
 * 软电话与坐席工作台前端常量定义（方案二专用）。
 *
 * 仅包含数据与类型声明，不引入 JsSIP / Vue 运行时依赖，
 * 方便被纯函数模块（utils/、stores/）与单元测试复用。
 */

/** 通话方向：1=呼入，2=呼出 */
export enum CallDirection {
  INBOUND = 1,
  OUTBOUND = 2
}

/**
 * 软电话状态机。
 * 空闲 → 注册中 → 已注册 → (拨号中|来电) → 振铃 → 通话 → (保持|结束) → 空闲
 */
export enum CallState {
  IDLE = 'idle', // 空闲（未签入或已签入但无通话）
  REGISTERING = 'registering', // 注册中
  REGISTERED = 'registered', // 已注册
  DIALING = 'dialing', // 拨号中（呼出尚未振铃）
  RINGING = 'ringing', // 振铃中（呼出远端振铃 或 呼入本地振铃）
  INCOMING = 'incoming', // 来电到达（等待坐席接听）
  ACTIVE = 'active', // 通话中
  HOLD = 'hold', // 保持
  ENDED = 'ended' // 通话结束（过渡态，随后回到 IDLE/REGISTERED）
}

/** SIP 注册状态 */
export enum RegisterStatus {
  UNREGISTERED = 'unregistered',
  REGISTERING = 'registering',
  REGISTERED = 'registered',
  FAILED = 'failed',
  DISCONNECTED = 'disconnected'
}

/** 信令网络连接状态 */
export enum NetworkStatus {
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting'
}

/** 坐席业务状态码（与后端坐席状态字典对齐） */
export const AGENT_STATUS_MAP: Record<string, { label: string; color: string }> = {
  '1': { label: '空闲', color: '#389e0d' },
  '2': { label: '忙碌', color: '#d48806' },
  '3': { label: '勿扰', color: '#cf1322' },
  '4': { label: '离线', color: '#8c8c8c' },
  '5': { label: '通话中', color: '#1677ff' },
  '6': { label: '振铃中', color: '#d4380d' },
  '7': { label: '话后', color: '#8c8c8c' }
}

export const REGISTER_STATUS_TEXT: Record<RegisterStatus, string> = {
  [RegisterStatus.UNREGISTERED]: '未注册',
  [RegisterStatus.REGISTERING]: '注册中',
  [RegisterStatus.REGISTERED]: '已注册',
  [RegisterStatus.FAILED]: '注册失败',
  [RegisterStatus.DISCONNECTED]: '连接断开'
}

export const REGISTER_STATUS_COLOR: Record<RegisterStatus, string> = {
  [RegisterStatus.UNREGISTERED]: '#8c8c8c',
  [RegisterStatus.REGISTERING]: '#1677ff',
  [RegisterStatus.REGISTERED]: '#52c41a',
  [RegisterStatus.FAILED]: '#ff4d4f',
  [RegisterStatus.DISCONNECTED]: '#faad14'
}

export const NETWORK_STATUS_COLOR: Record<NetworkStatus, string> = {
  [NetworkStatus.CONNECTED]: '#52c41a',
  [NetworkStatus.DISCONNECTED]: '#ff4d4f',
  [NetworkStatus.CONNECTING]: '#1677ff'
}

export const NETWORK_STATUS_TEXT: Record<NetworkStatus, string> = {
  [NetworkStatus.CONNECTED]: '已连接',
  [NetworkStatus.DISCONNECTED]: '已断开',
  [NetworkStatus.CONNECTING]: '连接中'
}

/**
 * WebSocket 事件名称（与方案一《接口与事件规范》冻结一致）。
 * 命名空间统一使用大写蛇形，业务事件前缀 CALL_ / TRANSCRIPT_。
 */
export const WsEvent = {
  /** 登录握手成功，返回 sessionId */
  SUCCEED: 'SUCCEED',
  /** 心跳 */
  HEARTBEAT: 'HEARTBEAT',
  /** 坐席状态变更 */
  AGENT_STATUS: 'AGENT_STATUS',
  /** 来电通知（弹屏入口） */
  INCOMING_CALL: 'INCOMING_CALL',
  /** 通话被驻留 */
  CALL_PARK: 'CALL_PARK',
  /** 通话被接听 */
  CALL_ANSWER: 'CALL_ANSWER',
  /** 双方桥接（正式通话开始） */
  CALL_BRIDGE: 'CALL_BRIDGE',
  /** 通话挂断 */
  CALL_HANGUP: 'CALL_HANGUP',
  /** 通话失败（网关/号码/媒体等） */
  CALL_FAIL: 'CALL_FAIL',
  /** 转写增量结果 */
  TRANSCRIPT_PARTIAL: 'TRANSCRIPT_PARTIAL',
  /** 转写最终结果 */
  TRANSCRIPT_FINAL: 'TRANSCRIPT_FINAL',
  /** 转写会话状态/异常 */
  TRANSCRIPT_STATUS: 'TRANSCRIPT_STATUS',
  UNKNOWN: 'UNKNOWN'
} as const

export type WsEventName = (typeof WsEvent)[keyof typeof WsEvent]

/** 统一 WebSocket 消息外层结构 */
export interface WsMessage<T = any> {
  /** WebSocket 会话ID */
  sessionId?: string
  /** 业务呼叫唯一标识，非通话类事件为空（与后端 WsMsgPayload.callId 对齐，方案一 9.7） */
  callId?: string
  timestamp?: number
  event: WsEventName | string
  data: T
}

/** 错误提示级别 */
export type ErrorLevel = 'info' | 'warning' | 'error' | 'success'

/**
 * 实时转写一条结果。
 * - isFinal=false：临时增量文本，按 segmentId 覆盖同一段
 * - isFinal=true：最终文本，替换该 segmentId 的临时文本
 */
export interface TranscriptSegment {
  /** 段落稳定标识，同 segmentId 反复更新 */
  segmentId: string
  /** 顺序序号（部分 ASR 不返回 segmentId 时用作回退 key） */
  seq?: number
  /** 文本内容 */
  text: string
  /** 是否最终结果 */
  isFinal: boolean
  /** 说话人：customer/agent/unknown（无法区分声道时用 unknown） */
  speaker?: 'customer' | 'agent' | 'unknown'
  /** 时间戳（毫秒），由前端在收到时补齐 */
  timestamp?: number
  /** 置信度 0~1 */
  confidence?: number
}

/** 转写会话状态 */
export type TranscriptSessionStatus = 'idle' | 'connecting' | 'active' | 'unavailable' | 'error'

/** TTS 服务商选项 */
export interface TtsProviderOption {
  value: string
  label: string
}

export const TTS_PROVIDERS: TtsProviderOption[] = [
  { value: 'mock', label: 'Mock 兜底语音' },
  { value: 'aliyun', label: '阿里云语音合成' },
  { value: 'tencent', label: '腾讯云语音合成' },
  { value: 'cosyvoice', label: 'CosyVoice（本地）' }
]

/** 音频采样率 */
export const TTS_SAMPLE_RATES = [
  { value: 8000, label: '8 kHz（电话）' },
  { value: 16000, label: '16 kHz' },
  { value: 22050, label: '22.05 kHz' },
  { value: 24000, label: '24 kHz' },
  { value: 48000, label: '48 kHz' }
]

/** 音频格式 */
export const TTS_FORMATS = [
  { value: 'mp3', label: 'MP3' },
  { value: 'wav', label: 'WAV' },
  { value: 'pcm', label: 'PCM' }
]

/** TTS 参数模型 */
export interface TtsParams {
  text: string
  provider: string
  voice: string
  rate: number // 语速 [-500, 500] 或 0.5~2.0，按服务商
  pitch: number // 音调
  volume: number // 音量 0~100
  sampleRate: number
  format: string
}

/** TTS 默认参数 */
export const DEFAULT_TTS_PARAMS: TtsParams = {
  text: '',
  provider: 'aliyun',
  voice: '',
  rate: 0,
  pitch: 0,
  volume: 50,
  sampleRate: 16000,
  format: 'mp3'
}

/** 拨号盘按键 */
export const DIALPAD_ROWS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['*', '0', '#']
]

/** localStorage 键 */
export const STORAGE_KEYS = {
  SIP_INSTANCE_ID: 'och:sip:instance-id',
  REGISTRATION_CLEANUP_PREFIX: 'och:sip:registration-cleanup:v1'
} as const

/** SIP 操作（注册/注销）超时（毫秒） */
export const SIP_OPERATION_TIMEOUT = 5000
