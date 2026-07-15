import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import VideoPlayer from '@/components/VideoPlayer.vue'

describe('VideoPlayer.vue', () => {
  it('renders video element with src', () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    const video = wrapper.find('video')
    expect(video.exists()).toBe(true)
    expect(video.attributes('src')).toBe('https://example.com/video.mp4')
  })

  it('shows loading spinner initially', () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    expect(wrapper.find('.loading-spinner').exists()).toBe(true)
  })

  it('renders controls', () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    expect(wrapper.find('.controls').exists()).toBe(true)
    expect(wrapper.find('.progress-bar').exists()).toBe(true)
    expect(wrapper.find('.volume-slider').exists()).toBe(true)
    expect(wrapper.find('.ctrl-btn.speed-btn').exists()).toBe(true)
  })

  it('shows big play button when not playing', () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    expect(wrapper.find('.big-play').exists()).toBe(true)
  })

  it('formatTime returns 0:00 for invalid input', async () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    expect(wrapper.vm.formatTime(-1)).toBe('0:00')
    expect(wrapper.vm.formatTime(NaN)).toBe('0:00')
    expect(wrapper.vm.formatTime(Infinity)).toBe('0:00')
  })

  it('formatTime formats correctly', async () => {
    const wrapper = mount(VideoPlayer, {
      props: { src: 'https://example.com/video.mp4' },
    })
    expect(wrapper.vm.formatTime(0)).toBe('0:00')
    expect(wrapper.vm.formatTime(65)).toBe('1:05')
    expect(wrapper.vm.formatTime(3661)).toBe('61:01')
  })
})
