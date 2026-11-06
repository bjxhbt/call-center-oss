<script setup lang="ts">
import { ReloadOutlined, PhoneOutlined } from '@ant-design/icons-vue'
import { getOverview, getAgentStatusList, getActiveCalls, getRecentCalls } from '@/apis/callingConfigure/monitor'
import { activeCallColumns, recentCallColumns } from './consts/tableColumns'
import { message, Tag, Badge } from 'ant-design-vue'
import dayjs from 'dayjs'

const overview = ref<any>({})
const agents = ref<any[]>([])
const activeCalls = ref<any[]>([])
const recentCalls = ref<any[]>([])
const loading = ref(false)
const autoRefresh = ref(true)
let refreshTimer: any = null

// 分页状态
const activePagination = reactive({ current: 1, pageSize: 10, total: 0 })
const recentPagination = reactive({ current: 1, pageSize: 10, total: 0 })

const formatDuration = (seconds: number) => {
  if (!seconds) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, '0')
  const s = (seconds % 60).toString().padStart(2, '0')
  return h > 0 ? `${h}:${m}:${s}` : `${m}:${s}`
}

const getDirectionText = (d: number) => d === 1 ? '呼入' : '呼出'
const getCallStateText = (s: number) => s === 1 ? '成功' : '失败'
const getCallStateColor = (s: number) => s === 1 ? 'success' : 'error'
const getHangupDirText = (d: number) => d === 1 ? '主叫' : d === 2 ? '被叫' : '系统'

const getOnlineStatusText = (s: number) => {
  const map: Record<number, string> = { 1: '空闲', 2: '忙碌', 3: '勿扰', 4: '离线', 5: '通话中', 6: '振铃中', 7: '话后处理' }
  return map[s] || '未知'
}

const fetchOverview = async () => {
  try {
    const [overviewRes, agentRes] = await Promise.all([
      getOverview(),
      getAgentStatusList()
    ])
    if (overviewRes.code === 200 && overviewRes.data) overview.value = overviewRes.data
    if (agentRes.code === 200 && agentRes.data) agents.value = agentRes.data || []
  } catch (e) { /* ignore */ }
}

const fetchActiveCalls = async () => {
  try {
    const res = await getActiveCalls(activePagination.current, activePagination.pageSize)
    if (res.code === 200 && res.data) {
      activeCalls.value = res.data.list || []
      activePagination.total = res.data.total || 0
    }
  } catch (e) { /* ignore */ }
}

const fetchRecentCalls = async () => {
  try {
    const res = await getRecentCalls(recentPagination.current, recentPagination.pageSize)
    if (res.code === 200 && res.data) {
      recentCalls.value = res.data.list || []
      recentPagination.total = res.data.total || 0
    }
  } catch (e) { /* ignore */ }
}

const fetchData = async () => {
  loading.value = true
  try {
    await Promise.all([fetchOverview(), fetchActiveCalls(), fetchRecentCalls()])
  } finally {
    loading.value = false
  }
}

const handleActivePageChange = (pag: any) => {
  activePagination.current = pag.current
  activePagination.pageSize = pag.pageSize
  fetchActiveCalls()
}

const handleRecentPageChange = (pag: any) => {
  recentPagination.current = pag.current
  recentPagination.pageSize = pag.pageSize
  fetchRecentCalls()
}

const handleRefresh = () => {
  activePagination.current = 1
  recentPagination.current = 1
  fetchData()
}

watch(autoRefresh, (val) => {
  if (val) startAutoRefresh()
  else stopAutoRefresh()
})

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    fetchOverview()
    fetchActiveCalls()
    fetchRecentCalls()
  }, 10000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const activeCallsFormatted = computed(() => {
  return (activeCalls.value || []).map(call => ({
    ...call,
    direction: getDirectionText(call.direction),
    agentName: call.agentName ? `${call.agentName}(${call.agentNumber})` : '-',
    callStartTime: call.callStartTime ? dayjs(call.callStartTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    duration: formatDuration(call.duration)
  }))
})

const recentCallsFormatted = computed(() => {
  return (recentCalls.value || []).map(call => ({
    ...call,
    direction: getDirectionText(call.direction),
    callStartTime: call.callStartTime ? dayjs(call.callStartTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    callDuration: formatDuration(call.callDuration),
    hangupDir: getHangupDirText(call.hangupDir)
  }))
})

const agentStats = computed(() => {
  const stats: Record<string, number> = { idle: 0, busy: 0, onCall: 0, offline: 0, other: 0 }
  agents.value.forEach(a => {
    const s = a.onlineStatus
    if (s === 1) stats.idle++
    else if (s === 2) stats.busy++
    else if (s === 5) stats.onCall++
    else if (s === 4) stats.offline++
    else stats.other++
  })
  return stats
})

onMounted(() => {
  fetchData()
  if (autoRefresh.value) startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>
<template>
  <!-- 顶部控制栏 -->
  <div class="mb8" style="display: flex; align-items: center; justify-content: space-between;">
    <div style="display: flex; align-items: center; gap: 12px;">
      <a-button type="primary" @click="handleRefresh" :loading="loading">
        <ReloadOutlined /> 刷新
      </a-button>
      <a-checkbox v-model:checked="autoRefresh">自动刷新(10秒)</a-checkbox>
    </div>
    <span style="color: #999; font-size: 12px;">最后刷新: {{ dayjs().format('HH:mm:ss') }}</span>
  </div>

  <!-- 概览统计卡片 -->
  <div class="mb16" style="display: flex; gap: 16px; flex-wrap: wrap;">
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="今日通话" :value="overview.todayTotal || 0">
        <template #prefix><PhoneOutlined /></template>
      </a-statistic>
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="今日接通" :value="overview.todayAnswered || 0" value-style="color: #52c41a" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="接通率" :value="overview.answerRate || 0" suffix="%" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="当前通话" :value="overview.activeCalls || 0" value-style="color: #1890ff" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="呼入" :value="overview.todayInbound || 0" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 140px;">
      <a-statistic title="呼出" :value="overview.todayOutbound || 0" />
    </a-card>
  </div>

  <div style="display: flex; gap: 16px;">
    <!-- 左侧: 坐席状态 -->
    <a-card title="坐席状态" size="small" style="width: 300px; flex-shrink: 0;">
      <div style="margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap;">
        <Tag color="green">空闲 {{ agentStats.idle }}</Tag>
        <Tag color="blue">通话中 {{ agentStats.onCall }}</Tag>
        <Tag color="orange">忙碌 {{ agentStats.busy }}</Tag>
        <Tag>离线 {{ agentStats.offline }}</Tag>
      </div>
      <div style="max-height: 400px; overflow-y: auto;">
        <div v-for="agent in agents" :key="agent.id"
          style="display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #f0f0f0;">
          <span>{{ agent.name }} ({{ agent.agentNumber }})</span>
          <Badge :status="agent.onlineStatus === 1 ? 'success' : agent.onlineStatus === 5 ? 'processing' : agent.onlineStatus === 4 ? 'default' : 'warning'"
            :text="getOnlineStatusText(agent.onlineStatus)" />
        </div>
        <div v-if="agents.length === 0" style="text-align: center; color: #999; padding: 20px;">暂无坐席</div>
      </div>
    </a-card>

    <!-- 右侧: 通话列表 -->
    <div style="flex: 1; display: flex; flex-direction: column; gap: 16px;">
      <!-- 正在通话 -->
      <a-card title="当前通话" size="small">
        <a-table :columns="activeCallColumns" :data-source="activeCallsFormatted" size="small"
          :scroll="{ x: 800 }" row-key="callId" :loading="loading"
          :pagination="{ current: activePagination.current, pageSize: activePagination.pageSize, total: activePagination.total, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条` }"
          @change="handleActivePageChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'duration'">
              <span style="color: #1890ff; font-weight: bold;">{{ record.duration }}</span>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 最近通话 -->
      <a-card title="最近通话" size="small">
        <a-table :columns="recentCallColumns" :data-source="recentCallsFormatted" size="small"
          :scroll="{ x: 900 }" row-key="callId" :loading="loading"
          :pagination="{ current: recentPagination.current, pageSize: recentPagination.pageSize, total: recentPagination.total, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条` }"
          @change="handleRecentPageChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'callState'">
              <Tag :color="getCallStateColor(record.callState)">{{ getCallStateText(record.callState) }}</Tag>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>
  </div>
</template>
<style scoped lang="less">
.mb16 {
  margin-bottom: 16px;
}
</style>
