/**
 * TTS 接口（方案二 8.5 / 8.6）。
 *
 * - getVoiceList(provider): 获取某服务商的音色列表
 * - synthesize(params): 合成并返回试听音频地址
 *
 * 真实接口路径待方案五（TTS 与验收）提供。联调时把 SOFTPHONE_MOCK.tts 置 false。
 */
import Request from '@/utils/axios'
import { TtsParams } from '@/consts/softphone'
import { SOFTPHONE_MOCK, ApiResult, ok, fail, mockDelay } from './index'
import { synthesizeMockAudio } from '@/services/tts/mockSynth'
import { toTtsPayload } from '@/utils/ttsParams'

export interface TtsVoice {
  value: string
  label: string
  /** 音色性别/风格描述 */
  desc?: string
}

export interface TtsSynthResult {
  /** 可直接 <audio src> 播放的地址 */
  audioUrl: string
  /** 后端任务ID（用于异步轮询/审计） */
  taskId?: string
  /** 音频时长（毫秒） */
  durationMs?: number
  /** 采样率 */
  sampleRate?: number
  /** 格式 */
  format?: string
  /** 是否命中后端合成缓存 */
  cacheHit?: boolean
  /** 后端缓存键，可用于文件重放 */
  cacheKey?: string
}

export interface TtsProviderStatus {
  provider: string
  /** Bean registered in the current Spring context. */
  available: boolean
  /** Local configuration/credential presence only; this is not a cloud-call acceptance result. */
  ready: boolean
}

export interface TtsRuntimeStatus {
  configuredEngine: string
  playbackBeanCount: number
  playbackBeanNames: string[]
  playbackImplementations: string[]
  resolvedPlaybackPort?: string
  uniquePlaybackBean: boolean
}

export interface TtsFileItem {
  id: number
  cacheKey: string
  provider: string
  voice: string
  speechText: string
  fileName: string
  listenUrl: string
  fileSize: number
  durationMs: number
  sampleRate: number
  format: string
  status: string
  errorCode?: string
  errorMsg?: string
  refCount: number
  createTime?: string
}

export interface TtsFilePage {
  list: TtsFileItem[]
  total: number
  pageNum: number
  pageSize: number
}

const VOICES_BY_PROVIDER: Record<string, TtsVoice[]> = {
  mock: [{ value: 'mock-default', label: 'Mock 兜底音色', desc: '无云凭证演示' }],
  aliyun: [
    { value: 'xiaoyun', label: '小云（女·标准）', desc: '通用场景' },
    { value: 'xiaogang', label: '小刚（男·标准）', desc: '通用场景' },
    { value: 'ruoxi', label: '若兮（女·温柔）', desc: '客服场景' },
    { value: 'siyue', label: '思悦（女·亲切）', desc: '客服场景' }
  ],
  tencent: [
    { value: 'xiaoyun', label: '智瑜（女·客服）', desc: '客服场景' },
    { value: 'xiaogang', label: '智云（男·标准）', desc: '通用场景' }
  ],
  cosyvoice: [
    { value: 'default', label: 'CosyVoice 默认音色', desc: '本地模型' }
  ],
  default: [{ value: 'default', label: '系统默认音色', desc: '' }]
}

export function getVoiceList(provider: string): Promise<ApiResult<TtsVoice[]>> {
  if (SOFTPHONE_MOCK.tts) {
    const list = VOICES_BY_PROVIDER[provider] || VOICES_BY_PROVIDER.default
    return mockDelay(ok(list))
  }
  return Request.get<any[]>('/tts/v1/voices', undefined, {
    params: { provider: String(provider || '').toUpperCase() }
  }).then((res: any) => ({
    ...res,
    data: (res?.data || []).map((item: any) => ({
      value: item.voice || item.value,
      label: item.name || item.label || item.voice,
      desc: [item.language, item.gender].filter(Boolean).join(' · ')
    }))
  }))
}

export function synthesize(params: TtsParams): Promise<ApiResult<TtsSynthResult>> {
  const payload = toTtsPayload(params)
  if (!payload.text) {
    return Promise.resolve(fail('请输入合成文本'))
  }
  if (SOFTPHONE_MOCK.tts) {
    return synthesizeMockAudio(params).then((res) =>
      ok({
        audioUrl: res.audioUrl,
        durationMs: res.durationMs,
        sampleRate: res.sampleRate,
        format: params.format,
        taskId: `mock-${Date.now()}`
      })
    )
  }
  return Request.post<any>('/tts/v1/synthesize', payload).then((res: any) => {
    if (res?.code !== 200 || !res?.data) return res
    const task = res.data
    if (String(task.status || '').toUpperCase() === 'FAILED' || !task.listenUrl) {
      return {
        ...res,
        code: 500,
        msg: task.errorMsg || 'TTS 合成失败，未生成可试听文件'
      }
    }
    return {
      ...res,
      data: {
        audioUrl: task.listenUrl || (task.cacheKey ? `/tts/v1/file/${task.cacheKey}` : ''),
        taskId: task.taskId,
        durationMs: task.durationMs,
        sampleRate: task.sampleRate,
        format: task.format,
        cacheHit: task.cacheHit,
        cacheKey: task.cacheKey
      }
    }
  })
}

export function getTtsProviderStatus(): Promise<ApiResult<TtsProviderStatus[]>> {
  if (SOFTPHONE_MOCK.tts) {
    return mockDelay(
      ok([
        { provider: 'MOCK', available: true, ready: true },
        { provider: 'ALIYUN', available: false, ready: false },
        { provider: 'TENCENT', available: false, ready: false },
        { provider: 'COSYVOICE', available: false, ready: false }
      ])
    )
  }
  return Request.get('/tts/v1/providers')
}

export function getTtsRuntimeStatus(): Promise<ApiResult<TtsRuntimeStatus>> {
  if (SOFTPHONE_MOCK.tts) {
    return mockDelay(
      ok({
        configuredEngine: 'esl',
        playbackBeanCount: 1,
        playbackBeanNames: ['fsTtsPlaybackAdapter'],
        playbackImplementations: ['FsTtsPlaybackAdapter'],
        resolvedPlaybackPort: 'FsTtsPlaybackAdapter',
        uniquePlaybackBean: true
      })
    )
  }
  return Request.get('/tts/v1/runtime')
}

export function getTtsFiles(params: {
  provider?: string
  keyword?: string
  pageIndex?: number
  pageSize?: number
}): Promise<ApiResult<TtsFilePage>> {
  if (SOFTPHONE_MOCK.tts) {
    return mockDelay(ok({ list: [], total: 0, pageNum: 1, pageSize: params.pageSize || 20 }))
  }
  return Request.post('/tts/v1/file-mgr/page/list', params)
}

export function deleteTtsFile(id: number): Promise<ApiResult<void>> {
  if (SOFTPHONE_MOCK.tts) return mockDelay(ok(undefined))
  return Request.post(`/tts/v1/file-mgr/delete/${id}`)
}

/** 释放 mock 合成的 object URL（真实接口返回的是远程地址，无需释放） */
export function revokeSynthUrl(result: TtsSynthResult | null | undefined): void {
  if (result?.audioUrl && result.audioUrl.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(result.audioUrl)
    } catch {
      /* noop */
    }
  }
}
