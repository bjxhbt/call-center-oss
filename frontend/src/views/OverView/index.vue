<script setup lang="ts">
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CustomerServiceOutlined,
  DashboardOutlined,
  PhoneOutlined,
  ReloadOutlined,
  RightOutlined,
  RobotOutlined,
  SettingOutlined,
  SoundOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'
import { getActiveCalls, getAgentStatusList, getOverview } from '@/apis/callingConfigure/monitor'
import { applicationName } from '@/consts/branding'
import { allSettledCompat } from '@/utils/browserCompat'

type DashboardResponse = {
  code?: number
  data?: any
}

const router = useRouter()
const loading = ref(false)
const loadFailed = ref(false)
const overviewLoaded = ref(false)
const overview = ref<Record<string, number>>({})
const agents = ref<any[]>([])
const activeCalls = ref<any[]>([])
const lastUpdated = ref<Date | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const todayText = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

const formatTime = (value?: string) => {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

const formatRefreshTime = computed(() => {
  if (!lastUpdated.value) return '等待首次刷新'
  return `更新于 ${lastUpdated.value.toLocaleTimeString('zh-CN', { hour12: false })}`
})

const agentStats = computed(() => {
  const stats = { total: agents.value.length, online: 0, idle: 0, onCall: 0, busy: 0 }
  agents.value.forEach((agent) => {
    const status = Number(agent.onlineStatus)
    if (status !== 4) stats.online += 1
    if (status === 1) stats.idle += 1
    if (status === 5 || status === 6) stats.onCall += 1
    if ([2, 3, 7].includes(status)) stats.busy += 1
  })
  return stats
})

const statItems = computed(() => [
  {
    label: '今日呼叫',
    value: overviewLoaded.value ? overview.value.todayTotal || 0 : '--',
    note: `呼入 ${overview.value.todayInbound || 0} / 呼出 ${overview.value.todayOutbound || 0}`,
    icon: PhoneOutlined,
    tone: 'blue'
  },
  {
    label: '当前通话',
    value: overviewLoaded.value ? overview.value.activeCalls || 0 : '--',
    note: '实时进行中的会话',
    icon: DashboardOutlined,
    tone: 'cyan'
  },
  {
    label: '在线坐席',
    value: agents.value.length ? agentStats.value.online : '--',
    note: `坐席总数 ${agentStats.value.total}`,
    icon: TeamOutlined,
    tone: 'green'
  },
  {
    label: '今日接通率',
    value: overviewLoaded.value ? `${overview.value.answerRate || 0}%` : '--',
    note: `已接通 ${overview.value.todayAnswered || 0} 通`,
    icon: CheckCircleOutlined,
    tone: 'orange'
  }
])

const agentStatusItems = computed(() => [
  { label: '空闲', value: agentStats.value.idle, color: '#16a34a' },
  { label: '通话 / 振铃', value: agentStats.value.onCall, color: '#1677ff' },
  { label: '忙碌 / 话后', value: agentStats.value.busy, color: '#f59e0b' },
  {
    label: '离线',
    value: Math.max(agentStats.value.total - agentStats.value.online, 0),
    color: '#98a2b3'
  }
])

const quickEntries = [
  { title: '坐席工作台', description: '处理呼入、外呼与客户沟通', path: '/agentWorkspace', icon: CustomerServiceOutlined },
  { title: '实时监控', description: '查看坐席和通话运行态势', path: '/realtimeMonitor', icon: DashboardOutlined },
  { title: '任务管理', description: '配置并跟进呼叫任务', path: '/callTaskManagement', icon: ApartmentOutlined },
  { title: '拨号计划', description: '维护号码匹配和路由规则', path: '/dialPlanManagement', icon: SettingOutlined }
]

const capabilityItems = [
  { title: '统一语音接入', description: 'SIP、E1 中继与 WebRTC 坐席接入', icon: PhoneOutlined },
  { title: '智能语音服务', description: '实时语音转写、语音合成与录音管理', icon: RobotOutlined },
  { title: '业务协同支撑', description: '客户、工单、任务与通话记录统一流转', icon: SoundOutlined }
]

const fetchDashboard = async () => {
  loading.value = true
  loadFailed.value = false
  try {
    const results = await allSettledCompat([
      getOverview(),
      getAgentStatusList(),
      getActiveCalls(1, 5)
    ])
    const [overviewResult, agentsResult, activeCallsResult] = results

    if (overviewResult.status === 'fulfilled') {
      const response = overviewResult.value as DashboardResponse
      if (response.code === 200 && response.data) {
        overview.value = response.data
        overviewLoaded.value = true
      }
    }
    if (agentsResult.status === 'fulfilled') {
      const response = agentsResult.value as DashboardResponse
      if (response.code === 200) agents.value = response.data || []
    }
    if (activeCallsResult.status === 'fulfilled') {
      const response = activeCallsResult.value as DashboardResponse
      if (response.code === 200) activeCalls.value = response.data?.list || []
    }

    loadFailed.value = results.every((result) => result.status === 'rejected')
    lastUpdated.value = new Date()
  } finally {
    loading.value = false
  }
}

const goTo = (path: string) => router.push(path)

onMounted(() => {
  fetchDashboard()
  refreshTimer = setInterval(fetchDashboard, 15000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="overview-page">
    <section class="overview-hero" aria-labelledby="overview-title">
      <div>
        <div class="product-name">{{ applicationName }}</div>
        <h1 id="overview-title">呼叫中心运行工作台</h1>
        <p>统一承载电话接入、坐席服务、任务调度与智能语音能力。</p>
      </div>
      <div class="hero-meta">
        <span class="system-status"><i></i>系统运行中</span>
        <time>{{ todayText }}</time>
      </div>
    </section>

    <section class="stat-strip" aria-label="今日运行指标">
      <article v-for="item in statItems" :key="item.label" class="stat-item">
        <div class="stat-icon" :class="`is-${item.tone}`">
          <component :is="item.icon" />
        </div>
        <div class="stat-content">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </article>
    </section>

    <div v-if="loadFailed" class="load-alert" role="status">
      暂时无法获取实时运行数据，请检查监控服务后重试。
    </div>

    <div class="overview-grid">
      <section class="panel operation-panel" aria-labelledby="operation-title">
        <header class="panel-header">
          <div>
            <h2 id="operation-title">实时运行态势</h2>
            <p>坐席状态和当前通话每 15 秒自动更新</p>
          </div>
          <button class="refresh-button" type="button" :disabled="loading" @click="fetchDashboard">
            <ReloadOutlined :spin="loading" />
            <span>{{ formatRefreshTime }}</span>
          </button>
        </header>

        <div class="operation-body">
          <div class="agent-overview">
            <div class="agent-total">
              <span>坐席在线</span>
              <strong>{{ agentStats.online }}<small>/ {{ agentStats.total }}</small></strong>
            </div>
            <div class="agent-status-list">
              <div v-for="status in agentStatusItems" :key="status.label" class="agent-status-row">
                <span><i :style="{ backgroundColor: status.color }"></i>{{ status.label }}</span>
                <strong>{{ status.value }}</strong>
              </div>
            </div>
          </div>

          <div class="active-call-list">
            <div class="list-title">
              <h3>当前通话</h3>
              <button type="button" @click="goTo('/realtimeMonitor')">查看全部 <RightOutlined /></button>
            </div>
            <div v-if="activeCalls.length" class="call-rows">
              <div v-for="call in activeCalls" :key="call.callId || call.id" class="call-row">
                <div class="call-direction"><PhoneOutlined /></div>
                <div class="call-numbers">
                  <strong>{{ call.callerNumber || '-' }} <span>→</span> {{ call.calleeNumber || '-' }}</strong>
                  <small>{{ call.agentName || call.agentNumber || '待分配坐席' }}</small>
                </div>
                <time>{{ formatTime(call.callStartTime) }}</time>
              </div>
            </div>
            <div v-else class="empty-calls">
              <CheckCircleOutlined />
              <span>当前没有进行中的通话</span>
            </div>
          </div>
        </div>
      </section>

      <section class="panel quick-panel" aria-labelledby="quick-title">
        <header class="panel-header">
          <div>
            <h2 id="quick-title">快捷入口</h2>
            <p>进入常用业务与运维功能</p>
          </div>
        </header>
        <nav class="quick-list" aria-label="常用功能">
          <button v-for="entry in quickEntries" :key="entry.path" type="button" @click="goTo(entry.path)">
            <span class="quick-icon"><component :is="entry.icon" /></span>
            <span class="quick-copy">
              <strong>{{ entry.title }}</strong>
              <small>{{ entry.description }}</small>
            </span>
            <RightOutlined class="quick-arrow" />
          </button>
        </nav>
      </section>
    </div>

    <section class="capability-section" aria-labelledby="capability-title">
      <div class="capability-heading">
        <h2 id="capability-title">平台核心能力</h2>
        <p>语音接入、客户服务与呼叫运营</p>
      </div>
      <div class="capability-list">
        <article v-for="item in capabilityItems" :key="item.title">
          <component :is="item.icon" />
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="less">
.overview-page {
  --overview-ink: #172033;
  --overview-muted: #667085;
  --overview-border: #dfe6ee;
  min-height: 100%;
  color: var(--overview-ink);
}

.overview-hero {
  min-height: 148px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 28px 32px;
  border-radius: 12px;
  background: #123a70;
  color: #fff;

  .product-name {
    margin-bottom: 8px;
    color: #b9d4f5;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0;
  }

  h1 {
    margin: 0;
    color: #fff;
    font-size: 28px;
    font-weight: 650;
    line-height: 1.3;
    letter-spacing: -0.02em;
  }

  p {
    margin: 8px 0 0 !important;
    color: #dce9f8;
    font-size: 14px;
  }
}

.hero-meta {
  min-width: max-content;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;

  time {
    color: #dce9f8;
    font-size: 13px;
  }
}

.system-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 999px;
  color: #fff;
  font-size: 13px;

  i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #4ade80;
  }
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 16px;
  border: 1px solid var(--overview-border);
  border-radius: 12px;
  background: #fff;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  padding: 20px 22px;

  & + & {
    border-left: 1px solid #e9eef4;
  }
}

.stat-icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  font-size: 19px;

  &.is-blue { color: #155eef; background: #edf4ff; }
  &.is-cyan { color: #087e8b; background: #eaf8fa; }
  &.is-green { color: #15803d; background: #edf9f0; }
  &.is-orange { color: #b54708; background: #fff4e8; }
}

.stat-content {
  min-width: 0;
  display: grid;

  span {
    color: var(--overview-muted);
    font-size: 13px;
  }

  strong {
    margin-top: 2px;
    color: var(--overview-ink);
    font-size: 24px;
    font-weight: 650;
    line-height: 1.25;
  }

  small {
    margin-top: 2px;
    overflow: hidden;
    color: #7b8798;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.load-alert {
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid #f5d08a;
  border-radius: 8px;
  background: #fff8e8;
  color: #7a4d00;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  margin-top: 16px;
}

.panel {
  border: 1px solid var(--overview-border);
  border-radius: 12px;
  background: #fff;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #e9eef4;

  h2 {
    margin: 0;
    color: var(--overview-ink);
    font-size: 16px;
    font-weight: 650;
  }

  p {
    margin: 3px 0 0 !important;
    color: var(--overview-muted);
    font-size: 12px;
  }
}

.refresh-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #526072;
  font: inherit;
  font-size: 12px;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: #f2f6fb;
    color: #155eef;
  }

  &:focus-visible {
    outline: 2px solid #1677ff;
    outline-offset: 2px;
  }
}

.operation-body {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 282px;
}

.agent-overview {
  padding: 20px;
  border-right: 1px solid #e9eef4;
  background: #f8fafc;
}

.agent-total {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3e9f0;

  span {
    color: var(--overview-muted);
    font-size: 13px;
  }

  strong {
    color: var(--overview-ink);
    font-size: 28px;
    font-weight: 650;
    line-height: 1;

    small {
      color: #7b8798;
      font-size: 13px;
      font-weight: 500;
    }
  }
}

.agent-status-list {
  margin-top: 12px;
}

.agent-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;

  span {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #475467;

    i {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }
  }

  strong {
    color: var(--overview-ink);
    font-weight: 600;
  }
}

.active-call-list {
  min-width: 0;
  padding: 18px 20px;
}

.list-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }

  button {
    border: 0;
    background: transparent;
    color: #155eef;
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }
}

.call-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;

  & + & {
    border-top: 1px solid #edf1f5;
  }

  time {
    margin-left: auto;
    color: #7b8798;
    font-size: 12px;
  }
}

.call-direction {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #edf4ff;
  color: #155eef;
}

.call-numbers {
  min-width: 0;
  display: flex;
  flex-direction: column;

  strong {
    overflow: hidden;
    color: #344054;
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;

    span { color: #98a2b3; }
  }

  small {
    margin-top: 2px;
    color: #7b8798;
    font-size: 12px;
  }
}

.empty-calls {
  min-height: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #7b8798;

  .anticon {
    color: #16a34a;
    font-size: 25px;
  }
}

.quick-list {
  padding: 6px 12px 12px;

  button {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 8px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    text-align: left;
    cursor: pointer;
    transition: background-color 160ms ease;

    & + button {
      border-top: 1px solid #edf1f5;
      border-radius: 0;
    }

    &:hover {
      background: #f4f8fd;
    }

    &:focus-visible {
      outline: 2px solid #1677ff;
      outline-offset: -2px;
    }
  }
}

.quick-icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #eef4fb;
  color: #174f8f;
  font-size: 17px;
}

.quick-copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;

  strong {
    color: #27364b;
    font-size: 13px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    overflow: hidden;
    color: var(--overview-muted);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.quick-arrow {
  color: #98a2b3;
  font-size: 11px;
}

.capability-section {
  display: flex;
  align-items: stretch;
  gap: 28px;
  margin-top: 16px;
  padding: 20px 24px;
  border: 1px solid var(--overview-border);
  border-radius: 12px;
  background: #fff;
}

.capability-heading {
  width: 220px;
  flex: 0 0 220px;
  padding-right: 24px;
  border-right: 1px solid #e9eef4;

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 650;
  }

  p {
    margin: 6px 0 0 !important;
    color: var(--overview-muted);
    font-size: 12px;
    line-height: 1.7;
  }
}

.capability-list {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;

  article {
    display: flex;
    align-items: flex-start;
    gap: 12px;

    > .anticon {
      margin-top: 2px;
      color: #155eef;
      font-size: 18px;
    }

    div {
      display: flex;
      flex-direction: column;
    }

    strong {
      color: #27364b;
      font-size: 13px;
      font-weight: 600;
    }

    span {
      margin-top: 4px;
      color: var(--overview-muted);
      font-size: 12px;
      line-height: 1.6;
    }
  }
}

@media (max-width: 1280px) {
  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-item:nth-child(3) {
    border-left: 0;
    border-top: 1px solid #e9eef4;
  }

  .stat-item:nth-child(4) {
    border-top: 1px solid #e9eef4;
  }

  .overview-grid {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 980px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .operation-body {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .capability-section {
    flex-direction: column;
  }

  .capability-heading {
    width: auto;
    padding: 0 0 16px;
    border-right: 0;
    border-bottom: 1px solid #e9eef4;
  }
}

@media (max-width: 720px) {
  .overview-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 24px;
  }

  .hero-meta {
    align-items: flex-start;
  }

  .stat-strip,
  .operation-body,
  .capability-list {
    grid-template-columns: 1fr;
  }

  .stat-item + .stat-item {
    border-top: 1px solid #e9eef4;
    border-left: 0;
  }

  .agent-overview {
    border-right: 0;
    border-bottom: 1px solid #e9eef4;
  }

  .capability-list {
    gap: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .quick-list button {
    transition: none;
  }
}
</style>
