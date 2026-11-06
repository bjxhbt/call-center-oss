import Request from '@/utils/axios'

// fs配置列表
export function getList(data: any) {
  return Request.post('/system/v1/fs/page/list', data)
}

// fs配置详情
export function getDetail(data: any) {
  return Request.post(`/system/v1/fs/get/${data.id}`, data)
}

// fs配置edit
export function editConfigure(data: any) {
  return Request.post(`/system/v1/fs/edit/${data.id}`, data)
}

// fs配置add
export function addConfigure(data: any) {
  return Request.post('/system/v1/fs/add', data)
}

// fs配置delete
export function deleteConfigure(data: any) {
  return Request.post('/system/v1/fs/delete', data)
}
