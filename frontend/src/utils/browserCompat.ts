/**
 * 统信 UOS 浏览器兼容层。
 *
 * UOS 不同版本预装的 Chromium 内核并不完全一致，部分现场仍会使用
 * 缺少 Promise.allSettled / ResizeObserver 的旧版本。兼容层只补充缺失能力，
 * 不覆盖浏览器原生实现，保证现代浏览器的行为保持不变。
 */

export type PromiseSettledResultCompat<T> =
  | { status: 'fulfilled'; value: T }
  | { status: 'rejected'; reason: unknown }

export function allSettledCompat(
  values: Iterable<PromiseLike<any> | any>
): Promise<PromiseSettledResultCompat<any>[]> {
  const items = Array.from(values)
  return Promise.all(
    items.map((value) =>
      Promise.resolve(value).then(
        (result) => ({ status: 'fulfilled' as const, value: result }),
        (reason) => ({ status: 'rejected' as const, reason })
      )
    )
  )
}

/**
 * 为旧版 Chromium 安装 Promise.allSettled，供第三方组件和业务代码使用。
 * 采用 globalThis 的宽松访问，避免在不完整 WebView 环境中触发异常。
 */
export function installBrowserCompatibility(): void {
  if (typeof Promise !== 'undefined' && typeof (Promise as any).allSettled !== 'function') {
    ;(Promise as any).allSettled = allSettledCompat
  }

  if (typeof window === 'undefined') return

  // JsSIP/LogicFlow 读取标准名称；老 Chromium 仅暴露 webkit 前缀时做别名。
  const windowRef = window as any
  if (!windowRef.RTCPeerConnection && windowRef.webkitRTCPeerConnection) {
    try {
      windowRef.RTCPeerConnection = windowRef.webkitRTCPeerConnection
    } catch {
      // 只读 WebView 全局对象无法写入时，audioManager 仍会识别 webkit 前缀。
    }
  }
  if (!windowRef.AudioContext && windowRef.webkitAudioContext) {
    try {
      windowRef.AudioContext = windowRef.webkitAudioContext
    } catch {
      // 只读 WebView 全局对象无法写入时，虚拟麦克风测试会直接使用前缀实现。
    }
  }

  // 旧版 UOS 可能只有 navigator.webkitGetUserMedia。通过 mediaDevices 统一入口，
  // 业务层无需为不同内核分支，且不覆盖原生 getUserMedia。
  const navigatorRef = windowRef.navigator as any
  const legacyGetUserMedia =
    navigatorRef.getUserMedia || navigatorRef.webkitGetUserMedia || navigatorRef.mozGetUserMedia
  if (legacyGetUserMedia && (!navigatorRef.mediaDevices || !navigatorRef.mediaDevices.getUserMedia)) {
    const mediaDevices = navigatorRef.mediaDevices || {}
    mediaDevices.getUserMedia = (constraints: MediaStreamConstraints) =>
      new Promise<MediaStream>((resolve, reject) => {
        legacyGetUserMedia.call(navigatorRef, constraints, resolve, reject)
      })
    try {
      navigatorRef.mediaDevices = mediaDevices
    } catch {
      // 某些 WebView 将 mediaDevices 设为只读，requestUserMedia 仍可直接回退。
    }
  }
}

export interface BrowserCapabilities {
  secureContext: boolean
  webRtc: boolean
  mediaDevices: boolean
  webSocket: boolean
}

/**
 * 返回软电话启动前需要的浏览器能力，便于 UOS 现场诊断。
 * 这里只做特性检测，不依赖 UA 字符串（UOS 浏览器可能修改 UA）。
 */
export function getBrowserCapabilities(): BrowserCapabilities {
  if (typeof window === 'undefined') {
    return { secureContext: false, webRtc: false, mediaDevices: false, webSocket: false }
  }

  const navigatorRef = window.navigator as Navigator & {
    getUserMedia?: unknown
    webkitGetUserMedia?: unknown
    mozGetUserMedia?: unknown
  }
  const secureContext =
    typeof window.isSecureContext === 'boolean'
      ? window.isSecureContext
      : window.location.protocol === 'https:' ||
        window.location.hostname === 'localhost' ||
        window.location.hostname === '127.0.0.1'

  return {
    secureContext,
    webRtc:
      typeof (window as any).RTCPeerConnection === 'function' ||
      typeof (window as any).webkitRTCPeerConnection === 'function',
    mediaDevices:
      !!navigatorRef.mediaDevices?.getUserMedia ||
      typeof navigatorRef.getUserMedia === 'function' ||
      typeof navigatorRef.webkitGetUserMedia === 'function' ||
      typeof navigatorRef.mozGetUserMedia === 'function',
    webSocket: typeof window.WebSocket === 'function'
  }
}

/**
 * 在 ResizeObserver 不存在时退回 window.resize，并返回清理函数。
 * 主要用于 LogicFlow 画布，避免旧版 UOS 调整窗口后布局失效。
 */
export function observeResize(element: Element, callback: () => void): () => void {
  const ResizeObserverRef = (window as any).ResizeObserver as
    | (new (handler: () => void) => { observe: (target: Element) => void; disconnect: () => void })
    | undefined

  if (ResizeObserverRef) {
    const observer = new ResizeObserverRef(callback)
    observer.observe(element)
    return () => observer.disconnect()
  }

  window.addEventListener('resize', callback)
  return () => window.removeEventListener('resize', callback)
}

/** 兼容旧版 Chromium 的 getUserMedia 前缀实现。 */
export function requestUserMedia(constraints: MediaStreamConstraints): Promise<MediaStream> {
  const mediaDevices = typeof navigator !== 'undefined' ? navigator.mediaDevices : undefined
  if (mediaDevices && typeof mediaDevices.getUserMedia === 'function') {
    return mediaDevices.getUserMedia(constraints)
  }

  if (typeof navigator === 'undefined') {
    return Promise.reject(new Error('getUserMedia is not supported'))
  }

  type LegacyGetUserMedia = (
    constraints: MediaStreamConstraints,
    success: (stream: MediaStream) => void,
    failure: (error: unknown) => void
  ) => void
  const navigatorRef = navigator as Navigator & {
    getUserMedia?: LegacyGetUserMedia
    webkitGetUserMedia?: LegacyGetUserMedia
    mozGetUserMedia?: LegacyGetUserMedia
  }
  const legacyGetUserMedia =
    navigatorRef.getUserMedia || navigatorRef.webkitGetUserMedia || navigatorRef.mozGetUserMedia
  if (typeof legacyGetUserMedia !== 'function') {
    return Promise.reject(new Error('getUserMedia is not supported'))
  }

  return new Promise<MediaStream>((resolve, reject) => {
    legacyGetUserMedia.call(navigatorRef, constraints, resolve, reject)
  })
}
