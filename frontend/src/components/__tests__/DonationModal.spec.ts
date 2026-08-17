import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DonationModal from '@/components/DonationModal.vue'

describe('DonationModal.vue', () => {
  it('forwards the modal shell close event', async () => {
    vi.useFakeTimers()
    const wrapper = mount(DonationModal, {
      props: { recipientUsername: 'ana' },
      global: {
        plugins: [createPinia()],
        stubs: { Teleport: true },
      },
    })

    await wrapper.get('.tv-modal-close').trigger('click')
    await vi.runAllTimersAsync()

    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
    vi.useRealTimers()
  })
})
