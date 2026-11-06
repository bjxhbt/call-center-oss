import { describe, it, expect } from 'vitest'
import {
  formatDuration,
  formatElapsed,
  maskPhone,
  isDialable,
  sanitizeDial
} from '@/utils/format'

describe('format', () => {
  it('formatDuration 补零', () => {
    expect(formatDuration(0)).toBe('00:00:00')
    expect(formatDuration(65)).toBe('00:01:05')
    expect(formatDuration(3661)).toBe('01:01:01')
  })
  it('formatDuration 负数归零', () => {
    expect(formatDuration(-10)).toBe('00:00:00')
  })
  it('formatElapsed 毫秒差', () => {
    expect(formatElapsed(0, 65_000)).toBe('00:01:05')
  })
  it('maskPhone 保留前3后4', () => {
    expect(maskPhone('13812345678')).toBe('138****5678')
    expect(maskPhone('1234567')).toBe('1234567')
    expect(maskPhone('')).toBe('')
  })
  it('isDialable 仅数字与 *#', () => {
    expect(isDialable('10086')).toBe(true)
    expect(isDialable('*123#')).toBe(true)
    expect(isDialable('100-86')).toBe(false)
    expect(isDialable('abc')).toBe(false)
  })
  it('sanitizeDial 过滤非法字符', () => {
    expect(sanitizeDial('138-1234 5678')).toBe('13812345678')
    expect(sanitizeDial('*#abc123')).toBe('*#123')
  })
})
