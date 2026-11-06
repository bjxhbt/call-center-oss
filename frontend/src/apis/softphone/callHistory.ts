/**
 * 来电弹屏历史通话接口（方案二 6.1）。
 *
 * 按 customerId 或 phone 获取最近通话列表，用于弹屏展示“历史通话入口”。
 * 后端话单接口（/v1/call/page/list）就绪后切换 SOFTPHONE_MOCK.callHistory。
 */
import Request from '@/utils/axios'
import { SOFTPHONE_MOCK, ApiResult, ok, mockDelay } from './index'

export interface CallHistoryItem {
  callId: string
  /** 1=呼入 2=呼出 */
  direction: 1 | 2
  /** 对方号码 */
  peerNumber: string
  /** 通话开始时间 */
  startedAt: string
  /** 通话时长（秒） */
  duration: number
  /** 挂断原因 */
  hangupCause?: string
  /** 是否有录音 */
  hasRecording: boolean
  /** 录音/话单详情入口 */
  recordUrl?: string
}

function buildMockHistory(phone: string): CallHistoryItem[] {
  const base = phone || '13800000000'
  return [
    {
      callId: 'mock-call-101',
      direction: 1,
      peerNumber: base,
      startedAt: '2026-07-12 10:24',
      duration: 128,
      hangupCause: '正常挂断',
      hasRecording: true,
      recordUrl: '#/callRecordManagement'
    },
    {
      callId: 'mock-call-088',
      direction: 2,
      peerNumber: base,
      startedAt: '2026-07-01 15:10',
      duration: 42,
      hangupCause: '正常挂断',
      hasRecording: true,
      recordUrl: '#/callRecordManagement'
    },
    {
      callId: 'mock-call-055',
      direction: 1,
      peerNumber: base,
      startedAt: '2026-06-20 09:00',
      duration: 0,
      hangupCause: '未接听',
      hasRecording: false
    }
  ]
}

/**
 * 真实分支归一化（已联调确认 /v1/call/page/list）：
 * 1) 返回分页对象 { total, list }（与 useFetchList 约定一致），调用方需要平铺数组；
 * 2) list 元素是完整话单实体（callerNumber/calleeNumber/callStartTime/callEndTime/
 *    recordDuration/hangupCauseCode/filePath/answerFlag/recordUrl...），并非 CallHistoryItem，
 *    需逐条映射；
 * 3) 过滤字段：后端按 callerNumber / calleeNumber 过滤（已验证），原 `phone` 被忽略。
 */
function diffSeconds(start?: string, end?: string): number {
  if (!start || !end) return 0
  const a = Date.parse(String(start).replace(/-/g, '/'))
  const b = Date.parse(String(end).replace(/-/g, '/'))
  if (Number.isNaN(a) || Number.isNaN(b)) return 0
  return Math.max(0, Math.round((b - a) / 1000))
}

function toCallHistoryItem(rec: any, phone?: string): CallHistoryItem {
  if (!rec) {
    return {
      callId: '',
      direction: 1,
      peerNumber: '',
      startedAt: '',
      duration: 0,
      hangupCause: '',
      hasRecording: false
    }
  }
  const callerNumber = rec.callerNumber ?? ''
  const calleeNumber = rec.calleeNumber ?? ''
  // 显示“对端”号码：若本条主叫即被查询号码，对端取被叫，反之取主叫
  const peerNumber =
    phone && String(callerNumber) === String(phone) ? calleeNumber : callerNumber
  const duration =
    Number(rec.recordDuration) > 0
      ? Number(rec.recordDuration)
      : diffSeconds(rec.callStartTime, rec.callEndTime)
  return {
    callId: String(rec.callId ?? ''),
    direction: (Number(rec.direction) === 2 ? 2 : 1) as 1 | 2,
    peerNumber: String(peerNumber ?? ''),
    startedAt: rec.callStartTime || rec.createTime || '',
    duration,
    hangupCause: Number(rec.answerFlag) === 0 ? '未接听' : '正常挂断',
    hasRecording: !!rec.filePath,
    recordUrl: rec.recordUrl || '#/callRecordManagement'
  }
}

function unwrapCallHistoryPage(res: any, phone?: string): ApiResult<CallHistoryItem[]> {
  const data = res?.data
  const rawList = Array.isArray(data)
    ? data
    : Array.isArray(data?.list)
      ? data.list
      : Array.isArray(data?.records)
        ? data.records
        : []
  return {
    code: res?.code ?? 200,
    msg: res?.msg ?? '',
    data: rawList.map((rec: any) => toCallHistoryItem(rec, phone))
  }
}

export function getCallHistoryByCustomer(
  customerId: number | string,
  phone?: string
): Promise<ApiResult<CallHistoryItem[]>> {
  if (SOFTPHONE_MOCK.callHistory) {
    return mockDelay(ok(buildMockHistory(phone || '')))
  }
  return Request.post('/v1/call/page/list', {
    customerId,
    callerNumber: phone,
    pageSize: 5,
    pageIndex: 1
  }).then((res: any) => unwrapCallHistoryPage(res, phone))
}

export function getCallHistoryByPhone(phone: string): Promise<ApiResult<CallHistoryItem[]>> {
  if (SOFTPHONE_MOCK.callHistory) {
    return mockDelay(ok(buildMockHistory(phone)))
  }
  return Request.post('/v1/call/page/list', {
    callerNumber: phone,
    pageSize: 5,
    pageIndex: 1
  }).then((res: any) => unwrapCallHistoryPage(res, phone))
}
