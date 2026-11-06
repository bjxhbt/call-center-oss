import Request from '@/utils/axios'
//菜单管理
export function getMenuList(data: any) {
  return Request.post('/system/v1/menu/list', data)
}

// 当前登录用户可见的菜单路由；侧栏必须使用此接口，不能使用菜单管理列表。
export function getUserMenuList() {
  return Request.get('/system/v1/user/getRouters')
}

export function getMenu(data: any) {
  return Request.post(`/system/v1/menu/get/${data.menuId}`, data) //menuId
}

export function addMenu(data: any) {
  return Request.post('/system/v1/menu/add', data)
}

export function editMenu(data: any) {
  return Request.post(`/system/v1/menu/edit/${data.menuId}`, data)
}

export function deleteMenu(data: any) {
  return Request.post('/system/v1/menu/delete', data)
}
