import Request from '@/utils/axios'

// fs拨号计划配置列表 /system/v1/dialplan/page/list
export function getList(data: any) {
  return Request.post('/system/v1/dialplan/page/list', data)
}

// fs拨号计划配置详情
export function getDetail(data: any) {
  return Request.post(`/system/v1/dialplan/get/${data.id}`, data)
}

// fs拨号计划配置edit
export function editConfigure(data: any) {
  return Request.post(`/system/v1/dialplan/edit/${data.id}`, data)
}

// fs拨号计划配置add
export function addConfigure(data: any) {
  return Request.post('/system/v1/dialplan/add', data)
}

// fs拨号计划配置delete
export function deleteConfigure(data: any) {
  return Request.post('/system/v1/dialplan/delete', data)
}
