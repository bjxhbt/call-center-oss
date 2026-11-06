import { describe, expect, it } from 'vitest'
import { ensurePlayableRecordingBlob } from '@/utils/recordingAudio'

function wavBytes() {
  return new Uint8Array([
    0x52, 0x49, 0x46, 0x46,
    0x24, 0x00, 0x00, 0x00,
    0x57, 0x41, 0x56, 0x45,
    0x66, 0x6d, 0x74, 0x20
  ])
}

describe('ensurePlayableRecordingBlob', () => {
  it('accepts a WAV response and supplies the audio MIME type', async () => {
    const source = new Blob([wavBytes()], { type: 'application/octet-stream' })

    const result = await ensurePlayableRecordingBlob(source)

    expect(result.size).toBe(source.size)
    expect(result.type).toBe('audio/wav')
  })

  it('keeps an existing WAV MIME type', async () => {
    const source = new Blob([wavBytes()], { type: 'audio/wav' })

    await expect(ensurePlayableRecordingBlob(source)).resolves.toBe(source)
  })

  it('rejects an authentication or JSON error response', async () => {
    const source = new Blob(['{"code":401}'], { type: 'application/json' })

    await expect(ensurePlayableRecordingBlob(source)).rejects.toThrow('有效的 WAV')
  })

  it('rejects an empty response', async () => {
    await expect(ensurePlayableRecordingBlob(new Blob())).rejects.toThrow('为空')
  })
})
