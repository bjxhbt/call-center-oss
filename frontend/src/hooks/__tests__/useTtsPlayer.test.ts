/**
 * useTtsPlayer 试听控件用例（§11.3 #5）：重复点击保护、生成失败、试听文件切换、资源释放。
 * 直接 stub 全局 Audio / URL，避免依赖 DOM 实现；mock @/apis/softphone/tts 控制合成结果。
 */
import { describe, it, expect, beforeEach, beforeAll, vi } from 'vitest'

// useTtsPlayer 在组合式 setup 之外调用时，onBeforeUnmount 会输出无害告警，测试中静音
beforeAll(() => {
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})

// ---- stub 全局 Audio / URL ----
class FakeAudio {
  src = ''
  volume = 1
  currentTime = 0
  duration = 0
  paused = true
  private listeners: Record<string, Array<(e?: any) => void>> = {}
  play() {
    this.paused = false
    return Promise.resolve()
  }
  pause() {
    this.paused = true
  }
  load() {
    /* noop */
  }
  removeAttribute() {
    this.src = ''
  }
  addEventListener(ev: string, fn: any) {
    ;(this.listeners[ev] ||= []).push(fn)
  }
  removeEventListener(ev: string, fn: any) {
    this.listeners[ev] = (this.listeners[ev] || []).filter((h) => h !== fn)
  }
  // 测试辅助：触发事件
  _emit(ev: string, payload?: any) {
    ;(this.listeners[ev] || []).forEach((fn) => fn(payload))
  }
}

const createObjectURL = vi.fn((_: Blob) => 'blob:fake')
const revokeObjectURL = vi.fn((_: string) => undefined)

;(globalThis as any).Audio = FakeAudio
;(globalThis as any).URL = { ...(globalThis as any).URL, createObjectURL, revokeObjectURL }

// ---- mock tts 接口 ----
const synthMock = vi.fn()
vi.mock('@/apis/softphone/tts', () => ({
  synthesize: (...args: any[]) => synthMock(...args),
  revokeSynthUrl: (r: any) => {
    if (r?.audioUrl && String(r.audioUrl).startsWith('blob:')) revokeObjectURL(r.audioUrl)
  }
}))

import { useTtsPlayer } from '@/hooks/useTtsPlayer'
import { buildTtsParams } from '@/utils/ttsParams'

const params = () => buildTtsParams({ text: '测试合成', provider: 'aliyun', voice: 'xiaoyun', format: 'mp3' })

function okResult(url: string) {
  return { code: 200, msg: '', data: { audioUrl: url, durationMs: 1000, sampleRate: 16000, format: 'mp3', taskId: 't1' } }
}

describe('useTtsPlayer', () => {
  beforeEach(() => {
    synthMock.mockReset()
    revokeObjectURL.mockClear()
  })

  it('生成成功：status idle→generating→ready，且记录结果', async () => {
    synthMock.mockResolvedValue(okResult('blob:aaa'))
    const player = useTtsPlayer()
    const ok = await player.generate(params())
    expect(ok).toBe(true)
    expect(player.generateStatus.value).toBe('ready')
    expect(player.currentResult.value?.audioUrl).toBe('blob:aaa')
  })

  it('重复点击保护：生成中再次 generate 立即返回 false，且只合成一次', async () => {
    let resolveFn: any
    synthMock.mockReturnValue(new Promise((r) => (resolveFn = r)))
    const player = useTtsPlayer()
    const p1 = player.generate(params()) // 未 await，进入 generating
    // 并发再次调用：应被 guard 拦截
    const second = await player.generate(params())
    expect(second).toBe(false)
    resolveFn(okResult('blob:bbb'))
    await p1
    expect(synthMock).toHaveBeenCalledTimes(1)
    expect(player.currentResult.value?.audioUrl).toBe('blob:bbb')
  })

  it('生成失败（接口非200）：status=error，记录错误原因', async () => {
    synthMock.mockResolvedValue({ code: 500, msg: '额度不足', data: null })
    const player = useTtsPlayer()
    const ok = await player.generate(params())
    expect(ok).toBe(false)
    expect(player.generateStatus.value).toBe('error')
    expect(player.errorMessage.value).toContain('额度不足')
  })

  it('生成异常（reject）：status=error，兜底错误信息', async () => {
    synthMock.mockRejectedValue(new Error('网络错误'))
    const player = useTtsPlayer()
    const ok = await player.generate(params())
    expect(ok).toBe(false)
    expect(player.generateStatus.value).toBe('error')
    expect(player.errorMessage.value).toContain('网络错误')
  })

  it('试听文件切换：第二次生成会释放旧 blob URL', async () => {
    synthMock.mockResolvedValueOnce(okResult('blob:old')).mockResolvedValueOnce(okResult('blob:new'))
    const player = useTtsPlayer()
    await player.generate(params())
    expect(player.currentResult.value?.audioUrl).toBe('blob:old')
    await player.generate(params())
    expect(player.currentResult.value?.audioUrl).toBe('blob:new')
    // 旧 blob:old 应被 revoke
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:old')
  })

  it('reset：清空结果与状态', async () => {
    synthMock.mockResolvedValue(okResult('blob:clr'))
    const player = useTtsPlayer()
    await player.generate(params())
    player.reset()
    expect(player.generateStatus.value).toBe('idle')
    expect(player.currentResult.value).toBeNull()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:clr')
  })
})
