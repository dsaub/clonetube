import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import IndexView from '@/views/IndexView.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

const videos = [
  {
    key: 'videos/uno',
    size: 1024,
    last_modified: '2026-07-20T12:00:00Z',
    original_filename: 'Documental del océano.mp4',
  },
  {
    key: 'videos/dos',
    size: 2048,
    last_modified: '2026-07-21T12:00:00Z',
    original_filename: 'Concierto en directo.mp4',
  },
]

function mountView() {
  return mount(IndexView, {
    global: {
      plugins: [createPinia()],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

beforeEach(() => {
  localStorage.clear()
  mockFetch.mockReset()
  mockFetch.mockResolvedValue({
    ok: true,
    json: () => Promise.resolve({ videos }),
  })
})

describe('IndexView search', () => {
  it('filters videos by the available filename, ignoring accents and case', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[type="search"]').setValue('OCEANO')

    const cards = wrapper.findAll('.video-card')
    expect(cards).toHaveLength(1)
    expect(cards[0]?.text()).toContain('Documental del océano.mp4')
    expect(wrapper.text()).toContain('1 resultado para «OCEANO»')
  })

  it('shows an empty search state and can clear the query', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[type="search"]').setValue('inexistente')

    expect(wrapper.text()).toContain('No hay coincidencias')
    await wrapper.find('.search-empty button').trigger('click')
    expect(wrapper.findAll('.video-card')).toHaveLength(2)
  })
})
