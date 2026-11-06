import { describe, it, expect, vi, beforeEach } from 'vitest'

// 被测模块依赖 Request（@/utils/axios）与 SOFTPHONE_MOCK（./index）；
// 必须在 import 被测模块之前完成 mock。
vi.mock('@/utils/axios', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn()
  }
}))

// 强制走真实分支（callHistory=false），无论运行期 VITE_SOFTPHONE_LIVE_API 取值。
vi.mock('../index', async (importOriginal: any) => {
  const orig = await importOriginal()
  return {
    ...orig,
    SOFTPHONE_MOCK: { ...orig.SOFTPHONE_MOCK, callHistory: false }
  }
})

import Request from '@/utils/axios'
import { getCallHistoryByPhone, getCallHistoryByCustomer } from '../callHistory'

// 联调取样的真实话单实体结构（/v1/call/page/list 的 list 元素）
const REAL_RECORD = {
  id: '413',
  callId: '2072167018328145920',
  callerNumber: '13800000000',
  calleeNumber: '1001',
  direction: 1,
  callStartTime: '2026-07-31 10:00:00',
  callEndTime: '2026-07-31 10:00:30',
  recordDuration: 30,
  answerFlag: 1,
  hangupCauseCode: 16,
  filePath: 'C:/record/2026-07-31/x.wav',
  recordUrl: null
}

describe('softphone/callHistory 真实分支：分页归一 + 话单实体映射', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('把 {total,list} 归一为平铺数组，并把话单实体映射为 CallHistoryItem', async () => {
    ;(Request.post as any).mockResolvedValue({
      code: 200,
      msg: '操作成功',
      data: { total: 162, list: [REAL_RECORD] }
    })
    const res = await getCallHistoryByPhone('13800000000')
    expect(res.code).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect(res.data).toHaveLength(1)
    const item = res.data[0]
    expect(item.callId).toBe('2072167018328145920')
    expect(item.direction).toBe(1)
    // 被查询号码是主叫 → peerNumber 取对端（被叫）
    expect(item.peerNumber).toBe('1001')
    expect(item.startedAt).toBe('2026-07-31 10:00:00')
    expect(item.duration).toBe(30) // 取自 recordDuration
    expect(item.hangupCause).toBe('正常挂断') // answerFlag=1
    expect(item.hasRecording).toBe(true)
  })

  it('recordDuration 缺失/为 0 时，按 start/end 计算秒数；未接听映射为“未接听”', async () => {
    const rec = {
      ...REAL_RECORD,
      recordDuration: 0,
      callEndTime: '2026-07-31 10:00:10',
      answerFlag: 0,
      filePath: null
    }
    ;(Request.post as any).mockResolvedValue({ code: 200, msg: '', data: { total: 1, list: [rec] } })
    const res = await getCallHistoryByPhone('13800000000')
    expect(res.data[0].duration).toBe(10)
    expect(res.data[0].hangupCause).toBe('未接听')
    expect(res.data[0].hasRecording).toBe(false)
  })

  it('兼容 records 字段（MyBatis-Plus 风格分页）', async () => {
    ;(Request.post as any).mockResolvedValue({
      code: 200,
      msg: '',
      data: { records: [REAL_RECORD], total: 1 }
    })
    const res = await getCallHistoryByCustomer(123, '13800000000')
    expect(res.data[0].callId).toBe('2072167018328145920')
  })

  it('空列表兜底为空数组', async () => {
    ;(Request.post as any).mockResolvedValue({ code: 200, msg: '', data: { list: [], total: 0 } })
    const res = await getCallHistoryByPhone('13800000000')
    expect(res.data).toEqual([])
  })

  it('data 缺失时兜底为空数组', async () => {
    ;(Request.post as any).mockResolvedValue({ code: 200, msg: '' })
    const res = await getCallHistoryByPhone('13800000000')
    expect(res.data).toEqual([])
  })

  it('业务错误码透传，data 归一为空数组', async () => {
    ;(Request.post as any).mockResolvedValue({ code: 21001, msg: '网络请求超时', data: null })
    const res = await getCallHistoryByPhone('13800000000')
    expect(res.code).toBe(21001)
    expect(res.msg).toBe('网络请求超时')
    expect(res.data).toEqual([])
  })
})
