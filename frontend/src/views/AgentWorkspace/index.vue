<template>
  <div class="agent-workspace">
    <a-row :gutter="16">
      <!-- 左：当前通话 + 客户查询 -->
      <a-col :xs="24" :lg="10">
        <a-card title="当前通话" :bordered="false" class="call-card">
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="状态">
              <a-badge :status="callBadge" :text="callStateText" />
            </a-descriptions-item>
            <a-descriptions-item label="方向">
              <a-tag :color="store.direction === 1 ? 'blue' : 'green'">
                {{ store.direction === 1 ? '呼入' : store.direction === 2 ? '呼出' : '-' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="对方号码">{{ store.remoteNumber || '-' }}</a-descriptions-item>
            <a-descriptions-item label="本机号码">{{ store.localNumber || '-' }}</a-descriptions-item>
            <a-descriptions-item label="callId">{{ store.callId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="通话时长">{{ store.statusDuration }}</a-descriptions-item>
            <a-descriptions-item label="SIP 注册">
              <a-badge :status="registerBadge" :text="registerText" />
            </a-descriptions-item>
          </a-descriptions>
          <a-space wrap style="margin-top: 8px;">
            <a-button size="small" :disabled="!store.hasActiveCall" @click="store.toggleHold">
              {{ store.isHold ? '恢复' : '保持' }}
            </a-button>
            <a-button size="small" :disabled="!store.hasActiveCall" @click="store.toggleMute">
              {{ store.isMute ? '取消静音' : '静音' }}
            </a-button>
            <a-button size="small" :disabled="!store.hasActiveCall" @click="store.openTransfer">转接</a-button>
            <a-button size="small" danger :disabled="!store.hasActiveCall && !store.incomingCallFlag" @click="store.hangup">
              挂断
            </a-button>
          </a-space>
        </a-card>

        <a-card title="客户查询" :bordered="false" class="section">
          <a-input-search
            v-model:value="queryNumber"
            placeholder="输入号码查询客户"
            enter-button="查询"
            @search="onQueryCustomer"
          />
          <a-spin :spinning="custLoading">
            <a-descriptions v-if="custInfo" :column="1" size="small" style="margin-top: 12px;">
              <a-descriptions-item label="姓名">{{ custInfo.name }}</a-descriptions-item>
              <a-descriptions-item label="号码">{{ custInfo.phone }}</a-descriptions-item>
              <a-descriptions-item label="级别">
                <a-tag :color="custInfo.level === 'VIP' ? 'gold' : 'blue'">{{ custInfo.level }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="备注">{{ custInfo.remark || '-' }}</a-descriptions-item>
            </a-descriptions>
            <a-empty v-else-if="queried" :image-style="{ height: '40px' }" description="未找到客户" style="margin-top: 12px;" />
          </a-spin>
        </a-card>

        <a-card title="快捷入口" :bordered="false" class="section">
          <a-space wrap>
            <a-button @click="goTts">TTS 配置与试听</a-button>
            <a-button @click="goTranscript">实时转写记录</a-button>
            <a-button @click="goCallRecord">话单管理</a-button>
            <a-button @click="goServiceStatus">语音服务状态</a-button>
          </a-space>
        </a-card>
      </a-col>

      <!-- 右：实时转写 -->
      <a-col :xs="24" :lg="14">
        <TranscriptionPanel />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import TranscriptionPanel from '@/components/TranscriptionPanel.vue'
import { useSoftphone } from '@/hooks/useSoftphone'
import { CallState, RegisterStatus } from '@/consts/softphone'
import { queryCustomerByPhone, CustomerBrief } from '@/apis/softphone/customer'

const store = useSoftphone()
const router = useRouter()

const queryNumber = ref('')
const custInfo = ref<CustomerBrief | null>(null)
const custLoading = ref(false)
const queried = ref(false)

const callBadge = computed<'default' | 'processing' | 'success' | 'warning' | 'error'>(() => {
  switch (store.callState as CallState) {
    case CallState.ACTIVE:
      return 'success'
    case CallState.RINGING:
    case CallState.INCOMING:
    case CallState.DIALING:
      return 'processing'
    case CallState.HOLD:
      return 'warning'
    default:
      return 'default'
  }
})

const callStateText = computed(() => {
  const map: Record<string, string> = {
    [CallState.IDLE]: '空闲',
    [CallState.REGISTERING]: '注册中',
    [CallState.REGISTERED]: '已就绪',
    [CallState.DIALING]: '拨号中',
    [CallState.RINGING]: '振铃中',
    [CallState.INCOMING]: '来电中',
    [CallState.ACTIVE]: '通话中',
    [CallState.HOLD]: '保持中',
    [CallState.ENDED]: '结束'
  }
  return map[store.callState as string] || '空闲'
})

const registerBadge = computed<'default' | 'processing' | 'success' | 'warning' | 'error'>(() => {
  switch (store.registerStatus as RegisterStatus) {
    case RegisterStatus.REGISTERED:
      return 'success'
    case RegisterStatus.REGISTERING:
      return 'processing'
    case RegisterStatus.FAILED:
      return 'error'
    case RegisterStatus.DISCONNECTED:
      return 'warning'
    default:
      return 'default'
  }
})
const registerText = computed(
  () =>
    (
      {
        [RegisterStatus.UNREGISTERED]: '未注册',
        [RegisterStatus.REGISTERING]: '注册中',
        [RegisterStatus.REGISTERED]: '已注册',
        [RegisterStatus.FAILED]: '注册失败',
        [RegisterStatus.DISCONNECTED]: '连接断开'
      } as Record<RegisterStatus, string>
    )[store.registerStatus as RegisterStatus] || '未注册'
)

async function onQueryCustomer(value: string) {
  const phone = (value || '').trim()
  if (!phone) {
    message.warning('请输入号码')
    return
  }
  custLoading.value = true
  queried.value = true
  try {
    const res: any = await queryCustomerByPhone(phone)
    custInfo.value = res?.code === 200 ? (res.data as CustomerBrief) : null
  } catch (e) {
    custInfo.value = null
  } finally {
    custLoading.value = false
  }
}

function goTts() {
  router.push('/ttsConfig')
}
function goTranscript() {
  router.push('/transcriptManagement')
}
function goCallRecord() {
  router.push('/callRecordManagement')
}
function goServiceStatus() {
  router.push('/aiServiceStatus')
}
</script>

<style lang="less" scoped>
.agent-workspace {
  width: 100%;
}
.section {
  margin-top: 16px;
}
.call-card :deep(.ant-descriptions) {
  margin-top: 4px;
}
</style>
