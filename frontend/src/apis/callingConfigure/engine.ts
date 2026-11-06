import Request from '@/utils/axios'

//引擎列表
export function getEnginePageList(data: any) {
  return Request.post('/call/v1/engine/page/list', data)
}

export function getEngineList(data: any) {
  return Request.post('/call/v1/engine/list', data)
}

// 引擎详情
export function getEngineDetail(data: any) {
  return Request.post(`/call/v1/engine/get/${data.id}`, data)
}
// 新增引擎
export function addEngine(data: any) {
  return Request.post('/call/v1/engine/add', data)
}

// 编辑引擎
export function editEngine(data: any) {
  return Request.post(`/call/v1/engine/edit/${data.id}`, data)
}

// 删除引擎
export function deleteEngine(data: any) {
  return Request.post('/call/v1/engine/delete', data)
}