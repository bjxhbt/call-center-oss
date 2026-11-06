/**
 * SipService 生命周期用例（§11.3 #1）：单例、注册、注册失败、注销/资源释放。
 * 用 vi.hoisted + vi.mock 把 jssip 换成可控 MockUA（避免 hoisting 引用错误）。
 * 注入确定性 window.localStorage，验证 instance_id 与 Contact 清理标记持久化。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// ---- 确定性 localStorage（node 环境无 window.localStorage，显式注入）----
const memStore = new Map<string, string>()
const memoryLocalStorage = {
  getItem: (k: string) => (memStore.has(k) ? memStore.get(k)! : null),
  setItem: (k: string, v: string) => void memStore.set(k, v),
  removeItem: (k: string) => void memStore.delete(k),
  clear: () => memStore.clear()
}
;(globalThis as any).window = {
  localStorage: memoryLocalStorage,
  location: { protocol: 'http:', host: 'localhost:3008' }
}
;(globalThis as any).localStorage = memoryLocalStorage

const mocks = vi.hoisted(() => {
  const state = { shouldFailRegister: false }

  class MockUA {
    static last: MockUA | null = null
    static instances: MockUA[] = []
    options: any
    handlers: Record<string, Array<(e?: any) => void>> = {}
    started = false
    stopped = false
    registered = false
    registerCount = 0
    unregisterCount = 0
    constructor(options: any) {
      this.options = options
      ;(MockUA as any).last = this
      ;(MockUA as any).instances.push(this)
    }
    on(ev: string, fn: any) {
      ;(this.handlers[ev] ||= []).push(fn)
      return this
    }
    once(ev: string, fn: any) {
      const wrap = (e?: any) => {
        this.removeListener(ev, wrap)
        fn(e)
      }
      this.on(ev, wrap)
    }
    removeListener(ev: string, fn: any) {
      this.handlers[ev] = (this.handlers[ev] || []).filter((h: any) => h !== fn)
    }
    start() {
      this.started = true
    }
    stop() {
      this.stopped = true
      this.started = false
    }
    register() {
      this.registerCount++
      if ((state as any).shouldFailRegister) {
        this.registered = false
        this._emit('registrationFailed', {
          cause: 'Registration Error',
          response: { status_code: 403 }
        })
        return
      }
      this.registered = true
      this._emit('registered')
    }
    unregister() {
      this.unregisterCount++
      this.registered = false
      this._emit('unregistered')
    }
    isRegistered() {
      return this.registered
    }
    _emit(ev: string, payload?: any) {
      ;(this.handlers[ev] || []).forEach((fn) => fn(payload))
    }
  }

  const WS = class {
    url: string
    constructor(url: string) {
      this.url = url
    }
  }

  return { MockUA, WS, state }
})

vi.mock('jssip', () => ({
  default: { UA: mocks.MockUA, WebSocketInterface: mocks.WS },
  UA: mocks.MockUA,
  WebSocketInterface: mocks.WS
}))

import { sipService } from '@/services/sip/SipService'
import { isRegistrationCleanupDone } from '@/services/sip/instanceId'

const { MockUA, state } = mocks

const cfg = (host: string, user = '1001') => ({
  sipUser: user,
  password: 'test-public-credential',
  sipHost: host,
  wsPort: 5066,
  wssPort: 7443,
  noAnswerTimeout: 60
})

describe('SipService 单例与配置', () => {
  it('模块导出的是单例', async () => {
    const mod = await import('@/services/sip/SipService')
    expect(mod.sipService).toBe(sipService)
  })
  it('configure 写入 config', () => {
    sipService.configure(cfg('h.example'))
    expect(sipService.getConfig()?.sipUser).toBe('1001')
    expect(sipService.getConfig()?.sipHost).toBe('h.example')
  })
})

describe('SipService 注册生命周期', () => {
  beforeEach(() => {
    state.shouldFailRegister = false
    ;(MockUA as any).instances = []
    ;(MockUA as any).last = null
    memStore.clear()
  })
  afterEach(async () => {
    await sipService.stop({ unregister: true }).catch(() => {})
  })

  it('start 创建 UA 并启动；register 成功后 isRegistered=true', async () => {
    const ok = await sipService.start(cfg('sip.a.com'))
    expect(ok).toBe(true)
    expect((MockUA as any).last?.started).toBe(true)
    expect(sipService.isStarted()).toBe(true)

    await sipService.register()
    expect(sipService.isRegistered()).toBe(true)
    expect((MockUA as any).last?.registerCount).toBeGreaterThanOrEqual(1)
  })

  it('注册失败（mock 抛 registrationFailed）时 register() reject', async () => {
    await sipService.start(cfg('sip.fail.com'))
    state.shouldFailRegister = true
    await expect(sipService.register()).rejects.toThrow()
    expect(sipService.isRegistered()).toBe(false)
  })

  it('重复 register 不抛错且保持已注册', async () => {
    await sipService.start(cfg('sip.b.com'))
    await sipService.register()
    await sipService.register()
    expect(sipService.isRegistered()).toBe(true)
  })

  it('unregister 调用 UA.unregister', async () => {
    await sipService.start(cfg('sip.c.com'))
    await sipService.register()
    const before = (MockUA as any).last?.unregisterCount || 0
    await sipService.unregister()
    expect((MockUA as any).last?.unregisterCount).toBeGreaterThan(before)
  })

  it('stop 释放：UA.stop 被调用，isStarted=false，会话清空', async () => {
    await sipService.start(cfg('sip.d.com'))
    await sipService.register()
    const ua = (MockUA as any).last
    await sipService.stop({ unregister: true })
    expect(ua?.stopped).toBe(true)
    expect(sipService.isStarted()).toBe(false)
    expect(sipService.getCurrentSession()).toBeNull()
  })

  it('首次注册执行 Contact 清理（unregister all + register）并置位', async () => {
    const host = 'sip.cleanup.com'
    const user = 'clean'
    try {
      window.localStorage.removeItem('och:sip:registration-cleanup:v1:sip.cleanup.com:clean')
    } catch {
      /* noop */
    }
    await sipService.start(cfg(host, user))
    await sipService.register()
    expect((MockUA as any).last?.unregisterCount).toBeGreaterThanOrEqual(1)
    expect(isRegistrationCleanupDone(host, user)).toBe(true)
  })
})
