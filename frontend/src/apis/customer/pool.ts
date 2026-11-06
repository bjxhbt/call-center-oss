import Request from '@/utils/axios'

// 模板列表分页
export function getList(data: any) {
  return Request.post('/customer/v1/seas/page/list', data)
}

// 详情
export function getDetail(data: any) {
  return Request.post('/customer/v1/seas/get', data)
}

// edit
export function editSeas(data: any) {
  return Request.post(`/customer/v1/seas/edit/${data.id}`, data)
}

// add
export function addSeas(data: any) {
  return Request.post('/customer/v1/seas/add', data)
}

// delete
export function deleteSeas(data: any) {
  return Request.post('/customer/v1/seas/delete', data)
}
