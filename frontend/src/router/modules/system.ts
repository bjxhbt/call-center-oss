export const systemRoutes = [
  {
    path: '/userManagement',
    name: '',
    meta: {
      title: '用户管理'
    },
    component: () => import('@/views/System/userManagement/index.vue')
  },
  {
    path: '/roleManagement',
    name: '',
    meta: {
      title: '角色管理'
    },
    component: () => import('@/views/System/roleManagement/index.vue')
  },
  {
    path: '/menuManagement',
    name: '',
    meta: {
      title: '菜单管理'
    },
    component: () => import('@/views/System/menuManagement/index.vue')
  },
  {
    path: '/logManagement',
    name: '',
    meta: {
      title: '日志管理'
    },
    component: () => import('@/views/System/logManagement/index.vue')
  }
]

// 用户管理，角色管理，菜单管理
