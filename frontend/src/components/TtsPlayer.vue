<template>
  <div class="tts-player">
    <a-space wrap>
      <a-button
        type="primary"
        :loading="generateStatus === 'generating'"
        :disabled="!canPreview"
        @click="onPreview"
      >
        <template #icon><SoundOutlined /></template>
        {{ previewButtonText }}
      </a-button>
      <template v-if="currentResult">
        <a-button v-if="playStatus === 'playing'" @click="pause">
          <template #icon><PauseOutlined /></template>暂停
        </a-button>
        <a-button v-else-if="playStatus === 'paused'" type="primary" @click="play">
          <template #icon><CaretRightOutlined /></template>继续
        </a-button>
        <a-button
          v-if="currentResult"
          :disabled="playStatus === 'playing'"
          @click="replay"
        >
          <template #icon><ReloadOutlined /></template>重新播放
        </a-button>
      </template>
    </a-space>

    <div v-if="currentResult" class="progress">
      <a-slider
        :min="0"
        :max="maxSlider"
        :value="currentSlider"
        :tip-formatter="(v: any) => `${v}s`"
        @change="onSeek"
      />
      <div class="time">
        {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
      </div>
    </div>

    <a-alert
      v-if="generateStatus === 'error'"
      type="error"
      show-icon
      :message="errorMessage || '合成失败'"
      style="margin-top: 8px;"
    />
    <a-alert
      v-else-if="generateStatus === 'ready' && playStatus === 'ended'"
      type="success"
      show-icon
      message="播放结束"
      style="margin-top: 8px;"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  CaretRightOutlined,
  PauseOutlined,
  ReloadOutlined,
  SoundOutlined
} from '@ant-design/icons-vue'
import { useTtsPlayer } from '@/hooks/useTtsPlayer'
import { TtsParams } from '@/consts/softphone'
import { validateTtsParams, countTextChars } from '@/utils/ttsParams'
import { formatDuration } from '@/utils/format'

const props = defineProps<{
  params: TtsParams
}>()

const emit = defineEmits<{
  (e: 'generated', payload: { ok: boolean; message?: string }): void
}>()

// 解构为顶层 ref，模板自动解包
const {
  generateStatus,
  playStatus,
  errorMessage,
  currentTime,
  duration,
  currentResult,
  generate,
  play,
  pause,
  replay,
  seek
} = useTtsPlayer()

const issues = computed(() => validateTtsParams(props.params))
const canPreview = computed(
  () =>
    generateStatus.value !== 'generating' &&
    countTextChars(props.params.text) > 0 &&
    issues.value.length === 0
)

const maxSlider = computed(() => Math.max(1, Math.ceil(duration.value || 0)))
const currentSlider = computed(() => Math.floor(currentTime.value || 0))

const previewButtonText = computed(() => {
  if (generateStatus.value === 'generating') return '生成中...'
  if (currentResult.value && playStatus.value === 'ended') return '重新试听'
  return '试听'
})

async function onPreview() {
  if (!canPreview.value) return
  const ok = await generate(props.params)
  if (ok) {
    await play()
    emit('generated', { ok: true })
  } else {
    emit('generated', { ok: false, message: errorMessage.value })
  }
}

function onSeek(value: any) {
  seek(Number(value))
}

function formatTime(seconds: number): string {
  return formatDuration(Math.floor(seconds || 0))
}
</script>

<style lang="less" scoped>
.tts-player {
  width: 100%;
}
.progress {
  margin-top: 8px;
  :deep(.ant-slider) {
    margin: 4px 0;
  }
}
.time {
  text-align: right;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
