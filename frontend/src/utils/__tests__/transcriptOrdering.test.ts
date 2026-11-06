/**
 * 转写乱序/重复用例（§11.3 #4）：同一 segmentId 增量覆盖、最终替换、
 * 乱序到达（后段先 final、前段后到）、重复 final 幂等。
 */
import { describe, it, expect } from 'vitest'
import {
  applyTranscriptSegment,
  applyTranscriptSegments,
  initialTranscriptState,
  composeFinalText
} from '@/utils/transcriptReducer'
import { TranscriptSegment } from '@/consts/softphone'

function p(id: string, text: string, speaker?: TranscriptSegment['speaker']): TranscriptSegment {
  return { segmentId: id, text, isFinal: false, speaker }
}
function f(id: string, text: string, speaker?: TranscriptSegment['speaker']): TranscriptSegment {
  return { segmentId: id, text, isFinal: true, speaker }
}

describe('乱序与重复', () => {
  it('后段先 final，前段后到：前段仍正常追加（不互相覆盖，按 segmentId 隔离）', () => {
    const state = applyTranscriptSegments(initialTranscriptState, [
      f('s2', '第二句最终'), // 后段先到并 final
      p('s1', '第一句临时'), // 前段后到（partial）
      f('s1', '第一句最终') // 前段最终
    ])
    const texts = state.segments.map((s) => s.text)
    expect(texts).toContain('第二句最终')
    expect(texts).toContain('第一句最终')
    expect(state.segments).toHaveLength(2)
  })

  it('同一 segmentId 重复 final：第二次被忽略（幂等，防重复追加）', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, f('s1', '最终文本A'))
    state = applyTranscriptSegment(state, f('s1', '最终文本B')) // 应忽略
    expect(state.segments).toHaveLength(1)
    expect(state.segments[0].text).toBe('最终文本A')
  })

  it('final 后再收到该段 partial：被忽略（不回退为临时）', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, f('s1', '最终'))
    state = applyTranscriptSegment(state, p('s1', '迟到的临时'))
    expect(state.segments[0].text).toBe('最终')
    expect(state.segments[0].isFinal).toBe(true)
  })

  it('partial 乱序更新（非递增 seq 但同 segmentId）以最后一次为准', () => {
    let state = initialTranscriptState
    state = applyTranscriptSegment(state, p('s1', '你好'))
    state = applyTranscriptSegment(state, p('s1', '你')) // 乱序回退文本
    state = applyTranscriptSegment(state, p('s1', '你好世界'))
    expect(state.segments[0].text).toBe('你好世界')
  })

  it('composeFinalText 保持 final 文本顺序（按到达顺序）', () => {
    const state = applyTranscriptSegments(initialTranscriptState, [
      f('a', '甲'),
      f('b', '乙'),
      f('c', '丙')
    ])
    expect(composeFinalText(state)).toBe('甲乙丙')
  })

  it('混合 partial 与 final：final 文本拼接送最终，partial 不计入', () => {
    const state = applyTranscriptSegments(initialTranscriptState, [
      p('s1', '临时A'),
      f('s2', '最终B'),
      p('s3', '临时C'),
      f('s4', '最终D')
    ])
    expect(composeFinalText(state)).toBe('最终B最终D')
  })
})
