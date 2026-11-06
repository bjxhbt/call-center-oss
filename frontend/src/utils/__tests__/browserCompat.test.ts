import { describe, expect, it } from 'vitest'
import { allSettledCompat } from '@/utils/browserCompat'

describe('browser compatibility helpers', () => {
  it('兼容 Promise.allSettled 的成功与失败结果', async () => {
    const results = await allSettledCompat([
      Promise.resolve('ok'),
      Promise.reject(new Error('failed'))
    ])

    expect(results[0]).toEqual({ status: 'fulfilled', value: 'ok' })
    expect(results[1].status).toBe('rejected')
    expect((results[1] as { reason: Error }).reason.message).toBe('failed')
  })
})

