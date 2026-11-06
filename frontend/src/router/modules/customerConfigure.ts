export const customerRoutes = [
  {
    path: '/custoemrTemplateManagement',
    name: '',
    meta: {
      title: '客户模板'
    },
    component: () => import('@/views/Customer/template/index.vue')
  },
  {
    path: '/customerFieldManagement',
    name: '',
    meta: {
      title: '客户字段'
    },
    component: () => import('@/views/Customer/field/index.vue')
  },
  {
    path: '/customerSeasManagement',
    name: '',
    meta: {
      title: '客户公海'
    },
    component: () => import('@/views/Customer/pool/index.vue')
  },
  {
    path: '/customerCrowdManagement',
    name: '',
    meta: {
      title: '客户人群'
    },
    component: () => import('@/views/Customer/crowd/index.vue')
  }
]
