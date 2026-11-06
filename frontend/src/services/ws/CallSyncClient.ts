/**
 * 统一 WebSocket 客户端单例（方案二 2.4 / 9.7）。
 *
 * 保证全局只有一个 WS 连接，负责：
 * - 拼接 ws/wss 地址（禁止 HTTPS 页面使用不安全 ws）
 * - 登录握手、心跳、断线指数退避重连（上限次数）
 * - 解析统一事件结构（WsMessage），分发到具体事件
 * - 透传来电/通话状态/转写事件给上层 store
 *
 * 旧的 src/components/WebSocketClient.ts 作为兼容 shim 委托到这里。
 */
import { Emitter } from '@/utils/emitter'
import { WsEvent, WsMessage, WsEventName } from '@/consts/softphone'
import { getToken } from '@/utils/local'

const MAX_RECONNECT_ATTEMPTS = 5
const HEARTBEAT_INTERVAL_MS = 10000

export interface CallSyncEvents {
  open: { sessionId: string }
  close: { code: number; reason: string; willReconnect: boolean }
  succeed: { sessionId: string }
  heartbeatAck: void
  agentStatus: any
  incomingCall: any
  callPark: any
  callAnswer: any
  callBridge: any
  callHangup: any
  callFail: any
  transcriptPartial: any
  transcriptFinal: any
  transcriptStatus: any
  raw: WsMessage
  error: { error: any }
}

function toEventName(value: unknown): WsEventName {
  const name = String(value || '')
  return (Object.values(WsEvent) as string[]).includes(name) ? (name as WsEventName) : WsEvent.UNKNOWN
}

function safeParse(data: any): WsMessage | null {
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return null
    }
  }
  if (data && typeof data === 'object') return data as WsMessage
  return null
}

function normalizePayload(data: any): any {
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return data
    }
  }
  return data
}

class CallSyncClient extends Emitter<CallSyncEvents> {
  private socket: WebSocket | null = null
  private heartbeatTimer: any = null
  private reconnectAttempts = 0
  private reconnectTimer: any = null
  private sessionId = ''
  private connecting = false
  /** 主动关闭标记，true 时不重连 */
  private intentionalClose = false

  isConnected(): boolean {
    return !!this.socket && this.socket.readyState === WebSocket.OPEN
  }

  getSessionId(): string {
    return this.sessionId
  }

  /** 建立（或复用）唯一连接 */
  connect(): void {
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED && this.socket.readyState !== WebSocket.CLOSING) {
      return
    }
    if (this.connecting) return
    this.connecting = true
    this.intentionalClose = false

    const token = getToken()
    const loc = window.location
    const wsProto = loc.protocol === 'https:' ? 'wss' : 'ws'
    const wsUrl = `${wsProto}://${loc.host}/ws?token=${encodeURIComponent(token)}`
    let socket: WebSocket
    try {
      socket = new WebSocket(wsUrl)
    } catch (error) {
      this.connecting = false
      this.emit('error', { error })
      this.scheduleReconnect()
      return
    }
    this.socket = socket

    socket.onopen = () => {
      this.connecting = false
      this.reconnectAttempts = 0
      this.startHeartbeat()
      this.emit('open', { sessionId: this.sessionId })
    }

    socket.onmessage = (event) => {
      const message = safeParse(event.data)
      if (!message) return
      this.routeMessage(message)
    }

    socket.onclose = (event) => {
      this.connecting = false
      this.stopHeartbeat()
      const willReconnect = !this.intentionalClose && this.reconnectAttempts < MAX_RECONNECT_ATTEMPTS
      this.emit('close', { code: event.code, reason: event.reason, willReconnect })
      if (this.intentionalClose) return
      this.scheduleReconnect()
    }

    socket.onerror = (error) => {
      this.emit('error', { error })
      // 触发 onclose 由其处理重连
      try {
        socket.close()
      } catch {
        /* noop */
      }
    }
  }

  /** 主动关闭（不重连） */
  disconnect(code = 1000): void {
    this.intentionalClose = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.socket) {
      try {
        this.socket.close(code)
      } catch {
        /* noop */
      }
    }
    this.socket = null
  }

  private scheduleReconnect(): void {
    if (this.intentionalClose) return
    if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      this.emit('error', { error: new Error('WebSocket 重连失败，请检查网络') })
      return
    }
    if (this.reconnectTimer) return
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 10000)
    this.reconnectAttempts += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.send({ sessionId: this.sessionId, timestamp: Date.now(), event: WsEvent.HEARTBEAT, data: {} })
    }, HEARTBEAT_INTERVAL_MS)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /** 发送一条消息（仅当连接 OPEN） */
  send(message: WsMessage): boolean {
    if (!this.isConnected()) return false
    try {
      const wireMessage = {
        ...message,
        data: typeof message.data === 'string' ? message.data : JSON.stringify(message.data ?? {})
      }
      this.socket!.send(JSON.stringify(wireMessage))
      return true
    } catch (error) {
      this.emit('error', { error })
      return false
    }
  }

  /** 上报坐席状态 */
  sendAgentStatus(agentId: number, onlineStatus: string): boolean {
    return this.send({
      sessionId: this.sessionId,
      timestamp: Date.now(),
      event: WsEvent.AGENT_STATUS,
      data: { agentId, onlineStatus: Number(onlineStatus) }
    })
  }

  private routeMessage(message: WsMessage): void {
    const event = toEventName(message.event)
    const data = normalizePayload(message.data)
    this.emit('raw', message)

    switch (event) {
      case WsEvent.SUCCEED:
        this.sessionId = message.sessionId || data?.sessionId || this.sessionId
        this.emit('succeed', { sessionId: this.sessionId })
        break
      case WsEvent.HEARTBEAT:
        this.emit('heartbeatAck', undefined)
        break
      case WsEvent.AGENT_STATUS:
        this.emit('agentStatus', data)
        break
      case WsEvent.INCOMING_CALL:
        this.emit('incomingCall', data)
        break
      case WsEvent.CALL_PARK:
        this.emit('callPark', data)
        break
      case WsEvent.CALL_ANSWER:
        this.emit('callAnswer', data)
        break
      case WsEvent.CALL_BRIDGE:
        this.emit('callBridge', data)
        break
      case WsEvent.CALL_HANGUP:
        this.emit('callHangup', data)
        break
      case WsEvent.CALL_FAIL:
        this.emit('callFail', data)
        break
      case WsEvent.TRANSCRIPT_PARTIAL:
        this.emit('transcriptPartial', data)
        break
      case WsEvent.TRANSCRIPT_FINAL:
        this.emit('transcriptFinal', data)
        break
      case WsEvent.TRANSCRIPT_STATUS:
        this.emit('transcriptStatus', data)
        break
      default:
        // 未知事件仅记录，不打断
        break
    }
  }
}

/** 单例 */
export const callSyncClient = new CallSyncClient()
