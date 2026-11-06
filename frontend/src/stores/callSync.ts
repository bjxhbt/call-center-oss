/**
 * WebSocket 状态同步 store（方案二 2.4 / 9.7）。
 *
 * - reactive 暴露 WS 连接状态、sessionId、错误
 * - 维护当前通话的 callId / sessionId，供软电话 store 与转写 store 过滤事件
 * - 订阅 callSyncClient 连接级事件；通话级事件由软电话 store 消费
 *
 * 全局只有一个连接（callSyncClient 单例），避免重复建立多个连接。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { callSyncClient } from '@/services/ws/CallSyncClient'

let bound = false

export const useCallSyncStore = defineStore('softphone-call-sync', () => {
  const connected = ref(false)
  const connecting = ref(false)
  const sessionId = ref('')
  const wsError = ref<string>('')
  const reconnecting = ref(false)

  /** 当前活动通话标识（用于过滤非当前通话事件） */
  const currentCallId = ref<string>('')
  const currentUniqueId = ref<string>('')
  /** 业务 sessionId（区别于 WS sessionId，部分后端用 sessionId 标识通话会话） */
  const currentCallSessionId = ref<string>('')

  function bindOnce() {
    if (bound) return
    bound = true
    callSyncClient.on('open', () => {
      connected.value = true
      connecting.value = false
      reconnecting.value = false
      wsError.value = ''
    })
    callSyncClient.on('close', (payload) => {
      connected.value = false
      connecting.value = false
      reconnecting.value = payload.willReconnect
      if (!payload.willReconnect) {
        wsError.value = '实时通道已断开'
      }
    })
    callSyncClient.on('succeed', (payload) => {
      sessionId.value = payload.sessionId
    })
    callSyncClient.on('error', (payload) => {
      wsError.value = 'WebSocket 错误，部分实时功能可能不可用'
      console.warn('[callSync] error', payload?.error)
    })
  }

  function init() {
    bindOnce()
    connecting.value = true
    callSyncClient.connect()
  }

  function disconnect() {
    callSyncClient.disconnect(1000)
  }

  function setActiveCall(ids: { callId?: string; uniqueId?: string; sessionId?: string }) {
    if (ids.callId !== undefined) currentCallId.value = ids.callId || ''
    if (ids.uniqueId !== undefined) currentUniqueId.value = ids.uniqueId || ''
    if (ids.sessionId !== undefined) currentCallSessionId.value = ids.sessionId || ''
  }

  function clearActiveCall() {
    currentCallId.value = ''
    currentUniqueId.value = ''
    currentCallSessionId.value = ''
  }

  function updateAgentStatus(agentId: number, onlineStatus: string) {
    return callSyncClient.sendAgentStatus(agentId, onlineStatus)
  }

  return {
    connected,
    connecting,
    sessionId,
    wsError,
    reconnecting,
    currentCallId,
    currentUniqueId,
    currentCallSessionId,
    bindOnce,
    init,
    disconnect,
    setActiveCall,
    clearActiveCall,
    updateAgentStatus
  }
})
