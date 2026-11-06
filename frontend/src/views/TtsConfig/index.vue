<template>
  <div class="tts-config">
    <header class="page-heading">
      <div>
        <h1>TTS 配置与试听</h1>
        <p>统一配置服务商、音色和音频参数；真实模式下结果会进入 TTS 文件管理。</p>
      </div>
      <a-space>
        <a-button @click="router.push('/aiServiceStatus')">服务状态</a-button>
        <a-button @click="router.push('/ttsFileManagement')">文件管理</a-button>
      </a-space>
    </header>
    <a-row :gutter="16">
      <a-col :xs="24" :lg="14">
        <a-card title="TTS 参数配置" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="合成文本" required :validate-status="textValidate.status" :help="textValidate.help">
              <a-textarea
                v-model:value="params.text"
                :rows="4"
                :maxlength="500"
                placeholder="请输入需要合成的文本（最多 500 字）"
                show-count
              />
            </a-form-item>

            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="服务商" required>
                  <a-select v-model:value="params.provider" @change="onProviderChange">
                    <a-select-option v-for="p in TTS_PROVIDERS" :key="p.value" :value="p.value">
                      {{ p.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="音色" required :validate-status="voiceValidate.status" :help="voiceValidate.help">
                  <a-select v-model:value="params.voice" :loading="voiceLoading" placeholder="请选择音色">
                    <a-select-option v-for="v in voiceList" :key="v.value" :value="v.value">
                      {{ v.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item label="语速（-500 慢 ~ 500 快）">
              <a-slider v-model:value="params.rate" :min="-500" :max="500" :step="10" />
            </a-form-item>
            <a-form-item label="音调（-500 低 ~ 500 高）">
              <a-slider v-model:value="params.pitch" :min="-500" :max="500" :step="10" />
            </a-form-item>
            <a-form-item label="音量（0 ~ 100）">
              <a-slider v-model:value="params.volume" :min="0" :max="100" :step="1" />
            </a-form-item>

            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="采样率">
                  <a-select v-model:value="params.sampleRate">
                    <a-select-option v-for="s in TTS_SAMPLE_RATES" :key="s.value" :value="s.value">
                      {{ s.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="音频格式">
                  <a-select v-model:value="params.format">
                    <a-select-option v-for="f in TTS_FORMATS" :key="f.value" :value="f.value">
                      {{ f.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="10">
        <a-card title="试听" :bordered="false">
          <TtsPlayer :params="params" @generated="onGenerated" />
          <a-divider style="margin: 12px 0;" />
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="字数">{{ charCount }} / 500</a-descriptions-item>
            <a-descriptions-item label="当前配置">
              {{ params.provider }} · {{ voiceLabel }} · {{ params.format }} · {{ params.sampleRate }}Hz
            </a-descriptions-item>
            <a-descriptions-item label="最近结果">
              <a-tag v-if="lastStatus === 'success'" color="green">合成成功</a-tag>
              <a-tag v-else-if="lastStatus === 'fail'" color="red">合成失败</a-tag>
              <a-tag v-else color="default">未生成</a-tag>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import TtsPlayer from '@/components/TtsPlayer.vue'
import {
  TTS_PROVIDERS,
  TTS_SAMPLE_RATES,
  TTS_FORMATS,
  TtsParams
} from '@/consts/softphone'
import { buildTtsParams, countTextChars, validateTtsParams } from '@/utils/ttsParams'
import { getVoiceList, TtsVoice } from '@/apis/softphone/tts'

const params = reactive<TtsParams>(buildTtsParams())
const router = useRouter()
const voiceList = ref<TtsVoice[]>([])
const voiceLoading = ref(false)
const lastStatus = ref<'none' | 'success' | 'fail'>('none')

const charCount = computed(() => countTextChars(params.text))
const voiceLabel = computed(
  () => voiceList.value.find((v) => v.value === params.voice)?.label || params.voice || '-'
)

const issues = computed(() => validateTtsParams(params))
const textValidate = computed(() => {
  const issue = issues.value.find((i) => i.field === 'text')
  return issue ? { status: 'error' as const, help: issue.message } : { status: '' as const, help: '' }
})
const voiceValidate = computed(() => {
  const issue = issues.value.find((i) => i.field === 'voice')
  return issue ? { status: 'error' as const, help: issue.message } : { status: '' as const, help: '' }
})

async function loadVoices(provider: string) {
  voiceLoading.value = true
  try {
    const res: any = await getVoiceList(provider)
    voiceList.value = res?.code === 200 ? (res.data as TtsVoice[]) : []
    // 切换服务商时默认选第一个音色
    if (voiceList.value.length && !voiceList.value.some((v) => v.value === params.voice)) {
      params.voice = voiceList.value[0].value
    }
  } catch (e) {
    voiceList.value = []
  } finally {
    voiceLoading.value = false
  }
}

function onProviderChange(value: string) {
  params.provider = value
  params.voice = ''
  void loadVoices(value)
}

function onGenerated(payload: { ok: boolean; message?: string }) {
  if (payload.ok) {
    lastStatus.value = 'success'
  } else {
    lastStatus.value = 'fail'
    if (payload.message) message.error(payload.message)
  }
}

onMounted(() => {
  void loadVoices(params.provider)
})

// 首次音色列表加载后若为空，给个默认值避免校验红
watch(voiceList, (list) => {
  if (list.length && !params.voice) params.voice = list[0].value
})
</script>

<style lang="less" scoped>
.tts-config {
  width: 100%;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}
.page-heading h1 {
  margin: 0 0 4px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 20px;
  line-height: 28px;
}
.page-heading p {
  margin: 0;
  color: rgba(0, 0, 0, 0.65);
}
@media (max-width: 768px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
