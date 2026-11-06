import Request from '@/utils/axios'
// 用户管理
export function getList(data: any) {
  return Request.post('/system/v1/user/page/list', data)
}

export function getUserSelectList(data: any) {
  return Request.post('/system/v1/user/select/list', data)
}

export function addUser(data: any) {
  return Request.post('/system/v1/user/add', data)
}

export function deleteUser(data: any) {
  return Request.post('/system/v1/user/delete', data)
}

export function editUser(data: any) {
  return Request.post(`/system/v1/user/edit/${data.userId}`, data) // userId
}

export function getUser(data: any) {
  return Request.post(`/system/v1/user/get/${data.id}`, data)
}

// 角色管理
export function getRoleList(data: any) {
  return Request.post('/system/v1/role/list', data)
}

export function getRole(data: any) {
  return Request.post('/system/v1/role/get', data)
}

export function editRole(data: any) {
  return Request.post(`/system/v1/role/edit/${data.roleId}`, data) // roleId
}

export function deleteRole(data: any) {
  return Request.post('/system/v1/role/delete', data)
}

export function addRole(data: any) {
  return Request.post('/system/v1/role/add', data)
}
