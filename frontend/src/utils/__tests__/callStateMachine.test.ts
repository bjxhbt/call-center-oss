import { describe, it, expect } from 'vitest'
import {
  reduceCallState,
  settleEndedState,
  canDial,
  canAnswer,
  canToggleHold,
  hasActiveMedia,
  CallEvent
} from '@/utils/callStateMachine'
import { CallState } from '@/consts/softphone'

describe('reduceCallState 合法迁移', () => {
  it('IDLE → start_register → REGISTERING', () => {
    expect(reduceCallState(CallState.IDLE, 'start_register')).toBe(CallState.REGISTERING)
  })
  it('REGISTERING → register_ok → REGISTERED', () => {
    expect(reduceCallState(CallState.REGISTERING, 'register_ok')).toBe(CallState.REGISTERED)
  })
  it('REGISTERED → dial → DIALING → outbound_progress → RINGING → answer → ACTIVE', () => {
    let s = reduceCallState(CallState.REGISTERED, 'dial')!
    expect(s).toBe(CallState.DIALING)
    s = reduceCallState(s, 'outbound_progress')!
    expect(s).toBe(CallState.RINGING)
    s = reduceCallState(s, 'answer')!
    expect(s).toBe(CallState.ACTIVE)
  })
  it('来电：REGISTERED → incoming → INCOMING → answer → ACTIVE', () => {
    let s = reduceCallState(CallState.REGISTERED, 'incoming')!
    expect(s).toBe(CallState.INCOMING)
    s = reduceCallState(s, 'answer')!
    expect(s).toBe(CallState.ACTIVE)
  })
  it('保持/恢复：ACTIVE ↔ HOLD', () => {
    let s = reduceCallState(CallState.ACTIVE, 'hold')!
    expect(s).toBe(CallState.HOLD)
    s = reduceCallState(s, 'resume')!
    expect(s).toBe(CallState.ACTIVE)
  })
  it('结束事件幂等：ENDED + end = ENDED', () => {
    expect(reduceCallState(CallState.ENDED, 'end')).toBe(CallState.ENDED)
  })
})

describe('reduceCallState 非法迁移返回 null（防状态残留/回跳）', () => {
  it('IDLE 不允许 answer', () => {
    expect(reduceCallState(CallState.IDLE, 'answer')).toBeNull()
  })
  it('ACTIVE 不允许 dial（避免重复呼叫）', () => {
    expect(reduceCallState(CallState.ACTIVE, 'dial')).toBeNull()
  })
  it('HOLD 不允许 dial', () => {
    expect(reduceCallState(CallState.HOLD, 'dial')).toBeNull()
  })
  it('REGISTERED 不允许 answer', () => {
    expect(reduceCallState(CallState.REGISTERED, 'answer')).toBeNull()
  })
})

describe('辅助判断', () => {
  it('settleEndedState', () => {
    expect(settleEndedState(true)).toBe(CallState.REGISTERED)
    expect(settleEndedState(false)).toBe(CallState.IDLE)
  })
  it('canDial 仅在 IDLE/REGISTERED', () => {
    expect(canDial(CallState.REGISTERED)).toBe(true)
    expect(canDial(CallState.ACTIVE)).toBe(false)
  })
  it('canAnswer 仅在 INCOMING/RINGING', () => {
    expect(canAnswer(CallState.INCOMING)).toBe(true)
    expect(canAnswer(CallState.RINGING)).toBe(true)
    expect(canAnswer(CallState.ACTIVE)).toBe(false)
  })
  it('canToggleHold 仅在 ACTIVE/HOLD', () => {
    expect(canToggleHold(CallState.ACTIVE)).toBe(true)
    expect(canToggleHold(CallState.HOLD)).toBe(true)
    expect(canToggleHold(CallState.IDLE)).toBe(false)
  })
  it('hasActiveMedia', () => {
    expect(hasActiveMedia(CallState.ACTIVE)).toBe(true)
    expect(hasActiveMedia(CallState.IDLE)).toBe(false)
  })
})

describe('CallEvent 类型完备性（编译期）', () => {
  it('包含关键事件', () => {
    const events: CallEvent[] = [
      'start_register',
      'register_ok',
      'register_fail',
      'dial',
      'outbound_progress',
      'incoming',
      'answer',
      'hold',
      'resume',
      'end'
    ]
    expect(events.length).toBeGreaterThan(5)
  })
})
