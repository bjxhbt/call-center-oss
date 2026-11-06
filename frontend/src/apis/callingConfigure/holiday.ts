import Request from '@/utils/axios'

//假期列表(分页)
export function getPageList(data: any) {
  return Request.post('/call/v1/holiday/page/getList', data)
}

//假期列表(全部)
export function getList(data: any) {
  return Request.post('/call/v1/holiday/getList', data)
}

//新增假期
export function addHoliday(data: any) {
  return Request.post('/call/v1/holiday/add', data)
}

//删除假期
export function deleteHoliday(data: any) {
  return Request.post('/call/v1/holiday/delete', data)
}

//编辑假期
export function editHoliday(data: any) {
  return Request.post(`/call/v1/holiday/edit/${data.id}`, data)
}

//假期详情
export function getHolidayDetail(data: any) {
  return Request.post(`/call/v1/holiday/get/${data.id}`, data)
}