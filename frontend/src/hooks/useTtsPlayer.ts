/**
 * TTS 试听组合式封装（方案二 8.6）。
 *
 * 管理：
 * - 合成请求状态：idle/generating/ready/error（防止重复提交）
 * - 播放状态：playing/paused/ended（防止重复播放绑定）
 * - 播放控件：试听、暂停、继续、重新播放
 * - 资源释放：切换/卸载时 revoke object URL、解绑事件
 */
import { onBeforeUnmount, ref, shallowRef } from 'vue'
import { TtsParams } from '@/consts/softphone'
import { synthesize, revokeSynthUrl, TtsSynthResult } from '@/apis/softphone/tts'

export type TtsGenerateStatus = 'idle' | 'generating' | 'ready' | 'error'
export type TtsPlayStatus = 'stopped' | 'playing' | 'paused' | 'ended'

export function useTtsPlayer() {
  const generateStatus = ref<TtsGenerateStatus>('idle')
  const playStatus = ref<TtsPlayStatus>('stopped')
  const errorMessage = ref<string>('')
  const currentTime = ref(0)
  const duration = ref(0)

  const audio = shallowRef<HTMLAudioElement | null>(null)
  const currentResult = ref<TtsSynthResult | null>(null)
  const lastParams = ref<TtsParams | null>(null)

  let bound = false

  function ensureAudio(): HTMLAudioElement {
    let el = audio.value
    if (!el) {
      el = new Audio()
      audio.value = el
    }
    bindAudioEvents(el)
    return el
  }

  function bindAudioEvents(el: HTMLAudioElement) {
    if (bound) return
    bound = true
    el.addEventListener('timeupdate', onTimeUpdate)
    el.addEventListener('loadedmetadata', onLoadedMeta)
    el.addEventListener('ended', onEnded)
    el.addEventListener('error', onError)
    el.addEventListener('play', onPlay)
    el.addEventListener('pause', onPause)
  }

  function onTimeUpdate() {
    currentTime.value = audio.value?.currentTime || 0
  }
  function onLoadedMeta() {
    duration.value = audio.value?.duration || 0
  }
  function onEnded() {
    playStatus.value = 'ended'
  }
  function onError() {
    playStatus.value = 'stopped'
    errorMessage.value = '音频加载失败，请重试'
    generateStatus.value = 'error'
  }
  function onPlay() {
    playStatus.value = 'playing'
  }
  function onPause() {
    if (playStatus.value === 'playing') playStatus.value = 'paused'
  }

  /** 合成（防止重复提交） */
  async function generate(params: TtsParams): Promise<boolean> {
    if (generateStatus.value === 'generating') return false
    generateStatus.value = 'generating'
    errorMessage.value = ''
    lastParams.value = { ...params }
    try {
      const res: any = await synthesize(params)
      if (res?.code !== 200) {
        generateStatus.value = 'error'
        errorMessage.value = res?.msg || '合成失败'
        return false
      }
      // 切换音频前释放旧的 object URL
      revokeSynthUrl(currentResult.value || undefined)
      currentResult.value = res.data as TtsSynthResult
      const el = ensureAudio()
      el.src = currentResult.value.audioUrl
      el.load()
      generateStatus.value = 'ready'
      playStatus.value = 'stopped'
      currentTime.value = 0
      duration.value = (currentResult.value.durationMs || 0) / 1000
      return true
    } catch (error: any) {
      generateStatus.value = 'error'
      errorMessage.value = error?.message || '合成失败，请稍后重试'
      return false
    }
  }

  /** 试听/继续播放 */
  async function play(): Promise<void> {
    const el = audio.value
    if (!el || !currentResult.value) return
    try {
      await el.play()
    } catch (error: any) {
      if (error?.name !== 'AbortError') {
        errorMessage.value = '播放失败：' + (error?.message || '未知错误')
      }
    }
  }

  function pause(): void {
    audio.value?.pause()
  }

  /** 重新播放（从头） */
  async function replay(): Promise<void> {
    const el = audio.value
    if (!el) return
    el.currentTime = 0
    await play()
  }

  function seek(time: number): void {
    if (audio.value) audio.value.currentTime = time
  }

  function reset(): void {
    const el = audio.value
    if (el) {
      el.pause()
      el.removeAttribute('src')
      el.load()
    }
    revokeSynthUrl(currentResult.value || undefined)
    currentResult.value = null
    generateStatus.value = 'idle'
    playStatus.value = 'stopped'
    errorMessage.value = ''
    currentTime.value = 0
    duration.value = 0
  }

  onBeforeUnmount(() => {
    const el = audio.value
    if (el) {
      el.removeEventListener('timeupdate', onTimeUpdate)
      el.removeEventListener('loadedmetadata', onLoadedMeta)
      el.removeEventListener('ended', onEnded)
      el.removeEventListener('error', onError)
      el.removeEventListener('play', onPlay)
      el.removeEventListener('pause', onPause)
      el.pause()
    }
    revokeSynthUrl(currentResult.value || undefined)
    bound = false
  })

  return {
    generateStatus,
    playStatus,
    errorMessage,
    currentTime,
    duration,
    currentResult,
    lastParams,
    generate,
    play,
    pause,
    replay,
    seek,
    reset
  }
}
