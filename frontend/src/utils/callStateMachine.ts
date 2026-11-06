/**
 * 通话状态机（方案二 2.4 / 4.4）。
 *
 * 纯函数：根据当前 CallState 与一个事件，计算合法的下一个状态。
 * 非法迁移返回 null，由调用方决定是否忽略。这样避免重复事件、
 * 服务端挂断与本地挂断造成状态残留或回跳。
 */
import { CallState } from '@/consts/softphone'

export type CallEvent =
  | 'start_register' // 开始注册
  | 'register_ok' // 注册成功
  | 'register_fail' // 注册失败
  | 'register_lost' // 连接断开/失联
  | 'unregister' // 注销
  | 'dial' // 发起呼出
  | 'outbound_progress' // 呼出远端振铃
  | 'incoming' // 来电到达
  | 'answer' // 接听/桥接确认
  | 'hold' // 保持
  | 'resume' // 恢复
  | 'end' // 通话结束（本地或远端挂断、失败）

/** 合法迁移表：当前状态 → 该状态下允许的事件 → 目标状态 */
const TRANSITIONS: Record<CallState, Partial<Record<CallEvent, CallState>>> = {
  [CallState.IDLE]: {
    start_register: CallState.REGISTERING,
    dial: CallState.DIALING,
    incoming: CallState.INCOMING
  },
  [CallState.REGISTERING]: {
    register_ok: CallState.REGISTERED,
    register_fail: CallState.IDLE,
    register_lost: CallState.IDLE,
    unregister: CallState.IDLE
  },
  [CallState.REGISTERED]: {
    dial: CallState.DIALING,
    incoming: CallState.INCOMING,
    unregister: CallState.IDLE,
    register_lost: CallState.IDLE,
    start_register: CallState.REGISTERING
  },
  [CallState.DIALING]: {
    outbound_progress: CallState.RINGING,
    answer: CallState.ACTIVE,
    end: CallState.ENDED,
    incoming: CallState.INCOMING
  },
  [CallState.RINGING]: {
    answer: CallState.ACTIVE,
    end: CallState.ENDED
  },
  [CallState.INCOMING]: {
    answer: CallState.ACTIVE,
    end: CallState.ENDED,
    outbound_progress: CallState.RINGING
  },
  [CallState.ACTIVE]: {
    hold: CallState.HOLD,
    end: CallState.ENDED
  },
  [CallState.HOLD]: {
    resume: CallState.ACTIVE,
    end: CallState.ENDED
  },
  [CallState.ENDED]: {
    end: CallState.ENDED // 允许重复 end 事件，幂等
  }
}

/**
 * 计算下一个状态。
 * @returns 目标状态，若迁移非法返回 null
 */
export function reduceCallState(current: CallState, event: CallEvent): CallState | null {
  const next = TRANSITIONS[current]?.[event]
  return next ?? null
}

/**
 * ENDED 是过渡态，外部应随后回到 IDLE（已注销）或 REGISTERED（仍在线）。
 */
export function settleEndedState(isRegistered: boolean): CallState {
  return isRegistered ? CallState.REGISTERED : CallState.IDLE
}

/** 是否处于“有活动媒体会话”的状态（接听后） */
export function hasActiveMedia(state: CallState): boolean {
  return (
    state === CallState.ACTIVE ||
    state === CallState.HOLD ||
    state === CallState.RINGING ||
    state === CallState.INCOMING ||
    state === CallState.DIALING
  )
}

/** 是否允许发起呼出 */
export function canDial(state: CallState): boolean {
  return state === CallState.REGISTERED || state === CallState.IDLE
}

/** 是否允许接听 */
export function canAnswer(state: CallState): boolean {
  return state === CallState.INCOMING || state === CallState.RINGING
}

/** 是否允许保持/恢复切换 */
export function canToggleHold(state: CallState): boolean {
  return state === CallState.ACTIVE || state === CallState.HOLD
}
