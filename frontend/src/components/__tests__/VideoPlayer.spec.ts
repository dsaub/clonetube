import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import VideoPlayer from '@/components/VideoPlayer.vue'

interface MockHlsConfig {
  startPosition?: number
  xhrSetup?: (xhr: XMLHttpRequest) => void
}

interface MockLevel {
  height: number
}

interface MockHlsInstance {
  config: MockHlsConfig
  currentLevel: number
  levels: MockLevel[]
  source: string
  media: HTMLMediaElement | null
  destroy: ReturnType<typeof vi.fn>
  emit: (event: string, data: unknown) => void
}

const hlsMock = vi.hoisted(() => ({
  instances: [] as MockHlsInstance[],
  supported: true,
}))

vi.mock('hls.js', () => {
  class MockHls implements MockHlsInstance {
    static Events = {
      MANIFEST_PARSED: 'manifestParsed',
      LEVEL_SWITCHED: 'levelSwitched',
      ERROR: 'error',
    }

    static isSupported() {
      return hlsMock.supported
    }

    config: MockHlsConfig
    currentLevel = -1
    levels: MockLevel[] = []
    source = ''
    media: HTMLMediaElement | null = null
    destroy = vi.fn()
    private handlers = new Map<string, (event: string, data: unknown) => void>()

    constructor(config: MockHlsConfig) {
      this.config = config
      hlsMock.instances.push(this)
    }

    on(event: string, handler: (event: string, data: unknown) => void) {
      this.handlers.set(event, handler)
    }

    emit(event: string, data: unknown) {
      this.handlers.get(event)?.(event, data)
    }

    loadSource(source: string) {
      this.source = source
    }

    attachMedia(media: HTMLMediaElement) {
      this.media = media
    }
  }

  return { default: MockHls }
})

const originalSrc = 'https://example.com/video.mp4'
const hlsSrc = 'https://example.com/master.m3u8'

async function mountHlsPlayer() {
  const wrapper = mount(VideoPlayer, { props: { src: originalSrc, hlsSrc, token: 'token-123' } })
  await flushPromises()
  const instance = hlsMock.instances.at(-1)!
  instance.levels = [{ height: 720 }, { height: 480 }]
  instance.emit('manifestParsed', { levels: instance.levels })
  await wrapper.vm.$nextTick()
  return { wrapper, instance }
}

beforeEach(() => {
  localStorage.clear()
  hlsMock.instances.length = 0
  hlsMock.supported = true
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('VideoPlayer.vue', () => {
  it('renders progressive video without a quality selector when HLS is absent', async () => {
    const wrapper = mount(VideoPlayer, { props: { src: originalSrc } })
    await flushPromises()

    expect(wrapper.find('video').attributes('src')).toBe(originalSrc)
    expect(wrapper.find('.quality-control').exists()).toBe(false)
  })

  it('renders Auto, descending HLS levels and Original', async () => {
    const { wrapper } = await mountHlsPlayer()
    await wrapper.find('.quality-btn').trigger('click')

    expect(wrapper.findAll('.quality-option').map((option) => option.text())).toEqual([
      'Auto',
      '720p',
      '480p',
      'Original',
    ])
  })

  it('selects a manual level, returns to Auto and persists both choices', async () => {
    const { wrapper, instance } = await mountHlsPlayer()
    await wrapper.find('.quality-btn').trigger('click')
    await wrapper.findAll('.quality-option')[1]!.trigger('click')

    expect(instance.currentLevel).toBe(0)
    expect(localStorage.getItem('clonetube-quality')).toBe('720p')

    await wrapper.find('.quality-btn').trigger('click')
    await wrapper.findAll('.quality-option')[0]!.trigger('click')
    expect(instance.currentLevel).toBe(-1)
    expect(localStorage.getItem('clonetube-quality')).toBe('auto')
  })

  it('falls back to Auto when the saved quality is unavailable', async () => {
    localStorage.setItem('clonetube-quality', '1080p')
    const { instance } = await mountHlsPlayer()

    expect(instance.currentLevel).toBe(-1)
    expect(localStorage.getItem('clonetube-quality')).toBe('auto')
  })

  it('updates the Auto label after an adaptive level switch', async () => {
    const { wrapper, instance } = await mountHlsPlayer()
    instance.emit('levelSwitched', { level: 1 })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.quality-btn').text()).toContain('Auto (480p)')
  })

  it('adds the Bearer token to HLS requests', async () => {
    const { instance } = await mountHlsPlayer()
    const xhr = { setRequestHeader: vi.fn() } as unknown as XMLHttpRequest

    instance.config.xhrSetup?.(xhr)
    expect(xhr.setRequestHeader).toHaveBeenCalledWith('Authorization', 'Bearer token-123')
  })

  it('switches to Original while preserving time and playback state', async () => {
    const { wrapper, instance } = await mountHlsPlayer()
    const video = wrapper.find('video').element
    Object.defineProperties(video, {
      currentTime: { value: 37, writable: true, configurable: true },
      paused: { value: false, configurable: true },
      readyState: { value: HTMLMediaElement.HAVE_METADATA, configurable: true },
    })

    await wrapper.find('.quality-btn').trigger('click')
    await wrapper.findAll('.quality-option').at(-1)!.trigger('click')

    expect(instance.destroy).toHaveBeenCalled()
    expect(video.src).toBe(originalSrc)
    expect(video.currentTime).toBe(37)
    expect(video.play).toHaveBeenCalled()
    expect(localStorage.getItem('clonetube-quality')).toBe('original')
  })

  it('uses native HLS with only Auto and Original when the browser supports it', async () => {
    hlsMock.supported = false
    vi.mocked(HTMLMediaElement.prototype.canPlayType).mockReturnValue('maybe')
    const wrapper = mount(VideoPlayer, { props: { src: originalSrc, hlsSrc } })
    await flushPromises()
    await wrapper.find('.quality-btn').trigger('click')

    expect(wrapper.find('video').attributes('src')).toBe(hlsSrc)
    expect(wrapper.findAll('.quality-option').map((option) => option.text())).toEqual(['Auto', 'Original'])
    expect(hlsMock.instances).toHaveLength(0)
  })
})
