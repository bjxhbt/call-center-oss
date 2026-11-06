export const fsConfigureRoutes = [
  {
    path: '/fsConfigureManagement',
    name: '',
    meta: {
      title: '配置管理'
    },
    component: () => import('@/views/FsConfigure/Configure/index.vue')
  },
  {
    path: '/fsModuleConfigureManagement',
    name: '',
    meta: {
      title: '模块配置管理'
    },
    component: () => import('@/views/FsConfigure/ModuleConfigure/index.vue')
  },
  {
    path: '/gatewayManagement',
    name: '',
    meta: {
      title: '网关管理'
    },
    component: () => import('@/views/FsConfigure/GatewayManagement/index.vue')
  },
  {
    path: '/accessControl',
    name: '',
    meta: {
      title: '访问控制管理'
    },
    component: () => import('@/views/FsConfigure/AccessControl/index.vue')
  },
  {
    path: '/dialPlanManagement',
    name: '',
    meta: {
      title: '拨号计划管理'
    },
    component: () => import('@/views/FsConfigure/DialPlanManagement/index.vue')
  }
]
