import { describe, it, expect } from 'vitest'
import { describeSipFailure, describeRegistrationFailure } from '@/utils/sipErrorMap'

describe('describeSipFailure', () => {
  it('把用户主动挂断(487)识别为 intentional，不报红', () => {
    const r = describeSipFailure({ cause: 'Canceled', statusCode: 487, originator: 'local' })
    expect(r.intentional).toBe(true)
    expect(r.level).toBe('info')
  })

  it('号码错误 404 给出中文提示且非 intentional', () => {
    const r = describeSipFailure({ statusCode: 404 })
    expect(r.intentional).toBe(false)
    expect(r.message).toContain('号码')
    expect(r.level).toBe('error')
  })

  it('用户忙 486', () => {
    const r = describeSipFailure({ statusCode: 486 })
    expect(r.intentional).toBe(false)
    expect(r.message).toContain('忙')
  })

  it('网关不可用 503', () => {
    const r = describeSipFailure({ statusCode: 503 })
    expect(r.message).toContain('服务不可用')
  })

  it('媒体失败(WebRTC)标记为 warning', () => {
    const r = describeSipFailure({ cause: 'User Denied Media Access' })
    expect(r.intentional).toBe(false)
    expect(r.level).toBe('warning')
    expect(r.message).toContain('麦克风')
  })

  it('JsSIP cause 映射：请求超时', () => {
    const r = describeSipFailure({ cause: 'Request Timeout' })
    expect(r.message).toContain('超时')
    expect(r.intentional).toBe(false)
  })

  it('无人接听 No Answer', () => {
    const r = describeSipFailure({ cause: 'No Answer' })
    expect(r.message).toContain('无人接听')
  })

  it('ended 事件视为正常结束', () => {
    const r = describeSipFailure({ cause: 'Bye' }, true)
    expect(r.intentional).toBe(true)
    expect(r.message).toBe('通话已结束')
  })

  it('未知原因兜底', () => {
    const r = describeSipFailure({})
    expect(r.message).toBeTruthy()
    expect(r.intentional).toBe(false)
  })

  it('reason phrase 兜底', () => {
    const r = describeSipFailure({ reasonPhrase: 'Some Weird Error' })
    expect(r.message).toContain('Some Weird Error')
  })
})

describe('describeRegistrationFailure', () => {
  it('401 鉴权失败', () => {
    expect(describeRegistrationFailure(undefined, 401)).toContain('鉴权')
  })
  it('cause Auth', () => {
    expect(describeRegistrationFailure('Authentication Error')).toContain('鉴权')
  })
  it('未知 cause 回退', () => {
    expect(describeRegistrationFailure('Something')).toContain('Something')
  })
})
