import Request from '@/utils/axios'

// 字段配置列表分页
export function getList(data: any) {
  return Request.post('/customer/v1/crowd/page/list', data)
}
// 字段配置列表不分页
export function getCrowdList(data: any) {
  return Request.post('/customer/v1/crowd/list', data)
}
// 详情
export function getDetail(data: any) {
  return Request.post('/customer/v1/crowd/get', data)
}

// edit
export function editCrowd(data: any) {
  return Request.post(`/customer/v1/crowd/edit/${data.id}`, data)
}

// add
export function addCrowd(data: any) {
  return Request.post('/customer/v1/crowd/add', data)
}

// delete
export function deleteCrowd(data: any) {
  return Request.post('/customer/v1/crowd/delete', data)
}
// 人群客户
export function getCustomerList(data: any) {
  return Request.post('/customer/v1/crowd/customer/page/list', data)
}
