import { describe, it, expect, beforeEach } from 'vitest'
import {
  createUuid,
  getStableSipInstanceId,
  getRegistrationCleanupKey,
  isRegistrationCleanupDone,
  markRegistrationCleanupDone
} from '@/services/sip/instanceId'

/** 内存版 storage，用于测试，避免污染真实 localStorage */
function memoryStorage(): { getItem(k: string): string | null; setItem(k: string, v: string): void; removeItem(k: string): void } {
  const map = new Map<string, string>()
  return {
    getItem: (k) => (map.has(k) ? map.get(k)! : null),
    setItem: (k, v) => void map.set(k, v),
    removeItem: (k) => void map.delete(k)
  }
}

describe('instanceId', () => {
  it('createUuid 合法 UUID v4', () => {
    const id = createUuid()
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)
  })

  it('getStableSipInstanceId 首次生成并持久化', () => {
    const storage = memoryStorage()
    const id1 = getStableSipInstanceId(storage)
    expect(id1).toMatch(/^[0-9a-f-]{36}$/i)
    // 持久化
    expect(storage.getItem('och:sip:instance-id')).toBe(id1)
  })

  it('getStableSipInstanceId 复用已持久化的合法值（稳定性）', () => {
    const storage = memoryStorage()
    const id1 = getStableSipInstanceId(storage)
    const id2 = getStableSipInstanceId(storage)
    const id3 = getStableSipInstanceId(storage)
    expect(id2).toBe(id1)
    expect(id3).toBe(id1)
  })

  it('getStableSipInstanceId 对非法持久化值重新生成', () => {
    const storage = memoryStorage()
    storage.setItem('och:sip:instance-id', 'not-a-uuid')
    const id = getStableSipInstanceId(storage)
    expect(id).toMatch(/^[0-9a-f-]{36}$/i)
    expect(id).not.toBe('not-a-uuid')
  })

  it('注册清理标记按 (host,user) 隔离', () => {
    const storage = memoryStorage()
    expect(isRegistrationCleanupDone('h1', 'u1', storage)).toBe(false)
    markRegistrationCleanupDone('h1', 'u1', storage)
    expect(isRegistrationCleanupDone('h1', 'u1', storage)).toBe(true)
    // 另一账号仍未清理
    expect(isRegistrationCleanupDone('h1', 'u2', storage)).toBe(false)
  })

  it('getRegistrationCleanupKey 包含 host 与 user', () => {
    const key = getRegistrationCleanupKey('hostA', 'userB')
    expect(key).toContain('hostA')
    expect(key).toContain('userB')
  })
})
