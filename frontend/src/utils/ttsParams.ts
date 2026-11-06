/**
 * TTS 参数构建与校验（方案二 8.5）。
 * 纯函数，便于单测。
 */
import { TtsParams, DEFAULT_TTS_PARAMS } from '@/consts/softphone'

export interface TtsValidationIssue {
  field: keyof TtsParams | 'root'
  message: string
}

/** 构造一份带默认值的 TTS 参数 */
export function buildTtsParams(overrides: Partial<TtsParams> = {}): TtsParams {
  return { ...DEFAULT_TTS_PARAMS, ...overrides }
}

/** 字数统计（中文按字符，去除首尾空白） */
export function countTextChars(text: string): number {
  return String(text || '').trim().length
}

/**
 * 校验 TTS 参数，返回问题列表（空数组表示通过）。
 */
export function validateTtsParams(params: TtsParams): TtsValidationIssue[] {
  const issues: TtsValidationIssue[] = []
  const text = String(params.text || '').trim()

  if (!text) {
    issues.push({ field: 'text', message: '请输入合成文本' })
  } else if (text.length > 500) {
    issues.push({ field: 'text', message: '合成文本不能超过 500 字' })
  }

  if (!params.provider) {
    issues.push({ field: 'provider', message: '请选择服务商' })
  }
  if (!params.voice) {
    issues.push({ field: 'voice', message: '请选择音色' })
  }
  if (!params.format) {
    issues.push({ field: 'format', message: '请选择音频格式' })
  }
  if (typeof params.rate !== 'number' || params.rate < -500 || params.rate > 500) {
    issues.push({ field: 'rate', message: '语速取值范围为 -500 ~ 500' })
  }
  if (typeof params.pitch !== 'number' || params.pitch < -500 || params.pitch > 500) {
    issues.push({ field: 'pitch', message: '音调取值范围为 -500 ~ 500' })
  }
  if (typeof params.volume !== 'number' || params.volume < 0 || params.volume > 100) {
    issues.push({ field: 'volume', message: '音量取值范围为 0 ~ 100' })
  }

  return issues
}

/**
 * 把前端表单参数转换为提交给后端 TTS 接口的负载。
 * 此处只做字段裁剪与归一化，不假定具体服务商协议。
 */
export function toTtsPayload(params: TtsParams) {
  return {
    text: String(params.text || '').trim(),
    // 后端枚举使用大写名称；前端选项保留小写以兼容既有表单。
    provider: String(params.provider || '').toUpperCase(),
    voice: params.voice,
    // 页面沿用云厂商常见的 [-500, 500] / [0, 100] 刻度，
    // 统一接口契约使用 [-1, 1] / [0, 1]。
    speed: Number(params.rate) / 500,
    pitch: Number(params.pitch) / 500,
    volume: Number(params.volume) / 100,
    sampleRate: Number(params.sampleRate),
    format: String(params.format || '').toUpperCase()
  }
}
