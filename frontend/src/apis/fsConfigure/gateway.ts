import Request from '@/utils/axios'

// fs网关配置列表
export function getGatewayPageList(data: any) {
  return Request.post('/system/v1/gateway/page/list', data)
}

// fs网关配置详情
export function getGatewayDetail(data: any) {
  return Request.post(`/system/v1/gateway/get/${data.id}`, data)
}

// fs网关配置edit
export function editGateway(data: any) {
  return Request.post(`/system/v1/gateway/edit/${data.id}`, data)
}

// fs网关配置add
export function addGateway(data: any) {
  return Request.post('/system/v1/gateway/add', data)
}

// fs网关配置delete
export function deleteGateway(data: any) {
  return Request.post('/system/v1/gateway/delete', data)
}
