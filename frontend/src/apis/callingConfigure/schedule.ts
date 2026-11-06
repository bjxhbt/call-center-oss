import Request from '@/utils/axios'

//日程列表
export function getPageList(data: any) {
  return Request.post('/call/v1/schedule/page/getList', data)
}

//新增日程
export function addSchedule(data: any) {
  return Request.post('/call/v1/schedule/add', data)
}

//删除日程
export function deleteSchedule(data: any) {
  return Request.post('/call/v1/schedule/delete', data)
}

//编辑日程
export function editSchedule(data: any) {
  return Request.post(`/call/v1/schedule/edit/${data.id}`, data)
}

//获取日程详情
export function getScheduleDetail(data: any) {
  return Request.post(`/call/v1/schedule/get/${data.id}`, data)
}
