const WAVE_CONTAINER_IDS = ['RIFF', 'RIFX', 'RF64']

function readAscii(bytes: Uint8Array, start: number, end: number) {
  return String.fromCharCode(...bytes.slice(start, end))
}

export async function ensurePlayableRecordingBlob(blob: Blob): Promise<Blob> {
  if (!blob || blob.size < 12) {
    throw new Error('录音文件为空或尚未完整上传')
  }

  const header = new Uint8Array(await blob.slice(0, 12).arrayBuffer())
  const containerId = readAscii(header, 0, 4)
  const format = readAscii(header, 8, 12)
  if (!WAVE_CONTAINER_IDS.includes(containerId) || format !== 'WAVE') {
    throw new Error('服务器未返回有效的 WAV 录音，请检查录音文件或登录状态')
  }

  if (blob.type === 'audio/wav' || blob.type === 'audio/x-wav' || blob.type === 'audio/wave') {
    return blob
  }
  return blob.slice(0, blob.size, 'audio/wav')
}
