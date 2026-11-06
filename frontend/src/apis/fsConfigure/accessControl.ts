import Request from '@/utils/axios'

// fs配置列表
export function getList(data: any) {
  return Request.post('/system/v1/acl/page/list', data)
}

// acl配置详情
export function getDetail(data: any) {
  return Request.post('/system/v1/acl/get/${data.id}', data)
}

// acl配置edit
export function editAccess(data: any) {
  return Request.post(`/system/v1/acl/list/edit/${data.id}`, data)
}

// acl配置add
export function addAccess(data: any) {
  return Request.post('/system/v1/acl/list/add', data)
}

// acl配置add
export function addNodeAccess(data: any) {
  return Request.post('/system/v1/acl/node/add', data)
}

// acl配置delete
export function deleteAccess(data: any) {
  return Request.post('/system/v1/acl/delete', data)
}
