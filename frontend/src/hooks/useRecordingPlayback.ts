import { onBeforeUnmount, ref } from 'vue'
import { loadRecordingAudio } from '@/apis/callingConfigure/callRecord'
import { ensurePlayableRecordingBlob } from '@/utils/recordingAudio'

export function useRecordingPlayback() {
  const playUrl = ref('')
  const playLoading = ref(false)
  let requestVersion = 0

  const releasePlayUrl = () => {
    if (playUrl.value.startsWith('blob:')) {
      URL.revokeObjectURL(playUrl.value)
    }
    playUrl.value = ''
  }

  const loadRecording = async (callId: string | number) => {
    const currentVersion = ++requestVersion
    releasePlayUrl()
    playLoading.value = true
    try {
      const responseBlob = await loadRecordingAudio(callId)
      const audioBlob = await ensurePlayableRecordingBlob(responseBlob)
      if (currentVersion !== requestVersion) return false
      playUrl.value = URL.createObjectURL(audioBlob)
      return true
    } finally {
      if (currentVersion === requestVersion) {
        playLoading.value = false
      }
    }
  }

  const resetRecording = () => {
    requestVersion++
    playLoading.value = false
    releasePlayUrl()
  }

  onBeforeUnmount(resetRecording)

  return {
    playUrl,
    playLoading,
    loadRecording,
    resetRecording
  }
}
