/**
 * 浏览器音频管理（方案二 2.3）。
 *
 * 职责：
 * - 检测安全上下文与 mediaDevices 可用性
 * - 统一申请麦克风权限，区分无设备/拒绝/占用
 * - 绑定远端媒体流（避免 audio.play 被重复 load 中断）
 * - 挂断后停止 Track、释放 audio 元素、清理事件
 * - 振铃音播放
 * - 虚拟麦克风测试（独立开关，不写进生产呼叫流程）
 */
import { Emitter } from '@/utils/emitter'
import { requestUserMedia } from '@/utils/browserCompat'

export type MicPermissionErrorKind =
  | 'not_supported' // 浏览器/上下文不支持
  | 'permission_denied' // 用户拒绝
  | 'no_device' // 无麦克风设备
  | 'device_in_use' // 设备被占用
  | 'unknown'

export class MicPermissionError extends Error {
  kind: MicPermissionErrorKind
  constructor(kind: MicPermissionErrorKind, message: string) {
    super(message)
    this.name = 'MicPermissionError'
    this.kind = kind
  }
}

export interface AudioDevice {
  deviceId: string
  label: string
  kind: MediaDeviceKind
}

export interface AudioManagerEvents {
  /** 麦克风权限/设备状态变化 */
  micStateChange: { ok: boolean; kind?: MicPermissionErrorKind; message?: string }
  devicesChange: AudioDevice[]
}

class AudioManager extends Emitter<AudioManagerEvents> {
  /** 远端音频元素（懒创建，全局复用，避免重复 load 中断播放） */
  private remoteAudio: HTMLAudioElement | null = null
  /** 当前绑定的远端 stream id，防止重复绑定 */
  private boundRemoteStreamId: string | null = null
  /** 旧版 Chromium 不支持 audio.srcObject 时使用的临时 URL */
  private remoteObjectUrl: string | null = null
  /** 振铃音频元素 */
  private ringAudio: HTMLAudioElement | null = null
  /** 最近一次枚举的设备列表 */
  private devices: AudioDevice[] = []
  /** 麦克风是否就绪 */
  micReady = false

  constructor() {
    super()
  }

  /** 是否运行在安全上下文（HTTPS/localhost），getUserMedia 前置条件 */
  isSecureContext(): boolean {
    if (typeof window === 'undefined') return false
    // getUserMedia 在 localhost/回环地址的 HTTP 开发环境中同样被 Chromium 允许。
    // UOS 浏览器部分版本不会正确填充 isSecureContext，因此保留协议/主机回退判断。
    if (typeof window.isSecureContext === 'boolean') return window.isSecureContext
    return (
      window.location.protocol === 'https:' ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
    )
  }

  /** mediaDevices 是否可用 */
  isMediaSupported(): boolean {
    if (typeof navigator === 'undefined') return false
    const navigatorRef = navigator as Navigator & {
      getUserMedia?: unknown
      webkitGetUserMedia?: unknown
      mozGetUserMedia?: unknown
    }
    return Boolean(
      navigatorRef.mediaDevices?.getUserMedia ||
      typeof navigatorRef.getUserMedia === 'function' ||
      typeof navigatorRef.webkitGetUserMedia === 'function' ||
      typeof navigatorRef.mozGetUserMedia === 'function'
    )
  }

  /** 综合前置检查：安全上下文 + mediaDevices + WebRTC */
  canUseMic(): boolean {
    return (
      this.isSecureContext() &&
      this.isMediaSupported() &&
      (typeof RTCPeerConnection !== 'undefined' ||
        (typeof window !== 'undefined' && typeof (window as any).webkitRTCPeerConnection !== 'undefined'))
    )
  }

  /**
   * 申请麦克风权限（仅用于权限预检/设备测试）。
   * 成功后立即停止采集的 track，避免占用设备。
   */
  async requestMicAccess(): Promise<MediaStream> {
    if (!this.canUseMic()) {
      throw this.toPermissionError('not_supported')
    }
    let stream: MediaStream
    try {
      stream = await requestUserMedia({ audio: true, video: false })
    } catch (error: any) {
      throw this.toPermissionError(this.classifyError(error), error?.message)
    }
    this.micReady = true
    this.emit('micStateChange', { ok: true })
    // 预检场景立即释放
    return stream
  }

  /** 预检麦克风可用性（申请后立即释放） */
  async ensureMicAvailable(): Promise<void> {
    const stream = await this.requestMicAccess()
    this.stopStream(stream)
  }

  /** 把 DOMException/OverconstrainedError 归类 */
  private classifyError(error: any): MicPermissionErrorKind {
    const name = String(error?.name || '').toLowerCase()
    if (name === 'notallowederror' || name === 'securityerror') return 'permission_denied'
    if (name === 'notfounderror' || name === 'devicesnotfound') return 'no_device'
    if (name === 'overconstrainederror') return 'no_device'
    if (name === 'notreadableerror' || name === 'trackstarterror') return 'device_in_use'
    if (name === 'notsupportederror' || name === 'typeerror') return 'not_supported'
    return 'unknown'
  }

  private toPermissionError(kind: MicPermissionErrorKind, raw?: string): MicPermissionError {
    const messageMap: Record<MicPermissionErrorKind, string> = {
      not_supported: '当前浏览器不支持软电话，请使用 HTTPS 环境并确认浏览器支持 WebRTC',
      permission_denied: '麦克风权限被拒绝，请在浏览器设置中允许访问麦克风',
      no_device: '未检测到麦克风设备，请连接麦克风后重试',
      device_in_use: '麦克风被其他程序占用，请关闭占用程序后重试',
      unknown: raw || '麦克风获取失败'
    }
    const err = new MicPermissionError(kind, messageMap[kind])
    this.micReady = false
    this.emit('micStateChange', { ok: false, kind, message: err.message })
    return err
  }

  /** 枚举音频输入设备（需在授权后才有 label） */
  async enumerateAudioDevices(): Promise<AudioDevice[]> {
    if (!this.isMediaSupported()) return []
    try {
      if (!navigator.mediaDevices || typeof navigator.mediaDevices.enumerateDevices !== 'function') {
        return []
      }
      const list = await navigator.mediaDevices.enumerateDevices()
      this.devices = list
        .filter((d) => d.kind === 'audioinput')
        .map((d) => ({
          deviceId: d.deviceId,
          label: d.label || '麦克风',
          kind: d.kind
        }))
      this.emit('devicesChange', this.devices)
      return this.devices
    } catch (error) {
      console.warn('[audioManager] enumerateDevices failed', error)
      return []
    }
  }

  getDevices(): AudioDevice[] {
    return this.devices
  }

  /**
   * 绑定远端媒体流到全局 audio 元素。
   * 同一 stream 不重复 set srcObject，避免 play() 被 load 中断。
   */
  bindRemoteStream(stream: MediaStream | null | undefined): void {
    if (!stream) return
    if (!this.remoteAudio) {
      this.remoteAudio = new Audio()
      this.remoteAudio.autoplay = true
      this.remoteAudio.setAttribute('playsinline', '')
    }
    if (this.boundRemoteStreamId === stream.id) {
      // 浏览器可能在自动播放被暂时拦截后再次触发相同音轨，必须重试播放。
      this.playRemote()
      return
    }
    this.boundRemoteStreamId = stream.id
    const audio = this.remoteAudio as any
    if ('srcObject' in audio) {
      audio.srcObject = stream
    } else if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      try {
        if (this.remoteObjectUrl) URL.revokeObjectURL(this.remoteObjectUrl)
        this.remoteObjectUrl = URL.createObjectURL(stream as unknown as Blob)
        audio.src = this.remoteObjectUrl
      } catch (error) {
        console.warn('[audioManager] legacy stream URL binding failed', error)
      }
    }
    this.remoteAudio.volume = 1
    this.playRemote()
  }

  /** 在接听点击或会话确认后重试远端音频播放。 */
  resumeRemotePlayback(): void {
    this.playRemote()
  }

  private playRemote(): void {
    if (!this.remoteAudio) return
    const playResult = this.remoteAudio.play()
    if (playResult && typeof (playResult as Promise<void>).catch === 'function') {
      ;(playResult as Promise<void>).catch((error: any) => {
        // AbortError 多为快速切流导致，忽略即可
        if (error?.name !== 'AbortError') {
          console.error('[audioManager] play remote failed', error)
        }
      })
    }
  }

  /** 停止并释放当前绑定的远端流 */
  releaseRemoteStream(): void {
    const audio = this.remoteAudio
    if (!audio) return
    const stream = (audio as any).srcObject as MediaStream | null | undefined
    this.stopStream(stream)
    const audioWithStream = audio as any
    if ('srcObject' in audioWithStream) audioWithStream.srcObject = null
    if (!('srcObject' in audioWithStream)) audio.src = ''
    if (this.remoteObjectUrl) {
      try {
        URL.revokeObjectURL(this.remoteObjectUrl)
      } catch {
        /* noop */
      }
      this.remoteObjectUrl = null
    }
    this.boundRemoteStreamId = null
    try {
      audio.pause()
    } catch {
      /* noop */
    }
  }

  /** 停止一条 MediaStream 的所有 track */
  stopStream(stream: MediaStream | null | undefined): void {
    if (!stream) return
    try {
      stream.getTracks().forEach((track) => track.stop())
    } catch (error) {
      console.warn('[audioManager] stopStream failed', error)
    }
  }

  /** 振铃音（来电） */
  playRing(src: string): void {
    if (!this.ringAudio) {
      this.ringAudio = new Audio(src)
    } else if (this.ringAudio.src !== src) {
      this.ringAudio.src = src
    }
    this.ringAudio.loop = true
    this.ringAudio.currentTime = 0
    const result = this.ringAudio.play()
    if (result && typeof result.catch === 'function') {
      result.catch((error: any) => {
        if (error?.name !== 'AbortError') console.error('[audioManager] play ring failed', error)
      })
    }
  }

  stopRing(): void {
    if (!this.ringAudio) return
    try {
      this.ringAudio.pause()
      this.ringAudio.currentTime = 0
    } catch {
      /* noop */
    }
  }

  /**
   * 虚拟麦克风测试：在无真实设备时，可注入一个合成 MediaStream 进行自检。
   * 该方法仅用于联调/测试，不会被生产呼叫流程调用。
   */
  async testWithVirtualMic(): Promise<MediaStream> {
    const AudioCtx: typeof AudioContext | undefined =
      (window as any).AudioContext || (window as any).webkitAudioContext
    if (!AudioCtx) {
      throw new MicPermissionError('not_supported', '当前浏览器不支持 AudioContext，无法构造虚拟麦克风')
    }
    const ctx = new AudioCtx()
    const dest = ctx.createMediaStreamDestination()
    const osc = ctx.createOscillator()
    osc.frequency.value = 440
    osc.connect(dest)
    osc.start()
    return dest.stream
  }

  /** 全量释放：挂断/退出登录时调用 */
  teardown(): void {
    this.releaseRemoteStream()
    this.stopRing()
    this.remoteAudio = null
    this.ringAudio = null
    this.micReady = false
    this.boundRemoteStreamId = null
    this.remoteObjectUrl = null
  }
}

/** 单例 */
export const audioManager = new AudioManager()
