import Request from '@/utils/axios'

// 呼叫任务管理

// 呼叫任务列表（分页）
export function getTaskPageList(data: any) {
  return Request.post('/call/task/v1/page/list', data)
}
// 呼叫任务列表（不分页）
export function getTaskList(data: any) {
  return Request.post('/call/task/v1/list', data)
}
// 新增呼叫任务
export function addTask(data: any) {
  return Request.post('/call/task/v1/add', data)
}
// 编辑呼叫任务
export function editTask(data: any) {
  return Request.post(`/call/task/v1/edit/${data.id}`, data)
}

// 删除呼叫任务
export function deleteTask(data: any) {
  return Request.post('/call/task/v1/delete', data)
}
// 获取任务详情
export function getTaskDetail(data: any) {
  return Request.post(`/call/task/v1/get/${data.id}`, data)
}

// 开始呼叫任务
export function startTask(data: any) {
  return Request.post(`/call/task/v1/start/${data.id}`, data)
}
// 暂停呼叫任务
export function pauseTask(data: any) {
  return Request.post(`/call/task/v1/pause/${data.id}`, data)
}
// 结束呼叫任务
export function endTask(data: any) {
  return Request.post(`/call/task/v1/end/${data.id}`, data)
}

// 任务联系人列表（分页）
export function getTaskContactList(data: any) {
  return Request.post('/call/task/v1/task/customer/list', data)
}
// 导入任务联系人
export function importTaskContactList(data: any) {
  return Request.post('/call/task/v1/task/customer/import', data)
}
