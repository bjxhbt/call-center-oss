/**
 * 浏览器音频设备组合式封装（方案二 2.3）。
 *
 * 响应式暴露：安全上下文/支持性、麦克风权限状态、设备列表、错误。
 * 虚拟麦克风测试为独立方法，不绑定到生产呼叫流程。
 */
import { onBeforeUnmount, ref } from 'vue'
import { audioManager, AudioDevice, MicPermissionError, MicPermissionErrorKind } from '@/services/sip/audioManager'

export function useMediaDevice() {
  const canUseMic = ref(audioManager.canUseMic())
  const micReady = ref(audioManager.micReady)
  const devices = ref<AudioDevice[]>(audioManager.getDevices())
  const permissionErrorKind = ref<MicPermissionErrorKind | null>(null)
  const permissionMessage = ref<string>('')
  const testing = ref(false)

  const offMic = audioManager.on('micStateChange', (payload) => {
    micReady.value = payload.ok
    permissionErrorKind.value = payload.ok ? null : payload.kind ?? null
    permissionMessage.value = payload.ok ? '' : payload.message ?? ''
  })
  const offDev = audioManager.on('devicesChange', (list) => {
    devices.value = list
  })

  async function refreshDevices() {
    canUseMic.value = audioManager.canUseMic()
    devices.value = await audioManager.enumerateAudioDevices()
  }

  async function requestMic(): Promise<boolean> {
    permissionErrorKind.value = null
    permissionMessage.value = ''
    try {
      await audioManager.ensureMicAvailable()
      micReady.value = true
      await refreshDevices()
      return true
    } catch (error) {
      if (error instanceof MicPermissionError) {
        permissionErrorKind.value = error.kind
        permissionMessage.value = error.message
      } else {
        permissionMessage.value = (error as Error)?.message || '麦克风获取失败'
      }
      return false
    }
  }

  /**
   * 虚拟麦克风测试：注入合成媒体流自检音频链路。
   * 仅用于联调/演示，不影响真实呼叫。
   */
  async function virtualMicTest(): Promise<MediaStream | null> {
    testing.value = true
    try {
      return await audioManager.testWithVirtualMic()
    } catch (error) {
      permissionMessage.value = (error as Error)?.message || '虚拟麦克风不可用'
      return null
    } finally {
      testing.value = false
    }
  }

  onBeforeUnmount(() => {
    offMic()
    offDev()
  })

  return {
    canUseMic,
    micReady,
    devices,
    permissionErrorKind,
    permissionMessage,
    testing,
    refreshDevices,
    requestMic,
    virtualMicTest
  }
}
