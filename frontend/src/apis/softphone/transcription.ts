/**
 * 转写历史接口（方案二 7.13 / 7.14）。
 *
 * 实时增量由 callSyncClient 的 TRANSCRIPT_* 事件推送（见 transcription store）；
 * 本文件仅负责按 callId 查询历史转写明细，用于“历史查看”入口。
 */
import Request from '@/utils/axios'
import { SOFTPHONE_MOCK, ApiResult, ok, mockDelay } from './index'
import { TranscriptSegment } from '@/consts/softphone'

export interface TranscriptHistoryDetail {
  callId: string
  caller?: string
  startedAt?: string
  durationSec?: number
  segments: TranscriptSegment[]
  fullText: string
}

export interface TranscriptSummary {
  id: number
  callId: string
  uniqueId?: string
  callerNumber?: string
  calleeNumber?: string
  agentId?: number
  agentNumber?: string
  agentName?: string
  direction?: number
  provider?: string
  sampleRate?: number
  fullText?: string
  segmentCount?: number
  charCount?: number
  startTime?: string
  endTime?: string
  durationMs?: number
  status?: string
  errorCode?: string
  errorMsg?: string
  recordUrl?: string
  firstTextLatencyMs?: number
  finalLatencyMs?: number
  reconnectCount?: number
  segments?: TranscriptSegment[]
}

export interface TranscriptPage {
  records: TranscriptSummary[]
  total: number
  current: number
  size: number
}

export interface TranscriptQuery {
  callId?: string
  callerNumber?: string
  calleeNumber?: string
  agentNumber?: string
  direction?: number
  status?: string
  provider?: string
  pageNum?: number
  pageSize?: number
}

function buildMockDetail(callId: string): TranscriptHistoryDetail {
  const segments: TranscriptSegment[] = [
    { segmentId: 's1', seq: 1, text: '您好，我想咨询一下套餐。', isFinal: true, speaker: 'customer' },
    { segmentId: 's2', seq: 2, text: '好的，请问您的账号是多少？', isFinal: true, speaker: 'agent' },
    { segmentId: 's3', seq: 3, text: '我的账号是 10086。', isFinal: true, speaker: 'customer' },
    { segmentId: 's4', seq: 4, text: '已为您查询，当前为尊享套餐。', isFinal: true, speaker: 'agent' }
  ]
  return {
    callId,
    caller: '13800000001',
    startedAt: '2026-07-12 10:24',
    durationSec: 128,
    segments,
    fullText: segments.map((s) => s.text).join('')
  }
}

export function getTranscriptByCallId(callId: string): Promise<ApiResult<TranscriptSummary | null>> {
  if (SOFTPHONE_MOCK.transcription) {
    if (!callId) return mockDelay(ok(null))
    const detail = buildMockDetail(callId)
    return mockDelay(
      ok({
        id: 0,
        callId: detail.callId,
        callerNumber: detail.caller,
        startTime: detail.startedAt,
        durationMs: Number(detail.durationSec || 0) * 1000,
        status: 'SUCCESS',
        fullText: detail.fullText,
        segmentCount: detail.segments.length,
        segments: detail.segments
      })
    )
  }
  return Request.post(`/asr/v1/transcript/getByCallId/${encodeURIComponent(callId)}`)
}

const MOCK_SUMMARIES: TranscriptSummary[] = [
  {
    id: 1,
    callId: '900001',
    callerNumber: '13800000001',
    calleeNumber: '10086',
    agentNumber: '8001',
    agentName: '演示坐席',
    direction: 2,
    provider: 'MOCK',
    fullText: '您好，我想咨询一下套餐。好的，请问您的账号是多少？',
    segmentCount: 4,
    charCount: 31,
    startTime: '2026-07-30 09:30:00',
    durationMs: 128000,
    status: 'SUCCESS',
    firstTextLatencyMs: 420,
    finalLatencyMs: 820
  }
]

export function getTranscriptPage(query: TranscriptQuery): Promise<ApiResult<TranscriptPage>> {
  if (SOFTPHONE_MOCK.transcription) {
    const keyword = String(query.callId || '').trim()
    const records = keyword
      ? MOCK_SUMMARIES.filter((item) => item.callId.includes(keyword))
      : MOCK_SUMMARIES
    return mockDelay(
      ok({
        records,
        total: records.length,
        current: query.pageNum || 1,
        size: query.pageSize || 20
      })
    )
  }
  return Request.post('/asr/v1/transcript/page/list', query)
}

export function getTranscriptDetail(id: number): Promise<ApiResult<TranscriptSummary | null>> {
  if (SOFTPHONE_MOCK.transcription) {
    const summary = MOCK_SUMMARIES.find((item) => item.id === id)
    if (!summary) return mockDelay(ok(null))
    const detail = buildMockDetail(summary.callId)
    return mockDelay(
      ok({
        ...summary,
        segments: detail.segments
      })
    )
  }
  return Request.post(`/asr/v1/transcript/get/${id}`)
}
