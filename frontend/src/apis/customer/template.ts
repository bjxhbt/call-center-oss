import Request from '@/utils/axios'

// 模板列表分页
export function getList(data: any) {
  return Request.post('/customer/v1/template/page/list', data)
}
// 获取模板不分页
export function getTempList(data: any) {
  return Request.post('/customer/v1/template/list', data)
}
// 详情
export function getDetail(data: any) {
  return Request.post('/customer/v1/template/get', data)
}

// edit
export function editTemplate(data: any) {
  return Request.post(`/customer/v1/template/edit/${data.id}`, data)
}

// add
export function addTemplate(data: any) {
  return Request.post('/customer/v1/template/add', data)
}

// delete
export function deleteTemplate(data: any) {
  return Request.post('/customer/v1/template/delete', data)
}
