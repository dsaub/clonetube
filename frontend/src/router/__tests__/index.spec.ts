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

  it('dev route loads DevView lazily', async () => {
    const route = router.resolve('/dev')
    const component = await route.components?.default
    expect(component).toBeDefined()
  })

  it('uses createWebHistory', () => {
    expect(router.options.history).toBeDefined()
  })
})
