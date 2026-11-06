/**
 * 通用格式化纯函数。
 */

/** 把秒数格式化为 HH:mm:ss */
export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds || 0))
  const h = String(Math.floor(safe / 3600)).padStart(2, '0')
  const m = String(Math.floor((safe % 3600) / 60)).padStart(2, '0')
  const s = String(safe % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

/** 把毫秒差格式化为 mm:ss（用于通话计时） */
export function formatElapsed(fromTimestamp: number, nowTimestamp: number): string {
  const seconds = Math.floor((nowTimestamp - fromTimestamp) / 1000)
  return formatDuration(seconds)
}

/** 号号脱敏：保留前 3 后 4 */
export function maskPhone(phone: string): string {
  if (!phone) return ''
  const str = String(phone)
  if (str.length <= 7) return str
  return `${str.slice(0, 3)}****${str.slice(-4)}`
}

/** 校验是否为合法拨号号码（数字 + *#） */
export function isDialable(phone: string): boolean {
  return /^[0-9*#]+$/.test(String(phone || '').trim())
}

/** 清理号码：仅保留数字与 *# */
export function sanitizeDial(phone: string): string {
  return String(phone || '').replace(/[^\d*#]/g, '')
}
