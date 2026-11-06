<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getBrowserCapabilities } from '@/utils/browserCompat'

const dismissed = ref(false)
const capabilities = ref(getBrowserCapabilities())

const issues = computed(() => {
  const result: string[] = []
  if (!capabilities.value.webSocket) result.push('当前浏览器不支持 WebSocket，实时坐席状态和来电通知不可用。')
  if (!capabilities.value.webRtc || !capabilities.value.mediaDevices) {
    result.push('当前浏览器未启用 WebRTC，软电话的麦克风和通话功能不可用。')
  } else if (!capabilities.value.secureContext) {
    result.push('当前页面不是安全上下文，请使用 HTTPS（或 localhost）访问才能启用麦克风。')
  }
  return result
})

const visible = computed(() => !dismissed.value && issues.value.length > 0)

onMounted(() => {
  capabilities.value = getBrowserCapabilities()
})

function dismiss() {
  dismissed.value = true
}
</script>

<template>
  <div v-if="visible" class="browser-compat-notice" role="alert">
    <div class="browser-compat-copy">
      <strong>浏览器兼容性提示</strong>
      <span v-for="issue in issues" :key="issue">{{ issue }}</span>
    </div>
    <button type="button" class="browser-compat-close" aria-label="关闭提示" @click="dismiss">知道了</button>
  </div>
</template>

<style scoped>
.browser-compat-notice {
  position: fixed;
  top: 12px;
  left: 50%;
  z-index: 1100;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: calc(100vw - 32px);
  max-width: 720px;
  padding: 12px 16px;
  transform: translateX(-50%);
  border: 1px solid #f5c36a;
  border-radius: 6px;
  background: #fffbe6;
  color: #614700;
  box-shadow: 0 4px 12px rgba(97, 71, 0, 0.12);
  font-size: 13px;
  line-height: 1.5;
}

.browser-compat-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.browser-compat-copy strong {
  font-weight: 600;
}

.browser-compat-close {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 4px 10px;
  border: 1px solid #d9ad45;
  border-radius: 4px;
  background: transparent;
  color: #614700;
  cursor: pointer;
  font: inherit;
}

.browser-compat-close:focus {
  outline: 2px solid #1677ff;
  outline-offset: 2px;
}

.browser-compat-close:focus-visible {
  outline: 2px solid #1677ff;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .browser-compat-notice {
    top: 8px;
    width: calc(100vw - 16px);
    padding: 10px 12px;
  }
}
</style>
