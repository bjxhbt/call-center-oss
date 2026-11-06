import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { systemRoutes } from './modules/system'
import { callingConfigureRoutes } from './modules/callingConfigure'
import { fsConfigureRoutes } from './modules/fsConfigure'
import { IVRManagementRoutes } from './modules/IVRManagement'
import { customerRoutes } from './modules/customerConfigure'
import { callTaskRoutes } from '@/router/modules/callTaskConfigure'
import {robotConfigureRoutes} from "@/router/modules/robotConfigure";
import { agentWorkspaceRoutes } from '@/router/modules/agentWorkspace'

const router = createRouter({
  history: createWebHistory((import.meta as any).env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      meta: {
        title: '登录'
      },
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/ivrEdit',
      name: 'ivrEdit',
      meta: {
        title: 'IVR编辑'
      },
      component: () => import('@/views/IVRManagement/ivrEdit.vue')
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      children: [
        {
          path: '/',
          name: 'overview',
          meta: {
            title: '首页'
          },
          component: () => import('@/views/OverView/index.vue')
        },
        ...systemRoutes,
        ...callingConfigureRoutes,
        ...fsConfigureRoutes,
        ...IVRManagementRoutes,
        ...callTaskRoutes,
        ...customerRoutes,
        ...robotConfigureRoutes,
        ...agentWorkspaceRoutes,
      ]
    }
  ]
})

export default router
