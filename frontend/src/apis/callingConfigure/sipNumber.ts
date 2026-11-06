import Request from '@/utils/axios'

// SIP号码列表
export function getSipList(data: any) {
  return Request.post('/system/v1/sip/list', data)
}

export function getSipSelectList(data: any) {
  return Request.post('/system/v1/sip/select/list', data)
}

// 删除SIP号码
export function deleteSip(data: any) {
  return Request.post('/system/v1/sip/delete', data)
}

// 新增SIP号码
export function addSip(data: any) {
  return Request.post('/system/v1/sip/add', data)
}

//批量新增SIP号码
export function batchAddSip(data: any) {
  return Request.post('/system/v1/sip/batch/add', data)
}

// 编辑SIP号码
export function editSip(data: any) {
  return Request.post(`/system/v1/sip/edit/${data.id}`, data)
}

// SIP号码详情
export function getSipDetail(data: any) {
  return Request.post(`/system/v1/sip/get/${data.id}`, data)
}


