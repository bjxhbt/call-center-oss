/**
 * 实时转写增量更新 reducer（方案二 7.13）。
 *
 * 纯函数：维护一个有序段落数组。临时结果按 segmentId 覆盖同一段，
 * 最终结果替换该段的临时文本且不再被临时结果覆盖。
 *
 * 设计要点：
 * - 同 segmentId 反复推送 partial 只更新文本，不新增段落
 * - final 到达后，该段锁定为最终文本，后续 partial 被忽略
 * - 没有 segmentId 时退化为按 seq 去重
 */
import { TranscriptSegment, TranscriptSessionStatus } from '@/consts/softphone'

export interface TranscriptState {
  segments: TranscriptSegment[]
  /** 转写会话状态 */
  status: TranscriptSessionStatus
  /** 最近一次错误（仅当 status=error/unavailable 时有意义） */
  error?: string
}

export const initialTranscriptState: TranscriptState = {
  segments: [],
  status: 'idle'
}

function segmentKey(segment: TranscriptSegment): string {
  if (segment.segmentId) return segment.segmentId
  if (segment.seq !== undefined) return `seq:${segment.seq}`
  return `text:${segment.text}`
}

/**
 * 用一段新结果更新状态。
 */
export function applyTranscriptSegment(
  state: TranscriptState,
  incoming: TranscriptSegment
): TranscriptState {
  const key = segmentKey(incoming)
  const existingIdx = state.segments.findIndex((seg) => segmentKey(seg) === key)

  // 已存在同 key 的最终段：忽略后续更新（最终结果优先且不可变）
  if (existingIdx >= 0 && state.segments[existingIdx].isFinal) {
    return state
  }

  const stamped: TranscriptSegment = {
    ...incoming,
    timestamp: incoming.timestamp ?? Date.now()
  }

  if (existingIdx >= 0) {
    const next = state.segments.slice()
    // 保留原 speaker（部分 partial 不带 speaker）
    const merged: TranscriptSegment = {
      ...next[existingIdx],
      ...stamped,
      speaker: stamped.speaker || next[existingIdx].speaker
    }
    next[existingIdx] = merged
    return { ...state, segments: next }
  }

  return { ...state, segments: [...state.segments, stamped] }
}

/** 把多条结果批量应用（按到达顺序） */
export function applyTranscriptSegments(
  state: TranscriptState,
  segments: TranscriptSegment[]
): TranscriptState {
  return segments.reduce(applyTranscriptSegment, state)
}

/** 设置会话状态 */
export function setTranscriptStatus(
  state: TranscriptState,
  status: TranscriptSessionStatus,
  error?: string
): TranscriptState {
  return { ...state, status, error }
}

/** 清空段落（保留会话状态） */
export function clearTranscriptSegments(state: TranscriptState): TranscriptState {
  return { ...state, segments: [] }
}

/** 拼接所有最终文本为一段话 */
export function composeFinalText(state: TranscriptState): string {
  return state.segments
    .filter((seg) => seg.isFinal)
    .map((seg) => seg.text)
    .join('')
    .trim()
}

/** 统计 */
export function transcriptStats(state: TranscriptState) {
  const finals = state.segments.filter((seg) => seg.isFinal)
  const chars = finals.reduce((sum, seg) => sum + seg.text.length, 0)
  return {
    total: state.segments.length,
    finalCount: finals.length,
    charCount: chars
  }
}
