/**
 * 通话状态机补充用例（§11.3 #2）：拒接、远端挂断、重复/乱序事件。
 * 验证非法迁移返回 null，避免状态残留或回跳。
 */
import { describe, it, expect } from 'vitest'
import { reduceCallState } from '@/utils/callStateMachine'
import { CallState } from '@/consts/softphone'

describe('拒接/远端挂断/重复事件', () => {
  it('拒接来电：INCOMING + end → ENDED（拒接等价于结束）', () => {
    expect(reduceCallState(CallState.INCOMING, 'end')).toBe(CallState.ENDED)
  })

  it('远端挂断：ACTIVE + end → ENDED', () => {
    expect(reduceCallState(CallState.ACTIVE, 'end')).toBe(CallState.ENDED)
  })

  it('呼出未接听挂断：RINGING + end → ENDED', () => {
    expect(reduceCallState(CallState.RINGING, 'end')).toBe(CallState.ENDED)
  })

  it('拨号中取消：DIALING + end → ENDED', () => {
    expect(reduceCallState(CallState.DIALING, 'end')).toBe(CallState.ENDED)
  })

  it('重复挂断事件幂等（ENDED + end → ENDED，不报错）', () => {
    expect(reduceCallState(CallState.ENDED, 'end')).toBe(CallState.ENDED)
    expect(reduceCallState(CallState.ENDED, 'end')).toBe(CallState.ENDED)
  })

  it('通话中不能再次 dial（防重复呼叫）', () => {
    expect(reduceCallState(CallState.ACTIVE, 'dial')).toBeNull()
    expect(reduceCallState(CallState.INCOMING, 'dial')).toBeNull()
  })

  it('保持中不能接听（防止错误状态跳转）', () => {
    expect(reduceCallState(CallState.HOLD, 'answer')).toBeNull()
  })

  it('空闲态不能直接保持/挂断/恢复', () => {
    expect(reduceCallState(CallState.IDLE, 'hold')).toBeNull()
    expect(reduceCallState(CallState.IDLE, 'resume')).toBeNull()
    // IDLE 的 end 不在迁移表 → null（空闲没有可结束的通话）
    expect(reduceCallState(CallState.IDLE, 'end')).toBeNull()
  })

  it('乱序事件：来电未到达时不能 confirmed-answer', () => {
    // 仅 INCOMING/RINGING 允许 answer；DIALING(呼出) answer 也合法（早接听）
    expect(reduceCallState(CallState.REGISTERED, 'answer')).toBeNull()
  })

  it('完整呼入链路：incoming→answer→hold→resume→end', () => {
    let s = CallState.REGISTERED
    s = reduceCallState(s, 'incoming')!
    expect(s).toBe(CallState.INCOMING)
    s = reduceCallState(s, 'answer')!
    expect(s).toBe(CallState.ACTIVE)
    s = reduceCallState(s, 'hold')!
    expect(s).toBe(CallState.HOLD)
    s = reduceCallState(s, 'resume')!
    expect(s).toBe(CallState.ACTIVE)
    s = reduceCallState(s, 'end')!
    expect(s).toBe(CallState.ENDED)
  })
})
