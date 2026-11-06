import Request from '@/utils/axios'

// 坐席列表
export function geAgentPageList(data: any) {
  return Request.post('/system/v1/agent/page/list', data)
}

// 坐席列表
export function getAgentList(data: any) {
  return Request.post('/system/v1/agent/list', data)
}

// delete坐席
export function deleteAgent(data: any) {
  return Request.post('/system/v1/agent/delete', data)
}

// 新增坐席
export function addAgent(data: any) {
  return Request.post('/system/v1/agent/add', data)
}

// 编辑坐席
export function editAgent(data: any) {
  return Request.post(`/system/v1/agent/edit/${data.id}`, data)
}

// 获取坐席详情
export function getDetail(data: any) {
  return Request.post(`/system/v1/agent/get/${data.id}`, data)
}

// 根据条件查询坐席
export function getInfoByQuery(data: any) {
  return Request.post(`/system/v1/agent/getInfoByQuery`, data)
}

// 获取当前用户SIP配置
export function getSipConfig() {
  return Request.get('/system/v1/agent/sip/config')
}

export function updateAgentOnlineStatus(agentId: number, onlineStatus: number) {
  return Request.post(`/system/v1/agent/online/status/${agentId}`, { onlineStatus })
}
