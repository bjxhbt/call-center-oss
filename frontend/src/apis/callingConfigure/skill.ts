import Request from '@/utils/axios'

// skill列表
export function getList(data: any) {
  return Request.post('/call/v1/skill/page/list', data)
}

// 创建skill
export function addSkill(data: any) {
  return Request.post('/call/v1/skill/add', data)
}

// 删除 skill 
export function deleteSkill(data: any) {
  return Request.post('/call/v1/skill/delete', data)
}

// 编辑 skill
export function editSkill(data: any) {
  return Request.post(`/call/v1/skill/edit/${data.id}`, data)
}

// 详情 skill
export function getSkillDetail(data: any) {
  return Request.post(`/call/v1/skill/get/${data.id}`, data)
}

