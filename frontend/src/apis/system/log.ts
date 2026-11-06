import Request from '@/utils/axios'
//日志管理
export function getLogList(data: any) {
  return Request.post('/system/v1/log/list', data)
}

export function emptyLog(data: any) {
  return Request.post('/system/v1/log/empty', data)
}

export function deleteLog(data: any) {
  return Request.post('/system/v1/log/delete', data)
}
