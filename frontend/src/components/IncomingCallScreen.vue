<template>
  <a-drawer
    v-model:open="visible"
    title="来电弹屏"
    placement="right"
    width="420"
    :mask="false"
    :maskClosable="false"
    :keyboard="false"
    :bodyStyle="{ padding: '16px' }"
    @close="onClose"
  >
    <!-- 顶部：来电号码与状态 -->
    <a-card size="small" :bordered="false" class="call-head">
      <div class="caller-row">
        <div>
          <div class="caller-label">主叫号码</div>
          <div class="caller-number">{{ store.remoteNumber || '-' }}</div>
        </div>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </div>
      <div class="meta-row">
        <span>callId：{{ store.callId || '-' }}</span>
      </div>
    </a-card>

    <!-- 客户资料 -->
    <a-spin :spinning="customerLoading">
      <a-card v-if="customer" size="small" title="客户资料" class="section">
        <a-descriptions :column="1" size="small">
          <a-descriptions-item label="姓名">{{ customer.name }}</a-descriptions-item>
          <a-descriptions-item label="号码">{{ customer.phone }}</a-descriptions-item>
          <a-descriptions-item label="级别">
            <a-tag :color="levelColor(customer.level)">{{ customer.level || '-' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="来源">{{ customer.source || '-' }}</a-descriptions-item>
          <a-descriptions-item label="跟进坐席">{{ customer.ownerAgent || '-' }}</a-descriptions-item>
          <a-descriptions-item label="最近联系">{{ customer.lastContactAt || '-' }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ customer.remark || '-' }}</a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card v-else-if="!customerLoading && lookedUp" size="small" title="未找到客户" class="section">
        <a-empty :image-style="{ height: '48px' }" description="未匹配到客户，可快速新增">
          <a-button type="primary" size="small" @click="showQuickCreate = true">快速新增客户</a-button>
        </a-empty>
      </a-card>
    </a-spin>

    <!-- 历史通话入口 -->
    <a-card size="small" title="历史通话" class="section">
      <a-spin :spinning="historyLoading">
        <a-list v-if="history.length" size="small" :data-source="history" :split="false">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <a-tag :color="item.direction === 1 ? 'blue' : 'green'">
                    {{ item.direction === 1 ? '呼入' : '呼出' }}
                  </a-tag>
                  <span>{{ item.startedAt }}</span>
                  <span class="dur">· {{ formatDuration(item.duration) }}</span>
                </template>
                <template #description>
                  {{ item.peerNumber }}
                  <span v-if="item.hangupCause" class="cause">· {{ item.hangupCause }}</span>
                  <a v-if="item.hasRecording" class="rec-link" :href="item.recordUrl">录音/话单</a>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-else :image-style="{ height: '40px' }" description="暂无历史通话" />
      </a-spin>
    </a-card>

    <!-- 通话结束后：通话详情入口 -->
    <div v-if="callEnded" class="section ended-tip">
      <a-alert type="info" show-icon message="来电已结束" :description="endedDesc" />
    </div>

    <!-- 快速新增客户 -->
    <a-modal
      v-model:open="showQuickCreate"
      title="快速新增客户"
      :confirm-loading="creating"
      @ok="submitQuickCreate"
    >
      <a-form :label-col="{ span: 5 }">
        <a-form-item label="号码" required>
          <a-input :value="store.remoteNumber" disabled />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="quickForm.name" placeholder="请输入客户姓名" />
        </a-form-item>
        <a-form-item label="级别">
          <a-select v-model:value="quickForm.level">
            <a-select-option value="VIP">VIP</a-select-option>
            <a-select-option value="普通">普通</a-select-option>
            <a-select-option value="潜在">潜在</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="quickForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useSoftphone } from '@/hooks/useSoftphone'
import { CallState } from '@/consts/softphone'
import {
  queryCustomerByPhone,
  quickCreateCustomer,
  CustomerBrief
} from '@/apis/softphone/customer'
import { getCallHistoryByPhone, CallHistoryItem } from '@/apis/softphone/callHistory'
import { formatDuration } from '@/utils/format'

const store = useSoftphone()

const customer = ref<CustomerBrief | null>(null)
const customerLoading = ref(false)
const lookedUp = ref(false)
const history = ref<CallHistoryItem[]>([])
const historyLoading = ref(false)
const showQuickCreate = ref(false)
const creating = ref(false)
const dismissedFor = ref<Set<string>>(new Set())

const quickForm = reactive({
  name: '',
  level: '普通',
  remark: ''
})

/** 弹屏显隐：来电或通话中（呼入）且未被本次关闭 */
const visible = computed(() => {
  if (!store.remoteNumber) return false
  if (dismissedFor.value.has(store.callId || store.remoteNumber)) return false
  const inboundActive =
    store.direction === 1 &&
    [CallState.INCOMING, CallState.RINGING, CallState.ACTIVE, CallState.HOLD].includes(
      store.callState as CallState
    )
  return inboundActive || store.incomingCallFlag
})

const callEnded = computed(
  () =>
    lookedUp.value &&
    !visible.value &&
    !!store.remoteNumber === false &&
    (store.callState === CallState.IDLE || store.callState === CallState.REGISTERED)
)

const endedDesc = computed(() => `主叫 ${store.remoteNumber || '-'} 的通话已结束，可在话单管理查看详情。`)

const statusText = computed(() => {
  const map: Record<string, string> = {
    [CallState.INCOMING]: '来电中',
    [CallState.RINGING]: '振铃中',
    [CallState.ACTIVE]: '通话中',
    [CallState.HOLD]: '保持中'
  }
  return map[store.callState as string] || '通话中'
})
const statusColor = computed(() =>
  store.callState === CallState.HOLD ? 'orange' : store.callState === CallState.ACTIVE ? 'green' : 'blue'
)

// 主叫变化时触发客户/历史查询
watch(
  () => store.remoteNumber,
  async (number, old) => {
    if (!number || number === old) return
    await lookup(number)
  },
  { immediate: false }
)

watch(
  () => store.incomingCallFlag,
  (flag) => {
    if (flag && store.remoteNumber && !customer.value) {
      void lookup(store.remoteNumber)
    }
  }
)

async function lookup(number: string) {
  lookedUp.value = true
  customerLoading.value = true
  historyLoading.value = true
  try {
    const res: any = await queryCustomerByPhone(number)
    customer.value = res?.code === 200 ? (res.data as CustomerBrief) : null
  } catch (e) {
    customer.value = null
  } finally {
    customerLoading.value = false
  }
  try {
    const res: any = await getCallHistoryByPhone(number)
    history.value = res?.code === 200 ? (res.data as CallHistoryItem[]) : []
  } catch (e) {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

function onClose() {
  const key = store.callId || store.remoteNumber
  if (key) dismissedFor.value.add(key)
}

async function submitQuickCreate() {
  if (!quickForm.name) {
    message.warning('请输入客户姓名')
    return
  }
  creating.value = true
  try {
    const res: any = await quickCreateCustomer({
      name: quickForm.name,
      phone: store.remoteNumber,
      level: quickForm.level,
      remark: quickForm.remark,
      callId: store.callId
    })
    if (res?.code === 200) {
      message.success('客户已新增')
      customer.value = res.data as CustomerBrief
      showQuickCreate.value = false
      quickForm.name = ''
      quickForm.remark = ''
    } else {
      message.error(res?.msg || '新增失败')
    }
  } catch (e) {
    message.error('新增失败')
  } finally {
    creating.value = false
  }
}

function levelColor(level?: string): string {
  if (level === 'VIP') return 'gold'
  if (level === '潜在') return 'default'
  return 'blue'
}
</script>

<style lang="less" scoped>
.section {
  margin-top: 12px;
}
.call-head {
  background: #f0f7ff;
  :deep(.ant-card-body) {
    padding: 12px;
  }
}
.caller-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.caller-label {
  color: #8c8c8c;
  font-size: 12px;
}
.caller-number {
  font-size: 20px;
  font-weight: 600;
  color: #1677ff;
}
.meta-row {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 6px;
}
.action-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.dur {
  color: #8c8c8c;
}
.cause {
  color: #faad14;
}
.rec-link {
  margin-left: 8px;
}
.ended-tip {
  margin-top: 16px;
}
</style>
