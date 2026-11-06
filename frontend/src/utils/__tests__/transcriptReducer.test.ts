import { describe, it, expect } from 'vitest'
import {
  applyTranscriptSegment,
  applyTranscriptSegments,
  clearTranscriptSegments,
  composeFinalText,
  initialTranscriptState,
  setTranscriptStatus,
  transcriptStats
} from '@/utils/transcriptReducer'
import { TranscriptSegment } from '@/consts/softphone'

function partial(segmentId: string, text: string, speaker?: TranscriptSegment['speaker']): TranscriptSegment {
  return { segmentId, text, isFinal: false, speaker }
}
function finalSeg(segmentId: string, text: string, speaker?: TranscriptSegment['speaker']): TranscriptSegment {
  return { segmentId, text, isFinal: true, speaker }
}

describe('transcriptReducer', () => {
  it('初始状态为空', () => {
    expect(initialTranscriptState.segments).toHaveLength(0)
    expect(initialTranscriptState.status).toBe('idle')
  })

  it('同 segmentId 的 partial 覆盖文本，不新增段落', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', '你好'))
    state = applyTranscriptSegment(state, partial('s1', '你好世界'))
    expect(state.segments).toHaveLength(1)
    expect(state.segments[0].text).toBe('你好世界')
    expect(state.segments[0].isFinal).toBe(false)
  })

  it('final 替换该段临时文本', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', '你好'))
    state = applyTranscriptSegment(state, finalSeg('s1', '你好，世界'))
    expect(state.segments[0].isFinal).toBe(true)
    expect(state.segments[0].text).toBe('你好，世界')
  })

  it('final 锁定后，后续 partial 被忽略（防止回退）', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, finalSeg('s1', '最终'))
    state = applyTranscriptSegment(state, partial('s1', '被忽略的临时'))
    expect(state.segments[0].text).toBe('最终')
    expect(state.segments[0].isFinal).toBe(true)
  })

  it('不同 segmentId 新增段落', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', 'A'))
    state = applyTranscriptSegment(state, partial('s2', 'B'))
    expect(state.segments).toHaveLength(2)
  })

  it('没有 segmentId 时按 seq 去重', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, { segmentId: '', seq: 1, text: 'A', isFinal: false })
    state = applyTranscriptSegment(state, { segmentId: '', seq: 1, text: 'AB', isFinal: false })
    expect(state.segments).toHaveLength(1)
    expect(state.segments[0].text).toBe('AB')
  })

  it('applyTranscriptSegments 批量保持顺序', () => {
    const state = applyTranscriptSegments(initialTranscriptState, [
      finalSeg('s1', '第一句'),
      finalSeg('s2', '第二句')
    ])
    expect(state.segments.map((s) => s.text)).toEqual(['第一句', '第二句'])
  })

  it('composeFinalText 仅拼接 final', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', '临时'))
    state = applyTranscriptSegment(state, finalSeg('s2', '最终句'))
    expect(composeFinalText(state)).toBe('最终句')
  })

  it('保留已有 speaker，partial 不覆盖为空', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', '你好', 'customer'))
    state = applyTranscriptSegment(state, partial('s1', '你好世界')) // 不带 speaker
    expect(state.segments[0].speaker).toBe('customer')
  })

  it('clearTranscriptSegments 清空段落但保留状态结构', () => {
    let state = applyTranscriptSegment(initialTranscriptState, finalSeg('s1', 'x'))
    state = clearTranscriptSegments(state)
    expect(state.segments).toHaveLength(0)
  })

  it('setTranscriptStatus 设置异常状态', () => {
    const state = setTranscriptStatus(initialTranscriptState, 'unavailable', 'ASR 离线')
    expect(state.status).toBe('unavailable')
    expect(state.error).toBe('ASR 离线')
  })

  it('transcriptStats 统计', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, partial('s1', '临时'))
    state = applyTranscriptSegment(state, finalSeg('s2', '最终'))
    const stats = transcriptStats(state)
    expect(stats.total).toBe(2)
    expect(stats.finalCount).toBe(1)
    expect(stats.charCount).toBe(2)
  })
})
