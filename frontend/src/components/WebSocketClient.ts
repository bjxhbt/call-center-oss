/**
 * 兼容 shim：保留旧导出（initWebsocketClient / closeWebSocket /
 * updateAgentStatus / onIncomingCall），内部委托到唯一连接 callSyncClient。
 *
 * 这样 SoftPhone / HomeView 等历史调用方无需改动，同时保证全局只有一个
 * WebSocket 连接（方案二 2.4 / 9.7）。
 */
import { callSyncClient } from '@/services/ws/CallSyncClient'

let incomingCallListener: ((data: any) => void) | null = null
let subscribed = false

function ensureIncomingSubscription() {
  if (subscribed) return
  subscribed = true
  callSyncClient.on('incomingCall', (data) => {
    if (incomingCallListener && data) {
      incomingCallListener(data)
    }
  })
}

export const initWebsocketClient = () => {
  ensureIncomingSubscription()
  callSyncClient.connect()
}

export const closeWebSocket = () => {
  callSyncClient.disconnect(1000)
}

export const updateAgentStatus = (agentId: number, status: string) => {
  callSyncClient.sendAgentStatus(agentId, status)
}

/** 注册来电通知回调（单槽，与历史行为一致） */
export const onIncomingCall = (callback: (data: any) => void) => {
  ensureIncomingSubscription()
  incomingCallListener = callback
}
