/**
 * 实时转写组合式封装（方案二 7.13）。
 *
 * 暴露 transcription store 的响应式状态，并提供：
 * - 滚动控制（autoScroll、scrollToBottom、attachScrollRef）
 * - 展开/收起
 * - Mock 会话开关（演示用）
 */
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { useTranscriptionStore } from '@/stores/transcription'

export function useTranscription() {
  const store = useTranscriptionStore()
  const scrollRef = ref<HTMLElement | null>(null)
  let userScrolledUp = false

  function onUserScroll() {
    const el = scrollRef.value
    if (!el) return
    // 用户向上滚动时暂停自动滚动；滚到底部恢复
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40
    userScrolledUp = !nearBottom
    if (nearBottom && !store.autoScroll) {
      store.autoScroll = true
    }
  }

  function scrollToBottom() {
    const el = scrollRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  }

  function maybeAutoScroll() {
    if (store.autoScroll && !userScrolledUp) {
      nextTick(scrollToBottom)
    }
  }

  onBeforeUnmount(() => {
    /* store 为全局单例，不在组件卸载时清空数据 */
  })

  return {
    store,
    scrollRef,
    onUserScroll,
    scrollToBottom,
    maybeAutoScroll
  }
}
