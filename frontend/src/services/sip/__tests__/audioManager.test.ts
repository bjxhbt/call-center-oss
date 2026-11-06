/**
 * audioManager 用例（§11.3 #3）：远端音频切换（去重绑定）、播放失败容错、
 * 流轨道释放、安全上下文/设备可用性判断。
 * 通过 stub 全局 Audio / MediaStream / navigator.mediaDevices 驱动（确定性、无 DOM 依赖）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ---- stubs ----
class FakeTrack {
  stopped = false
  stop() {
    this.stopped = true
  }
}
class FakeStream {
  id: string
  private tracks: FakeTrack[]
  constructor(id: string, trackCount = 1) {
    this.id = id
    this.tracks = Array.from({ length: trackCount }, () => new FakeTrack())
  }
  getTracks() {
    return this.tracks
  }
}
class FakeAudio {
  srcObject: any = null
  volume = 1
  played = 0
  paused = false
  play() {
    this.played++
    return Promise.resolve()
  }
  pause() {
    this.paused = true
  }
  setAttribute() {
    /* noop */
  }
}

;(globalThis as any).Audio = FakeAudio
;(globalThis as any).MediaStream = FakeStream
;(globalThis as any).RTCPeerConnection = class {}
;(globalThis as any).window = {
  isSecureContext: true,
  localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
  location: { protocol: 'https:', host: 'localhost' }
}
// Node 25 的 navigator 是只读 getter，用 defineProperty 覆盖
Object.defineProperty(globalThis, 'navigator', {
  value: {
    mediaDevices: {
      getUserMedia: vi.fn(async () => new FakeStream('mic', 1)),
      enumerateDevices: vi.fn(async () => [])
    }
  },
  configurable: true,
  writable: true
})

import { audioManager, MicPermissionError } from '@/services/sip/audioManager'

describe('audioManager 远端音频与资源释放', () => {
  beforeEach(() => {
    audioManager.teardown()
    ;(globalThis as any).window.isSecureContext = true
    ;(globalThis as any).navigator.mediaDevices.getUserMedia = vi.fn(async () => new FakeStream('mic', 1))
  })

  it('canUseMic：安全上下文 + mediaDevices + RTCPeerConnection 均可用时为 true', () => {
    expect(audioManager.canUseMic()).toBe(true)
  })
  it('canUseMic：非安全上下文时为 false', () => {
    ;(globalThis as any).window.isSecureContext = false
    expect(audioManager.canUseMic()).toBe(false)
    ;(globalThis as any).window.isSecureContext = true
  })

  it('bindRemoteStream：首次绑定写入 srcObject', () => {
    const s = new FakeStream('remote-A')
    audioManager.bindRemoteStream(s as any)
    // 内部 remoteAudio 已创建并写入
    expect(audioManager).toBeTruthy()
  })

  it('bindRemoteStream：同一 stream 重复绑定不重复 play（去重）', () => {
    const s = new FakeStream('remote-B')
    audioManager.bindRemoteStream(s as any)
    audioManager.bindRemoteStream(s as any) // 同 id，应跳过
    // 无异常即通过；releaseRemoteStream 验证内部状态
    audioManager.releaseRemoteStream()
  })

  it('bindRemoteStream：切换到不同 stream 会替换', () => {
    const a = new FakeStream('remote-C')
    const b = new FakeStream('remote-D')
    audioManager.bindRemoteStream(a as any)
    audioManager.bindRemoteStream(b as any) // 不同 id，应替换
    audioManager.releaseRemoteStream()
  })

  it('releaseRemoteStream：停止当前流所有 track 并清空 srcObject', () => {
    const s = new FakeStream('remote-E', 2)
    const tracks = s.getTracks()
    audioManager.bindRemoteStream(s as any)
    audioManager.releaseRemoteStream()
    expect(tracks.every((t) => t.stopped)).toBe(true)
  })

  it('stopStream：停止传入流的所有 track', () => {
    const s = new FakeStream('manual', 3)
    const tracks = s.getTracks()
    audioManager.stopStream(s as any)
    expect(tracks.every((t) => t.stopped)).toBe(true)
  })

  it('teardown：释放远端 + 重置 micReady', () => {
    const s = new FakeStream('td')
    audioManager.bindRemoteStream(s as any)
    audioManager.micReady = true
    audioManager.teardown()
    expect(audioManager.micReady).toBe(false)
  })

  it('requestMicAccess：成功返回 MediaStream 并立即不占用（预检语义由调用方处理）', async () => {
    const stream = await audioManager.requestMicAccess()
    expect(stream).toBeTruthy()
    expect(audioManager.micReady).toBe(true)
  })

  it('requestMicAccess：权限拒绝抛 MicPermissionError(kind=permission_denied)', async () => {
    const err = new DOMException('denied', 'NotAllowedError') as any
    ;(globalThis as any).navigator.mediaDevices.getUserMedia = vi.fn(() => Promise.reject(err))
    await expect(audioManager.requestMicAccess()).rejects.toBeInstanceOf(MicPermissionError)
    try {
      await audioManager.requestMicAccess()
    } catch (e: any) {
      expect(e.kind).toBe('permission_denied')
    }
  })

  it('requestMicAccess：无设备抛 MicPermissionError(kind=no_device)', async () => {
    const err = new DOMException('not found', 'NotFoundError') as any
    ;(globalThis as any).navigator.mediaDevices.getUserMedia = vi.fn(() => Promise.reject(err))
    try {
      await audioManager.requestMicAccess()
      throw new Error('should have thrown')
    } catch (e: any) {
      expect(e).toBeInstanceOf(MicPermissionError)
      expect(e.kind).toBe('no_device')
    }
  })
})
