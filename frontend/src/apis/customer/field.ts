import Request from '@/utils/axios'

// 字段配置列表分页
export function getList(data: any) {
  return Request.post('/customer/v1/field/page/list', data)
}
// 字段配置列表不分页
export function getSimp(data: any) {
  return Request.post('/customer/v1/field/list', data)
}
// 详情
export function getDetail(data: any) {
  return Request.post('/customer/v1/field/get', data)
}

// edit
export function editField(data: any) {
  return Request.post(`/customer/v1/field/edit/${data.id}`, data)
}

// add
export function addField(data: any) {
  return Request.post('/customer/v1/field/add', data)
}

// delete
export function deleteField(data: any) {
  return Request.post('/customer/v1/field/delete', data)
}
