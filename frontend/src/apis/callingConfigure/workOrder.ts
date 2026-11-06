import Request from '@/utils/axios'

// 工单列表(分页)
export function getWorkOrderList(params: any) {
  return Request.get('/v1/workorder/list', null, { params })
}

// 工单详情
export function getWorkOrderDetail(id: number) {
  return Request.get(`/v1/workorder/get/${id}`)
}

// 创建工单
export function createWorkOrder(data: any) {
  return Request.post('/v1/workorder/create', data)
}

export function getWorkOrderHistory(phone: string, excludeId?: number) {
  return Request.get('/v1/workorder/history', null, {
    params: { phone, ...(excludeId ? { excludeId } : {}) }
  })
}

export function downloadWorkOrderAttachment(id: number | string) {
  return Request.download(`/system/v1/file/download/${id}`)
}

// 更新工单状态
export function updateWorkOrder(id: number, data: any) {
  return Request.post(`/v1/workorder/update/${id}`, data)
}

// 删除工单
export function deleteWorkOrder(id: number) {
  return Request.post(`/v1/workorder/delete/${id}`)
}
