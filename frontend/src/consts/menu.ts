import {
  AppstoreOutlined,
  AudioOutlined,
  ControlOutlined,
  DatabaseOutlined,
  HomeOutlined,
  PhoneOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  UnorderedListOutlined
} from '@ant-design/icons-vue'
import { getUserMenuList } from '@/apis/system/menu'
import { setLocalMenus } from '@/utils/local'
import { h } from 'vue'

type menuType = {
  key: string
  icon?: () => any
  label: string
  path?: string
  children?: menuType[]
}
/*
  menuId
  menuType = 'M' / 'C' 是菜单, 'F'是功能权限
  name
  parentId
  path
  icon
*/
type IconName = keyof typeof iconMap
const iconMap = {
  HomeOutlined,
  AppstoreOutlined,
  AudioOutlined,
  ControlOutlined,
  DatabaseOutlined,
  PhoneOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  UnorderedListOutlined
}
const resolveMenuIcon = (iconName?: string) => iconMap[iconName as IconName] || HomeOutlined
const transformTree = (tree: any) => {
  return tree
    .filter((node: any) => node.status !== 1 && node.visible !== 1)
    .map((node: any) => {
      // 过滤掉 'F' 类型（按钮权限），保留 'M' 和 'C' 类型的子节点
      const menuChildren = node.children?.filter(
        (item: any) => item.menuType !== 'F' && item.status !== 1 && item.visible !== 1
      )
      const iconName = node.iconName || 'HomeOutlined'
      const iconComponent = resolveMenuIcon(iconName)
      return {
        label: node.name,
        key: node.menuId,
        path: node.path,
        icon: node.menuType === 'M' ? () => h(iconComponent) : undefined,
        iconName: node.iconName,
        menuType: node.menuType,
        parentId: node.parentId,
        children: menuChildren?.length ? transformTree(menuChildren) : undefined // 递归处理子节点
      }
    })
}
export const MENU_CHANGED_EVENT = 'och-menu-changed'
export const MENU_CHANGED_STORAGE_KEY = 'och-menu-changed-at'

export const getMenus = async () => {
  const res = await getUserMenuList()
  const menuArr = [
    {
      key: 0,
      icon: () => h(HomeOutlined),
      label: '首页',
      path: '/',
      iconName: 'HomeOutlined'
    },
    ...transformTree(res?.data || res || [])
  ]
  setLocalMenus(JSON.stringify(menuArr))
  return menuArr
}

/** 通知整个应用菜单数据已变更，侧栏需要重新加载 */
export const notifyMenuChanged = () => {
  localStorage.setItem(MENU_CHANGED_STORAGE_KEY, String(Date.now()))
  window.dispatchEvent(new CustomEvent(MENU_CHANGED_EVENT))
}
