import Request from '@/utils/axios'

//号码池列表
export function getPageList(data: any) {
  return Request.post('/call/v1/display/pool/page/list', data)
}

//新增号码池
export function addPhonePool(data: any) {
  return Request.post('/call/v1/display/pool/add', data)
}

//修改号码池
export function editPhonePool(data: any) {
  return Request.post(`/call/v1/display/pool/edit/${data.id}`, data)
}

//删除号码池
export function deletePhonePool(data: any) {
  return Request.post(`/call/v1/display/pool/delete`, data)
}

//号码池详情
export function getPhonePoolDetail(data: any) {
  return Request.post(`/call/v1/display/pool/get/${data.id}`, data)
}
// 号码池不分页
export function getListPool(data: any) {
  return Request.post('/call/v1/display/pool/list', data)
}
