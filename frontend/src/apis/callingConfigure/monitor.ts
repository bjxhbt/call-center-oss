import Request from '@/utils/axios'

// 获取监控概览
export function getOverview() {
  return Request.get('/v1/monitor/overview')
}

// 获取坐席状态列表
export function getAgentStatusList() {
  return Request.get('/v1/monitor/agents')
}

// 获取正在进行的通话(分页)
export function getActiveCalls(page: number = 1, size: number = 10) {
  return Request.get('/v1/monitor/active-calls', null, { params: { page, size } })
}

// 获取最近通话记录(分页)
export function getRecentCalls(page: number = 1, size: number = 10) {
  return Request.get('/v1/monitor/recent-calls', null, { params: { page, size } })
}

// 获取小时级趋势
export function getHourlyTrend() {
  return Request.get('/v1/monitor/hourly-trend')
}
