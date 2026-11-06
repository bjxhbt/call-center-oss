import { describe, it, expect } from 'vitest'
import {
  buildTtsParams,
  countTextChars,
  validateTtsParams,
  toTtsPayload
} from '@/utils/ttsParams'
import { DEFAULT_TTS_PARAMS } from '@/consts/softphone'

describe('ttsParams', () => {
  it('buildTtsParams 合并默认值', () => {
    const p = buildTtsParams({ text: '你好', voice: 'xiaoyun' })
    expect(p.text).toBe('你好')
    expect(p.voice).toBe('xiaoyun')
    expect(p.provider).toBe(DEFAULT_TTS_PARAMS.provider)
    expect(p.volume).toBe(DEFAULT_TTS_PARAMS.volume)
  })

  it('countTextChars 去空白计数', () => {
    expect(countTextChars('  你好 世界  ')).toBe(5)
    expect(countTextChars('')).toBe(0)
  })

  it('validateTtsParams 空文本报错', () => {
    const issues = validateTtsParams(buildTtsParams({ voice: 'xiaoyun' }))
    expect(issues.some((i) => i.field === 'text')).toBe(true)
  })

  it('validateTtsParams 缺音色报错', () => {
    const issues = validateTtsParams(buildTtsParams({ text: '你好' }))
    expect(issues.some((i) => i.field === 'voice')).toBe(true)
  })

  it('validateTtsParams 超长文本报错', () => {
    const issues = validateTtsParams(buildTtsParams({ text: '字'.repeat(501), voice: 'v' }))
    expect(issues.some((i) => i.field === 'text')).toBe(true)
  })

  it('validateTtsParams 越界 rate/volume 报错', () => {
    const issues = validateTtsParams(
      buildTtsParams({ text: '你好', voice: 'v', rate: 9999, volume: 200 })
    )
    expect(issues.some((i) => i.field === 'rate')).toBe(true)
    expect(issues.some((i) => i.field === 'volume')).toBe(true)
  })

  it('validateTtsParams 合法参数无问题', () => {
    const issues = validateTtsParams(
      buildTtsParams({ text: '你好', provider: 'aliyun', voice: 'xiaoyun', format: 'mp3' })
    )
    expect(issues).toHaveLength(0)
  })

  it('toTtsPayload 对齐后端统一 TTS 契约', () => {
    const payload = toTtsPayload(
      buildTtsParams({ text: ' hi ', provider: 'aliyun', voice: 'v', rate: 100, pitch: -250, volume: 50 })
    )
    expect(payload.text).toBe('hi')
    expect(payload.provider).toBe('ALIYUN')
    expect(payload.speed).toBe(0.2)
    expect(payload.pitch).toBe(-0.5)
    expect(payload.volume).toBe(0.5)
    expect(payload.format).toBe(DEFAULT_TTS_PARAMS.format.toUpperCase())
  })
})
