/**
 * JsSIP UA 单例服务（方案二 2.1 / 2.2）。
 *
 * 负责：
 * - UA 的创建、启动、停止、注册、注销（唯一实例）
 * - 拨号、接听、拒接、挂断、保持、恢复、静音、转接
 * - 会话事件归一化，向上层（store）发射结构化事件
 * - 切换账号/退出时清理定时器、监听器与 Contact
 *
 * 不直接持有 UI 状态；UI 通过 store 订阅。
 */
import JsSIP from 'jssip'
import { Emitter } from '@/utils/emitter'
import { NetworkStatus, SIP_OPERATION_TIMEOUT } from '@/consts/softphone'
import {
  getStableSipInstanceId,
  isRegistrationCleanupDone,
  markRegistrationCleanupDone
} from './instanceId'
import { audioManager } from './audioManager'
import { requestUserMedia } from '@/utils/browserCompat'

export interface SipAccountConfig {
  sipUser: string
  password: string
  sipHost: string
  /** 明文 ws 端口（http 页面） */
  wsPort?: number
  /** wss 端口（https 页面），缺省时回退 7443 */
  wssPort?: number
  /** 后端直接给出的完整 WebSocket 地址（优先级最高） */
  wsServers?: string
  noAnswerTimeout?: number
}

export type SessionOriginator = 'local' | 'remote'

export interface SipServiceEvents {
  network: NetworkStatus
  registered: void
  unregistered: void
  registrationFailed: { cause?: string; statusCode?: number; reasonPhrase?: string }
  /** 新的 RTC 会话（呼出/呼入） */
  newSession: {
    session: any
    originator: SessionOriginator
    direction: 'incoming' | 'outgoing'
    callId: string
    uniqueId: string
    remoteNumber: string
    localNumber: string
  }
  /** 呼出/呼入振铃 */
  progress: { session: any; originator: SessionOriginator }
  /** 通话已建立（confirmed） */
  confirmed: { session: any }
  /** 通话失败 */
  failed: {
    session: any
    originator?: SessionOriginator
    cause?: string
    statusCode?: number
    reasonPhrase?: string
  }
  /** 通话正常结束 */
  ended: { session: any; originator?: SessionOriginator; cause?: string }
  /** UA 内部诊断日志 */
  log: { level: 'info' | 'warn' | 'error'; message: string; extra?: any }
}

export interface DialOptions {
  mediaConstraints?: MediaStreamConstraints
  extraHeaders?: string[]
}

function buildWsUrl(config: SipAccountConfig): string {
  const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:'
  // 后端直接给出的完整地址优先；但 HTTPS 页面禁止使用不安全 ws
  if (config.wsServers) {
    if (isHttps && config.wsServers.startsWith('ws://')) {
      // 强制升级为 wss
      return config.wsServers.replace(/^ws:\/\//, 'wss://')
    }
    return config.wsServers
  }
  const host = config.sipHost
  if (isHttps) {
    const port = config.wssPort && config.wssPort > 0 ? config.wssPort : 7443
    return `wss://${host}:${port}`
  }
  const port = config.wsPort && config.wsPort > 0 ? config.wsPort : 5066
  return `ws://${host}:${port}`
}

class SipService extends Emitter<SipServiceEvents> {
  private ua: JsSIP.UA | null = null
  private config: SipAccountConfig | null = null
  private currentSession: any = null
  private incomingSession: any = null
  /** 标记当前 UA 是否绑定过事件，避免重复绑定 */
  private eventsBound = false

  constructor() {
    super()
  }

  isStarted(): boolean {
    return !!this.ua
  }

  isRegistered(): boolean {
    return !!this.ua && this.ua.isRegistered()
  }

  getConfig(): SipAccountConfig | null {
    return this.config
  }

  getCurrentSession(): any {
    return this.currentSession
  }

  getIncomingSession(): any {
    return this.incomingSession
  }

  /** 配置（不创建 UA） */
  configure(config: SipAccountConfig): void {
    this.config = config
  }

  /**
   * 创建并启动 UA（register:false，注册由 register() 显式触发）。
   * 若已存在 UA，先停止。
   */
  async start(config?: SipAccountConfig): Promise<boolean> {
    if (config) this.configure(config)
    if (!this.config) {
      this.emit('log', { level: 'warn', message: 'SIP 配置为空，无法启动' })
      return false
    }
    if (!this.config.sipUser || !this.config.sipHost) {
      this.emit('log', { level: 'warn', message: 'SIP user/host 缺失' })
      return false
    }
    if (this.ua) {
      await this.stop({ unregister: false })
    }

    const instanceId = getStableSipInstanceId()
    const wsUrl = buildWsUrl(this.config)
    const contactHost = `och-${instanceId.replace(/-/g, '')}.invalid`
    const contactTransport = wsUrl.startsWith('wss:') ? 'wss' : 'ws'
    const socket = new JsSIP.WebSocketInterface(wsUrl)
    const uri = `sip:${this.config.sipUser}@${this.config.sipHost};transport=ws`

    const ua = new JsSIP.UA({
      sockets: [socket],
      uri,
      contact_uri: `sip:${this.config.sipUser}@${contactHost};transport=${contactTransport}`,
      instance_id: instanceId,
      password: this.config.password,
      register: false,
      register_expires: 30,
      session_timers: false,
      no_answer_timeout: this.config.noAnswerTimeout ?? 60
    })

    this.ua = ua
    this.eventsBound = false
    this.bindUaEvents(ua)
    this.emit('network', NetworkStatus.CONNECTING)
    ua.start()
    this.emit('log', { level: 'info', message: 'SIP UA 已启动', extra: { wsUrl, user: this.config.sipUser } })
    return true
  }

  private bindUaEvents(ua: JsSIP.UA): void {
    if (this.eventsBound) return
    this.eventsBound = true
    const isCurrent = () => this.ua === ua

    ua.on('connected', () => {
      if (!isCurrent()) return
      this.emit('network', NetworkStatus.CONNECTED)
    })
    ua.on('disconnected', (event: any) => {
      if (!isCurrent()) return
      this.emit('network', NetworkStatus.DISCONNECTED)
      this.emit('log', { level: 'warn', message: 'SIP WebSocket 断开', extra: event })
    })
    ua.on('registered', () => {
      if (!isCurrent()) return
      this.emit('registered', undefined)
    })
    ua.on('unregistered', () => {
      if (!isCurrent()) return
      this.emit('unregistered', undefined)
    })
    ua.on('registrationFailed', (event: any) => {
      if (!isCurrent()) return
      const cause = event?.cause
      const statusCode = event?.response?.status_code
      const reasonPhrase = event?.response?.reason_phrase
      this.emit('registrationFailed', { cause, statusCode, reasonPhrase })
    })

    ua.on('newRTCSession', (data: any) => {
      if (!isCurrent()) return
      const session = data?.session
      if (!session) return
      const originator: SessionOriginator = data.originator === 'remote' ? 'remote' : 'local'
      const direction = session.direction || (originator === 'remote' ? 'incoming' : 'outgoing')

      // 若已有活动会话且新的呼入到达，按忙处理（避免覆盖当前通话）
      if (this.currentSession && originator === 'remote') {
        try {
          session.terminate({ status_code: 486, reason_phrase: 'Busy Here' })
        } catch (error) {
          this.emit('log', { level: 'warn', message: '终止并发呼入失败', extra: error })
        }
        return
      }

      const callId = this.readSipHeader(data.request, 'X-OCH-Call-ID') || this.readSipHeader(session, 'X-OCH-Call-ID')
      const uniqueId = this.readSipHeader(data.request, 'X-OCH-Unique-ID') || this.readSipHeader(session, 'X-OCH-Unique-ID')
      const remoteNumber = session?.remote_identity?.uri?.user || ''
      const localNumber = session?.local_identity?.uri?.user || this.config?.sipUser || ''

      // 记录到 session.data，便于后续挂断回查
      session.data = { ...(session.data || {}), callId, uniqueId }

      this.currentSession = session
      if (originator === 'remote') this.incomingSession = session

      this.bindSessionEvents(session, originator)
      this.bindRemoteAudio(session)

      this.emit('newSession', {
        session,
        originator,
        direction,
        callId,
        uniqueId,
        remoteNumber,
        localNumber
      })
    })
  }

  private bindSessionEvents(session: any, originator: SessionOriginator): void {
    session.on('progress', () => {
      this.emit('progress', { session, originator })
    })
    session.on('accepted', () => {
      // accepted 早于 confirmed，这里不单独暴露，统一等 confirmed
    })
    session.on('confirmed', () => {
      this.syncRemoteAudio(session?.connection)
      audioManager.resumeRemotePlayback()
      this.emit('confirmed', { session })
    })
    session.on('failed', (event: any) => {
      this.emit('failed', {
        session,
        originator: event?.originator === 'local' || event?.originator === 'remote' ? event.originator : originator,
        cause: event?.cause,
        statusCode: event?.message?.status_code,
        reasonPhrase: event?.message?.reason_phrase
      })
      this.cleanupSession(session)
    })
    session.on('ended', (event: any) => {
      this.emit('ended', {
        session,
        originator: event?.originator === 'local' || event?.originator === 'remote' ? event.originator : originator,
        cause: event?.cause
      })
      this.cleanupSession(session)
    })
  }

  private bindRemoteAudio(session: any): void {
    if (!session || typeof session.on !== 'function') return
    session.on('peerconnection', (data: any) => {
      this.attachRemoteAudio(data?.peerconnection)
    })
    // 某些 JsSIP/浏览器组合会在 newRTCSession 回调前创建 PeerConnection。
    // 直接补绑现有连接，避免错过 peerconnection/track 事件后整通电话无声。
    this.attachRemoteAudio(session.connection)
  }

  private attachRemoteAudio(pc: RTCPeerConnection | null | undefined): void {
    if (!pc || (pc as any).__ochRemoteAudioBound) return
    ;(pc as any).__ochRemoteAudioBound = true

    const fallbackStream = new MediaStream()
    const handleTrack = (event: RTCTrackEvent | any) => {
      const stream = event?.streams?.[0] as MediaStream | undefined
      if (stream) {
        audioManager.bindRemoteStream(stream)
        return
      }
      const track = event?.track as MediaStreamTrack | undefined
      if (!track) return
      if (!fallbackStream.getTracks().some((item) => item.id === track.id)) {
        fallbackStream.addTrack(track)
      }
      audioManager.bindRemoteStream(fallbackStream)
    }

    pc.addEventListener?.('track', handleTrack)
    ;(pc as any).onaddstream = (event: any) => audioManager.bindRemoteStream(event?.stream)
    this.syncRemoteAudio(pc, fallbackStream)
  }

  private syncRemoteAudio(
    pc: RTCPeerConnection | null | undefined,
    targetStream: MediaStream = new MediaStream()
  ): void {
    if (!pc) return
    const tracks = pc
      .getReceivers?.()
      .map((receiver) => receiver.track)
      .filter((track): track is MediaStreamTrack => !!track && track.kind === 'audio') || []
    tracks.forEach((track) => {
      if (!targetStream.getTracks().some((item) => item.id === track.id)) targetStream.addTrack(track)
    })
    if (targetStream.getAudioTracks().length) audioManager.bindRemoteStream(targetStream)
  }

  private cleanupSession(session: any): void {
    if (!session) return
    try {
      const pc = session?.connection
      pc?.getSenders?.().forEach((sender: RTCRtpSender) => sender.track?.stop())
      pc?.getReceivers?.().forEach((receiver: RTCRtpReceiver) => receiver.track?.stop())
      pc?.close?.()
    } catch (error) {
      this.emit('log', { level: 'warn', message: '关闭 PeerConnection 失败', extra: error })
    }
    if (this.currentSession === session) this.currentSession = null
    if (this.incomingSession === session) this.incomingSession = null
  }

  /** 读取 SIP 自定义头（兼容多种 JsSIP 对象形态） */
  readSipHeader(source: any, name: string): string {
    if (!source) return ''
    try {
      if (typeof source.getHeader === 'function') return source.getHeader(name) || ''
      if (typeof source.get_header === 'function') return source.get_header(name) || ''
      if (source.request) return this.readSipHeader(source.request, name)
      if (source._request) return this.readSipHeader(source._request, name)
    } catch (error) {
      this.emit('log', { level: 'warn', message: `读取 SIP 头失败 ${name}`, extra: error })
    }
    return ''
  }

  /** 等待 UA 一次操作完成（注册/注销），带超时 */
  private waitForOperation(
    ua: JsSIP.UA,
    successEvent: 'registered' | 'unregistered',
    failureEvent: 'registrationFailed' | null,
    operation: () => void
  ): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      let timer: any = null
      const anyUa = ua as any
      const cleanup = () => {
        if (timer) clearTimeout(timer)
        anyUa.removeListener(successEvent, onSuccess)
        if (failureEvent) anyUa.removeListener(failureEvent, onFailure)
      }
      const onSuccess = () => {
        cleanup()
        resolve()
      }
      const onFailure = (event: any) => {
        cleanup()
        reject(
          new Error(event?.cause || event?.response?.reason_phrase || 'SIP operation failed')
        )
      }
      anyUa.once(successEvent, onSuccess)
      if (failureEvent) anyUa.once(failureEvent, onFailure)
      timer = setTimeout(() => {
        cleanup()
        reject(new Error(`SIP ${successEvent} timeout`))
      }, SIP_OPERATION_TIMEOUT)
      try {
        operation()
      } catch (error) {
        cleanup()
        reject(error)
      }
    })
  }

  /**
   * 注册当前账号。
   * 首次注册成功后执行一次 unregister(all)+register，清理 FreeSWITCH 残留 Contact。
   */
  async register(): Promise<void> {
    const ua = this.ua
    if (!ua) throw new Error('UA 未启动')
    if (!ua.isRegistered()) {
      await this.waitForOperation(ua, 'registered', 'registrationFailed', () => ua.register())
    }
    if (!this.config) return
    const host = this.config.sipHost
    const user = this.config.sipUser
    if (isRegistrationCleanupDone(host, user)) return
    this.emit('log', { level: 'info', message: '清理旧 SIP Contact', extra: { host, user } })
    await this.waitForOperation(ua, 'unregistered', null, () => ua.unregister({ all: true }))
    await this.waitForOperation(ua, 'registered', 'registrationFailed', () => ua.register())
    markRegistrationCleanupDone(host, user)
  }

  /** 注销 */
  async unregister(all = true): Promise<void> {
    const ua = this.ua
    if (!ua || !ua.isRegistered()) return
    await this.waitForOperation(ua, 'unregistered', null, () => ua.unregister({ all }))
  }

  /**
   * 停止 UA：注销 + 停止 + 清理会话与音频。
   * @param opts.unregister 是否先注销（默认 true）
   */
  async stop(opts: { unregister?: boolean } = {}): Promise<void> {
    const ua = this.ua
    this.ua = null
    const shouldUnregister = opts.unregister !== false
    try {
      if (ua && shouldUnregister) await this.unregisterSafe(ua)
    } catch (error) {
      this.emit('log', { level: 'warn', message: '注销失败', extra: error })
    } finally {
      try {
        ua?.stop?.()
      } catch (error) {
        this.emit('log', { level: 'warn', message: '停止 UA 失败', extra: error })
      }
    }
    this.cleanupSession(this.currentSession)
    this.cleanupSession(this.incomingSession)
    this.currentSession = null
    this.incomingSession = null
    this.eventsBound = false
    audioManager.releaseRemoteStream()
  }

  private async unregisterSafe(ua: JsSIP.UA): Promise<void> {
    if (!ua.isRegistered()) return
    await this.waitForOperation(ua, 'unregistered', null, () => ua.unregister({ all: true }))
  }

  /** 发起呼出 */
  async call(destination: string, options: DialOptions = {}): Promise<any> {
    const ua = this.ua
    if (!ua) throw new Error('UA 未启动')
    if (!ua.isRegistered()) throw new Error('尚未签入 SIP，无法呼叫')
    if (!this.config) throw new Error('SIP 配置缺失')

    // 安全上下文与麦克风前置检查
    if (!audioManager.canUseMic()) {
      throw new Error('当前浏览器不支持软电话，请使用 HTTPS 环境并确认麦克风权限')
    }
    // 预检麦克风（申请后立即释放，避免长时间占用）
    try {
      const stream = await requestUserMedia({ audio: true, video: false })
      audioManager.stopStream(stream)
    } catch (error: any) {
      throw new Error(audioManagerErrorText(error))
    }

    const target = `sip:${destination}@${this.config.sipHost}`
    const session = ua.call(target, {
      mediaConstraints: options.mediaConstraints ?? { audio: true, video: false },
      extraHeaders: options.extraHeaders
    })
    this.currentSession = session
    return session
  }

  /** 接听来电 */
  answer(session?: any): void {
    const target = session || this.incomingSession || this.currentSession
    if (!target) throw new Error('没有可接听的通话')
    target.answer({ mediaConstraints: { audio: true, video: false } })
    // 接听按钮属于用户手势，在这里重试可解除浏览器的自动播放限制。
    audioManager.resumeRemotePlayback()
    this.currentSession = target
    this.incomingSession = null
  }

  /** 挂断（本地主动） */
  hangup(): void {
    const session = this.incomingSession || this.currentSession
    if (!session) return
    const isIncomingRinging = session.direction === 'incoming' && typeof session.isEstablished === 'function' && !session.isEstablished()
    try {
      if (isIncomingRinging) {
        session.terminate({ status_code: 486, reason_phrase: 'Busy Here' })
      } else {
        session.terminate()
      }
    } catch (error) {
      this.emit('log', { level: 'warn', message: 'JsSIP terminate 失败', extra: error })
    }
    this.cleanupSession(session)
  }

  hold(): void {
    const session = this.currentSession
    if (!session) throw new Error('没有活动通话')
    session.hold({ useUpdate: false })
  }

  unhold(): void {
    const session = this.currentSession
    if (!session) throw new Error('没有活动通话')
    session.unhold({ useUpdate: false })
  }

  mute(): void {
    const session = this.currentSession
    if (!session) throw new Error('没有活动通话')
    session.mute({ audio: true, video: false })
  }

  unmute(): void {
    const session = this.currentSession
    if (!session) throw new Error('没有活动通话')
    session.unmute({ audio: true, video: false })
  }

  transfer(target: string): void {
    const session = this.currentSession
    if (!session) throw new Error('没有活动通话')
    if (!this.config) throw new Error('SIP 配置缺失')
    session.refer(`sip:${target}@${this.config.sipHost}`)
  }

  isHeld(): boolean {
    const session = this.currentSession
    return !!session && typeof session.isOnHold === 'function' && !!session.isOnHold().local
  }

  isMuted(): boolean {
    const session = this.currentSession
    return !!session && typeof session.isMuted === 'function' && !!session.isMuted().audio
  }
}

function audioManagerErrorText(error: any): string {
  const name = String(error?.name || '').toLowerCase()
  if (name === 'notallowederror' || name === 'securityerror') return '麦克风权限被拒绝，请在浏览器中允许访问'
  if (name === 'notfounderror' || name === 'devicesnotfound') return '未检测到麦克风设备'
  if (name === 'notreadableerror') return '麦克风被其他程序占用'
  return '无法使用麦克风，请检查浏览器权限'
}

/** 单例 */
export const sipService = new SipService()
