export const callTaskRoutes = [
  {
    path: '/callTaskManagement',
    name: '',
    meta: {
      title: '任务管理'
    },
    component: () => import('@/views/callTaskConfigure/CallTask/index.vue')
  }
]
