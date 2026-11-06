/**
 * 软电话核心 store（方案二 2.1 / 2.2 / 2.3 / 2.4 / 2.5）。
 *
 * 全局唯一 UA 来源：通过单例 sipService 持有，页面切换不会重复创建 UA、
 * 重复注册或重复绑定事件。所有 UI 通过 useSoftphone() 订阅本 store。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  AGENT_STATUS_MAP,
  CallDirection,
  CallState,
  NetworkStatus,
  RegisterStatus
} from '@/consts/softphone'
import { reduceCallState, settleEndedState, CallEvent } from '@/utils/callStateMachine'
import { describeSipFailure, describeRegistrationFailure } from '@/utils/sipErrorMap'
import { formatDuration, sanitizeDial } from '@/utils/format'
import { sipService, SipAccountConfig } from '@/services/sip/SipService'
import { audioManager } from '@/services/sip/audioManager'
import { callSyncClient } from '@/services/ws/CallSyncClient'
import { getSipConfig, updateAgentOnlineStatus } from '@/apis/callingConfigure/agent'
import { forceHangupAgent } from '@/apis/callingConfigure/callRecord'
import { useCallSyncStore } from './callSync'
import { useTranscriptionStore } from './transcription'
import ringSrc from '@/assets/ringing.wav'

export interface SipAccount {
  agentId: number
  agentName?: string
  sipUser: string
  onlineStatus?: number
  password: string
  sipHost: string
  wsPort?: number
  wssPort?: number
  wsServers?: string
}

export interface SoftphoneError {
  message: string
  level: 'info' | 'warning' | 'error' | 'success'
  /** 是否用户主动行为（不展示为系统错误） */
  intentional?: boolean
}

let sipBound = false
let callSyncBound = false

export const useSoftphoneStore = defineStore('softphone', () => {
  const callSync = useCallSyncStore()
  const transcript = useTranscriptionStore()

  const sipAccountList = ref<SipAccount[]>([])
  const selectedAgentId = ref<number | null>(null)

  /** 登录态：'1'=未登录(UA未启动) '2'=已登录(UA已启动) */
  const loginState = ref<'1' | '2'>('1')
  /** 签入态：'1'=已注册 '2'=未注册 */
  const signInOut = ref<'1' | '2'>('2')
  const sipRegistrationChanging = ref(false)
  const isLoginLoading = ref(false)

  const agentStatus = ref<string>('4')
  const autoAnswer = ref(false)

  const registerStatus = ref<RegisterStatus>(RegisterStatus.UNREGISTERED)
  const networkStatus = ref<NetworkStatus>(NetworkStatus.DISCONNECTED)
  const callState = ref<CallState>(CallState.IDLE)

  // 当前通话信息
  const callId = ref<string>('')
  const uniqueId = ref<string>('')
  const remoteNumber = ref<string>('')
  const localNumber = ref<string>('')
  const direction = ref<CallDirection | null>(null)
  const isHold = ref(false)
  const isMute = ref(false)

  const incomingCallFlag = ref(false)
  const incomingCallNumber = ref('')

  const phoneNumber = ref('')
  const transferModalVisible = ref(false)
  const transferNumber = ref('')

  const lastError = ref<SoftphoneError | null>(null)

  // 状态计时
  const statusDuration = ref('00:00:00')
  const statusStartedAt = ref<number>(Date.now())
  let statusTimer: any = null

  const selectedAccount = computed(
    () => sipAccountList.value.find((item) => item.agentId === selectedAgentId.value) || null
  )
  const isSignedIn = computed(() => signInOut.value === '1')
  const canDial = computed(
    () =>
      (callState.value === CallState.REGISTERED || callState.value === CallState.IDLE) &&
      registerStatus.value === RegisterStatus.REGISTERED
  )
  const hasActiveCall = computed(
    () =>
      callState.value === CallState.ACTIVE ||
      callState.value === CallState.HOLD ||
      callState.value === CallState.RINGING ||
      callState.value === CallState.INCOMING ||
      callState.value === CallState.DIALING
  )

  /** 把错误推到 UI（用户主动挂断不计为系统错误） */
  function notifyError(error: SoftphoneError | null) {
    if (!error) {
      lastError.value = null
      return
    }
    lastError.value = error
    if (error.intentional) {
      // 主动操作仅提示，不报红
      if (error.message) message.info(error.message)
      return
    }
    if (error.level === 'error') message.error(error.message)
    else if (error.level === 'warning') message.warning(error.message)
    else if (error.level === 'success') message.success(error.message)
    else if (error.message) message.info(error.message)
  }

  function transition(event: CallEvent): boolean {
    const next = reduceCallState(callState.value, event)
    if (next === null) return false
    callState.value = next
    return true
  }

  function startStatusTimer() {
    stopStatusTimer()
    statusStartedAt.value = Date.now()
    statusTimer = setInterval(() => {
      const seconds = Math.floor((Date.now() - statusStartedAt.value) / 1000)
      statusDuration.value = formatDuration(seconds)
    }, 1000)
  }

  function stopStatusTimer() {
    if (statusTimer) {
      clearInterval(statusTimer)
      statusTimer = null
    }
  }

  function resetStatusTimer() {
    startStatusTimer()
  }

  /** 订阅 sipService（仅一次） */
  function bindSipOnce() {
    if (sipBound) return
    sipBound = true
    sipService.on('network', (status) => {
      networkStatus.value = status
      if (status === NetworkStatus.DISCONNECTED && registerStatus.value === RegisterStatus.REGISTERED) {
        registerStatus.value = RegisterStatus.DISCONNECTED
      }
    })
    sipService.on('registered', () => {
      registerStatus.value = RegisterStatus.REGISTERED
      networkStatus.value = NetworkStatus.CONNECTED
      callState.value = CallState.REGISTERED
      signInOut.value = '1'
      void applyAgentStatus('1')
    })
    sipService.on('unregistered', () => {
      registerStatus.value = RegisterStatus.UNREGISTERED
      if (!hasActiveCall.value) callState.value = CallState.IDLE
      signInOut.value = '2'
      void applyAgentStatus('4')
    })
    sipService.on('registrationFailed', (info) => {
      registerStatus.value = RegisterStatus.FAILED
      signInOut.value = '2'
      void applyAgentStatus('4')
      const msg = describeRegistrationFailure(info.cause, info.statusCode)
      notifyError({ message: msg, level: 'error' })
    })
    sipService.on('newSession', (info) => {
      callId.value = info.callId
      uniqueId.value = info.uniqueId
      remoteNumber.value = info.remoteNumber
      localNumber.value = info.localNumber
      direction.value = info.direction === 'incoming' ? CallDirection.INBOUND : CallDirection.OUTBOUND
      callSync.setActiveCall({ callId: info.callId, uniqueId: info.uniqueId })
      transcript.setCallId(info.callId)
      if (info.direction === 'incoming') {
        incomingCallFlag.value = true
        incomingCallNumber.value = info.remoteNumber
        phoneNumber.value = info.remoteNumber
        transition('incoming')
        audioManager.playRing(ringSrc)
        void applyAgentStatus('6')
        notifyIncomingWorkOrderPopup(info)
      } else {
        transition('dial')
        void applyAgentStatus('6')
      }
    })
    sipService.on('progress', (info) => {
      if (info.originator === 'remote') {
        transition('outbound_progress')
        if (direction.value === CallDirection.INBOUND && autoAnswer.value) {
          answer()
        }
      }
    })
    sipService.on('confirmed', () => {
      audioManager.stopRing()
      incomingCallFlag.value = false
      transition('answer')
      void applyAgentStatus('5')
      resetStatusTimer()
    })
    sipService.on('failed', (info) => {
      audioManager.stopRing()
      const result = describeSipFailure(
        {
          cause: info.cause,
          statusCode: info.statusCode,
          reasonPhrase: info.reasonPhrase,
          originator: info.originator
        },
        false
      )
      endCall(result)
    })
    sipService.on('ended', (info) => {
      audioManager.stopRing()
      const result = describeSipFailure(
        { cause: info.cause, originator: info.originator },
        true
      )
      endCall(result)
    })
    sipService.on('log', (entry) => {
      const fn = entry.level === 'error' ? console.error : entry.level === 'warn' ? console.warn : console.info
      fn(`[sipService] ${entry.message}`, entry.extra || '')
    })
  }

  /** 订阅 callSyncClient 通话级事件（服务端状态同步） */
  function bindCallSyncOnce() {
    if (callSyncBound) return
    callSyncBound = true
    callSyncClient.on('incomingCall', (data) => {
      // WS 来电通知：补齐 callId/主叫（SIP 头缺失时由后端提供）
      mergeIncomingCallData(data)
    })
    callSyncClient.on('agentStatus', (data) => {
      const incomingAgentId = Number(data?.agentId ?? data?.id)
      const incomingStatus = String(data?.onlineStatus ?? data?.status ?? '')
      if (incomingAgentId !== selectedAgentId.value || !AGENT_STATUS_MAP[incomingStatus]) return
      agentStatus.value = incomingStatus
      resetStatusTimer()
    })
    callSyncClient.on('callPark', (data) => {
      if (!belongsToCurrent(data)) return
      // 驻留视为保持
      if (callState.value === CallState.ACTIVE) transition('hold')
    })
    callSyncClient.on('callAnswer', (data) => {
      if (!belongsToCurrent(data)) return
      if (callState.value === CallState.INCOMING || callState.value === CallState.RINGING) {
        transition('answer')
      }
    })
    callSyncClient.on('callBridge', (data) => {
      if (!belongsToCurrent(data)) return
      audioManager.stopRing()
      incomingCallFlag.value = false
      transition('answer')
      void applyAgentStatus('5')
      resetStatusTimer()
    })
    callSyncClient.on('callHangup', (data) => {
      if (!belongsToCurrent(data)) return
      audioManager.stopRing()
      endCall({ message: '通话已结束', level: 'info', intentional: true })
    })
    callSyncClient.on('callFail', (data) => {
      if (!belongsToCurrent(data)) return
      audioManager.stopRing()
      const result = describeSipFailure(
        {
          statusCode: data?.statusCode || data?.status_code,
          reasonPhrase: data?.reason || data?.reasonPhrase
        },
        false
      )
      endCall(result)
    })
  }

  function belongsToCurrent(data: any): boolean {
    if (!data) return true
    const incomingCallId = data.callId || data.callID || data.call_id
    const incomingSessionId = data.sessionId || data.session_id
    if (!callId.value && !uniqueId.value) return true
    if (incomingCallId && callId.value && String(incomingCallId) === String(callId.value)) return true
    if (incomingSessionId && uniqueId.value && String(incomingSessionId) === String(uniqueId.value))
      return true
    if (!incomingCallId && !incomingSessionId) return true
    return false
  }

  function mergeIncomingCallData(data: any) {
    if (!data) return
    const incomingCaller = data.caller || data.callerNumber || data.from
    const incomingCallId = data.callId || data.callID
    const incomingUniqueId = data.uniqueId || data.uuid || data.sessionId
    if (incomingCallId && !callId.value) {
      callId.value = String(incomingCallId)
      callSync.setActiveCall({ callId: String(incomingCallId) })
      transcript.setCallId(String(incomingCallId))
    }
    if (incomingUniqueId && !uniqueId.value) {
      uniqueId.value = String(incomingUniqueId)
      callSync.setActiveCall({ uniqueId: String(incomingUniqueId) })
    }
    if (incomingCaller && !remoteNumber.value) {
      remoteNumber.value = String(incomingCaller)
      incomingCallNumber.value = String(incomingCaller)
      phoneNumber.value = String(incomingCaller)
    }
    notifyIncomingWorkOrderPopup({
      callId: callId.value,
      uniqueId: uniqueId.value,
      remoteNumber: remoteNumber.value,
      localNumber: localNumber.value
    })
  }

  /** 通知 HomeView 来电工单弹窗（保持历史集成契约） */
  function notifyIncomingWorkOrderPopup(info: any) {
    if (typeof window === 'undefined') return
    window.dispatchEvent(
      new CustomEvent('softphone-incoming-call', {
        detail: {
          callId: info.callId || '',
          uniqueId: info.uniqueId || '',
          caller: info.remoteNumber || incomingCallNumber.value || '',
          callee: info.localNumber || localNumber.value || '',
          direction: 1,
          status: 'ringing'
        }
      })
    )
  }

  /** 统一收尾通话：状态、媒体、计时、坐席态 */
  function endCall(result: { message: string; level: SoftphoneError['level']; intentional?: boolean }) {
    transition('end')
    incomingCallFlag.value = false
    incomingCallNumber.value = ''
    isHold.value = false
    isMute.value = false
    audioManager.stopRing()
    audioManager.releaseRemoteStream()
    const wasInbound = direction.value === CallDirection.INBOUND
    const wasOutbound = direction.value === CallDirection.OUTBOUND
    direction.value = null
    // 结束态短暂停留后回到稳定态
    callState.value = settleEndedState(registerStatus.value === RegisterStatus.REGISTERED)
    // 坐席态回到空闲/话后
    void applyAgentStatus(signInOut.value === '1' ? '7' : '4')
    stopStatusTimer()
    statusDuration.value = '00:00:00'
    // 清理转写会话
    transcript.reset()
    callSync.clearActiveCall()
    callId.value = ''
    uniqueId.value = ''
    void wasInbound
    void wasOutbound
    notifyError({ message: result.message, level: result.intentional ? 'info' : result.level, intentional: result.intentional })
    if (signInOut.value === '1') startStatusTimer()
  }

  /** 拉取坐席 SIP 配置 */
  async function loadSipConfig() {
    try {
      const res: any = await getSipConfig()
      const data = Array.isArray(res?.data) ? res.data : []
      sipAccountList.value = data
      if (!selectedAgentId.value && data.length > 0) {
        selectedAgentId.value = data[0].agentId
        applyAccount(data[0])
        void applyAgentStatus(
          sipService.isRegistered() && signInOut.value === '1'
            ? String(data[0].onlineStatus || 1)
            : '4'
        )
      }
    } catch (error) {
      const status = (error as any)?.response?.status ?? (error as any)?.status ?? 'network'
      const reason =
        (error as any)?.response?.data?.msg || (error as any)?.data?.msg || (error as any)?.message || 'unknown'
      console.error(`Load SIP config failed (status=${status}): ${reason}`)
      notifyError({ message: '获取 SIP 配置失败', level: 'error' })
    }
  }

  function applyAccount(account: SipAccount) {
    sipService.configure(toSipConfig(account))
  }

  function toSipConfig(account: SipAccount): SipAccountConfig {
    return {
      sipUser: account.sipUser,
      password: account.password,
      sipHost: account.sipHost,
      wsPort: account.wsPort,
      wssPort: account.wssPort,
      wsServers: account.wsServers,
      noAnswerTimeout: 60
    }
  }

  async function selectSipAccount(agentId: number) {
    const account = sipAccountList.value.find((item) => item.agentId === agentId)
    if (!account) return
    selectedAgentId.value = agentId
    applyAccount(account)
    void applyAgentStatus(
      sipService.isRegistered() && signInOut.value === '1'
        ? String(account.onlineStatus || 1)
        : '4'
    )
    if (sipService.isStarted() && loginState.value === '2') {
      // 切换账号：注销旧实例并提示重新登录
      await logout({ silent: true })
      loginState.value = '1'
      signInOut.value = '2'
      message.info('SIP 账号已切换，请重新登录')
    }
  }

  /** 登录：启动 UA（不自动注册） */
  async function login() {
    if (loginState.value === '2') return
    isLoginLoading.value = true
    try {
      bindSipOnce()
      bindCallSyncOnce()
      callSync.init()
      transcript.bindOnce()
      if (!sipAccountList.value.length) await loadSipConfig()
      if (!selectedAccount.value) {
        notifyError({ message: '没有可用的 SIP 账号', level: 'warning' })
        return
      }
      applyAccount(selectedAccount.value)
      registerStatus.value = RegisterStatus.UNREGISTERED
      callState.value = CallState.IDLE
      const ok = await sipService.start(toSipConfig(selectedAccount.value))
      if (!ok) {
        notifyError({ message: 'SIP 配置为空', level: 'warning' })
        return
      }
      loginState.value = '2'
      notifyError({ message: '登录成功', level: 'success' })
    } catch (error: any) {
      console.error('Initialize SIP client failed:', error)
      notifyError({ message: `SIP 登录失败：${error?.message || '未知错误'}`, level: 'error' })
    } finally {
      isLoginLoading.value = false
    }
  }

  /** 退出：注销并停止 UA */
  async function logout(opts: { silent?: boolean } = {}) {
    try {
      await sipService.stop({ unregister: true })
    } catch (error) {
      console.warn('Stop SIP failed:', error)
    }
    loginState.value = '1'
    signInOut.value = '2'
    registerStatus.value = RegisterStatus.UNREGISTERED
    networkStatus.value = NetworkStatus.DISCONNECTED
    callState.value = CallState.IDLE
    void applyAgentStatus('4')
    incomingCallFlag.value = false
    isHold.value = false
    isMute.value = false
    audioManager.teardown()
    callSync.clearActiveCall()
    stopStatusTimer()
    statusDuration.value = '00:00:00'
    if (!opts.silent) notifyError({ message: '已退出', level: 'success' })
  }

  /** 签入：注册 */
  async function signIn() {
    if (!sipService.isStarted()) {
      notifyError({ message: '请先登录 SIP', level: 'warning' })
      signInOut.value = '2'
      return
    }
    if (sipRegistrationChanging.value) return
    sipRegistrationChanging.value = true
    registerStatus.value = RegisterStatus.REGISTERING
    callState.value = CallState.REGISTERING
    try {
      await sipService.register()
      signInOut.value = '1'
      void applyAgentStatus('1')
      callState.value = CallState.REGISTERED
      resetStatusTimer()
      notifyError({ message: '签入成功', level: 'success' })
    } catch (error: any) {
      console.error('Register failed:', error)
      signInOut.value = '2'
      callState.value = CallState.IDLE
      notifyError({ message: `SIP 签入失败：${error?.message || '未知错误'}`, level: 'error' })
    } finally {
      sipRegistrationChanging.value = false
    }
  }

  /** 签出：注销 */
  async function signOut() {
    if (!sipService.isStarted()) {
      signInOut.value = '2'
      return
    }
    if (sipRegistrationChanging.value) return
    sipRegistrationChanging.value = true
    try {
      await sipService.unregister(true)
      signInOut.value = '2'
      void applyAgentStatus('4')
      callState.value = CallState.IDLE
      stopStatusTimer()
      statusDuration.value = '00:00:00'
      notifyError({ message: '签出成功', level: 'success' })
    } catch (error: any) {
      console.error('Unregister failed:', error)
      signInOut.value = sipService.isRegistered() ? '1' : '2'
      notifyError({ message: `SIP 签出失败：${error?.message || '未知错误'}`, level: 'error' })
    } finally {
      sipRegistrationChanging.value = false
    }
  }

  async function toggleSignInOut() {
    if (signInOut.value === '1') await signOut()
    else await signIn()
  }

  async function reportAgentStatus(code: string) {
    const agentId = selectedAgentId.value
    if (!agentId) return
    const sentByWebSocket = callSync.updateAgentStatus(agentId, code)
    try {
      await updateAgentOnlineStatus(agentId, Number(code))
    } catch (error) {
      if (!sentByWebSocket) throw error
      console.warn('REST agent status sync failed; WebSocket update was sent:', error)
    }
  }

  async function applyAgentStatus(code: string) {
    if (!AGENT_STATUS_MAP[code]) return
    agentStatus.value = code
    resetStatusTimer()
    try {
      await reportAgentStatus(code)
    } catch (error) {
      console.warn('Sync agent status failed:', error)
    }
  }

  function setAgentStatus(code: string) {
    void applyAgentStatus(code)
  }

  function setPhoneNumber(value: string) {
    phoneNumber.value = sanitizeDial(value)
  }

  function appendDial(digit: string) {
    phoneNumber.value += digit
  }

  async function makeCall() {
    if (!phoneNumber.value) {
      notifyError({ message: '请输入号码', level: 'warning' })
      return
    }
    if (!sipService.isStarted() || !sipService.isRegistered()) {
      notifyError({ message: '请先签入 SIP', level: 'warning' })
      return
    }
    if (!audioManager.canUseMic()) {
      notifyError({
        message: '浏览器软电话需要 HTTPS 和麦克风权限',
        level: 'error'
      })
      return
    }
    try {
      await sipService.call(phoneNumber.value)
    } catch (error: any) {
      console.error('Make call failed:', error)
      notifyError({ message: error?.message || '呼叫失败', level: 'error' })
    }
  }

  function answer() {
    try {
      sipService.answer()
      audioManager.stopRing()
      incomingCallFlag.value = false
      transition('answer')
      void applyAgentStatus('5')
      resetStatusTimer()
    } catch (error: any) {
      console.error('Answer failed:', error)
      notifyError({ message: `接听失败：${error.message || '未知错误'}`, level: 'error' })
    }
  }

  /** 挂断：活动会话只结束坐席 SIP 侧，保留主叫进入满意度评价 */
  async function hangup() {
    audioManager.stopRing()
    incomingCallFlag.value = false
    const hasSession = sipService.getCurrentSession() || sipService.getIncomingSession()
    if (!hasSession) {
      void serverHangupFallback()
      notifyError({ message: '已发送挂断请求', level: 'info' })
      endCall({ message: '通话已结束', level: 'info', intentional: true })
      return
    }
    try {
      sipService.hangup()
    } catch (error) {
      console.warn('JsSIP hangup failed, continue agent-only fallback:', error)
      void serverHangupFallback()
    }
    endCall({ message: '正在挂断...', level: 'info', intentional: true })
  }

  /** 后端兜底仅挂断当前坐席侧，禁止按 callId 或对端号码挂断 */
  async function serverHangupFallback() {
    const agentId = selectedAgentId.value
    if (!agentId) return
    try {
      await forceHangupAgent(agentId)
    } catch (error) {
      console.warn('Agent-only server hangup failed:', error)
    }
  }

  function toggleHold() {
    try {
      if (isHold.value) {
        sipService.unhold()
        isHold.value = false
        transition('resume')
        notifyError({ message: '已取消保持', level: 'success' })
      } else {
        sipService.hold()
        isHold.value = true
        transition('hold')
        notifyError({ message: '已保持', level: 'success' })
      }
    } catch (error: any) {
      notifyError({ message: error?.message || '保持操作失败', level: 'warning' })
    }
  }

  function toggleMute() {
    try {
      if (isMute.value) {
        sipService.unmute()
        isMute.value = false
        notifyError({ message: '已取消静音', level: 'success' })
      } else {
        sipService.mute()
        isMute.value = true
        notifyError({ message: '已静音', level: 'success' })
      }
    } catch (error: any) {
      notifyError({ message: error?.message || '静音操作失败', level: 'warning' })
    }
  }

  function openTransfer() {
    if (!hasActiveCall.value) {
      notifyError({ message: '没有活动通话', level: 'warning' })
      return
    }
    transferNumber.value = ''
    transferModalVisible.value = true
  }

  function confirmTransfer() {
    const num = sanitizeDial(transferNumber.value)
    if (!num) {
      notifyError({ message: '请输入转接号码', level: 'warning' })
      return
    }
    try {
      sipService.transfer(num)
      transferModalVisible.value = false
      notifyError({ message: '转接请求已发送', level: 'success' })
    } catch (error: any) {
      notifyError({ message: error?.message || '转接失败', level: 'error' })
    }
  }

  function cancelTransfer() {
    transferModalVisible.value = false
  }

  function toggleAutoAnswer() {
    autoAnswer.value = !autoAnswer.value
    notifyError({ message: `自动应答已${autoAnswer.value ? '开启' : '关闭'}`, level: 'success' })
  }

  function clearError() {
    lastError.value = null
  }

  /** 组件挂载时调用 */
  function init() {
    bindSipOnce()
    bindCallSyncOnce()
    callSync.bindOnce()
    transcript.bindOnce()
    startStatusTimer()
  }

  /** 组件卸载时调用 */
  function destroy() {
    stopStatusTimer()
  }

  return {
    // state
    sipAccountList,
    selectedAgentId,
    loginState,
    signInOut,
    sipRegistrationChanging,
    isLoginLoading,
    agentStatus,
    autoAnswer,
    registerStatus,
    networkStatus,
    callState,
    callId,
    uniqueId,
    remoteNumber,
    localNumber,
    direction,
    isHold,
    isMute,
    incomingCallFlag,
    incomingCallNumber,
    phoneNumber,
    transferModalVisible,
    transferNumber,
    lastError,
    statusDuration,
    // getters
    selectedAccount,
    isSignedIn,
    canDial,
    hasActiveCall,
    // actions
    init,
    destroy,
    loadSipConfig,
    selectSipAccount,
    login,
    logout,
    signIn,
    signOut,
    toggleSignInOut,
    setAgentStatus,
    setPhoneNumber,
    appendDial,
    makeCall,
    answer,
    hangup,
    toggleHold,
    toggleMute,
    openTransfer,
    confirmTransfer,
    cancelTransfer,
    toggleAutoAnswer,
    clearError
  }
})
