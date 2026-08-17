import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import TvModalShell from '@/components/TvModalShell.vue'

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

function mountModal() {
  wrapper = mount(TvModalShell, {
    attachTo: document.body,
    props: { labelledby: 'modal-title' },
    slots: {
      default: '<h2 id="modal-title">Prueba</h2><button class="modal-action">Continuar</button>',
    },
  })
  return wrapper
}

describe('TvModalShell.vue', () => {
  it('moves focus into the modal and restores it when unmounted', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()

    const modal = mountModal()
    await modal.vm.$nextTick()
    expect(document.activeElement).toBe(document.querySelector('.tv-modal-panel'))

    modal.unmount()
    wrapper = undefined
    expect(document.activeElement).toBe(opener)
  })

  it('keeps keyboard focus inside the modal', async () => {
    const modal = mountModal()
    await modal.vm.$nextTick()
    const close = document.querySelector<HTMLButtonElement>('.tv-modal-close')!
    const action = document.querySelector<HTMLButtonElement>('.modal-action')!

    action.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.activeElement).toBe(close)

    close.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }))
    expect(document.activeElement).toBe(action)
  })
})
