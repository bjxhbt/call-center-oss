/**
 * Mock 实时转写数据源（方案二 7.13）。
 *
 * 在真实 ASR 未就绪时，按脚本产生增量/最终结果，验证转写面板。
 * 不进入生产呼叫链路；由 transcription store 在 Mock 模式下显式启动。
 */
import { TranscriptSegment } from '@/consts/softphone'

interface ScriptLine {
  speaker: 'customer' | 'agent'
  parts: string[] // 拆成多段，前几段为 partial，最后拼成 final
}

const SCRIPT: ScriptLine[] = [
  { speaker: 'customer', parts: ['您好', '您好，我想', '您好，我想咨询一下'] },
  { speaker: 'agent', parts: ['好的', '好的，请问您', '好的，请问您的账号是多少'] },
  { speaker: 'customer', parts: ['我的账号', '我的账号是 10086', '我的账号是 10086，姓名张三'] },
  { speaker: 'agent', parts: ['好的张先生', '好的张先生，我帮您查一下', '好的张先生，我帮您查一下，请稍等'] },
  { speaker: 'customer', parts: ['好的', '好的，谢谢你'] }
]

export interface MockTranscriptOptions {
  /** 每段 partial 间隔毫秒 */
  intervalMs?: number
  /** 收到一段结果时回调 */
  onSegment: (segment: TranscriptSegment) => void
  /** 脚本播放结束回调 */
  onDone?: () => void
}

/**
 * 启动一次 Mock 转写会话，返回停止函数。
 */
export function startMockTranscript(options: MockTranscriptOptions): () => void {
  const intervalMs = options.intervalMs ?? 700
  let lineIdx = 0
  let partIdx = 0
  let segmentSeq = 0
  let timer: any = null
  let stopped = false

  const emit = () => {
    if (stopped) return
    const line = SCRIPT[lineIdx]
    if (!line) {
      stop()
      options.onDone?.()
      return
    }
    const partial = line.parts.slice(0, partIdx + 1).join('')
    const isFinal = partIdx === line.parts.length - 1
    segmentSeq += 1
    const segmentId = `mock-${lineIdx}`
    options.onSegment({
      segmentId,
      seq: segmentSeq,
      text: partial,
      isFinal,
      speaker: line.speaker,
      timestamp: Date.now()
    })
    if (isFinal) {
      lineIdx += 1
      partIdx = 0
    } else {
      partIdx += 1
    }
  }

  timer = setInterval(emit, intervalMs)
  // 立即发一段，便于演示
  emit()

  function stop() {
    stopped = true
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  return stop
}
