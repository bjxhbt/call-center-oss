<template>
  <div class="page-shell">
    <header class="page-heading">
      <div>
        <div class="title-row">
          <h1>语音服务状态</h1>
          <a-tag :color="liveMode ? 'processing' : 'default'">{{ liveMode ? '真实接口模式' : 'Mock 演示模式' }}</a-tag>
        </div>
        <p>查看 ASR/TTS 配置就绪情况，并执行不依赖电话线路的 ASR 模拟链路。</p>
      </div>
      <a-button :loading="loading" @click="loadStatus">
        <template #icon><ReloadOutlined /></template>
        刷新状态
      </a-button>
    </header>

    <div class="status-layout">
      <section class="surface status-summary">
        <div class="section-heading">
          <div>
            <h2>ASR 运行状态</h2>
            <p>来自 /asr/v1/admin/status</p>
          </div>
          <a-badge :status="asrStatus?.enabled ? 'success' : 'default'" :text="asrStatus?.enabled ? '已启用' : '未启用'" />
        </div>

        <a-skeleton v-if="loading && !asrStatus" active :paragraph="{ rows: 5 }" />
        <a-descriptions v-else :column="2" size="small" bordered>
          <a-descriptions-item label="配置服务商">{{ asrStatus?.configuredProvider || '-' }}</a-descriptions-item>
          <a-descriptions-item label="实际解析服务商">{{ asrStatus?.resolvedProvider || '-' }}</a-descriptions-item>
          <a-descriptions-item label="活动会话">{{ asrStatus?.activeSessions ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="输入格式">
            {{ asrStatus ? `${asrStatus.inputCodec} · ${asrStatus.sampleRate} Hz` : '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="FreeSWITCH ESL 事件通道">
            <a-tag :color="asrStatus?.freeSwitch?.eslConnected ? 'success' : 'error'">
              {{ asrStatus?.freeSwitch?.eslConnected ? 'CONNECTED' : 'DISCONNECTED' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="ESL 命令通道">
            <a-tag :color="commandChannelReady ? 'success' : asrStatus?.freeSwitch?.eslConnected ? 'warning' : 'error'">
              {{ commandChannelReady ? 'READY' : asrStatus?.freeSwitch?.eslConnected ? 'EVENT ONLY' : 'UNAVAILABLE' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="音频推流">
            <a-tag :color="asrStatus?.freeSwitch?.audioStreamOperational ? 'success' : 'error'">
              {{ asrStatus?.freeSwitch?.audioStreamOperational ? 'READY' : 'BLOCKED' }}
            </a-tag>
            <span v-if="asrStatus?.freeSwitch?.audioStreamEnabled">
              {{ `${asrStatus.freeSwitch.audioStreamModule || '-'} · ${asrStatus.freeSwitch.audioStreamSampleRate || '-'} Hz` }}
            </span>
            <span v-else>未启用</span>
          </a-descriptions-item>
        </a-descriptions>

        <div class="provider-list">
          <div class="list-heading"><strong>ASR Provider</strong><span>代码/配置就绪</span></div>
          <div v-for="item in asrProviders" :key="item.provider" class="provider-row">
            <span>{{ item.provider }}</span>
            <a-tag :color="item.available ? 'success' : 'default'">{{ item.available ? 'READY' : 'NOT READY' }}</a-tag>
          </div>
          <a-empty v-if="!asrProviders.length && !loading" :image-style="{ height: '40px' }" description="未返回 ASR 状态" />
        </div>
      </section>

      <section class="surface">
        <div class="section-heading">
          <div>
            <h2>TTS Provider</h2>
            <p>来自 /tts/v1/providers</p>
          </div>
          <a-button type="link" @click="router.push('/ttsConfig')">配置与试听</a-button>
        </div>
        <a-skeleton v-if="loading && !ttsProviders.length" active :paragraph="{ rows: 4 }" />
        <div v-else class="provider-list compact">
          <div class="list-heading"><strong>服务商</strong><span>Provider 可用性</span></div>
          <div v-for="item in ttsProviders" :key="item.provider" class="provider-row">
            <span>{{ item.provider }}</span>
            <a-tag :color="item.ready ? 'success' : item.available ? 'warning' : 'default'">
              {{ item.ready ? 'READY' : item.available ? 'CONFIG MISSING' : 'UNAVAILABLE' }}
            </a-tag>
          </div>
          <a-empty v-if="!ttsProviders.length" :image-style="{ height: '40px' }" description="未返回 TTS 状态" />
        </div>
        <div class="runtime-block">
          <div class="list-heading"><strong>播放运行时</strong><span>唯一 Bean 校验</span></div>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="配置引擎">{{ ttsRuntime?.configuredEngine || '-' }}</a-descriptions-item>
            <a-descriptions-item label="实际实现">{{ ttsRuntime?.resolvedPlaybackPort || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Bean 数量">
              <a-tag :color="ttsRuntime?.uniquePlaybackBean ? 'success' : 'error'">
                {{ ttsRuntime?.playbackBeanCount ?? '-' }} · {{ ttsRuntime?.uniquePlaybackBean ? 'UNIQUE' : 'CONFLICT' }}
              </a-tag>
            </a-descriptions-item>
          </a-descriptions>
        </div>
      </section>
    </div>

    <section class="surface simulation-panel">
      <div class="section-heading">
        <div>
          <h2>ASR 模拟链路</h2>
          <p>使用后端可读取的 WAV 路径验证“会话创建 → 喂音频 → 挂断 → 落库”，不代表 FreeSWITCH 推流已通过。</p>
        </div>
      </div>
      <a-form layout="vertical" :model="simulation">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8">
            <a-form-item label="callId" required>
              <a-input-number v-model:value="simulation.callId" :min="1" :precision="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-form-item label="主叫号码">
              <a-input v-model:value="simulation.caller" placeholder="13800000000" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="8">
            <a-form-item label="被叫号码">
              <a-input v-model:value="simulation.callee" placeholder="10086" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="后端 WAV 路径" required>
          <a-input v-model:value="simulation.wavPath" placeholder="classpath:audio/asr-smoke.wav 或后端服务器绝对路径" />
          <div class="field-help">路径由 och-api 所在服务器读取，不是浏览器本地路径。</div>
        </a-form-item>
        <a-button type="primary" :loading="simulating" :disabled="!canSimulate" @click="runSimulation">
          <template #icon><ExperimentOutlined /></template>
          执行模拟
        </a-button>
      </a-form>

      <a-result
        v-if="simulationResult"
        class="simulation-result"
        :status="simulationResult.framesSent > 0 ? 'success' : 'warning'"
        :title="simulationResult.framesSent > 0 ? '模拟链路已执行' : '链路已执行，但未发送音频帧'"
        :sub-title="`Provider ${simulationResult.resolvedProvider || '-'}，发送 ${simulationResult.framesSent || 0} 帧`"
      >
        <template #extra>
          <a-button v-if="simulationResult.summary?.callId" @click="goTranscript(simulationResult.summary.callId)">查看转写记录</a-button>
        </template>
      </a-result>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ExperimentOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAsrServiceStatus, simulateAsrChain, type AsrServiceStatus, type AsrSimulationResult } from '@/apis/softphone/aiVoice'
import { getTtsProviderStatus, getTtsRuntimeStatus, type TtsProviderStatus, type TtsRuntimeStatus } from '@/apis/softphone/tts'
import { SOFTPHONE_LIVE_API } from '@/apis/softphone'
import { allSettledCompat } from '@/utils/browserCompat'

const router = useRouter()
const liveMode = SOFTPHONE_LIVE_API
const loading = ref(false)
const simulating = ref(false)
const asrStatus = ref<AsrServiceStatus | null>(null)
const ttsProviders = ref<TtsProviderStatus[]>([])
const ttsRuntime = ref<TtsRuntimeStatus | null>(null)
const simulationResult = ref<AsrSimulationResult | null>(null)
const simulation = reactive({
  callId: Date.now(),
  caller: '13800000000',
  callee: '10086',
  wavPath: ''
})

const asrProviders = computed(() =>
  Object.entries(asrStatus.value?.readyProviders || {}).map(([provider, available]) => ({ provider, available }))
)
const commandChannelReady = computed(
  () => asrStatus.value?.freeSwitch?.eslCommandReady ?? Boolean(asrStatus.value?.freeSwitch?.eslConnected)
)
const canSimulate = computed(() => Number(simulation.callId) > 0 && Boolean(simulation.wavPath.trim()))

async function loadStatus() {
  loading.value = true
  const [asr, tts, runtime] = await allSettledCompat([
    getAsrServiceStatus(),
    getTtsProviderStatus(),
    getTtsRuntimeStatus()
  ])
  if (asr.status === 'fulfilled' && asr.value?.code === 200) {
    asrStatus.value = asr.value.data
  }
  if (tts.status === 'fulfilled' && tts.value?.code === 200) {
    ttsProviders.value = tts.value.data || []
  }
  if (runtime.status === 'fulfilled' && runtime.value?.code === 200) {
    ttsRuntime.value = runtime.value.data
  }
  if (asr.status === 'rejected' || tts.status === 'rejected' || runtime.status === 'rejected') {
    message.warning('部分语音服务状态暂不可用，请检查后端服务和权限')
  }
  loading.value = false
}

async function runSimulation() {
  if (!canSimulate.value) return
  simulating.value = true
  simulationResult.value = null
  try {
    const res = await simulateAsrChain({
      callId: Number(simulation.callId),
      caller: simulation.caller.trim(),
      callee: simulation.callee.trim(),
      wavPath: simulation.wavPath.trim(),
      direction: 2
    })
    if (res?.code === 200) {
      simulationResult.value = res.data
      message.success('ASR 模拟链路执行完成')
    }
  } catch (error: any) {
    message.error(error?.message || 'ASR 模拟链路执行失败')
  } finally {
    simulating.value = false
  }
}

function goTranscript(callId: string) {
  router.push({ path: '/transcriptManagement', query: { callId } })
}

void loadStatus()
</script>

<style scoped lang="less">
.page-shell { min-width: 0; }
.page-heading, .section-heading, .title-row { display: flex; align-items: flex-start; }
.page-heading { justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.title-row { align-items: center; gap: 10px; }
.page-heading h1 { margin: 0; color: rgba(0, 0, 0, 0.88); font-size: 20px; line-height: 28px; }
.page-heading p, .section-heading p { margin: 4px 0 0; color: rgba(0, 0, 0, 0.65); }
.status-layout { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr); gap: 16px; }
.surface { background: #fff; padding: 20px; }
.section-heading { justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.section-heading h2 { margin: 0; color: rgba(0, 0, 0, 0.88); font-size: 16px; line-height: 24px; }
.provider-list { margin-top: 20px; border-top: 1px solid #f0f0f0; }
.provider-list.compact { margin-top: 0; }
.runtime-block { margin-top: 20px; border-top: 1px solid #f0f0f0; }
.list-heading, .provider-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 44px; }
.list-heading { color: rgba(0, 0, 0, 0.65); }
.provider-row { border-top: 1px solid #f0f0f0; color: rgba(0, 0, 0, 0.88); }
.simulation-panel { margin-top: 16px; }
.field-help { margin-top: 4px; color: rgba(0, 0, 0, 0.58); font-size: 12px; }
.simulation-result { padding-bottom: 0; }
@media (max-width: 900px) {
  .status-layout { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .page-heading { align-items: stretch; flex-direction: column; gap: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}
</style>
