import Request from '@/utils/axios'

// 菜单
export function getMenuList(data: any) {
  return Request.post('/system/v1/menu/list', data)
}
