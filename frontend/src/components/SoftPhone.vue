<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  AudioMutedOutlined,
  AudioOutlined,
  CustomerServiceOutlined,
  LoginOutlined,
  LogoutOutlined,
  NumberOutlined,
  PauseOutlined,
  PhoneOutlined,
  PlayCircleOutlined,
  PoweroffOutlined,
  UserSwitchOutlined,
  WifiOutlined
} from '@ant-design/icons-vue'
import { useSoftphone } from '@/hooks/useSoftphone'
import {
  AGENT_STATUS_MAP,
  CallState,
  DIALPAD_ROWS,
  NETWORK_STATUS_COLOR,
  NETWORK_STATUS_TEXT,
  NetworkStatus,
  REGISTER_STATUS_COLOR,
  REGISTER_STATUS_TEXT,
  RegisterStatus
} from '@/consts/softphone'
import { sanitizeDial } from '@/utils/format'

const store = useSoftphone()
const isKeypadVisible = ref(false)

const registerStatusText = computed(
  () => REGISTER_STATUS_TEXT[store.registerStatus as RegisterStatus] || '未注册'
)
const registerStatusColor = computed(
  () => REGISTER_STATUS_COLOR[store.registerStatus as RegisterStatus] || '#8c8c8c'
)
const networkStatusText = computed(
  () => NETWORK_STATUS_TEXT[store.networkStatus as NetworkStatus] || '已断开'
)
const networkStatusColor = computed(
  () => NETWORK_STATUS_COLOR[store.networkStatus as NetworkStatus] || '#8c8c8c'
)

const callStateText = computed(() => {
  const labels: Record<string, string> = {
    [CallState.IDLE]: '空闲',
    [CallState.REGISTERING]: '注册中',
    [CallState.REGISTERED]: '已就绪',
    [CallState.DIALING]: '正在呼叫',
    [CallState.RINGING]: '振铃中',
    [CallState.INCOMING]: '来电',
    [CallState.ACTIVE]: '通话中',
    [CallState.HOLD]: '保持中',
    [CallState.ENDED]: '通话结束'
  }
  return labels[store.callState] || '空闲'
})

const agentStatusLabel = computed(
  () => AGENT_STATUS_MAP[store.agentStatus]?.label || '离线'
)

const phoneHeadline = computed(() => {
  if (store.hasActiveCall) {
    return store.remoteNumber || store.incomingCallNumber || store.phoneNumber || '当前通话'
  }
  return '软电话'
})

const phoneSubline = computed(() => {
  if (store.hasActiveCall) return `${callStateText.value} · ${store.statusDuration}`
  if (store.signInOut === '1') return `${agentStatusLabel.value} · ${store.statusDuration}`
  return store.loginState === '2' ? '客户端已登录，等待签入' : '客户端未登录'
})

const statusDotColor = computed(() => {
  if (store.hasActiveCall) return store.isHold ? '#f59e0b' : '#16a34a'
  if (store.signInOut === '1') return AGENT_STATUS_MAP[store.agentStatus]?.color || '#16a34a'
  return '#98a2b3'
})

async function toggleLogin() {
  if (store.loginState === '1') await store.login()
  else await store.logout()
}

async function toggleSignInOut() {
  if (store.signInOut === '1') await store.signOut()
  else await store.signIn()
}

function onAgentStatusChange(value: string | number) {
  store.setAgentStatus(String(value))
}

function isAutomaticStatus(key: string) {
  return ['4', '5', '6'].includes(key)
}

async function onSipAccountChange(agentId: number | string) {
  await store.selectSipAccount(Number(agentId))
}

function onPhoneNumberInput(event: Event) {
  store.setPhoneNumber((event.target as HTMLInputElement).value)
}

function onTransferInput(event: Event) {
  store.transferNumber = sanitizeDial((event.target as HTMLInputElement).value)
}

async function makeCallFromDialpad() {
  await store.makeCall()
  isKeypadVisible.value = false
}

onMounted(async () => {
  store.init()
  await store.loadSipConfig()
})

onBeforeUnmount(() => {
  store.destroy()
})
</script>

<template>
  <div class="soft-phone" :class="{ 'has-call': store.hasActiveCall }">
    <div class="phone-toolbar">
      <div class="phone-identity" :title="phoneSubline">
        <div class="phone-mark"><CustomerServiceOutlined /></div>
        <div class="phone-copy">
          <strong>{{ phoneHeadline }}</strong>
          <span><i :style="{ backgroundColor: statusDotColor }"></i>{{ phoneSubline }}</span>
        </div>
      </div>

      <div class="session-controls">
        <a-tooltip :title="store.loginState === '1' ? '登录 SIP 客户端' : '退出 SIP 客户端'">
          <a-button
            class="toolbar-icon login-toggle"
            size="small"
            :loading="store.isLoginLoading"
            :aria-label="store.loginState === '1' ? '登录 SIP 客户端' : '退出 SIP 客户端'"
            @click="toggleLogin"
          >
            <template #icon>
              <LoginOutlined v-if="store.loginState === '1'" />
              <LogoutOutlined v-else />
            </template>
          </a-button>
        </a-tooltip>

        <a-button
          class="sign-button"
          size="small"
          :class="{ 'is-signed-in': store.signInOut === '1' }"
          :disabled="store.loginState !== '2' || store.sipRegistrationChanging"
          :loading="store.sipRegistrationChanging"
          @click="toggleSignInOut"
        >
          <span>{{ store.signInOut === '1' ? '签出' : '签入' }}</span>
        </a-button>

        <a-select
          class="agent-select"
          :value="store.agentStatus"
          :disabled="store.signInOut !== '1'"
          size="small"
          @change="onAgentStatusChange"
        >
          <a-select-option
            v-for="key in Object.keys(AGENT_STATUS_MAP)"
            :key="key"
            :value="key"
            :disabled="isAutomaticStatus(key)"
          >
            {{ AGENT_STATUS_MAP[key].label }}
          </a-select-option>
        </a-select>
      </div>

      <div class="dial-zone">
        <a-input
          class="number-input"
          placeholder="输入号码"
          allow-clear
          :value="store.phoneNumber"
          @input="onPhoneNumberInput"
          @pressEnter="store.makeCall"
        >
          <template #prefix><PhoneOutlined /></template>
          <template #suffix>
            <a-popover v-model:open="isKeypadVisible" trigger="click" placement="bottom">
              <template #content>
                <div class="dialpad" aria-label="拨号盘">
                  <div class="dialpad-display">{{ store.phoneNumber || '请输入号码' }}</div>
                  <div class="dialpad-grid">
                    <a-button
                      v-for="num in DIALPAD_ROWS.flat()"
                      :key="num"
                      class="dialpad-button"
                      @click="store.appendDial(num)"
                    >
                      {{ num }}
                    </a-button>
                  </div>
                  <a-button
                    type="primary"
                    block
                    class="dialpad-call"
                    :disabled="!store.phoneNumber || !store.canDial"
                    @click="makeCallFromDialpad"
                  >
                    <PhoneOutlined /> 呼叫
                  </a-button>
                </div>
              </template>
              <a-button type="text" size="small" aria-label="打开拨号盘">
                <NumberOutlined />
              </a-button>
            </a-popover>
          </template>
        </a-input>
        <a-button
          class="call-button"
          type="primary"
          size="small"
          :disabled="!store.phoneNumber || !store.canDial"
          @click="store.makeCall"
        >
          <PhoneOutlined /><span>呼叫</span>
        </a-button>
      </div>

      <div class="call-actions" aria-label="通话控制">
        <a-tooltip :title="store.isHold ? '恢复通话' : '保持通话'">
          <a-button
            class="action-button"
            size="small"
            :class="{ active: store.isHold }"
            :disabled="!store.hasActiveCall"
            :aria-label="store.isHold ? '恢复通话' : '保持通话'"
            @click="store.toggleHold"
          >
            <PlayCircleOutlined v-if="store.isHold" />
            <PauseOutlined v-else />
          </a-button>
        </a-tooltip>

        <a-tooltip :title="store.isMute ? '取消静音' : '静音'">
          <a-button
            class="action-button"
            size="small"
            :class="{ active: store.isMute }"
            :disabled="!store.hasActiveCall"
            :aria-label="store.isMute ? '取消静音' : '静音'"
            @click="store.toggleMute"
          >
            <AudioMutedOutlined v-if="store.isMute" />
            <AudioOutlined v-else />
          </a-button>
        </a-tooltip>

        <a-tooltip title="转接">
          <a-button
            class="action-button"
            size="small"
            :disabled="!store.hasActiveCall"
            aria-label="转接"
            @click="store.openTransfer"
          >
            <UserSwitchOutlined />
          </a-button>
        </a-tooltip>

        <a-tooltip title="挂断">
          <a-button
            class="action-button hangup-button"
            size="small"
            :disabled="!store.hasActiveCall"
            aria-label="挂断"
            @click="store.hangup"
          >
            <PoweroffOutlined />
          </a-button>
        </a-tooltip>
      </div>

      <div class="connection-controls" aria-label="软电话配置">
        <label v-if="store.sipAccountList.length > 0" class="sip-account-field">
          <span class="connection-label">坐席</span>
          <a-select
            class="sip-account-select"
            :value="store.selectedAgentId"
            placeholder="选择坐席"
            size="small"
            :disabled="store.hasActiveCall"
            @change="onSipAccountChange"
          >
            <a-select-option
              v-for="item in store.sipAccountList"
              :key="item.agentId"
              :value="item.agentId"
            >
              {{ item.agentName || item.sipUser }}（{{ item.sipUser }}）
            </a-select-option>
          </a-select>
        </label>
        <a-tooltip title="SIP 注册状态">
          <span class="status-chip" :style="{ color: registerStatusColor }">
            <i :style="{ backgroundColor: registerStatusColor }"></i>
            <span>{{ registerStatusText }}</span>
          </span>
        </a-tooltip>
        <a-tooltip title="网络连接状态">
          <span class="status-chip" :style="{ color: networkStatusColor }">
            <WifiOutlined /><span>{{ networkStatusText }}</span>
          </span>
        </a-tooltip>
        <label class="auto-answer-control">
          <span class="connection-label">自动接听</span>
          <a-switch size="small" :checked="store.autoAnswer" @change="store.toggleAutoAnswer" />
        </label>
      </div>
    </div>

    <a-modal
      v-model:open="store.incomingCallFlag"
      class="incoming-call-modal"
      title="来电提醒"
      width="420px"
      :maskClosable="false"
      :keyboard="false"
      :closable="false"
    >
      <div class="incoming-call-content">
        <div class="incoming-icon"><PhoneOutlined /></div>
        <span>正在呼入</span>
        <strong>{{ store.incomingCallNumber || store.remoteNumber || '-' }}</strong>
        <small v-if="store.callId">通话标识：{{ store.callId }}</small>
      </div>
      <template #footer>
        <div class="incoming-actions">
          <a-button danger size="large" @click="store.hangup"><PoweroffOutlined /> 拒接</a-button>
          <a-button type="primary" size="large" @click="store.answer"><PhoneOutlined /> 接听</a-button>
        </div>
      </template>
    </a-modal>

    <a-modal
      v-model:open="store.transferModalVisible"
      title="通话转接"
      width="420px"
      ok-text="确认转接"
      cancel-text="取消"
      :maskClosable="false"
      @ok="store.confirmTransfer"
      @cancel="store.cancelTransfer"
    >
      <div class="transfer-content">
        <label for="transfer-number">转接号码</label>
        <a-input
          id="transfer-number"
          :value="store.transferNumber"
          size="large"
          placeholder="请输入分机号或电话号码"
          allow-clear
          @input="onTransferInput"
          @pressEnter="store.confirmTransfer"
        >
          <template #prefix><UserSwitchOutlined /></template>
        </a-input>
        <p>转接后，当前通话将由目标坐席或号码继续处理。</p>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.soft-phone {
  width: 100%;
  min-width: 0;
}

.phone-toolbar {
  min-width: 0;
  height: 48px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 7px;
  border: 1px solid #dfe7f0;
  border-radius: 10px;
  background: #f7f9fc;
}

.phone-identity {
  min-width: 96px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.phone-mark {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #e9f2ff;
  color: #155eef;
  font-size: 16px;
}

.has-call .phone-mark {
  background: #eaf8ef;
  color: #15803d;
}

.phone-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.2;

  strong {
    max-width: 94px;
    overflow: hidden;
    color: #27364b;
    font-size: 12px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: 3px;
    color: #667085;
    font-size: 10px;
    white-space: nowrap;

    i {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
  }
}

.session-controls,
.dial-zone,
.call-actions {
  display: flex;
  align-items: center;
  gap: 5px;
}

.session-controls {
  padding-left: 8px;
  border-left: 1px solid #dfe7f0;
}

.toolbar-icon,
.action-button {
  width: 30px;
  height: 30px;
  padding: 0;
  border-color: #d6dee8;
  border-radius: 7px;
  color: #475467;
  box-shadow: none;
}

.toolbar-icon:hover:not(:disabled),
.action-button:hover:not(:disabled) {
  border-color: #8ab8f8;
  color: #155eef;
}

.sign-button {
  min-width: 52px;
  height: 30px;
  border-color: #b9d5f8;
  border-radius: 7px;
  color: #155eef;
  box-shadow: none;

  &.is-signed-in {
    border-color: #cbd5e1;
    color: #475467;
    background: #fff;
  }
}

.agent-select {
  width: 92px;

  :deep(.ant-select-selector) {
    height: 30px !important;
    border-color: #d6dee8 !important;
    border-radius: 7px !important;
    box-shadow: none !important;
  }

  :deep(.ant-select-selection-item) {
    font-size: 12px;
    line-height: 28px !important;
  }
}

.dial-zone {
  min-width: 190px;
  flex: 1 1 260px;
  max-width: 310px;
}

.number-input {
  min-width: 118px;
  height: 32px;
  border-color: #cfd8e3;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;

  &:hover,
  &:focus-within {
    border-color: #6ba5f7;
  }

  :deep(.ant-input-prefix) {
    color: #667085;
  }
}

.call-button {
  height: 32px;
  padding-left: 12px;
  padding-right: 12px;
  padding-inline: 12px;
  border-radius: 8px;
  background: #1677ff;
  font-weight: 600;
  box-shadow: none;
}

.call-actions {
  padding-left: 8px;
  border-left: 1px solid #dfe7f0;
}

.action-button.active {
  border-color: #f5b942;
  background: #fff7e6;
  color: #b26a00;
}

.action-button.hangup-button {
  border-color: #ffd2d2;
  background: #fff1f0;
  color: #cf1322;

  &:hover:not(:disabled) {
    border-color: #ff7875;
    color: #a8071a;
  }
}

.connection-controls {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 8px;
  border-left: 1px solid #dfe7f0;
}

.sip-account-field,
.auto-answer-control,
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.connection-label {
  color: #667085;
  font-size: 11px;
}

.sip-account-select {
  width: 140px;

  :deep(.ant-select-selector) {
    height: 30px !important;
    border-color: #d6dee8 !important;
    border-radius: 7px !important;
    box-shadow: none !important;
  }

  :deep(.ant-select-selection-item) {
    font-size: 11px;
    line-height: 28px !important;
  }
}

.status-chip {
  height: 28px;
  padding: 0 7px;
  border-radius: 6px;
  background: #fff;
  font-size: 11px;
  font-weight: 600;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
}

.dialpad {
  width: 230px;
}

.dialpad-display {
  margin-bottom: 10px;
  padding: 8px 10px;
  overflow: hidden;
  border-radius: 7px;
  background: #f4f7fa;
  color: #27364b;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialpad-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.dialpad-button {
  height: 38px;
  border-color: #dfe7f0;
  border-radius: 8px;
  color: #27364b;
  font-weight: 600;
  box-shadow: none;
}

.dialpad-call {
  height: 36px;
  margin-top: 10px;
  border-radius: 8px;
  font-weight: 600;
}


.incoming-call-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 20px;

  > span {
    margin-top: 12px;
    color: #667085;
    font-size: 13px;
  }

  > strong {
    margin-top: 4px;
    color: #172033;
    font-size: 26px;
    font-weight: 650;
  }

  > small {
    margin-top: 10px;
    color: #7b8798;
    font-size: 11px;
  }
}

.incoming-icon {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eaf8ef;
  color: #15803d;
  font-size: 23px;
}

.incoming-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;

  .ant-btn {
    border-radius: 8px;
    font-weight: 600;
  }
}

.transfer-content {
  label {
    display: block;
    margin-bottom: 7px;
    color: #344054;
    font-size: 13px;
    font-weight: 600;
  }

  p {
    margin: 10px 0 0 !important;
    color: #667085;
    font-size: 12px;
  }
}

@media (max-width: 1200px) {
  .phone-identity {
    min-width: 32px;
  }

  .phone-copy,
  .call-button span {
    display: none;
  }

  .call-button {
    width: 32px;
    padding: 0;
  }

  .dial-zone {
    min-width: 150px;
  }

  .connection-label,
  .status-chip span {
    display: none;
  }

  .sip-account-select {
    width: 100px;
  }
}

@media (max-width: 1000px) {
  .phone-identity,
  .connection-controls {
    display: none;
  }

  .dial-zone {
    max-width: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .toolbar-icon,
  .action-button,
  .sign-button {
    transition: none;
  }
}
</style>
