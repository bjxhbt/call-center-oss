/**
 * 极简类型安全事件发射器（避免引入 mitt 等额外依赖）。
 * 支持多监听、once、移除。供 SipService / AudioManager 使用。
 */
export type Listener<T = any> = (payload: T) => void

export class Emitter<Events extends Record<string, any>> {
  private listeners: { [K in keyof Events]?: Set<Listener<Events[K]>> } = {}

  on<K extends keyof Events>(event: K, listener: Listener<Events[K]>): () => void {
    (this.listeners[event] ?? (this.listeners[event] = new Set())).add(listener)
    return () => this.off(event, listener)
  }

  once<K extends keyof Events>(event: K, listener: Listener<Events[K]>): () => void {
    const wrapper: Listener<Events[K]> = (payload) => {
      this.off(event, wrapper as Listener<Events[K]>)
      listener(payload)
    }
    return this.on(event, wrapper as Listener<Events[K]>)
  }

  off<K extends keyof Events>(event: K, listener: Listener<Events[K]>): void {
    this.listeners[event]?.delete(listener)
  }

  emit<K extends keyof Events>(event: K, payload: Events[K]): void {
    const set = this.listeners[event]
    if (!set || set.size === 0) return
    // 拷贝一份，避免监听器在执行中增删导致迭代异常
    set.forEach((listener) => {
      try {
        listener(payload)
      } catch (error) {
        // 单个监听器抛错不影响其他监听器
        console.error(`[Emitter] listener for "${String(event)}" threw:`, error)
      }
    })
  }

  clear(): void {
    this.listeners = {}
  }

  listenerCount<K extends keyof Events>(event: K): number {
    return this.listeners[event]?.size ?? 0
  }
}
