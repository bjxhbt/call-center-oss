import Request from '@/utils/axios'
import { ApiResult, ok, mockDelay, SOFTPHONE_MOCK } from './index'

export interface AsrServiceStatus {
  enabled: boolean
  configuredProvider: string
  readyProviders: Record<string, boolean>
  resolvedProvider: string
  activeSessions: number
  sampleRate: number
  inputCodec: string
  freeSwitch: {
    eslConnected: boolean
    eslAddress?: string
    /** True only when synchronous ESL API commands can be sent. */
    eslCommandReady?: boolean
    eslCommandAddress?: string
    /** Address/timestamp of recently received inbound ESL events. */
    eslEventAddress?: string
    eslEventLastSeenAt?: number
    eslEventAgeMillis?: number
    audioStreamEnabled: boolean
    audioStreamOperational: boolean
    audioStreamModule?: string
    audioForkAvailable?: boolean
    audioStreamModuleAvailable?: boolean
    audioStreamSampleRate?: number
    bothLegs: boolean
    streamEndpointConfigured: boolean
    streamTokenConfigured: boolean
  }
}

export interface AsrSimulationRequest {
  callId: number
  uniqueId?: string
  caller?: string
  callee?: string
  agentNumber?: string
  direction?: number
  wavPath?: string
}

export interface AsrSimulationResult {
  framesSent: number
  resolvedProvider: string
  summary?: any
}

export function getAsrServiceStatus(): Promise<ApiResult<AsrServiceStatus>> {
  if (SOFTPHONE_MOCK.transcription) {
    return mockDelay(
      ok({
        enabled: true,
        configuredProvider: 'MOCK',
        readyProviders: {
          MOCK: true,
          TENCENT: false,
          ALIYUN: false,
          FUNASR: false
        },
        resolvedProvider: 'MOCK',
        activeSessions: 0,
        sampleRate: 16000,
        inputCodec: 'PCMA',
        freeSwitch: {
          eslConnected: false,
          audioStreamEnabled: true,
          audioStreamOperational: false,
          audioStreamModule: 'audio_fork',
          audioForkAvailable: false,
          audioStreamSampleRate: 8000,
          bothLegs: false,
          streamEndpointConfigured: true,
          streamTokenConfigured: false
        }
      })
    )
  }
  return Request.post('/asr/v1/admin/status')
}

export function simulateAsrChain(
  payload: AsrSimulationRequest
): Promise<ApiResult<AsrSimulationResult>> {
  if (SOFTPHONE_MOCK.transcription) {
    return mockDelay(
      ok({
        framesSent: payload.wavPath ? 50 : 0,
        resolvedProvider: 'MOCK',
        summary: {
          callId: String(payload.callId),
          status: 'SUCCESS',
          fullText: payload.wavPath ? 'Mock 模拟转写完成。' : ''
        }
      }),
      600
    )
  }
  return Request.post('/asr/v1/admin/simulate', payload)
}
