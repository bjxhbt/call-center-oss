<template>
  <div class="voice-file-field">
    <a-select
      :value="normalizedValue || undefined"
      allow-clear
      option-label-prop="label"
      placeholder="请选择语音文件"
      show-search
      :filter-option="filterVoiceOption"
      @change="handleChange"
    >
      <a-select-option
        v-for="item in normalizedOptions"
        :key="item.id"
        :value="item.id"
        :label="formatVoiceLabel(item)"
      >
        <div class="voice-option-title">
          <span>{{ item.name || '未命名语音' }}</span>
          <span class="voice-option-id">ID {{ item.id }}</span>
        </div>
        <div class="voice-option-text">
          {{ item.speechText || '暂未维护语音文本' }}
        </div>
      </a-select-option>
    </a-select>

    <div v-if="normalizedValue" class="voice-detail" :class="{ 'voice-detail-missing': !selectedVoice }">
      <div class="voice-detail-row">
        <span class="voice-detail-label">语音名称</span>
        <span class="voice-detail-name">
          {{ selectedVoice?.name || '未找到对应语音文件' }}
        </span>
        <span class="voice-detail-id">ID {{ normalizedValue }}</span>
      </div>
      <div class="voice-detail-label voice-text-label">语音文本</div>
      <a-tooltip placement="top" :title="displayText">
        <div class="voice-detail-text">{{ displayText }}</div>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type VoiceOption = {
  id: string | number
  name?: string
  speechText?: string
  [key: string]: unknown
}

const props = withDefaults(
  defineProps<{
    modelValue?: string | number | null
    options?: VoiceOption[]
    fallbackText?: string
  }>(),
  {
    modelValue: '',
    options: () => [],
    fallbackText: ''
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string, voice?: VoiceOption]
}>()

const normalizedValue = computed(() =>
  props.modelValue == null ? '' : String(props.modelValue)
)

const normalizedOptions = computed(() =>
  props.options.map((item) => ({
    ...item,
    id: String(item.id)
  }))
)

const selectedVoice = computed(() =>
  normalizedOptions.value.find((item) => item.id === normalizedValue.value)
)

const displayText = computed(
  () => selectedVoice.value?.speechText || props.fallbackText || '该语音文件暂未维护文本'
)

const formatVoiceLabel = (voice: VoiceOption) =>
  `${voice.name || '未命名语音'}（ID ${voice.id}）`

const filterVoiceOption = (input: string, option: { value?: string | number }) => {
  const keyword = String(input || '').trim().toLowerCase()
  const fileId = String(option?.value ?? '')
  const voice = normalizedOptions.value.find((item) => item.id === fileId)
  return [voice?.name, voice?.id, voice?.speechText]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(keyword))
}

const handleChange = (value?: string | number) => {
  const normalized = value == null ? '' : String(value)
  const voice = normalizedOptions.value.find((item) => item.id === normalized)
  emit('update:modelValue', normalized)
  emit('change', normalized, voice)
}
</script>

<style scoped>
.voice-file-field {
  width: 100%;
}

.voice-option-title,
.voice-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.voice-option-title {
  justify-content: space-between;
  color: rgba(0, 0, 0, 0.85);
  font-weight: 500;
}

.voice-option-id,
.voice-option-text,
.voice-detail-label,
.voice-detail-id {
  color: rgba(0, 0, 0, 0.65);
  font-size: 12px;
}

.voice-option-id,
.voice-detail-id {
  flex: none;
}

.voice-option-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-detail {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f5f7fa;
}

.voice-detail-missing {
  background: #fff7e6;
}

.voice-detail-name {
  min-width: 0;
  overflow: hidden;
  color: rgba(0, 0, 0, 0.85);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-detail-id {
  margin-left: auto;
}

.voice-text-label {
  margin-top: 6px;
}

.voice-detail-text {
  display: -webkit-box;
  overflow: hidden;
  color: rgba(0, 0, 0, 0.85);
  font-size: 13px;
  line-height: 20px;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
