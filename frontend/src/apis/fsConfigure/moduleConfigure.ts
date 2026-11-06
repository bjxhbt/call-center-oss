import Request from '@/utils/axios'

// fs模块配置列表
export function getList(data: any) {
  return Request.post('/system/v1/modules/page/list', data)
}

// fs模块配置详情
export function getDetail(data: any) {
  return Request.post(`/system/v1/modules/get/${data.id}`, data)
}

// fs模块配置edit
export function editConfigure(data: any) {
  return Request.post(`/system/v1/modules/edit/${data.id}`, data)
}

// fs模块配置add
export function addConfigure(data: any) {
  return Request.post('/system/v1/modules/add', data)
}

// fs模块配置delete
export function deleteConfigure(data: any) {
  return Request.post('/system/v1/modules/delete', data)
}
