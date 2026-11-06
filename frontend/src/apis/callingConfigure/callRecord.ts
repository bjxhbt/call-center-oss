import Request from '@/utils/axios'

// 呼叫列表
export function getList(data: any) {
  return Request.post('/v1/call/page/list', data)
}

// 创建呼叫
export function createCall(data: any) {
  return Request.post('/v1/call/create', data)
}

// 获取录音播放地址
export function getPlayUrl(callId: string) {
  return Request.get(`/v1/call/record/play/${encodeURIComponent(String(callId))}`)
}

export function loadRecordingAudio(callId: string | number) {
  return Request.download(
    `/v1/call/record/stream/${encodeURIComponent(String(callId))}`,
    { timeout: 60000 }
  )
}

export function downloadRecording(callId: string | number) {
  return Request.download(
    `/v1/call/record/download/${encodeURIComponent(String(callId))}`,
    { timeout: 60000 }
  )
}

export function forceHangupCall(callId: string | number) {
  return Request.post(`/v1/call/hangup/${callId}`, {})
}

export function forceHangupAgent(agentId: string | number) {
  return Request.post(`/v1/call/hangup/agent/${agentId}`, {})
}

export function forceHangupAgentNumber(agentNumber: string | number) {
  return Request.post(`/v1/call/hangup/agent-number/${agentNumber}`, {})
}
