/**
 * 实时转写 store（方案二 7.13）。
 *
 * - 消费 callSyncClient 的 TRANSCRIPT_PARTIAL / FINAL / STATUS 事件
 * - 用纯 reducer 维护增量/最终段落（同 segmentId 覆盖，final 锁定）
 * - 提供 Mock 模式，ASR 未就绪时也能验证面板
 * - ASR 失败只标记转写不可用，不影响通话
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  TranscriptSegment,
  TranscriptSessionStatus
} from '@/consts/softphone'
import {
  TranscriptState,
  initialTranscriptState,
  applyTranscriptSegment,
  setTranscriptStatus,
  clearTranscriptSegments,
  composeFinalText,
  transcriptStats
} from '@/utils/transcriptReducer'
import { callSyncClient } from '@/services/ws/CallSyncClient'
import { startMockTranscript } from '@/services/asr/MockTranscriptSource'

/** 历史会话摘要 */
export interface TranscriptHistoryItem {
  id: string
  callId?: string
  caller?: string
  text: string
  createdAt: number
  segmentCount: number
}

let bound = false
let mockStop: (() => void) | null = null

export const useTranscriptionStore = defineStore('softphone-transcription', () => {
  const segments = ref<TranscriptSegment[]>([])
  const status = ref<TranscriptSessionStatus>('idle')
  const error = ref<string | undefined>(undefined)
  const autoScroll = ref(true)
  const expanded = ref(true)
  const history = ref<TranscriptHistoryItem[]>([])

  /** 当前绑定的 callId，用于过滤 */
  const callId = ref<string>('')

  const state = computed<TranscriptState>(() => ({
    segments: segments.value,
    status: status.value,
    error: error.value
  }))

  const finalText = computed(() => composeFinalText(state.value))
  const stats = computed(() => transcriptStats(state.value))

  /** 订阅 callSyncClient（只订阅一次） */
  function bindOnce() {
    if (bound) return
    bound = true
    callSyncClient.on('transcriptPartial', (data) => {
      if (!belongsToCurrent(data)) return
      applyIncoming(toSegment(data, false))
    })
    callSyncClient.on('transcriptFinal', (data) => {
      if (!belongsToCurrent(data)) return
      applyIncoming(toSegment(data, true))
    })
    callSyncClient.on('transcriptStatus', (data) => {
      if (!belongsToCurrent(data)) return
      const next = mapStatus(data?.status || data?.code)
      status.value = next
      error.value = next === 'error' || next === 'unavailable' ? data?.message || '转写不可用' : undefined
    })
  }

  function belongsToCurrent(data: any): boolean {
    if (!data) return true
    const incomingCallId = data.callId || data.callID
    // 没有当前 callId 或事件未带 callId 时不过滤
    if (!callId.value || !incomingCallId) return true
    return String(incomingCallId) === String(callId.value)
  }

  function setCallId(id: string) {
    callId.value = id || ''
  }

  function applyIncoming(segment: TranscriptSegment | null) {
    if (!segment) return
    const next = applyTranscriptSegment(
      { segments: segments.value, status: status.value, error: error.value },
      segment
    )
    segments.value = next.segments
    if (status.value === 'idle') status.value = 'active'
  }

  function applySegment(segment: TranscriptSegment) {
    applyIncoming(segment)
  }

  function clear() {
    segments.value = clearTranscriptSegments({
      segments: segments.value,
      status: status.value,
      error: error.value
    }).segments
    error.value = undefined
  }

  function markUnavailable(message = '转写不可用') {
    status.value = 'unavailable'
    error.value = message
  }

  function reset() {
    segments.value = []
    status.value = 'idle'
    error.value = undefined
    stopMock()
  }

  function toggleAutoScroll() {
    autoScroll.value = !autoScroll.value
  }

  function toggleExpanded() {
    expanded.value = !expanded.value
  }

  /** 把当前会话存入历史并清空 */
  function saveToHistory(meta?: { callId?: string; caller?: string }) {
    const text = composeFinalText(state.value)
    if (!text && segments.value.length === 0) return
    const item: TranscriptHistoryItem = {
      id: `th-${Date.now()}`,
      callId: meta?.callId || callId.value,
      caller: meta?.caller,
      text,
      createdAt: Date.now(),
      segmentCount: segments.value.length
    }
    history.value = [item, ...history.value].slice(0, 50)
    reset()
    return item
  }

  function startMock() {
    bindOnce()
    stopMock()
    status.value = 'active'
    error.value = undefined
    segments.value = []
    mockStop = startMockTranscript({
      onSegment: (segment) => applySegment(segment),
      onDone: () => {
        status.value = 'idle'
      }
    })
  }

  function stopMock() {
    if (mockStop) {
      mockStop()
      mockStop = null
    }
  }

  return {
    segments,
    status,
    error,
    autoScroll,
    expanded,
    history,
    callId,
    finalText,
    stats,
    bindOnce,
    setCallId,
    applySegment,
    clear,
    markUnavailable,
    reset,
    toggleAutoScroll,
    toggleExpanded,
    saveToHistory,
    startMock,
    stopMock
  }
})

function toSegment(data: any, isFinal: boolean): TranscriptSegment | null {
  if (!data) return null
  const segmentId = data.segmentId || data.segment_id || data.id
  const seq = typeof data.seq === 'number' ? data.seq : undefined
  const text = typeof data.text === 'string' ? data.text : data.content
  if (!text && !segmentId) return null
  const speaker = normalizeSpeaker(data.speaker || data.role || data.channel)
  return {
    segmentId: segmentId ? String(segmentId) : `seq:${seq ?? Date.now()}`,
    seq,
    text: text || '',
    isFinal,
    speaker,
    timestamp: typeof data.timestamp === 'number' ? data.timestamp : Date.now(),
    confidence: typeof data.confidence === 'number' ? data.confidence : undefined
  }
}

function normalizeSpeaker(value: any): TranscriptSegment['speaker'] {
  const v = String(value || '').toLowerCase()
  if (v === 'customer' || v === 'callee' || v === 'client' || v === '0' || v === 'left') return 'customer'
  if (v === 'agent' || v === 'caller' || v === '1' || v === 'right') return 'agent'
  return 'unknown'
}

function mapStatus(value: any): TranscriptSessionStatus {
  const v = String(value || '').toLowerCase()
  if (v === 'active' || v === 'ok' || v === 'running') return 'active'
  if (v === 'connecting' || v === 'pending') return 'connecting'
  if (v === 'unavailable' || v === 'disabled' || v === 'off') return 'unavailable'
  if (v === 'error' || v === 'failed') return 'error'
  if (v === 'idle') return 'idle'
  return 'active'
}
