/**
 * Mock TTS 合成器（方案二 8.6）。
 *
 * 在真实 TTS 云服务未接入时，根据文本长度与服务商/音色/语速生成一段
 * 短提示音 WAV（16-bit PCM），用于验证试听控件的播放/暂停/继续/重播。
 *
 * 不进入生产链路；联调真实 TTS 后由 tts.ts 切换走真实接口。
 */
import { TtsParams } from '@/consts/softphone'

export interface MockSynthResult {
  audioUrl: string
  durationMs: number
  sampleRate: number
}

const VOICE_FREQ: Record<string, number> = {
  // 不同音色映射到不同基频，便于试听区分
  'xiaoyun': 440,
  'xiaogang': 220,
  'ruoxi': 520,
  'siyue': 330,
  'default': 360
}

/** 生成 16-bit PCM WAV Blob URL */
function encodeWavPcm(samples: Float32Array, sampleRate: number): string {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)

  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i))
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true) // PCM chunk size
  view.setUint16(20, 1, true) // audio format = PCM
  view.setUint16(22, 1, true) // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // byte rate
  view.setUint16(32, 2, true) // block align
  view.setUint16(34, 16, true) // bits per sample
  writeString(36, 'data')
  view.setUint32(40, samples.length * 2, true)

  let offset = 44
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    offset += 2
  }

  const blob = new Blob([view.buffer], { type: 'audio/wav' })
  return URL.createObjectURL(blob)
}

/** 合成一段反映参数的提示音 */
export function synthesizeMockAudio(params: TtsParams): Promise<MockSynthResult> {
  return new Promise((resolve) => {
    const sampleRate = params.sampleRate || 16000
    const text = String(params.text || '')
    // 文本越长时长越长；语速越大时长越短（rate 0~500）
    const rateFactor = 1 - (Number(params.rate) || 0) / 1000 // -500→1.5, 0→1, 500→0.5
    const baseSeconds = Math.min(6, Math.max(0.8, text.length * 0.12))
    const seconds = Math.max(0.6, baseSeconds * rateFactor)
    const total = Math.floor(sampleRate * seconds)

    const freq = VOICE_FREQ[params.voice] || VOICE_FREQ.default
    const volume = Math.max(0, Math.min(1, (Number(params.volume) || 50) / 100))
    const pitchShift = (Number(params.pitch) || 0) / 500 // -1~1

    const samples = new Float32Array(total)
    for (let i = 0; i < total; i++) {
      const t = i / sampleRate
      // 基音 + 二次谐波，包络做淡入淡出避免爆音
      const envelope = Math.min(1, t * 8, (seconds - t) * 8)
      const f = freq * (1 + pitchShift * 0.5)
      const wave =
        Math.sin(2 * Math.PI * f * t) * 0.6 + Math.sin(2 * Math.PI * f * 2 * t) * 0.25
      samples[i] = wave * envelope * volume * 0.8
    }

    const audioUrl = encodeWavPcm(samples, sampleRate)
    resolve({ audioUrl, durationMs: Math.round(seconds * 1000), sampleRate })
  })
}

/** 释放 mock 合成产生的 object URL */
export function revokeMockAudio(url: string): void {
  if (url && url.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(url)
    } catch {
      /* noop */
    }
  }
}
