import Request from '@/utils/axios'

//语音文件列表
export function getPageList(data: any) {
  return Request.post('/call/v1/voice/file/page/list', data)
}

//新增语音文件
export function addVoiceFile(data: any) {
  return Request.post('/call/v1/voice/file/add', data)
}

//删除语音文件
export function deleteVoiceFile(data: any) {
  return Request.post('/call/v1/voice/file/delete', data)
}

//编辑语音文件
export function editVoiceFile(data: any) {
  return Request.post(`/call/v1/voice/file/edit/${data.id}`, data)
}

//获取语音文件详情
export function getVoiceFileDetail(data: any) {
  return Request.post(`/call/v1/voice/file/get/${data.id}`, data)
}
