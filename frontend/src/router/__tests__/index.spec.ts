import { describe, it, expect } from 'vitest'
import router from '@/router'

describe('router', () => {
  it('has dev route', () => {
    const route = router.resolve('/dev')
    expect(route.name).toBe('dev')
  })

  it('has watch route', () => {
    const route = router.resolve('/watch')
    expect(route.name).toBe('watch')
  })

  it('has studio route', () => {
    const route = router.resolve('/studio')
    expect(route.name).toBe('studio')
  })

  it('has channel route with the handle as a parameter', () => {
    const route = router.resolve('/channel/@ana')
    expect(route.name).toBe('channel')
    expect(route.params.handle).toBe('@ana')
  })

  it('uses createWebHistory', () => {
    expect(router.options.history).toBeDefined()
  })
})
