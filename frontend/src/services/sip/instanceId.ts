/**
 * SIP 注册 instance_id 管理（方案二 2.2）。
 *
 * 浏览器实例需要稳定的 instance_id，避免每次刷新生成不同标识，
 * 进而在 FreeSWITCH 侧产生多个 Contact。
 *
 * 持久化到 localStorage；存储接口可注入以便单元测试。
 */
import { STORAGE_KEYS } from '@/consts/softphone'

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export interface InstanceIdStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

/** 生成一个 UUID v4（优先使用 crypto.randomUUID） */
export function createUuid(): string {
  const cryptoRef = (globalThis as any).crypto
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return cryptoRef.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16)
    const value = char === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

/**
 * 获取稳定的 SIP instance_id。
 * - 若已持久化且格式合法，直接复用
 * - 否则生成新的并持久化
 * - 持久化失败时回退到内存值（每次会话稳定）
 */
export function getStableSipInstanceId(storage?: InstanceIdStorage | null): string {
  const store = storage ?? safeLocalStorage()
  if (store) {
    try {
      const stored = store.getItem(STORAGE_KEYS.SIP_INSTANCE_ID)
      if (stored && UUID_REGEX.test(stored)) return stored
      const instanceId = createUuid()
      store.setItem(STORAGE_KEYS.SIP_INSTANCE_ID, instanceId)
      return instanceId
    } catch (error) {
      console.warn('[instanceId] persist failed, fallback to session value', error)
    }
  }
  return createUuid()
}

/**
 * 注册清理标记：每个 (host, user) 首次注册成功后，强制一次 unregister(all)+register，
 * 清掉 FreeSWITCH 上可能残留的旧 Contact，随后置位避免每次都清理。
 */
export function getRegistrationCleanupKey(host: string, user: string): string {
  return `${STORAGE_KEYS.REGISTRATION_CLEANUP_PREFIX}:${host}:${user}`
}

export function isRegistrationCleanupDone(
  host: string,
  user: string,
  storage?: InstanceIdStorage | null
): boolean {
  const store = storage ?? safeLocalStorage()
  if (!store) return false
  try {
    return store.getItem(getRegistrationCleanupKey(host, user)) === '1'
  } catch {
    return false
  }
}

export function markRegistrationCleanupDone(
  host: string,
  user: string,
  storage?: InstanceIdStorage | null
): void {
  const store = storage ?? safeLocalStorage()
  if (!store) return
  try {
    store.setItem(getRegistrationCleanupKey(host, user), '1')
  } catch (error) {
    console.warn('[instanceId] mark cleanup failed', error)
  }
}

/** 重置 instance_id（仅供调试/切换设备，不在生产流程调用） */
export function resetStableSipInstanceId(storage?: InstanceIdStorage | null): void {
  const store = storage ?? safeLocalStorage()
  if (!store) return
  try {
    store.removeItem(STORAGE_KEYS.SIP_INSTANCE_ID)
  } catch {
    /* noop */
  }
}

function safeLocalStorage(): InstanceIdStorage | null {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return null
    return window.localStorage
  } catch {
    return null
  }
}
